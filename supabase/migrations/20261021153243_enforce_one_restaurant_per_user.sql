-- Enforce the one-restaurant-per-account rule at the database layer.
-- The application already rejects cross-restaurant membership; these indexes also
-- prevent concurrent requests from creating duplicate memberships for one account.
CREATE UNIQUE INDEX IF NOT EXISTS restaurant_staff_one_restaurant_per_user_idx
  ON public.restaurant_staff (user_id);

CREATE UNIQUE INDEX IF NOT EXISTS loyalty_members_one_restaurant_per_user_idx
  ON public.loyalty_members (auth_user_id)
  WHERE auth_user_id IS NOT NULL;
