-- Qualify token cleanup columns because expires_at is also a RETURNS TABLE OUT parameter.
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

    -- Only the newest unused code remains valid for this customer.
    delete from private.loyalty_qr_tokens AS token_row
    where token_row.member_id = v_member_id and token_row.used_at is null;
    delete from private.loyalty_qr_tokens AS token_row
    where token_row.member_id = v_member_id
      and token_row.expires_at < now() - interval '1 day';

    insert into private.loyalty_qr_tokens (restaurant_id, member_id, expires_at)
    values (v_restaurant_id, v_member_id, v_expires_at)
    returning token into v_token;
    return query select v_token, v_expires_at;
end;
$function$;