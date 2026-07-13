ALTER TABLE subscription ADD COLUMN scope VARCHAR(10) NOT NULL DEFAULT 'USER'
    CONSTRAINT chk_subscription_scope CHECK (scope IN ('USER', 'SYSTEM'));

CREATE INDEX idx_subscription_scope_tenant
    ON subscription (tenancy_id) WHERE scope = 'SYSTEM';
