-- Disable direct point awards that bypass the single-use customer QR flow.
-- Add FK indexes for reward/QR relationships.
begin;

revoke execute on function public.adjust_loyalty_points_idempotent(uuid, uuid, integer, text)
    from public, anon, authenticated;

create index if not exists loyalty_qr_tokens_restaurant_id_idx
    on private.loyalty_qr_tokens (restaurant_id);
create index if not exists loyalty_reward_redemptions_reward_id_idx
    on public.loyalty_reward_redemptions (reward_id);
create index if not exists loyalty_reward_redemptions_redeemed_by_idx
    on public.loyalty_reward_redemptions (redeemed_by);
create index if not exists loyalty_reward_redemptions_fulfilled_by_idx
    on public.loyalty_reward_redemptions (fulfilled_by)
    where fulfilled_by is not null;

commit;