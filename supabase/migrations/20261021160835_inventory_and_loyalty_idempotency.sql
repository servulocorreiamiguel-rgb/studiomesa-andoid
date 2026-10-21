-- Idempotency keys for stock movements and manual loyalty point adjustments.
-- Existing rows remain valid; legacy rows have a NULL client_request_id.
BEGIN;

ALTER TABLE public.restaurant_inventory_movements
  ADD COLUMN IF NOT EXISTS client_request_id uuid;
ALTER TABLE public.loyalty_transactions
  ADD COLUMN IF NOT EXISTS client_request_id uuid;

CREATE UNIQUE INDEX IF NOT EXISTS restaurant_inventory_movements_request_id_idx
  ON public.restaurant_inventory_movements (restaurant_id, created_by, client_request_id)
  WHERE client_request_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS loyalty_transactions_request_id_idx
  ON public.loyalty_transactions (restaurant_id, created_by, client_request_id)
  WHERE client_request_id IS NOT NULL;

CREATE OR REPLACE FUNCTION public.adjust_inventory_stock_idempotent(p_client_request_id uuid, p_item_id uuid, p_delta numeric, p_note text DEFAULT NULL::text)
 RETURNS TABLE(item_id uuid, quantity numeric)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE
  v_restaurant_id uuid;
  v_old_quantity numeric(12,3);
  v_new_quantity numeric(12,3);
  v_note text := left(trim(coalesce(p_note, 'Ajuste de stock')), 500);
  v_existing public.restaurant_inventory_movements%rowtype;
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF p_client_request_id IS NULL THEN
    RAISE EXCEPTION 'O identificador da tentativa de stock está em falta.';
  END IF;
  IF p_item_id IS NULL THEN
    RAISE EXCEPTION 'Seleciona um artigo de stock válido.';
  END IF;
  IF p_delta IS NULL OR p_delta = 0 OR abs(p_delta) > 999999999 THEN
    RAISE EXCEPTION 'A alteração de stock não é válida.';
  END IF;

  SELECT m.* INTO v_existing
  FROM public.restaurant_inventory_movements AS m
  WHERE m.created_by = (SELECT auth.uid())
    AND m.client_request_id = p_client_request_id
  FOR UPDATE;

  IF FOUND THEN
    IF NOT private.is_restaurant_staff(v_existing.restaurant_id) THEN
      RAISE EXCEPTION 'Sem autorização para consultar este movimento de stock.';
    END IF;
    IF v_existing.item_id = p_item_id
       AND v_existing.delta = p_delta
       AND v_existing.note = v_note THEN
      RETURN QUERY SELECT v_existing.item_id, v_existing.quantity_after;
      RETURN;
    END IF;
    RAISE EXCEPTION 'Esta tentativa já foi utilizada com dados de stock diferentes.';
  END IF;

  SELECT i.restaurant_id, i.quantity
    INTO v_restaurant_id, v_old_quantity
  FROM public.restaurant_inventory_items AS i
  WHERE i.id = p_item_id
  FOR UPDATE;

  IF v_restaurant_id IS NULL THEN
    RAISE EXCEPTION 'Artigo de stock não encontrado.';
  END IF;
  IF NOT private.is_restaurant_staff(v_restaurant_id) THEN
    RAISE EXCEPTION 'Sem autorização para alterar o stock deste restaurante.';
  END IF;

  -- Recheck after locking the item row: an identical concurrent request may
  -- have completed while this request waited for the lock.
  SELECT m.* INTO v_existing
  FROM public.restaurant_inventory_movements AS m
  WHERE m.created_by = (SELECT auth.uid())
    AND m.client_request_id = p_client_request_id
  FOR UPDATE;

  IF FOUND THEN
    IF NOT private.is_restaurant_staff(v_existing.restaurant_id) THEN
      RAISE EXCEPTION 'Sem autorização para consultar este movimento de stock.';
    END IF;
    IF v_existing.item_id = p_item_id
       AND v_existing.delta = p_delta
       AND v_existing.note = v_note THEN
      RETURN QUERY SELECT v_existing.item_id, v_existing.quantity_after;
      RETURN;
    END IF;
    RAISE EXCEPTION 'Esta tentativa já foi utilizada com dados de stock diferentes.';
  END IF;

  v_new_quantity := v_old_quantity + p_delta;
  IF v_new_quantity < 0 THEN
    RAISE EXCEPTION 'O stock não pode ficar negativo.';
  END IF;
  IF v_new_quantity > 999999999 THEN
    RAISE EXCEPTION 'A quantidade excede o limite permitido.';
  END IF;

  UPDATE public.restaurant_inventory_items
  SET quantity = v_new_quantity, updated_at = now()
  WHERE id = p_item_id;

  INSERT INTO public.restaurant_inventory_movements
    (restaurant_id, item_id, delta, quantity_after, note, created_by, client_request_id)
  VALUES
    (v_restaurant_id, p_item_id, p_delta, v_new_quantity, v_note, (SELECT auth.uid()), p_client_request_id);

  RETURN QUERY SELECT p_item_id, v_new_quantity::numeric;
END;
$function$

CREATE OR REPLACE FUNCTION public.adjust_loyalty_points_idempotent(p_client_request_id uuid, p_member_id uuid, p_delta integer, p_note text DEFAULT NULL::text)
 RETURNS TABLE(member_id uuid, member_code text, points integer)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE
  v_restaurant_id uuid;
  v_member_code text;
  v_old_points integer;
  v_new_points integer;
  v_current_points integer;
  v_note text := nullif(trim(coalesce(p_note, '')), '');
  v_existing public.loyalty_transactions%rowtype;
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF p_client_request_id IS NULL THEN
    RAISE EXCEPTION 'O identificador da tentativa de pontos está em falta.';
  END IF;
  IF p_delta IS NULL OR p_delta = 0 OR abs(p_delta::bigint) > 100000 THEN
    RAISE EXCEPTION 'A alteração de pontos não é válida.';
  END IF;

  SELECT t.* INTO v_existing
  FROM public.loyalty_transactions AS t
  WHERE t.created_by = (SELECT auth.uid())
    AND t.client_request_id = p_client_request_id
  FOR UPDATE;

  IF FOUND THEN
    IF NOT private.is_restaurant_staff(v_existing.restaurant_id) THEN
      RAISE EXCEPTION 'Sem autorização para consultar esta transação de pontos.';
    END IF;
    IF v_existing.member_id = p_member_id
       AND v_existing.points_delta = p_delta
       AND v_existing.note IS NOT DISTINCT FROM v_note THEN
      SELECT lm.member_code, lm.points
        INTO v_member_code, v_current_points
      FROM public.loyalty_members AS lm
      WHERE lm.id = p_member_id
        AND lm.restaurant_id = v_existing.restaurant_id;
      IF NOT FOUND THEN
        RAISE EXCEPTION 'Cartão de cliente não encontrado.';
      END IF;
      RETURN QUERY SELECT p_member_id, v_member_code, v_current_points;
      RETURN;
    END IF;
    RAISE EXCEPTION 'Esta tentativa já foi utilizada com dados de pontos diferentes.';
  END IF;

  SELECT lm.restaurant_id, lm.member_code, lm.points
    INTO v_restaurant_id, v_member_code, v_old_points
  FROM public.loyalty_members AS lm
  WHERE lm.id = p_member_id
  FOR UPDATE;

  IF v_restaurant_id IS NULL THEN
    RAISE EXCEPTION 'Cartão de cliente não encontrado.';
  END IF;
  IF NOT private.is_restaurant_staff(v_restaurant_id) THEN
    RAISE EXCEPTION 'Sem autorização para alterar pontos deste restaurante.';
  END IF;

  -- Recheck after locking the member so a concurrent identical retry cannot
  -- apply the points adjustment twice.
  SELECT t.* INTO v_existing
  FROM public.loyalty_transactions AS t
  WHERE t.created_by = (SELECT auth.uid())
    AND t.client_request_id = p_client_request_id
  FOR UPDATE;

  IF FOUND THEN
    IF NOT private.is_restaurant_staff(v_existing.restaurant_id) THEN
      RAISE EXCEPTION 'Sem autorização para consultar esta transação de pontos.';
    END IF;
    IF v_existing.member_id = p_member_id
       AND v_existing.points_delta = p_delta
       AND v_existing.note IS NOT DISTINCT FROM v_note THEN
      SELECT lm.member_code, lm.points
        INTO v_member_code, v_current_points
      FROM public.loyalty_members AS lm
      WHERE lm.id = p_member_id
        AND lm.restaurant_id = v_existing.restaurant_id;
      IF NOT FOUND THEN
        RAISE EXCEPTION 'Cartão de cliente não encontrado.';
      END IF;
      RETURN QUERY SELECT p_member_id, v_member_code, v_current_points;
      RETURN;
    END IF;
    RAISE EXCEPTION 'Esta tentativa já foi utilizada com dados de pontos diferentes.';
  END IF;

  v_new_points := v_old_points + p_delta;
  IF v_new_points < 0 THEN
    RAISE EXCEPTION 'O saldo não pode ficar negativo.';
  END IF;

  UPDATE public.loyalty_members AS lm
  SET points = v_new_points
  WHERE lm.id = p_member_id;

  INSERT INTO public.loyalty_transactions
    (restaurant_id, member_id, created_by, points_delta, points_after, note, client_request_id)
  VALUES
    (v_restaurant_id, p_member_id, (SELECT auth.uid()), p_delta, v_new_points, v_note, p_client_request_id);

  RETURN QUERY SELECT p_member_id, v_member_code, v_new_points;
END;
$function$

REVOKE ALL ON FUNCTION public.adjust_inventory_stock_idempotent(uuid, uuid, numeric, text)
  FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.adjust_inventory_stock_idempotent(uuid, uuid, numeric, text)
  TO authenticated;

REVOKE ALL ON FUNCTION public.adjust_loyalty_points_idempotent(uuid, uuid, integer, text)
  FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.adjust_loyalty_points_idempotent(uuid, uuid, integer, text)
  TO authenticated;

COMMIT;
