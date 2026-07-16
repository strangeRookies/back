package com.strange.safety.camera.dto;

import com.strange.safety.camera.entity.Camera;
import com.strange.safety.camera.entity.CameraConnectionStatus;
import com.strange.safety.camera.entity.CameraStatus;
import com.strange.safety.camera.entity.RoiConfig;
import com.strange.safety.camera.overlay.AiOverlayResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class CameraResponse {

    private static final String OVERLAY_STREAM_TYPE_MJPEG = "MJPEG";

    private Long cameraId;
    private Long facilityId;
    private String cameraLoginId;
    private String cameraName;
    private String cameraSerialNumber;
    private String rtspUrl;
    private CameraStatus status;
    private String locationDescription;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean aiEnabled;
    private String assignedVideoPath;
    private CameraConnectionStatus connectionStatus;
    private Instant lastConnectionReportAt;
    private List<RoiConfigEntry> roiConfigs;
    private String overlayUrl;
    private String overlayStreamType;
    private boolean overlayRenderedInStream;
    
    @com.fasterxml.jackson.annotation.JsonProperty("isCorporate")
    private boolean isCorporate;

    @Getter
    @Builder
    public static class RoiConfigEntry {
        private Long roiConfigId;
        private Long scenarioId;
        private String scenarioType;
        private String polygonPoints;
    }

    public static CameraResponse from(Camera camera) {
        return from(camera, null);
    }

    public static CameraResponse from(Camera camera, AiOverlayResponse overlay) {
        return fromWithRoi(camera, List.of(), overlay);
    }

    public static CameraResponse fromWithRoi(Camera camera, List<RoiConfig> activeRois) {
        return fromWithRoi(camera, activeRois, null);
    }

    public static CameraResponse fromWithRoi(Camera camera, List<RoiConfig> activeRois, AiOverlayResponse overlay) {
        CameraResponseBuilder builder = CameraResponse.builder()
                .cameraId(camera.getId())
                .facilityId(camera.getFacility().getId())
                .cameraLoginId(camera.getCameraLoginId())
                .cameraName(camera.getCameraName())
                .cameraSerialNumber(camera.getCameraSerialNumber())
                .rtspUrl(camera.getRtspUrl())
                .status(camera.getStatus())
                .locationDescription(camera.getLocationDescription())
                .createdAt(camera.getCreatedAt())
                .updatedAt(camera.getUpdatedAt())
                .aiEnabled(camera.isAiEnabled())
                .assignedVideoPath(camera.getAssignedVideoPath())
                .connectionStatus(camera.getConnectionStatus())
                .lastConnectionReportAt(camera.getLastConnectionReportAt())
                .roiConfigs(activeRois.stream()
                        .map(roi -> RoiConfigEntry.builder()
                                .roiConfigId(roi.getId())
                                .scenarioId(roi.getScenario().getId())
                                .scenarioType(roi.getScenario().getScenarioType().name())
                                .polygonPoints(roi.getPolygonPoints())
                                .build())
                        .toList())
                .overlayRenderedInStream(false)
                .isCorporate(false);        applyOverlay(builder, overlay);
        return builder.build();
    }

    public static CameraResponse fromCorporate(com.strange.safety.corporatecamera.entity.CorporateCamera camera) {
        return fromCorporate(camera, null);
    }

    public static CameraResponse fromCorporate(
            com.strange.safety.corporatecamera.entity.CorporateCamera camera,
            AiOverlayResponse overlay) {
        List<RoiConfig> activeRois = camera.getRoiConfigs() != null ?
                camera.getRoiConfigs().stream().filter(RoiConfig::isActive).toList() : List.of();
        CameraResponseBuilder builder = CameraResponse.builder()
                .cameraId(camera.getId())
                .facilityId(camera.getCompanyProfile().getId())
                .cameraLoginId(camera.getCameraLoginId())
                .cameraName(camera.getCameraName())
                .cameraSerialNumber(camera.getCameraSerialNumber())
                .rtspUrl(camera.getRtspUrl())
                .status(camera.getStatus())
                .locationDescription(camera.getLocationDescription())
                .createdAt(camera.getCreatedAt())
                .updatedAt(camera.getUpdatedAt())
                .aiEnabled(true)
                .assignedVideoPath(camera.getAssignedVideoPath())
                .connectionStatus(camera.getConnectionStatus())
                .lastConnectionReportAt(camera.getLastConnectionReportAt())
                .roiConfigs(activeRois.stream()
                        .map(roi -> RoiConfigEntry.builder()
                                .roiConfigId(roi.getId())
                                .scenarioId(roi.getScenario().getId())
                                .scenarioType(roi.getScenario().getScenarioType().name())
                                .polygonPoints(roi.getPolygonPoints())
                                .build())
                        .toList())
                .overlayRenderedInStream(false)
                .isCorporate(true);
        applyOverlay(builder, overlay);
        return builder.build();
    }

    private static void applyOverlay(CameraResponseBuilder builder, AiOverlayResponse overlay) {
        if (overlay == null || overlay.overlayUrl() == null) {
            return;
        }
        builder.overlayUrl(overlay.overlayUrl())
                .overlayStreamType(OVERLAY_STREAM_TYPE_MJPEG)
                .overlayRenderedInStream(true);
    }
}
