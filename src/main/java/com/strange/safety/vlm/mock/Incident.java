package com.strange.safety.vlm.mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record Incident(
        String incidentId,
        String originalEventId,
        String cameraLoginId,
        String locationName,
        IncidentStatus status,
        LocalDateTime detectedAt,
        List<IncidentEvent> events,
        List<MediaAsset> mediaAssets
) {
    public Incident {
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(originalEventId, "originalEventId");
        Objects.requireNonNull(cameraLoginId, "cameraLoginId");
        Objects.requireNonNull(locationName, "locationName");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(detectedAt, "detectedAt");
        events = List.copyOf(events);
        mediaAssets = List.copyOf(mediaAssets);
    }
}
