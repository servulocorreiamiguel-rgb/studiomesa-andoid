-- Idempotent reservation creation for safe retries after network timeouts.
-- Existing reservation rows are preserved; older rows leave client_request_id NULL.
BEGIN;

ALTER TABLE public.restaurant_reservations
  ADD COLUMN IF NOT EXISTS client_request_id uuid;

CREATE UNIQUE INDEX IF NOT EXISTS restaurant_reservations_client_request_id_idx
  ON public.restaurant_reservations (restaurant_id, created_by, client_request_id)
  WHERE client_request_id IS NOT NULL;

CREATE OR REPLACE FUNCTION public.create_restaurant_reservation_idempotent(
  p_client_request_id uuid,
  p_restaurant_id uuid,
  p_guest_name text,
  p_guest_phone text,
  p_party_size integer,
  p_reservation_at timestamptz,
  p_duration_minutes integer,
  p_table_number integer,
  p_notes text
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
  v_name text := trim(coalesce(p_guest_name, ''));
  v_phone text := trim(coalesce(p_guest_phone, ''));
  v_notes text := trim(coalesce(p_notes, ''));
  v_table_id uuid;
  v_seats integer;
  v_reservation_id uuid;
  v_existing public.restaurant_reservations%rowtype;
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF p_restaurant_id IS NULL OR NOT private.is_restaurant_staff(p_restaurant_id) THEN
    RAISE EXCEPTION 'Sem autorização para gerir reservas deste restaurante.';
  END IF;
  IF p_client_request_id IS NULL THEN
    RAISE EXCEPTION 'O identificador seguro do pedido está em falta.';
  END IF;

  -- Return the prior result for the same user and exact payload before time checks.
  SELECT r.* INTO v_existing
  FROM public.restaurant_reservations AS r
  WHERE r.restaurant_id = p_restaurant_id
    AND r.created_by = (SELECT auth.uid())
    AND r.client_request_id = p_client_request_id
  FOR UPDATE;

  IF FOUND THEN
    IF v_existing.guest_name = v_name
       AND v_existing.guest_phone = v_phone
       AND v_existing.party_size IS NOT DISTINCT FROM p_party_size
       AND v_existing.reservation_at IS NOT DISTINCT FROM p_reservation_at
       AND v_existing.duration_minutes IS NOT DISTINCT FROM p_duration_minutes
       AND v_existing.table_number IS NOT DISTINCT FROM p_table_number
       AND v_existing.notes = v_notes THEN
      RETURN v_existing.id;
    END IF;
    RAISE EXCEPTION 'Esta tentativa já foi utilizada com dados diferentes. Atualiza as reservas antes de voltar a tentar.';
  END IF;

  IF char_length(v_name) NOT BETWEEN 1 AND 100 THEN
    RAISE EXCEPTION 'O nome do cliente deve ter entre 1 e 100 caracteres.';
  END IF;
  IF char_length(v_phone) > 40 OR char_length(v_notes) > 1000 THEN
    RAISE EXCEPTION 'O contacto ou as notas excedem o limite permitido.';
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
  IF p_duration_minutes IS NULL OR p_duration_minutes NOT BETWEEN 30 AND 480 THEN
    RAISE EXCEPTION 'A duração deve estar entre 30 e 480 minutos.';
  END IF;

  IF p_table_number IS NOT NULL THEN
    SELECT t.id, t.seats INTO v_table_id, v_seats
    FROM public.restaurant_tables AS t
    WHERE t.restaurant_id = p_restaurant_id
      AND t.table_number = p_table_number
    FOR UPDATE;

    IF v_table_id IS NULL THEN
      RAISE EXCEPTION 'A mesa selecionada não existe neste restaurante.';
    END IF;
    IF v_seats < p_party_size THEN
      RAISE EXCEPTION 'A mesa não tem capacidade para este número de pessoas.';
    END IF;

    -- Recheck after the table lock to catch a concurrent identical retry.
    SELECT r.* INTO v_existing
    FROM public.restaurant_reservations AS r
    WHERE r.restaurant_id = p_restaurant_id
      AND r.created_by = (SELECT auth.uid())
      AND r.client_request_id = p_client_request_id
    FOR UPDATE;

    IF FOUND THEN
      IF v_existing.guest_name = v_name
         AND v_existing.guest_phone = v_phone
         AND v_existing.party_size IS NOT DISTINCT FROM p_party_size
         AND v_existing.reservation_at IS NOT DISTINCT FROM p_reservation_at
         AND v_existing.duration_minutes IS NOT DISTINCT FROM p_duration_minutes
         AND v_existing.table_number IS NOT DISTINCT FROM p_table_number
         AND v_existing.notes = v_notes THEN
        RETURN v_existing.id;
      END IF;
      RAISE EXCEPTION 'Esta tentativa já foi utilizada com dados diferentes. Atualiza as reservas antes de voltar a tentar.';
    END IF;

    IF EXISTS (
      SELECT 1
      FROM public.restaurant_reservations AS r
      WHERE r.restaurant_id = p_restaurant_id
        AND r.table_number = p_table_number
        AND r.status IN ('Pendente', 'Confirmada')
        AND r.reservation_at < p_reservation_at + (p_duration_minutes * interval '1 minute')
        AND p_reservation_at < r.reservation_at + (r.duration_minutes * interval '1 minute')
    ) THEN
      RAISE EXCEPTION 'Esta mesa já tem uma reserva pendente ou confirmada nesse horário.';
    END IF;
  END IF;

  INSERT INTO public.restaurant_reservations
    (client_request_id, restaurant_id, guest_name, guest_phone, party_size, reservation_at,
     duration_minutes, table_number, status, notes, created_by, updated_at)
  VALUES
    (p_client_request_id, p_restaurant_id, v_name, v_phone, p_party_size, p_reservation_at,
     p_duration_minutes, p_table_number, 'Pendente', v_notes, (SELECT auth.uid()), now())
  ON CONFLICT (restaurant_id, created_by, client_request_id)
    WHERE client_request_id IS NOT NULL
    DO NOTHING
  RETURNING id INTO v_reservation_id;

  IF v_reservation_id IS NULL THEN
    SELECT r.* INTO v_existing
    FROM public.restaurant_reservations AS r
    WHERE r.restaurant_id = p_restaurant_id
      AND r.created_by = (SELECT auth.uid())
      AND r.client_request_id = p_client_request_id
    FOR UPDATE;

    IF FOUND
       AND v_existing.guest_name = v_name
       AND v_existing.guest_phone = v_phone
       AND v_existing.party_size IS NOT DISTINCT FROM p_party_size
       AND v_existing.reservation_at IS NOT DISTINCT FROM p_reservation_at
       AND v_existing.duration_minutes IS NOT DISTINCT FROM p_duration_minutes
       AND v_existing.table_number IS NOT DISTINCT FROM p_table_number
       AND v_existing.notes = v_notes THEN
      RETURN v_existing.id;
    END IF;
    RAISE EXCEPTION 'Não foi possível confirmar a reserva. Atualiza a lista antes de voltar a tentar.';
  END IF;

  RETURN v_reservation_id;
END;
$function$;

REVOKE ALL ON FUNCTION public.create_restaurant_reservation_idempotent(uuid, uuid, text, text, integer, timestamptz, integer, integer, text)
  FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_restaurant_reservation_idempotent(uuid, uuid, text, text, integer, timestamptz, integer, integer, text)
  TO authenticated;

COMMIT;
