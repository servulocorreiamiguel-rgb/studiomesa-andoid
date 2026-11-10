-- Keep privileged loyalty shop implementations in the non-exposed private schema.
-- Public RPCs remain SECURITY INVOKER and delegate to authenticated-only internals.
BEGIN;

GRANT USAGE ON SCHEMA private TO authenticated;

ALTER FUNCTION public.save_loyalty_reward(uuid, uuid, text, text, integer, integer, boolean)
    SET SCHEMA private;
ALTER FUNCTION private.save_loyalty_reward(uuid, uuid, text, text, integer, integer, boolean)
    RENAME TO save_loyalty_reward_internal;

ALTER FUNCTION public.get_restaurant_loyalty_rewards(uuid)
    SET SCHEMA private;
ALTER FUNCTION private.get_restaurant_loyalty_rewards(uuid)
    RENAME TO get_restaurant_loyalty_rewards_internal;

ALTER FUNCTION public.get_customer_loyalty_rewards(uuid)
    SET SCHEMA private;
ALTER FUNCTION private.get_customer_loyalty_rewards(uuid)
    RENAME TO get_customer_loyalty_rewards_internal;

ALTER FUNCTION public.redeem_loyalty_reward(uuid, uuid)
    SET SCHEMA private;
ALTER FUNCTION private.redeem_loyalty_reward(uuid, uuid)
    RENAME TO redeem_loyalty_reward_internal;

ALTER FUNCTION public.fulfill_loyalty_reward_redemption(uuid)
    SET SCHEMA private;
ALTER FUNCTION private.fulfill_loyalty_reward_redemption(uuid)
    RENAME TO fulfill_loyalty_reward_redemption_internal;

ALTER FUNCTION public.get_my_loyalty_reward_redemptions()
    SET SCHEMA private;
ALTER FUNCTION private.get_my_loyalty_reward_redemptions()
    RENAME TO get_my_loyalty_reward_redemptions_internal;

ALTER FUNCTION public.get_restaurant_loyalty_reward_redemptions(uuid)
    SET SCHEMA private;
ALTER FUNCTION private.get_restaurant_loyalty_reward_redemptions(uuid)
    RENAME TO get_restaurant_loyalty_reward_redemptions_internal;

REVOKE ALL ON FUNCTION private.save_loyalty_reward_internal(uuid, uuid, text, text, integer, integer, boolean)
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private.get_restaurant_loyalty_rewards_internal(uuid)
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private.get_customer_loyalty_rewards_internal(uuid)
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private.redeem_loyalty_reward_internal(uuid, uuid)
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private.fulfill_loyalty_reward_redemption_internal(uuid)
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private.get_my_loyalty_reward_redemptions_internal()
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private.get_restaurant_loyalty_reward_redemptions_internal(uuid)
    FROM PUBLIC, anon, authenticated;

GRANT EXECUTE ON FUNCTION private.save_loyalty_reward_internal(uuid, uuid, text, text, integer, integer, boolean)
    TO authenticated;
GRANT EXECUTE ON FUNCTION private.get_restaurant_loyalty_rewards_internal(uuid)
    TO authenticated;
GRANT EXECUTE ON FUNCTION private.get_customer_loyalty_rewards_internal(uuid)
    TO authenticated;
GRANT EXECUTE ON FUNCTION private.redeem_loyalty_reward_internal(uuid, uuid)
    TO authenticated;
GRANT EXECUTE ON FUNCTION private.fulfill_loyalty_reward_redemption_internal(uuid)
    TO authenticated;
GRANT EXECUTE ON FUNCTION private.get_my_loyalty_reward_redemptions_internal()
    TO authenticated;
GRANT EXECUTE ON FUNCTION private.get_restaurant_loyalty_reward_redemptions_internal(uuid)
    TO authenticated;

CREATE FUNCTION public.save_loyalty_reward(
    p_restaurant_id uuid,
    p_reward_id uuid,
    p_name text,
    p_description text,
    p_points_cost integer,
    p_stock_limit integer,
    p_is_active boolean
)
RETURNS uuid
LANGUAGE sql
SECURITY INVOKER
SET search_path TO ''
AS $function$
    SELECT private.save_loyalty_reward_internal(
        p_restaurant_id, p_reward_id, p_name, p_description,
        p_points_cost, p_stock_limit, p_is_active
    );
$function$;

CREATE FUNCTION public.get_restaurant_loyalty_rewards(p_restaurant_id uuid)
RETURNS TABLE (
    id uuid,
    name text,
    description text,
    points_cost integer,
    is_active boolean,
    stock_limit integer,
    redeemed_count integer
)
LANGUAGE sql
SECURITY INVOKER
SET search_path TO ''
AS $function$
    SELECT *
    FROM private.get_restaurant_loyalty_rewards_internal(p_restaurant_id);
$function$;

CREATE FUNCTION public.get_customer_loyalty_rewards(p_member_id uuid)
RETURNS TABLE (
    id uuid,
    name text,
    description text,
    points_cost integer,
    stock_limit integer,
    redeemed_count integer
)
LANGUAGE sql
SECURITY INVOKER
SET search_path TO ''
AS $function$
    SELECT *
    FROM private.get_customer_loyalty_rewards_internal(p_member_id);
$function$;

CREATE FUNCTION public.redeem_loyalty_reward(
    p_reward_id uuid,
    p_client_request_id uuid
)
RETURNS TABLE (
    redemption_id uuid,
    reward_name text,
    points_spent integer,
    points_remaining integer,
    status text
)
LANGUAGE sql
SECURITY INVOKER
SET search_path TO ''
AS $function$
    SELECT *
    FROM private.redeem_loyalty_reward_internal(p_reward_id, p_client_request_id);
$function$;

CREATE FUNCTION public.fulfill_loyalty_reward_redemption(p_redemption_id uuid)
RETURNS void
LANGUAGE sql
SECURITY INVOKER
SET search_path TO ''
AS $function$
    SELECT private.fulfill_loyalty_reward_redemption_internal(p_redemption_id);
$function$;

CREATE FUNCTION public.get_my_loyalty_reward_redemptions()
RETURNS TABLE (
    redemption_id uuid,
    reward_name text,
    points_spent integer,
    status text,
    redeemed_at timestamptz,
    fulfilled_at timestamptz
)
LANGUAGE sql
SECURITY INVOKER
SET search_path TO ''
AS $function$
    SELECT *
    FROM private.get_my_loyalty_reward_redemptions_internal();
$function$;

CREATE FUNCTION public.get_restaurant_loyalty_reward_redemptions(p_restaurant_id uuid)
RETURNS TABLE (
    redemption_id uuid,
    reward_id uuid,
    reward_name text,
    customer_name text,
    customer_code text,
    points_spent integer,
    status text,
    redeemed_at timestamptz,
    fulfilled_at timestamptz
)
LANGUAGE sql
SECURITY INVOKER
SET search_path TO ''
AS $function$
    SELECT *
    FROM private.get_restaurant_loyalty_reward_redemptions_internal(p_restaurant_id);
$function$;

REVOKE ALL ON FUNCTION public.save_loyalty_reward(uuid, uuid, text, text, integer, integer, boolean)
    FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_restaurant_loyalty_rewards(uuid)
    FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_customer_loyalty_rewards(uuid)
    FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.redeem_loyalty_reward(uuid, uuid)
    FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.fulfill_loyalty_reward_redemption(uuid)
    FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_my_loyalty_reward_redemptions()
    FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_restaurant_loyalty_reward_redemptions(uuid)
    FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.save_loyalty_reward(uuid, uuid, text, text, integer, integer, boolean)
    TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_restaurant_loyalty_rewards(uuid)
    TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_customer_loyalty_rewards(uuid)
    TO authenticated;
GRANT EXECUTE ON FUNCTION public.redeem_loyalty_reward(uuid, uuid)
    TO authenticated;
GRANT EXECUTE ON FUNCTION public.fulfill_loyalty_reward_redemption(uuid)
    TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_my_loyalty_reward_redemptions()
    TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_restaurant_loyalty_reward_redemptions(uuid)
    TO authenticated;

COMMIT;
