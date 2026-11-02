-- Granular server-enforced permissions for restaurant staff.
-- Existing staff retain all operational access until the owner saves a custom matrix.

begin;

create table if not exists private.restaurant_staff_permissions (
    restaurant_id uuid not null,
    user_id uuid not null,
    permissions text[] not null,
    updated_by uuid references auth.users(id) on delete set null,
    updated_at timestamptz not null default now(),
    primary key (restaurant_id, user_id),
    constraint restaurant_staff_permissions_staff_fk
        foreign key (restaurant_id, user_id)
        references public.restaurant_staff (restaurant_id, user_id)
        on delete cascade,
    constraint restaurant_staff_permissions_known_values
        check (permissions <@ array['orders','tables','reservations','menu','inventory','loyalty']::text[])
);

create index if not exists restaurant_staff_permissions_user_id_idx
    on private.restaurant_staff_permissions (user_id);

alter table private.restaurant_staff_permissions enable row level security;
revoke all on table private.restaurant_staff_permissions from public, anon, authenticated;

create or replace function private.has_restaurant_permission(
    p_restaurant_id uuid,
    p_permission text
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $function$
    select coalesce((
        select case
            when rs.role = 'owner' then true
            when rs.role <> 'staff' then false
            when p.permissions is null then
                p_permission = any(array['orders','tables','reservations','menu','inventory','loyalty']::text[])
            else p_permission = any(p.permissions)
        end
        from public.restaurant_staff rs
        left join private.restaurant_staff_permissions p
          on p.restaurant_id = rs.restaurant_id
         and p.user_id = rs.user_id
        where rs.restaurant_id = p_restaurant_id
          and rs.user_id = (select auth.uid())
          and p_permission = any(array['orders','tables','reservations','menu','inventory','loyalty']::text[])
        limit 1
    ), false);
$function$;

revoke all on function private.has_restaurant_permission(uuid,text) from public, anon, authenticated;
grant usage on schema private to authenticated;
grant execute on function private.has_restaurant_permission(uuid,text) to authenticated;

create or replace function public.get_restaurant_staff_permissions(p_user_id uuid)
returns text[]
language plpgsql
stable
security definer
set search_path = ''
as $function$
declare
    v_restaurant_id uuid;
    v_role text;
    v_permissions text[];
begin
    if (select auth.uid()) is null then
        raise exception 'É necessário iniciar sessão.';
    end if;

    select rs.restaurant_id into v_restaurant_id
    from public.restaurant_staff rs
    where rs.user_id = (select auth.uid())
      and rs.role = 'owner'
    order by rs.created_at asc
    limit 1;

    if v_restaurant_id is null then
        raise exception 'Só o proprietário pode consultar as permissões da equipa.';
    end if;

    select rs.role into v_role
    from public.restaurant_staff rs
    where rs.restaurant_id = v_restaurant_id
      and rs.user_id = p_user_id;

    if v_role is distinct from 'staff' then
        raise exception 'O membro selecionado não é um funcionário deste restaurante.';
    end if;

    select p.permissions into v_permissions
    from private.restaurant_staff_permissions p
    where p.restaurant_id = v_restaurant_id
      and p.user_id = p_user_id;

    return coalesce(v_permissions, array['orders','tables','reservations','menu','inventory','loyalty']::text[]);
end;
$function$;

create or replace function public.set_restaurant_staff_permissions(
    p_user_id uuid,
    p_permissions text[]
)
returns text[]
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_restaurant_id uuid;
    v_role text;
    v_permissions text[];
begin
    if (select auth.uid()) is null then
        raise exception 'É necessário iniciar sessão.';
    end if;
    if p_user_id is null or p_permissions is null then
        raise exception 'Seleciona um funcionário e permissões válidas.';
    end if;

    select rs.restaurant_id into v_restaurant_id
    from public.restaurant_staff rs
    where rs.user_id = (select auth.uid())
      and rs.role = 'owner'
    order by rs.created_at asc
    limit 1;

    if v_restaurant_id is null then
        raise exception 'Só o proprietário pode alterar permissões da equipa.';
    end if;

    select rs.role into v_role
    from public.restaurant_staff rs
    where rs.restaurant_id = v_restaurant_id
      and rs.user_id = p_user_id
    for update;

    if v_role is distinct from 'staff' then
        raise exception 'O membro selecionado não é um funcionário deste restaurante.';
    end if;

    if cardinality(p_permissions) > 6
       or exists (
           select 1 from unnest(p_permissions) as requested(permission)
           where requested.permission not in ('orders','tables','reservations','menu','inventory','loyalty')
       )
       or cardinality(p_permissions) <> (
           select count(distinct requested.permission)
           from unnest(p_permissions) as requested(permission)
       ) then
        raise exception 'A lista de permissões contém valores inválidos ou repetidos.';
    end if;

    select coalesce(array_agg(allowed.permission order by allowed.ordinality), array[]::text[])
      into v_permissions
    from unnest(array['orders','tables','reservations','menu','inventory','loyalty']::text[])
         with ordinality as allowed(permission, ordinality)
    where allowed.permission = any(p_permissions);

    insert into private.restaurant_staff_permissions
        (restaurant_id, user_id, permissions, updated_by, updated_at)
    values
        (v_restaurant_id, p_user_id, v_permissions, (select auth.uid()), now())
    on conflict (restaurant_id, user_id)
    do update set permissions = excluded.permissions,
                  updated_by = excluded.updated_by,
                  updated_at = excluded.updated_at;

    return v_permissions;
end;
$function$;

revoke all on function public.get_restaurant_staff_permissions(uuid) from public, anon, authenticated;
grant execute on function public.get_restaurant_staff_permissions(uuid) to authenticated;
revoke all on function public.set_restaurant_staff_permissions(uuid,text[]) from public, anon, authenticated;
grant execute on function public.set_restaurant_staff_permissions(uuid,text[]) to authenticated;

alter policy restaurant_tables_staff_all on public.restaurant_tables
    using (private.has_restaurant_permission(restaurant_id, 'tables'))
    with check (private.has_restaurant_permission(restaurant_id, 'tables'));
alter policy menu_items_staff_all on public.menu_items
    using (private.has_restaurant_permission(restaurant_id, 'menu'))
    with check (private.has_restaurant_permission(restaurant_id, 'menu'));
alter policy restaurant_orders_staff_all on public.restaurant_orders
    using (private.has_restaurant_permission(restaurant_id, 'orders'))
    with check (private.has_restaurant_permission(restaurant_id, 'orders'));
alter policy restaurant_inventory_items_staff_read on public.restaurant_inventory_items
    using (private.has_restaurant_permission(restaurant_id, 'inventory'));
alter policy restaurant_inventory_movements_staff_read on public.restaurant_inventory_movements
    using (private.has_restaurant_permission(restaurant_id, 'inventory'));
alter policy restaurant_reservations_staff_read on public.restaurant_reservations
    using (private.has_restaurant_permission(restaurant_id, 'reservations'));
alter policy loyalty_members_read_self_or_staff on public.loyalty_members
    using (
        auth_user_id = (select auth.uid())
        or private.has_restaurant_permission(restaurant_id, 'loyalty')
    );
alter policy loyalty_transactions_read_self_or_staff on public.loyalty_transactions
    using (
        exists (
            select 1 from public.loyalty_members lm
            where lm.id = loyalty_transactions.member_id
              and lm.auth_user_id = (select auth.uid())
        )
        or private.has_restaurant_permission(restaurant_id, 'loyalty')
    );



commit;
