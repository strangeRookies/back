package com.strange.safety.vlm.entity;

import com.strange.safety.alert.entity.AlertEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AlertEventDescriptionRetryTest {

    @Test
    void vlmAndEmbeddingRetriesHaveIndependentStateAndSchedules() {
        AlertEventDescription job = job(3);
        LocalDateTime vlmRetryAt = LocalDateTime.of(2026, 7, 17, 12, 1);

        job.markFailed("vlm 429", vlmRetryAt);

        assertEquals(1, job.getRetryCount());
        assertEquals(VlmJobStatus.PENDING, job.getStatus());
        assertSame(vlmRetryAt, job.getNextAttemptAt());
        assertEquals(0, job.getEmbeddingRetryCount());
        assertEquals(EmbeddingJobStatus.PENDING, job.getEmbeddingStatus());

        job.markSuccess("{}", "description", null, null, null, false);
        LocalDateTime embeddingRetryAt = LocalDateTime.of(2026, 7, 17, 12, 2);
        job.markEmbeddingFailed("embedding 429", embeddingRetryAt);

        assertEquals(VlmJobStatus.SUCCESS, job.getStatus());
        assertEquals(1, job.getRetryCount());
        assertNull(job.getNextAttemptAt());
        assertEquals(EmbeddingJobStatus.PENDING, job.getEmbeddingStatus());
        assertEquals(1, job.getEmbeddingRetryCount());
        assertSame(embeddingRetryAt, job.getEmbeddingNextAttemptAt());
    }

    @Test
    void exhaustedRetriesClearDelayedEligibilityPerStage() {
        AlertEventDescription job = job(1);
        LocalDateTime retryAt = LocalDateTime.now().plusMinutes(1);

        job.markFailed("failed", retryAt);

        assertEquals(VlmJobStatus.FAILED, job.getStatus());
        assertNull(job.getNextAttemptAt());

        job.markSuccess("{}", "description", null, null, null, false);
        job.markEmbeddingFailed("failed", retryAt);

        assertEquals(EmbeddingJobStatus.FAILED, job.getEmbeddingStatus());
        assertNull(job.getEmbeddingNextAttemptAt());
        assertTrue(job.getDescriptionEmbedding() == null || job.getDescriptionEmbedding().isBlank());
    }

    private AlertEventDescription job(int maxRetries) {
        return AlertEventDescription.builder()
                .alertEvent(mock(AlertEvent.class))
                .sourceAssetType(VlmSourceType.CLIP)
                .sourceAssetKey("clip.mp4")
                .promptVersion("v1")
                .vlmModelName("gemini")
                .maxRetries(maxRetries)
                .build();
    }
}
