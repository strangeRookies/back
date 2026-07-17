-- Add durable, independently scheduled retry state for VLM analysis and embedding.
-- Safe to re-run on PostgreSQL.
ALTER TABLE alert_event_descriptions
    ADD COLUMN IF NOT EXISTS next_attempt_at timestamp,
    ADD COLUMN IF NOT EXISTS embedding_status varchar(20),
    ADD COLUMN IF NOT EXISTS embedding_retry_count integer,
    ADD COLUMN IF NOT EXISTS embedding_max_retries integer,
    ADD COLUMN IF NOT EXISTS embedding_locked_until timestamp,
    ADD COLUMN IF NOT EXISTS embedding_next_attempt_at timestamp,
    ADD COLUMN IF NOT EXISTS embedding_error_message text;

UPDATE alert_event_descriptions
   SET embedding_status = CASE
           WHEN description_embedding IS NOT NULL AND description_embedding <> '' THEN 'SUCCESS'
           ELSE 'PENDING'
       END
 WHERE embedding_status IS NULL;

UPDATE alert_event_descriptions
   SET embedding_retry_count = 0
 WHERE embedding_retry_count IS NULL;

UPDATE alert_event_descriptions
   SET embedding_max_retries = max_retries
 WHERE embedding_max_retries IS NULL;

ALTER TABLE alert_event_descriptions
    ALTER COLUMN embedding_status SET DEFAULT 'PENDING',
    ALTER COLUMN embedding_status SET NOT NULL,
    ALTER COLUMN embedding_retry_count SET DEFAULT 0,
    ALTER COLUMN embedding_retry_count SET NOT NULL,
    ALTER COLUMN embedding_max_retries SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_alert_descriptions_vlm_retry_eligibility
    ON alert_event_descriptions (status, next_attempt_at, alert_event_description_id);

CREATE INDEX IF NOT EXISTS idx_alert_descriptions_embedding_retry_eligibility
    ON alert_event_descriptions (embedding_status, embedding_next_attempt_at, alert_event_description_id);
