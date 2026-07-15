package com.strange.safety.vlm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmbeddingServiceTest {

    @Test
    void exposesNormalizedRealModelIdentityWhileKeepingRawRequestModel() {
        EmbeddingService service = new EmbeddingService(new ObjectMapper());
        ReflectionTestUtils.setField(service, "mockMode", false);
        ReflectionTestUtils.setField(service, "queryEmbeddingModel", "text-embedding-004");

        assertEquals("gemini-text-embedding-004", service.embeddingModelName());
    }

    @Test
    void exposesStableMockModelIdentity() {
        EmbeddingService service = new EmbeddingService(new ObjectMapper());
        ReflectionTestUtils.setField(service, "mockMode", true);
        ReflectionTestUtils.setField(service, "queryEmbeddingModel", "text-embedding-004");

        assertEquals("mock-vlm-index-768", service.embeddingModelName());
    }
}
