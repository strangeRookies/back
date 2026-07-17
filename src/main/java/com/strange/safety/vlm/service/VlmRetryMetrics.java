package com.strange.safety.vlm.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class VlmRetryMetrics {
    private final Counter vlmRateLimited;
    private final Counter embeddingRateLimited;

    public VlmRetryMetrics(MeterRegistry meterRegistry) {
        this.vlmRateLimited = Counter.builder("vlm.analysis.http.429")
                .description("Upstream HTTP 429 responses during VLM analysis")
                .register(meterRegistry);
        this.embeddingRateLimited = Counter.builder("vlm.embedding.http.429")
                .description("Upstream HTTP 429 responses during embedding")
                .register(meterRegistry);
    }

    public void recordVlmRateLimited() {
        vlmRateLimited.increment();
    }

    public void recordEmbeddingRateLimited() {
        embeddingRateLimited.increment();
    }
}
