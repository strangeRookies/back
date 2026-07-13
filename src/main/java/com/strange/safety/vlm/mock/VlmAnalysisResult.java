package com.strange.safety.vlm.mock;

import java.util.List;
import java.util.Objects;

public record VlmAnalysisResult(
        String schemaVersion,
        String incidentId,
        String summary,
        String observedAction,
        String preEventActivity,
        String postEventPosture,
        String movementAfterEvent,
        boolean recoveryObserved,
        double estimatedLyingDurationSec,
        String riskLevel,
        PersonDescription personDescription,
        List<String> uncertainty,
        List<Integer> evidenceFrameIndexes
) {
    public VlmAnalysisResult {
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(personDescription, "personDescription");
        uncertainty = List.copyOf(uncertainty);
        evidenceFrameIndexes = List.copyOf(evidenceFrameIndexes);
    }
}
