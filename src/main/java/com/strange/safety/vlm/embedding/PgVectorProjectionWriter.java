package com.strange.safety.vlm.embedding;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Optional write path into {@code description_embedding_vec}.
 * Requires SQL in {@code db/pgvector_alert_event_descriptions.sql} applied first.
 * Disabled by default so local DBs without the extension keep working.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PgVectorProjectionWriter {

    private final JdbcTemplate jdbcTemplate;

    @Value("${vlm.pgvector-write-enabled:false}")
    private boolean enabled;

    public void projectIfEnabled(Long descriptionId, double[] vector) {
        if (!enabled || descriptionId == null || vector == null || vector.length == 0) {
            return;
        }
        String literal = Arrays.stream(vector)
                .mapToObj(Double::toString)
                .collect(Collectors.joining(",", "[", "]"));
        try {
            jdbcTemplate.update(
                    "UPDATE alert_event_descriptions SET description_embedding_vec = CAST(? AS vector) WHERE alert_event_description_id = ?",
                    literal,
                    descriptionId
            );
        } catch (Exception ex) {
            // Soft-fail: keep text embedding path usable if extension/column missing.
            log.warn("pgvector project failed for id={}: {}", descriptionId, ex.getMessage());
        }
    }
}
