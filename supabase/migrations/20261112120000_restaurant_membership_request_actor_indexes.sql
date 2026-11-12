-- Adds coverage for nullable membership-request actor foreign keys.
-- The table is introduced by 20261108164151, so these indexes must be created
-- in a later migration to preserve clean chronological replay.
BEGIN;

CREATE INDEX IF NOT EXISTS restaurant_membership_requests_requester_user_id_idx
    ON public.restaurant_membership_requests (requester_user_id)
    WHERE requester_user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS restaurant_membership_requests_reviewed_by_idx
    ON public.restaurant_membership_requests (reviewed_by)
    WHERE reviewed_by IS NOT NULL;

COMMIT;
