-- Digest buffer store (platform#158)

CREATE TABLE IF NOT EXISTS digest_buffer (
    id                UUID NOT NULL PRIMARY KEY,
    user_id           VARCHAR(255) NOT NULL,
    tenancy_id        VARCHAR(255) NOT NULL,
    channel_id        VARCHAR(255) NOT NULL,
    notification_json TEXT NOT NULL,
    buffered_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_digest_buffer_key
    ON digest_buffer (user_id, tenancy_id, channel_id);
