package com.strange.safety.vlm.mock;

import java.time.LocalDateTime;
import java.util.List;

public record MockSemanticSearchResultResponse(
        long alertEventId,
        long cameraId,
        String cameraLoginId,
        String scenarioType,
        String severity,
        LocalDateTime detectedAt,
        String vlmDescription,
        String vlmJson,
        double similarityScore,
        List<String> keyframeUrls
) {
    public MockSemanticSearchResultResponse {
        keyframeUrls = List.copyOf(keyframeUrls);
    }

    static MockSemanticSearchResultResponse from(SearchResult result) {
        Incident incident = result.incident();
        String scenarioType = incident.events().isEmpty()
                ? IncidentEventType.NORMAL.name()
                : incident.events().getFirst().eventType().name();
        return new MockSemanticSearchResultResponse(
                stablePositiveId(incident.incidentId()),
                stablePositiveId(incident.cameraLoginId()),
                incident.cameraLoginId(),
                scenarioType,
                incident.status().name(),
                incident.detectedAt(),
                result.document().documentText(),
                "{\"mock\":true,\"incidentId\":\"" + incident.incidentId() + "\"}",
                result.similarityScore(),
                List.of()
        );
    }

    private static long stablePositiveId(String value) {
        return Integer.toUnsignedLong(value.hashCode());
    }
}
