package com.strange.safety.vlm.dto;

import com.strange.safety.alert.entity.AlertEvent;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class SemanticSearchResultResponse {
    private Long alertEventId;
    private Long cameraId;
    private String cameraLoginId;
    private String scenarioType;
    private String severity;
    private LocalDateTime detectedAt;
    private String vlmDescription;
    private String vlmJson;
    private double similarityScore;
    private List<String> keyframeUrls;
    private String snapshotUrl;

    public static SemanticSearchResultResponse from(AlertEvent event, String description,
                                                    String vlmJson, double similarityScore,
                                                    List<String> keyframeUrls, String snapshotUrl) {
        return SemanticSearchResultResponse.builder()
                .alertEventId(event.getId())
                .cameraId(event.getCamera() != null ? event.getCamera().getId() : event.getCorporateCamera().getId())
                .cameraLoginId(event.getCamera() != null
                        ? event.getCamera().getCameraLoginId()
                        : event.getCorporateCamera().getCameraLoginId())
                .scenarioType(event.getScenario().getScenarioType().name())
                .severity(event.getSeverity().name())
                .detectedAt(event.getDetectedAt())
                .vlmDescription(description)
                .vlmJson(vlmJson)
                .similarityScore(similarityScore)
                .keyframeUrls(keyframeUrls)
                .snapshotUrl(snapshotUrl)
                .build();
    }
}
