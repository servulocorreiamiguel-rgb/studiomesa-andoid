
ALTER TABLE private.restaurant_join_codes
  ADD COLUMN IF NOT EXISTS staff_code text;

UPDATE private.restaurant_join_codes
SET staff_code = upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8))
WHERE staff_code IS NULL OR trim(staff_code) = '';

ALTER TABLE private.restaurant_join_codes
  ALTER COLUMN staff_code SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS restaurant_join_codes_staff_code_idx
  ON private.restaurant_join_codes (staff_code);

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

CREATE OR REPLACE FUNCTION public.get_my_restaurant_details()
RETURNS TABLE(
  restaurant_id uuid,
  restaurant_name text,
  join_code text,
  staff_code text,
  user_role text
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
  SELECT r.id,
         r.name,
         CASE WHEN rs.role = 'owner' THEN jc.join_code ELSE NULL END,
         CASE WHEN rs.role = 'owner' THEN jc.staff_code ELSE NULL END,
         rs.role
  FROM public.restaurants AS r
  JOIN public.restaurant_staff AS rs ON rs.restaurant_id = r.id
  JOIN private.restaurant_join_codes AS jc ON jc.restaurant_id = r.id
  WHERE rs.user_id = (SELECT auth.uid())
  ORDER BY rs.created_at ASC
  LIMIT 1;
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

REVOKE ALL ON FUNCTION public.create_restaurant(text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_restaurant(text, text) TO authenticated;
REVOKE ALL ON FUNCTION public.get_my_restaurant_details() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_my_restaurant_details() TO authenticated;
REVOKE ALL ON FUNCTION public.join_restaurant_staff(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.join_restaurant_staff(text) TO authenticated;
