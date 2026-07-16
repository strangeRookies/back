package com.strange.safety.auth.session;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("!test & !local-h2")
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String TOKEN_KEY_PREFIX = "auth:refresh:";
    private static final String USER_KEY_PREFIX = "auth:user-refresh:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(Long userId, String tokenHash, Duration ttl) {
        redisTemplate.opsForValue().set(tokenKey(tokenHash), String.valueOf(userId), ttl);
        redisTemplate.opsForSet().add(userKey(userId), tokenHash);
        redisTemplate.expire(userKey(userId), ttl);
    }

    @Override
    public Optional<RefreshTokenSession> findByTokenHash(String tokenHash) {
        String userId = redisTemplate.opsForValue().get(tokenKey(tokenHash));
        if (!StringUtils.hasText(userId)) {
            return Optional.empty();
        }
        return Optional.of(new RefreshTokenSession(Long.valueOf(userId), tokenHash));
    }

    @Override
    public void revoke(String tokenHash) {
        findByTokenHash(tokenHash).ifPresent(session ->
                redisTemplate.opsForSet().remove(userKey(session.userId()), tokenHash));
        redisTemplate.delete(tokenKey(tokenHash));
    }

    @Override
    public void revokeAllByUserId(Long userId) {
        String userKey = userKey(userId);
        Set<String> tokenHashes = redisTemplate.opsForSet().members(userKey);
        if (tokenHashes != null && !tokenHashes.isEmpty()) {
            redisTemplate.delete(tokenHashes.stream().map(this::tokenKey).toList());
        }
        redisTemplate.delete(userKey);
    }

    private String tokenKey(String tokenHash) {
        return TOKEN_KEY_PREFIX + tokenHash;
    }

    private String userKey(Long userId) {
        return USER_KEY_PREFIX + userId;
    }
}
