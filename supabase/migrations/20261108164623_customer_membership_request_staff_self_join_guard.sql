CREATE OR REPLACE FUNCTION public.request_restaurant_membership(
  p_restaurant_id uuid,
  p_display_name text
)
RETURNS TABLE (request_id uuid, request_status text, restaurant_name text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_user_id uuid := (SELECT auth.uid());
  v_name text := trim(coalesce(p_display_name, ''));
  v_restaurant_name text;
  v_request_id uuid;
  v_status text;
BEGIN
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'Inicia sessão para pedir associação.';
  END IF;
  IF p_restaurant_id IS NULL THEN
    RAISE EXCEPTION 'Escolhe um restaurante válido.';
  END IF;
  IF char_length(v_name) < 1 OR char_length(v_name) > 100 THEN
    RAISE EXCEPTION 'O nome deve ter entre 1 e 100 caracteres.';
  END IF;

  SELECT r.name INTO v_restaurant_name
  FROM public.restaurants AS r
  WHERE r.id = p_restaurant_id;

  IF v_restaurant_name IS NULL THEN
    RAISE EXCEPTION 'O restaurante já não está disponível.';
  END IF;

  IF EXISTS (
    SELECT 1 FROM public.restaurant_staff AS rs
    WHERE rs.restaurant_id = p_restaurant_id AND rs.user_id = v_user_id
  ) THEN
    RAISE EXCEPTION 'Esta conta já tem acesso de gestão a este restaurante.';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM public.loyalty_members AS lm
    WHERE lm.auth_user_id = v_user_id
      AND lm.restaurant_id = p_restaurant_id
  ) THEN
    RAISE EXCEPTION 'Esta conta já tem um cartão associado a este restaurante.';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM public.loyalty_members AS lm
    WHERE lm.auth_user_id = v_user_id
      AND lm.restaurant_id <> p_restaurant_id
  ) THEN
    RAISE EXCEPTION 'Esta versão ainda permite apenas um restaurante associado por conta cliente.';
  END IF;

  INSERT INTO public.restaurant_membership_requests
    (restaurant_id, requester_user_id, display_name, status, requested_at, reviewed_at, reviewed_by)
  VALUES
    (p_restaurant_id, v_user_id, v_name, 'pending', now(), NULL, NULL)
  ON CONFLICT (restaurant_id, requester_user_id)
  DO UPDATE SET
    display_name = EXCLUDED.display_name,
    status = 'pending',
    requested_at = now(),
    reviewed_at = NULL,
    reviewed_by = NULL
  WHERE public.restaurant_membership_requests.status = 'rejected'
  RETURNING id, status INTO v_request_id, v_status;

  IF v_request_id IS NULL THEN
    SELECT req.id, req.status
      INTO v_request_id, v_status
    FROM public.restaurant_membership_requests AS req
    WHERE req.restaurant_id = p_restaurant_id
      AND req.requester_user_id = v_user_id;
  END IF;

  RETURN QUERY SELECT v_request_id, v_status, v_restaurant_name;
END;
$function$;