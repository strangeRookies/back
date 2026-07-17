package com.strange.safety.vlm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.vlm.entity.AlertEventDescription;
import com.strange.safety.vlm.entity.VlmJobStatus;
import com.strange.safety.vlm.entity.VlmSourceType;
import com.strange.safety.vlm.repository.AlertEventDescriptionRepository;
import com.strange.safety.vlm.repository.PgVectorSearchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VlmClipJobCompletionServiceRetryTest {

    @Test
    void rateLimitSchedulesExponentialBackoffAndIncrementsOnlyVlmMetric() {
        AlertEventDescriptionRepository repository = mock(AlertEventDescriptionRepository.class);
        VlmRetryMetrics metrics = mock(VlmRetryMetrics.class);
        VlmClipJobCompletionService service = new VlmClipJobCompletionService(
                repository,
                new ObjectMapper(),
                mock(PgVectorSearchRepository.class),
                metrics
        );
        ReflectionTestUtils.setField(service, "retryBaseDelaySeconds", 30L);
        ReflectionTestUtils.setField(service, "retryMaxDelaySeconds", 120L);
        AlertEventDescription job = job();
        when(repository.findById(7L)).thenReturn(Optional.of(job));
        LocalDateTime before = LocalDateTime.now();

        service.markFailed(7L, "ai_worker_transient_http_429", true);

        assertEquals(VlmJobStatus.PENDING, job.getStatus());
        assertEquals(1, job.getRetryCount());
        assertTrue(job.getNextAttemptAt().isAfter(before.plusSeconds(29)));
        verify(metrics).recordVlmRateLimited();
        verify(repository).save(job);
        assertEquals(Duration.ofSeconds(30), service.retryDelay(0));
        assertEquals(Duration.ofSeconds(60), service.retryDelay(1));
        assertEquals(Duration.ofSeconds(120), service.retryDelay(8));
    }

    private AlertEventDescription job() {
        return AlertEventDescription.builder()
                .alertEvent(mock(AlertEvent.class))
                .sourceAssetType(VlmSourceType.CLIP)
                .sourceAssetKey("clip.mp4")
                .promptVersion("v1")
                .vlmModelName("gemini")
                .maxRetries(4)
                .build();
    }
}
