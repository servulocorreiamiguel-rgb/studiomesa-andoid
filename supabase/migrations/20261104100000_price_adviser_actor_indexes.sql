-- Adds coverage for nullable actor foreign keys flagged by Supabase Performance Advisor.
-- IF NOT EXISTS also makes this safe when reconciling parallel migration history.
BEGIN;

CREATE INDEX IF NOT EXISTS restaurant_price_adviser_audit_acted_by_idx
    ON private.restaurant_price_adviser_audit (acted_by)
    WHERE acted_by IS NOT NULL;

CREATE INDEX IF NOT EXISTS restaurant_price_adviser_settings_updated_by_idx
    ON private.restaurant_price_adviser_settings (updated_by)
    WHERE updated_by IS NOT NULL;

COMMIT;
