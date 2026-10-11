ALTER TABLE registry_entry ADD COLUMN orphaned_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE registry_cascade_rule ADD COLUMN on_source_expired VARCHAR(30);
ALTER TABLE registry_cascade_rule ADD COLUMN cascade_grace_period_seconds BIGINT;
