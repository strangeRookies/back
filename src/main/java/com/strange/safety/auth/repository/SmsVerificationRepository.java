package com.strange.safety.auth.repository;

import com.strange.safety.auth.entity.SmsVerification;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmsVerificationRepository extends JpaRepository<SmsVerification, Long> {
    Optional<SmsVerification> findByVerificationTokenHash(String verificationTokenHash);

    boolean existsByPhoneNumberAndCreatedAtAfter(String phoneNumber, LocalDateTime createdAt);

    long countByPhoneNumberAndCreatedAtAfter(String phoneNumber, LocalDateTime createdAt);
}
