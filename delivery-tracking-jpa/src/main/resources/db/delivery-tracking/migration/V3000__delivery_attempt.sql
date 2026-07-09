-- Delivery attempt tracking (platform#154)

CREATE TABLE IF NOT EXISTS delivery_attempt (
    id                VARCHAR(36) NOT NULL PRIMARY KEY,
    notification_id   VARCHAR(36),
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
    payload           TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_delivery_attempt_retry
    ON delivery_attempt (status, next_retry_at);

CREATE INDEX IF NOT EXISTS idx_delivery_attempt_notification
    ON delivery_attempt (notification_id);

CREATE INDEX IF NOT EXISTS idx_delivery_attempt_user
    ON delivery_attempt (user_id, tenancy_id, created_at);
