CREATE TABLE authn_static_credential (
    actor_id     VARCHAR(255) NOT NULL,
    tenancy_id   VARCHAR(255) NOT NULL,
    provider     VARCHAR(255) NOT NULL,
    credential   TEXT         NOT NULL,
    type         VARCHAR(50)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    PRIMARY KEY (actor_id, provider, tenancy_id)
);
