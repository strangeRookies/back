package com.strange.safety.push.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeletePushDeviceRequest(
        @NotBlank @Size(max = 512) String token
) {
}
