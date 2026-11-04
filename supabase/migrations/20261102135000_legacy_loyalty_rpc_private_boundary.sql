-- Keep legacy loyalty RPCs compatible while moving any privileged
-- implementations that exist in this deployment into the non-exposed schema.
--
-- The clean migration history owns the customer membership/menu RPCs below.
-- Some earlier QR and redemption RPCs were historically provisioned manually
-- in hosted projects, so those are migrated conditionally when present.
BEGIN;

GRANT USAGE ON SCHEMA private TO authenticated;

-- These two RPCs are part of the versioned migration history.
ALTER FUNCTION public.get_member_restaurant_menu(uuid) SET SCHEMA private;
ALTER FUNCTION private.get_member_restaurant_menu(uuid)
    RENAME TO get_member_restaurant_menu_internal;

ALTER FUNCTION public.get_my_loyalty_membership() SET SCHEMA private;
ALTER FUNCTION private.get_my_loyalty_membership()
    RENAME TO get_my_loyalty_membership_internal;

REVOKE ALL ON FUNCTION private.get_member_restaurant_menu_internal(uuid)
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private.get_my_loyalty_membership_internal()
    FROM PUBLIC, anon, authenticated;

GRANT EXECUTE ON FUNCTION private.get_member_restaurant_menu_internal(uuid)
    TO authenticated;
GRANT EXECUTE ON FUNCTION private.get_my_loyalty_membership_internal()
    TO authenticated;

DO $wrapper$
DECLARE
    v_result_type text;
BEGIN
    -- Preserve the exact OUT-column contract from the deployment being migrated.
    -- Hosted databases may contain a richer menu signature than the original
    -- three-column migration baseline.
    v_result_type := pg_get_function_result(
        'private.get_member_restaurant_menu_internal(uuid)'::regprocedure
    );
    EXECUTE format(
        $ddl$
            CREATE FUNCTION public.get_member_restaurant_menu(p_member_id uuid)
            RETURNS %s
            LANGUAGE sql
            STABLE
            SECURITY INVOKER
            SET search_path TO ''
            AS $function$
                SELECT *
                FROM private.get_member_restaurant_menu_internal($1);
            $function$;
        $ddl$,
        v_result_type
    );
END;
$wrapper$;

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

REVOKE ALL ON FUNCTION public.get_member_restaurant_menu(uuid)
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.get_my_loyalty_membership()
    FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_member_restaurant_menu(uuid)
    TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_my_loyalty_membership()
    TO authenticated;

-- Legacy endpoints below are present in some already-provisioned projects,
-- but are intentionally not assumed to exist in a clean Git migration replay.
DO $migration$
BEGIN
    IF to_regprocedure('public.create_loyalty_qr_token()') IS NOT NULL THEN
        EXECUTE 'ALTER FUNCTION public.create_loyalty_qr_token() SET SCHEMA private';
        EXECUTE 'ALTER FUNCTION private.create_loyalty_qr_token() RENAME TO create_loyalty_qr_token_internal';
        EXECUTE 'REVOKE ALL ON FUNCTION private.create_loyalty_qr_token_internal() FROM PUBLIC, anon, authenticated';
        EXECUTE 'GRANT EXECUTE ON FUNCTION private.create_loyalty_qr_token_internal() TO authenticated';
        EXECUTE $ddl$
            CREATE FUNCTION public.create_loyalty_qr_token()
            RETURNS TABLE (qr_token uuid, expires_at timestamptz)
            LANGUAGE sql
            SECURITY INVOKER
            SET search_path TO ''
            AS $function$
                SELECT * FROM private.create_loyalty_qr_token_internal();
            $function$;
        $ddl$;
        EXECUTE 'REVOKE ALL ON FUNCTION public.create_loyalty_qr_token() FROM PUBLIC, anon, authenticated';
        EXECUTE 'GRANT EXECUTE ON FUNCTION public.create_loyalty_qr_token() TO authenticated';
    END IF;

    IF to_regprocedure('public.award_loyalty_points_from_qr(uuid,integer,uuid,text)') IS NOT NULL THEN
        EXECUTE 'ALTER FUNCTION public.award_loyalty_points_from_qr(uuid, integer, uuid, text) SET SCHEMA private';
        EXECUTE 'ALTER FUNCTION private.award_loyalty_points_from_qr(uuid, integer, uuid, text) RENAME TO award_loyalty_points_from_qr_internal';
        EXECUTE 'REVOKE ALL ON FUNCTION private.award_loyalty_points_from_qr_internal(uuid, integer, uuid, text) FROM PUBLIC, anon, authenticated';
        EXECUTE 'GRANT EXECUTE ON FUNCTION private.award_loyalty_points_from_qr_internal(uuid, integer, uuid, text) TO authenticated';
        EXECUTE $ddl$
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
        $ddl$;
        EXECUTE 'REVOKE ALL ON FUNCTION public.award_loyalty_points_from_qr(uuid, integer, uuid, text) FROM PUBLIC, anon, authenticated';
        EXECUTE 'GRANT EXECUTE ON FUNCTION public.award_loyalty_points_from_qr(uuid, integer, uuid, text) TO authenticated';
    END IF;

    IF to_regprocedure('public.get_restaurant_reward_redemptions(uuid)') IS NOT NULL THEN
        EXECUTE 'ALTER FUNCTION public.get_restaurant_reward_redemptions(uuid) SET SCHEMA private';
        EXECUTE 'ALTER FUNCTION private.get_restaurant_reward_redemptions(uuid) RENAME TO get_restaurant_reward_redemptions_internal';
        EXECUTE 'REVOKE ALL ON FUNCTION private.get_restaurant_reward_redemptions_internal(uuid) FROM PUBLIC, anon, authenticated';
        EXECUTE 'GRANT EXECUTE ON FUNCTION private.get_restaurant_reward_redemptions_internal(uuid) TO authenticated';
        EXECUTE $ddl$
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
        $ddl$;
        EXECUTE 'REVOKE ALL ON FUNCTION public.get_restaurant_reward_redemptions(uuid) FROM PUBLIC, anon, authenticated';
        EXECUTE 'GRANT EXECUTE ON FUNCTION public.get_restaurant_reward_redemptions(uuid) TO authenticated';
    END IF;
END;
$migration$;

COMMIT;
