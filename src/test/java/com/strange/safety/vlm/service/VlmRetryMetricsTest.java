package com.strange.safety.vlm.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VlmRetryMetricsTest {

    @Test
    void recordsVlmAndEmbeddingRateLimitsSeparately() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        VlmRetryMetrics metrics = new VlmRetryMetrics(registry);

        metrics.recordVlmRateLimited();
        metrics.recordEmbeddingRateLimited();
        metrics.recordEmbeddingRateLimited();

        assertEquals(1.0, registry.get("vlm.analysis.http.429").counter().count());
        assertEquals(2.0, registry.get("vlm.embedding.http.429").counter().count());
    }
}
