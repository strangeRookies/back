package com.strange.safety.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record SafetyEventDto(
        @NotBlank
        @JsonProperty("type")
        String type,

        @NotBlank
        @JsonProperty("camera_id")
        String cameraId,

        @NotNull
        @JsonProperty("timestamp")
        Instant timestamp,

        @NotBlank
        @JsonProperty("severity")
        String severity,

        @NotBlank
        @JsonProperty("message")
        String message,

        @JsonProperty("source")
        String source
) {
}
