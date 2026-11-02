-- StudioMesa loyalty shop, one-time customer QR tokens and secure redemptions.
-- Customers can only mint their own short-lived QR token.
-- Staff can award points once per token; the server validates roles, balances and retries.
-- Reward catalog writes are restricted to restaurant owners.

begin;

create table if not exists public.loyalty_rewards (
    id uuid primary key default gen_random_uuid(),
    restaurant_id uuid not null references public.restaurants(id) on delete cascade,
    name text not null check (char_length(trim(name)) between 1 and 100),
    description text not null default '' check (char_length(description) <= 500),
    points_cost integer not null check (points_cost between 1 and 1000000),
    is_active boolean not null default true,
    stock_limit integer check (stock_limit is null or stock_limit >= 0),
    redeemed_count integer not null default 0 check (redeemed_count >= 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists loyalty_rewards_restaurant_active_idx
    on public.loyalty_rewards (restaurant_id, is_active, points_cost);

create table if not exists public.loyalty_reward_redemptions (
    id uuid primary key default gen_random_uuid(),
    restaurant_id uuid not null references public.restaurants(id) on delete cascade,
    member_id uuid not null references public.loyalty_members(id) on delete cascade,
    reward_id uuid not null references public.loyalty_rewards(id) on delete cascade,
    points_spent integer not null check (points_spent > 0),
    status text not null default 'pending' check (status in ('pending', 'fulfilled')),
    redeemed_by uuid not null references auth.users(id),
    redeemed_at timestamptz not null default now(),
    fulfilled_by uuid references auth.users(id) on delete set null,
    fulfilled_at timestamptz,
    client_request_id uuid not null,
    constraint loyalty_reward_redemptions_request_key unique (member_id, client_request_id)
);

create index if not exists loyalty_reward_redemptions_restaurant_status_idx
    on public.loyalty_reward_redemptions (restaurant_id, status, redeemed_at desc);

create table if not exists private.loyalty_qr_tokens (
    token uuid primary key default gen_random_uuid(),
    restaurant_id uuid not null references public.restaurants(id) on delete cascade,
    member_id uuid not null references public.loyalty_members(id) on delete cascade,
    issued_at timestamptz not null default now(),
    expires_at timestamptz not null,
    used_at timestamptz
);

create index if not exists loyalty_qr_tokens_member_expiry_idx
    on private.loyalty_qr_tokens (member_id, expires_at desc);

alter table public.loyalty_rewards enable row level security;
alter table public.loyalty_reward_redemptions enable row level security;
alter table private.loyalty_qr_tokens enable row level security;

revoke all on table public.loyalty_rewards, public.loyalty_reward_redemptions
    from public, anon, authenticated, service_role;
revoke all on table private.loyalty_qr_tokens
    from public, anon, authenticated, service_role;

create or replace function public.save_loyalty_reward(
    p_restaurant_id uuid, p_reward_id uuid, p_name text, p_description text,
    p_points_cost integer, p_stock_limit integer, p_is_active boolean
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_name text := trim(coalesce(p_name, ''));
    v_description text := trim(coalesce(p_description, ''));
    v_reward_id uuid;
begin
    if (select auth.uid()) is null then
        raise exception 'É necessário iniciar sessão.';
    end if;
    if p_restaurant_id is null or not exists (
        select 1 from public.restaurant_staff rs
        where rs.restaurant_id = p_restaurant_id
          and rs.user_id = (select auth.uid())
          and rs.role = 'owner'
    ) then
        raise exception 'Apenas o proprietário pode gerir a loja de pontos.';
    end if;
    if p_reward_id is null then
        raise exception 'O identificador da recompensa está em falta.';
    end if;
    if char_length(v_name) not between 1 and 100
       or char_length(v_description) > 500
       or p_points_cost is null or p_points_cost not between 1 and 1000000
       or (p_stock_limit is not null and p_stock_limit < 0)
       or p_is_active is null then
        raise exception 'Os dados da recompensa não são válidos.';
    end if;

    insert into public.loyalty_rewards
        (id, restaurant_id, name, description, points_cost, stock_limit, is_active)
    values
        (p_reward_id, p_restaurant_id, v_name, v_description, p_points_cost, p_stock_limit, p_is_active)
    on conflict (id) do update
       set name = excluded.name,
           description = excluded.description,
           points_cost = excluded.points_cost,
           stock_limit = excluded.stock_limit,
           is_active = excluded.is_active,
           updated_at = now()
     where public.loyalty_rewards.restaurant_id = excluded.restaurant_id
    returning id into v_reward_id;

    if v_reward_id is null then
        raise exception 'A recompensa não existe neste restaurante.';
    end if;
    return v_reward_id;
end;
$function$;

create or replace function public.get_restaurant_loyalty_rewards(p_restaurant_id uuid)
returns table (id uuid, name text, description text, points_cost integer,
               is_active boolean, stock_limit integer, redeemed_count integer)
language plpgsql stable security definer set search_path = ''
as $function$
begin
    if (select auth.uid()) is null or p_restaurant_id is null
       or not private.is_restaurant_staff(p_restaurant_id) then
        raise exception 'Sem autorização para consultar a loja de pontos.';
    end if;
    return query
    select r.id, r.name, r.description, r.points_cost,
           r.is_active, r.stock_limit, r.redeemed_count
    from public.loyalty_rewards r
    where r.restaurant_id = p_restaurant_id
    order by r.is_active desc, r.points_cost asc, r.name asc;
end;
$function$;

create or replace function public.get_customer_loyalty_rewards(p_member_id uuid)
returns table (id uuid, name text, description text, points_cost integer,
               stock_limit integer, redeemed_count integer)
language plpgsql stable security definer set search_path = ''
as $function$
declare
    v_restaurant_id uuid;
begin
    if (select auth.uid()) is null then
        raise exception 'É necessário iniciar sessão.';
    end if;
    select lm.restaurant_id into v_restaurant_id
    from public.loyalty_members lm
    where lm.id = p_member_id and lm.auth_user_id = (select auth.uid());
    if v_restaurant_id is null then
        raise exception 'O cartão de cliente não pertence a esta conta.';
    end if;
    return query
    select r.id, r.name, r.description, r.points_cost, r.stock_limit, r.redeemed_count
    from public.loyalty_rewards r
    where r.restaurant_id = v_restaurant_id and r.is_active
      and (r.stock_limit is null or r.redeemed_count < r.stock_limit)
    order by r.points_cost asc, r.name asc;
end;
$function$;

create or replace function public.create_loyalty_qr_token()
returns table (qr_token uuid, expires_at timestamptz)
language plpgsql security definer set search_path = ''
as $function$
declare
    v_member_id uuid;
    v_restaurant_id uuid;
    v_token uuid;
    v_expires_at timestamptz := now() + interval '2 minutes';
begin
    if (select auth.uid()) is null then
        raise exception 'É necessário iniciar sessão.';
    end if;
    select lm.id, lm.restaurant_id into v_member_id, v_restaurant_id
    from public.loyalty_members lm
    where lm.auth_user_id = (select auth.uid())
    order by lm.created_at asc limit 1
    for update;
    if v_member_id is null then
        raise exception 'Associa primeiro a tua conta ao restaurante.';
    end if;

    -- Only the newest code remains valid for this customer.
    delete from private.loyalty_qr_tokens
    where member_id = v_member_id and used_at is null;
    delete from private.loyalty_qr_tokens
    where member_id = v_member_id and expires_at < now() - interval '1 day';
    insert into private.loyalty_qr_tokens (restaurant_id, member_id, expires_at)
    values (v_restaurant_id, v_member_id, v_expires_at)
    returning token into v_token;
    return query select v_token, v_expires_at;
end;
$function$;

create or replace function public.award_loyalty_points_from_qr(
    p_qr_token uuid, p_delta integer, p_client_request_id uuid, p_note text default null
)
returns table (member_id uuid, member_code text, points integer)
language plpgsql security definer set search_path = ''
as $function$
declare
    v_token private.loyalty_qr_tokens%rowtype;
    v_member public.loyalty_members%rowtype;
    v_existing public.loyalty_transactions%rowtype;
    v_note text := nullif(trim(coalesce(p_note, '')), '');
    v_new_points integer;
begin
    if (select auth.uid()) is null then raise exception 'É necessário iniciar sessão.'; end if;
    if p_qr_token is null or p_client_request_id is null then
        raise exception 'O QR ou o identificador desta operação está em falta.';
    end if;
    if p_delta is null or p_delta not between 1 and 100000 then
        raise exception 'Os pontos a atribuir têm de estar entre 1 e 100000.';
    end if;
    if char_length(coalesce(v_note, '')) > 200 then
        raise exception 'A descrição excede o limite permitido.';
    end if;

    -- Check a committed request before looking up the QR: the customer may have
    -- generated a new token, or the original may have expired, after the award committed.
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
        select lm.* into v_member from public.loyalty_members lm where lm.id = v_existing.member_id;
        return query select v_member.id, v_member.member_code, v_member.points;
        return;
    end if;

    select t.* into v_token
    from private.loyalty_qr_tokens t where t.token = p_qr_token for update;
    if not found then raise exception 'QR inválido. Pede ao cliente que apresente um código novo.'; end if;
    if not private.is_restaurant_staff(v_token.restaurant_id) then
        raise exception 'Sem autorização para atribuir pontos neste restaurante.';
    end if;

    -- Recheck after taking the QR lock to serialize concurrent duplicate requests.
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
        select lm.* into v_member from public.loyalty_members lm where lm.id = v_token.member_id;
        return query select v_member.id, v_member.member_code, v_member.points;
        return;
    end if;

    if v_token.used_at is not null then raise exception 'Este QR já foi utilizado. Pede ao cliente um código novo.'; end if;
    if v_token.expires_at <= now() then raise exception 'Este QR expirou. Pede ao cliente que gere outro.'; end if;

    select lm.* into v_member
    from public.loyalty_members lm
    where lm.id = v_token.member_id and lm.restaurant_id = v_token.restaurant_id
    for update;
    if not found then raise exception 'Cartão de cliente não encontrado.'; end if;

    v_new_points := v_member.points + p_delta;
    if v_new_points < v_member.points then raise exception 'O saldo de pontos excederia o limite permitido.'; end if;

    update private.loyalty_qr_tokens set used_at = now() where token = p_qr_token;
    update public.loyalty_members set points = v_new_points where id = v_member.id;
    insert into public.loyalty_transactions
        (restaurant_id, member_id, created_by, points_delta, points_after, note, client_request_id)
    values
        (v_token.restaurant_id, v_member.id, (select auth.uid()), p_delta, v_new_points,
         coalesce(v_note, 'Pontos atribuídos através de QR'), p_client_request_id);
    return query select v_member.id, v_member.member_code, v_new_points;
end;
$function$;

create or replace function public.redeem_loyalty_reward(p_reward_id uuid, p_client_request_id uuid)
returns table (redemption_id uuid, reward_name text, points_spent integer,
               points_remaining integer, status text)
language plpgsql security definer set search_path = ''
as $function$
declare
    v_member public.loyalty_members%rowtype;
    v_reward public.loyalty_rewards%rowtype;
    v_existing public.loyalty_reward_redemptions%rowtype;
    v_redemption_id uuid;
    v_remaining integer;
begin
    if (select auth.uid()) is null then raise exception 'É necessário iniciar sessão.'; end if;
    if p_reward_id is null or p_client_request_id is null then
        raise exception 'A recompensa ou o identificador de resgate está em falta.';
    end if;

    select lm.* into v_member
    from public.loyalty_members lm
    where lm.auth_user_id = (select auth.uid())
    order by lm.created_at asc limit 1
    for update;
    if not found then raise exception 'Não existe um cartão de fidelização associado a esta conta.'; end if;

    select rr.* into v_existing
    from public.loyalty_reward_redemptions rr
    where rr.member_id = v_member.id and rr.client_request_id = p_client_request_id
    for update;
    if found then
        if v_existing.reward_id <> p_reward_id then
            raise exception 'Este identificador já foi utilizado para outra recompensa.';
        end if;
        select r.name into v_reward.name from public.loyalty_rewards r where r.id = p_reward_id;
        return query select v_existing.id, v_reward.name, v_existing.points_spent,
                             v_member.points, v_existing.status;
        return;
    end if;

    select r.* into v_reward
    from public.loyalty_rewards r
    where r.id = p_reward_id and r.restaurant_id = v_member.restaurant_id and r.is_active
    for update;
    if not found then raise exception 'Esta recompensa já não está disponível.'; end if;
    if v_reward.stock_limit is not null and v_reward.redeemed_count >= v_reward.stock_limit then
        raise exception 'Esta recompensa já esgotou.';
    end if;
    if v_member.points < v_reward.points_cost then
        raise exception 'Não tens pontos suficientes para esta recompensa.';
    end if;

    v_remaining := v_member.points - v_reward.points_cost;
    update public.loyalty_members set points = v_remaining where id = v_member.id;
    update public.loyalty_rewards
       set redeemed_count = redeemed_count + 1, updated_at = now()
     where id = v_reward.id;

    insert into public.loyalty_reward_redemptions
        (restaurant_id, member_id, reward_id, points_spent, redeemed_by, client_request_id)
    values
        (v_member.restaurant_id, v_member.id, v_reward.id, v_reward.points_cost,
         (select auth.uid()), p_client_request_id)
    returning id into v_redemption_id;

    insert into public.loyalty_transactions
        (restaurant_id, member_id, created_by, points_delta, points_after, note, client_request_id)
    values
        (v_member.restaurant_id, v_member.id, (select auth.uid()), -v_reward.points_cost,
         v_remaining, 'Resgate: ' || v_reward.name, p_client_request_id);
    return query select v_redemption_id, v_reward.name, v_reward.points_cost, v_remaining, 'pending'::text;
end;
$function$;

create or replace function public.get_restaurant_reward_redemptions(p_restaurant_id uuid)
returns table (redemption_id uuid, customer_name text, reward_name text,
               points_spent integer, status text, redeemed_at timestamptz)
language plpgsql stable security definer set search_path = ''
as $function$
begin
    if (select auth.uid()) is null or p_restaurant_id is null
       or not private.is_restaurant_staff(p_restaurant_id) then
        raise exception 'Sem autorização para consultar os resgates deste restaurante.';
    end if;
    return query
    select rr.id, lm.display_name, r.name, rr.points_spent, rr.status, rr.redeemed_at
    from public.loyalty_reward_redemptions rr
    join public.loyalty_members lm on lm.id = rr.member_id
    join public.loyalty_rewards r on r.id = rr.reward_id
    where rr.restaurant_id = p_restaurant_id
    order by rr.redeemed_at desc limit 100;
end;
$function$;

create or replace function public.fulfill_loyalty_reward_redemption(p_redemption_id uuid)
returns void
language plpgsql security definer set search_path = ''
as $function$
declare
    v_restaurant_id uuid;
    v_status text;
begin
    if (select auth.uid()) is null then raise exception 'É necessário iniciar sessão.'; end if;
    select rr.restaurant_id, rr.status into v_restaurant_id, v_status
    from public.loyalty_reward_redemptions rr
    where rr.id = p_redemption_id for update;
    if v_restaurant_id is null or not private.is_restaurant_staff(v_restaurant_id) then
        raise exception 'Sem autorização para confirmar este resgate.';
    end if;
    if v_status = 'fulfilled' then return; end if;
    update public.loyalty_reward_redemptions
       set status = 'fulfilled', fulfilled_by = (select auth.uid()), fulfilled_at = now()
     where id = p_redemption_id and status = 'pending';
end;
$function$;

revoke all on function public.save_loyalty_reward(uuid, uuid, text, text, integer, integer, boolean) from public, anon, authenticated;
revoke all on function public.get_restaurant_loyalty_rewards(uuid) from public, anon, authenticated;
revoke all on function public.get_customer_loyalty_rewards(uuid) from public, anon, authenticated;
revoke all on function public.create_loyalty_qr_token() from public, anon, authenticated;
revoke all on function public.award_loyalty_points_from_qr(uuid, integer, uuid, text) from public, anon, authenticated;
revoke all on function public.redeem_loyalty_reward(uuid, uuid) from public, anon, authenticated;
revoke all on function public.get_restaurant_reward_redemptions(uuid) from public, anon, authenticated;
revoke all on function public.fulfill_loyalty_reward_redemption(uuid) from public, anon, authenticated;

grant execute on function public.save_loyalty_reward(uuid, uuid, text, text, integer, integer, boolean) to authenticated;
grant execute on function public.get_restaurant_loyalty_rewards(uuid) to authenticated;
grant execute on function public.get_customer_loyalty_rewards(uuid) to authenticated;
grant execute on function public.create_loyalty_qr_token() to authenticated;
grant execute on function public.award_loyalty_points_from_qr(uuid, integer, uuid, text) to authenticated;
grant execute on function public.redeem_loyalty_reward(uuid, uuid) to authenticated;
grant execute on function public.get_restaurant_reward_redemptions(uuid) to authenticated;
grant execute on function public.fulfill_loyalty_reward_redemption(uuid) to authenticated;

commit;