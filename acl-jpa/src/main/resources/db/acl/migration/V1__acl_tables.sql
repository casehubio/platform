-- ACL flat-grant tables for AccessControlProvider (platform#68)

CREATE SEQUENCE IF NOT EXISTS acl_entry_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS acl_entry (
    id          BIGINT       NOT NULL DEFAULT nextval('acl_entry_seq'),
    actor_id    VARCHAR(255) NOT NULL,
    resource_id VARCHAR(255) NOT NULL,
    action      VARCHAR(50)  NOT NULL,
    condition   TEXT,
    granted_at  TIMESTAMP    NOT NULL,
    expires_at  TIMESTAMP,
    tenancy_id  VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_acl_entry UNIQUE (actor_id, resource_id, action)
);

CREATE INDEX IF NOT EXISTS idx_acl_actor_resource ON acl_entry (actor_id, resource_id);
CREATE INDEX IF NOT EXISTS idx_acl_resource       ON acl_entry (resource_id);
CREATE INDEX IF NOT EXISTS idx_acl_tenancy        ON acl_entry (tenancy_id);

CREATE TABLE IF NOT EXISTS resource_parent (
    child_resource_id  VARCHAR(255) NOT NULL,
    parent_resource_id VARCHAR(255) NOT NULL,
    tenancy_id         VARCHAR(64)  NOT NULL,
    PRIMARY KEY (child_resource_id)
);

CREATE INDEX IF NOT EXISTS idx_rp_parent ON resource_parent (parent_resource_id);

-- ACL audit log — tracks GRANT/REVOKE operations
CREATE SEQUENCE IF NOT EXISTS acl_audit_log_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS acl_audit_log (
    id            BIGINT        NOT NULL DEFAULT nextval('acl_audit_log_seq'),
    actor_id      VARCHAR(255)  NOT NULL,
    resource_id   VARCHAR(255)  NOT NULL,
    action        VARCHAR(50)   NOT NULL,
    operation     VARCHAR(20)   NOT NULL,
    performed_by  VARCHAR(255)  NOT NULL,
    performed_at  TIMESTAMP     NOT NULL DEFAULT now(),
    expires_at    TIMESTAMP,
    metadata      JSONB,
    tenancy_id    VARCHAR(64)   NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_audit_resource    ON acl_audit_log (resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_actor       ON acl_audit_log (actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_performed   ON acl_audit_log (performed_by);
CREATE INDEX IF NOT EXISTS idx_audit_tenancy     ON acl_audit_log (tenancy_id);
