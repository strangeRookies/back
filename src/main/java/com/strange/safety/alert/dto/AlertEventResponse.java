package com.strange.safety.alert.dto;

import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.alert.entity.AlertSeverity;
import com.strange.safety.alert.entity.AlertStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;

@Getter
@Builder
@Jacksonized
public class AlertEventResponse {

    private Long alertEventId;
    private String eventId;
    private Long cameraId;
    private Long scenarioId;
    private String scenarioType;
    private Float confidenceScore;
    private AlertSeverity severity;
    private AlertStatus status;
    private LocalDateTime detectedAt;
    private String clipUrl;
    private LocalDateTime acknowledgedAt;
    private Long acknowledgedBy;
    private LocalDateTime createdAt;
    private String cameraLoginId;
    private String snapshotUrl;
    private String vlmDescription;
    private String summaryKo;
    private String reason;

    private static final String DEFAULT_VLM_REASON = "1명이 바닥 가까이에 누운 자세로 감지되었고, 쓰러짐 신호가 연속 확인되어 쓰러짐 의심 이벤트가 발생했습니다.";

    public static AlertEventResponse from(AlertEvent event) {
        return from(event, null, event.getClipUrl());
    }

    public static AlertEventResponse from(AlertEvent event, String snapshotUrl) {
        return from(event, snapshotUrl, event.getClipUrl());
    }

    public static AlertEventResponse from(AlertEvent event, String snapshotUrl, String clipUrl) {
        return AlertEventResponse.builder()
                .alertEventId(event.getId())
                .eventId(event.getEventId())
                .cameraId(event.getCamera() != null ? event.getCamera().getId() : (event.getCorporateCamera() != null ? event.getCorporateCamera().getId() : null))
                .cameraLoginId(event.getCamera() != null ? event.getCamera().getCameraLoginId() : (event.getCorporateCamera() != null ? event.getCorporateCamera().getCameraLoginId() : null))
                .scenarioId(event.getScenario().getId())
                .scenarioType(event.getScenario().getScenarioType().name())
                .confidenceScore(event.getConfidenceScore())
                .severity(event.getSeverity())
                .status(event.getStatus())
                .detectedAt(event.getDetectedAt())
                .clipUrl(clipUrl)
                .acknowledgedAt(event.getAcknowledgedAt())
                .acknowledgedBy(event.getAcknowledgedBy() != null ? event.getAcknowledgedBy().getId() : null)
                .createdAt(event.getCreatedAt())
                .snapshotUrl(snapshotUrl)
                .vlmDescription(DEFAULT_VLM_REASON)
                .summaryKo(DEFAULT_VLM_REASON)
                .reason(DEFAULT_VLM_REASON)
                .build();
    }
}
