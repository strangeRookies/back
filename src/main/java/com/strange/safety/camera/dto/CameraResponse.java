package com.strange.safety.camera.dto;

import com.strange.safety.camera.entity.Camera;
import com.strange.safety.camera.entity.CameraConnectionStatus;
import com.strange.safety.camera.entity.CameraStatus;
import com.strange.safety.camera.entity.CameraSourceType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Builder
public class CameraResponse {

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
    private CameraSourceType sourceType;
    private String assignedVideoPath;
    private CameraConnectionStatus connectionStatus;
    private Instant lastConnectionReportAt;

    public static CameraResponse from(Camera camera) {
        return CameraResponse.builder()
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
                .sourceType(camera.getSourceType())
                .assignedVideoPath(camera.getAssignedVideoPath())
                .connectionStatus(camera.getConnectionStatus())
                .lastConnectionReportAt(camera.getLastConnectionReportAt())
                .build();
    }
}
