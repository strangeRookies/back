package com.strange.safety.camera.overlay;

import com.fasterxml.jackson.annotation.JsonAlias;

public record OverlayEvent(
        String type,
        Double confidence,
        @JsonAlias("track_id")
        Long trackingId,
        @JsonAlias("bbox")
        BoundingBox boundingBox
) {
}
