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

    public static AlertEventResponse from(AlertEvent event) {
        return AlertEventResponse.builder()
                .alertEventId(event.getId())
                .cameraId(event.getCamera().getId())
                .scenarioId(event.getScenario().getId())
                .scenarioType(event.getScenario().getScenarioType().name())
                .confidenceScore(event.getConfidenceScore())
                .severity(event.getSeverity())
                .status(event.getStatus())
                .detectedAt(event.getDetectedAt())
                .clipUrl(event.getClipUrl())
                .acknowledgedAt(event.getAcknowledgedAt())
                .acknowledgedBy(event.getAcknowledgedBy() != null ? event.getAcknowledgedBy().getId() : null)
                .createdAt(event.getCreatedAt())
                .build();
    }
}
