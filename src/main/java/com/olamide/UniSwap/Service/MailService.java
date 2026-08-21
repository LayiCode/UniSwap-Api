package com.olamide.UniSwap.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

// Sends transactional emails. Spring Boot only creates a JavaMailSender bean
// when MAIL_HOST is set, so in plain local dev (no SMTP) we degrade to
// printing the message to the log instead — every email flow still works end
// to end, you just read the code from the backend console.
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String host;
    private final String from;

    public MailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.from:}") String from,
            @Value("${spring.mail.username:}") String smtpUsername
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.host = host;
        // The From address is the verified sender (MAIL_FROM); if unset, fall
        // back to the SMTP username so local SMTP setups still work.
        this.from = (from == null || from.isBlank()) ? smtpUsername : from;
    }

    public boolean isSmtpConfigured() {
        return host != null && !host.isBlank();
    }

    // Fire-and-forget on the mailExecutor pool: SMTP latency or outages must
    // never block the HTTP request that triggered the send.
    @Async("mailExecutor")
    public void sendVerificationCode(String to, String code, VerificationPurpose purpose) {
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
            return;
        }

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("[DEV] Mail sender unavailable, email verification for {} ({}): code {}", to, purpose, code);
            return;
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
        } catch (MailException ex) {
            // Never fail the request because email delivery failed — the caller
            // (forgot-password/login-code) must not reveal whether the address exists.
            log.error("Failed to send email to {}", to, ex);
        }
    }
}
