
CREATE OR REPLACE FUNCTION public.adjust_loyalty_points(
  p_member_id uuid, p_delta integer, p_note text DEFAULT NULL
)
RETURNS TABLE(member_id uuid, member_code text, points integer)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_restaurant_id uuid;
  v_member_code text;
  v_old_points integer;
  v_new_points integer;
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF p_delta IS NULL OR p_delta = 0 OR abs(p_delta::bigint) > 100000 THEN
    RAISE EXCEPTION 'A alteração de pontos não é válida.';
  END IF;

  SELECT lm.restaurant_id, lm.member_code, lm.points
    INTO v_restaurant_id, v_member_code, v_old_points
    FROM public.loyalty_members AS lm
   WHERE lm.id = p_member_id
   FOR UPDATE;

  IF v_restaurant_id IS NULL THEN
    RAISE EXCEPTION 'Cartão de cliente não encontrado.';
  END IF;
  IF NOT private.is_restaurant_staff(v_restaurant_id) THEN
    RAISE EXCEPTION 'Sem autorização para alterar pontos deste restaurante.';
  END IF;

  v_new_points := v_old_points + p_delta;
  IF v_new_points < 0 THEN
    RAISE EXCEPTION 'O saldo não pode ficar negativo.';
  END IF;

  UPDATE public.loyalty_members AS lm
     SET points = v_new_points
   WHERE lm.id = p_member_id;

  INSERT INTO public.loyalty_transactions
      (restaurant_id, member_id, created_by, points_delta, points_after, note)
  VALUES
      (v_restaurant_id, p_member_id, (SELECT auth.uid()), p_delta,
       v_new_points, nullif(trim(coalesce(p_note, '')), ''));

  RETURN QUERY SELECT p_member_id, v_member_code, v_new_points;
END;
$function$;

REVOKE ALL ON FUNCTION public.is_restaurant_staff(uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.adjust_loyalty_points(uuid, integer, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.adjust_loyalty_points(uuid, integer, text) TO authenticated;
