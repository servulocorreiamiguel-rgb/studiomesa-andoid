BEGIN;

-- One client account may own one loyalty card per restaurant.
-- The composite unique constraint on (restaurant_id, auth_user_id) remains in place.
DROP INDEX IF EXISTS public.loyalty_members_one_restaurant_per_user_idx;

CREATE OR REPLACE FUNCTION public.join_restaurant(
  p_join_code text,
  p_display_name text
)
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
  IF char_length(v_display_name) NOT BETWEEN 1 AND 100 THEN
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
    SELECT 1 FROM public.restaurant_staff AS rs
    WHERE rs.restaurant_id = v_restaurant_id
      AND rs.user_id = (SELECT auth.uid())
  ) THEN
    RAISE EXCEPTION 'Esta conta já tem acesso de gestão a este restaurante.';
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

CREATE OR REPLACE FUNCTION public.get_my_loyalty_membership()
RETURNS TABLE(
  member_id uuid,
  member_code text,
  points integer,
  display_name text,
  restaurant_id uuid,
  restaurant_name text
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
  SELECT lm.id, lm.member_code, lm.points, lm.display_name, r.id, r.name
  FROM public.loyalty_members AS lm
  JOIN public.restaurants AS r ON r.id = lm.restaurant_id
  WHERE lm.auth_user_id = (SELECT auth.uid())
  ORDER BY lower(r.name), lm.created_at ASC;
END;
$function$;

-- Restaurant discovery approval also supports customers who already have cards elsewhere.
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
  IF char_length(v_name) NOT BETWEEN 1 AND 100 THEN
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
    SELECT 1 FROM public.loyalty_members AS lm
    WHERE lm.auth_user_id = v_user_id
      AND lm.restaurant_id = p_restaurant_id
  ) THEN
    RAISE EXCEPTION 'Esta conta já tem um cartão associado a este restaurante.';
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
      SELECT 1 FROM public.restaurant_staff AS rs
      WHERE rs.restaurant_id = v_request.restaurant_id
        AND rs.user_id = v_request.requester_user_id
    ) THEN
      RAISE EXCEPTION 'Esta conta já tem acesso de gestão a este restaurante.';
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

-- Every card-specific operation receives an explicit membership ID.
DROP FUNCTION IF EXISTS public.create_loyalty_qr_token();
CREATE FUNCTION public.create_loyalty_qr_token(p_member_id uuid)
RETURNS TABLE(qr_token uuid, expires_at timestamptz)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_restaurant_id uuid;
    v_token uuid;
    v_expires_at timestamptz := now() + interval '2 minutes';
BEGIN
    IF (SELECT auth.uid()) IS NULL THEN
        RAISE EXCEPTION 'É necessário iniciar sessão.';
    END IF;
    IF p_member_id IS NULL THEN
        RAISE EXCEPTION 'Seleciona um cartão de fidelização.';
    END IF;

    SELECT lm.restaurant_id INTO v_restaurant_id
    FROM public.loyalty_members AS lm
    WHERE lm.id = p_member_id
      AND lm.auth_user_id = (SELECT auth.uid())
    FOR UPDATE;

    IF v_restaurant_id IS NULL THEN
        RAISE EXCEPTION 'O cartão selecionado não pertence a esta conta.';
    END IF;

    DELETE FROM private.loyalty_qr_tokens AS token_row
    WHERE token_row.member_id = p_member_id AND token_row.used_at IS NULL;
    DELETE FROM private.loyalty_qr_tokens AS token_row
    WHERE token_row.member_id = p_member_id
      AND token_row.expires_at < now() - interval '1 day';

    INSERT INTO private.loyalty_qr_tokens (restaurant_id, member_id, expires_at)
    VALUES (v_restaurant_id, p_member_id, v_expires_at)
    RETURNING token INTO v_token;

    RETURN QUERY SELECT v_token, v_expires_at;
END;
$function$;

REVOKE ALL ON FUNCTION public.create_loyalty_qr_token(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_loyalty_qr_token(uuid) TO authenticated;

DROP FUNCTION IF EXISTS public.redeem_loyalty_reward(uuid, uuid);
DROP FUNCTION IF EXISTS private.redeem_loyalty_reward_internal(uuid, uuid);
CREATE FUNCTION private.redeem_loyalty_reward_internal(
    p_member_id uuid,
    p_reward_id uuid,
    p_client_request_id uuid
)
RETURNS TABLE(redemption_id uuid, reward_name text, points_spent integer, points_remaining integer, status text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_member public.loyalty_members%ROWTYPE;
    v_reward public.loyalty_rewards%ROWTYPE;
    v_existing public.loyalty_reward_redemptions%ROWTYPE;
    v_redemption_id uuid;
    v_remaining integer;
BEGIN
    IF (SELECT auth.uid()) IS NULL THEN
        RAISE EXCEPTION 'É necessário iniciar sessão.';
    END IF;
    IF p_member_id IS NULL OR p_reward_id IS NULL OR p_client_request_id IS NULL THEN
        RAISE EXCEPTION 'O cartão, a recompensa ou o identificador de resgate está em falta.';
    END IF;

    SELECT lm.* INTO v_member
    FROM public.loyalty_members AS lm
    WHERE lm.id = p_member_id
      AND lm.auth_user_id = (SELECT auth.uid())
    FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'O cartão selecionado não pertence a esta conta.';
    END IF;

    SELECT rr.* INTO v_existing
    FROM public.loyalty_reward_redemptions AS rr
    WHERE rr.member_id = v_member.id
      AND rr.client_request_id = p_client_request_id
    FOR UPDATE;
    IF FOUND THEN
        IF v_existing.reward_id <> p_reward_id THEN
            RAISE EXCEPTION 'Este identificador já foi utilizado para outra recompensa.';
        END IF;
        SELECT r.name INTO v_reward.name
        FROM public.loyalty_rewards AS r
        WHERE r.id = p_reward_id;
        RETURN QUERY SELECT v_existing.id, v_reward.name, v_existing.points_spent,
                            v_member.points, v_existing.status;
        RETURN;
    END IF;

    SELECT r.* INTO v_reward
    FROM public.loyalty_rewards AS r
    WHERE r.id = p_reward_id
      AND r.restaurant_id = v_member.restaurant_id
      AND r.is_active
    FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Esta recompensa não pertence ao cartão selecionado ou já não está disponível.';
    END IF;
    IF v_reward.stock_limit IS NOT NULL AND v_reward.redeemed_count >= v_reward.stock_limit THEN
        RAISE EXCEPTION 'Esta recompensa já esgotou.';
    END IF;
    IF v_member.points < v_reward.points_cost THEN
        RAISE EXCEPTION 'Não tens pontos suficientes para esta recompensa.';
    END IF;

    v_remaining := v_member.points - v_reward.points_cost;
    UPDATE public.loyalty_members SET points = v_remaining WHERE id = v_member.id;
    UPDATE public.loyalty_rewards
       SET redeemed_count = redeemed_count + 1, updated_at = now()
     WHERE id = v_reward.id;

    INSERT INTO public.loyalty_reward_redemptions
        (restaurant_id, member_id, reward_id, points_spent, redeemed_by, client_request_id)
    VALUES
        (v_member.restaurant_id, v_member.id, v_reward.id, v_reward.points_cost,
         (SELECT auth.uid()), p_client_request_id)
    RETURNING id INTO v_redemption_id;

    INSERT INTO public.loyalty_transactions
        (restaurant_id, member_id, created_by, points_delta, points_after, note, client_request_id)
    VALUES
        (v_member.restaurant_id, v_member.id, (SELECT auth.uid()), -v_reward.points_cost,
         v_remaining, 'Resgate: ' || v_reward.name, p_client_request_id);

    RETURN QUERY SELECT v_redemption_id, v_reward.name, v_reward.points_cost,
                        v_remaining, 'pending'::text;
END;
$function$;

REVOKE ALL ON FUNCTION private.redeem_loyalty_reward_internal(uuid, uuid, uuid)
  FROM PUBLIC, anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION private.redeem_loyalty_reward_internal(uuid, uuid, uuid) TO postgres;

CREATE FUNCTION public.redeem_loyalty_reward(
    p_member_id uuid,
    p_reward_id uuid,
    p_client_request_id uuid
)
RETURNS TABLE(redemption_id uuid, reward_name text, points_spent integer, points_remaining integer, status text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN
    IF (SELECT auth.uid()) IS NULL THEN
        RAISE EXCEPTION 'É necessário iniciar sessão.';
    END IF;
    RETURN QUERY
    SELECT * FROM private.redeem_loyalty_reward_internal(
        p_member_id, p_reward_id, p_client_request_id
    );
END;
$function$;

REVOKE ALL ON FUNCTION public.redeem_loyalty_reward(uuid, uuid, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.redeem_loyalty_reward(uuid, uuid, uuid) TO authenticated;

DROP FUNCTION IF EXISTS public.get_my_loyalty_reward_redemptions();
DROP FUNCTION IF EXISTS private.get_my_loyalty_reward_redemptions_internal();
CREATE FUNCTION private.get_my_loyalty_reward_redemptions_internal(p_member_id uuid)
RETURNS TABLE(
    redemption_id uuid,
    reward_name text,
    points_spent integer,
    status text,
    redeemed_at timestamptz,
    fulfilled_at timestamptz
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
    IF p_member_id IS NULL OR NOT EXISTS (
        SELECT 1 FROM public.loyalty_members AS lm
        WHERE lm.id = p_member_id
          AND lm.auth_user_id = (SELECT auth.uid())
    ) THEN
        RAISE EXCEPTION 'O cartão selecionado não pertence a esta conta.';
    END IF;

    RETURN QUERY
    SELECT rr.id, r.name, rr.points_spent, rr.status, rr.redeemed_at, rr.fulfilled_at
    FROM public.loyalty_reward_redemptions AS rr
    JOIN public.loyalty_rewards AS r ON r.id = rr.reward_id
    WHERE rr.member_id = p_member_id
    ORDER BY rr.redeemed_at DESC
    LIMIT 50;
END;
$function$;

REVOKE ALL ON FUNCTION private.get_my_loyalty_reward_redemptions_internal(uuid)
  FROM PUBLIC, anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION private.get_my_loyalty_reward_redemptions_internal(uuid) TO postgres;

CREATE FUNCTION public.get_my_loyalty_reward_redemptions(p_member_id uuid)
RETURNS TABLE(
    redemption_id uuid,
    reward_name text,
    points_spent integer,
    status text,
    redeemed_at timestamptz,
    fulfilled_at timestamptz
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
    SELECT * FROM private.get_my_loyalty_reward_redemptions_internal(p_member_id);
END;
$function$;

REVOKE ALL ON FUNCTION public.get_my_loyalty_reward_redemptions(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_my_loyalty_reward_redemptions(uuid) TO authenticated;

DROP FUNCTION IF EXISTS public.get_my_marketing_opt_in();
CREATE FUNCTION public.get_my_marketing_opt_in(p_member_id uuid)
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_enabled boolean;
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  SELECT lm.marketing_opt_in INTO v_enabled
  FROM public.loyalty_members AS lm
  WHERE lm.id = p_member_id
    AND lm.auth_user_id = (SELECT auth.uid());
  IF NOT FOUND THEN
    RAISE EXCEPTION 'O cartão selecionado não pertence a esta conta.';
  END IF;
  RETURN coalesce(v_enabled, false);
END;
$function$;

DROP FUNCTION IF EXISTS public.set_my_marketing_opt_in(boolean);
CREATE FUNCTION public.set_my_marketing_opt_in(p_member_id uuid, p_enabled boolean)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF p_member_id IS NULL OR p_enabled IS NULL THEN
    RAISE EXCEPTION 'O cartão ou a preferência de campanhas não é válido.';
  END IF;
  UPDATE public.loyalty_members AS lm
     SET marketing_opt_in = p_enabled
   WHERE lm.id = p_member_id
     AND lm.auth_user_id = (SELECT auth.uid());
  IF NOT FOUND THEN
    RAISE EXCEPTION 'O cartão selecionado não pertence a esta conta.';
  END IF;
  RETURN p_enabled;
END;
$function$;

REVOKE ALL ON FUNCTION public.get_my_marketing_opt_in(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_my_marketing_opt_in(uuid) TO authenticated;
REVOKE ALL ON FUNCTION public.set_my_marketing_opt_in(uuid, boolean) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.set_my_marketing_opt_in(uuid, boolean) TO authenticated;

DROP FUNCTION IF EXISTS public.get_my_restaurant_reservations();
CREATE FUNCTION public.get_my_restaurant_reservations(p_member_id uuid)
RETURNS TABLE(
    reservation_id uuid,
    guest_name text,
    party_size integer,
    reservation_at timestamptz,
    duration_minutes integer,
    table_number integer,
    status text,
    notes text,
    created_at timestamptz
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_user_id uuid := (SELECT auth.uid());
    v_restaurant_id uuid;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'É necessário iniciar sessão.';
    END IF;
    SELECT lm.restaurant_id INTO v_restaurant_id
    FROM public.loyalty_members AS lm
    WHERE lm.id = p_member_id
      AND lm.auth_user_id = v_user_id;
    IF v_restaurant_id IS NULL THEN
        RAISE EXCEPTION 'O cartão selecionado não pertence a esta conta.';
    END IF;

    RETURN QUERY
    SELECT r.id, r.guest_name, r.party_size, r.reservation_at,
           r.duration_minutes, r.table_number, r.status, r.notes, r.created_at
    FROM public.restaurant_reservations AS r
    WHERE r.restaurant_id = v_restaurant_id
      AND r.created_by = v_user_id
    ORDER BY r.reservation_at DESC
    LIMIT 50;
END;
$function$;

REVOKE ALL ON FUNCTION public.get_my_restaurant_reservations(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_my_restaurant_reservations(uuid) TO authenticated;

DROP FUNCTION IF EXISTS public.create_customer_reservation_request(
    uuid, text, text, integer, timestamptz, integer, text
);
CREATE FUNCTION public.create_customer_reservation_request(
    p_member_id uuid,
    p_client_request_id uuid,
    p_guest_name text,
    p_guest_phone text,
    p_party_size integer,
    p_reservation_at timestamptz,
    p_duration_minutes integer DEFAULT 90,
    p_notes text DEFAULT NULL
)
RETURNS TABLE(reservation_id uuid, status text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_user_id uuid := (SELECT auth.uid());
    v_restaurant_id uuid;
    v_name text := trim(coalesce(p_guest_name, ''));
    v_phone text := trim(coalesce(p_guest_phone, ''));
    v_notes text := trim(coalesce(p_notes, ''));
    v_existing public.restaurant_reservations%ROWTYPE;
    v_reservation_id uuid;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'É necessário iniciar sessão.';
    END IF;
    IF p_client_request_id IS NULL OR p_member_id IS NULL THEN
        RAISE EXCEPTION 'O cartão ou o identificador seguro do pedido está em falta.';
    END IF;

    SELECT lm.restaurant_id INTO v_restaurant_id
    FROM public.loyalty_members AS lm
    WHERE lm.id = p_member_id
      AND lm.auth_user_id = v_user_id;
    IF v_restaurant_id IS NULL THEN
        RAISE EXCEPTION 'O cartão selecionado não pertence a esta conta.';
    END IF;

    SELECT r.* INTO v_existing
    FROM public.restaurant_reservations AS r
    WHERE r.restaurant_id = v_restaurant_id
      AND r.created_by = v_user_id
      AND r.client_request_id = p_client_request_id
    FOR UPDATE;
    IF FOUND THEN
        IF v_existing.guest_name = v_name
           AND v_existing.guest_phone = v_phone
           AND v_existing.party_size = p_party_size
           AND v_existing.reservation_at = p_reservation_at
           AND v_existing.duration_minutes = p_duration_minutes
           AND v_existing.notes = v_notes THEN
            RETURN QUERY SELECT v_existing.id, v_existing.status;
            RETURN;
        END IF;
        RAISE EXCEPTION 'Esta tentativa já foi utilizada com dados diferentes.';
    END IF;

    IF char_length(v_name) NOT BETWEEN 1 AND 100 THEN
        RAISE EXCEPTION 'O nome deve ter entre 1 e 100 caracteres.';
    END IF;
    IF char_length(v_phone) > 40 OR char_length(v_notes) > 1000 THEN
        RAISE EXCEPTION 'O contacto ou as notas excedem o limite permitido.';
    END IF;
    IF p_party_size IS NULL OR p_party_size NOT BETWEEN 1 AND 60 THEN
        RAISE EXCEPTION 'O número de pessoas deve estar entre 1 e 60.';
    END IF;
    IF p_reservation_at IS NULL OR p_reservation_at < now() THEN
        RAISE EXCEPTION 'A data da reserva tem de ser futura.';
    END IF;
    IF p_reservation_at > now() + interval '1 year' THEN
        RAISE EXCEPTION 'A reserva não pode estar a mais de um ano de distância.';
    END IF;
    IF p_duration_minutes IS NULL OR p_duration_minutes NOT BETWEEN 30 AND 480 THEN
        RAISE EXCEPTION 'A duração deve estar entre 30 e 480 minutos.';
    END IF;

    INSERT INTO public.restaurant_reservations (
        restaurant_id, guest_name, guest_phone, party_size, reservation_at,
        duration_minutes, table_number, status, notes, created_by, client_request_id, updated_at
    ) VALUES (
        v_restaurant_id, v_name, v_phone, p_party_size, p_reservation_at,
        p_duration_minutes, NULL, 'Pendente', v_notes, v_user_id, p_client_request_id, now()
    )
    ON CONFLICT (restaurant_id, created_by, client_request_id)
        WHERE client_request_id IS NOT NULL
    DO NOTHING
    RETURNING id INTO v_reservation_id;

    IF v_reservation_id IS NULL THEN
        SELECT r.* INTO v_existing
        FROM public.restaurant_reservations AS r
        WHERE r.restaurant_id = v_restaurant_id
          AND r.created_by = v_user_id
          AND r.client_request_id = p_client_request_id
        FOR UPDATE;
        IF FOUND
           AND v_existing.guest_name = v_name
           AND v_existing.guest_phone = v_phone
           AND v_existing.party_size = p_party_size
           AND v_existing.reservation_at = p_reservation_at
           AND v_existing.duration_minutes = p_duration_minutes
           AND v_existing.notes = v_notes THEN
            RETURN QUERY SELECT v_existing.id, v_existing.status;
            RETURN;
        END IF;
        RAISE EXCEPTION 'Não foi possível confirmar o pedido de reserva. Atualiza a lista antes de voltar a tentar.';
    END IF;

    RETURN QUERY SELECT v_reservation_id, 'Pendente'::text;
END;
$function$;

REVOKE ALL ON FUNCTION public.create_customer_reservation_request(
    uuid, uuid, text, text, integer, timestamptz, integer, text
) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_customer_reservation_request(
    uuid, uuid, text, text, integer, timestamptz, integer, text
) TO authenticated;

-- Backward-compatible RPC overloads for clients that have not yet upgraded.
-- Older app versions historically used the oldest membership implicitly.
CREATE OR REPLACE FUNCTION public.create_loyalty_qr_token()
RETURNS TABLE(qr_token uuid, expires_at timestamptz)
LANGUAGE sql
SECURITY DEFINER
SET search_path TO ''
AS $function$
    SELECT * FROM public.create_loyalty_qr_token((
        SELECT lm.id
        FROM public.loyalty_members AS lm
        WHERE lm.auth_user_id = (SELECT auth.uid())
        ORDER BY lm.created_at ASC
        LIMIT 1
    ));
$function$;

CREATE OR REPLACE FUNCTION public.redeem_loyalty_reward(
    p_reward_id uuid,
    p_client_request_id uuid
)
RETURNS TABLE(
    redemption_id uuid,
    reward_name text,
    points_spent integer,
    points_remaining integer,
    status text
)
LANGUAGE sql
SECURITY DEFINER
SET search_path TO ''
AS $function$
    SELECT * FROM public.redeem_loyalty_reward(
        (
            SELECT lm.id
            FROM public.loyalty_members AS lm
            WHERE lm.auth_user_id = (SELECT auth.uid())
            ORDER BY lm.created_at ASC
            LIMIT 1
        ),
        p_reward_id,
        p_client_request_id
    );
$function$;

CREATE OR REPLACE FUNCTION public.get_my_loyalty_reward_redemptions()
RETURNS TABLE(
    redemption_id uuid,
    reward_name text,
    points_spent integer,
    status text,
    redeemed_at timestamptz,
    fulfilled_at timestamptz
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
    SELECT * FROM public.get_my_loyalty_reward_redemptions((
        SELECT lm.id
        FROM public.loyalty_members AS lm
        WHERE lm.auth_user_id = (SELECT auth.uid())
        ORDER BY lm.created_at ASC
        LIMIT 1
    ));
$function$;

CREATE OR REPLACE FUNCTION public.get_my_marketing_opt_in()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
    SELECT public.get_my_marketing_opt_in((
        SELECT lm.id
        FROM public.loyalty_members AS lm
        WHERE lm.auth_user_id = (SELECT auth.uid())
        ORDER BY lm.created_at ASC
        LIMIT 1
    ));
$function$;

CREATE OR REPLACE FUNCTION public.set_my_marketing_opt_in(p_enabled boolean)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
SET search_path TO ''
AS $function$
    SELECT public.set_my_marketing_opt_in((
        SELECT lm.id
        FROM public.loyalty_members AS lm
        WHERE lm.auth_user_id = (SELECT auth.uid())
        ORDER BY lm.created_at ASC
        LIMIT 1
    ), p_enabled);
$function$;

CREATE OR REPLACE FUNCTION public.get_my_restaurant_reservations()
RETURNS TABLE(
    reservation_id uuid,
    guest_name text,
    party_size integer,
    reservation_at timestamptz,
    duration_minutes integer,
    table_number integer,
    status text,
    notes text,
    created_at timestamptz
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
    SELECT * FROM public.get_my_restaurant_reservations((
        SELECT lm.id
        FROM public.loyalty_members AS lm
        WHERE lm.auth_user_id = (SELECT auth.uid())
        ORDER BY lm.created_at ASC
        LIMIT 1
    ));
$function$;

CREATE OR REPLACE FUNCTION public.create_customer_reservation_request(
    p_client_request_id uuid,
    p_guest_name text,
    p_guest_phone text,
    p_party_size integer,
    p_reservation_at timestamptz,
    p_duration_minutes integer DEFAULT 90,
    p_notes text DEFAULT NULL
)
RETURNS TABLE(reservation_id uuid, status text)
LANGUAGE sql
SECURITY DEFINER
SET search_path TO ''
AS $function$
    SELECT * FROM public.create_customer_reservation_request(
        (
            SELECT lm.id
            FROM public.loyalty_members AS lm
            WHERE lm.auth_user_id = (SELECT auth.uid())
            ORDER BY lm.created_at ASC
            LIMIT 1
        ),
        p_client_request_id,
        p_guest_name,
        p_guest_phone,
        p_party_size,
        p_reservation_at,
        p_duration_minutes,
        p_notes
    );
$function$;

REVOKE ALL ON FUNCTION public.create_loyalty_qr_token() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_loyalty_qr_token() TO authenticated;
REVOKE ALL ON FUNCTION public.redeem_loyalty_reward(uuid, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.redeem_loyalty_reward(uuid, uuid) TO authenticated;
REVOKE ALL ON FUNCTION public.get_my_loyalty_reward_redemptions() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_my_loyalty_reward_redemptions() TO authenticated;
REVOKE ALL ON FUNCTION public.get_my_marketing_opt_in() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_my_marketing_opt_in() TO authenticated;
REVOKE ALL ON FUNCTION public.set_my_marketing_opt_in(boolean) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.set_my_marketing_opt_in(boolean) TO authenticated;
REVOKE ALL ON FUNCTION public.get_my_restaurant_reservations() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_my_restaurant_reservations() TO authenticated;
REVOKE ALL ON FUNCTION public.create_customer_reservation_request(
    uuid, text, text, integer, timestamptz, integer, text
) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_customer_reservation_request(
    uuid, text, text, integer, timestamptz, integer, text
) TO authenticated;

-- Preserve safe behavior for older installed APKs. These wrappers only work while
-- an account has a single loyalty card; when multiple cards exist, old clients
-- must update rather than silently operating on the wrong restaurant.
CREATE OR REPLACE FUNCTION private.require_single_customer_membership()
RETURNS uuid
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_member_id uuid;
    v_count integer;
BEGIN
    IF (SELECT auth.uid()) IS NULL THEN
        RAISE EXCEPTION 'É necessário iniciar sessão.';
    END IF;
    SELECT count(*)::integer, min(lm.id)
      INTO v_count, v_member_id
    FROM public.loyalty_members AS lm
    WHERE lm.auth_user_id = (SELECT auth.uid());
    IF v_count = 0 THEN
        RAISE EXCEPTION 'Associa primeiro a tua conta ao restaurante.';
    END IF;
    IF v_count > 1 THEN
        RAISE EXCEPTION 'Esta versão da aplicação suporta apenas um restaurante. Atualiza o StudioMesa para escolheres o cartão correto.';
    END IF;
    RETURN v_member_id;
END;
$function$;

REVOKE ALL ON FUNCTION private.require_single_customer_membership() FROM PUBLIC, anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION private.require_single_customer_membership() TO postgres;

CREATE FUNCTION public.create_loyalty_qr_token()
RETURNS TABLE(qr_token uuid, expires_at timestamptz)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN
    RETURN QUERY
    SELECT * FROM public.create_loyalty_qr_token(
        private.require_single_customer_membership()
    );
END;
$function$;

CREATE FUNCTION public.redeem_loyalty_reward(
    p_reward_id uuid,
    p_client_request_id uuid
)
RETURNS TABLE(
    redemption_id uuid,
    reward_name text,
    points_spent integer,
    points_remaining integer,
    status text
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN
    RETURN QUERY
    SELECT * FROM public.redeem_loyalty_reward(
        private.require_single_customer_membership(),
        p_reward_id,
        p_client_request_id
    );
END;
$function$;

CREATE FUNCTION public.get_my_loyalty_reward_redemptions()
RETURNS TABLE(
    redemption_id uuid,
    reward_name text,
    points_spent integer,
    status text,
    redeemed_at timestamptz,
    fulfilled_at timestamptz
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN
    RETURN QUERY
    SELECT * FROM public.get_my_loyalty_reward_redemptions(
        private.require_single_customer_membership()
    );
END;
$function$;

CREATE FUNCTION public.get_my_marketing_opt_in()
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN
    RETURN public.get_my_marketing_opt_in(
        private.require_single_customer_membership()
    );
END;
$function$;

CREATE FUNCTION public.set_my_marketing_opt_in(p_enabled boolean)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN
    RETURN public.set_my_marketing_opt_in(
        private.require_single_customer_membership(),
        p_enabled
    );
END;
$function$;

CREATE FUNCTION public.get_my_restaurant_reservations()
RETURNS TABLE(
    reservation_id uuid,
    guest_name text,
    party_size integer,
    reservation_at timestamptz,
    duration_minutes integer,
    table_number integer,
    status text,
    notes text,
    created_at timestamptz
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN
    RETURN QUERY
    SELECT * FROM public.get_my_restaurant_reservations(
        private.require_single_customer_membership()
    );
END;
$function$;

CREATE FUNCTION public.create_customer_reservation_request(
    p_client_request_id uuid,
    p_guest_name text,
    p_guest_phone text,
    p_party_size integer,
    p_reservation_at timestamptz,
    p_duration_minutes integer DEFAULT 90,
    p_notes text DEFAULT NULL
)
RETURNS TABLE(reservation_id uuid, status text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN
    RETURN QUERY
    SELECT * FROM public.create_customer_reservation_request(
        private.require_single_customer_membership(),
        p_client_request_id,
        p_guest_name,
        p_guest_phone,
        p_party_size,
        p_reservation_at,
        p_duration_minutes,
        p_notes
    );
END;
$function$;

-- Keep all legacy public entry points restricted to authenticated sessions.
REVOKE ALL ON FUNCTION public.create_loyalty_qr_token() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.redeem_loyalty_reward(uuid, uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_my_loyalty_reward_redemptions() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_my_marketing_opt_in() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.set_my_marketing_opt_in(boolean) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_my_restaurant_reservations() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.create_customer_reservation_request(
    uuid, text, text, integer, timestamptz, integer, text
) FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.create_loyalty_qr_token() TO authenticated;
GRANT EXECUTE ON FUNCTION public.redeem_loyalty_reward(uuid, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_my_loyalty_reward_redemptions() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_my_marketing_opt_in() TO authenticated;
GRANT EXECUTE ON FUNCTION public.set_my_marketing_opt_in(boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_my_restaurant_reservations() TO authenticated;
GRANT EXECUTE ON FUNCTION public.create_customer_reservation_request(
    uuid, text, text, integer, timestamptz, integer, text
) TO authenticated;

NOTIFY pgrst, 'reload schema';
COMMIT;