-- Version the customer reservation flow and keep privileged database work
-- behind SECURITY INVOKER public RPCs. The customer can only request/cancel
-- reservations attached to their own loyalty membership; restaurant staff can
-- assign tables only inside their restaurant.
BEGIN;

GRANT USAGE ON SCHEMA private TO authenticated;

CREATE OR REPLACE FUNCTION private.create_customer_reservation_request_internal(
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
    IF p_client_request_id IS NULL THEN
        RAISE EXCEPTION 'O identificador seguro do pedido está em falta.';
    END IF;

    SELECT lm.restaurant_id
      INTO v_restaurant_id
      FROM public.loyalty_members AS lm
     WHERE lm.auth_user_id = v_user_id
     ORDER BY lm.created_at ASC
     LIMIT 1;

    IF v_restaurant_id IS NULL THEN
        RAISE EXCEPTION 'Associa primeiro a tua conta ao restaurante.';
    END IF;

    -- Check for an earlier committed request before validating the current
    -- clock window: retries must return the original result, not create a new one.
    SELECT r.*
      INTO v_existing
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
        duration_minutes, table_number, status, notes, created_by,
        client_request_id, updated_at
    )
    VALUES (
        v_restaurant_id, v_name, v_phone, p_party_size, p_reservation_at,
        p_duration_minutes, NULL, 'Pendente', v_notes, v_user_id,
        p_client_request_id, now()
    )
    ON CONFLICT (restaurant_id, created_by, client_request_id)
        WHERE client_request_id IS NOT NULL
    DO NOTHING
    RETURNING id INTO v_reservation_id;

    IF v_reservation_id IS NULL THEN
        SELECT r.*
          INTO v_existing
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

CREATE OR REPLACE FUNCTION private.get_my_restaurant_reservations_internal()
RETURNS TABLE (
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

    SELECT lm.restaurant_id
      INTO v_restaurant_id
      FROM public.loyalty_members AS lm
     WHERE lm.auth_user_id = v_user_id
     ORDER BY lm.created_at ASC
     LIMIT 1;

    IF v_restaurant_id IS NULL THEN
        RETURN;
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

CREATE OR REPLACE FUNCTION private.cancel_my_restaurant_reservation_internal(
    p_reservation_id uuid
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_user_id uuid := (SELECT auth.uid());
    v_restaurant_id uuid;
    v_status text;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'É necessário iniciar sessão.';
    END IF;

    SELECT lm.restaurant_id
      INTO v_restaurant_id
      FROM public.loyalty_members AS lm
     WHERE lm.auth_user_id = v_user_id
     ORDER BY lm.created_at ASC
     LIMIT 1;

    IF v_restaurant_id IS NULL THEN
        RAISE EXCEPTION 'Associa primeiro a tua conta ao restaurante.';
    END IF;

    SELECT r.status
      INTO v_status
      FROM public.restaurant_reservations AS r
     WHERE r.id = p_reservation_id
       AND r.restaurant_id = v_restaurant_id
       AND r.created_by = v_user_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Esta reserva não pertence à tua conta.';
    END IF;
    IF v_status = 'Cancelada' THEN
        RETURN;
    END IF;
    IF v_status NOT IN ('Pendente', 'Confirmada') THEN
        RAISE EXCEPTION 'Só podes cancelar pedidos pendentes ou reservas confirmadas.';
    END IF;

    UPDATE public.restaurant_reservations
       SET status = 'Cancelada',
           updated_at = now()
     WHERE id = p_reservation_id
       AND restaurant_id = v_restaurant_id
       AND created_by = v_user_id;
END;
$function$;

CREATE OR REPLACE FUNCTION private.assign_restaurant_reservation_table_internal(
    p_reservation_id uuid,
    p_table_number integer
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_restaurant_id uuid;
    v_reservation public.restaurant_reservations%ROWTYPE;
    v_table_id uuid;
    v_table_seats integer;
BEGIN
    IF (SELECT auth.uid()) IS NULL THEN
        RAISE EXCEPTION 'É necessário iniciar sessão.';
    END IF;
    IF p_table_number IS NULL OR p_table_number NOT BETWEEN 1 AND 100 THEN
        RAISE EXCEPTION 'Seleciona uma mesa válida.';
    END IF;

    SELECT r.*
      INTO v_reservation
      FROM public.restaurant_reservations AS r
     WHERE r.id = p_reservation_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Reserva não encontrada.';
    END IF;

    v_restaurant_id := v_reservation.restaurant_id;
    IF NOT private.is_restaurant_staff(v_restaurant_id) THEN
        RAISE EXCEPTION 'Sem autorização para gerir reservas deste restaurante.';
    END IF;
    IF v_reservation.status <> 'Pendente' THEN
        RAISE EXCEPTION 'Só podes atribuir mesa a um pedido de reserva pendente.';
    END IF;

    SELECT t.id, t.seats
      INTO v_table_id, v_table_seats
      FROM public.restaurant_tables AS t
     WHERE t.restaurant_id = v_restaurant_id
       AND t.table_number = p_table_number
     FOR UPDATE;

    IF v_table_id IS NULL THEN
        RAISE EXCEPTION 'A mesa selecionada não existe neste restaurante.';
    END IF;
    IF v_table_seats < v_reservation.party_size THEN
        RAISE EXCEPTION 'A mesa não tem capacidade para este número de pessoas.';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM public.restaurant_reservations AS r
         WHERE r.restaurant_id = v_restaurant_id
           AND r.table_number = p_table_number
           AND r.id <> p_reservation_id
           AND r.status IN ('Pendente', 'Confirmada')
           AND r.reservation_at < v_reservation.reservation_at
                                      + (v_reservation.duration_minutes * interval '1 minute')
           AND v_reservation.reservation_at < r.reservation_at
                                      + (r.duration_minutes * interval '1 minute')
    ) THEN
        RAISE EXCEPTION 'Esta mesa já tem uma reserva pendente ou confirmada nesse horário.';
    END IF;

    UPDATE public.restaurant_reservations
       SET table_number = p_table_number,
           updated_at = now()
     WHERE id = p_reservation_id
       AND restaurant_id = v_restaurant_id;
END;
$function$;

REVOKE ALL ON FUNCTION private.create_customer_reservation_request_internal(uuid, text, text, integer, timestamptz, integer, text)
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private.get_my_restaurant_reservations_internal()
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private.cancel_my_restaurant_reservation_internal(uuid)
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private.assign_restaurant_reservation_table_internal(uuid, integer)
    FROM PUBLIC, anon, authenticated;

GRANT EXECUTE ON FUNCTION private.create_customer_reservation_request_internal(uuid, text, text, integer, timestamptz, integer, text)
    TO authenticated;
GRANT EXECUTE ON FUNCTION private.get_my_restaurant_reservations_internal()
    TO authenticated;
GRANT EXECUTE ON FUNCTION private.cancel_my_restaurant_reservation_internal(uuid)
    TO authenticated;
GRANT EXECUTE ON FUNCTION private.assign_restaurant_reservation_table_internal(uuid, integer)
    TO authenticated;

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
SECURITY INVOKER
SET search_path TO ''
AS $function$
    SELECT *
    FROM private.create_customer_reservation_request_internal(
        p_client_request_id, p_guest_name, p_guest_phone, p_party_size,
        p_reservation_at, p_duration_minutes, p_notes
    );
$function$;

CREATE OR REPLACE FUNCTION public.get_my_restaurant_reservations()
RETURNS TABLE (
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
SECURITY INVOKER
SET search_path TO ''
AS $function$
    SELECT * FROM private.get_my_restaurant_reservations_internal();
$function$;

CREATE OR REPLACE FUNCTION public.cancel_my_restaurant_reservation(p_reservation_id uuid)
RETURNS void
LANGUAGE sql
SECURITY INVOKER
SET search_path TO ''
AS $function$
    SELECT private.cancel_my_restaurant_reservation_internal(p_reservation_id);
$function$;

CREATE OR REPLACE FUNCTION public.assign_restaurant_reservation_table(
    p_reservation_id uuid,
    p_table_number integer
)
RETURNS void
LANGUAGE sql
SECURITY INVOKER
SET search_path TO ''
AS $function$
    SELECT private.assign_restaurant_reservation_table_internal(
        p_reservation_id, p_table_number
    );
$function$;

REVOKE ALL ON FUNCTION public.create_customer_reservation_request(uuid, text, text, integer, timestamptz, integer, text)
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.get_my_restaurant_reservations()
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.cancel_my_restaurant_reservation(uuid)
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.assign_restaurant_reservation_table(uuid, integer)
    FROM PUBLIC, anon, authenticated;

GRANT EXECUTE ON FUNCTION public.create_customer_reservation_request(uuid, text, text, integer, timestamptz, integer, text)
    TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_my_restaurant_reservations()
    TO authenticated;
GRANT EXECUTE ON FUNCTION public.cancel_my_restaurant_reservation(uuid)
    TO authenticated;
GRANT EXECUTE ON FUNCTION public.assign_restaurant_reservation_table(uuid, integer)
    TO authenticated;

COMMIT;
