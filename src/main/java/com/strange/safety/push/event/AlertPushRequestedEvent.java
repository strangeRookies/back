package com.strange.safety.push.event;

import java.time.Instant;

public record AlertPushRequestedEvent(
        Long alertEventId,
        Long cameraId,
        String cameraLoginId,
        String cameraName,
        String targetType,
        Long targetId,
        String scenarioType,
        String severity,
        Instant occurredAt
) {
}
