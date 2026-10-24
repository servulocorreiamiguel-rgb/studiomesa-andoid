
CREATE SCHEMA IF NOT EXISTS private;

CREATE TABLE IF NOT EXISTS private.restaurant_join_codes (
  restaurant_id uuid PRIMARY KEY REFERENCES public.restaurants(id) ON DELETE CASCADE,
  join_code text NOT NULL UNIQUE
    CHECK (join_code ~ '^[A-Z0-9]{6,12}$')
);

-- Preserve any existing join codes before moving them out of the exposed schema.
INSERT INTO private.restaurant_join_codes (restaurant_id, join_code)
SELECT id, join_code
FROM public.restaurants
ON CONFLICT (restaurant_id) DO NOTHING;

REVOKE ALL ON SCHEMA private FROM PUBLIC, anon, authenticated;
REVOKE ALL ON TABLE private.restaurant_join_codes FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.create_restaurant(p_name text, p_join_code text)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_restaurant_id uuid;
  v_name text := trim(coalesce(p_name, ''));
  v_code text := upper(trim(coalesce(p_join_code, '')));
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF char_length(v_name) < 2 OR char_length(v_name) > 100 THEN
    RAISE EXCEPTION 'O nome do restaurante deve ter entre 2 e 100 caracteres.';
  END IF;
  IF v_code !~ '^[A-Z0-9]{6,12}$' THEN
    RAISE EXCEPTION 'O código deve ter 6 a 12 letras/números.';
  END IF;

  INSERT INTO public.restaurants (name)
  VALUES (v_name)
  RETURNING id INTO v_restaurant_id;

  INSERT INTO private.restaurant_join_codes (restaurant_id, join_code)
  VALUES (v_restaurant_id, v_code);

  INSERT INTO public.restaurant_staff (restaurant_id, user_id, role)
  VALUES (v_restaurant_id, (SELECT auth.uid()), 'owner');

  RETURN v_restaurant_id;
END;
$function$;

CREATE OR REPLACE FUNCTION public.join_restaurant(p_join_code text, p_display_name text)
RETURNS TABLE(member_id uuid, member_code text, points integer, restaurant_name text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_restaurant_id uuid;
  v_restaurant_name text;
  v_member_id uuid;
  v_member_code text;
  v_points integer;
  v_display_name text := trim(coalesce(p_display_name, ''));
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário criar conta ou iniciar sessão.';
  END IF;
  IF char_length(v_display_name) < 1 OR char_length(v_display_name) > 100 THEN
    RAISE EXCEPTION 'O nome deve ter entre 1 e 100 caracteres.';
  END IF;

  SELECT r.id, r.name
    INTO v_restaurant_id, v_restaurant_name
    FROM public.restaurants AS r
    JOIN private.restaurant_join_codes AS jc
      ON jc.restaurant_id = r.id
   WHERE jc.join_code = upper(trim(coalesce(p_join_code, '')));

  IF v_restaurant_id IS NULL THEN
    RAISE EXCEPTION 'Código do restaurante inválido.';
  END IF;

  INSERT INTO public.loyalty_members (restaurant_id, auth_user_id, display_name)
  VALUES (v_restaurant_id, (SELECT auth.uid()), v_display_name)
  ON CONFLICT (restaurant_id, auth_user_id)
  DO UPDATE SET display_name = EXCLUDED.display_name
  RETURNING id, public.loyalty_members.member_code, public.loyalty_members.points
       INTO v_member_id, v_member_code, v_points;

  RETURN QUERY SELECT v_member_id, v_member_code, v_points, v_restaurant_name;
END;
$function$;

CREATE OR REPLACE FUNCTION public.get_my_restaurant()
RETURNS TABLE(restaurant_id uuid, restaurant_name text, join_code text)
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
  SELECT r.id, r.name, jc.join_code
  FROM public.restaurants AS r
  JOIN public.restaurant_staff AS rs ON rs.restaurant_id = r.id
  JOIN private.restaurant_join_codes AS jc ON jc.restaurant_id = r.id
  WHERE rs.user_id = (SELECT auth.uid())
  ORDER BY rs.created_at ASC
  LIMIT 1;
END;
$function$;

REVOKE ALL ON FUNCTION public.create_restaurant(text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_restaurant(text, text) TO authenticated;
REVOKE ALL ON FUNCTION public.join_restaurant(text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.join_restaurant(text, text) TO authenticated;
REVOKE ALL ON FUNCTION public.get_my_restaurant() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_my_restaurant() TO authenticated;

ALTER TABLE public.restaurants DROP COLUMN join_code;
