package com.strange.safety.auth.sms;

import com.strange.safety.auth.entity.VerificationPurpose;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class InMemorySmsVerificationStore implements SmsVerificationStore {

    private final Map<String, Entry<String>> verifiedTokens = new ConcurrentHashMap<>();
    private final Map<String, Instant> cooldowns = new ConcurrentHashMap<>();
    private final Map<String, Entry<Long>> dailyCounts = new ConcurrentHashMap<>();

    @Override
    public boolean isCooldownActive(String phoneNumber) {
        Instant expiresAt = cooldowns.get(phoneNumber);
        if (expiresAt == null) {
            return false;
        }
        if (!expiresAt.isAfter(Instant.now())) {
            cooldowns.remove(phoneNumber);
            return false;
        }
        return true;
    }

    @Override
    public long dailySentCount(String phoneNumber, LocalDate date) {
        Entry<Long> entry = dailyCounts.get(dailyKey(phoneNumber, date));
        if (entry == null) {
            return 0L;
        }
        if (entry.isExpired()) {
            dailyCounts.remove(dailyKey(phoneNumber, date));
            return 0L;
        }
        return entry.value();
    }

    @Override
    public void recordSent(String phoneNumber, Duration cooldownTtl, LocalDate date, Duration dailyTtl) {
        if (!cooldownTtl.isZero() && !cooldownTtl.isNegative()) {
            cooldowns.put(phoneNumber, Instant.now().plus(cooldownTtl));
        }
        dailyCounts.merge(dailyKey(phoneNumber, date), new Entry<>(1L, Instant.now().plus(dailyTtl)),
                (current, next) -> current.isExpired() ? next : new Entry<>(current.value() + 1, next.expiresAt()));
    }

    @Override
    public void saveVerifiedToken(String tokenHash, String phoneNumber, VerificationPurpose purpose, Duration ttl) {
        verifiedTokens.put(tokenHash, new Entry<>(verifiedValue(phoneNumber, purpose), Instant.now().plus(ttl)));
    }

    @Override
    public boolean consumeVerifiedToken(String tokenHash, String phoneNumber, VerificationPurpose purpose) {
        Entry<String> entry = verifiedTokens.get(tokenHash);
        if (entry == null || entry.isExpired() || !entry.value().equals(verifiedValue(phoneNumber, purpose))) {
            return false;
        }
        verifiedTokens.remove(tokenHash);
        return true;
    }

    @Override
    public void clear(String phoneNumber) {
        cooldowns.remove(phoneNumber);
        dailyCounts.keySet().removeIf(key -> key.startsWith(phoneNumber + ":"));
    }

    private String dailyKey(String phoneNumber, LocalDate date) {
        return phoneNumber + ":" + date;
    }

    private String verifiedValue(String phoneNumber, VerificationPurpose purpose) {
        return phoneNumber + "|" + purpose.name();
    }

    private record Entry<T>(T value, Instant expiresAt) {
        private boolean isExpired() {
            return !expiresAt.isAfter(Instant.now());
        }
    }
}
