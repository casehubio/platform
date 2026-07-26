-- Include tenancy_id in unique constraint so the same (actor, resource, action)
-- tuple can exist in different tenants.
ALTER TABLE acl_entry DROP CONSTRAINT uq_acl_entry;
ALTER TABLE acl_entry ADD CONSTRAINT uq_acl_entry
    UNIQUE (actor_id, resource_id, action, tenancy_id);

-- Include tenancy_id in primary key so different tenants can register
-- parent mappings for the same child resource.
ALTER TABLE resource_parent DROP CONSTRAINT resource_parent_pkey;
ALTER TABLE resource_parent ADD CONSTRAINT resource_parent_pkey
    PRIMARY KEY (child_resource_id, tenancy_id);
