package com.strange.safety.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

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

        String source
) {
}

