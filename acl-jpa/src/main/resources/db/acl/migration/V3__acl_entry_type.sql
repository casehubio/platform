-- Add entry_type column to distinguish ALLOW and DENY entries
ALTER TABLE acl_entry ADD COLUMN entry_type VARCHAR(5) NOT NULL DEFAULT 'ALLOW';

-- Expand unique constraint to include entry_type so same (actor, resource, action, tenant)
-- can have both an ALLOW and a DENY entry
ALTER TABLE acl_entry DROP CONSTRAINT uq_acl_entry;
ALTER TABLE acl_entry ADD CONSTRAINT uq_acl_entry
    UNIQUE (actor_id, resource_id, action, tenancy_id, entry_type);

CREATE INDEX IF NOT EXISTS idx_acl_entry_type ON acl_entry (entry_type);
