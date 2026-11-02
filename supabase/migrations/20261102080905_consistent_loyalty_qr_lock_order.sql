-- Serialize QR generation and QR consumption using the same lock order:
-- loyalty member first, then the QR token. This avoids a deadlock if a customer
-- refreshes their QR while a staff member is crediting the previous one.
create or replace function public.award_loyalty_points_from_qr(
    p_qr_token uuid, p_delta integer, p_client_request_id uuid, p_note text default null
)
returns table (member_id uuid, member_code text, points integer)
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_token private.loyalty_qr_tokens%rowtype;
    v_member public.loyalty_members%rowtype;
    v_existing public.loyalty_transactions%rowtype;
    v_note text := nullif(trim(coalesce(p_note, '')), '');
    v_new_points integer;
    v_member_id uuid;
    v_restaurant_id uuid;
begin
    if (select auth.uid()) is null then
        raise exception 'É necessário iniciar sessão.';
    end if;
    if p_qr_token is null or p_client_request_id is null then
        raise exception 'O QR ou o identificador desta operação está em falta.';
    end if;
    if p_delta is null or p_delta not between 1 and 100000 then
        raise exception 'Os pontos a atribuir têm de estar entre 1 e 100000.';
    end if;
    if char_length(coalesce(v_note, '')) > 200 then
        raise exception 'A descrição excede o limite permitido.';
    end if;

    -- Return an already committed request even if the customer has since
    -- generated a replacement QR or the original token has expired.
    select t.* into v_existing
    from public.loyalty_transactions t
    where t.created_by = (select auth.uid())
      and t.client_request_id = p_client_request_id
    for update;
    if found then
        if v_existing.points_delta <> p_delta
           or v_existing.note is distinct from coalesce(v_note, 'Pontos atribuídos através de QR')
           or not private.is_restaurant_staff(v_existing.restaurant_id) then
            raise exception 'Este identificador já foi utilizado noutra atribuição de pontos.';
        end if;
        select lm.* into v_member
        from public.loyalty_members lm
        where lm.id = v_existing.member_id;
        return query select v_member.id, v_member.member_code, v_member.points;
        return;
    end if;

    -- Read the token without locking so we can discover the member to lock first.
    select t.member_id, t.restaurant_id
      into v_member_id, v_restaurant_id
    from private.loyalty_qr_tokens t
    where t.token = p_qr_token;
    if not found then
        raise exception 'QR inválido. Pede ao cliente que apresente um código novo.';
    end if;
    if not private.is_restaurant_staff(v_restaurant_id) then
        raise exception 'Sem autorização para atribuir pontos neste restaurante.';
    end if;

    -- Consistent lock order with create_loyalty_qr_token(): member, then QR token.
    select lm.* into v_member
    from public.loyalty_members lm
    where lm.id = v_member_id
      and lm.restaurant_id = v_restaurant_id
    for update;
    if not found then
        raise exception 'Cartão de cliente não encontrado.';
    end if;

    -- The customer may have replaced the QR while this request waited for the
    -- member lock. Re-read and lock the token to make the final decision atomic.
    select t.* into v_token
    from private.loyalty_qr_tokens t
    where t.token = p_qr_token
      and t.member_id = v_member.id
      and t.restaurant_id = v_member.restaurant_id
    for update;
    if not found then
        raise exception 'Este QR já não está ativo. Pede ao cliente um código novo.';
    end if;

    -- Recheck after both locks; concurrent identical requests return the first result.
    select t.* into v_existing
    from public.loyalty_transactions t
    where t.restaurant_id = v_token.restaurant_id
      and t.created_by = (select auth.uid())
      and t.client_request_id = p_client_request_id
    for update;
    if found then
        if v_existing.member_id <> v_token.member_id
           or v_existing.points_delta <> p_delta
           or v_existing.note is distinct from coalesce(v_note, 'Pontos atribuídos através de QR') then
            raise exception 'Este identificador já foi utilizado noutra atribuição de pontos.';
        end if;
        select lm.* into v_member
        from public.loyalty_members lm
        where lm.id = v_token.member_id;
        return query select v_member.id, v_member.member_code, v_member.points;
        return;
    end if;

    if v_token.used_at is not null then
        raise exception 'Este QR já foi utilizado. Pede ao cliente um código novo.';
    end if;
    if v_token.expires_at <= now() then
        raise exception 'Este QR expirou. Pede ao cliente que gere outro.';
    end if;

    if v_member.points::bigint + p_delta::bigint > 2147483647 then
        raise exception 'O saldo de pontos excederia o limite permitido.';
    end if;
    v_new_points := (v_member.points::bigint + p_delta::bigint)::integer;

    update private.loyalty_qr_tokens
       set used_at = now()
     where token = p_qr_token;
    update public.loyalty_members
       set points = v_new_points
     where id = v_member.id;

    insert into public.loyalty_transactions
        (restaurant_id, member_id, created_by, points_delta, points_after, note, client_request_id)
    values
        (v_token.restaurant_id, v_member.id, (select auth.uid()), p_delta, v_new_points,
         coalesce(v_note, 'Pontos atribuídos através de QR'), p_client_request_id);

    return query select v_member.id, v_member.member_code, v_new_points;
end;
$function$;