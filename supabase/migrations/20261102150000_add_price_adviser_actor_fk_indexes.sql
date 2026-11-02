-- Cover nullable foreign keys used to track the users who changed price-adviser settings.
-- Partial indexes keep entries only for rows with an assigned actor.
BEGIN;

CREATE INDEX IF NOT EXISTS restaurant_price_adviser_audit_acted_by_idx
    ON private.restaurant_price_adviser_audit (acted_by)
    WHERE acted_by IS NOT NULL;

CREATE INDEX IF NOT EXISTS restaurant_price_adviser_settings_updated_by_idx
    ON private.restaurant_price_adviser_settings (updated_by)
    WHERE updated_by IS NOT NULL;

COMMIT;
