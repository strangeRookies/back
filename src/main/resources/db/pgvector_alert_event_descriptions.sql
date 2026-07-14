-- Smart Safety VLM — pgvector schema (apply on AWS RDS / local Postgres when ready)
-- Not auto-run by Spring in this MVP; keep as operational SQL.

CREATE EXTENSION IF NOT EXISTS vector;

-- Parallel column next to legacy text embedding (comma-separated) for gradual cutover.
ALTER TABLE alert_event_descriptions
    ADD COLUMN IF NOT EXISTS description_embedding_vec vector(768);

CREATE INDEX IF NOT EXISTS idx_alert_event_descriptions_embedding_vec
    ON alert_event_descriptions
    USING ivfflat (description_embedding_vec vector_cosine_ops)
    WITH (lists = 100);

-- Example ANN search (bind :query_vec as string '[0.1,0.2,...]'):
-- SELECT alert_event_description_id,
--        1 - (description_embedding_vec <=> CAST(:query_vec AS vector)) AS similarity
-- FROM alert_event_descriptions
-- WHERE status = 'SUCCESS'
--   AND description_embedding_vec IS NOT NULL
-- ORDER BY description_embedding_vec <=> CAST(:query_vec AS vector)
-- LIMIT :top_k;
