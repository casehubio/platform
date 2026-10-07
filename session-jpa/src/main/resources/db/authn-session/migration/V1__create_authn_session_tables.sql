CREATE TABLE authn_session (
    session_id         VARCHAR(255)                NOT NULL PRIMARY KEY,
    actor_id           VARCHAR(255)                NOT NULL,
    tenancy_id         VARCHAR(255)                NOT NULL,
    groups_json        TEXT,
    auth_method        VARCHAR(100)                NOT NULL,
    csrf_token         VARCHAR(255),
    created_at         TIMESTAMP WITH TIME ZONE    NOT NULL,
    expires_at         TIMESTAMP WITH TIME ZONE    NOT NULL,
    device_fingerprint VARCHAR(500)
);

CREATE INDEX idx_authn_session_tenant_actor ON authn_session(tenancy_id, actor_id);
CREATE INDEX idx_authn_session_expires      ON authn_session(expires_at);

CREATE TABLE authn_refresh_token (
    token       VARCHAR(500)                NOT NULL PRIMARY KEY,
    family_id   VARCHAR(255)                NOT NULL,
    actor_id    VARCHAR(255)                NOT NULL,
    tenancy_id  VARCHAR(255)                NOT NULL,
    groups_json TEXT,
    auth_method VARCHAR(100)                NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE    NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE    NOT NULL,
    consumed    BOOLEAN                     NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_authn_refresh_token_family       ON authn_refresh_token(family_id);
CREATE INDEX idx_authn_refresh_token_tenant_actor  ON authn_refresh_token(tenancy_id, actor_id);
CREATE INDEX idx_authn_refresh_token_expires        ON authn_refresh_token(expires_at);
