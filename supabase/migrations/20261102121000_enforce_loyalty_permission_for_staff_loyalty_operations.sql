-- Enforce the granular loyalty permission across all staff-facing loyalty RPC internals.
-- The shop reconciliation migrations recreate membership-only implementations after
-- the original granular-permission migration. Invoice QR issuance also needs the same gate.
BEGIN;

CREATE OR REPLACE FUNCTION private.issue_loyalty_invoice_qr_internal(
  p_member_id uuid,
  p_amount numeric,
  p_invoice_reference text,
  p_client_request_id uuid
)
RETURNS TABLE (
  qr_token uuid,
  amount numeric,
  points integer,
  invoice_reference text,
  expires_at timestamptz
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_user_id uuid := (SELECT auth.uid());
  v_member public.loyalty_members%rowtype;
  v_existing private.loyalty_invoice_qr_codes%rowtype;
  v_invoice_reference text := trim(coalesce(p_invoice_reference, ''));
  v_points integer;
  v_token uuid;
  v_expires_at timestamptz;
BEGIN
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF p_member_id IS NULL OR p_client_request_id IS NULL THEN
    RAISE EXCEPTION 'Falta identificar o cliente ou esta emissão.';
  END IF;
  IF char_length(v_invoice_reference) NOT BETWEEN 1 AND 100 THEN
    RAISE EXCEPTION 'Indica a referência da fatura (1 a 100 caracteres).';
  END IF;
  IF p_amount IS NULL OR p_amount <> round(p_amount, 2)
     OR p_amount < 1 OR p_amount > 100000.99 THEN
    RAISE EXCEPTION 'O total deve ser válido, em euros, com no máximo duas casas decimais e pelo menos 1 euro.';
  END IF;

  -- Initial rule: one point for each complete euro. The client cannot choose
  -- the points value; it is calculated and capped by the server.
  v_points := floor(p_amount)::integer;
  IF v_points NOT BETWEEN 1 AND 100000 THEN
    RAISE EXCEPTION 'O valor da compra não permite calcular uma pontuação válida.';
  END IF;

  SELECT lm.* INTO v_member
  FROM public.loyalty_members AS lm
  WHERE lm.id = p_member_id
  FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Cartão de cliente não encontrado.';
  END IF;
  IF NOT private.has_restaurant_permission(v_member.restaurant_id, 'loyalty') THEN
    RAISE EXCEPTION 'Sem autorização para emitir códigos de fidelização neste restaurante.';
  END IF;

  PERFORM pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(
      v_member.restaurant_id::text || ':request:' || v_user_id::text || ':' || p_client_request_id::text,
      0
    )
  );
  PERFORM pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(
      v_member.restaurant_id::text || ':invoice:' || v_invoice_reference,
      0
    )
  );

  SELECT q.* INTO v_existing
  FROM private.loyalty_invoice_qr_codes AS q
  WHERE q.restaurant_id = v_member.restaurant_id
    AND q.issued_by = v_user_id
    AND q.client_request_id = p_client_request_id
  FOR UPDATE;

  IF FOUND THEN
    IF v_existing.member_id <> p_member_id
       OR v_existing.amount <> p_amount
       OR v_existing.invoice_reference <> v_invoice_reference THEN
      RAISE EXCEPTION 'Esta tentativa de emissão já foi usada com dados diferentes.';
    END IF;
    RETURN QUERY SELECT v_existing.token, v_existing.amount, v_existing.points,
                        v_existing.invoice_reference, v_existing.expires_at;
    RETURN;
  END IF;

  SELECT q.* INTO v_existing
  FROM private.loyalty_invoice_qr_codes AS q
  WHERE q.restaurant_id = v_member.restaurant_id
    AND q.invoice_reference = v_invoice_reference
  FOR UPDATE;

  IF FOUND THEN
    RAISE EXCEPTION 'Já existe um QR de fidelização para esta referência de fatura.';
  END IF;

  v_expires_at := now() + interval '30 days';
  INSERT INTO private.loyalty_invoice_qr_codes (
    restaurant_id, member_id, invoice_reference, amount, points,
    issued_by, client_request_id, expires_at
  )
  VALUES (
    v_member.restaurant_id, p_member_id, v_invoice_reference, p_amount, v_points,
    v_user_id, p_client_request_id, v_expires_at
  )
  RETURNING token INTO v_token;

  RETURN QUERY SELECT v_token, p_amount, v_points, v_invoice_reference, v_expires_at;
END;
$function$;

CREATE OR REPLACE FUNCTION private.get_restaurant_loyalty_rewards_internal(p_restaurant_id uuid)
RETURNS TABLE (
    id uuid,
    name text,
    description text,
    points_cost integer,
    is_active boolean,
    stock_limit integer,
    redeemed_count integer
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN
    IF (SELECT auth.uid()) IS NULL OR p_restaurant_id IS NULL
       OR NOT private.has_restaurant_permission(p_restaurant_id, 'loyalty') THEN
        RAISE EXCEPTION 'Sem autorização para consultar a loja de pontos.';
    END IF;

    RETURN QUERY
    SELECT r.id, r.name, r.description, r.points_cost,
           r.is_active, r.stock_limit, r.redeemed_count
    FROM public.loyalty_rewards r
    WHERE r.restaurant_id = p_restaurant_id
    ORDER BY r.is_active DESC, r.points_cost ASC, r.name ASC;
END;
$function$;

CREATE OR REPLACE FUNCTION private.get_restaurant_loyalty_reward_redemptions_internal(p_restaurant_id uuid)
RETURNS TABLE (
    redemption_id uuid,
    reward_id uuid,
    reward_name text,
    customer_name text,
    customer_code text,
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
    IF (SELECT auth.uid()) IS NULL OR p_restaurant_id IS NULL
       OR NOT private.has_restaurant_permission(p_restaurant_id, 'loyalty') THEN
        RAISE EXCEPTION 'Sem autorização para consultar os pedidos de recompensas.';
    END IF;

    RETURN QUERY
    SELECT rr.id,
           r.id,
           r.name,
           lm.display_name,
           lm.member_code,
           rr.points_spent,
           rr.status,
           rr.redeemed_at,
           rr.fulfilled_at
    FROM public.loyalty_reward_redemptions rr
    JOIN public.loyalty_rewards r
      ON r.id = rr.reward_id
     AND r.restaurant_id = rr.restaurant_id
    JOIN public.loyalty_members lm
      ON lm.id = rr.member_id
     AND lm.restaurant_id = rr.restaurant_id
    WHERE rr.restaurant_id = p_restaurant_id
    ORDER BY (rr.status = 'pending') DESC, rr.redeemed_at DESC
    LIMIT 100;
END;
$function$;

COMMIT;
