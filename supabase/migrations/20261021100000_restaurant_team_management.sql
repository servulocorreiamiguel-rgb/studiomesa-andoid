-- Owner-only team roster and immediate staff membership revocation.
create or replace function public.list_restaurant_team()
returns table (
  member_user_id uuid,
  member_email text,
  member_role text,
  joined_at timestamptz
)
language plpgsql
stable
security definer
set search_path = ''
as $function$
declare
  v_restaurant_id uuid;
begin
  if (select auth.uid()) is null then
    raise exception 'É necessário iniciar sessão.';
  end if;

  select rs.restaurant_id
    into v_restaurant_id
  from public.restaurant_staff as rs
  where rs.user_id = (select auth.uid())
    and rs.role = 'owner'
  order by rs.created_at asc
  limit 1;

  if v_restaurant_id is null then
    raise exception 'Só o proprietário pode consultar a lista da equipa.';
  end if;

  return query
  select rs.user_id, coalesce(u.email, ''::text), rs.role, rs.created_at
  from public.restaurant_staff as rs
  left join auth.users as u on u.id = rs.user_id
  where rs.restaurant_id = v_restaurant_id
  order by case when rs.role = 'owner' then 0 else 1 end, rs.created_at asc;
end;
$function$;

create or replace function public.remove_restaurant_staff(p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_restaurant_id uuid;
  v_target_role text;
  v_deleted integer;
begin
  if (select auth.uid()) is null then
    raise exception 'É necessário iniciar sessão.';
  end if;
  if p_user_id is null then
    raise exception 'Seleciona um membro válido da equipa.';
  end if;
  if p_user_id = (select auth.uid()) then
    raise exception 'Não podes remover a tua própria conta por este ecrã.';
  end if;

  select rs.restaurant_id
    into v_restaurant_id
  from public.restaurant_staff as rs
  where rs.user_id = (select auth.uid())
    and rs.role = 'owner'
  order by rs.created_at asc
  limit 1;

  if v_restaurant_id is null then
    raise exception 'Só o proprietário pode remover membros da equipa.';
  end if;

  select rs.role
    into v_target_role
  from public.restaurant_staff as rs
  where rs.restaurant_id = v_restaurant_id
    and rs.user_id = p_user_id
  for update;

  if v_target_role is null then
    raise exception 'Este membro já não pertence à equipa.';
  end if;
  if v_target_role <> 'staff' then
    raise exception 'Não é permitido remover o proprietário por esta operação.';
  end if;

  delete from public.restaurant_staff as rs
  where rs.restaurant_id = v_restaurant_id
    and rs.user_id = p_user_id
    and rs.role = 'staff';

  get diagnostics v_deleted = row_count;
  if v_deleted <> 1 then
    raise exception 'Não foi possível remover o membro da equipa.';
  end if;
end;
$function$;

revoke all on function public.list_restaurant_team() from public, anon;
revoke all on function public.remove_restaurant_staff(uuid) from public, anon;
grant execute on function public.list_restaurant_team() to authenticated;
grant execute on function public.remove_restaurant_staff(uuid) to authenticated;
