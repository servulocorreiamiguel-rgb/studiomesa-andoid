
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
  SELECT d.restaurant_id, d.restaurant_name, d.join_code
  FROM public.get_my_restaurant_details() AS d;
END;
$function$;

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
  v_staff_code text := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8));
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF EXISTS (
    SELECT 1 FROM public.restaurant_staff AS rs
    WHERE rs.user_id = (SELECT auth.uid())
  ) THEN
    RAISE EXCEPTION 'Esta conta já está ligada a um restaurante. Usa a conta existente ou uma conta separada para criar outro restaurante.';
  END IF;
  IF char_length(v_name) < 2 OR char_length(v_name) > 100 THEN
    RAISE EXCEPTION 'O nome do restaurante deve ter entre 2 e 100 caracteres.';
  END IF;
  IF v_code !~ '^[A-Z0-9]{6,12}$' THEN
    RAISE EXCEPTION 'O código de adesão de clientes deve ter 6 a 12 letras/números.';
  END IF;

  INSERT INTO public.restaurants (name)
  VALUES (v_name)
  RETURNING id INTO v_restaurant_id;

  INSERT INTO private.restaurant_join_codes (restaurant_id, join_code, staff_code)
  VALUES (v_restaurant_id, v_code, v_staff_code);

  INSERT INTO public.restaurant_staff (restaurant_id, user_id, role)
  VALUES (v_restaurant_id, (SELECT auth.uid()), 'owner');

  INSERT INTO public.restaurant_tables (restaurant_id, table_number)
  SELECT v_restaurant_id, s.table_number
  FROM generate_series(1, 12) AS s(table_number);

  RETURN v_restaurant_id;
END;
$function$;

CREATE OR REPLACE FUNCTION public.join_restaurant_staff(p_staff_code text)
RETURNS TABLE(restaurant_id uuid, restaurant_name text, user_role text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_restaurant_id uuid;
  v_restaurant_name text;
  v_existing_restaurant_id uuid;
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;

  SELECT r.id, r.name
    INTO v_restaurant_id, v_restaurant_name
    FROM private.restaurant_join_codes AS jc
    JOIN public.restaurants AS r ON r.id = jc.restaurant_id
   WHERE jc.staff_code = upper(trim(coalesce(p_staff_code, '')));

  IF v_restaurant_id IS NULL THEN
    RAISE EXCEPTION 'Código de equipa inválido.';
  END IF;

  SELECT rs.restaurant_id
    INTO v_existing_restaurant_id
    FROM public.restaurant_staff AS rs
   WHERE rs.user_id = (SELECT auth.uid())
   ORDER BY rs.created_at ASC
   LIMIT 1;

  IF v_existing_restaurant_id IS NOT NULL AND v_existing_restaurant_id <> v_restaurant_id THEN
    RAISE EXCEPTION 'Esta conta já está ligada a outro restaurante. Usa uma conta separada para cada restaurante.';
  END IF;

  INSERT INTO public.restaurant_staff (restaurant_id, user_id, role)
  VALUES (v_restaurant_id, (SELECT auth.uid()), 'staff')
  ON CONFLICT (restaurant_id, user_id) DO NOTHING;

  RETURN QUERY
  SELECT r.id, r.name, rs.role
  FROM public.restaurants AS r
  JOIN public.restaurant_staff AS rs ON rs.restaurant_id = r.id
  WHERE r.id = v_restaurant_id
    AND rs.user_id = (SELECT auth.uid());
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
    JOIN private.restaurant_join_codes AS jc ON jc.restaurant_id = r.id
   WHERE jc.join_code = upper(trim(coalesce(p_join_code, '')));

  IF v_restaurant_id IS NULL THEN
    RAISE EXCEPTION 'Código do restaurante inválido.';
  END IF;
  IF EXISTS (
    SELECT 1 FROM public.loyalty_members AS existing
    WHERE existing.auth_user_id = (SELECT auth.uid())
      AND existing.restaurant_id <> v_restaurant_id
  ) THEN
    RAISE EXCEPTION 'Esta conta já está associada a outro cartão de fidelização. Usa uma conta separada para cada restaurante.';
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

REVOKE ALL ON FUNCTION public.get_my_restaurant() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_my_restaurant() TO authenticated;
REVOKE ALL ON FUNCTION public.create_restaurant(text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_restaurant(text, text) TO authenticated;
REVOKE ALL ON FUNCTION public.join_restaurant_staff(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.join_restaurant_staff(text) TO authenticated;
REVOKE ALL ON FUNCTION public.join_restaurant(text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.join_restaurant(text, text) TO authenticated;
