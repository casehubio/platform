CREATE TABLE subscription (
    id              VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id         VARCHAR(255) NOT NULL,
    tenancy_id      VARCHAR(255) NOT NULL,
    name            VARCHAR(500) NOT NULL,
    event_type      VARCHAR(500) NOT NULL,
    filters_json    TEXT,
    template_json    TEXT NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL
);

CREATE INDEX idx_subscription_user_tenant_enabled
    ON subscription (user_id, tenancy_id, enabled, created_at DESC);

CREATE INDEX idx_subscription_enabled
    ON subscription (enabled) WHERE enabled = TRUE;
