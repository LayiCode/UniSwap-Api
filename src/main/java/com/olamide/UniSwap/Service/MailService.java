package com.olamide.UniSwap.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

// Sends transactional emails, preferring Brevo's HTTPS REST API when a
// BREVO_API_KEY is configured. Render blocks outbound SMTP (ports 465/587) —
// socket connects to smtp-relay.brevo.com just time out — but HTTPS (443)
// egress works fine (the app already talks to Supabase over HTTPS). So in
// production we call POST https://api.brevo.com/v3/smtp/email over 443 and
// bypass SMTP entirely.
//
// When no API key is set (plain local dev / no SMTP) we degrade to printing
// the message to the log — every email flow still works end to end, you just
// read the code from the backend console.
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";
    private static final Duration API_TIMEOUT = Duration.ofSeconds(10);

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String host;
    private final String from;
    private final String brevoApiKey;

    public MailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.from:}") String from,
            @Value("${spring.mail.username:}") String smtpUsername,
            @Value("${app.mail.brevo-api-key:}") String brevoApiKey
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.host = host;
        this.brevoApiKey = brevoApiKey;
        // The From address is the verified sender (MAIL_FROM); if unset, fall
        // back to the SMTP username so local SMTP setups still work.
        this.from = (from == null || from.isBlank()) ? smtpUsername : from;
    }

    public boolean isSmtpConfigured() {
        // Production uses the Brevo REST API (brevoApiKey set) which needs no
        // SMTP host; SMTP is only the local/no-key fallback.
        return (brevoApiKey != null && !brevoApiKey.isBlank())
                || (host != null && !host.isBlank());
    }

    // Attempts to email a verification code. Returns true when the message was
    // handed to the SMTP relay (queued), false when delivery is impossible —
    // no SMTP configured, the sender bean is missing, or the relay refused the
    // message. Callers use the boolean to decide whether to surface the code in
    // the response as a fallback: campus email is notoriously unreliable, and a
    // stuck signup is worse than a code shown on screen.
    public boolean sendVerificationCode(String to, String code, VerificationPurpose purpose) {
        String subject = switch (purpose) {
            case SIGNUP -> "Verify your UniSwap email";
            case LOGIN -> "Your UniSwap login code";
            case RESET -> "Your UniSwap password reset code";
        };
        String body = "Hi,\n\n"
                + "Your UniSwap verification code is:\n\n"
                + "   " + code + "\n\n"
                + "It expires in 10 minutes. If you didn't request this, "
                + "you can safely ignore this email.\n\n"
                + "— UniSwap";

        if (!isSmtpConfigured()) {
            // Dev mode: no SMTP configured, print the code so the flow is usable.
            log.warn("[DEV] Email verification for {} ({}): code {}", to, purpose, code);
            return false;
        }

        // Production path: Brevo REST API over HTTPS (443). Render can't reach
        // Brevo's SMTP ports, so this is the reliable way to actually send.
        if (brevoApiKey != null && !brevoApiKey.isBlank()) {
            return sendViaBrevoApi(to, subject, body, purpose);
        }

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("[DEV] Mail sender unavailable, email verification for {} ({}): code {}", to, purpose, code);
            return false;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            // Makes delivery attempts visible in production logs: if neither
            // this line nor the error below appears, the send never ran.
            log.info("Email queued via SMTP: to={}, purpose={}", to, purpose);
            return true;
        } catch (MailException ex) {
            // Never fail the request because email delivery failed — the caller
            // (forgot-password/login-code) must not reveal whether the address exists.
            log.error("Failed to send email to {}", to, ex);
            return false;
        }
    }

    // Sends via Brevo's transactional email REST API. Returns true only when
    // Brevo accepts the message (HTTP 201). Any other status or a transport
    // error returns false so the caller can surface the code as a fallback.
    private boolean sendViaBrevoApi(String to, String subject, String body, VerificationPurpose purpose) {
        try {
            String payload = "{"
                    + "\"sender\":{\"email\":" + jsonEscape(from) + ",\"name\":\"UniSwap\"},"
                    + "\"to\":[{\"email\":" + jsonEscape(to) + "}],"
                    + "\"subject\":" + jsonEscape(subject) + ","
                    + "\"textContent\":" + jsonEscape(body)
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BREVO_API_URL))
                    .timeout(API_TIMEOUT)
                    .header("api-key", brevoApiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                log.info("Email sent via Brevo API: to={}, purpose={}, status={}", to, purpose, response.statusCode());
                return true;
            }
            log.error("Brevo API rejected email to {} ({}): status={}, body={}",
                    to, purpose, response.statusCode(), truncate(response.body()));
            return false;
        } catch (IOException | InterruptedException ex) {
            log.error("Failed to send email via Brevo API to {} ({})", to, purpose, ex);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // Minimal JSON string escaping for the fixed-shape payload we build.
    private static String jsonEscape(String value) {
        if (value == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private static String truncate(String s) {
        return s == null ? "" : (s.length() > 500 ? s.substring(0, 500) : s);
    }
}
