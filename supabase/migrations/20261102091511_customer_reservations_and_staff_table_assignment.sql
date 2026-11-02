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
    if v_user_id is null then
        raise exception 'É necessário iniciar sessão.';
    end if;
    if p_client_request_id is null then
        raise exception 'O identificador seguro do pedido está em falta.';
    end if;
    select lm.restaurant_id into v_restaurant_id
    from public.loyalty_members lm
    where lm.auth_user_id = v_user_id
    order by lm.created_at asc limit 1;
    if v_restaurant_id is null then
        raise exception 'Associa primeiro a tua conta ao restaurante.';
    end if;

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
           and v_existing.table_number is null
           and v_existing.notes = v_notes then
            return query select v_existing.id, v_existing.status;
            return;
        end if;
        raise exception 'Esta tentativa já foi utilizada com dados diferentes.';
    end if;

    if char_length(v_name) not between 1 and 100 then
        raise exception 'O nome deve ter entre 1 e 100 caracteres.';
    end if;
    if char_length(v_phone) > 40 or char_length(v_notes) > 1000 then
        raise exception 'O contacto ou as notas excedem o limite permitido.';
    end if;
    if p_party_size is null or p_party_size not between 1 and 60 then
        raise exception 'O número de pessoas deve estar entre 1 e 60.';
    end if;
    if p_reservation_at is null or p_reservation_at < now() then
        raise exception 'A data da reserva tem de ser futura.';
    end if;
    if p_reservation_at > now() + interval '1 year' then
        raise exception 'A reserva não pode estar a mais de um ano de distância.';
    end if;
    if p_duration_minutes is null or p_duration_minutes not between 30 and 480 then
        raise exception 'A duração deve estar entre 30 e 480 minutos.';
    end if;

    insert into public.restaurant_reservations (
        restaurant_id, guest_name, guest_phone, party_size, reservation_at,
        duration_minutes, table_number, status, notes, created_by,
        client_request_id, updated_at
    ) values (
        v_restaurant_id, v_name, v_phone, p_party_size, p_reservation_at,
        p_duration_minutes, null, 'Pendente', v_notes, v_user_id,
        p_client_request_id, now()
    )
    on conflict (restaurant_id, created_by, client_request_id)
        where client_request_id is not null
        do nothing
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
           and v_existing.table_number is null
           and v_existing.notes = v_notes then
            return query select v_existing.id, v_existing.status;
            return;
        end if;
        raise exception 'Não foi possível confirmar o pedido de reserva. Atualiza a lista antes de voltar a tentar.';
    end if;
    return query select v_reservation_id, 'Pendente'::text;
end;
$function$;

create or replace function public.get_my_restaurant_reservations()
returns table (
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
language plpgsql stable security definer
set search_path = ''
as $function$
declare
    v_user_id uuid := (select auth.uid());
    v_restaurant_id uuid;
begin
    if v_user_id is null then
        raise exception 'É necessário iniciar sessão.';
    end if;
    select lm.restaurant_id into v_restaurant_id
    from public.loyalty_members lm
    where lm.auth_user_id = v_user_id
    order by lm.created_at asc limit 1;
    if v_restaurant_id is null then return; end if;
    return query
    select r.id, r.guest_name, r.party_size, r.reservation_at,
           r.duration_minutes, r.table_number, r.status, r.notes, r.created_at
    from public.restaurant_reservations r
    where r.restaurant_id = v_restaurant_id and r.created_by = v_user_id
    order by r.reservation_at desc limit 50;
end;
$function$;

create or replace function public.cancel_my_restaurant_reservation(p_reservation_id uuid)
returns void
language plpgsql security definer
set search_path = ''
as $function$
declare
    v_user_id uuid := (select auth.uid());
    v_restaurant_id uuid;
    v_status text;
begin
    if v_user_id is null then raise exception 'É necessário iniciar sessão.'; end if;
    select lm.restaurant_id into v_restaurant_id
    from public.loyalty_members lm
    where lm.auth_user_id = v_user_id
    order by lm.created_at asc limit 1;
    if v_restaurant_id is null then raise exception 'Associa primeiro a tua conta ao restaurante.'; end if;
    select r.status into v_status
    from public.restaurant_reservations r
    where r.id = p_reservation_id and r.restaurant_id = v_restaurant_id and r.created_by = v_user_id
    for update;
    if not found then raise exception 'Esta reserva não pertence à tua conta.'; end if;
    if v_status = 'Cancelada' then return; end if;
    if v_status not in ('Pendente', 'Confirmada') then
        raise exception 'Só podes cancelar pedidos pendentes ou reservas confirmadas.';
    end if;
    update public.restaurant_reservations
    set status = 'Cancelada', updated_at = now()
    where id = p_reservation_id and restaurant_id = v_restaurant_id and created_by = v_user_id;
end;
$function$;

create or replace function public.assign_restaurant_reservation_table(
    p_reservation_id uuid, p_table_number integer
)
returns void
language plpgsql security definer
set search_path = ''
as $function$
declare
    v_restaurant_id uuid;
    v_reservation public.restaurant_reservations%rowtype;
    v_table_id uuid;
    v_table_seats integer;
begin
    if (select auth.uid()) is null then raise exception 'É necessário iniciar sessão.'; end if;
    if p_table_number is null or p_table_number not between 1 and 100 then
        raise exception 'Seleciona uma mesa válida.';
    end if;
    select r.* into v_reservation
    from public.restaurant_reservations r where r.id = p_reservation_id for update;
    if not found then raise exception 'Reserva não encontrada.'; end if;
    v_restaurant_id := v_reservation.restaurant_id;
    if not private.is_restaurant_staff(v_restaurant_id) then
        raise exception 'Sem autorização para gerir reservas deste restaurante.';
    end if;
    if v_reservation.status <> 'Pendente' then
        raise exception 'Só podes atribuir mesa a um pedido de reserva pendente.';
    end if;
    select t.id, t.seats into v_table_id, v_table_seats
    from public.restaurant_tables t
    where t.restaurant_id = v_restaurant_id and t.table_number = p_table_number
    for update;
    if v_table_id is null then raise exception 'A mesa selecionada não existe neste restaurante.'; end if;
    if v_table_seats < v_reservation.party_size then
        raise exception 'A mesa não tem capacidade para este número de pessoas.';
    end if;
    if exists (
        select 1 from public.restaurant_reservations r
        where r.restaurant_id = v_restaurant_id
          and r.table_number = p_table_number
          and r.id <> p_reservation_id
          and r.status in ('Pendente', 'Confirmada')
          and r.reservation_at < v_reservation.reservation_at
                                  + (v_reservation.duration_minutes * interval '1 minute')
          and v_reservation.reservation_at < r.reservation_at
                                  + (r.duration_minutes * interval '1 minute')
    ) then
        raise exception 'Esta mesa já tem uma reserva pendente ou confirmada nesse horário.';
    end if;
    update public.restaurant_reservations
    set table_number = p_table_number, updated_at = now()
    where id = p_reservation_id and restaurant_id = v_restaurant_id;
end;
$function$;

revoke all on function public.create_customer_reservation_request(uuid,text,text,integer,timestamptz,integer,text)
    from public, anon, authenticated;
grant execute on function public.create_customer_reservation_request(uuid,text,text,integer,timestamptz,integer,text)
    to authenticated;
revoke all on function public.get_my_restaurant_reservations() from public, anon, authenticated;
grant execute on function public.get_my_restaurant_reservations() to authenticated;
revoke all on function public.cancel_my_restaurant_reservation(uuid) from public, anon, authenticated;
grant execute on function public.cancel_my_restaurant_reservation(uuid) to authenticated;
revoke all on function public.assign_restaurant_reservation_table(uuid,integer) from public, anon, authenticated;
grant execute on function public.assign_restaurant_reservation_table(uuid,integer) to authenticated;