package com.strange.safety.vlm.mock;

import java.time.LocalDateTime;
import java.util.Set;

public record SearchRequest(
        String query,
        Set<String> cameraLoginIds,
        Set<IncidentEventType> eventTypes,
        Set<IncidentStatus> incidentStatuses,
        LocalDateTime from,
        LocalDateTime to,
        int topK,
        double minSimilarity
) {
    public SearchRequest {
        cameraLoginIds = Set.copyOf(cameraLoginIds);
        eventTypes = Set.copyOf(eventTypes);
        incidentStatuses = Set.copyOf(incidentStatuses);
    }
}
