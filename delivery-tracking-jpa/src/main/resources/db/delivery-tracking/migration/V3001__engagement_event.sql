-- Engagement tracking (platform#170)

ALTER TABLE delivery_attempt ADD COLUMN first_opened_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE delivery_attempt ADD COLUMN first_clicked_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE engagement_event (
    id              VARCHAR(36) NOT NULL PRIMARY KEY,
    attempt_id      VARCHAR(36) NOT NULL REFERENCES delivery_attempt(id) ON DELETE CASCADE,
    notification_id VARCHAR(36),
    channel_id      VARCHAR(255) NOT NULL,
    user_id         VARCHAR(255) NOT NULL,
    tenancy_id      VARCHAR(255) NOT NULL,
    type            VARCHAR(20) NOT NULL,
    recorded_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    metadata        TEXT
);

CREATE INDEX idx_engagement_event_attempt ON engagement_event (attempt_id);
CREATE INDEX idx_engagement_event_notification ON engagement_event (notification_id);
CREATE INDEX idx_engagement_event_user ON engagement_event (user_id, tenancy_id, type, recorded_at);
