package com.strange.safety.push.dto;

import com.strange.safety.push.entity.PushPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterPushDeviceRequest(
        @NotBlank @Size(max = 512) String token,
        @Size(max = 255) String deviceId,
        @NotNull PushPlatform platform,
        @Size(max = 50) String appVersion
) {
}
