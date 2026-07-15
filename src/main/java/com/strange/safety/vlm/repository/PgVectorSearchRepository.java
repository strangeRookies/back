package com.strange.safety.vlm.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class PgVectorSearchRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PgVectorSearchRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void project(long descriptionId, String encodedEmbedding) {
        int updated = jdbcTemplate.getJdbcTemplate().update("""
                update alert_event_descriptions
                   set description_embedding_vector = cast(? as vector)
                 where alert_event_description_id = ?
                """, toVectorLiteral(encodedEmbedding), descriptionId);
        if (updated != 1) {
            throw new IllegalStateException("VLM vector projection did not update exactly one row");
        }
    }

    public List<ScoredId> searchFacility(long facilityId, String queryEmbedding, String embeddingModel,
                                         double minSimilarity, int topK, LocalDateTime dateFrom,
                                         LocalDateTime dateTo, Long cameraId, boolean excludeMock) {
        return search("""
                join cameras c on c.camera_id = e.camera_id
                where c.facility_id = :scopeId
                """, facilityId, queryEmbedding, embeddingModel, minSimilarity, topK,
                dateFrom, dateTo, cameraId, excludeMock);
    }

    public List<ScoredId> searchCompany(long companyProfileId, String queryEmbedding, String embeddingModel,
                                        double minSimilarity, int topK, LocalDateTime dateFrom,
                                        LocalDateTime dateTo, Long cameraId, boolean excludeMock) {
        return search("""
                join corporate_cameras c on c.camera_id = e.corporate_camera_id
                where c.company_profile_id = :scopeId
                """, companyProfileId, queryEmbedding, embeddingModel, minSimilarity, topK,
                dateFrom, dateTo, cameraId, excludeMock);
    }

    private List<ScoredId> search(String scopeJoin, long scopeId, String queryEmbedding,
                                  String embeddingModel, double minSimilarity, int topK,
                                  LocalDateTime dateFrom, LocalDateTime dateTo,
                                  Long cameraId, boolean excludeMock) {
        StringBuilder sql = new StringBuilder("""
                select d.alert_event_description_id,
                       1 - (d.description_embedding_vector <=> cast(:embedding as vector)) as similarity
                  from alert_event_descriptions d
                  join alert_events e on e.alert_event_id = d.alert_event_id
                """).append(scopeJoin).append("""
                   and d.status = 'SUCCESS'
                   and d.description_embedding_vector is not null
                   and d.embedding_model_name = :embeddingModel
                """);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("scopeId", scopeId)
                .addValue("embedding", toVectorLiteral(queryEmbedding))
                .addValue("embeddingModel", embeddingModel)
                .addValue("minSimilarity", minSimilarity)
                .addValue("topK", topK)
                .addValue("excludeMock", excludeMock);
        if (dateFrom != null) {
            sql.append(" and e.detected_at >= :dateFrom\n");
            parameters.addValue("dateFrom", dateFrom);
        }
        if (dateTo != null) {
            sql.append(" and e.detected_at <= :dateTo\n");
            parameters.addValue("dateTo", dateTo);
        }
        if (cameraId != null) {
            sql.append(" and c.camera_id = :cameraId\n");
            parameters.addValue("cameraId", cameraId);
        }
        sql.append("""
                   and (:excludeMock = false or d.mock_result = false)
                   and 1 - (d.description_embedding_vector <=> cast(:embedding as vector)) >= :minSimilarity
                 order by d.description_embedding_vector <=> cast(:embedding as vector), d.alert_event_description_id
                 limit :topK
                """);
        return jdbcTemplate.query(sql.toString(), parameters, (rs, rowNum) -> new ScoredId(
                rs.getLong("alert_event_description_id"), rs.getDouble("similarity")));
    }

    private String toVectorLiteral(String encodedEmbedding) {
        return "[" + encodedEmbedding + "]";
    }

    public record ScoredId(long id, double similarity) {
    }
}
