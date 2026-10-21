-- Repeating the same reservation state after a lost response is a successful no-op.
-- Invalid transitions and access checks remain enforced by the server.
CREATE OR REPLACE FUNCTION public.update_restaurant_reservation_status(p_reservation_id uuid, p_status text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_restaurant_id uuid;
  v_current_status text;
begin
  if (select auth.uid()) is null then
    raise exception 'É necessário iniciar sessão.';
  end if;
  if p_status not in ('Pendente', 'Confirmada', 'Cancelada', 'Concluída', 'Não compareceu') then
    raise exception 'Estado de reserva inválido.';
  end if;

  select r.restaurant_id, r.status into v_restaurant_id, v_current_status
  from public.restaurant_reservations as r
  where r.id = p_reservation_id
  for update;

  if v_restaurant_id is null then
    raise exception 'Reserva não encontrada.';
  end if;
  if not private.is_restaurant_staff(v_restaurant_id) then
    raise exception 'Sem autorização para alterar esta reserva.';
  end if;

  -- Network retries should treat an already-applied target state as success.
  if v_current_status = p_status then
    return;
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
$function$
