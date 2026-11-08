-- StudioMesa customer reactivation campaigns and in-app marketing notifications.
-- The visit date is measured from positive loyalty point awards in the existing QR flow.
ALTER TABLE public.loyalty_members
  ADD COLUMN IF NOT EXISTS marketing_opt_in boolean NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS public.restaurant_reactivation_campaigns (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  restaurant_id uuid NOT NULL REFERENCES public.restaurants(id) ON DELETE CASCADE,
  created_by uuid NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
  client_request_id uuid NOT NULL,
  title text NOT NULL CHECK (char_length(title) BETWEEN 1 AND 80),
  message text NOT NULL CHECK (char_length(message) BETWEEN 1 AND 500),
  promotion_type text NOT NULL DEFAULT 'none'
    CHECK (promotion_type IN ('none', 'discount', 'cashback', 'extra_points', 'voucher', 'free_item', 'custom')),
  promotion_title text,
  promotion_details text,
  inactivity_days integer NOT NULL DEFAULT 30 CHECK (inactivity_days BETWEEN 30 AND 365),
  status text NOT NULL DEFAULT 'sent' CHECK (status = 'sent'),
  recipient_count integer NOT NULL DEFAULT 0 CHECK (recipient_count >= 0),
  created_at timestamptz NOT NULL DEFAULT now(),
  sent_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT restaurant_reactivation_campaigns_request_unique
    UNIQUE (restaurant_id, created_by, client_request_id),
  CONSTRAINT restaurant_reactivation_campaigns_promotion_check
    CHECK (
      (promotion_type = 'none' AND promotion_title IS NULL AND promotion_details IS NULL)
      OR
      (promotion_type <> 'none'
       AND promotion_title IS NOT NULL
       AND char_length(promotion_title) BETWEEN 1 AND 80
       AND promotion_details IS NOT NULL
       AND char_length(promotion_details) BETWEEN 1 AND 250)
    )
);

CREATE TABLE IF NOT EXISTS public.restaurant_customer_notifications (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  campaign_id uuid NOT NULL REFERENCES public.restaurant_reactivation_campaigns(id) ON DELETE CASCADE,
  restaurant_id uuid NOT NULL REFERENCES public.restaurants(id) ON DELETE CASCADE,
  member_id uuid NOT NULL REFERENCES public.loyalty_members(id) ON DELETE CASCADE,
  recipient_user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  title text NOT NULL CHECK (char_length(title) BETWEEN 1 AND 80),
  message text NOT NULL CHECK (char_length(message) BETWEEN 1 AND 500),
  promotion_type text NOT NULL DEFAULT 'none'
    CHECK (promotion_type IN ('none', 'discount', 'cashback', 'extra_points', 'voucher', 'free_item', 'custom')),
  promotion_title text,
  promotion_details text,
  created_at timestamptz NOT NULL DEFAULT now(),
  read_at timestamptz,
  CONSTRAINT restaurant_customer_notifications_campaign_member_unique
    UNIQUE (campaign_id, member_id)
);

CREATE INDEX IF NOT EXISTS loyalty_transactions_inactive_member_lookup_idx
  ON public.loyalty_transactions (restaurant_id, member_id, created_at DESC)
  WHERE points_delta > 0;

CREATE INDEX IF NOT EXISTS restaurant_customer_notifications_recipient_created_idx
  ON public.restaurant_customer_notifications (recipient_user_id, created_at DESC);

ALTER TABLE public.restaurant_reactivation_campaigns ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.restaurant_customer_notifications ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE public.restaurant_reactivation_campaigns FROM PUBLIC, anon, authenticated;
REVOKE ALL ON TABLE public.restaurant_customer_notifications FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.get_my_marketing_opt_in()
 RETURNS boolean
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE
  v_enabled boolean;
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;

  SELECT lm.marketing_opt_in
    INTO v_enabled
  FROM public.loyalty_members AS lm
  WHERE lm.auth_user_id = (SELECT auth.uid())
  ORDER BY lm.created_at ASC
  LIMIT 1;

  RETURN coalesce(v_enabled, false);
END;
$function$


CREATE OR REPLACE FUNCTION public.set_my_marketing_opt_in(p_enabled boolean)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE
  v_rows integer;
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;

  IF p_enabled IS NULL THEN
    RAISE EXCEPTION 'A preferência de campanhas não é válida.';
  END IF;

  UPDATE public.loyalty_members AS lm
     SET marketing_opt_in = p_enabled
   WHERE lm.auth_user_id = (SELECT auth.uid());

  GET DIAGNOSTICS v_rows = ROW_COUNT;
  IF v_rows = 0 THEN
    RAISE EXCEPTION 'Associa primeiro a tua conta ao restaurante.';
  END IF;

  RETURN p_enabled;
END;
$function$


CREATE OR REPLACE FUNCTION public.get_restaurant_inactive_members(p_restaurant_id uuid, p_inactivity_days integer DEFAULT 30)
 RETURNS TABLE(member_id uuid, display_name text, member_code text, points integer, last_visit_at timestamp with time zone, marketing_opt_in boolean, has_account boolean)
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF p_restaurant_id IS NULL
     OR NOT EXISTS (
       SELECT 1
       FROM public.restaurant_staff AS rs
       WHERE rs.restaurant_id = p_restaurant_id
         AND rs.user_id = (SELECT auth.uid())
         AND rs.role = 'owner'
     ) THEN
    RAISE EXCEPTION 'Só o proprietário pode consultar os clientes inativos.';
  END IF;
  IF p_inactivity_days IS NULL OR p_inactivity_days NOT BETWEEN 30 AND 365 THEN
    RAISE EXCEPTION 'O período de inatividade deve estar entre 30 e 365 dias.';
  END IF;

  RETURN QUERY
  SELECT
    lm.id,
    lm.display_name,
    lm.member_code,
    lm.points,
    activity.last_visit_at,
    lm.marketing_opt_in,
    (lm.auth_user_id IS NOT NULL) AS has_account
  FROM public.loyalty_members AS lm
  JOIN LATERAL (
    SELECT max(lt.created_at) AS last_visit_at
    FROM public.loyalty_transactions AS lt
    WHERE lt.restaurant_id = lm.restaurant_id
      AND lt.member_id = lm.id
      AND lt.points_delta > 0
  ) AS activity ON activity.last_visit_at IS NOT NULL
  WHERE lm.restaurant_id = p_restaurant_id
    AND activity.last_visit_at <= now() - make_interval(days => p_inactivity_days)
  ORDER BY activity.last_visit_at ASC, lm.display_name ASC;
END;
$function$


CREATE OR REPLACE FUNCTION public.send_reactivation_campaign(p_restaurant_id uuid, p_client_request_id uuid, p_title text, p_message text, p_promotion_type text DEFAULT 'none'::text, p_promotion_title text DEFAULT NULL::text, p_promotion_details text DEFAULT NULL::text, p_inactivity_days integer DEFAULT 30)
 RETURNS TABLE(campaign_id uuid, recipient_count integer)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE
  v_user_id uuid := (SELECT auth.uid());
  v_title text := trim(coalesce(p_title, ''));
  v_message text := trim(coalesce(p_message, ''));
  v_promotion_type text := lower(trim(coalesce(p_promotion_type, 'none')));
  v_promotion_title text := nullif(trim(coalesce(p_promotion_title, '')), '');
  v_promotion_details text := nullif(trim(coalesce(p_promotion_details, '')), '');
  v_campaign_id uuid;
  v_existing public.restaurant_reactivation_campaigns%rowtype;
  v_recipient_count integer := 0;
BEGIN
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF p_restaurant_id IS NULL
     OR NOT EXISTS (
       SELECT 1
       FROM public.restaurant_staff AS rs
       WHERE rs.restaurant_id = p_restaurant_id
         AND rs.user_id = v_user_id
         AND rs.role = 'owner'
     ) THEN
    RAISE EXCEPTION 'Só o proprietário pode enviar campanhas.';
  END IF;
  IF p_client_request_id IS NULL THEN
    RAISE EXCEPTION 'O identificador seguro da campanha está em falta.';
  END IF;
  IF char_length(v_title) NOT BETWEEN 1 AND 80 THEN
    RAISE EXCEPTION 'O título deve ter entre 1 e 80 caracteres.';
  END IF;
  IF char_length(v_message) NOT BETWEEN 1 AND 500 THEN
    RAISE EXCEPTION 'A mensagem deve ter entre 1 e 500 caracteres.';
  END IF;
  IF v_promotion_type NOT IN ('none', 'discount', 'cashback', 'extra_points', 'voucher', 'free_item', 'custom') THEN
    RAISE EXCEPTION 'O tipo de promoção selecionado não é válido.';
  END IF;
  IF v_promotion_type = 'none' THEN
    v_promotion_title := NULL;
    v_promotion_details := NULL;
  ELSIF char_length(coalesce(v_promotion_title, '')) NOT BETWEEN 1 AND 80
     OR char_length(coalesce(v_promotion_details, '')) NOT BETWEEN 1 AND 250 THEN
    RAISE EXCEPTION 'Preenche o nome e os detalhes da promoção (máximo de 80 e 250 caracteres).';
  END IF;
  IF p_inactivity_days IS NULL OR p_inactivity_days NOT BETWEEN 30 AND 365 THEN
    RAISE EXCEPTION 'O período de inatividade deve estar entre 30 e 365 dias.';
  END IF;

  SELECT c.* INTO v_existing
  FROM public.restaurant_reactivation_campaigns AS c
  WHERE c.restaurant_id = p_restaurant_id
    AND c.created_by = v_user_id
    AND c.client_request_id = p_client_request_id
  FOR UPDATE;

  IF FOUND THEN
    IF v_existing.title = v_title
       AND v_existing.message = v_message
       AND v_existing.promotion_type = v_promotion_type
       AND v_existing.promotion_title IS NOT DISTINCT FROM v_promotion_title
       AND v_existing.promotion_details IS NOT DISTINCT FROM v_promotion_details
       AND v_existing.inactivity_days = p_inactivity_days THEN
      RETURN QUERY SELECT v_existing.id, v_existing.recipient_count;
      RETURN;
    END IF;
    RAISE EXCEPTION 'Este pedido já foi utilizado para outra campanha.';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM public.loyalty_members AS lm
    JOIN LATERAL (
      SELECT max(lt.created_at) AS last_visit_at
      FROM public.loyalty_transactions AS lt
      WHERE lt.restaurant_id = lm.restaurant_id
        AND lt.member_id = lm.id
        AND lt.points_delta > 0
    ) AS activity ON activity.last_visit_at IS NOT NULL
    WHERE lm.restaurant_id = p_restaurant_id
      AND lm.auth_user_id IS NOT NULL
      AND lm.marketing_opt_in = true
      AND activity.last_visit_at <= now() - make_interval(days => p_inactivity_days)
  ) THEN
    RAISE EXCEPTION 'Não existem clientes inativos com consentimento para receber campanhas.';
  END IF;

  INSERT INTO public.restaurant_reactivation_campaigns (
    restaurant_id, created_by, client_request_id, title, message,
    promotion_type, promotion_title, promotion_details, inactivity_days
  )
  VALUES (
    p_restaurant_id, v_user_id, p_client_request_id, v_title, v_message,
    v_promotion_type, v_promotion_title, v_promotion_details, p_inactivity_days
  )
  ON CONFLICT (restaurant_id, created_by, client_request_id) DO NOTHING
  RETURNING id INTO v_campaign_id;

  IF v_campaign_id IS NULL THEN
    SELECT c.* INTO v_existing
    FROM public.restaurant_reactivation_campaigns AS c
    WHERE c.restaurant_id = p_restaurant_id
      AND c.created_by = v_user_id
      AND c.client_request_id = p_client_request_id
    FOR UPDATE;

    IF NOT FOUND
       OR v_existing.title <> v_title
       OR v_existing.message <> v_message
       OR v_existing.promotion_type <> v_promotion_type
       OR v_existing.promotion_title IS DISTINCT FROM v_promotion_title
       OR v_existing.promotion_details IS DISTINCT FROM v_promotion_details
       OR v_existing.inactivity_days <> p_inactivity_days THEN
      RAISE EXCEPTION 'Esta tentativa já foi utilizada com outros dados.';
    END IF;

    RETURN QUERY SELECT v_existing.id, v_existing.recipient_count;
    RETURN;
  END IF;

  INSERT INTO public.restaurant_customer_notifications (
    campaign_id, restaurant_id, member_id, recipient_user_id,
    title, message, promotion_type, promotion_title, promotion_details
  )
  SELECT
    v_campaign_id, lm.restaurant_id, lm.id, lm.auth_user_id,
    v_title, v_message, v_promotion_type, v_promotion_title, v_promotion_details
  FROM public.loyalty_members AS lm
  JOIN LATERAL (
    SELECT max(lt.created_at) AS last_visit_at
    FROM public.loyalty_transactions AS lt
    WHERE lt.restaurant_id = lm.restaurant_id
      AND lt.member_id = lm.id
      AND lt.points_delta > 0
  ) AS activity ON activity.last_visit_at IS NOT NULL
  WHERE lm.restaurant_id = p_restaurant_id
    AND lm.auth_user_id IS NOT NULL
    AND lm.marketing_opt_in = true
    AND activity.last_visit_at <= now() - make_interval(days => p_inactivity_days)
  ON CONFLICT (campaign_id, member_id) DO NOTHING;

  GET DIAGNOSTICS v_recipient_count = ROW_COUNT;

  IF v_recipient_count = 0 THEN
    RAISE EXCEPTION 'Não foi possível encontrar destinatários com consentimento ativo.';
  END IF;

  UPDATE public.restaurant_reactivation_campaigns AS c
     SET recipient_count = v_recipient_count,
         sent_at = now()
   WHERE c.id = v_campaign_id;

  RETURN QUERY SELECT v_campaign_id, v_recipient_count;
END;
$function$


CREATE OR REPLACE FUNCTION public.list_my_marketing_notifications()
 RETURNS TABLE(id uuid, title text, message text, promotion_type text, promotion_title text, promotion_details text, restaurant_name text, created_at timestamp with time zone, read_at timestamp with time zone)
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;

  RETURN QUERY
  SELECT
    n.id,
    n.title,
    n.message,
    n.promotion_type,
    n.promotion_title,
    n.promotion_details,
    r.name,
    n.created_at,
    n.read_at
  FROM public.restaurant_customer_notifications AS n
  JOIN public.restaurants AS r ON r.id = n.restaurant_id
  WHERE n.recipient_user_id = (SELECT auth.uid())
  ORDER BY n.created_at DESC
  LIMIT 100;
END;
$function$


CREATE OR REPLACE FUNCTION public.mark_my_marketing_notification_read(p_notification_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF p_notification_id IS NULL THEN
    RAISE EXCEPTION 'A notificação selecionada não é válida.';
  END IF;

  UPDATE public.restaurant_customer_notifications AS n
     SET read_at = coalesce(n.read_at, now())
   WHERE n.id = p_notification_id
     AND n.recipient_user_id = (SELECT auth.uid());

  IF NOT FOUND THEN
    RAISE EXCEPTION 'A notificação não existe ou não pertence à tua conta.';
  END IF;
END;
$function$


REVOKE ALL ON FUNCTION public.get_my_marketing_opt_in() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.set_my_marketing_opt_in(boolean) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_restaurant_inactive_members(uuid, integer) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.send_reactivation_campaign(uuid, uuid, text, text, text, text, text, integer) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.list_my_marketing_notifications() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.mark_my_marketing_notification_read(uuid) FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.get_my_marketing_opt_in() TO authenticated;
GRANT EXECUTE ON FUNCTION public.set_my_marketing_opt_in(boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_restaurant_inactive_members(uuid, integer) TO authenticated;
GRANT EXECUTE ON FUNCTION public.send_reactivation_campaign(uuid, uuid, text, text, text, text, text, integer) TO authenticated;
GRANT EXECUTE ON FUNCTION public.list_my_marketing_notifications() TO authenticated;
GRANT EXECUTE ON FUNCTION public.mark_my_marketing_notification_read(uuid) TO authenticated;
