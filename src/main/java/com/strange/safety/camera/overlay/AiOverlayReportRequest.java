package com.strange.safety.camera.overlay;

public record AiOverlayReportRequest(
        String cameraLoginId,
        String rtspUrl,
        Integer overlayPort,
        String overlayUrl,
        Long pid,
        AiOverlayStatus status
) {
}
