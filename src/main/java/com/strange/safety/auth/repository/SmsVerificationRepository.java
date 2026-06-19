package com.strange.safety.auth.repository;

import com.strange.safety.auth.entity.SmsVerification;
import com.strange.safety.auth.entity.VerificationPurpose;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmsVerificationRepository extends JpaRepository<SmsVerification, Long> {
    Optional<SmsVerification> findTopByPhoneNumberAndPurposeAndVerifiedAtIsNullOrderByIdDesc(
            String phoneNumber,
            VerificationPurpose purpose
    );
}
