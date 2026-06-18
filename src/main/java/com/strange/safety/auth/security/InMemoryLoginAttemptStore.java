package com.strange.safety.auth.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class InMemoryLoginAttemptStore implements LoginAttemptStore {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public boolean isLocked(String email, int maxFailures) {
        Entry entry = currentEntry(email);
        return entry != null && entry.failures() >= maxFailures;
    }

    @Override
    public long recordFailure(String email, Duration lockTtl) {
        Instant expiresAt = Instant.now().plus(lockTtl);
        return entries.merge(email, new Entry(1L, expiresAt),
                (current, next) -> current.isExpired() ? next : new Entry(current.failures() + 1, expiresAt)
        ).failures();
    }

    @Override
    public void clear(String email) {
        entries.remove(email);
    }

    private Entry currentEntry(String email) {
        Entry entry = entries.get(email);
        if (entry != null && entry.isExpired()) {
            entries.remove(email);
            return null;
        }
        return entry;
    }

    private record Entry(long failures, Instant expiresAt) {
        private boolean isExpired() {
            return !expiresAt.isAfter(Instant.now());
        }
    }
}
