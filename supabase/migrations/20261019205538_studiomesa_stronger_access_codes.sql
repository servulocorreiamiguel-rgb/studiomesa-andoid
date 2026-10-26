
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
  v_staff_code text := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 12));
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

CREATE OR REPLACE FUNCTION public.rotate_restaurant_staff_code()
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_restaurant_id uuid;
  v_new_code text := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 12));
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;

  SELECT rs.restaurant_id INTO v_restaurant_id
  FROM public.restaurant_staff AS rs
  WHERE rs.user_id = (SELECT auth.uid()) AND rs.role = 'owner'
  ORDER BY rs.created_at ASC
  LIMIT 1;

  IF v_restaurant_id IS NULL THEN
    RAISE EXCEPTION 'Só o proprietário pode renovar o código da equipa.';
  END IF;

  UPDATE private.restaurant_join_codes AS jc
  SET staff_code = v_new_code
  WHERE jc.restaurant_id = v_restaurant_id;

  RETURN v_new_code;
END;
$function$;

CREATE OR REPLACE FUNCTION public.rotate_restaurant_customer_code()
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_restaurant_id uuid;
  v_new_code text := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 12));
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;

  SELECT rs.restaurant_id INTO v_restaurant_id
  FROM public.restaurant_staff AS rs
  WHERE rs.user_id = (SELECT auth.uid()) AND rs.role = 'owner'
  ORDER BY rs.created_at ASC
  LIMIT 1;

  IF v_restaurant_id IS NULL THEN
    RAISE EXCEPTION 'Só o proprietário pode renovar o código dos clientes.';
  END IF;

  UPDATE private.restaurant_join_codes AS jc
  SET join_code = v_new_code
  WHERE jc.restaurant_id = v_restaurant_id;

  RETURN v_new_code;
END;
$function$;

REVOKE ALL ON FUNCTION public.create_restaurant(text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_restaurant(text, text) TO authenticated;
REVOKE ALL ON FUNCTION public.rotate_restaurant_staff_code() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.rotate_restaurant_staff_code() TO authenticated;
REVOKE ALL ON FUNCTION public.rotate_restaurant_customer_code() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.rotate_restaurant_customer_code() TO authenticated;
