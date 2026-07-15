package com.strange.safety.vlm.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;

public record VlmIndexPayload(
        String schemaVersion,
        String incidentId,
        String cameraLoginId,
        OffsetDateTime capturedAt,
        JsonNode vlmResult,
        Search search
) {
    public record Search(
            String document,
            List<String> keywords,
            String embeddingModel,
            int embeddingDimension,
            List<Double> embedding
    ) {
    }
}
