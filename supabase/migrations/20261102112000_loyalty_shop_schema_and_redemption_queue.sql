-- Reconcile the hosted loyalty shop schema with the versioned migration history.
-- The hosted project already has the base objects; IF NOT EXISTS / OR REPLACE make
-- this safe there while allowing a clean migration replay to build the same feature.
BEGIN;

CREATE TABLE IF NOT EXISTS public.loyalty_rewards (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id uuid NOT NULL REFERENCES public.restaurants(id) ON DELETE CASCADE,
    name text NOT NULL CHECK (char_length(trim(name)) BETWEEN 1 AND 100),
    description text NOT NULL DEFAULT '' CHECK (char_length(description) <= 500),
    points_cost integer NOT NULL CHECK (points_cost BETWEEN 1 AND 1000000),
    is_active boolean NOT NULL DEFAULT true,
    stock_limit integer CHECK (stock_limit IS NULL OR stock_limit >= 0),
    redeemed_count integer NOT NULL DEFAULT 0 CHECK (redeemed_count >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS loyalty_rewards_restaurant_active_idx
    ON public.loyalty_rewards (restaurant_id, is_active, points_cost);

CREATE TABLE IF NOT EXISTS public.loyalty_reward_redemptions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id uuid NOT NULL REFERENCES public.restaurants(id) ON DELETE CASCADE,
    member_id uuid NOT NULL REFERENCES public.loyalty_members(id) ON DELETE CASCADE,
    reward_id uuid NOT NULL REFERENCES public.loyalty_rewards(id) ON DELETE CASCADE,
    points_spent integer NOT NULL CHECK (points_spent > 0),
    status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'fulfilled')),
    redeemed_by uuid NOT NULL REFERENCES auth.users(id),
    redeemed_at timestamptz NOT NULL DEFAULT now(),
    fulfilled_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
    fulfilled_at timestamptz,
    client_request_id uuid NOT NULL,
    CONSTRAINT loyalty_reward_redemptions_request_key UNIQUE (member_id, client_request_id)
);

CREATE INDEX IF NOT EXISTS loyalty_reward_redemptions_restaurant_status_idx
    ON public.loyalty_reward_redemptions (restaurant_id, status, redeemed_at DESC);
CREATE INDEX IF NOT EXISTS loyalty_reward_redemptions_reward_id_idx
    ON public.loyalty_reward_redemptions (reward_id);
CREATE INDEX IF NOT EXISTS loyalty_reward_redemptions_redeemed_by_idx
    ON public.loyalty_reward_redemptions (redeemed_by);
CREATE INDEX IF NOT EXISTS loyalty_reward_redemptions_fulfilled_by_idx
    ON public.loyalty_reward_redemptions (fulfilled_by)
    WHERE fulfilled_by IS NOT NULL;

ALTER TABLE public.loyalty_rewards ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.loyalty_reward_redemptions ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.loyalty_rewards FROM PUBLIC, anon, authenticated;
REVOKE ALL ON TABLE public.loyalty_reward_redemptions FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.save_loyalty_reward(
    p_restaurant_id uuid,
    p_reward_id uuid,
    p_name text,
    p_description text,
    p_points_cost integer,
    p_stock_limit integer,
    p_is_active boolean
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_name text := pg_catalog.btrim(coalesce(p_name, ''));
    v_description text := pg_catalog.btrim(coalesce(p_description, ''));
    v_reward_id uuid;
BEGIN
    IF (SELECT auth.uid()) IS NULL THEN
        RAISE EXCEPTION 'É necessário iniciar sessão.';
    END IF;
    IF p_restaurant_id IS NULL OR NOT EXISTS (
        SELECT 1
        FROM public.restaurant_staff rs
        WHERE rs.restaurant_id = p_restaurant_id
          AND rs.user_id = (SELECT auth.uid())
          AND rs.role = 'owner'
    ) THEN
        RAISE EXCEPTION 'Apenas o proprietário pode gerir a loja de pontos.';
    END IF;
    IF p_reward_id IS NULL THEN
        RAISE EXCEPTION 'O identificador da recompensa está em falta.';
    END IF;
    IF pg_catalog.char_length(v_name) NOT BETWEEN 1 AND 100
       OR pg_catalog.char_length(v_description) > 500
       OR p_points_cost IS NULL OR p_points_cost NOT BETWEEN 1 AND 1000000
       OR (p_stock_limit IS NOT NULL AND p_stock_limit < 0)
       OR p_is_active IS NULL THEN
        RAISE EXCEPTION 'Os dados da recompensa não são válidos.';
    END IF;

    INSERT INTO public.loyalty_rewards
        (id, restaurant_id, name, description, points_cost, stock_limit, is_active)
    VALUES
        (p_reward_id, p_restaurant_id, v_name, v_description, p_points_cost, p_stock_limit, p_is_active)
    ON CONFLICT (id) DO UPDATE
       SET name = excluded.name,
           description = excluded.description,
           points_cost = excluded.points_cost,
           stock_limit = excluded.stock_limit,
           is_active = excluded.is_active,
           updated_at = now()
     WHERE public.loyalty_rewards.restaurant_id = excluded.restaurant_id
    RETURNING id INTO v_reward_id;

    IF v_reward_id IS NULL THEN
        RAISE EXCEPTION 'A recompensa não existe neste restaurante.';
    END IF;
    RETURN v_reward_id;
END;
$function$;

CREATE OR REPLACE FUNCTION public.get_restaurant_loyalty_rewards(p_restaurant_id uuid)
RETURNS TABLE (
    id uuid,
    name text,
    description text,
    points_cost integer,
    is_active boolean,
    stock_limit integer,
    redeemed_count integer
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN
    IF (SELECT auth.uid()) IS NULL OR p_restaurant_id IS NULL
       OR NOT private.is_restaurant_staff(p_restaurant_id) THEN
        RAISE EXCEPTION 'Sem autorização para consultar a loja de pontos.';
    END IF;

    RETURN QUERY
    SELECT r.id, r.name, r.description, r.points_cost,
           r.is_active, r.stock_limit, r.redeemed_count
    FROM public.loyalty_rewards r
    WHERE r.restaurant_id = p_restaurant_id
    ORDER BY r.is_active DESC, r.points_cost ASC, r.name ASC;
END;
$function$;

CREATE OR REPLACE FUNCTION public.get_customer_loyalty_rewards(p_member_id uuid)
RETURNS TABLE (
    id uuid,
    name text,
    description text,
    points_cost integer,
    stock_limit integer,
    redeemed_count integer
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_restaurant_id uuid;
BEGIN
    IF (SELECT auth.uid()) IS NULL THEN
        RAISE EXCEPTION 'É necessário iniciar sessão.';
    END IF;

    SELECT lm.restaurant_id INTO v_restaurant_id
    FROM public.loyalty_members lm
    WHERE lm.id = p_member_id
      AND lm.auth_user_id = (SELECT auth.uid());

    IF v_restaurant_id IS NULL THEN
        RAISE EXCEPTION 'O cartão de cliente não pertence a esta conta.';
    END IF;

    RETURN QUERY
    SELECT r.id, r.name, r.description, r.points_cost, r.stock_limit, r.redeemed_count
    FROM public.loyalty_rewards r
    WHERE r.restaurant_id = v_restaurant_id
      AND r.is_active
      AND (r.stock_limit IS NULL OR r.redeemed_count < r.stock_limit)
    ORDER BY r.points_cost ASC, r.name ASC;
END;
$function$;

CREATE OR REPLACE FUNCTION public.redeem_loyalty_reward(
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
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_member public.loyalty_members%rowtype;
    v_reward public.loyalty_rewards%rowtype;
    v_existing public.loyalty_reward_redemptions%rowtype;
    v_redemption_id uuid;
    v_remaining integer;
BEGIN
    IF (SELECT auth.uid()) IS NULL THEN
        RAISE EXCEPTION 'É necessário iniciar sessão.';
    END IF;
    IF p_reward_id IS NULL OR p_client_request_id IS NULL THEN
        RAISE EXCEPTION 'A recompensa ou o identificador de resgate está em falta.';
    END IF;

    SELECT lm.* INTO v_member
    FROM public.loyalty_members lm
    WHERE lm.auth_user_id = (SELECT auth.uid())
    ORDER BY lm.created_at ASC
    LIMIT 1
    FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Não existe um cartão de fidelização associado a esta conta.';
    END IF;

    SELECT rr.* INTO v_existing
    FROM public.loyalty_reward_redemptions rr
    WHERE rr.member_id = v_member.id
      AND rr.client_request_id = p_client_request_id
    FOR UPDATE;
    IF FOUND THEN
        IF v_existing.reward_id <> p_reward_id THEN
            RAISE EXCEPTION 'Este identificador já foi utilizado para outra recompensa.';
        END IF;
        SELECT r.name INTO v_reward.name
        FROM public.loyalty_rewards r
        WHERE r.id = p_reward_id;
        RETURN QUERY
        SELECT v_existing.id, v_reward.name, v_existing.points_spent,
               v_member.points, v_existing.status;
        RETURN;
    END IF;

    SELECT r.* INTO v_reward
    FROM public.loyalty_rewards r
    WHERE r.id = p_reward_id
      AND r.restaurant_id = v_member.restaurant_id
      AND r.is_active
    FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Esta recompensa já não está disponível.';
    END IF;
    IF v_reward.stock_limit IS NOT NULL AND v_reward.redeemed_count >= v_reward.stock_limit THEN
        RAISE EXCEPTION 'Esta recompensa já esgotou.';
    END IF;
    IF v_member.points < v_reward.points_cost THEN
        RAISE EXCEPTION 'Não tens pontos suficientes para esta recompensa.';
    END IF;

    v_remaining := v_member.points - v_reward.points_cost;
    UPDATE public.loyalty_members
    SET points = v_remaining
    WHERE id = v_member.id;

    UPDATE public.loyalty_rewards
    SET redeemed_count = redeemed_count + 1,
        updated_at = now()
    WHERE id = v_reward.id;

    INSERT INTO public.loyalty_reward_redemptions
        (restaurant_id, member_id, reward_id, points_spent, redeemed_by, client_request_id)
    VALUES
        (v_member.restaurant_id, v_member.id, v_reward.id, v_reward.points_cost,
         (SELECT auth.uid()), p_client_request_id)
    RETURNING id INTO v_redemption_id;

    INSERT INTO public.loyalty_transactions
        (restaurant_id, member_id, created_by, points_delta, points_after, note, client_request_id)
    VALUES
        (v_member.restaurant_id, v_member.id, (SELECT auth.uid()), -v_reward.points_cost,
         v_remaining, 'Resgate: ' || v_reward.name, p_client_request_id);

    RETURN QUERY
    SELECT v_redemption_id, v_reward.name, v_reward.points_cost, v_remaining, 'pending'::text;
END;
$function$;

CREATE OR REPLACE FUNCTION public.fulfill_loyalty_reward_redemption(p_redemption_id uuid)
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

    SELECT rr.restaurant_id, rr.status INTO v_restaurant_id, v_status
    FROM public.loyalty_reward_redemptions rr
    WHERE rr.id = p_redemption_id
    FOR UPDATE;

    IF v_restaurant_id IS NULL OR NOT private.is_restaurant_staff(v_restaurant_id) THEN
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

CREATE OR REPLACE FUNCTION public.get_my_loyalty_reward_redemptions()
RETURNS TABLE (
    redemption_id uuid,
    reward_name text,
    points_spent integer,
    status text,
    redeemed_at timestamptz,
    fulfilled_at timestamptz
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_member_id uuid;
BEGIN
    IF (SELECT auth.uid()) IS NULL THEN
        RAISE EXCEPTION 'É necessário iniciar sessão.';
    END IF;

    SELECT lm.id INTO v_member_id
    FROM public.loyalty_members lm
    WHERE lm.auth_user_id = (SELECT auth.uid())
    ORDER BY lm.created_at ASC
    LIMIT 1;

    IF v_member_id IS NULL THEN
        RETURN;
    END IF;

    RETURN QUERY
    SELECT rr.id, r.name, rr.points_spent, rr.status, rr.redeemed_at, rr.fulfilled_at
    FROM public.loyalty_reward_redemptions rr
    JOIN public.loyalty_rewards r ON r.id = rr.reward_id
    WHERE rr.member_id = v_member_id
    ORDER BY rr.redeemed_at DESC
    LIMIT 50;
END;
$function$;

CREATE OR REPLACE FUNCTION public.get_restaurant_loyalty_reward_redemptions(p_restaurant_id uuid)
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
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN
    IF (SELECT auth.uid()) IS NULL OR p_restaurant_id IS NULL
       OR NOT private.is_restaurant_staff(p_restaurant_id) THEN
        RAISE EXCEPTION 'Sem autorização para consultar os pedidos de recompensas.';
    END IF;

    RETURN QUERY
    SELECT rr.id,
           r.id,
           r.name,
           lm.display_name,
           lm.member_code,
           rr.points_spent,
           rr.status,
           rr.redeemed_at,
           rr.fulfilled_at
    FROM public.loyalty_reward_redemptions rr
    JOIN public.loyalty_rewards r
      ON r.id = rr.reward_id
     AND r.restaurant_id = rr.restaurant_id
    JOIN public.loyalty_members lm
      ON lm.id = rr.member_id
     AND lm.restaurant_id = rr.restaurant_id
    WHERE rr.restaurant_id = p_restaurant_id
    ORDER BY (rr.status = 'pending') DESC, rr.redeemed_at DESC
    LIMIT 100;
END;
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
