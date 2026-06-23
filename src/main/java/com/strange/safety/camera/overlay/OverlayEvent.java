package com.strange.safety.camera.overlay;

public record OverlayEvent(
        String type,
        Double confidence,
        Long trackingId,
        BoundingBox boundingBox
) {
}
