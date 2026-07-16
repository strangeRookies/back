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

@Service
@RequiredArgsConstructor
public class VlmClipJobCompletionService {

    private final AlertEventDescriptionRepository repository;
    private final ObjectMapper objectMapper;
    private final PgVectorSearchRepository pgVectorRepository;

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
        AlertEventDescription job = repository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("VLM job not found: " + jobId));
        job.markFailed(errorMessage);
        repository.save(job);
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