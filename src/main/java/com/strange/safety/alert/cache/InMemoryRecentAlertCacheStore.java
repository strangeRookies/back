package com.strange.safety.alert.cache;

import com.strange.safety.alert.dto.AlertEventResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class InMemoryRecentAlertCacheStore implements RecentAlertCacheStore {

    private static final Duration RECENT_WINDOW = Duration.ofMinutes(10);
    private static final int RECENT_LIMIT = 100;

    private final Map<String, List<AlertEventResponse>> alertsByContext = new ConcurrentHashMap<>();

    @Override
    public void add(String contextKey, AlertEventResponse alert) {
        alertsByContext.compute(contextKey, (key, current) -> {
            List<AlertEventResponse> alerts = current == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(current);
            alerts.add(alert);
            LocalDateTime cutoff = LocalDateTime.now().minus(RECENT_WINDOW);
            return alerts.stream()
                    .filter(item -> item.getDetectedAt() != null && !item.getDetectedAt().isBefore(cutoff))
                    .sorted(Comparator.comparing(AlertEventResponse::getDetectedAt).reversed())
                    .limit(RECENT_LIMIT)
                    .toList();
        });
    }

    @Override
    public List<AlertEventResponse> findRecent(String contextKey) {
        return alertsByContext.getOrDefault(contextKey, List.of());
    }
}
