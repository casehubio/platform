-- Add columns for credential resolution + rotation
ALTER TABLE authn_static_credential
    ADD COLUMN id VARCHAR(36),
    ADD COLUMN credential_hash VARCHAR(64),
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN expires_at TIMESTAMP;
