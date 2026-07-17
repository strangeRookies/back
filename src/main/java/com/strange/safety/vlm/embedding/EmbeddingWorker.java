package com.strange.safety.vlm.embedding;

import com.strange.safety.vlm.entity.AlertEventDescription;
import com.strange.safety.vlm.repository.AlertEventDescriptionRepository;
import com.strange.safety.vlm.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Separate embedding pass for rows that already have VLM text but need (re)embedding.
 * Primary path still embeds inside {@code VlmProcessingScheduler} on success;
 * this worker covers retries / provider cutover without re-running VLM.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmbeddingWorker {

    private final AlertEventDescriptionRepository repository;
    private final EmbeddingService embeddingService;
    private final EmbeddingJobClaimService claimService;
    private final EmbeddingJobCompletionService completionService;

    @Value("${vlm.embedding-worker-enabled:false}")
    private boolean enabled;

    @Value("${vlm.batch-size:2}")
    private int batchSize;


    @Scheduled(fixedDelayString = "${vlm.embedding-worker-delay-ms:30000}")
    public void reembedIfNeeded() {
        if (!enabled) {
            return;
        }
        if (!embeddingService.canEmbed()) {
            log.debug("EmbeddingWorker skipped: provider unavailable");
            return;
        }

        List<Long> jobIds = claimService.claimJobIds(LocalDateTime.now(), batchSize);
        for (Long jobId : jobIds) {
            AlertEventDescription row = repository.findById(jobId).orElse(null);
            if (row == null) {
                continue;
            }
            try {
                double[] vector = embeddingService.embed(row.getVlmDescription());
                completionService.markSuccess(jobId, vector);
                log.info("EmbeddingWorker updated description id={}", jobId);
            } catch (RuntimeException ex) {
                boolean rateLimited = ex instanceof EmbeddingService.EmbeddingProviderException providerFailure
                        && providerFailure.isRateLimited();
                completionService.markFailed(jobId, safeFailure(ex), rateLimited);
                log.warn("EmbeddingWorker failed description id={} rateLimited={} reason={}",
                        jobId, rateLimited, safeFailure(ex));
            }
        }
    }

    private String safeFailure(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank()
                ? ex.getClass().getSimpleName()
                : message.substring(0, Math.min(message.length(), 500));
    }
}
