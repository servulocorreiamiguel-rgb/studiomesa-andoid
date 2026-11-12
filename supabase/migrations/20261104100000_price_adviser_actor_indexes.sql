-- Adds coverage for nullable actor foreign keys flagged by Supabase Performance Advisor.
-- The membership-request unique index starts with restaurant_id, so separate
-- requester/reviewer indexes are needed for references to auth.users(id).
-- IF NOT EXISTS also makes this safe when reconciling parallel migration history.
BEGIN;

CREATE INDEX IF NOT EXISTS restaurant_price_adviser_audit_acted_by_idx
    ON private.restaurant_price_adviser_audit (acted_by)
    WHERE acted_by IS NOT NULL;

CREATE INDEX IF NOT EXISTS restaurant_price_adviser_settings_updated_by_idx
    ON private.restaurant_price_adviser_settings (updated_by)
    WHERE updated_by IS NOT NULL;

CREATE INDEX IF NOT EXISTS restaurant_membership_requests_requester_user_id_idx
    ON public.restaurant_membership_requests (requester_user_id)
    WHERE requester_user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS restaurant_membership_requests_reviewed_by_idx
    ON public.restaurant_membership_requests (reviewed_by)
    WHERE reviewed_by IS NOT NULL;

COMMIT;
