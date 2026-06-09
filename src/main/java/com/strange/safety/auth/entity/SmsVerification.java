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

    @Column(name = "verification_token_hash", unique = true, length = 64)
    private String verificationTokenHash;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "request_ip", length = 64)
    private String requestIp;

    private SmsVerification(String phoneNumber, VerificationPurpose purpose,
                            String codeHash, Instant codeExpiresAt, String requestIp) {
        this.phoneNumber = phoneNumber;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.codeExpiresAt = codeExpiresAt;
        this.requestIp = requestIp;
    }

    public static SmsVerification issue(String phoneNumber, VerificationPurpose purpose,
                                        String codeHash, Instant codeExpiresAt) {
        return issue(phoneNumber, purpose, codeHash, codeExpiresAt, null);
    }

    public static SmsVerification issue(String phoneNumber, VerificationPurpose purpose,
                                        String codeHash, Instant codeExpiresAt, String requestIp) {
        return new SmsVerification(phoneNumber, purpose, codeHash, codeExpiresAt, requestIp);
    }

    public boolean canConfirm(Instant now) {
        return usedAt == null && verifiedAt == null && codeExpiresAt.isAfter(now)
                && failedAttempts < MAX_ATTEMPTS;
    }

    public void recordFailure() {
        failedAttempts++;
    }

    public void verify(String tokenHash, Instant tokenExpiresAt, Instant now) {
        this.verificationTokenHash = tokenHash;
        this.tokenExpiresAt = tokenExpiresAt;
        this.verifiedAt = now;
    }

    public boolean canUse(String phoneNumber, VerificationPurpose purpose, Instant now) {
        return this.phoneNumber.equals(phoneNumber) && this.purpose == purpose
                && verifiedAt != null && usedAt == null && tokenExpiresAt != null
                && tokenExpiresAt.isAfter(now);
    }

    public void use(Instant now) {
        this.usedAt = now;
    }
}
