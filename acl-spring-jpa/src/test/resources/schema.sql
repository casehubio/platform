CREATE SEQUENCE IF NOT EXISTS acl_entry_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS acl_entry (
    id          BIGINT       NOT NULL DEFAULT nextval('acl_entry_seq'),
    actor_id    VARCHAR(255) NOT NULL,
    resource_id VARCHAR(255) NOT NULL,
    action      VARCHAR(50)  NOT NULL,
    entry_type  VARCHAR(5)   NOT NULL DEFAULT 'ALLOW',
    condition   TEXT,
    granted_at  TIMESTAMP    NOT NULL,
    expires_at  TIMESTAMP,
    tenancy_id  VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_acl_entry UNIQUE (actor_id, resource_id, action, tenancy_id, entry_type)
);

CREATE TABLE IF NOT EXISTS resource_parent (
    child_resource_id  VARCHAR(255) NOT NULL,
    parent_resource_id VARCHAR(255) NOT NULL,
    tenancy_id         VARCHAR(64)  NOT NULL,
    PRIMARY KEY (child_resource_id, tenancy_id)
);

CREATE SEQUENCE IF NOT EXISTS acl_audit_log_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS acl_audit_log (
    id            BIGINT        NOT NULL DEFAULT nextval('acl_audit_log_seq'),
    actor_id      VARCHAR(255)  NOT NULL,
    resource_id   VARCHAR(255)  NOT NULL,
    action        VARCHAR(50)   NOT NULL,
    operation     VARCHAR(20)   NOT NULL,
    performed_by  VARCHAR(255)  NOT NULL,
    performed_at  TIMESTAMP     NOT NULL,
    expires_at    TIMESTAMP,
    metadata      CLOB,
    tenancy_id    VARCHAR(64)   NOT NULL,
    PRIMARY KEY (id)
);
