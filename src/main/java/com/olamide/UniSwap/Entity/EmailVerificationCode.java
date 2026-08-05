package com.olamide.UniSwap.Entity;

import com.olamide.UniSwap.Service.VerificationPurpose;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

// A short-lived, one-time numeric code emailed to a user to prove they control
// the inbox (signup verification, passwordless login, password reset). Only the
// SHA-256 HASH of the code is persisted, and failed guesses are counted so the
// 6-digit space can't be brute-forced.
@Entity
@Table(name = "email_verification_codes", indexes = {
        @Index(name = "idx_verification_email", columnList = "email"),
        @Index(name = "idx_verification_email_purpose", columnList = "email, purpose")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailVerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VerificationPurpose purpose;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used", nullable = false)
    private boolean used;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public boolean isExpired() {
        return expiresAt == null || LocalDateTime.now(ZoneOffset.UTC).isAfter(expiresAt);
    }
}
