-- Disambiguate the campaign/member unique constraint from the RETURNS TABLE output variable.
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
  ON CONFLICT ON CONSTRAINT restaurant_customer_notifications_campaign_member_unique DO NOTHING;

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
$function$;
