package com.strange.safety.camera.overlay;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OverlayMessage(
        String schemaVersion,
        String messageType,
        long timestampMs,
        String streamId,
        @JsonAlias("camera_login_id")
        String cameraLoginId,
        int frameWidth,
        int frameHeight,
        List<OverlayEvent> events
) {
    public OverlayMessage {
        events = events == null ? null : List.copyOf(events);
    }

    public String resolvedCameraLoginId() {
        if (cameraLoginId != null && !cameraLoginId.isBlank()) {
            return cameraLoginId;
        }
        return streamId;
    }

    public OverlayMessage withCameraLoginId(String matchedCameraLoginId) {
        return new OverlayMessage(
                schemaVersion,
                messageType,
                timestampMs,
                streamId,
                matchedCameraLoginId,
                frameWidth,
                frameHeight,
                events);
    }
}
