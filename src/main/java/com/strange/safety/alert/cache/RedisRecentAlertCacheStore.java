package com.strange.safety.alert.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.strange.safety.alert.dto.AlertEventResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class RedisRecentAlertCacheStore implements RecentAlertCacheStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRecentAlertCacheStore.class);
    private static final String KEY_PREFIX = "alerts:recent:facility:";
    private static final Duration RECENT_WINDOW = Duration.ofMinutes(10);
    private static final Duration KEY_TTL = Duration.ofMinutes(15);
    private static final int RECENT_LIMIT = 100;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void add(Long facilityId, AlertEventResponse alert) {
        if (facilityId == null || alert == null || alert.getDetectedAt() == null) {
            return;
        }

        String key = key(facilityId);
        long now = System.currentTimeMillis();
        long cutoff = now - RECENT_WINDOW.toMillis();
        try {
            String value = objectMapper.writeValueAsString(alert);
            double score = toEpochMillis(alert.getDetectedAt());
            redisTemplate.opsForZSet().add(key, value, score);
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, cutoff);
            redisTemplate.expire(key, KEY_TTL);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to cache recent alert: facilityId={}, alertEventId={}",
                    facilityId, alert.getAlertEventId(), ex);
        }
    }

    @Override
    public List<AlertEventResponse> findRecent(Long facilityId) {
        if (facilityId == null) {
            return Collections.emptyList();
        }

        long now = System.currentTimeMillis();
        long cutoff = now - RECENT_WINDOW.toMillis();
        Set<String> values = redisTemplate.opsForZSet()
                .reverseRangeByScore(key(facilityId), cutoff, now, 0, RECENT_LIMIT);
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        List<AlertEventResponse> alerts = new ArrayList<>();
        for (String value : values) {
            try {
                alerts.add(objectMapper.readValue(value, AlertEventResponse.class));
            } catch (JsonProcessingException ex) {
                log.warn("Failed to read recent alert cache entry: facilityId={}", facilityId, ex);
            }
        }
        return alerts;
    }

    private String key(Long facilityId) {
        return KEY_PREFIX + facilityId;
    }

    private long toEpochMillis(LocalDateTime dateTime) {
        return dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}
