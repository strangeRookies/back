-- Run explicitly against PostgreSQL after Hibernate has created alert_event_descriptions.
-- Set VLM_PGVECTOR_ENABLED=true only after this script completes successfully.
CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE alert_event_descriptions
    ADD COLUMN IF NOT EXISTS description_embedding_vector vector(768);

UPDATE alert_event_descriptions
   SET description_embedding_vector = ('[' || description_embedding || ']')::vector
 WHERE description_embedding IS NOT NULL
   AND description_embedding_vector IS NULL
   AND array_length(string_to_array(description_embedding, ','), 1) = 768;

CREATE INDEX IF NOT EXISTS idx_alert_event_descriptions_embedding_hnsw
    ON alert_event_descriptions
    USING hnsw (description_embedding_vector vector_cosine_ops)
    WHERE status = 'SUCCESS' AND description_embedding_vector IS NOT NULL;
