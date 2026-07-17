package com.strange.safety.vlm.snapshotassist;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SnapshotAssistContext(
        String eventId,
        String cameraLoginId,
        String eventType,
        String trackId,
        Double confidence,
        Double faintProbability,
        String lifecycleState,
        Integer consecutiveCount,
        String detectorReason,
        String capturedAt
) {
    public static SnapshotAssistContext empty(String eventId, String cameraLoginId) {
        return new SnapshotAssistContext(eventId, cameraLoginId, null, null, null, null, null, null, null, null);
    }
}