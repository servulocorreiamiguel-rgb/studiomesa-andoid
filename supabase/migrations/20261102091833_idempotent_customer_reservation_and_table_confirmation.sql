-- Preserve idempotent retries if a restaurant assigns a table after the
-- customer's first request; require table assignment before confirmation.
create or replace function public.create_customer_reservation_request(
    p_client_request_id uuid,
    p_guest_name text,
    p_guest_phone text,
    p_party_size integer,
    p_reservation_at timestamptz,
    p_duration_minutes integer default 90,
    p_notes text default null
)
returns table (reservation_id uuid, status text)
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_user_id uuid := (select auth.uid());
    v_restaurant_id uuid;
    v_name text := trim(coalesce(p_guest_name, ''));
    v_phone text := trim(coalesce(p_guest_phone, ''));
    v_notes text := trim(coalesce(p_notes, ''));
    v_existing public.restaurant_reservations%rowtype;
    v_reservation_id uuid;
begin
    if v_user_id is null then raise exception 'É necessário iniciar sessão.'; end if;
    if p_client_request_id is null then raise exception 'O identificador seguro do pedido está em falta.'; end if;
    select lm.restaurant_id into v_restaurant_id
    from public.loyalty_members lm where lm.auth_user_id = v_user_id
    order by lm.created_at asc limit 1;
    if v_restaurant_id is null then raise exception 'Associa primeiro a tua conta ao restaurante.'; end if;

    -- Do not require table_number to remain NULL on retry: the restaurant may
    -- already have assigned a table after the first request was committed.
    select r.* into v_existing
    from public.restaurant_reservations r
    where r.restaurant_id = v_restaurant_id
      and r.created_by = v_user_id
      and r.client_request_id = p_client_request_id
    for update;
    if found then
        if v_existing.guest_name = v_name
           and v_existing.guest_phone = v_phone
           and v_existing.party_size = p_party_size
           and v_existing.reservation_at = p_reservation_at
           and v_existing.duration_minutes = p_duration_minutes
           and v_existing.notes = v_notes then
            return query select v_existing.id, v_existing.status;
            return;
        end if;
        raise exception 'Esta tentativa já foi utilizada com dados diferentes.';
    end if;

    if char_length(v_name) not between 1 and 100 then raise exception 'O nome deve ter entre 1 e 100 caracteres.'; end if;
    if char_length(v_phone) > 40 or char_length(v_notes) > 1000 then raise exception 'O contacto ou as notas excedem o limite permitido.'; end if;
    if p_party_size is null or p_party_size not between 1 and 60 then raise exception 'O número de pessoas deve estar entre 1 e 60.'; end if;
    if p_reservation_at is null or p_reservation_at < now() then raise exception 'A data da reserva tem de ser futura.'; end if;
    if p_reservation_at > now() + interval '1 year' then raise exception 'A reserva não pode estar a mais de um ano de distância.'; end if;
    if p_duration_minutes is null or p_duration_minutes not between 30 and 480 then raise exception 'A duração deve estar entre 30 e 480 minutos.'; end if;

    insert into public.restaurant_reservations (
        restaurant_id, guest_name, guest_phone, party_size, reservation_at,
        duration_minutes, table_number, status, notes, created_by, client_request_id, updated_at
    ) values (
        v_restaurant_id, v_name, v_phone, p_party_size, p_reservation_at,
        p_duration_minutes, null, 'Pendente', v_notes, v_user_id, p_client_request_id, now()
    )
    on conflict (restaurant_id, created_by, client_request_id)
        where client_request_id is not null do nothing
    returning id into v_reservation_id;

    if v_reservation_id is null then
        select r.* into v_existing
        from public.restaurant_reservations r
        where r.restaurant_id = v_restaurant_id
          and r.created_by = v_user_id
          and r.client_request_id = p_client_request_id
        for update;
        if found
           and v_existing.guest_name = v_name
           and v_existing.guest_phone = v_phone
           and v_existing.party_size = p_party_size
           and v_existing.reservation_at = p_reservation_at
           and v_existing.duration_minutes = p_duration_minutes
           and v_existing.notes = v_notes then
            return query select v_existing.id, v_existing.status;
            return;
        end if;
        raise exception 'Não foi possível confirmar o pedido de reserva. Atualiza a lista antes de voltar a tentar.';
    end if;
    return query select v_reservation_id, 'Pendente'::text;
end;
$function$;

create or replace function public.update_restaurant_reservation_status(
    p_reservation_id uuid, p_status text
)
returns void
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_restaurant_id uuid;
    v_current_status text;
    v_table_number integer;
begin
    if (select auth.uid()) is null then raise exception 'É necessário iniciar sessão.'; end if;
    if p_status not in ('Pendente', 'Confirmada', 'Cancelada', 'Concluída', 'Não compareceu') then
        raise exception 'Estado de reserva inválido.';
    end if;
    select r.restaurant_id, r.status, r.table_number
    into v_restaurant_id, v_current_status, v_table_number
    from public.restaurant_reservations r
    where r.id = p_reservation_id
    for update;
    if v_restaurant_id is null then raise exception 'Reserva não encontrada.'; end if;
    if not private.is_restaurant_staff(v_restaurant_id) then
        raise exception 'Sem autorização para alterar esta reserva.';
    end if;

    -- An already-applied status remains a safe retry, including for legacy rows.
    if v_current_status = p_status then return; end if;
    if p_status = 'Confirmada' and v_table_number is null then
        raise exception 'Atribui uma mesa antes de confirmar esta reserva.';
    end if;
    if v_current_status in ('Cancelada', 'Concluída', 'Não compareceu') then
        raise exception 'A reserva já terminou; cria uma nova reserva se precisares de a reabrir.';
    end if;
    if v_current_status = 'Pendente' and p_status not in ('Confirmada', 'Cancelada') then
        raise exception 'Uma reserva pendente só pode ser confirmada ou cancelada.';
    end if;
    if v_current_status = 'Confirmada' and p_status not in ('Concluída', 'Cancelada', 'Não compareceu') then
        raise exception 'Uma reserva confirmada só pode ser concluída, cancelada ou marcada como não compareceu.';
    end if;
    update public.restaurant_reservations
    set status = p_status, updated_at = now()
    where id = p_reservation_id;
end;
$function$;

revoke all on function public.create_customer_reservation_request(uuid,text,text,integer,timestamptz,integer,text)
    from public, anon, authenticated;
grant execute on function public.create_customer_reservation_request(uuid,text,text,integer,timestamptz,integer,text)
    to authenticated;
revoke all on function public.update_restaurant_reservation_status(uuid,text)
    from public, anon, authenticated;
grant execute on function public.update_restaurant_reservation_status(uuid,text)
    to authenticated;