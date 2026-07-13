package com.strange.safety.vlm.pgvector;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ANN-style cosine search via pgvector when enabled.
 * Default {@link com.strange.safety.vlm.service.SemanticSearchService} still ranks in-memory;
 * switch call sites here after RDS has the extension + column.
 */
@Repository
@RequiredArgsConstructor
public class PgVectorSearchRepository {

    private final JdbcTemplate jdbcTemplate;

    @Value("${vlm.pgvector-search-enabled:false}")
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public List<PgVectorHit> search(double[] queryEmbedding, int topK) {
        if (!enabled) {
            throw new IllegalStateException("vlm.pgvector-search-enabled=false");
        }
        String literal = Arrays.stream(queryEmbedding)
                .mapToObj(Double::toString)
                .collect(Collectors.joining(",", "[", "]"));
        int limit = Math.max(1, Math.min(topK, 50));
        return jdbcTemplate.query(
                """
                        SELECT alert_event_description_id AS id,
                               1 - (description_embedding_vec <=> CAST(? AS vector)) AS similarity
                        FROM alert_event_descriptions
                        WHERE status = 'SUCCESS'
                          AND description_embedding_vec IS NOT NULL
                        ORDER BY description_embedding_vec <=> CAST(? AS vector)
                        LIMIT ?
                        """,
                (rs, rowNum) -> new PgVectorHit(rs.getLong("id"), rs.getDouble("similarity")),
                literal,
                literal,
                limit
        );
    }

    public record PgVectorHit(long descriptionId, double similarity) {
    }
}
