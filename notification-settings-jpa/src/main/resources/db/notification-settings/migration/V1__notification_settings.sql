-- Notification settings store (platform#143, #145)

CREATE TABLE IF NOT EXISTS notification_preferences (
    user_id               VARCHAR(255) NOT NULL,
    tenancy_id            VARCHAR(255) NOT NULL,
    channel_defaults_json TEXT,
    quiet_hours_json      TEXT,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id, tenancy_id)
);

CREATE TABLE IF NOT EXISTS mute_rules (
    id              VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id         VARCHAR(255) NOT NULL,
    tenancy_id      VARCHAR(255) NOT NULL,
    scope           VARCHAR(20) NOT NULL,
    scope_id        VARCHAR(500) NOT NULL,
    entity_type     VARCHAR(500),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at      TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_mute_rules_user_tenant
    ON mute_rules (user_id, tenancy_id);

CREATE TABLE IF NOT EXISTS snooze (
    user_id         VARCHAR(255) NOT NULL,
    tenancy_id      VARCHAR(255) NOT NULL,
    until_time      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id, tenancy_id)
);
