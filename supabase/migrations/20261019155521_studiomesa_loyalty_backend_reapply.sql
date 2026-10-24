-- StudioMesa shared loyalty backend (Supabase / PostgreSQL)
-- Run once in Supabase Dashboard > SQL Editor, after creating a project.
-- Designed for authenticated users and Row Level Security. Do not disable RLS.

begin;

create table if not exists public.restaurants (
    id uuid primary key default gen_random_uuid(),
    name text not null check (char_length(trim(name)) between 2 and 100),
    join_code text not null unique check (join_code ~ '^[A-Z0-9]{6,12}$'),
    created_at timestamptz not null default now()
);

create table if not exists public.restaurant_staff (
    restaurant_id uuid not null references public.restaurants(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    role text not null default 'owner' check (role in ('owner', 'staff')),
    created_at timestamptz not null default now(),
    primary key (restaurant_id, user_id)
);

create table if not exists public.loyalty_members (
    id uuid primary key default gen_random_uuid(),
    restaurant_id uuid not null references public.restaurants(id) on delete cascade,
    auth_user_id uuid not null references auth.users(id) on delete cascade,
    member_code text not null unique default upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 10)),
    display_name text not null check (char_length(trim(display_name)) between 1 and 100),
    points integer not null default 0 check (points >= 0),
    created_at timestamptz not null default now(),
    unique (restaurant_id, auth_user_id)
);

create table if not exists public.loyalty_transactions (
    id uuid primary key default gen_random_uuid(),
    restaurant_id uuid not null references public.restaurants(id) on delete cascade,
    member_id uuid not null references public.loyalty_members(id) on delete cascade,
    created_by uuid not null references auth.users(id),
    points_delta integer not null check (points_delta <> 0),
    points_after integer not null check (points_after >= 0),
    note text,
    created_at timestamptz not null default now()
);

create index if not exists loyalty_members_restaurant_idx
    on public.loyalty_members (restaurant_id);
create index if not exists loyalty_members_user_idx
    on public.loyalty_members (auth_user_id);
create index if not exists loyalty_transactions_member_created_idx
    on public.loyalty_transactions (member_id, created_at desc);

create or replace function public.is_restaurant_staff(p_restaurant_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.restaurant_staff rs
        where rs.restaurant_id = p_restaurant_id
          and rs.user_id = auth.uid()
    );
$$;

create or replace function public.create_restaurant(p_name text, p_join_code text)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_restaurant_id uuid;
    v_name text := trim(coalesce(p_name, ''));
    v_code text := upper(trim(coalesce(p_join_code, '')));
begin
    if auth.uid() is null then
        raise exception 'É necessário iniciar sessão.';
    end if;
    if char_length(v_name) < 2 or char_length(v_name) > 100 then
        raise exception 'O nome do restaurante deve ter entre 2 e 100 caracteres.';
    end if;
    if v_code !~ '^[A-Z0-9]{6,12}$' then
        raise exception 'O código deve ter 6 a 12 letras/números.';
    end if;

    insert into public.restaurants (name, join_code)
    values (v_name, v_code)
    returning id into v_restaurant_id;

    insert into public.restaurant_staff (restaurant_id, user_id, role)
    values (v_restaurant_id, auth.uid(), 'owner');

    return v_restaurant_id;
end;
$$;

create or replace function public.join_restaurant(p_join_code text, p_display_name text)
returns table (member_id uuid, member_code text, points integer, restaurant_name text)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_restaurant_id uuid;
    v_restaurant_name text;
    v_member_id uuid;
    v_member_code text;
    v_points integer;
    v_display_name text := trim(coalesce(p_display_name, ''));
begin
    if auth.uid() is null then
        raise exception 'É necessário criar conta ou iniciar sessão.';
    end if;
    if char_length(v_display_name) < 1 or char_length(v_display_name) > 100 then
        raise exception 'O nome deve ter entre 1 e 100 caracteres.';
    end if;

    select r.id, r.name
      into v_restaurant_id, v_restaurant_name
      from public.restaurants r
     where r.join_code = upper(trim(coalesce(p_join_code, '')));

    if v_restaurant_id is null then
        raise exception 'Código do restaurante inválido.';
    end if;

    insert into public.loyalty_members (restaurant_id, auth_user_id, display_name)
    values (v_restaurant_id, auth.uid(), v_display_name)
    on conflict (restaurant_id, auth_user_id)
    do update set display_name = excluded.display_name
    returning id, loyalty_members.member_code, loyalty_members.points
         into v_member_id, v_member_code, v_points;

    return query select v_member_id, v_member_code, v_points, v_restaurant_name;
end;
$$;

create or replace function public.adjust_loyalty_points(
    p_member_id uuid,
    p_delta integer,
    p_note text default null
)
returns table (member_id uuid, member_code text, points integer)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_restaurant_id uuid;
    v_member_code text;
    v_old_points integer;
    v_new_points integer;
begin
    if auth.uid() is null then
        raise exception 'É necessário iniciar sessão.';
    end if;
    if p_delta is null or p_delta = 0 or abs(p_delta::bigint) > 100000 then
        raise exception 'A alteração de pontos não é válida.';
    end if;

    select lm.restaurant_id, lm.member_code, lm.points
      into v_restaurant_id, v_member_code, v_old_points
      from public.loyalty_members lm
     where lm.id = p_member_id
     for update;

    if v_restaurant_id is null then
        raise exception 'Cartão de cliente não encontrado.';
    end if;
    if not public.is_restaurant_staff(v_restaurant_id) then
        raise exception 'Sem autorização para alterar pontos deste restaurante.';
    end if;

    v_new_points := v_old_points + p_delta;
    if v_new_points < 0 then
        raise exception 'O saldo não pode ficar negativo.';
    end if;

    update public.loyalty_members lm
       set points = v_new_points
     where lm.id = p_member_id;

    insert into public.loyalty_transactions
        (restaurant_id, member_id, created_by, points_delta, points_after, note)
    values
        (v_restaurant_id, p_member_id, auth.uid(), p_delta, v_new_points,
         nullif(trim(coalesce(p_note, '')), ''));

    return query select p_member_id, v_member_code, v_new_points;
end;
$$;

alter table public.restaurants enable row level security;
alter table public.restaurant_staff enable row level security;
alter table public.loyalty_members enable row level security;
alter table public.loyalty_transactions enable row level security;

drop policy if exists restaurants_read_member_or_staff on public.restaurants;
create policy restaurants_read_member_or_staff
on public.restaurants for select to authenticated
using (
    public.is_restaurant_staff(id)
    or exists (
        select 1 from public.loyalty_members lm
        where lm.restaurant_id = restaurants.id
          and lm.auth_user_id = auth.uid()
    )
);

drop policy if exists restaurant_staff_read_self_or_same_restaurant on public.restaurant_staff;
create policy restaurant_staff_read_self_or_same_restaurant
on public.restaurant_staff for select to authenticated
using (user_id = auth.uid() or public.is_restaurant_staff(restaurant_id));

drop policy if exists loyalty_members_read_self_or_staff on public.loyalty_members;
create policy loyalty_members_read_self_or_staff
on public.loyalty_members for select to authenticated
using (auth_user_id = auth.uid() or public.is_restaurant_staff(restaurant_id));

drop policy if exists loyalty_transactions_read_self_or_staff on public.loyalty_transactions;
create policy loyalty_transactions_read_self_or_staff
on public.loyalty_transactions for select to authenticated
using (
    exists (
        select 1 from public.loyalty_members lm
        where lm.id = loyalty_transactions.member_id
          and lm.auth_user_id = auth.uid()
    )
    or public.is_restaurant_staff(restaurant_id)
);

-- Only signed-in users may use these functions; table writes are performed
-- by the SECURITY DEFINER functions after checking auth.uid() and staff role.
revoke all on public.restaurants, public.restaurant_staff,
    public.loyalty_members, public.loyalty_transactions from anon, authenticated;
grant select on public.restaurants, public.restaurant_staff,
    public.loyalty_members, public.loyalty_transactions to authenticated;

revoke all on function public.is_restaurant_staff(uuid) from public, anon;
revoke all on function public.create_restaurant(text, text) from public, anon;
revoke all on function public.join_restaurant(text, text) from public, anon;
revoke all on function public.adjust_loyalty_points(uuid, integer, text) from public, anon;

grant execute on function public.is_restaurant_staff(uuid) to authenticated;
grant execute on function public.create_restaurant(text, text) to authenticated;
grant execute on function public.join_restaurant(text, text) to authenticated;
grant execute on function public.adjust_loyalty_points(uuid, integer, text) to authenticated;

commit;
