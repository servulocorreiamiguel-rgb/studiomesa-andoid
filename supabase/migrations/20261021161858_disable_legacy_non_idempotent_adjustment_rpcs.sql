-- Disable legacy write RPCs that do not support idempotency keys.
-- The current Android client uses the *_idempotent variants, which validate the
-- authenticated user and restaurant membership and deduplicate retries.
BEGIN;

REVOKE EXECUTE ON FUNCTION public.adjust_inventory_stock(uuid, numeric, text)
  FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public.adjust_loyalty_points(uuid, integer, text)
  FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public.create_restaurant_reservation(uuid, text, text, integer, timestamptz, integer, integer, text)
  FROM PUBLIC, anon, authenticated;

COMMIT;
