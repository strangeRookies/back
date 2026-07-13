package com.strange.safety.vlm.mock;

import java.time.LocalDateTime;
import java.util.Objects;

public record IncidentEvent(
        String eventId,
        String originalEventId,
        String cameraLoginId,
        String trackId,
        IncidentEventType eventType,
        LocalDateTime occurredAt
) {
    public IncidentEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(originalEventId, "originalEventId");
        Objects.requireNonNull(cameraLoginId, "cameraLoginId");
        Objects.requireNonNull(trackId, "trackId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
