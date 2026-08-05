package com.olamide.UniSwap.Service;

import com.olamide.UniSwap.Entity.User;
import com.olamide.UniSwap.Repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

    public PasswordResetService(
            UserRepository userRepository,
            UserService userService,
            PasswordEncoder passwordEncoder,
            EmailVerificationService emailVerificationService
    ) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
    }

    // Step 1 of the reset flow: email a one-time code. Deliberately returns the
    // same "we accepted this" response whether or not the email exists, so
    // callers can't enumerate registered accounts.
    @Transactional
    public void requestPasswordReset(String email) {
        String normalized = UserService.normalizeEmail(email);
        User user = userRepository.findByEmail(normalized).orElse(null);
        if (user == null || !user.isEmailVerified()) {
            return;
        }
        emailVerificationService.generateAndSendCode(normalized, VerificationPurpose.RESET);
        log.info("Issued password reset code for user {}", normalized);
    }

    // Step 2: verify the emailed code, then set the new password. The code is
    // consumed on first use, so it can't be replayed.
    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        String normalized = UserService.normalizeEmail(email);
        User user = userRepository.findByEmail(normalized)
                .orElseThrow(() -> badCode("Invalid or expired reset code"));
        if (!user.isEmailVerified()) {
            throw badCode("Invalid or expired reset code");
        }
        if (!emailVerificationService.verifyCode(normalized, VerificationPurpose.RESET, code)) {
            throw badCode("Invalid or expired reset code");
        }

        // Enforce the same password rules as registration, including the
        // password-must-not-match-username check.
        userService.validateCredentials(user.getUsername(), newPassword, user.getPassword());

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setEmailVerified(true);
        userRepository.save(user);
        log.info("Password reset completed for user {}", normalized);
    }

    private ResponseStatusException badCode(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
