package com.strange.safety.alert.dto;

import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.alert.entity.AlertSeverity;
import com.strange.safety.alert.entity.AlertStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class AlertEventDetailResponse {

    private Long alertEventId;
    private Long cameraId;
    private Long scenarioId;
    private String scenarioType;
    private Float confidenceScore;
    private AlertSeverity severity;
    private AlertStatus status;
    private String keypointData;
    private String boundingBoxData;
    private String clipUrl;
    private String clipPath;
    private Double faintProb;
    private LocalDateTime detectedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime acknowledgedAt;
    private Long acknowledgedBy;
    private List<SnapshotResponse> snapshots;
    private String vlmDescription;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private static final String DEFAULT_VLM_REASON = "1명이 바닥 가까이에 누운 자세로 감지되었고, 쓰러짐 신호가 연속 확인되어 쓰러짐 의심 이벤트가 발생했습니다.";

    public static AlertEventDetailResponse from(AlertEvent event, List<SnapshotResponse> snapshots,
                                                String vlmDescription) {
        return from(event, snapshots, vlmDescription, event.getClipUrl());
    }

    public static AlertEventDetailResponse from(AlertEvent event, List<SnapshotResponse> snapshots,
                                                String vlmDescription, String clipUrl) {
        String effectiveVlm = (vlmDescription == null || vlmDescription.isBlank()) ? DEFAULT_VLM_REASON : vlmDescription;
        return AlertEventDetailResponse.builder()
                .alertEventId(event.getId())
                .cameraId(event.getCamera() != null ? event.getCamera().getId() : (event.getCorporateCamera() != null ? event.getCorporateCamera().getId() : null))
                .scenarioId(event.getScenario().getId())
                .scenarioType(event.getScenario().getScenarioType().name())
                .confidenceScore(event.getConfidenceScore())
                .severity(event.getSeverity())
                .status(event.getStatus())
                .keypointData(event.getKeypointData())
                .boundingBoxData(event.getBoundingBoxData())
                .clipUrl(clipUrl)
                .clipPath(event.getClipPath())
                .faintProb(event.getFaintProb())
                .detectedAt(event.getDetectedAt())
                .resolvedAt(event.getResolvedAt())
                .acknowledgedAt(event.getAcknowledgedAt())
                .acknowledgedBy(event.getAcknowledgedBy() != null ? event.getAcknowledgedBy().getId() : null)
                .snapshots(snapshots)
                .vlmDescription(effectiveVlm)
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
