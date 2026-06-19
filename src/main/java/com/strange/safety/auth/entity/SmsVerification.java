package com.strange.safety.auth.entity;

import com.strange.safety.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sms_verifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SmsVerification extends BaseEntity {

    private static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sms_verification_id")
    private Long id;

    @Column(name = "phone_number", nullable = false, length = 30)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VerificationPurpose purpose;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "code_expires_at", nullable = false)
    private Instant codeExpiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    private SmsVerification(String phoneNumber, VerificationPurpose purpose,
                            String codeHash, Instant codeExpiresAt) {
        this.phoneNumber = phoneNumber;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.codeExpiresAt = codeExpiresAt;
    }

    public static SmsVerification issue(String phoneNumber, VerificationPurpose purpose,
                                        String codeHash, Instant codeExpiresAt) {
        return new SmsVerification(phoneNumber, purpose, codeHash, codeExpiresAt);
    }

    public boolean canConfirm(Instant now) {
        return verifiedAt == null && codeExpiresAt.isAfter(now)
                && failedAttempts < MAX_ATTEMPTS;
    }

    public void recordFailure() {
        failedAttempts++;
    }

    public void markVerified(Instant now) {
        this.verifiedAt = now;
    }
}
