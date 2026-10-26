-- Make staff joining robust to the per-user unique index under concurrent requests.
-- A user may belong to only one restaurant; a race must not turn that rule into an
-- unhandled unique-violation error or leave the caller believing a join succeeded.
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
  v_inserted boolean := false;
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

  IF v_existing_restaurant_id IS NOT NULL
     AND v_existing_restaurant_id <> v_restaurant_id THEN
    RAISE EXCEPTION 'Esta conta já está ligada a outro restaurante. Usa uma conta separada para cada restaurante.';
  END IF;

  INSERT INTO public.restaurant_staff (restaurant_id, user_id, role)
  VALUES (v_restaurant_id, (SELECT auth.uid()), 'staff')
  ON CONFLICT (user_id) DO NOTHING
  RETURNING true INTO v_inserted;

  IF NOT coalesce(v_inserted, false) THEN
    -- The unique-per-user index can win a race with another join request.
    -- Re-read the winner and confirm it is the same restaurant before returning.
    SELECT rs.restaurant_id
      INTO v_existing_restaurant_id
      FROM public.restaurant_staff AS rs
     WHERE rs.user_id = (SELECT auth.uid())
     ORDER BY rs.created_at ASC
     LIMIT 1;

    IF v_existing_restaurant_id IS NULL THEN
      RAISE EXCEPTION 'Não foi possível confirmar a adesão à equipa. Atualiza os dados e tenta novamente.';
    END IF;
    IF v_existing_restaurant_id <> v_restaurant_id THEN
      RAISE EXCEPTION 'Esta conta já está ligada a outro restaurante. Usa uma conta separada para cada restaurante.';
    END IF;
  END IF;

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
