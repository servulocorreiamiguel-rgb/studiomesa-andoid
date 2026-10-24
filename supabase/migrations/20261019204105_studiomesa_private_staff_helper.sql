
CREATE OR REPLACE FUNCTION private.is_restaurant_staff(p_restaurant_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
  SELECT EXISTS (
    SELECT 1
    FROM public.restaurant_staff AS rs
    WHERE rs.restaurant_id = p_restaurant_id
      AND rs.user_id = (SELECT auth.uid())
  );
$function$;

GRANT USAGE ON SCHEMA private TO authenticated;
REVOKE ALL ON FUNCTION private.is_restaurant_staff(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION private.is_restaurant_staff(uuid) TO authenticated;

ALTER POLICY loyalty_members_read_self_or_staff
  ON public.loyalty_members
  USING (
    auth_user_id = (SELECT auth.uid())
    OR private.is_restaurant_staff(restaurant_id)
  );

ALTER POLICY loyalty_transactions_read_self_or_staff
  ON public.loyalty_transactions
  USING (
    EXISTS (
      SELECT 1
      FROM public.loyalty_members AS lm
      WHERE lm.id = loyalty_transactions.member_id
        AND lm.auth_user_id = (SELECT auth.uid())
    )
    OR private.is_restaurant_staff(restaurant_id)
  );

ALTER POLICY restaurant_staff_read_self_or_same_restaurant
  ON public.restaurant_staff
  USING (
    user_id = (SELECT auth.uid())
    OR private.is_restaurant_staff(restaurant_id)
  );

ALTER POLICY restaurants_read_member_or_staff
  ON public.restaurants
  USING (
    private.is_restaurant_staff(id)
    OR EXISTS (
      SELECT 1
      FROM public.loyalty_members AS lm
      WHERE lm.restaurant_id = restaurants.id
        AND lm.auth_user_id = (SELECT auth.uid())
    )
  );
