package com.strange.safety.corporatecamera.dto;

import com.strange.safety.camera.entity.CameraConnectionStatus;
import com.strange.safety.camera.entity.CameraStatus;
import com.strange.safety.camera.overlay.AiOverlayResponse;
import com.strange.safety.corporatecamera.entity.CorporateCamera;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Builder
public class CorporateCameraResponse {

    private static final String OVERLAY_STREAM_TYPE_MJPEG = "MJPEG";

    private Long cameraId;
    private Long companyProfileId;
    private String cameraName;
    private String cameraSerialNumber;
    private String rtspUrl;
    private String locationDescription;
    private String cameraLoginId;
    private boolean passwordSet;
    private CameraStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String assignedVideoPath;
    private CameraConnectionStatus connectionStatus;
    private Instant lastConnectionReportAt;
    private String overlayUrl;
    private String overlayStreamType;
    private boolean overlayRenderedInStream;
    @com.fasterxml.jackson.annotation.JsonProperty("isCorporate")
    private boolean isCorporate;
    private java.util.List<com.strange.safety.camera.dto.CameraResponse.RoiConfigEntry> roiConfigs;

    public static CorporateCameraResponse from(CorporateCamera camera) {
        return from(camera, null);
    }

    public static CorporateCameraResponse from(CorporateCamera camera, AiOverlayResponse overlay) {
        CorporateCameraResponseBuilder builder = CorporateCameraResponse.builder()
                .cameraId(camera.getId())
                .companyProfileId(camera.getCompanyProfile().getId())
                .cameraName(camera.getCameraName())
                .cameraSerialNumber(camera.getCameraSerialNumber())
                .rtspUrl(camera.getRtspUrl())
                .locationDescription(camera.getLocationDescription())
                .cameraLoginId(camera.getCameraLoginId())
                .passwordSet(camera.getCameraPasswordEncrypted() != null)
                .status(camera.getStatus())
                .createdAt(camera.getCreatedAt())
                .updatedAt(camera.getUpdatedAt())
                .assignedVideoPath(camera.getAssignedVideoPath())
                .connectionStatus(camera.getConnectionStatus())
                .lastConnectionReportAt(camera.getLastConnectionReportAt())
                .isCorporate(true)
                .overlayRenderedInStream(false);
        
        if (camera.getRoiConfigs() != null) {
            builder.roiConfigs(camera.getRoiConfigs().stream()
                    .filter(com.strange.safety.camera.entity.RoiConfig::isActive)
                    .map(roi -> com.strange.safety.camera.dto.CameraResponse.RoiConfigEntry.builder()
                            .roiConfigId(roi.getId())
                            .scenarioId(roi.getScenario().getId())
                            .scenarioType(roi.getScenario().getScenarioType().name())
                            .polygonPoints(roi.getPolygonPoints())
                            .build())
                    .toList());
        } else {
            builder.roiConfigs(java.util.List.of());
        }
        applyOverlay(builder, overlay);
        return builder.build();
    }

    private static void applyOverlay(CorporateCameraResponseBuilder builder, AiOverlayResponse overlay) {
        if (overlay == null || overlay.overlayUrl() == null) {
            return;
        }
        builder.overlayUrl(overlay.overlayUrl())
                .overlayStreamType(OVERLAY_STREAM_TYPE_MJPEG)
                .overlayRenderedInStream(true);
    }
}
