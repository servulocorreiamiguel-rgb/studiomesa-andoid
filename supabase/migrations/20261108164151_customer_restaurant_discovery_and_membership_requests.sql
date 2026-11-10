CREATE TABLE IF NOT EXISTS public.restaurant_membership_requests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  restaurant_id uuid NOT NULL REFERENCES public.restaurants(id) ON DELETE CASCADE,
  requester_user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  display_name text NOT NULL CHECK (char_length(trim(display_name)) BETWEEN 1 AND 100),
  status text NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'approved', 'rejected')),
  requested_at timestamptz NOT NULL DEFAULT now(),
  reviewed_at timestamptz,
  reviewed_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
  CONSTRAINT restaurant_membership_requests_requester_unique
    UNIQUE (restaurant_id, requester_user_id)
);

CREATE INDEX IF NOT EXISTS restaurant_membership_requests_pending_idx
  ON public.restaurant_membership_requests (restaurant_id, requested_at DESC)
  WHERE status = 'pending';

ALTER TABLE public.restaurant_membership_requests ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.restaurant_membership_requests FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.search_restaurants_by_name(p_query text)
RETURNS TABLE (restaurant_id uuid, restaurant_name text)
LANGUAGE plpgsql
STABLE SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_query text := trim(coalesce(p_query, ''));
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'Inicia sessão para procurar restaurantes.';
  END IF;
  IF char_length(v_query) < 3 OR char_length(v_query) > 80 THEN
    RAISE EXCEPTION 'Escreve pelo menos 3 caracteres do nome do restaurante.';
  END IF;

  RETURN QUERY
  SELECT r.id, r.name
  FROM public.restaurants AS r
  WHERE r.name ILIKE '%' || v_query || '%'
  ORDER BY lower(r.name), r.id
  LIMIT 10;
END;
$function$;

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

CREATE OR REPLACE FUNCTION public.get_my_restaurant_membership_requests()
RETURNS TABLE (
  request_id uuid,
  restaurant_id uuid,
  restaurant_name text,
  display_name text,
  request_status text,
  requested_at timestamptz,
  reviewed_at timestamptz
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'Inicia sessão para consultar os pedidos.';
  END IF;

  RETURN QUERY
  SELECT req.id, req.restaurant_id, r.name, req.display_name,
         req.status, req.requested_at, req.reviewed_at
  FROM public.restaurant_membership_requests AS req
  JOIN public.restaurants AS r ON r.id = req.restaurant_id
  WHERE req.requester_user_id = (SELECT auth.uid())
  ORDER BY req.requested_at DESC
  LIMIT 30;
END;
$function$;

CREATE OR REPLACE FUNCTION public.get_pending_restaurant_membership_requests(
  p_restaurant_id uuid
)
RETURNS TABLE (request_id uuid, display_name text, requested_at timestamptz)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'Inicia sessão para consultar os pedidos.';
  END IF;
  IF NOT EXISTS (
    SELECT 1
    FROM public.restaurant_staff AS rs
    WHERE rs.restaurant_id = p_restaurant_id
      AND rs.user_id = (SELECT auth.uid())
      AND rs.role = 'owner'
  ) THEN
    RAISE EXCEPTION 'Apenas o proprietário pode rever pedidos de associação.';
  END IF;

  RETURN QUERY
  SELECT req.id, req.display_name, req.requested_at
  FROM public.restaurant_membership_requests AS req
  WHERE req.restaurant_id = p_restaurant_id
    AND req.status = 'pending'
  ORDER BY req.requested_at ASC
  LIMIT 100;
END;
$function$;

CREATE OR REPLACE FUNCTION public.review_restaurant_membership_request(
  p_request_id uuid,
  p_approve boolean
)
RETURNS TABLE (request_status text, member_id uuid, restaurant_name text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_request public.restaurant_membership_requests%ROWTYPE;
  v_member_id uuid;
  v_restaurant_name text;
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'Inicia sessão para rever pedidos.';
  END IF;
  IF p_request_id IS NULL OR p_approve IS NULL THEN
    RAISE EXCEPTION 'O pedido de associação não é válido.';
  END IF;

  SELECT req.* INTO v_request
  FROM public.restaurant_membership_requests AS req
  WHERE req.id = p_request_id
  FOR UPDATE;

  IF v_request.id IS NULL THEN
    RAISE EXCEPTION 'O pedido de associação já não existe.';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM public.restaurant_staff AS rs
    WHERE rs.restaurant_id = v_request.restaurant_id
      AND rs.user_id = (SELECT auth.uid())
      AND rs.role = 'owner'
  ) THEN
    RAISE EXCEPTION 'Apenas o proprietário deste restaurante pode rever o pedido.';
  END IF;

  IF v_request.status <> 'pending' THEN
    RAISE EXCEPTION 'Este pedido já foi revisto.';
  END IF;

  SELECT r.name INTO v_restaurant_name
  FROM public.restaurants AS r
  WHERE r.id = v_request.restaurant_id;

  IF p_approve THEN
    IF EXISTS (
      SELECT 1
      FROM public.loyalty_members AS lm
      WHERE lm.auth_user_id = v_request.requester_user_id
        AND lm.restaurant_id <> v_request.restaurant_id
    ) THEN
      RAISE EXCEPTION 'Este cliente já tem uma conta associada a outro restaurante.';
    END IF;

    INSERT INTO public.loyalty_members (restaurant_id, auth_user_id, display_name)
    VALUES (v_request.restaurant_id, v_request.requester_user_id, v_request.display_name)
    ON CONFLICT (restaurant_id, auth_user_id)
    DO UPDATE SET display_name = EXCLUDED.display_name
    RETURNING id INTO v_member_id;

    UPDATE public.restaurant_membership_requests
    SET status = 'approved',
        reviewed_at = now(),
        reviewed_by = (SELECT auth.uid())
    WHERE id = v_request.id;
  ELSE
    UPDATE public.restaurant_membership_requests
    SET status = 'rejected',
        reviewed_at = now(),
        reviewed_by = (SELECT auth.uid())
    WHERE id = v_request.id;
  END IF;

  RETURN QUERY
  SELECT req.status, v_member_id, v_restaurant_name
  FROM public.restaurant_membership_requests AS req
  WHERE req.id = v_request.id;
END;
$function$;

REVOKE ALL ON FUNCTION public.search_restaurants_by_name(text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.request_restaurant_membership(uuid, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_my_restaurant_membership_requests() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_pending_restaurant_membership_requests(uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.review_restaurant_membership_request(uuid, boolean) FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.search_restaurants_by_name(text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.request_restaurant_membership(uuid, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_my_restaurant_membership_requests() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_pending_restaurant_membership_requests(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.review_restaurant_membership_request(uuid, boolean) TO authenticated;

COMMENT ON TABLE public.restaurant_membership_requests IS
  'Pedidos de clientes para aderirem a um programa de fidelização; apenas o proprietário pode aprovar ou rejeitar.';