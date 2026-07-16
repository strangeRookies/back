package com.strange.safety.auth.security;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!test & !local-h2")
@RequiredArgsConstructor
public class RedisLoginAttemptStore implements LoginAttemptStore {

    private static final String KEY_PREFIX = "auth:login-fail:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean isLocked(String email, int maxFailures) {
        String failures = redisTemplate.opsForValue().get(key(email));
        return failures != null && Long.parseLong(failures) >= maxFailures;
    }

    @Override
    public long recordFailure(String email, Duration lockTtl) {
        Long failures = redisTemplate.opsForValue().increment(key(email));
        redisTemplate.expire(key(email), lockTtl);
        return failures == null ? 1L : failures;
    }

    @Override
    public void clear(String email) {
        redisTemplate.delete(key(email));
    }

    private String key(String email) {
        return KEY_PREFIX + email;
    }
}
