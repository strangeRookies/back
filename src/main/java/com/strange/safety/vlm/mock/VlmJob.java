package com.strange.safety.vlm.mock;

import java.util.Objects;

public record VlmJob(String jobId, Incident incident, MediaAsset mediaAsset, VlmAnalysisResult result) {
    public VlmJob {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(incident, "incident");
        Objects.requireNonNull(mediaAsset, "mediaAsset");
        Objects.requireNonNull(result, "result");
    }
}
