ALTER TABLE memory_entry ADD COLUMN subject_type  TEXT;
ALTER TABLE memory_entry ADD COLUMN confidence    TEXT;
ALTER TABLE memory_entry ADD COLUMN pleasure      REAL;
ALTER TABLE memory_entry ADD COLUMN arousal       REAL;
ALTER TABLE memory_entry ADD COLUMN dominance     REAL;
ALTER TABLE memory_entry ADD COLUMN principal_id  TEXT;
ALTER TABLE memory_entry ADD COLUMN shared_with   TEXT;
