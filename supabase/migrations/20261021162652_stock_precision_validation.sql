-- Reject values with precision greater than the numeric(12,3) stock columns.
-- This prevents silent rounding and keeps movement idempotency comparisons consistent.
BEGIN;

CREATE OR REPLACE FUNCTION public.save_inventory_item(p_restaurant_id uuid, p_item_id uuid, p_name text, p_unit text, p_quantity numeric, p_low_stock_threshold numeric, p_note text DEFAULT NULL::text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_item_id uuid;
  v_old_quantity numeric(12,3) := 0;
  v_delta numeric(12,3);
  v_name text := trim(coalesce(p_name, ''));
  v_unit text := trim(coalesce(p_unit, ''));
begin
  if (select auth.uid()) is null then
    raise exception 'É necessário iniciar sessão.';
  end if;
  if p_restaurant_id is null or not private.is_restaurant_staff(p_restaurant_id) then
    raise exception 'Sem autorização para gerir o stock deste restaurante.';
  end if;
  if char_length(v_name) not between 1 and 100 or char_length(v_unit) not between 1 and 24 then
    raise exception 'Indica um nome e uma unidade válidos.';
  end if;
  if p_quantity is null or p_low_stock_threshold is null
     or p_quantity < 0 or p_low_stock_threshold < 0
     or p_quantity > 999999999 or p_low_stock_threshold > 999999999 then
    raise exception 'A quantidade ou o limite de stock não é válido.';
  end if;

  if p_quantity <> round(p_quantity, 3)
     or p_low_stock_threshold <> round(p_low_stock_threshold, 3) then
    raise exception 'As quantidades de stock admitem no máximo três casas decimais.';
  end if;

  if p_item_id is null then
    insert into public.restaurant_inventory_items
      (restaurant_id, name, unit, quantity, low_stock_threshold, created_by, updated_at)
    values
      (p_restaurant_id, v_name, v_unit, p_quantity, p_low_stock_threshold, (select auth.uid()), now())
    returning id into v_item_id;

    if p_quantity > 0 then
      insert into public.restaurant_inventory_movements
        (restaurant_id, item_id, delta, quantity_after, note, created_by)
      values
        (p_restaurant_id, v_item_id, p_quantity, p_quantity,
         left(trim(coalesce(p_note, 'Stock inicial')), 500), (select auth.uid()));
    end if;
    return v_item_id;
  end if;

  select i.quantity into v_old_quantity
  from public.restaurant_inventory_items as i
  where i.id = p_item_id and i.restaurant_id = p_restaurant_id
  for update;

  if not found then
    raise exception 'Artigo de stock não encontrado neste restaurante.';
  end if;

  update public.restaurant_inventory_items
  set name = v_name,
      unit = v_unit,
      quantity = p_quantity,
      low_stock_threshold = p_low_stock_threshold,
      updated_at = now()
  where id = p_item_id and restaurant_id = p_restaurant_id
  returning id into v_item_id;

  v_delta := p_quantity - v_old_quantity;
  if v_delta <> 0 then
    insert into public.restaurant_inventory_movements
      (restaurant_id, item_id, delta, quantity_after, note, created_by)
    values
      (p_restaurant_id, v_item_id, v_delta, p_quantity,
       left(trim(coalesce(p_note, 'Ajuste de stock')), 500), (select auth.uid()));
  end if;

  return v_item_id;
end;
$function$;

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

  IF p_delta <> round(p_delta, 3) THEN
    RAISE EXCEPTION 'A alteração de stock pode ter no máximo três casas decimais.';
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
$function$;

COMMIT;
