ALTER TABLE memory_entry ADD COLUMN subject_type  VARCHAR(255);
ALTER TABLE memory_entry ADD COLUMN confidence    VARCHAR(50);
ALTER TABLE memory_entry ADD COLUMN pleasure      DOUBLE PRECISION;
ALTER TABLE memory_entry ADD COLUMN arousal       DOUBLE PRECISION;
ALTER TABLE memory_entry ADD COLUMN dominance     DOUBLE PRECISION;
ALTER TABLE memory_entry ADD COLUMN principal_id  VARCHAR(255);
ALTER TABLE memory_entry ADD COLUMN shared_with   TEXT;
