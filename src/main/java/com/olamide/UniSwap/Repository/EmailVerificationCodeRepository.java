package com.olamide.UniSwap.Repository;

import com.olamide.UniSwap.Entity.EmailVerificationCode;
import com.olamide.UniSwap.Service.VerificationPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {
    Optional<EmailVerificationCode> findFirstByEmailAndPurposeOrderByCreatedAtDesc(String email, VerificationPurpose purpose);
    void deleteByEmailAndPurpose(String email, VerificationPurpose purpose);
}
