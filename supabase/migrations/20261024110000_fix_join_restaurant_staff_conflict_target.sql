-- Fix an output-parameter name collision in join_restaurant_staff.
-- RETURNS TABLE exposes restaurant_id as a PL/pgSQL variable, making the
-- column-list ON CONFLICT target ambiguous when the function is executed.
BEGIN;

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
  ON CONFLICT ON CONSTRAINT restaurant_staff_pkey DO NOTHING;

  RETURN QUERY
  SELECT r.id, r.name, rs.role
  FROM public.restaurants AS r
  JOIN public.restaurant_staff AS rs ON rs.restaurant_id = r.id
  WHERE r.id = v_restaurant_id
    AND rs.user_id = (SELECT auth.uid());
END;
$function$;

REVOKE ALL ON FUNCTION public.join_restaurant_staff(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.join_restaurant_staff(text) TO authenticated;

COMMIT;
