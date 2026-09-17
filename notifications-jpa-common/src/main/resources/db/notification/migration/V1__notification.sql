-- Notification store table (platform#135)

CREATE TABLE IF NOT EXISTS notification (
    id                VARCHAR(36)  NOT NULL,
    user_id           VARCHAR(255) NOT NULL,
    tenancy_id        VARCHAR(64)  NOT NULL,
    title             VARCHAR(500) NOT NULL,
    body              TEXT,
    category          VARCHAR(255) NOT NULL,
    severity          VARCHAR(20)  NOT NULL,
    action_url        VARCHAR(2000),
    source_event_id   VARCHAR(255) NOT NULL,
    source_entity_type VARCHAR(255) NOT NULL,
    source_entity_id  VARCHAR(255) NOT NULL,
    source_actor_id   VARCHAR(255) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    read_at           TIMESTAMP WITH TIME ZONE,
    dismissed_at      TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);

-- Primary query: unread notifications newest first
CREATE INDEX IF NOT EXISTS idx_notification_user_status_created
    ON notification (user_id, tenancy_id, status, created_at DESC);

-- Filtered views by category
CREATE INDEX IF NOT EXISTS idx_notification_user_category_created
    ON notification (user_id, tenancy_id, category, created_at DESC);

-- Pagination and retention cleanup
CREATE INDEX IF NOT EXISTS idx_notification_user_created
    ON notification (user_id, tenancy_id, created_at);
