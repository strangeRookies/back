package com.strange.safety.camera.overlay;

import java.util.List;

public record OverlayMessage(
        String schemaVersion,
        String messageType,
        long timestampMs,
        String streamId,
        int frameWidth,
        int frameHeight,
        List<OverlayEvent> events
) {
    public OverlayMessage {
        events = events == null ? null : List.copyOf(events);
    }
}
