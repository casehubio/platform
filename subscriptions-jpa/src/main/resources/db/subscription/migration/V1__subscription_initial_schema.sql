-- Consolidated initial schema for subscriptions.
-- Replaces V1–V3 incremental migrations (no production database exists).

CREATE TABLE subscription (
    id            VARCHAR(36) NOT NULL PRIMARY KEY,
    owner_id      VARCHAR(255) NOT NULL,
    tenancy_id    VARCHAR(255) NOT NULL,
    name          VARCHAR(500) NOT NULL,
    event_type    VARCHAR(500) NOT NULL,
    filters_json  TEXT,
    targets_json  TEXT NOT NULL,
    template_json TEXT NOT NULL,
    include_actor BOOLEAN NOT NULL DEFAULT FALSE,
    scope         VARCHAR(10) NOT NULL DEFAULT 'USER'
                  CONSTRAINT chk_subscription_scope CHECK (scope IN ('USER', 'SYSTEM')),
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL
);

CREATE INDEX idx_subscription_owner_tenant_enabled
    ON subscription (owner_id, tenancy_id, enabled, created_at DESC);

CREATE INDEX idx_subscription_enabled
    ON subscription (enabled) WHERE enabled = TRUE;

CREATE INDEX idx_subscription_scope_tenant
    ON subscription (tenancy_id) WHERE scope = 'SYSTEM';
