-- V2: Add targets, includeActor, rename userId → ownerId

-- Rename user_id column to owner_id
ALTER TABLE subscription RENAME COLUMN user_id TO owner_id;

-- Add new columns
ALTER TABLE subscription ADD COLUMN targets_json TEXT;
ALTER TABLE subscription ADD COLUMN include_actor BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill: existing subscriptions get explicit USER target pointing to owner
UPDATE subscription SET targets_json =
    jsonb_build_array(jsonb_build_object('type', 'USER', 'id', owner_id))::text
    WHERE targets_json IS NULL;

-- Make targets_json non-nullable after backfill
ALTER TABLE subscription ALTER COLUMN targets_json SET NOT NULL;

-- Update indexes for renamed column
DROP INDEX IF EXISTS idx_subscription_user_tenant_enabled;
CREATE INDEX idx_subscription_owner_tenant_enabled
    ON subscription (owner_id, tenancy_id, enabled, created_at DESC);
