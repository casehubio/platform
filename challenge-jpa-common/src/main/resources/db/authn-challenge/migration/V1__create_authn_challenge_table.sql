CREATE TABLE authn_challenge (
    challenge_id    VARCHAR(255)                NOT NULL PRIMARY KEY,
    method          VARCHAR(100)                NOT NULL,
    tenancy_id      VARCHAR(255)                NOT NULL,
    challenge_data  BYTEA,
    created_at      TIMESTAMP WITH TIME ZONE    NOT NULL,
    expires_at      TIMESTAMP WITH TIME ZONE    NOT NULL
);

CREATE INDEX idx_authn_challenge_expires ON authn_challenge(expires_at);
