CREATE TABLE acl_entry (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    VARCHAR(255) NOT NULL,
    resource_id VARCHAR(255) NOT NULL,
    action      VARCHAR(50)  NOT NULL,
    granted_at  TIMESTAMP    NOT NULL DEFAULT now(),
    expires_at  TIMESTAMP    NULL,
    tenancy_id  VARCHAR(64)  NOT NULL,

    CONSTRAINT uq_acl_entry UNIQUE (actor_id, resource_id, action)
);

CREATE TABLE resource_parent (
    child_resource_id  VARCHAR(255) NOT NULL,
    parent_resource_id VARCHAR(255) NOT NULL,
    tenancy_id         VARCHAR(64)  NOT NULL,
    PRIMARY KEY (child_resource_id)
);

CREATE INDEX idx_acl_actor_resource ON acl_entry (actor_id, resource_id);
CREATE INDEX idx_acl_resource       ON acl_entry (resource_id);
CREATE INDEX idx_acl_tenancy        ON acl_entry (tenancy_id);
CREATE INDEX idx_rp_parent          ON resource_parent (parent_resource_id);
