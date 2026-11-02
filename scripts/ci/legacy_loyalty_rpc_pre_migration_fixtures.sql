-- Simulate legacy RPCs which exist in already-provisioned Supabase projects
-- but are intentionally absent from a clean replay of versioned migrations.
-- These stubs are test-only and are moved behind private wrappers by the
-- conditional legacy-boundary migration that follows.
CREATE FUNCTION public.create_loyalty_qr_token()
RETURNS TABLE (qr_token uuid, expires_at timestamptz)
LANGUAGE sql
SECURITY DEFINER
SET search_path TO ''
AS $function$
    SELECT pg_catalog.gen_random_uuid(), pg_catalog.now() + interval '2 minutes'
    WHERE false;
$function$;

CREATE FUNCTION public.award_loyalty_points_from_qr(
    p_qr_token uuid,
    p_delta integer,
    p_client_request_id uuid,
    p_note text DEFAULT NULL
)
RETURNS TABLE (member_id uuid, member_code text, points integer)
LANGUAGE sql
SECURITY DEFINER
SET search_path TO ''
AS $function$
    SELECT NULL::uuid, NULL::text, NULL::integer
    WHERE false;
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
SECURITY DEFINER
SET search_path TO ''
AS $function$
    SELECT NULL::uuid, NULL::text, NULL::text, NULL::integer, NULL::text, NULL::timestamptz
    WHERE false;
$function$;
