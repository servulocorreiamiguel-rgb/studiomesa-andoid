-- Keep legacy loyalty RPCs compatible for the Android client while moving
-- privileged implementations out of the exposed public API schema.
BEGIN;

GRANT USAGE ON SCHEMA private TO authenticated;

ALTER FUNCTION public.create_loyalty_qr_token() SET SCHEMA private;
ALTER FUNCTION private.create_loyalty_qr_token()
    RENAME TO create_loyalty_qr_token_internal;

ALTER FUNCTION public.award_loyalty_points_from_qr(uuid, integer, uuid, text) SET SCHEMA private;
ALTER FUNCTION private.award_loyalty_points_from_qr(uuid, integer, uuid, text)
    RENAME TO award_loyalty_points_from_qr_internal;

ALTER FUNCTION public.get_member_restaurant_menu(uuid) SET SCHEMA private;
ALTER FUNCTION private.get_member_restaurant_menu(uuid)
    RENAME TO get_member_restaurant_menu_internal;

ALTER FUNCTION public.get_my_loyalty_membership() SET SCHEMA private;
ALTER FUNCTION private.get_my_loyalty_membership()
    RENAME TO get_my_loyalty_membership_internal;

ALTER FUNCTION public.get_restaurant_reward_redemptions(uuid) SET SCHEMA private;
ALTER FUNCTION private.get_restaurant_reward_redemptions(uuid)
    RENAME TO get_restaurant_reward_redemptions_internal;

REVOKE ALL ON FUNCTION private.create_loyalty_qr_token_internal()
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private.award_loyalty_points_from_qr_internal(uuid, integer, uuid, text)
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private.get_member_restaurant_menu_internal(uuid)
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private.get_my_loyalty_membership_internal()
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private.get_restaurant_reward_redemptions_internal(uuid)
    FROM PUBLIC, anon, authenticated;

GRANT EXECUTE ON FUNCTION private.create_loyalty_qr_token_internal()
    TO authenticated;
GRANT EXECUTE ON FUNCTION private.award_loyalty_points_from_qr_internal(uuid, integer, uuid, text)
    TO authenticated;
GRANT EXECUTE ON FUNCTION private.get_member_restaurant_menu_internal(uuid)
    TO authenticated;
GRANT EXECUTE ON FUNCTION private.get_my_loyalty_membership_internal()
    TO authenticated;
GRANT EXECUTE ON FUNCTION private.get_restaurant_reward_redemptions_internal(uuid)
    TO authenticated;

CREATE FUNCTION public.create_loyalty_qr_token()
RETURNS TABLE (qr_token uuid, expires_at timestamptz)
LANGUAGE sql
SECURITY INVOKER
SET search_path TO ''
AS $function$
    SELECT * FROM private.create_loyalty_qr_token_internal();
$function$;

CREATE FUNCTION public.award_loyalty_points_from_qr(
    p_qr_token uuid,
    p_delta integer,
    p_client_request_id uuid,
    p_note text DEFAULT NULL
)
RETURNS TABLE (member_id uuid, member_code text, points integer)
LANGUAGE sql
SECURITY INVOKER
SET search_path TO ''
AS $function$
    SELECT *
    FROM private.award_loyalty_points_from_qr_internal(
        p_qr_token, p_delta, p_client_request_id, p_note
    );
$function$;

CREATE FUNCTION public.get_member_restaurant_menu(p_member_id uuid)
RETURNS TABLE (
    item_id uuid,
    name text,
    category text,
    description text,
    price numeric,
    allergens text
)
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path TO ''
AS $function$
    SELECT *
    FROM private.get_member_restaurant_menu_internal(p_member_id);
$function$;

CREATE FUNCTION public.get_my_loyalty_membership()
RETURNS TABLE (
    member_id uuid,
    member_code text,
    points integer,
    display_name text,
    restaurant_id uuid,
    restaurant_name text
)
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path TO ''
AS $function$
    SELECT * FROM private.get_my_loyalty_membership_internal();
$function$;

CREATE FUNCTION public.get_restaurant_reward_redemptions(p_restaurant_id uuid)
RETURNS TABLE (
    redemption_id uuid,
    customer_name text,
    reward_name text,
    points_spent integer,
    status text,
    redeemed_at timestamptz
)
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path TO ''
AS $function$
    SELECT *
    FROM private.get_restaurant_reward_redemptions_internal(p_restaurant_id);
$function$;

REVOKE ALL ON FUNCTION public.create_loyalty_qr_token()
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.award_loyalty_points_from_qr(uuid, integer, uuid, text)
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.get_member_restaurant_menu(uuid)
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.get_my_loyalty_membership()
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.get_restaurant_reward_redemptions(uuid)
    FROM PUBLIC, anon, authenticated;

GRANT EXECUTE ON FUNCTION public.create_loyalty_qr_token()
    TO authenticated;
GRANT EXECUTE ON FUNCTION public.award_loyalty_points_from_qr(uuid, integer, uuid, text)
    TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_member_restaurant_menu(uuid)
    TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_my_loyalty_membership()
    TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_restaurant_reward_redemptions(uuid)
    TO authenticated;

COMMIT;
