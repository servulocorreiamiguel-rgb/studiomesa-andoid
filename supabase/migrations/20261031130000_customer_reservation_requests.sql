BEGIN;

-- Customer-originated reservation requests are linked to the authenticated
-- loyalty membership. Customers never receive direct INSERT/UPDATE privileges.
ALTER TABLE public.restaurant_reservations
  ADD COLUMN IF NOT EXISTS loyalty_member_id uuid
  REFERENCES public.loyalty_members(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS restaurant_reservations_member_time_idx
  ON public.restaurant_reservations (loyalty_member_id, reservation_at DESC)
  WHERE loyalty_member_id IS NOT NULL;

DROP POLICY IF EXISTS restaurant_reservations_customer_read ON public.restaurant_reservations;
CREATE POLICY restaurant_reservations_customer_read
  ON public.restaurant_reservations
  FOR SELECT TO authenticated
  USING (
    loyalty_member_id IS NOT NULL
    AND EXISTS (
      SELECT 1
      FROM public.loyalty_members AS lm
      WHERE lm.id = restaurant_reservations.loyalty_member_id
        AND lm.restaurant_id = restaurant_reservations.restaurant_id
        AND lm.auth_user_id = (SELECT auth.uid())
    )
  );

CREATE OR REPLACE FUNCTION public.create_customer_reservation_request(
  p_client_request_id uuid,
  p_member_id uuid,
  p_party_size integer,
  p_reservation_at timestamptz,
  p_guest_phone text DEFAULT '',
  p_notes text DEFAULT ''
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_user_id uuid := (SELECT auth.uid());
  v_restaurant_id uuid;
  v_guest_name text;
  v_phone text := trim(coalesce(p_guest_phone, ''));
  v_notes text := trim(coalesce(p_notes, ''));
  v_reservation_id uuid;
  v_existing public.restaurant_reservations%rowtype;
BEGIN
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF p_client_request_id IS NULL OR p_member_id IS NULL THEN
    RAISE EXCEPTION 'A identificação segura do pedido e o cartão de cliente são obrigatórios.';
  END IF;

  SELECT lm.restaurant_id, trim(lm.display_name)
    INTO v_restaurant_id, v_guest_name
  FROM public.loyalty_members AS lm
  WHERE lm.id = p_member_id
    AND lm.auth_user_id = v_user_id
  FOR SHARE;

  IF v_restaurant_id IS NULL OR v_guest_name IS NULL OR v_guest_name = '' THEN
    RAISE EXCEPTION 'O cartão de cliente não pertence a esta conta ou já não está disponível.';
  END IF;
  IF p_party_size IS NULL OR p_party_size NOT BETWEEN 1 AND 60 THEN
    RAISE EXCEPTION 'O número de pessoas deve estar entre 1 e 60.';
  END IF;
  IF p_reservation_at IS NULL OR p_reservation_at < now() THEN
    RAISE EXCEPTION 'A reserva tem de ser para o futuro.';
  END IF;
  IF p_reservation_at > now() + interval '1 year' THEN
    RAISE EXCEPTION 'A reserva não pode estar a mais de um ano de distância.';
  END IF;
  IF char_length(v_phone) > 40 OR char_length(v_notes) > 1000 THEN
    RAISE EXCEPTION 'O contacto ou as notas excedem o limite permitido.';
  END IF;

  -- The existing idempotency index is shared safely with staff-created
  -- reservations because the request UUID is scoped by restaurant and creator.
  SELECT r.* INTO v_existing
  FROM public.restaurant_reservations AS r
  WHERE r.restaurant_id = v_restaurant_id
    AND r.created_by = v_user_id
    AND r.client_request_id = p_client_request_id
  FOR UPDATE;

  IF FOUND THEN
    IF v_existing.loyalty_member_id = p_member_id
       AND v_existing.guest_name = v_guest_name
       AND v_existing.guest_phone = v_phone
       AND v_existing.party_size = p_party_size
       AND v_existing.reservation_at = p_reservation_at
       AND v_existing.duration_minutes = 90
       AND v_existing.table_number IS NULL
       AND v_existing.notes = v_notes THEN
      RETURN v_existing.id;
    END IF;
    RAISE EXCEPTION 'Esta tentativa já foi utilizada com dados diferentes. Atualiza as reservas antes de voltar a tentar.';
  END IF;

  INSERT INTO public.restaurant_reservations
    (client_request_id, restaurant_id, loyalty_member_id, guest_name, guest_phone,
     party_size, reservation_at, duration_minutes, table_number, status,
     notes, created_by, updated_at)
  VALUES
    (p_client_request_id, v_restaurant_id, p_member_id, v_guest_name, v_phone,
     p_party_size, p_reservation_at, 90, NULL, 'Pendente',
     v_notes, v_user_id, now())
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
       AND v_existing.loyalty_member_id = p_member_id
       AND v_existing.guest_name = v_guest_name
       AND v_existing.guest_phone = v_phone
       AND v_existing.party_size = p_party_size
       AND v_existing.reservation_at = p_reservation_at
       AND v_existing.duration_minutes = 90
       AND v_existing.table_number IS NULL
       AND v_existing.notes = v_notes THEN
      RETURN v_existing.id;
    END IF;
    RAISE EXCEPTION 'Não foi possível confirmar o pedido de reserva. Atualiza a lista antes de voltar a tentar.';
  END IF;

  RETURN v_reservation_id;
END;
$function$;

CREATE OR REPLACE FUNCTION public.cancel_my_customer_reservation(
  p_reservation_id uuid
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_user_id uuid := (SELECT auth.uid());
  v_member_id uuid;
  v_restaurant_id uuid;
  v_status text;
  v_reservation_at timestamptz;
BEGIN
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;

  SELECT r.loyalty_member_id, r.restaurant_id, r.status, r.reservation_at
    INTO v_member_id, v_restaurant_id, v_status, v_reservation_at
  FROM public.restaurant_reservations AS r
  WHERE r.id = p_reservation_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Pedido de reserva não encontrado.';
  END IF;
  IF v_member_id IS NULL OR NOT EXISTS (
    SELECT 1
    FROM public.loyalty_members AS lm
    WHERE lm.id = v_member_id
      AND lm.restaurant_id = v_restaurant_id
      AND lm.auth_user_id = v_user_id
  ) THEN
    RAISE EXCEPTION 'Não tens autorização para cancelar este pedido de reserva.';
  END IF;
  -- A retry after a lost HTTP response is harmless once cancellation committed.
  IF v_status = 'Cancelada' THEN
    RETURN;
  END IF;
  IF v_status NOT IN ('Pendente', 'Confirmada') OR v_reservation_at <= now() THEN
    RAISE EXCEPTION 'Só podes cancelar pedidos pendentes ou confirmados com data futura.';
  END IF;

  UPDATE public.restaurant_reservations
  SET status = 'Cancelada', updated_at = now()
  WHERE id = p_reservation_id;
END;
$function$;

CREATE OR REPLACE FUNCTION public.assign_restaurant_reservation_table(
  p_reservation_id uuid,
  p_table_number integer
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_user_id uuid := (SELECT auth.uid());
  v_restaurant_id uuid;
  v_party_size integer;
  v_reservation_at timestamptz;
  v_duration_minutes integer;
  v_status text;
  v_current_table_number integer;
  v_seats integer;
BEGIN
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF p_reservation_id IS NULL OR p_table_number IS NULL OR p_table_number NOT BETWEEN 1 AND 100 THEN
    RAISE EXCEPTION 'Indica uma reserva e uma mesa válidas.';
  END IF;

  SELECT r.restaurant_id, r.party_size, r.reservation_at, r.duration_minutes,
         r.status, r.table_number
    INTO v_restaurant_id, v_party_size, v_reservation_at, v_duration_minutes,
         v_status, v_current_table_number
  FROM public.restaurant_reservations AS r
  WHERE r.id = p_reservation_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Reserva não encontrada.';
  END IF;
  IF NOT private.is_restaurant_staff(v_restaurant_id) THEN
    RAISE EXCEPTION 'Sem autorização para atribuir uma mesa a esta reserva.';
  END IF;
  IF v_status NOT IN ('Pendente', 'Confirmada') OR v_reservation_at <= now() THEN
    RAISE EXCEPTION 'Só podes atribuir mesas a reservas pendentes ou confirmadas com data futura.';
  END IF;
  IF v_current_table_number = p_table_number THEN
    RETURN;
  END IF;

  SELECT t.seats
    INTO v_seats
  FROM public.restaurant_tables AS t
  WHERE t.restaurant_id = v_restaurant_id
    AND t.table_number = p_table_number
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'A mesa selecionada não existe neste restaurante.';
  END IF;
  IF v_seats < v_party_size THEN
    RAISE EXCEPTION 'A mesa não tem capacidade para este número de pessoas.';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM public.restaurant_reservations AS r
    WHERE r.restaurant_id = v_restaurant_id
      AND r.id <> p_reservation_id
      AND r.table_number = p_table_number
      AND r.status IN ('Pendente', 'Confirmada')
      AND r.reservation_at < v_reservation_at + (v_duration_minutes * interval '1 minute')
      AND v_reservation_at < r.reservation_at + (r.duration_minutes * interval '1 minute')
  ) THEN
    RAISE EXCEPTION 'Esta mesa já tem uma reserva pendente ou confirmada nesse horário.';
  END IF;

  UPDATE public.restaurant_reservations
  SET table_number = p_table_number, updated_at = now()
  WHERE id = p_reservation_id;
END;
$function$;

REVOKE ALL ON FUNCTION public.create_customer_reservation_request(uuid, uuid, integer, timestamptz, text, text)
  FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_customer_reservation_request(uuid, uuid, integer, timestamptz, text, text)
  TO authenticated;

REVOKE ALL ON FUNCTION public.assign_restaurant_reservation_table(uuid, integer)
  FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.assign_restaurant_reservation_table(uuid, integer)
  TO authenticated;

REVOKE ALL ON FUNCTION public.cancel_my_customer_reservation(uuid)
  FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.cancel_my_customer_reservation(uuid)
  TO authenticated;

COMMIT;
