CREATE TABLE authn_identity_binding (
    provider         VARCHAR(100) NOT NULL,
    external_id      VARCHAR(500) NOT NULL,
    tenancy_id       VARCHAR(255) NOT NULL,
    actor_id         VARCHAR(255) NOT NULL,
    email            VARCHAR(500),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (provider, external_id, tenancy_id)
);

CREATE INDEX idx_authn_identity_binding_tenant_actor ON authn_identity_binding(tenancy_id, actor_id);

CREATE TABLE authn_oauth_token (
    actor_id         VARCHAR(255) NOT NULL,
    provider         VARCHAR(100) NOT NULL,
    tenancy_id       VARCHAR(255) NOT NULL,
    access_token     TEXT NOT NULL,
    refresh_token    TEXT,
    granted_scopes_json TEXT,
    expires_at       TIMESTAMP WITH TIME ZONE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (actor_id, provider, tenancy_id)
);
