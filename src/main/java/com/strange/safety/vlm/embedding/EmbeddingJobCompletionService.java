package com.strange.safety.vlm.embedding;

import com.strange.safety.vlm.entity.AlertEventDescription;
import com.strange.safety.vlm.repository.AlertEventDescriptionRepository;
import com.strange.safety.vlm.service.EmbeddingService;
import com.strange.safety.vlm.service.VlmRetryMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EmbeddingJobCompletionService {
    private final AlertEventDescriptionRepository repository;
    private final EmbeddingService embeddingService;
    private final PgVectorProjectionWriter pgVectorProjectionWriter;
    private final VlmRetryMetrics retryMetrics;

    @Value("${vlm.embedding-retry-base-delay-seconds:${vlm.retry-base-delay-seconds:60}}")
    private long retryBaseDelaySeconds;

    @Value("${vlm.embedding-retry-max-delay-seconds:${vlm.retry-max-delay-seconds:3600}}")
    private long retryMaxDelaySeconds;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(long jobId, double[] vector) {
        AlertEventDescription job = findJob(jobId);
        pgVectorProjectionWriter.projectIfEnabled(jobId, vector);
        job.markEmbeddingSuccess(
                embeddingService.encode(vector),
                embeddingService.embeddingModelName()
        );
        repository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(long jobId, String errorMessage, boolean rateLimited) {
        AlertEventDescription job = findJob(jobId);
        if (rateLimited) {
            retryMetrics.recordEmbeddingRateLimited();
        }
        job.markEmbeddingFailed(errorMessage,
                LocalDateTime.now().plus(retryDelay(job.getEmbeddingRetryCount())));
        repository.save(job);
    }

    Duration retryDelay(int completedRetries) {
        long multiplier = 1L << Math.min(Math.max(completedRetries, 0), 30);
        long delaySeconds = Math.min(retryMaxDelaySeconds, retryBaseDelaySeconds * multiplier);
        return Duration.ofSeconds(delaySeconds);
    }

    private AlertEventDescription findJob(long jobId) {
        return repository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Embedding job not found: " + jobId));
    }
}
