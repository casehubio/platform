CREATE TABLE registration_invitation (
    id            VARCHAR(255) NOT NULL,
    token_hash    VARCHAR(64)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    tenancy_id    VARCHAR(255) NOT NULL,
    assigned_groups TEXT,
    expires_at    TIMESTAMP,
    created_by    VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    consumed      BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE (token_hash)
);
