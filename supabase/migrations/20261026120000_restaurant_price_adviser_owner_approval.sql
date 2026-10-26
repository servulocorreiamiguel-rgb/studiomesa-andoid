-- Owner-controlled application of an individual price recommendation.
-- The setting is off until the owner enables it. Every apply call is re-authorized,
-- recalculated against current server data, and recorded in a private audit table.
BEGIN;

CREATE TABLE IF NOT EXISTS private.restaurant_price_adviser_settings (
  restaurant_id uuid PRIMARY KEY REFERENCES public.restaurants(id) ON DELETE CASCADE,
  enabled boolean NOT NULL DEFAULT false,
  updated_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE private.restaurant_price_adviser_settings ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE private.restaurant_price_adviser_settings FROM PUBLIC, anon, authenticated;

CREATE TABLE IF NOT EXISTS private.restaurant_price_adviser_audit (
  action_id uuid PRIMARY KEY,
  restaurant_id uuid NOT NULL REFERENCES public.restaurants(id) ON DELETE CASCADE,
  -- Keep the identifier and name snapshot even if the menu item is later removed.
  menu_item_id uuid NOT NULL,
  item_name text NOT NULL CHECK (char_length(trim(item_name)) BETWEEN 1 AND 120),
  acted_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
  previous_price numeric(10, 2) NOT NULL CHECK (previous_price > 0),
  applied_price numeric(10, 2) NOT NULL CHECK (applied_price > 0),
  unit_cost numeric(10, 2) NOT NULL CHECK (unit_cost > 0),
  target_cost_percent numeric(5, 2) NOT NULL
    CHECK (target_cost_percent > 0 AND target_cost_percent <= 100),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS restaurant_price_adviser_audit_recent_idx
  ON private.restaurant_price_adviser_audit (restaurant_id, created_at DESC);

ALTER TABLE private.restaurant_price_adviser_audit ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE private.restaurant_price_adviser_audit FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.get_restaurant_price_adviser_enabled()
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_user_id uuid := (SELECT auth.uid());
  v_restaurant_id uuid;
  v_enabled boolean;
BEGIN
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;

  SELECT rs.restaurant_id
    INTO v_restaurant_id
    FROM public.restaurant_staff AS rs
   WHERE rs.user_id = v_user_id
     AND rs.role = 'owner'
   ORDER BY rs.created_at ASC
   LIMIT 1;

  IF v_restaurant_id IS NULL THEN
    RAISE EXCEPTION 'Só o proprietário pode consultar esta autorização.';
  END IF;

  SELECT settings.enabled
    INTO v_enabled
    FROM private.restaurant_price_adviser_settings AS settings
   WHERE settings.restaurant_id = v_restaurant_id;

  RETURN COALESCE(v_enabled, false);
END;
$function$;

CREATE OR REPLACE FUNCTION public.set_restaurant_price_adviser_enabled(p_enabled boolean)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_user_id uuid := (SELECT auth.uid());
  v_restaurant_id uuid;
  v_enabled boolean;
BEGIN
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF p_enabled IS NULL THEN
    RAISE EXCEPTION 'Indica se a autorização deve ficar ativa ou inativa.';
  END IF;

  SELECT rs.restaurant_id
    INTO v_restaurant_id
    FROM public.restaurant_staff AS rs
   WHERE rs.user_id = v_user_id
     AND rs.role = 'owner'
   ORDER BY rs.created_at ASC
   LIMIT 1;

  IF v_restaurant_id IS NULL THEN
    RAISE EXCEPTION 'Só o proprietário pode alterar esta autorização.';
  END IF;

  INSERT INTO private.restaurant_price_adviser_settings AS existing (
    restaurant_id, enabled, updated_by, updated_at
  )
  VALUES (v_restaurant_id, p_enabled, v_user_id, pg_catalog.clock_timestamp())
  ON CONFLICT (restaurant_id)
  DO UPDATE
     SET enabled = EXCLUDED.enabled,
         updated_by = EXCLUDED.updated_by,
         updated_at = EXCLUDED.updated_at
  RETURNING existing.enabled INTO v_enabled;

  RETURN v_enabled;
END;
$function$;

CREATE OR REPLACE FUNCTION public.apply_restaurant_advised_menu_price(
  p_action_id uuid,
  p_item_id uuid,
  p_expected_price numeric,
  p_new_price numeric,
  p_target_cost_percent numeric
)
RETURNS TABLE(
  action_id uuid,
  menu_item_id uuid,
  item_name text,
  previous_price numeric,
  applied_price numeric,
  unit_cost numeric,
  target_cost_percent numeric,
  acted_at timestamptz,
  was_replay boolean
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_user_id uuid := (SELECT auth.uid());
  v_restaurant_id uuid;
  v_enabled boolean;
  v_current_price numeric(10, 2);
  v_unit_cost numeric(10, 2);
  v_item_name text;
  v_recommended_price numeric;
  v_applied_price numeric(10, 2);
  v_applied_at timestamptz;
  v_existing private.restaurant_price_adviser_audit%ROWTYPE;
BEGIN
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;

  SELECT rs.restaurant_id
    INTO v_restaurant_id
    FROM public.restaurant_staff AS rs
   WHERE rs.user_id = v_user_id
     AND rs.role = 'owner'
   ORDER BY rs.created_at ASC
   LIMIT 1;

  IF v_restaurant_id IS NULL THEN
    RAISE EXCEPTION 'Só o proprietário pode aplicar um preço recomendado.';
  END IF;
  IF p_action_id IS NULL OR p_item_id IS NULL THEN
    RAISE EXCEPTION 'O identificador da ação ou do artigo não é válido.';
  END IF;

  -- Return a previous committed result for an equivalent retry. A replay never
  -- performs a second menu update, even if the owner has since switched the setting off.
  SELECT audit.*
    INTO v_existing
    FROM private.restaurant_price_adviser_audit AS audit
   WHERE audit.action_id = p_action_id;

  IF FOUND THEN
    IF v_existing.restaurant_id IS DISTINCT FROM v_restaurant_id
       OR v_existing.acted_by IS DISTINCT FROM v_user_id
       OR v_existing.menu_item_id IS DISTINCT FROM p_item_id
       OR v_existing.previous_price IS DISTINCT FROM p_expected_price
       OR v_existing.applied_price IS DISTINCT FROM p_new_price
       OR v_existing.target_cost_percent IS DISTINCT FROM p_target_cost_percent THEN
      RAISE EXCEPTION 'Este identificador de ação já foi utilizado com outros dados.';
    END IF;

    RETURN QUERY
    SELECT v_existing.action_id, v_existing.menu_item_id, v_existing.item_name,
           v_existing.previous_price, v_existing.applied_price, v_existing.unit_cost,
           v_existing.target_cost_percent, v_existing.created_at, true;
    RETURN;
  END IF;

  IF p_expected_price IS NULL
     OR p_expected_price <= 0
     OR p_expected_price > 99999999.99
     OR p_expected_price <> pg_catalog.round(p_expected_price, 2) THEN
    RAISE EXCEPTION 'O preço atual esperado não é válido.';
  END IF;
  IF p_new_price IS NULL
     OR p_new_price <= 0
     OR p_new_price > 99999999.99
     OR p_new_price <> pg_catalog.round(p_new_price, 2) THEN
    RAISE EXCEPTION 'O novo preço não é válido.';
  END IF;
  IF p_target_cost_percent IS NULL
     OR p_target_cost_percent < 0.01
     OR p_target_cost_percent > 100
     OR p_target_cost_percent <> pg_catalog.round(p_target_cost_percent, 2) THEN
    RAISE EXCEPTION 'O objetivo de custo deve estar entre 0,01% e 100%, com no máximo duas casas decimais.';
  END IF;

  -- Lock the current setting. Disabling waits for an in-flight application to
  -- finish; a missing row means disabled.
  SELECT settings.enabled
    INTO v_enabled
    FROM private.restaurant_price_adviser_settings AS settings
   WHERE settings.restaurant_id = v_restaurant_id
   FOR SHARE;

  IF NOT FOUND OR COALESCE(v_enabled, false) IS NOT TRUE THEN
    RAISE EXCEPTION 'O proprietário não ativou a aplicação de preços recomendados.';
  END IF;

  -- Lock the current row and constrain it to the caller's own active menu.
  SELECT mi.price, mi.cost, mi.name
    INTO v_current_price, v_unit_cost, v_item_name
    FROM public.menu_items AS mi
   WHERE mi.id = p_item_id
     AND mi.restaurant_id = v_restaurant_id
     AND mi.is_active = true
   FOR UPDATE OF mi;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'O artigo não existe, está inativo ou não pertence ao restaurante autorizado.';
  END IF;

  -- Another call with the same action ID may have completed while this
  -- transaction waited for the menu row lock.
  SELECT audit.*
    INTO v_existing
    FROM private.restaurant_price_adviser_audit AS audit
   WHERE audit.action_id = p_action_id;

  IF FOUND THEN
    IF v_existing.restaurant_id IS DISTINCT FROM v_restaurant_id
       OR v_existing.acted_by IS DISTINCT FROM v_user_id
       OR v_existing.menu_item_id IS DISTINCT FROM p_item_id
       OR v_existing.previous_price IS DISTINCT FROM p_expected_price
       OR v_existing.applied_price IS DISTINCT FROM p_new_price
       OR v_existing.target_cost_percent IS DISTINCT FROM p_target_cost_percent THEN
      RAISE EXCEPTION 'Este identificador de ação já foi utilizado com outros dados.';
    END IF;

    RETURN QUERY
    SELECT v_existing.action_id, v_existing.menu_item_id, v_existing.item_name,
           v_existing.previous_price, v_existing.applied_price, v_existing.unit_cost,
           v_existing.target_cost_percent, v_existing.created_at, true;
    RETURN;
  END IF;

  IF v_current_price IS DISTINCT FROM p_expected_price THEN
    RAISE EXCEPTION 'O preço mudou desde a análise. Atualiza a carta e calcula novamente antes de aplicar.';
  END IF;
  IF v_unit_cost IS NULL OR v_unit_cost <= 0 THEN
    RAISE EXCEPTION 'O custo unitário atual não é válido; não é possível calcular um preço recomendado.';
  END IF;

  -- Recompute from the locked, current database cost; never trust the client value.
  v_recommended_price := pg_catalog.round(v_unit_cost * 100 / p_target_cost_percent, 2);
  IF v_recommended_price > 99999999.99 THEN
    RAISE EXCEPTION 'O preço calculado excede o limite suportado pela carta.';
  END IF;
  IF p_new_price IS DISTINCT FROM v_recommended_price THEN
    RAISE EXCEPTION 'A recomendação já não corresponde aos dados atuais. Atualiza a carta e calcula novamente.';
  END IF;
  IF v_recommended_price = v_current_price THEN
    RAISE EXCEPTION 'O preço recomendado é igual ao atual; não há alteração a aplicar.';
  END IF;

  UPDATE public.menu_items AS mi
     SET price = v_recommended_price
   WHERE mi.id = p_item_id
     AND mi.restaurant_id = v_restaurant_id
     AND mi.is_active = true
     AND mi.price = v_current_price
  RETURNING mi.price INTO v_applied_price;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'O preço mudou durante a operação. Atualiza a carta e tenta novamente.';
  END IF;

  INSERT INTO private.restaurant_price_adviser_audit (
    action_id, restaurant_id, menu_item_id, item_name, acted_by,
    previous_price, applied_price, unit_cost, target_cost_percent
  )
  VALUES (
    p_action_id, v_restaurant_id, p_item_id, v_item_name, v_user_id,
    v_current_price, v_applied_price, v_unit_cost, p_target_cost_percent
  )
  ON CONFLICT (action_id) DO NOTHING
  RETURNING created_at INTO v_applied_at;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'A ação foi processada em simultâneo. Volta a consultar o histórico antes de repetir.';
  END IF;

  RETURN QUERY
  SELECT p_action_id, p_item_id, v_item_name, v_current_price, v_applied_price,
         v_unit_cost, p_target_cost_percent, v_applied_at, false;
END;
$function$;

CREATE OR REPLACE FUNCTION public.get_restaurant_price_adviser_audit(p_limit integer DEFAULT 20)
RETURNS TABLE(
  action_id uuid,
  menu_item_id uuid,
  item_name text,
  previous_price numeric,
  applied_price numeric,
  unit_cost numeric,
  target_cost_percent numeric,
  acted_at timestamptz
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_user_id uuid := (SELECT auth.uid());
  v_restaurant_id uuid;
  v_limit integer := GREATEST(1, LEAST(COALESCE(p_limit, 20), 100));
BEGIN
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;

  SELECT rs.restaurant_id
    INTO v_restaurant_id
    FROM public.restaurant_staff AS rs
   WHERE rs.user_id = v_user_id
     AND rs.role = 'owner'
   ORDER BY rs.created_at ASC
   LIMIT 1;

  IF v_restaurant_id IS NULL THEN
    RAISE EXCEPTION 'Só o proprietário pode consultar o histórico de alterações de preços.';
  END IF;

  RETURN QUERY
  SELECT audit.action_id, audit.menu_item_id, audit.item_name,
         audit.previous_price, audit.applied_price, audit.unit_cost,
         audit.target_cost_percent, audit.created_at
    FROM private.restaurant_price_adviser_audit AS audit
   WHERE audit.restaurant_id = v_restaurant_id
   ORDER BY audit.created_at DESC, audit.action_id DESC
   LIMIT v_limit;
END;
$function$;

REVOKE ALL ON FUNCTION public.get_restaurant_price_adviser_enabled() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_restaurant_price_adviser_enabled() TO authenticated;

REVOKE ALL ON FUNCTION public.set_restaurant_price_adviser_enabled(boolean) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.set_restaurant_price_adviser_enabled(boolean) TO authenticated;

REVOKE ALL ON FUNCTION public.apply_restaurant_advised_menu_price(uuid, uuid, numeric, numeric, numeric) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.apply_restaurant_advised_menu_price(uuid, uuid, numeric, numeric, numeric) TO authenticated;

REVOKE ALL ON FUNCTION public.get_restaurant_price_adviser_audit(integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_restaurant_price_adviser_audit(integer) TO authenticated;

COMMIT;
