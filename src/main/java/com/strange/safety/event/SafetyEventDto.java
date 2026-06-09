package com.strange.safety.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record SafetyEventDto(
        @JsonAlias({"type", "event_type"})
        String type,

        @JsonProperty("camera_id")
        @JsonAlias({"camera_id", "cameraId"})
        String cameraId,

        @JsonAlias({"timestamp", "detected_at"})
        Instant timestamp,

        String severity,

        String message,

        String source,

        Float confidence,

        List<Number> bbox,

        @JsonProperty("track_id")
        @JsonAlias({"track_id", "trackId"})
        String trackId
) {
}

