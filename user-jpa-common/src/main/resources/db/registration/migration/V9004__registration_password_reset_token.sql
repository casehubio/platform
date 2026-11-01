CREATE TABLE registration_password_reset_token (
    token_hash    VARCHAR(64)  NOT NULL,
    user_id       VARCHAR(255) NOT NULL,
    tenancy_id    VARCHAR(255) NOT NULL,
    expires_at    TIMESTAMP    NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    PRIMARY KEY (token_hash)
);
