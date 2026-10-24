
CREATE OR REPLACE FUNCTION public.get_member_restaurant_menu(p_member_id uuid)
RETURNS TABLE(item_id uuid, name text, price numeric)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_restaurant_id uuid;
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;

  SELECT lm.restaurant_id
    INTO v_restaurant_id
    FROM public.loyalty_members AS lm
   WHERE lm.id = p_member_id
     AND lm.auth_user_id = (SELECT auth.uid());

  IF v_restaurant_id IS NULL THEN
    RAISE EXCEPTION 'O cartão de cliente não pertence a esta conta.';
  END IF;

  RETURN QUERY
  SELECT mi.id, mi.name, mi.price
  FROM public.menu_items AS mi
  WHERE mi.restaurant_id = v_restaurant_id
    AND mi.is_active = true
  ORDER BY mi.name ASC;
END;
$function$;

REVOKE ALL ON FUNCTION public.get_member_restaurant_menu(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_member_restaurant_menu(uuid) TO authenticated;

CREATE OR REPLACE FUNCTION public.get_my_loyalty_membership()
RETURNS TABLE(
  member_id uuid,
  member_code text,
  points integer,
  display_name text,
  restaurant_id uuid,
  restaurant_name text
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;

  RETURN QUERY
  SELECT lm.id, lm.member_code, lm.points, lm.display_name, r.id, r.name
  FROM public.loyalty_members AS lm
  JOIN public.restaurants AS r ON r.id = lm.restaurant_id
  WHERE lm.auth_user_id = (SELECT auth.uid())
  ORDER BY lm.created_at ASC
  LIMIT 1;
END;
$function$;

REVOKE ALL ON FUNCTION public.get_my_loyalty_membership() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_my_loyalty_membership() TO authenticated;
