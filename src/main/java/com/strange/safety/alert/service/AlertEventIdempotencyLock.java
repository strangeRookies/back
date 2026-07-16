package com.strange.safety.alert.service;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Serializes inserts for the same external eventId across backend instances.
 * PostgreSQL transaction advisory locks are released automatically on commit/rollback.
 */
@Component
public class AlertEventIdempotencyLock {

    private final JdbcTemplate jdbcTemplate;

    public AlertEventIdempotencyLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void acquire(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        long lockKey = UUID.nameUUIDFromBytes(eventId.trim().getBytes(StandardCharsets.UTF_8))
                .getMostSignificantBits();
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            String product = connection.getMetaData().getDatabaseProductName();
            if (!"PostgreSQL".equalsIgnoreCase(product)) {
                return null;
            }
            try (var statement = connection.prepareStatement("select pg_advisory_xact_lock(?)")) {
                statement.setLong(1, lockKey);
                statement.execute();
            }
            return null;
        });
    }
}
