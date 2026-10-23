
ALTER TABLE public.menu_items
  ADD COLUMN IF NOT EXISTS category text NOT NULL DEFAULT 'Prato principal';
ALTER TABLE public.menu_items
  ADD COLUMN IF NOT EXISTS description text NOT NULL DEFAULT '';
ALTER TABLE public.menu_items
  ADD COLUMN IF NOT EXISTS allergens text NOT NULL DEFAULT '';

ALTER TABLE public.menu_items
  DROP CONSTRAINT IF EXISTS menu_items_category_check;
ALTER TABLE public.menu_items
  ADD CONSTRAINT menu_items_category_check
  CHECK (char_length(trim(category)) BETWEEN 1 AND 60);

ALTER TABLE public.menu_items
  DROP CONSTRAINT IF EXISTS menu_items_description_check;
ALTER TABLE public.menu_items
  ADD CONSTRAINT menu_items_description_check
  CHECK (char_length(description) <= 500);

ALTER TABLE public.menu_items
  DROP CONSTRAINT IF EXISTS menu_items_allergens_check;
ALTER TABLE public.menu_items
  ADD CONSTRAINT menu_items_allergens_check
  CHECK (char_length(allergens) <= 300);

DROP FUNCTION IF EXISTS public.get_member_restaurant_menu(uuid);

CREATE FUNCTION public.get_member_restaurant_menu(p_member_id uuid)
RETURNS TABLE(
  item_id uuid,
  name text,
  category text,
  description text,
  price numeric,
  allergens text
)
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
  SELECT mi.id, mi.name, mi.category, mi.description, mi.price, mi.allergens
  FROM public.menu_items AS mi
  WHERE mi.restaurant_id = v_restaurant_id
    AND mi.is_active = true
  ORDER BY mi.category ASC, mi.name ASC;
END;
$function$;

REVOKE ALL ON FUNCTION public.get_member_restaurant_menu(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_member_restaurant_menu(uuid) TO authenticated;
