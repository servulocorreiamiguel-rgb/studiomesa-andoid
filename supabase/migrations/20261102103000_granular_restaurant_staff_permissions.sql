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

create or replace function public.save_inventory_item(
  p_restaurant_id uuid,
  p_item_id uuid,
  p_name text,
  p_unit text,
  p_quantity numeric,
  p_low_stock_threshold numeric,
  p_note text default null
) returns uuid
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_item_id uuid;
  v_old_quantity numeric(12,3) := 0;
  v_delta numeric(12,3);
  v_name text := trim(coalesce(p_name, ''));
  v_unit text := trim(coalesce(p_unit, ''));
begin
  if (select auth.uid()) is null then
    raise exception 'É necessário iniciar sessão.';
  end if;
  if p_restaurant_id is null or not private.has_restaurant_permission(p_restaurant_id, 'inventory') then
    raise exception 'Sem autorização para gerir o stock deste restaurante.';
  end if;
  if char_length(v_name) not between 1 and 100 or char_length(v_unit) not between 1 and 24 then
    raise exception 'Indica um nome e uma unidade válidos.';
  end if;
  if p_quantity is null or p_low_stock_threshold is null
     or p_quantity < 0 or p_low_stock_threshold < 0
     or p_quantity > 999999999 or p_low_stock_threshold > 999999999 then
    raise exception 'A quantidade ou o limite de stock não é válido.';
  end if;

  if p_item_id is null then
    insert into public.restaurant_inventory_items
      (restaurant_id, name, unit, quantity, low_stock_threshold, created_by, updated_at)
    values
      (p_restaurant_id, v_name, v_unit, p_quantity, p_low_stock_threshold, (select auth.uid()), now())
    returning id into v_item_id;

    if p_quantity > 0 then
      insert into public.restaurant_inventory_movements
        (restaurant_id, item_id, delta, quantity_after, note, created_by)
      values
        (p_restaurant_id, v_item_id, p_quantity, p_quantity,
         left(trim(coalesce(p_note, 'Stock inicial')), 500), (select auth.uid()));
    end if;
    return v_item_id;
  end if;

  select i.quantity into v_old_quantity
  from public.restaurant_inventory_items as i
  where i.id = p_item_id and i.restaurant_id = p_restaurant_id
  for update;

  if not found then
    raise exception 'Artigo de stock não encontrado neste restaurante.';
  end if;

  update public.restaurant_inventory_items
  set name = v_name,
      unit = v_unit,
      quantity = p_quantity,
      low_stock_threshold = p_low_stock_threshold,
      updated_at = now()
  where id = p_item_id and restaurant_id = p_restaurant_id
  returning id into v_item_id;

  v_delta := p_quantity - v_old_quantity;
  if v_delta <> 0 then
    insert into public.restaurant_inventory_movements
      (restaurant_id, item_id, delta, quantity_after, note, created_by)
    values
      (p_restaurant_id, v_item_id, v_delta, p_quantity,
       left(trim(coalesce(p_note, 'Ajuste de stock')), 500), (select auth.uid()));
  end if;

  return v_item_id;
end;
$function$;

CREATE OR REPLACE FUNCTION public.adjust_inventory_stock_idempotent(p_client_request_id uuid, p_item_id uuid, p_delta numeric, p_note text DEFAULT NULL::text)
 RETURNS TABLE(item_id uuid, quantity numeric)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE
  v_restaurant_id uuid;
  v_old_quantity numeric(12,3);
  v_new_quantity numeric(12,3);
  v_note text := left(trim(coalesce(p_note, 'Ajuste de stock')), 500);
  v_existing public.restaurant_inventory_movements%rowtype;
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF p_client_request_id IS NULL THEN
    RAISE EXCEPTION 'O identificador da tentativa de stock está em falta.';
  END IF;
  IF p_item_id IS NULL THEN
    RAISE EXCEPTION 'Seleciona um artigo de stock válido.';
  END IF;
  IF p_delta IS NULL OR p_delta = 0 OR abs(p_delta) > 999999999 THEN
    RAISE EXCEPTION 'A alteração de stock não é válida.';
  END IF;

  SELECT m.* INTO v_existing
  FROM public.restaurant_inventory_movements AS m
  WHERE m.created_by = (SELECT auth.uid())
    AND m.client_request_id = p_client_request_id
  FOR UPDATE;

  IF FOUND THEN
    IF NOT private.has_restaurant_permission(v_existing.restaurant_id, 'inventory') THEN
      RAISE EXCEPTION 'Sem autorização para consultar este movimento de stock.';
    END IF;
    IF v_existing.item_id = p_item_id
       AND v_existing.delta = p_delta
       AND v_existing.note = v_note THEN
      RETURN QUERY SELECT v_existing.item_id, v_existing.quantity_after;
      RETURN;
    END IF;
    RAISE EXCEPTION 'Esta tentativa já foi utilizada com dados de stock diferentes.';
  END IF;

  SELECT i.restaurant_id, i.quantity
    INTO v_restaurant_id, v_old_quantity
  FROM public.restaurant_inventory_items AS i
  WHERE i.id = p_item_id
  FOR UPDATE;

  IF v_restaurant_id IS NULL THEN
    RAISE EXCEPTION 'Artigo de stock não encontrado.';
  END IF;
  IF NOT private.has_restaurant_permission(v_restaurant_id, 'inventory') THEN
    RAISE EXCEPTION 'Sem autorização para alterar o stock deste restaurante.';
  END IF;

  -- Recheck after locking the item row: an identical concurrent request may
  -- have completed while this request waited for the lock.
  SELECT m.* INTO v_existing
  FROM public.restaurant_inventory_movements AS m
  WHERE m.created_by = (SELECT auth.uid())
    AND m.client_request_id = p_client_request_id
  FOR UPDATE;

  IF FOUND THEN
    IF NOT private.has_restaurant_permission(v_existing.restaurant_id, 'inventory') THEN
      RAISE EXCEPTION 'Sem autorização para consultar este movimento de stock.';
    END IF;
    IF v_existing.item_id = p_item_id
       AND v_existing.delta = p_delta
       AND v_existing.note = v_note THEN
      RETURN QUERY SELECT v_existing.item_id, v_existing.quantity_after;
      RETURN;
    END IF;
    RAISE EXCEPTION 'Esta tentativa já foi utilizada com dados de stock diferentes.';
  END IF;

  v_new_quantity := v_old_quantity + p_delta;
  IF v_new_quantity < 0 THEN
    RAISE EXCEPTION 'O stock não pode ficar negativo.';
  END IF;
  IF v_new_quantity > 999999999 THEN
    RAISE EXCEPTION 'A quantidade excede o limite permitido.';
  END IF;

  UPDATE public.restaurant_inventory_items
  SET quantity = v_new_quantity, updated_at = now()
  WHERE id = p_item_id;

  INSERT INTO public.restaurant_inventory_movements
    (restaurant_id, item_id, delta, quantity_after, note, created_by, client_request_id)
  VALUES
    (v_restaurant_id, p_item_id, p_delta, v_new_quantity, v_note, (SELECT auth.uid()), p_client_request_id);

  RETURN QUERY SELECT p_item_id, v_new_quantity::numeric;
END;
$function$;

CREATE OR REPLACE FUNCTION public.create_inventory_item_idempotent(
  p_client_request_id uuid,
  p_restaurant_id uuid,
  p_name text,
  p_unit text,
  p_quantity numeric,
  p_low_stock_threshold numeric,
  p_note text DEFAULT NULL::text
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_user_id uuid := (SELECT auth.uid());
  v_name text := trim(coalesce(p_name, ''));
  v_unit text := trim(coalesce(p_unit, ''));
  v_note text := left(trim(coalesce(p_note, 'Stock inicial')), 500);
  v_payload jsonb;
  v_existing_payload jsonb;
  v_item_id uuid;
BEGIN
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF p_client_request_id IS NULL THEN
    RAISE EXCEPTION 'O identificador da tentativa de criação de stock está em falta.';
  END IF;
  IF p_restaurant_id IS NULL OR NOT private.has_restaurant_permission(p_restaurant_id, 'inventory') THEN
    RAISE EXCEPTION 'Sem autorização para gerir o stock deste restaurante.';
  END IF;
  IF char_length(v_name) NOT BETWEEN 1 AND 100
     OR char_length(v_unit) NOT BETWEEN 1 AND 24 THEN
    RAISE EXCEPTION 'Indica um nome e uma unidade válidos.';
  END IF;
  IF p_quantity IS NULL OR p_low_stock_threshold IS NULL
     OR p_quantity < 0 OR p_low_stock_threshold < 0
     OR p_quantity > 999999999 OR p_low_stock_threshold > 999999999 THEN
    RAISE EXCEPTION 'A quantidade ou o limite de stock não é válido.';
  END IF;
  IF p_quantity <> round(p_quantity, 3)
     OR p_low_stock_threshold <> round(p_low_stock_threshold, 3) THEN
    RAISE EXCEPTION 'As quantidades de stock admitem no máximo três casas decimais.';
  END IF;
  IF length(coalesce(p_note, '')) > 500 THEN
    RAISE EXCEPTION 'A nota do movimento pode ter no máximo 500 caracteres.';
  END IF;

  v_payload := jsonb_build_object(
    'name', v_name,
    'unit', v_unit,
    'quantity', p_quantity,
    'low_stock_threshold', p_low_stock_threshold,
    'note', v_note
  );

  SELECT i.id, i.creation_payload
    INTO v_item_id, v_existing_payload
  FROM public.restaurant_inventory_items AS i
  WHERE i.restaurant_id = p_restaurant_id
    AND i.created_by = v_user_id
    AND i.creation_request_id = p_client_request_id
  FOR UPDATE;

  IF FOUND THEN
    IF v_existing_payload = v_payload THEN
      RETURN v_item_id;
    END IF;
    RAISE EXCEPTION 'Esta tentativa já foi utilizada com dados de stock diferentes.';
  END IF;

  BEGIN
    INSERT INTO public.restaurant_inventory_items
      (restaurant_id, name, unit, quantity, low_stock_threshold, created_by,
       creation_request_id, creation_payload)
    VALUES
      (p_restaurant_id, v_name, v_unit, p_quantity, p_low_stock_threshold, v_user_id,
       p_client_request_id, v_payload)
    RETURNING id INTO v_item_id;
  EXCEPTION WHEN unique_violation THEN
    -- Two identical requests can reach the server concurrently. Re-read the row
    -- created by the winner and return it only when the original payload matches.
    SELECT i.id, i.creation_payload
      INTO v_item_id, v_existing_payload
    FROM public.restaurant_inventory_items AS i
    WHERE i.restaurant_id = p_restaurant_id
      AND i.created_by = v_user_id
      AND i.creation_request_id = p_client_request_id
    FOR UPDATE;

    IF FOUND AND v_existing_payload = v_payload THEN
      RETURN v_item_id;
    END IF;
    RAISE EXCEPTION 'Esta tentativa já foi utilizada ou não pôde ser concluída.';
  END;

  IF p_quantity > 0 THEN
    INSERT INTO public.restaurant_inventory_movements
      (restaurant_id, item_id, delta, quantity_after, note, created_by)
    VALUES
      (p_restaurant_id, v_item_id, p_quantity, p_quantity, v_note, v_user_id);
  END IF;

  RETURN v_item_id;
END;
$function$;

CREATE OR REPLACE FUNCTION public.save_inventory_item_with_cost(
  p_restaurant_id uuid,
  p_item_id uuid,
  p_name text,
  p_unit text,
  p_quantity numeric,
  p_low_stock_threshold numeric,
  p_note text DEFAULT NULL::text,
  p_unit_cost_per_unit numeric DEFAULT NULL::numeric
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_item_id uuid;
  v_old_quantity numeric(12,3) := 0;
  v_delta numeric(12,3);
  v_name text := trim(coalesce(p_name, ''));
  v_unit text := trim(coalesce(p_unit, ''));
  v_note text := trim(coalesce(p_note, ''));
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF p_restaurant_id IS NULL OR NOT private.has_restaurant_permission(p_restaurant_id, 'inventory') THEN
    RAISE EXCEPTION 'Sem autorização para gerir o stock deste restaurante.';
  END IF;
  IF char_length(v_name) NOT BETWEEN 1 AND 100
     OR char_length(v_unit) NOT BETWEEN 1 AND 24 THEN
    RAISE EXCEPTION 'Indica um nome e uma unidade válidos.';
  END IF;
  IF p_quantity IS NULL OR p_low_stock_threshold IS NULL
     OR p_quantity < 0 OR p_low_stock_threshold < 0
     OR p_quantity > 999999999 OR p_low_stock_threshold > 999999999 THEN
    RAISE EXCEPTION 'A quantidade ou o limite de stock não é válido.';
  END IF;
  IF p_quantity <> round(p_quantity, 3)
     OR p_low_stock_threshold <> round(p_low_stock_threshold, 3) THEN
    RAISE EXCEPTION 'As quantidades de stock admitem no máximo três casas decimais.';
  END IF;
  IF length(coalesce(p_note, '')) > 500 THEN
    RAISE EXCEPTION 'A nota do movimento pode ter no máximo 500 caracteres.';
  END IF;
  IF p_unit_cost_per_unit IS NOT NULL
     AND (p_unit_cost_per_unit < 0
       OR p_unit_cost_per_unit > 99999999.9999
       OR p_unit_cost_per_unit <> round(p_unit_cost_per_unit, 4)) THEN
    RAISE EXCEPTION 'O custo unitário deve ser não negativo e ter no máximo quatro casas decimais.';
  END IF;

  IF p_item_id IS NULL THEN
    INSERT INTO public.restaurant_inventory_items
      (restaurant_id, name, unit, quantity, low_stock_threshold,
       unit_cost_per_unit, created_by, updated_at)
    VALUES
      (p_restaurant_id, v_name, v_unit, p_quantity, p_low_stock_threshold,
       p_unit_cost_per_unit, (SELECT auth.uid()), now())
    RETURNING id INTO v_item_id;

    IF p_quantity > 0 THEN
      INSERT INTO public.restaurant_inventory_movements
        (restaurant_id, item_id, delta, quantity_after, note, created_by)
      VALUES
        (p_restaurant_id, v_item_id, p_quantity, p_quantity,
         left(coalesce(v_note, 'Stock inicial'), 500), (SELECT auth.uid()));
    END IF;
    RETURN v_item_id;
  END IF;

  SELECT i.quantity INTO v_old_quantity
  FROM public.restaurant_inventory_items AS i
  WHERE i.id = p_item_id AND i.restaurant_id = p_restaurant_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Artigo de stock não encontrado neste restaurante.';
  END IF;

  UPDATE public.restaurant_inventory_items
  SET name = v_name,
      unit = v_unit,
      quantity = p_quantity,
      low_stock_threshold = p_low_stock_threshold,
      unit_cost_per_unit = p_unit_cost_per_unit,
      updated_at = now()
  WHERE id = p_item_id AND restaurant_id = p_restaurant_id
  RETURNING id INTO v_item_id;

  v_delta := p_quantity - v_old_quantity;
  IF v_delta <> 0 THEN
    INSERT INTO public.restaurant_inventory_movements
      (restaurant_id, item_id, delta, quantity_after, note, created_by)
    VALUES
      (p_restaurant_id, v_item_id, v_delta, p_quantity,
       left(coalesce(v_note, 'Ajuste de stock'), 500), (SELECT auth.uid()));
  END IF;

  RETURN v_item_id;
END;
$function$;

CREATE OR REPLACE FUNCTION public.create_inventory_item_idempotent_with_cost(
  p_client_request_id uuid,
  p_restaurant_id uuid,
  p_name text,
  p_unit text,
  p_quantity numeric,
  p_low_stock_threshold numeric,
  p_note text DEFAULT NULL::text,
  p_unit_cost_per_unit numeric DEFAULT NULL::numeric
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_user_id uuid := (SELECT auth.uid());
  v_name text := trim(coalesce(p_name, ''));
  v_unit text := trim(coalesce(p_unit, ''));
  v_note text := left(trim(coalesce(p_note, 'Stock inicial')), 500);
  v_payload jsonb;
  v_existing_payload jsonb;
  v_item_id uuid;
BEGIN
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF p_client_request_id IS NULL THEN
    RAISE EXCEPTION 'O identificador da tentativa de criação de stock está em falta.';
  END IF;
  IF p_restaurant_id IS NULL OR NOT private.has_restaurant_permission(p_restaurant_id, 'inventory') THEN
    RAISE EXCEPTION 'Sem autorização para gerir o stock deste restaurante.';
  END IF;
  IF char_length(v_name) NOT BETWEEN 1 AND 100
     OR char_length(v_unit) NOT BETWEEN 1 AND 24 THEN
    RAISE EXCEPTION 'Indica um nome e uma unidade válidos.';
  END IF;
  IF p_quantity IS NULL OR p_low_stock_threshold IS NULL
     OR p_quantity < 0 OR p_low_stock_threshold < 0
     OR p_quantity > 999999999 OR p_low_stock_threshold > 999999999 THEN
    RAISE EXCEPTION 'A quantidade ou o limite de stock não é válido.';
  END IF;
  IF p_quantity <> round(p_quantity, 3)
     OR p_low_stock_threshold <> round(p_low_stock_threshold, 3) THEN
    RAISE EXCEPTION 'As quantidades de stock admitem no máximo três casas decimais.';
  END IF;
  IF length(coalesce(p_note, '')) > 500 THEN
    RAISE EXCEPTION 'A nota do movimento pode ter no máximo 500 caracteres.';
  END IF;
  IF p_unit_cost_per_unit IS NOT NULL
     AND (p_unit_cost_per_unit < 0
       OR p_unit_cost_per_unit > 99999999.9999
       OR p_unit_cost_per_unit <> round(p_unit_cost_per_unit, 4)) THEN
    RAISE EXCEPTION 'O custo unitário deve ser não negativo e ter no máximo quatro casas decimais.';
  END IF;

  v_payload := jsonb_build_object(
    'name', v_name,
    'unit', v_unit,
    'quantity', p_quantity,
    'low_stock_threshold', p_low_stock_threshold,
    'note', v_note,
    'unit_cost_per_unit', p_unit_cost_per_unit
  );

  SELECT i.id, i.creation_payload
    INTO v_item_id, v_existing_payload
  FROM public.restaurant_inventory_items AS i
  WHERE i.restaurant_id = p_restaurant_id
    AND i.created_by = v_user_id
    AND i.creation_request_id = p_client_request_id
  FOR UPDATE;

  IF FOUND THEN
    IF v_existing_payload = v_payload THEN
      RETURN v_item_id;
    END IF;
    RAISE EXCEPTION 'Esta tentativa já foi utilizada com dados de stock diferentes.';
  END IF;

  BEGIN
    INSERT INTO public.restaurant_inventory_items
      (restaurant_id, name, unit, quantity, low_stock_threshold,
       unit_cost_per_unit, created_by, creation_request_id, creation_payload)
    VALUES
      (p_restaurant_id, v_name, v_unit, p_quantity, p_low_stock_threshold,
       p_unit_cost_per_unit, v_user_id, p_client_request_id, v_payload)
    RETURNING id INTO v_item_id;
  EXCEPTION WHEN unique_violation THEN
    SELECT i.id, i.creation_payload
      INTO v_item_id, v_existing_payload
    FROM public.restaurant_inventory_items AS i
    WHERE i.restaurant_id = p_restaurant_id
      AND i.created_by = v_user_id
      AND i.creation_request_id = p_client_request_id
    FOR UPDATE;

    IF FOUND AND v_existing_payload = v_payload THEN
      RETURN v_item_id;
    END IF;
    RAISE EXCEPTION 'Esta tentativa já foi utilizada ou não pôde ser concluída.';
  END;

  IF p_quantity > 0 THEN
    INSERT INTO public.restaurant_inventory_movements
      (restaurant_id, item_id, delta, quantity_after, note, created_by)
    VALUES
      (p_restaurant_id, v_item_id, p_quantity, p_quantity, v_note, v_user_id);
  END IF;

  RETURN v_item_id;
END;
$function$;

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
  IF p_restaurant_id IS NULL OR NOT private.has_restaurant_permission(p_restaurant_id, 'reservations') THEN
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
    if not private.has_restaurant_permission(v_restaurant_id, 'reservations') then
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
    if not private.has_restaurant_permission(v_restaurant_id, 'reservations') then
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
           or not private.has_restaurant_permission(v_existing.restaurant_id, 'loyalty') then
            raise exception 'Este identificador já foi utilizado noutra atribuição de pontos.';
        end if;
        select lm.* into v_member from public.loyalty_members lm where lm.id = v_existing.member_id;
        return query select v_member.id, v_member.member_code, v_member.points;
        return;
    end if;

    select t.* into v_token
    from private.loyalty_qr_tokens t where t.token = p_qr_token for update;
    if not found then raise exception 'QR inválido. Pede ao cliente que apresente um código novo.'; end if;
    if not private.has_restaurant_permission(v_token.restaurant_id, 'loyalty') then
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
    if v_restaurant_id is null or not private.has_restaurant_permission(v_restaurant_id, 'loyalty') then
        raise exception 'Sem autorização para confirmar este resgate.';
    end if;
    if v_status = 'fulfilled' then return; end if;
    update public.loyalty_reward_redemptions
       set status = 'fulfilled', fulfilled_by = (select auth.uid()), fulfilled_at = now()
     where id = p_redemption_id and status = 'pending';
end;
$function$;

create or replace function public.get_restaurant_loyalty_rewards(p_restaurant_id uuid)
returns table (id uuid, name text, description text, points_cost integer,
               is_active boolean, stock_limit integer, redeemed_count integer)
language plpgsql stable security definer set search_path = ''
as $function$
begin
    if (select auth.uid()) is null or p_restaurant_id is null
       or not private.has_restaurant_permission(p_restaurant_id, 'loyalty') then
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

create or replace function public.get_restaurant_reward_redemptions(p_restaurant_id uuid)
returns table (redemption_id uuid, customer_name text, reward_name text,
               points_spent integer, status text, redeemed_at timestamptz)
language plpgsql stable security definer set search_path = ''
as $function$
begin
    if (select auth.uid()) is null or p_restaurant_id is null
       or not private.has_restaurant_permission(p_restaurant_id, 'loyalty') then
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

commit;
