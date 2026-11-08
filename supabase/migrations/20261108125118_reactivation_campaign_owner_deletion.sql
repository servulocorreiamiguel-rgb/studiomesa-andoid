-- Campaigns are owner-created; deleting that account must not block the
-- existing account-deletion flow. Their recipient notifications cascade too.
ALTER TABLE public.restaurant_reactivation_campaigns
  DROP CONSTRAINT IF EXISTS restaurant_reactivation_campaigns_created_by_fkey;

ALTER TABLE public.restaurant_reactivation_campaigns
  ADD CONSTRAINT restaurant_reactivation_campaigns_created_by_fkey
  FOREIGN KEY (created_by)
  REFERENCES auth.users(id)
  ON DELETE CASCADE;

