-- Issue a one-time loyalty QR linked to a receipt reference and amount.
-- This is a loyalty record only; it does not create or certify a Portuguese tax invoice.
BEGIN;

CREATE TABLE IF NOT EXISTS private.loyalty_invoice_qr_codes (
  token uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  restaurant_id uuid NOT NULL REFERENCES public.restaurants(id) ON DELETE CASCADE,
  member_id uuid NOT NULL REFERENCES public.loyalty_members(id) ON DELETE CASCADE,
  invoice_reference text NOT NULL
    CHECK (char_length(trim(invoice_reference)) BETWEEN 1 AND 100),
  amount numeric(12,2) NOT NULL CHECK (amount > 0),
  points integer NOT NULL CHECK (points BETWEEN 1 AND 100000),
  issued_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
  client_request_id uuid NOT NULL,
  issued_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  redeemed_at timestamptz,
  redeemed_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
  loyalty_transaction_id uuid UNIQUE
    REFERENCES public.loyalty_transactions(id) ON DELETE SET NULL,
  CONSTRAINT loyalty_invoice_qr_expiry_after_issue
    CHECK (expires_at > issued_at),
  CONSTRAINT loyalty_invoice_qr_redemption_state
    CHECK (
      (redeemed_at IS NULL AND loyalty_transaction_id IS NULL)
      OR (redeemed_at IS NOT NULL AND loyalty_transaction_id IS NOT NULL)
    ),
  CONSTRAINT loyalty_invoice_qr_invoice_unique
    UNIQUE (restaurant_id, invoice_reference),
  CONSTRAINT loyalty_invoice_qr_request_unique
    UNIQUE (restaurant_id, issued_by, client_request_id)
);

CREATE INDEX IF NOT EXISTS loyalty_invoice_qr_member_idx
  ON private.loyalty_invoice_qr_codes (member_id, issued_at DESC);
CREATE INDEX IF NOT EXISTS loyalty_invoice_qr_expiry_idx
  ON private.loyalty_invoice_qr_codes (expires_at)
  WHERE redeemed_at IS NULL;

ALTER TABLE private.loyalty_invoice_qr_codes ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE private.loyalty_invoice_qr_codes FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.issue_loyalty_invoice_qr(
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
  IF NOT private.is_restaurant_staff(v_member.restaurant_id) THEN
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

CREATE OR REPLACE FUNCTION public.redeem_loyalty_invoice_qr(p_qr_token uuid)
RETURNS TABLE (
  member_id uuid,
  member_code text,
  points integer,
  points_awarded integer,
  invoice_reference text,
  amount numeric
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_user_id uuid := (SELECT auth.uid());
  v_qr private.loyalty_invoice_qr_codes%rowtype;
  v_member public.loyalty_members%rowtype;
  v_transaction public.loyalty_transactions%rowtype;
  v_new_points integer;
  v_note text;
BEGIN
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF p_qr_token IS NULL THEN
    RAISE EXCEPTION 'O QR de fidelização é inválido.';
  END IF;

  SELECT q.* INTO v_qr
  FROM private.loyalty_invoice_qr_codes AS q
  WHERE q.token = p_qr_token
  FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'QR de fidelização não encontrado.';
  END IF;

  SELECT lm.* INTO v_member
  FROM public.loyalty_members AS lm
  WHERE lm.id = v_qr.member_id
    AND lm.restaurant_id = v_qr.restaurant_id
    AND lm.auth_user_id = v_user_id
  FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Este QR não pertence ao cartão de cliente desta conta.';
  END IF;

  IF v_qr.redeemed_at IS NOT NULL THEN
    IF v_qr.redeemed_by IS DISTINCT FROM v_user_id
       OR v_qr.loyalty_transaction_id IS NULL THEN
      RAISE EXCEPTION 'Este QR já foi utilizado.';
    END IF;
    SELECT t.* INTO v_transaction
    FROM public.loyalty_transactions AS t
    WHERE t.id = v_qr.loyalty_transaction_id;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'O histórico desta atribuição não está disponível. Contacta o restaurante.';
    END IF;
    RETURN QUERY SELECT v_member.id, v_member.member_code, v_member.points,
                        v_transaction.points_delta, v_qr.invoice_reference, v_qr.amount;
    RETURN;
  END IF;

  IF v_qr.expires_at <= now() THEN
    RAISE EXCEPTION 'Este QR expirou. Pede ao restaurante um novo código.';
  END IF;
  IF v_member.points::bigint + v_qr.points::bigint > 2147483647 THEN
    RAISE EXCEPTION 'O saldo de pontos excederia o limite permitido.';
  END IF;

  v_new_points := (v_member.points::bigint + v_qr.points::bigint)::integer;
  v_note := left(
    'Compra · documento ' || v_qr.invoice_reference || ' · ' || v_qr.amount::text || ' EUR',
    500
  );

  UPDATE public.loyalty_members AS lm
  SET points = v_new_points
  WHERE lm.id = v_member.id
    AND lm.restaurant_id = v_qr.restaurant_id;

  INSERT INTO public.loyalty_transactions (
    restaurant_id, member_id, created_by, points_delta, points_after, note, client_request_id
  )
  VALUES (
    v_qr.restaurant_id, v_member.id, v_qr.issued_by, v_qr.points, v_new_points,
    v_note, v_qr.token
  )
  RETURNING * INTO v_transaction;

  UPDATE private.loyalty_invoice_qr_codes
  SET redeemed_at = now(),
      redeemed_by = v_user_id,
      loyalty_transaction_id = v_transaction.id
  WHERE token = v_qr.token;

  RETURN QUERY SELECT v_member.id, v_member.member_code, v_new_points,
                      v_qr.points, v_qr.invoice_reference, v_qr.amount;
END;
$function$;

REVOKE ALL ON FUNCTION public.issue_loyalty_invoice_qr(uuid, numeric, text, uuid)
  FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.issue_loyalty_invoice_qr(uuid, numeric, text, uuid)
  TO authenticated;

REVOKE ALL ON FUNCTION public.redeem_loyalty_invoice_qr(uuid)
  FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.redeem_loyalty_invoice_qr(uuid)
  TO authenticated;

COMMIT;
