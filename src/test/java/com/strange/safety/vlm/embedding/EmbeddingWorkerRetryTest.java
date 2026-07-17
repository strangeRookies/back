package com.strange.safety.vlm.embedding;

import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.vlm.entity.AlertEventDescription;
import com.strange.safety.vlm.entity.VlmSourceType;
import com.strange.safety.vlm.repository.AlertEventDescriptionRepository;
import com.strange.safety.vlm.service.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmbeddingWorkerRetryTest {

    @Test
    void embedding429SchedulesOnlyEmbeddingRetryAndMetric() {
        AlertEventDescriptionRepository repository = mock(AlertEventDescriptionRepository.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        EmbeddingJobClaimService claimService = mock(EmbeddingJobClaimService.class);
        EmbeddingJobCompletionService completionService = mock(EmbeddingJobCompletionService.class);
        EmbeddingWorker worker = new EmbeddingWorker(
                repository,
                embeddingService,
                claimService,
                completionService
        );
        ReflectionTestUtils.setField(worker, "enabled", true);
        ReflectionTestUtils.setField(worker, "batchSize", 2);
        AlertEventDescription row = rowAwaitingEmbedding();
        when(embeddingService.canEmbed()).thenReturn(true);
        when(claimService.claimJobIds(any(LocalDateTime.class), eq(2))).thenReturn(List.of(9L));
        when(repository.findById(9L)).thenReturn(java.util.Optional.of(row));
        when(embeddingService.embed("description"))
                .thenThrow(new EmbeddingService.EmbeddingProviderException(429));

        worker.reembedIfNeeded();

        verify(claimService).claimJobIds(any(LocalDateTime.class), eq(2));
        verify(completionService).markFailed(
                9L, "Query embedding provider returned HTTP 429", true);
    }

    private AlertEventDescription rowAwaitingEmbedding() {
        AlertEventDescription row = AlertEventDescription.builder()
                .alertEvent(mock(AlertEvent.class))
                .sourceAssetType(VlmSourceType.CLIP)
                .sourceAssetKey("clip.mp4")
                .promptVersion("v1")
                .vlmModelName("gemini")
                .maxRetries(3)
                .build();
        ReflectionTestUtils.setField(row, "id", 9L);
        row.markSuccess("{}", "description", null, null, null, false);
        return row;
    }
}
