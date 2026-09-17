-- Consolidated initial schema for delivery tracking.
-- Replaces V3000–V3003 incremental migrations (no production database exists).

CREATE TABLE IF NOT EXISTS delivery_attempt (
    id                VARCHAR(36) NOT NULL PRIMARY KEY,
    source_id         VARCHAR(255),
    source_type       VARCHAR(30) NOT NULL,
    channel_id        VARCHAR(255) NOT NULL,
    user_id           VARCHAR(255) NOT NULL,
    tenancy_id        VARCHAR(255) NOT NULL,
    delivery_type     VARCHAR(20) NOT NULL,
    status            VARCHAR(20) NOT NULL,
    attempt_count     INTEGER NOT NULL DEFAULT 0,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    last_attempted_at TIMESTAMP WITH TIME ZONE,
    delivered_at      TIMESTAMP WITH TIME ZONE,
    next_retry_at     TIMESTAMP WITH TIME ZONE,
    failure_reason    TEXT,
    payload           TEXT NOT NULL,
    first_opened_at   TIMESTAMP WITH TIME ZONE,
    first_clicked_at  TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_delivery_attempt_retry
    ON delivery_attempt (status, next_retry_at);

CREATE INDEX IF NOT EXISTS idx_delivery_attempt_source
    ON delivery_attempt (source_id, source_type);

CREATE INDEX IF NOT EXISTS idx_delivery_attempt_user
    ON delivery_attempt (user_id, tenancy_id, created_at);

CREATE TABLE engagement_event (
    id          VARCHAR(36) NOT NULL PRIMARY KEY,
    attempt_id  VARCHAR(36) REFERENCES delivery_attempt(id) ON DELETE SET NULL,
    source_id   VARCHAR(255),
    source_type VARCHAR(30) NOT NULL,
    channel_id  VARCHAR(255) NOT NULL,
    user_id     VARCHAR(255) NOT NULL,
    tenancy_id  VARCHAR(255) NOT NULL,
    type        VARCHAR(20) NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    metadata    TEXT
);

CREATE INDEX idx_engagement_event_attempt ON engagement_event (attempt_id);
CREATE INDEX idx_engagement_event_source ON engagement_event (source_id, source_type);
CREATE INDEX idx_engagement_event_user ON engagement_event (user_id, tenancy_id, type, recorded_at);
