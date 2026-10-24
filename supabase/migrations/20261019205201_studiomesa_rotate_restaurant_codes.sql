
CREATE OR REPLACE FUNCTION public.rotate_restaurant_staff_code()
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_restaurant_id uuid;
  v_new_code text := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8));
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;

  SELECT rs.restaurant_id
    INTO v_restaurant_id
    FROM public.restaurant_staff AS rs
   WHERE rs.user_id = (SELECT auth.uid())
     AND rs.role = 'owner'
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
  v_new_code text := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8));
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;

  SELECT rs.restaurant_id
    INTO v_restaurant_id
    FROM public.restaurant_staff AS rs
   WHERE rs.user_id = (SELECT auth.uid())
     AND rs.role = 'owner'
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

REVOKE ALL ON FUNCTION public.rotate_restaurant_staff_code() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.rotate_restaurant_staff_code() TO authenticated;
REVOKE ALL ON FUNCTION public.rotate_restaurant_customer_code() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.rotate_restaurant_customer_code() TO authenticated;
