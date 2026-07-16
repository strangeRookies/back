package com.strange.safety.auth.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"test", "local-h2"})
public class InMemoryRefreshTokenStore implements RefreshTokenStore {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public void save(Long userId, String tokenHash, Duration ttl) {
        entries.put(tokenHash, new Entry(userId, Instant.now().plus(ttl)));
    }

    @Override
    public Optional<RefreshTokenSession> findByTokenHash(String tokenHash) {
        Entry entry = entries.get(tokenHash);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.expiresAt().isAfter(Instant.now())) {
            entries.remove(tokenHash);
            return Optional.empty();
        }
        return Optional.of(new RefreshTokenSession(entry.userId(), tokenHash));
    }

    @Override
    public void revoke(String tokenHash) {
        entries.remove(tokenHash);
    }

    @Override
    public void revokeAllByUserId(Long userId) {
        entries.entrySet().removeIf(entry -> entry.getValue().userId().equals(userId));
    }

    private record Entry(Long userId, Instant expiresAt) {
    }
}
