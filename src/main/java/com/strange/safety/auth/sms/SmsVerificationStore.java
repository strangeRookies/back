package com.strange.safety.auth.sms;

import com.strange.safety.auth.entity.VerificationPurpose;
import java.time.Duration;
import java.time.LocalDate;

public interface SmsVerificationStore {

    boolean isCooldownActive(String phoneNumber);

    long dailySentCount(String phoneNumber, LocalDate date);

    void recordSent(String phoneNumber, Duration cooldownTtl, LocalDate date, Duration dailyTtl);

    void saveVerifiedToken(String tokenHash, String phoneNumber, VerificationPurpose purpose, Duration ttl);

    boolean consumeVerifiedToken(String tokenHash, String phoneNumber, VerificationPurpose purpose);

    void clear(String phoneNumber);
}
