BEGIN;

-- Cover the two user foreign keys reported by Supabase Performance Advisor.
-- These are private audit/configuration tables; no client access is granted.
CREATE INDEX IF NOT EXISTS restaurant_price_adviser_audit_acted_by_idx
  ON private.restaurant_price_adviser_audit (acted_by);

CREATE INDEX IF NOT EXISTS restaurant_price_adviser_settings_updated_by_idx
  ON private.restaurant_price_adviser_settings (updated_by);

COMMIT;
