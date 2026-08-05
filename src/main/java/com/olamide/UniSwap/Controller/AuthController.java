package com.olamide.UniSwap.Controller;

import com.olamide.UniSwap.Dto.*;
import com.olamide.UniSwap.Service.LoginRateLimiter;
import com.olamide.UniSwap.Service.PasswordResetService;
import com.olamide.UniSwap.Service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

// Publicly accessible endpoints — see SecurityConfig, /api/auth/** is permitAll.
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    public static final String GOOGLE_AUTHORIZATION_URL = "/oauth2/authorization/google";

    private final UserService userService;
    private final PasswordResetService passwordResetService;
    private final LoginRateLimiter loginRateLimiter;

    @Value("${GOOGLE_CLIENT_ID:}")
    private String googleClientId;

    @Value("${app.backend-url:http://localhost:8080}")
    private String backendUrl;

    // Creates the account (emailVerified=false) and emails a signup code.
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Password login: authenticates with email + password.
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequest request) {
        AuthResponseDTO response = userService.login(request);
        return ResponseEntity.ok(response);
    }

    // Live username-availability check for the register form.
    @GetMapping("/check-username")
    public ResponseEntity<Map<String, Object>> checkUsername(
            @RequestParam(required = false, defaultValue = "") String username) {
        return ResponseEntity.ok(Map.of(
                "available", userService.isUsernameAvailable(username),
                "username", username));
    }

    // Confirms the emailed signup code and unlocks login for the account.
    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, Object>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        userService.verifyEmail(request.getEmail(), request.getCode());
        return ResponseEntity.ok(Map.of("message", "Email verified. You can now log in."));
    }

    // Passwordless login, step 1: email a one-time code (if the address exists
    // and is verified — the response is identical otherwise, anti-enumeration).
    @PostMapping("/login-code")
    public ResponseEntity<Void> requestLoginCode(@Valid @RequestBody LoginCodeRequest request) {
        if (!loginRateLimiter.isAllowed("login-code:" + UserService.normalizeEmail(request.getEmail()))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many code requests. Please try again in a few minutes.");
        }
        userService.requestLoginCode(request.getEmail());
        return ResponseEntity.ok().build();
    }

    // Passwordless login, step 2: exchange the emailed code for a JWT.
    @PostMapping("/login-code/verify")
    public ResponseEntity<AuthResponseDTO> loginWithCode(@Valid @RequestBody LoginCodeVerifyRequest request) {
        AuthResponseDTO response = userService.loginWithCode(request.getEmail(), request.getCode());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        // Throttle per email so the endpoint can't be used to spam a victim's
        // inbox. The response is identical whether or not the account exists.
        if (!loginRateLimiter.isAllowed("reset:" + UserService.normalizeEmail(request.getEmail()))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many reset requests. Please try again in a few minutes.");
        }
        passwordResetService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getEmail(), request.getCode(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }

    // Re-sends the signup code for an account that hasn't been verified yet.
    // Silent (200) whether or not such an account exists, so callers can't
    // probe registered-but-unverified emails.
    @PostMapping("/resend-verification-code")
    public ResponseEntity<Void> resendVerificationCode(@Valid @RequestBody LoginCodeRequest request) {
        if (!loginRateLimiter.isAllowed("resend:" + UserService.normalizeEmail(request.getEmail()))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many resend requests. Please try again in a few minutes.");
        }
        userService.resendSignupCode(request.getEmail());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/config")
    public ResponseEntity<AuthConfigResponse> config() {
        boolean googleEnabled = googleClientId != null && !googleClientId.isBlank();
        return ResponseEntity.ok(AuthConfigResponse.builder()
                .googleEnabled(googleEnabled)
                .googleAuthorizationUrl(backendUrl + GOOGLE_AUTHORIZATION_URL)
                .build());
    }
}
