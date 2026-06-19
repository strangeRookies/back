package com.strange.safety.camera.overlay;

import java.time.Instant;

public record AiOverlayResponse(
        String cameraLoginId,
        String rtspUrl,
        Integer overlayPort,
        String overlayUrl,
        Long pid,
        AiOverlayStatus status,
        Instant updatedAt
) {
}
