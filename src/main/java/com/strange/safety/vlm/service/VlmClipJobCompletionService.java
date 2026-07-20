package com.strange.safety.vlm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.strange.safety.vlm.dto.VlmIndexPayload;
import com.strange.safety.vlm.entity.AlertEventDescription;
import com.strange.safety.vlm.repository.AlertEventDescriptionRepository;
import com.strange.safety.vlm.repository.PgVectorSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class VlmClipJobCompletionService {

    private final AlertEventDescriptionRepository repository;
    private final ObjectMapper objectMapper;
    private final PgVectorSearchRepository pgVectorRepository;
    private final VlmRetryMetrics retryMetrics;

    @Value("${vlm.retry-base-delay-seconds:60}")
    private long retryBaseDelaySeconds;

    @Value("${vlm.retry-max-delay-seconds:3600}")
    private long retryMaxDelaySeconds;

    @Value("${vlm.pgvector.enabled:${VLM_PGVECTOR_ENABLED:false}}")
    private boolean pgVectorEnabled;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(long jobId, VlmIndexPayload payload) throws IOException {
        AlertEventDescription job = repository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("VLM job not found: " + jobId));
        String encodedEmbedding = encode(payload.search().embedding());
        if (pgVectorEnabled) {
            pgVectorRepository.project(job.getId(), encodedEmbedding);
        }
        job.markSuccess(
                objectMapper.writeValueAsString(payload.vlmResult()),
                payload.search().document(),
                encodedEmbedding,
                null,
                payload.search().embeddingModel(),
                payload.vlmResult().path("is_mock").booleanValue()
        );
        repository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(long jobId, String errorMessage) {
        markFailed(jobId, errorMessage, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(long jobId, String errorMessage, boolean rateLimited) {
        AlertEventDescription job = repository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("VLM job not found: " + jobId));
        if (rateLimited) {
            retryMetrics.recordVlmRateLimited();
        }
        job.markFailed(errorMessage, LocalDateTime.now().plus(retryDelay(job.getRetryCount())));
        repository.save(job);
    }

    Duration retryDelay(int completedRetries) {
        long multiplier = 1L << Math.min(Math.max(completedRetries, 0), 30);
        long delaySeconds = Math.min(retryMaxDelaySeconds, retryBaseDelaySeconds * multiplier);
        return Duration.ofSeconds(delaySeconds);
    }

    private String encode(java.util.List<Double> embedding) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < embedding.size(); index += 1) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(embedding.get(index));
        }
        return builder.toString();
    }
}