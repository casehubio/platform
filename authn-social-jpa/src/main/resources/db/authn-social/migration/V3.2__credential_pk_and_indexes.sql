-- Complete PK migration and add indexes (runs after V3.1 Java backfill)
ALTER TABLE authn_static_credential ALTER COLUMN id SET NOT NULL;
ALTER TABLE authn_static_credential ALTER COLUMN credential_hash SET NOT NULL;
ALTER TABLE authn_static_credential DROP CONSTRAINT authn_static_credential_pkey;
ALTER TABLE authn_static_credential ADD PRIMARY KEY (id);

ALTER TABLE authn_static_credential DROP COLUMN credential;

CREATE INDEX idx_static_cred_hash ON authn_static_credential(credential_hash);
CREATE INDEX idx_static_cred_actor ON authn_static_credential(actor_id, provider, tenancy_id);
