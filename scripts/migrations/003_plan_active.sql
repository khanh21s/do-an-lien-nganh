-- =============================================================================
-- T14-SUB: admin plan management (enable/disable plans instead of deleting them).
-- Apply to an EXISTING subscription_db (fresh volumes get this from init-db.sql).
-- Idempotent: safe to run more than once.
--
--   docker exec -i parroto-postgres psql -U parroto -d subscription_db -v ON_ERROR_STOP=1 \
--     < scripts/migrations/003_plan_active.sql
-- (snap's docker-compose exec -T does not forward stdin: it silently runs nothing)
-- =============================================================================

-- false = hidden from GET /subscriptions/plans; new payments / code redemptions refused.
-- Existing subscriptions and transactions keep referencing the plan (no hard delete).
ALTER TABLE plan ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE plan ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP;

-- Admin transaction list / revenue stats filter and sort by creation time.
CREATE INDEX IF NOT EXISTS idx_payment_created ON payment_transaction(created_at DESC);
