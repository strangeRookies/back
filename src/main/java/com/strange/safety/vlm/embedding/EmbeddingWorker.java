package com.strange.safety.vlm.embedding;

import com.strange.safety.vlm.entity.AlertEventDescription;
import com.strange.safety.vlm.entity.VlmJobStatus;
import com.strange.safety.vlm.repository.AlertEventDescriptionRepository;
import com.strange.safety.vlm.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    private final PgVectorProjectionWriter pgVectorProjectionWriter;

    @Value("${vlm.embedding-worker-enabled:false}")
    private boolean enabled;

    @Value("${vlm.batch-size:2}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${vlm.embedding-worker-delay-ms:30000}")
    @Transactional
    public void reembedIfNeeded() {
        if (!enabled) {
            return;
        }
        if (!embeddingService.canEmbed()) {
            log.debug("EmbeddingWorker skipped: provider unavailable");
            return;
        }
        // SUCCESS rows with empty embedding — defensive; repository may return empty until query exists.
        List<AlertEventDescription> rows = repository.findAll(PageRequest.of(0, batchSize)).getContent()
                .stream()
                .filter(row -> row.getStatus() == VlmJobStatus.SUCCESS)
                .filter(row -> row.getVlmDescription() != null && !row.getVlmDescription().isBlank())
                .filter(row -> row.getDescriptionEmbedding() == null || row.getDescriptionEmbedding().isBlank())
                .toList();
        for (AlertEventDescription row : rows) {
            double[] vector = embeddingService.embed(row.getVlmDescription());
            row.markSuccess(
                    row.getVlmJson(),
                    row.getVlmDescription(),
                    embeddingService.encode(vector),
                    row.getDeidentifiedKeyframeKeys() == null ? "" : row.getDeidentifiedKeyframeKeys(),
                    embeddingService.embeddingModelName(),
                    row.isMockResult()
            );
            pgVectorProjectionWriter.projectIfEnabled(row.getId(), vector);
            log.info("EmbeddingWorker updated description id={}", row.getId());
        }
    }
}
