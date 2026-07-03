package com.strange.safety.camera.overlay;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OverlayEvent(
        String type,
        Double confidence,
        Boolean eventTriggered,
        @JsonAlias({"trackId", "track_id"})
        Long trackingId,
        BoundingBox bbox,
        BoundingBox boundingBox,
        List<?> keypoints,
        Long frameId,
        @JsonAlias("display_id")
        Long displayId,
        String displayLabel
) {
    public OverlayEvent {
        if (bbox == null) {
            bbox = boundingBox;
        }
        if (boundingBox == null) {
            boundingBox = bbox;
        }
        keypoints = keypoints == null ? null : List.copyOf(keypoints);
    }

    public OverlayEvent(
            String type,
            Double confidence,
            Boolean eventTriggered,
            Long trackingId,
            BoundingBox bbox,
            BoundingBox boundingBox,
            List<?> keypoints
    ) {
        this(type, confidence, eventTriggered, trackingId, bbox, boundingBox, keypoints, null, null, null);
    }

    public OverlayEvent(String type, Double confidence, Long trackingId, BoundingBox boundingBox) {
        this(type, confidence, null, trackingId, boundingBox, boundingBox, null, null, null, null);
    }

    public OverlayEvent(
            String type,
            Double confidence,
            Boolean eventTriggered,
            Long trackingId,
            BoundingBox bbox,
            BoundingBox boundingBox,
            List<?> keypoints,
            Long frameId) {
        this(type, confidence, eventTriggered, trackingId, bbox, boundingBox, keypoints, frameId, null, null);
    }

    public BoundingBox resolvedBoundingBox() {
        return bbox != null ? bbox : boundingBox;
    }
}
