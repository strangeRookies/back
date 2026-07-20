package com.strange.safety.vlm.embedding;

import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.vlm.entity.AlertEventDescription;
import com.strange.safety.vlm.entity.EmbeddingJobStatus;
import com.strange.safety.vlm.entity.VlmJobStatus;
import com.strange.safety.vlm.entity.VlmSourceType;
import com.strange.safety.vlm.repository.AlertEventDescriptionRepository;
import com.strange.safety.vlm.service.EmbeddingService;
import com.strange.safety.vlm.service.VlmRetryMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmbeddingJobCompletionServiceTest {

    @Test
    void rateLimitCompletesClaimWithIndependentDelayedRetry() {
        AlertEventDescriptionRepository repository = mock(AlertEventDescriptionRepository.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        VlmRetryMetrics metrics = mock(VlmRetryMetrics.class);
        EmbeddingJobCompletionService service = new EmbeddingJobCompletionService(
                repository,
                embeddingService,
                mock(PgVectorProjectionWriter.class),
                metrics
        );
        ReflectionTestUtils.setField(service, "retryBaseDelaySeconds", 30L);
        ReflectionTestUtils.setField(service, "retryMaxDelaySeconds", 300L);
        AlertEventDescription job = job();
        job.markEmbeddingProcessing(LocalDateTime.now().plusMinutes(2));
        when(repository.findById(9L)).thenReturn(Optional.of(job));

        service.markFailed(9L, "HTTP 429", true);

        assertEquals(VlmJobStatus.SUCCESS, job.getStatus());
        assertEquals(0, job.getRetryCount());
        assertEquals(EmbeddingJobStatus.PENDING, job.getEmbeddingStatus());
        assertEquals(1, job.getEmbeddingRetryCount());
        assertNotNull(job.getEmbeddingNextAttemptAt());
        verify(metrics).recordEmbeddingRateLimited();
        verify(repository).save(job);
    }

    private AlertEventDescription job() {
        AlertEventDescription job = AlertEventDescription.builder()
                .alertEvent(mock(AlertEvent.class))
                .sourceAssetType(VlmSourceType.CLIP)
                .sourceAssetKey("clip.mp4")
                .promptVersion("v1")
                .vlmModelName("gemini")
                .maxRetries(3)
                .build();
        job.markSuccess("{}", "description", null, null, null, false);
        return job;
    }
}
