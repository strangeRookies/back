package com.strange.safety.camera.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.strange.safety.camera.entity.RoiConfig;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RoiConfigResponse {

    private Long roiConfigId;
    private Long cameraId;
    private Long scenarioId;
    private String scenarioType;
    private String polygonPoints;
    @JsonProperty("isActive")
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static RoiConfigResponse from(RoiConfig roiConfig) {
        Long cameraId = roiConfig.getCamera() != null ? 
                roiConfig.getCamera().getId() : 
                roiConfig.getCorporateCamera().getId();
        return RoiConfigResponse.builder()
                .roiConfigId(roiConfig.getId())
                .cameraId(cameraId)
                .scenarioId(roiConfig.getScenario().getId())
                .scenarioType(roiConfig.getScenario().getScenarioType().name())
                .polygonPoints(roiConfig.getPolygonPoints())
                .isActive(roiConfig.isActive())
                .createdAt(roiConfig.getCreatedAt())
                .updatedAt(roiConfig.getUpdatedAt())
                .build();
    }
}
