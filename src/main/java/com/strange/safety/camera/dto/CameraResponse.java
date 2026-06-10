package com.strange.safety.camera.dto;

import com.strange.safety.camera.entity.Camera;
import com.strange.safety.camera.entity.CameraStatus;
import lombok.Builder;
import lombok.Getter;

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
                .build();
    }
}
