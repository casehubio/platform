CREATE TABLE authn_webauthn_credential (
    credential_id    VARCHAR(500) NOT NULL PRIMARY KEY,
    actor_id         VARCHAR(255) NOT NULL,
    tenancy_id       VARCHAR(255) NOT NULL,
    public_key_cose  BYTEA,
    sign_count       BIGINT NOT NULL DEFAULT 0,
    transports_json  TEXT,
    aaguid           VARCHAR(100),
    display_name     VARCHAR(500),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at     TIMESTAMP WITH TIME ZONE,
    discoverable     BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_authn_webauthn_tenant_actor ON authn_webauthn_credential(tenancy_id, actor_id);
