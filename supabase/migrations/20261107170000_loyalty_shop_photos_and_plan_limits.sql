-- Owner-managed loyalty catalog photos and server-enforced plan limits.
-- Active catalogue limits are enforced under a restaurant row lock; APK flavour is never trusted.
BEGIN;

CREATE TABLE IF NOT EXISTS private.restaurant_loyalty_entitlements (
    restaurant_id uuid PRIMARY KEY REFERENCES public.restaurants(id) ON DELETE CASCADE,
    plan_code text NOT NULL CHECK (plan_code IN ('essencial', 'profissional', 'premium')),
    assigned_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE private.restaurant_loyalty_entitlements ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE private.restaurant_loyalty_entitlements FROM PUBLIC, anon, authenticated, service_role;
COMMENT ON TABLE private.restaurant_loyalty_entitlements IS
    'Privileged, server-owned plan assignments. Never accept plan changes from Android or user metadata. Missing rows default to Essencial.';

ALTER TABLE public.loyalty_rewards
    ADD COLUMN IF NOT EXISTS image_path text;

ALTER TABLE public.loyalty_rewards
    DROP CONSTRAINT IF EXISTS loyalty_rewards_image_path_shape_check;
ALTER TABLE public.loyalty_rewards
    ADD CONSTRAINT loyalty_rewards_image_path_shape_check
    CHECK (
        image_path IS NULL OR
        image_path ~ (
            '^' || restaurant_id::text || '/' || id::text || '/[0-9a-f-]{36}[.]jpg$'
        )
    );

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('loyalty-reward-images', 'loyalty-reward-images', true, 1572864, ARRAY['image/jpeg']::text[])
ON CONFLICT (id) DO UPDATE
SET public = true,
    file_size_limit = 1572864,
    allowed_mime_types = ARRAY['image/jpeg']::text[];

CREATE OR REPLACE FUNCTION private.can_upload_loyalty_reward_image(
    p_restaurant_id uuid,
    p_reward_id uuid
)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $function$
    SELECT
        (SELECT auth.uid()) IS NOT NULL
        AND EXISTS (
            SELECT 1
            FROM public.restaurant_staff rs
            JOIN public.loyalty_rewards r
              ON r.restaurant_id = rs.restaurant_id
             AND r.id = p_reward_id
            LEFT JOIN private.restaurant_loyalty_entitlements e
              ON e.restaurant_id = rs.restaurant_id
            WHERE rs.restaurant_id = p_restaurant_id
              AND rs.user_id = (SELECT auth.uid())
              AND rs.role = 'owner'
              AND COALESCE(e.plan_code, 'essencial') IN ('profissional', 'premium')
        );
$function$;

CREATE OR REPLACE FUNCTION private.can_delete_loyalty_reward_image(
    p_restaurant_id uuid
)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $function$
    SELECT (SELECT auth.uid()) IS NOT NULL
       AND EXISTS (
            SELECT 1
            FROM public.restaurant_staff rs
            WHERE rs.restaurant_id = p_restaurant_id
              AND rs.user_id = (SELECT auth.uid())
              AND rs.role = 'owner'
       );
$function$;

REVOKE ALL ON FUNCTION private.can_upload_loyalty_reward_image(uuid, uuid) FROM PUBLIC, anon, authenticated, service_role;
REVOKE ALL ON FUNCTION private.can_delete_loyalty_reward_image(uuid) FROM PUBLIC, anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION private.can_upload_loyalty_reward_image(uuid, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION private.can_delete_loyalty_reward_image(uuid) TO authenticated;

DROP POLICY IF EXISTS loyalty_reward_images_owner_insert ON storage.objects;
CREATE POLICY loyalty_reward_images_owner_insert
ON storage.objects
FOR INSERT
TO authenticated
WITH CHECK (
    bucket_id = 'loyalty-reward-images'
    AND name ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}/[0-9a-f-]{36}[.]jpg$'
    AND private.can_upload_loyalty_reward_image(
        split_part(name, '/', 1)::uuid,
        split_part(name, '/', 2)::uuid
    )
);

DROP POLICY IF EXISTS loyalty_reward_images_owner_delete ON storage.objects;
CREATE POLICY loyalty_reward_images_owner_delete
ON storage.objects
FOR DELETE
TO authenticated
USING (
    bucket_id = 'loyalty-reward-images'
    AND name ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}/[0-9a-f-]{36}[.]jpg$'
    AND private.can_delete_loyalty_reward_image(split_part(name, '/', 1)::uuid)
);

-- Replace the customer getter's return type only after removing its public wrapper.
DROP FUNCTION IF EXISTS public.get_customer_loyalty_rewards(uuid);
DROP FUNCTION IF EXISTS private.get_customer_loyalty_rewards_internal(uuid);
CREATE FUNCTION private.get_customer_loyalty_rewards_internal(p_member_id uuid)
RETURNS TABLE (
    id uuid,
    name text,
    description text,
    points_cost integer,
    stock_limit integer,
    redeemed_count integer,
    image_path text
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
    v_restaurant_id uuid;
    v_plan_code text;
    v_photos_enabled boolean;
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

    SELECT COALESCE(e.plan_code, 'essencial')
    INTO v_plan_code
    FROM (SELECT v_restaurant_id AS restaurant_id) scope
    LEFT JOIN private.restaurant_loyalty_entitlements e
      ON e.restaurant_id = scope.restaurant_id;

    v_photos_enabled := v_plan_code IN ('profissional', 'premium');

    RETURN QUERY
    SELECT r.id, r.name, r.description, r.points_cost, r.stock_limit,
           r.redeemed_count,
           CASE WHEN v_photos_enabled THEN r.image_path ELSE NULL END
    FROM public.loyalty_rewards r
    WHERE r.restaurant_id = v_restaurant_id
      AND r.is_active
      AND (r.stock_limit IS NULL OR r.redeemed_count < r.stock_limit)
    ORDER BY r.points_cost ASC, r.name ASC;
END;
$function$;

CREATE FUNCTION public.get_customer_loyalty_rewards(p_member_id uuid)
RETURNS TABLE (
    id uuid,
    name text,
    description text,
    points_cost integer,
    stock_limit integer,
    redeemed_count integer,
    image_path text
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $function$
    SELECT *
    FROM private.get_customer_loyalty_rewards_internal(p_member_id);
$function$;

-- Replace the restaurant getter with the same image field, while keeping staff access scoped.
DROP FUNCTION IF EXISTS public.get_restaurant_loyalty_rewards(uuid);
DROP FUNCTION IF EXISTS private.get_restaurant_loyalty_rewards_internal(uuid);
CREATE FUNCTION private.get_restaurant_loyalty_rewards_internal(p_restaurant_id uuid)
RETURNS TABLE (
    id uuid,
    name text,
    description text,
    points_cost integer,
    is_active boolean,
    stock_limit integer,
    redeemed_count integer,
    image_path text
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
    v_plan_code text;
    v_photos_enabled boolean;
BEGIN
    IF (SELECT auth.uid()) IS NULL OR p_restaurant_id IS NULL
       OR NOT private.is_restaurant_staff(p_restaurant_id) THEN
        RAISE EXCEPTION 'Sem autorização para consultar a loja de pontos.';
    END IF;

    SELECT COALESCE(e.plan_code, 'essencial') INTO v_plan_code
    FROM (SELECT p_restaurant_id AS restaurant_id) scope
    LEFT JOIN private.restaurant_loyalty_entitlements e
      ON e.restaurant_id = scope.restaurant_id;
    v_photos_enabled := v_plan_code IN ('profissional', 'premium');

    RETURN QUERY
    SELECT r.id, r.name, r.description, r.points_cost,
           r.is_active, r.stock_limit, r.redeemed_count,
           CASE WHEN v_photos_enabled THEN r.image_path ELSE NULL END
    FROM public.loyalty_rewards r
    WHERE r.restaurant_id = p_restaurant_id
    ORDER BY r.is_active DESC, r.points_cost ASC, r.name ASC;
END;
$function$;

CREATE FUNCTION public.get_restaurant_loyalty_rewards(p_restaurant_id uuid)
RETURNS TABLE (
    id uuid,
    name text,
    description text,
    points_cost integer,
    is_active boolean,
    stock_limit integer,
    redeemed_count integer,
    image_path text
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $function$
    SELECT *
    FROM private.get_restaurant_loyalty_rewards_internal(p_restaurant_id);
$function$;

-- The plan is looked up in a private table; missing assignment is fail-safe Essencial.
CREATE OR REPLACE FUNCTION private.get_restaurant_loyalty_entitlements_internal(p_restaurant_id uuid)
RETURNS TABLE (
    plan_code text,
    reward_limit integer,
    active_rewards integer,
    photos_enabled boolean,
    max_photo_bytes integer
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
    v_plan_code text;
BEGIN
    IF (SELECT auth.uid()) IS NULL OR p_restaurant_id IS NULL OR NOT EXISTS (
        SELECT 1
        FROM public.restaurant_staff rs
        WHERE rs.restaurant_id = p_restaurant_id
          AND rs.user_id = (SELECT auth.uid())
          AND rs.role = 'owner'
    ) THEN
        RAISE EXCEPTION 'Apenas o proprietário pode consultar os limites do plano.';
    END IF;

    SELECT COALESCE(e.plan_code, 'essencial')
    INTO v_plan_code
    FROM (SELECT p_restaurant_id AS restaurant_id) scope
    LEFT JOIN private.restaurant_loyalty_entitlements e
      ON e.restaurant_id = scope.restaurant_id;

    RETURN QUERY
    SELECT v_plan_code,
           CASE v_plan_code
               WHEN 'profissional' THEN 15
               WHEN 'premium' THEN 50
               ELSE 5
           END,
           (SELECT count(*)::integer
            FROM public.loyalty_rewards r
            WHERE r.restaurant_id = p_restaurant_id AND r.is_active),
           v_plan_code IN ('profissional', 'premium'),
           1572864;
END;
$function$;

CREATE FUNCTION public.get_restaurant_loyalty_entitlements(p_restaurant_id uuid)
RETURNS TABLE (
    plan_code text,
    reward_limit integer,
    active_rewards integer,
    photos_enabled boolean,
    max_photo_bytes integer
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $function$
    SELECT *
    FROM private.get_restaurant_loyalty_entitlements_internal(p_restaurant_id);
$function$;

-- Recreate the save function with a per-restaurant row lock so parallel owner requests
-- cannot exceed the active catalogue limit.
DROP FUNCTION IF EXISTS public.save_loyalty_reward(uuid, uuid, text, text, integer, integer, boolean);
DROP FUNCTION IF EXISTS private.save_loyalty_reward_internal(uuid, uuid, text, text, integer, integer, boolean);
CREATE FUNCTION private.save_loyalty_reward_internal(
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
SET search_path = ''
AS $function$
DECLARE
    v_name text := pg_catalog.btrim(coalesce(p_name, ''));
    v_description text := pg_catalog.btrim(coalesce(p_description, ''));
    v_reward_id uuid;
    v_plan_code text;
    v_reward_limit integer;
    v_active_count integer;
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

    -- Serialize reward creation/activation against concurrent owner devices.
    PERFORM 1 FROM public.restaurants r
    WHERE r.id = p_restaurant_id
    FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Restaurante não encontrado.';
    END IF;

    SELECT COALESCE(e.plan_code, 'essencial')
    INTO v_plan_code
    FROM (SELECT p_restaurant_id AS restaurant_id) scope
    LEFT JOIN private.restaurant_loyalty_entitlements e
      ON e.restaurant_id = scope.restaurant_id;

    v_reward_limit := CASE v_plan_code
        WHEN 'profissional' THEN 15
        WHEN 'premium' THEN 50
        ELSE 5
    END;

    IF p_is_active THEN
        SELECT count(*)::integer INTO v_active_count
        FROM public.loyalty_rewards r
        WHERE r.restaurant_id = p_restaurant_id
          AND r.is_active
          AND r.id <> p_reward_id;

        IF v_active_count >= v_reward_limit THEN
            RAISE EXCEPTION 'O plano % permite até % recompensas ativas. Desativa uma recompensa ou altera o plano para continuar.',
                v_plan_code, v_reward_limit;
        END IF;
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
SECURITY DEFINER
SET search_path = ''
AS $function$
    SELECT private.save_loyalty_reward_internal(
        p_restaurant_id, p_reward_id, p_name, p_description,
        p_points_cost, p_stock_limit, p_is_active
    );
$function$;

-- Set or clear one photo path only after the object exists in the public catalogue bucket.
CREATE OR REPLACE FUNCTION private.set_loyalty_reward_image_path_internal(
    p_restaurant_id uuid,
    p_reward_id uuid,
    p_image_path text
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
    v_plan_code text;
BEGIN
    IF (SELECT auth.uid()) IS NULL OR p_restaurant_id IS NULL OR p_reward_id IS NULL
       OR NOT EXISTS (
            SELECT 1
            FROM public.restaurant_staff rs
            WHERE rs.restaurant_id = p_restaurant_id
              AND rs.user_id = (SELECT auth.uid())
              AND rs.role = 'owner'
       ) THEN
        RAISE EXCEPTION 'Apenas o proprietário pode gerir fotografias de recompensas.';
    END IF;

    IF p_image_path IS NOT NULL THEN
        SELECT COALESCE(e.plan_code, 'essencial')
        INTO v_plan_code
        FROM (SELECT p_restaurant_id AS restaurant_id) scope
        LEFT JOIN private.restaurant_loyalty_entitlements e
          ON e.restaurant_id = scope.restaurant_id;

        IF v_plan_code NOT IN ('profissional', 'premium') THEN
            RAISE EXCEPTION 'As fotografias da loja de pontos estão disponíveis nos planos Profissional e Premium.';
        END IF;

        IF p_image_path !~ (
            '^' || p_restaurant_id::text || '/' || p_reward_id::text || '/[0-9a-f-]{36}[.]jpg$'
        ) THEN
            RAISE EXCEPTION 'O caminho da fotografia não é válido.';
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM storage.objects o
            WHERE o.bucket_id = 'loyalty-reward-images'
              AND o.name = p_image_path
              AND o.metadata ->> 'mimetype' = 'image/jpeg'
              AND COALESCE(NULLIF(o.metadata ->> 'size', '')::bigint, 0) BETWEEN 1 AND 1572864
        ) THEN
            RAISE EXCEPTION 'A fotografia não existe ou excede os limites permitidos.';
        END IF;
    END IF;

    UPDATE public.loyalty_rewards r
       SET image_path = p_image_path,
           updated_at = now()
     WHERE r.id = p_reward_id
       AND r.restaurant_id = p_restaurant_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'A recompensa não existe neste restaurante.';
    END IF;
END;
$function$;

CREATE FUNCTION public.set_loyalty_reward_image_path(
    p_restaurant_id uuid,
    p_reward_id uuid,
    p_image_path text
)
RETURNS void
LANGUAGE sql
SECURITY DEFINER
SET search_path = ''
AS $function$
    SELECT private.set_loyalty_reward_image_path_internal(
        p_restaurant_id, p_reward_id, p_image_path
    );
$function$;

-- Explicitly restrict every privileged function to authenticated callers at the public boundary.
REVOKE ALL ON FUNCTION private.get_customer_loyalty_rewards_internal(uuid) FROM PUBLIC, anon, authenticated, service_role;
REVOKE ALL ON FUNCTION private.get_restaurant_loyalty_rewards_internal(uuid) FROM PUBLIC, anon, authenticated, service_role;
REVOKE ALL ON FUNCTION private.get_restaurant_loyalty_entitlements_internal(uuid) FROM PUBLIC, anon, authenticated, service_role;
REVOKE ALL ON FUNCTION private.save_loyalty_reward_internal(uuid, uuid, text, text, integer, integer, boolean) FROM PUBLIC, anon, authenticated, service_role;
REVOKE ALL ON FUNCTION private.set_loyalty_reward_image_path_internal(uuid, uuid, text) FROM PUBLIC, anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION private.get_customer_loyalty_rewards_internal(uuid) TO postgres;
GRANT EXECUTE ON FUNCTION private.get_restaurant_loyalty_rewards_internal(uuid) TO postgres;
GRANT EXECUTE ON FUNCTION private.get_restaurant_loyalty_entitlements_internal(uuid) TO postgres;
GRANT EXECUTE ON FUNCTION private.save_loyalty_reward_internal(uuid, uuid, text, text, integer, integer, boolean) TO postgres;
GRANT EXECUTE ON FUNCTION private.set_loyalty_reward_image_path_internal(uuid, uuid, text) TO postgres;

REVOKE ALL ON FUNCTION public.get_customer_loyalty_rewards(uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.get_restaurant_loyalty_rewards(uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.get_restaurant_loyalty_entitlements(uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.save_loyalty_reward(uuid, uuid, text, text, integer, integer, boolean) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.set_loyalty_reward_image_path(uuid, uuid, text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_customer_loyalty_rewards(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_restaurant_loyalty_rewards(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_restaurant_loyalty_entitlements(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.save_loyalty_reward(uuid, uuid, text, text, integer, integer, boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION public.set_loyalty_reward_image_path(uuid, uuid, text) TO authenticated;

NOTIFY pgrst, 'reload schema';
COMMIT;
