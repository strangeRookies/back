package com.strange.safety.corporatecamera.dto;

import com.strange.safety.camera.entity.CameraConnectionStatus;
import com.strange.safety.camera.entity.CameraStatus;
import com.strange.safety.corporatecamera.entity.CorporateCamera;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Builder
public class CorporateCameraResponse {

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

    public static CorporateCameraResponse from(CorporateCamera camera) {
        return CorporateCameraResponse.builder()
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
                .build();
    }
}
