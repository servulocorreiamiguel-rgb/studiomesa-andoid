-- StudioMesa: shared inventory and reservation workflows.
-- All writes are performed by authorization-checked RPCs; team members may only
-- read records belonging to restaurants they are assigned to.

begin;

create table if not exists public.restaurant_inventory_items (
  id uuid primary key default gen_random_uuid(),
  restaurant_id uuid not null references public.restaurants(id) on delete cascade,
  name text not null check (char_length(trim(name)) between 1 and 100),
  unit text not null default 'unidades' check (char_length(trim(unit)) between 1 and 24),
  quantity numeric(12,3) not null default 0 check (quantity >= 0 and quantity <= 999999999),
  low_stock_threshold numeric(12,3) not null default 0 check (low_stock_threshold >= 0 and low_stock_threshold <= 999999999),
  created_by uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index if not exists restaurant_inventory_items_restaurant_name_key
  on public.restaurant_inventory_items (restaurant_id, lower(name));
create index if not exists restaurant_inventory_items_restaurant_idx
  on public.restaurant_inventory_items (restaurant_id, name);

create table if not exists public.restaurant_inventory_movements (
  id uuid primary key default gen_random_uuid(),
  restaurant_id uuid not null references public.restaurants(id) on delete cascade,
  item_id uuid not null references public.restaurant_inventory_items(id) on delete cascade,
  delta numeric(12,3) not null check (delta <> 0),
  quantity_after numeric(12,3) not null check (quantity_after >= 0),
  note text not null default '' check (char_length(note) <= 500),
  created_by uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now()
);
create index if not exists restaurant_inventory_movements_item_created_idx
  on public.restaurant_inventory_movements (item_id, created_at desc);

create table if not exists public.restaurant_reservations (
  id uuid primary key default gen_random_uuid(),
  restaurant_id uuid not null references public.restaurants(id) on delete cascade,
  guest_name text not null check (char_length(trim(guest_name)) between 1 and 100),
  guest_phone text not null default '' check (char_length(guest_phone) <= 40),
  party_size integer not null check (party_size between 1 and 60),
  reservation_at timestamptz not null,
  duration_minutes integer not null default 90 check (duration_minutes between 30 and 480),
  table_number integer,
  status text not null default 'Pendente'
    check (status in ('Pendente', 'Confirmada', 'Cancelada', 'Concluída', 'Não compareceu')),
  notes text not null default '' check (char_length(notes) <= 1000),
  created_by uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint restaurant_reservations_table_number_positive check (table_number is null or table_number between 1 and 100)
);
create index if not exists restaurant_reservations_restaurant_time_idx
  on public.restaurant_reservations (restaurant_id, reservation_at);
create index if not exists restaurant_reservations_restaurant_table_time_idx
  on public.restaurant_reservations (restaurant_id, table_number, reservation_at)
  where table_number is not null and status in ('Pendente', 'Confirmada');

alter table public.restaurant_inventory_items enable row level security;
alter table public.restaurant_inventory_movements enable row level security;
alter table public.restaurant_reservations enable row level security;

drop policy if exists restaurant_inventory_items_staff_read on public.restaurant_inventory_items;
create policy restaurant_inventory_items_staff_read
  on public.restaurant_inventory_items for select to authenticated
  using (private.is_restaurant_staff(restaurant_id));

drop policy if exists restaurant_inventory_movements_staff_read on public.restaurant_inventory_movements;
create policy restaurant_inventory_movements_staff_read
  on public.restaurant_inventory_movements for select to authenticated
  using (private.is_restaurant_staff(restaurant_id));

drop policy if exists restaurant_reservations_staff_read on public.restaurant_reservations;
create policy restaurant_reservations_staff_read
  on public.restaurant_reservations for select to authenticated
  using (private.is_restaurant_staff(restaurant_id));

revoke all on table public.restaurant_inventory_items from anon, authenticated;
revoke all on table public.restaurant_inventory_movements from anon, authenticated;
revoke all on table public.restaurant_reservations from anon, authenticated;
grant select on table public.restaurant_inventory_items to authenticated;
grant select on table public.restaurant_inventory_movements to authenticated;
grant select on table public.restaurant_reservations to authenticated;

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
  if p_restaurant_id is null or not private.is_restaurant_staff(p_restaurant_id) then
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

create or replace function public.adjust_inventory_stock(
  p_item_id uuid,
  p_delta numeric,
  p_note text default null
) returns table(item_id uuid, quantity numeric)
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_restaurant_id uuid;
  v_old_quantity numeric(12,3);
  v_new_quantity numeric(12,3);
begin
  if (select auth.uid()) is null then
    raise exception 'É necessário iniciar sessão.';
  end if;
  if p_delta is null or p_delta = 0 or abs(p_delta) > 999999999 then
    raise exception 'A alteração de stock não é válida.';
  end if;

  select i.restaurant_id, i.quantity
    into v_restaurant_id, v_old_quantity
  from public.restaurant_inventory_items as i
  where i.id = p_item_id
  for update;

  if v_restaurant_id is null then
    raise exception 'Artigo de stock não encontrado.';
  end if;
  if not private.is_restaurant_staff(v_restaurant_id) then
    raise exception 'Sem autorização para alterar o stock deste restaurante.';
  end if;

  v_new_quantity := v_old_quantity + p_delta;
  if v_new_quantity < 0 then
    raise exception 'O stock não pode ficar negativo.';
  end if;
  if v_new_quantity > 999999999 then
    raise exception 'A quantidade excede o limite permitido.';
  end if;

  update public.restaurant_inventory_items
  set quantity = v_new_quantity, updated_at = now()
  where id = p_item_id;

  insert into public.restaurant_inventory_movements
    (restaurant_id, item_id, delta, quantity_after, note, created_by)
  values
    (v_restaurant_id, p_item_id, p_delta, v_new_quantity,
     left(trim(coalesce(p_note, 'Ajuste de stock')), 500), (select auth.uid()));

  return query select p_item_id, v_new_quantity::numeric;
end;
$function$;

create or replace function public.create_restaurant_reservation(
  p_restaurant_id uuid,
  p_guest_name text,
  p_guest_phone text,
  p_party_size integer,
  p_reservation_at timestamptz,
  p_duration_minutes integer,
  p_table_number integer,
  p_notes text
) returns uuid
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_name text := trim(coalesce(p_guest_name, ''));
  v_phone text := trim(coalesce(p_guest_phone, ''));
  v_notes text := trim(coalesce(p_notes, ''));
  v_table_id uuid;
  v_seats integer;
  v_reservation_id uuid;
begin
  if (select auth.uid()) is null then
    raise exception 'É necessário iniciar sessão.';
  end if;
  if p_restaurant_id is null or not private.is_restaurant_staff(p_restaurant_id) then
    raise exception 'Sem autorização para gerir reservas deste restaurante.';
  end if;
  if char_length(v_name) not between 1 and 100 then
    raise exception 'O nome do cliente deve ter entre 1 e 100 caracteres.';
  end if;
  if char_length(v_phone) > 40 or char_length(v_notes) > 1000 then
    raise exception 'O contacto ou as notas excedem o limite permitido.';
  end if;
  if p_party_size is null or p_party_size not between 1 and 60 then
    raise exception 'O número de pessoas deve estar entre 1 e 60.';
  end if;
  if p_reservation_at is null or p_reservation_at < now() then
    raise exception 'A reserva tem de ser para o futuro.';
  end if;
  if p_reservation_at > now() + interval '1 year' then
    raise exception 'A reserva não pode estar a mais de um ano de distância.';
  end if;
  if p_duration_minutes is null or p_duration_minutes not between 30 and 480 then
    raise exception 'A duração deve estar entre 30 e 480 minutos.';
  end if;

  if p_table_number is not null then
    select t.id, t.seats into v_table_id, v_seats
    from public.restaurant_tables as t
    where t.restaurant_id = p_restaurant_id and t.table_number = p_table_number
    for update;

    if v_table_id is null then
      raise exception 'A mesa selecionada não existe neste restaurante.';
    end if;
    if v_seats < p_party_size then
      raise exception 'A mesa não tem capacidade para este número de pessoas.';
    end if;

    if exists (
      select 1
      from public.restaurant_reservations as r
      where r.restaurant_id = p_restaurant_id
        and r.table_number = p_table_number
        and r.status in ('Pendente', 'Confirmada')
        and r.reservation_at < p_reservation_at + (p_duration_minutes * interval '1 minute')
        and p_reservation_at < r.reservation_at + (r.duration_minutes * interval '1 minute')
    ) then
      raise exception 'Esta mesa já tem uma reserva pendente ou confirmada nesse horário.';
    end if;
  end if;

  insert into public.restaurant_reservations
    (restaurant_id, guest_name, guest_phone, party_size, reservation_at,
     duration_minutes, table_number, status, notes, created_by, updated_at)
  values
    (p_restaurant_id, v_name, v_phone, p_party_size, p_reservation_at,
     p_duration_minutes, p_table_number, 'Pendente', v_notes, (select auth.uid()), now())
  returning id into v_reservation_id;

  return v_reservation_id;
end;
$function$;

create or replace function public.update_restaurant_reservation_status(
  p_reservation_id uuid,
  p_status text
) returns void
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_restaurant_id uuid;
begin
  if (select auth.uid()) is null then
    raise exception 'É necessário iniciar sessão.';
  end if;
  if p_status not in ('Pendente', 'Confirmada', 'Cancelada', 'Concluída', 'Não compareceu') then
    raise exception 'Estado de reserva inválido.';
  end if;

  select r.restaurant_id into v_restaurant_id
  from public.restaurant_reservations as r
  where r.id = p_reservation_id
  for update;

  if v_restaurant_id is null then
    raise exception 'Reserva não encontrada.';
  end if;
  if not private.is_restaurant_staff(v_restaurant_id) then
    raise exception 'Sem autorização para alterar esta reserva.';
  end if;

  update public.restaurant_reservations
  set status = p_status, updated_at = now()
  where id = p_reservation_id;
end;
$function$;

revoke all on function public.save_inventory_item(uuid, uuid, text, text, numeric, numeric, text) from public, anon;
revoke all on function public.adjust_inventory_stock(uuid, numeric, text) from public, anon;
revoke all on function public.create_restaurant_reservation(uuid, text, text, integer, timestamptz, integer, integer, text) from public, anon;
revoke all on function public.update_restaurant_reservation_status(uuid, text) from public, anon;
grant execute on function public.save_inventory_item(uuid, uuid, text, text, numeric, numeric, text) to authenticated;
grant execute on function public.adjust_inventory_stock(uuid, numeric, text) to authenticated;
grant execute on function public.create_restaurant_reservation(uuid, text, text, integer, timestamptz, integer, integer, text) to authenticated;
grant execute on function public.update_restaurant_reservation_status(uuid, text) to authenticated;

commit;
