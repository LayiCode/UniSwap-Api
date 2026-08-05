package com.olamide.UniSwap.Service;

import com.olamide.UniSwap.Entity.EmailVerificationCode;
import com.olamide.UniSwap.Repository.EmailVerificationCodeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;

@Service
public class EmailVerificationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS = 5;

    private final EmailVerificationCodeRepository codeRepository;
    private final MailService mailService;
    private final long codeTtlMinutes;

    public EmailVerificationService(
            EmailVerificationCodeRepository codeRepository,
            MailService mailService,
            @Value("${app.verification-code.ttl-minutes:10}") long codeTtlMinutes
    ) {
        this.codeRepository = codeRepository;
        this.mailService = mailService;
        this.codeTtlMinutes = codeTtlMinutes;
    }

    // Issues a fresh 6-digit code for an address+purpose and emails it. Returns
    // the raw code so tests (and internal callers) can inspect it — never expose
    // it through a controller.
    @Transactional
    public String generateAndSendCode(String email, VerificationPurpose purpose) {
        String normalized = UserService.normalizeEmail(email);

        // Only the most recent code per address+purpose should be usable.
        codeRepository.deleteByEmailAndPurpose(normalized, purpose);

        String rawCode = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        EmailVerificationCode code = EmailVerificationCode.builder()
                .email(normalized)
                .purpose(purpose)
                .codeHash(hash(rawCode))
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(codeTtlMinutes))
                .used(false)
                .attempts(0)
                .build();
        codeRepository.save(code);

        mailService.sendVerificationCode(normalized, rawCode, purpose);
        return rawCode;
    }

    // Validates AND consumes a code: returns true only once, and a wrong guess
    // eats into the attempts budget so the 6-digit space can't be brute-forced.
    @Transactional
    public boolean verifyCode(String email, VerificationPurpose purpose, String code) {
        if (code == null || code.isBlank()) return false;

        EmailVerificationCode record = codeRepository
                .findFirstByEmailAndPurposeOrderByCreatedAtDesc(
                        UserService.normalizeEmail(email), purpose)
                .orElse(null);

        if (record == null || record.isUsed() || record.isExpired()) {
            return false;
        }
        if (record.getAttempts() >= MAX_ATTEMPTS) {
            record.setUsed(true);
            codeRepository.save(record);
            return false;
        }

        if (hash(code.trim()).equals(record.getCodeHash())) {
            record.setUsed(true);
            codeRepository.save(record);
            return true;
        }

        record.setAttempts(record.getAttempts() + 1);
        codeRepository.save(record);
        return false;
    }

    // Hashes the raw code with SHA-256 so a leaked codes table is useless.
    public static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
