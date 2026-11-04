-- Let a customer inspect their own reward redemption history.
-- The member ID is resolved from auth.uid(); clients cannot choose another card.

create or replace function public.get_my_loyalty_reward_redemptions()
returns table (
    redemption_id uuid,
    reward_name text,
    points_spent integer,
    status text,
    redeemed_at timestamptz,
    fulfilled_at timestamptz
)
language plpgsql
stable
security definer
set search_path = ''
as $function$
declare
    v_member_id uuid;
begin
    if (select auth.uid()) is null then
        raise exception 'É necessário iniciar sessão.';
    end if;

    select lm.id into v_member_id
    from public.loyalty_members lm
    where lm.auth_user_id = (select auth.uid())
    order by lm.created_at asc
    limit 1;

    if v_member_id is null then
        return;
    end if;

    return query
    select rr.id, r.name, rr.points_spent, rr.status, rr.redeemed_at, rr.fulfilled_at
    from public.loyalty_reward_redemptions rr
    join public.loyalty_rewards r on r.id = rr.reward_id
    where rr.member_id = v_member_id
    order by rr.redeemed_at desc
    limit 50;
end;
$function$;

revoke all on function public.get_my_loyalty_reward_redemptions()
    from public, anon, authenticated;
grant execute on function public.get_my_loyalty_reward_redemptions()
    to authenticated;