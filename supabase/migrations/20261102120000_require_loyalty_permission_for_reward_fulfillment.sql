-- Enforce the owner's granular loyalty permission when staff confirm a reward delivery.
-- The earlier implementation checked restaurant membership only, which allowed a
-- staff member to fulfill redemptions after the owner disabled the loyalty permission.
BEGIN;

CREATE OR REPLACE FUNCTION private.fulfill_loyalty_reward_redemption_internal(
    p_redemption_id uuid
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_restaurant_id uuid;
    v_status text;
BEGIN
    IF (SELECT auth.uid()) IS NULL THEN
        RAISE EXCEPTION 'É necessário iniciar sessão.';
    END IF;

    SELECT rr.restaurant_id, rr.status
      INTO v_restaurant_id, v_status
    FROM public.loyalty_reward_redemptions AS rr
    WHERE rr.id = p_redemption_id
    FOR UPDATE;

    IF v_restaurant_id IS NULL
       OR NOT private.has_restaurant_permission(v_restaurant_id, 'loyalty') THEN
        RAISE EXCEPTION 'Sem autorização para confirmar este resgate.';
    END IF;

    IF v_status = 'fulfilled' THEN
        RETURN;
    END IF;

    UPDATE public.loyalty_reward_redemptions
       SET status = 'fulfilled',
           fulfilled_by = (SELECT auth.uid()),
           fulfilled_at = now()
     WHERE id = p_redemption_id
       AND status = 'pending';
END;
$function$;

REVOKE ALL ON FUNCTION private.fulfill_loyalty_reward_redemption_internal(uuid)
    FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION private.fulfill_loyalty_reward_redemption_internal(uuid)
    TO authenticated;

COMMIT;
