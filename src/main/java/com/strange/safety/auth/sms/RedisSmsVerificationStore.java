package com.strange.safety.auth.sms;

import com.strange.safety.auth.entity.VerificationPurpose;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class RedisSmsVerificationStore implements SmsVerificationStore {

    private static final String VERIFIED_KEY_PREFIX = "auth:sms:verified:";
    private static final String COOLDOWN_KEY_PREFIX = "auth:sms:cooldown:";
    private static final String DAILY_KEY_PREFIX = "auth:sms:daily:";
    private static final String VALUE_DELIMITER = "|";
    private static final DateTimeFormatter DAILY_KEY_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean isCooldownActive(String phoneNumber) {
        Boolean exists = redisTemplate.hasKey(cooldownKey(phoneNumber));
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public long dailySentCount(String phoneNumber, LocalDate date) {
        String count = redisTemplate.opsForValue().get(dailyKey(phoneNumber, date));
        return count == null ? 0L : Long.parseLong(count);
    }

    @Override
    public void recordSent(String phoneNumber, Duration cooldownTtl, LocalDate date, Duration dailyTtl) {
        if (!cooldownTtl.isZero() && !cooldownTtl.isNegative()) {
            redisTemplate.opsForValue().set(cooldownKey(phoneNumber), "1", cooldownTtl);
        }
        Long count = redisTemplate.opsForValue().increment(dailyKey(phoneNumber, date));
        if (count != null && count == 1L) {
            redisTemplate.expire(dailyKey(phoneNumber, date), dailyTtl);
        }
    }

    @Override
    public void saveVerifiedToken(String tokenHash, String phoneNumber, VerificationPurpose purpose, Duration ttl) {
        redisTemplate.opsForValue().set(verifiedKey(tokenHash), phoneNumber + VALUE_DELIMITER + purpose.name(), ttl);
    }

    @Override
    public boolean consumeVerifiedToken(String tokenHash, String phoneNumber, VerificationPurpose purpose) {
        String key = verifiedKey(tokenHash);
        String value = redisTemplate.opsForValue().get(key);
        if (!verifiedValue(phoneNumber, purpose).equals(value)) {
            return false;
        }
        redisTemplate.delete(key);
        return true;
    }

    @Override
    public void clear(String phoneNumber) {
        redisTemplate.delete(cooldownKey(phoneNumber));
        redisTemplate.delete(dailyKey(phoneNumber, LocalDate.now()));
    }

    private String verifiedKey(String tokenHash) {
        return VERIFIED_KEY_PREFIX + tokenHash;
    }

    private String cooldownKey(String phoneNumber) {
        return COOLDOWN_KEY_PREFIX + phoneNumber;
    }

    private String dailyKey(String phoneNumber, LocalDate date) {
        return DAILY_KEY_PREFIX + phoneNumber + ":" + date.format(DAILY_KEY_DATE_FORMAT);
    }

    private String verifiedValue(String phoneNumber, VerificationPurpose purpose) {
        return phoneNumber + VALUE_DELIMITER + purpose.name();
    }
}
