-- Persist current purchase cost per stock unit and snapshot it on each movement.
-- Historical movements remain NULL: the app must not reconstruct old waste using today's price.
BEGIN;

ALTER TABLE public.restaurant_inventory_items
  ADD COLUMN IF NOT EXISTS unit_cost_per_unit numeric(12,4);

ALTER TABLE public.restaurant_inventory_movements
  ADD COLUMN IF NOT EXISTS unit_cost_at_movement numeric(12,4),
  ADD COLUMN IF NOT EXISTS unit_at_movement text;

DO $constraints$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'restaurant_inventory_items_unit_cost_nonnegative'
      AND conrelid = 'public.restaurant_inventory_items'::regclass
  ) THEN
    ALTER TABLE public.restaurant_inventory_items
      ADD CONSTRAINT restaurant_inventory_items_unit_cost_nonnegative
      CHECK (unit_cost_per_unit IS NULL OR unit_cost_per_unit >= 0);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'restaurant_inventory_movements_unit_cost_nonnegative'
      AND conrelid = 'public.restaurant_inventory_movements'::regclass
  ) THEN
    ALTER TABLE public.restaurant_inventory_movements
      ADD CONSTRAINT restaurant_inventory_movements_unit_cost_nonnegative
      CHECK (unit_cost_at_movement IS NULL OR unit_cost_at_movement >= 0);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'restaurant_inventory_movements_unit_snapshot_valid'
      AND conrelid = 'public.restaurant_inventory_movements'::regclass
  ) THEN
    ALTER TABLE public.restaurant_inventory_movements
      ADD CONSTRAINT restaurant_inventory_movements_unit_snapshot_valid
      CHECK (
        unit_at_movement IS NULL
        OR char_length(trim(unit_at_movement)) BETWEEN 1 AND 24
      );
  END IF;
END;
$constraints$;

CREATE OR REPLACE FUNCTION private.capture_inventory_movement_unit_cost()
RETURNS trigger
LANGUAGE plpgsql
SET search_path TO ''
AS $function$
BEGIN
  SELECT i.unit_cost_per_unit, i.unit
    INTO NEW.unit_cost_at_movement, NEW.unit_at_movement
  FROM public.restaurant_inventory_items AS i
  WHERE i.id = NEW.item_id
    AND i.restaurant_id = NEW.restaurant_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Artigo de stock não pertence ao restaurante do movimento.';
  END IF;

  RETURN NEW;
END;
$function$;

REVOKE ALL ON FUNCTION private.capture_inventory_movement_unit_cost()
  FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS restaurant_inventory_movement_cost_snapshot
  ON public.restaurant_inventory_movements;

CREATE TRIGGER restaurant_inventory_movement_cost_snapshot
  BEFORE INSERT ON public.restaurant_inventory_movements
  FOR EACH ROW
  EXECUTE FUNCTION private.capture_inventory_movement_unit_cost();

CREATE OR REPLACE FUNCTION public.save_inventory_item_with_cost(
  p_restaurant_id uuid,
  p_item_id uuid,
  p_name text,
  p_unit text,
  p_quantity numeric,
  p_low_stock_threshold numeric,
  p_note text DEFAULT NULL::text,
  p_unit_cost_per_unit numeric DEFAULT NULL::numeric
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_item_id uuid;
  v_old_quantity numeric(12,3) := 0;
  v_delta numeric(12,3);
  v_name text := trim(coalesce(p_name, ''));
  v_unit text := trim(coalesce(p_unit, ''));
  v_note text := trim(coalesce(p_note, ''));
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF p_restaurant_id IS NULL OR NOT private.is_restaurant_staff(p_restaurant_id) THEN
    RAISE EXCEPTION 'Sem autorização para gerir o stock deste restaurante.';
  END IF;
  IF char_length(v_name) NOT BETWEEN 1 AND 100
     OR char_length(v_unit) NOT BETWEEN 1 AND 24 THEN
    RAISE EXCEPTION 'Indica um nome e uma unidade válidos.';
  END IF;
  IF p_quantity IS NULL OR p_low_stock_threshold IS NULL
     OR p_quantity < 0 OR p_low_stock_threshold < 0
     OR p_quantity > 999999999 OR p_low_stock_threshold > 999999999 THEN
    RAISE EXCEPTION 'A quantidade ou o limite de stock não é válido.';
  END IF;
  IF p_quantity <> round(p_quantity, 3)
     OR p_low_stock_threshold <> round(p_low_stock_threshold, 3) THEN
    RAISE EXCEPTION 'As quantidades de stock admitem no máximo três casas decimais.';
  END IF;
  IF length(coalesce(p_note, '')) > 500 THEN
    RAISE EXCEPTION 'A nota do movimento pode ter no máximo 500 caracteres.';
  END IF;
  IF p_unit_cost_per_unit IS NOT NULL
     AND (p_unit_cost_per_unit < 0
       OR p_unit_cost_per_unit > 99999999.9999
       OR p_unit_cost_per_unit <> round(p_unit_cost_per_unit, 4)) THEN
    RAISE EXCEPTION 'O custo unitário deve ser não negativo e ter no máximo quatro casas decimais.';
  END IF;

  IF p_item_id IS NULL THEN
    INSERT INTO public.restaurant_inventory_items
      (restaurant_id, name, unit, quantity, low_stock_threshold,
       unit_cost_per_unit, created_by, updated_at)
    VALUES
      (p_restaurant_id, v_name, v_unit, p_quantity, p_low_stock_threshold,
       p_unit_cost_per_unit, (SELECT auth.uid()), now())
    RETURNING id INTO v_item_id;

    IF p_quantity > 0 THEN
      INSERT INTO public.restaurant_inventory_movements
        (restaurant_id, item_id, delta, quantity_after, note, created_by)
      VALUES
        (p_restaurant_id, v_item_id, p_quantity, p_quantity,
         left(coalesce(v_note, 'Stock inicial'), 500), (SELECT auth.uid()));
    END IF;
    RETURN v_item_id;
  END IF;

  SELECT i.quantity INTO v_old_quantity
  FROM public.restaurant_inventory_items AS i
  WHERE i.id = p_item_id AND i.restaurant_id = p_restaurant_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Artigo de stock não encontrado neste restaurante.';
  END IF;

  UPDATE public.restaurant_inventory_items
  SET name = v_name,
      unit = v_unit,
      quantity = p_quantity,
      low_stock_threshold = p_low_stock_threshold,
      unit_cost_per_unit = p_unit_cost_per_unit,
      updated_at = now()
  WHERE id = p_item_id AND restaurant_id = p_restaurant_id
  RETURNING id INTO v_item_id;

  v_delta := p_quantity - v_old_quantity;
  IF v_delta <> 0 THEN
    INSERT INTO public.restaurant_inventory_movements
      (restaurant_id, item_id, delta, quantity_after, note, created_by)
    VALUES
      (p_restaurant_id, v_item_id, v_delta, p_quantity,
       left(coalesce(v_note, 'Ajuste de stock'), 500), (SELECT auth.uid()));
  END IF;

  RETURN v_item_id;
END;
$function$;

CREATE OR REPLACE FUNCTION public.create_inventory_item_idempotent_with_cost(
  p_client_request_id uuid,
  p_restaurant_id uuid,
  p_name text,
  p_unit text,
  p_quantity numeric,
  p_low_stock_threshold numeric,
  p_note text DEFAULT NULL::text,
  p_unit_cost_per_unit numeric DEFAULT NULL::numeric
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_user_id uuid := (SELECT auth.uid());
  v_name text := trim(coalesce(p_name, ''));
  v_unit text := trim(coalesce(p_unit, ''));
  v_note text := left(trim(coalesce(p_note, 'Stock inicial')), 500);
  v_payload jsonb;
  v_existing_payload jsonb;
  v_item_id uuid;
BEGIN
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF p_client_request_id IS NULL THEN
    RAISE EXCEPTION 'O identificador da tentativa de criação de stock está em falta.';
  END IF;
  IF p_restaurant_id IS NULL OR NOT private.is_restaurant_staff(p_restaurant_id) THEN
    RAISE EXCEPTION 'Sem autorização para gerir o stock deste restaurante.';
  END IF;
  IF char_length(v_name) NOT BETWEEN 1 AND 100
     OR char_length(v_unit) NOT BETWEEN 1 AND 24 THEN
    RAISE EXCEPTION 'Indica um nome e uma unidade válidos.';
  END IF;
  IF p_quantity IS NULL OR p_low_stock_threshold IS NULL
     OR p_quantity < 0 OR p_low_stock_threshold < 0
     OR p_quantity > 999999999 OR p_low_stock_threshold > 999999999 THEN
    RAISE EXCEPTION 'A quantidade ou o limite de stock não é válido.';
  END IF;
  IF p_quantity <> round(p_quantity, 3)
     OR p_low_stock_threshold <> round(p_low_stock_threshold, 3) THEN
    RAISE EXCEPTION 'As quantidades de stock admitem no máximo três casas decimais.';
  END IF;
  IF length(coalesce(p_note, '')) > 500 THEN
    RAISE EXCEPTION 'A nota do movimento pode ter no máximo 500 caracteres.';
  END IF;
  IF p_unit_cost_per_unit IS NOT NULL
     AND (p_unit_cost_per_unit < 0
       OR p_unit_cost_per_unit > 99999999.9999
       OR p_unit_cost_per_unit <> round(p_unit_cost_per_unit, 4)) THEN
    RAISE EXCEPTION 'O custo unitário deve ser não negativo e ter no máximo quatro casas decimais.';
  END IF;

  v_payload := jsonb_build_object(
    'name', v_name,
    'unit', v_unit,
    'quantity', p_quantity,
    'low_stock_threshold', p_low_stock_threshold,
    'note', v_note,
    'unit_cost_per_unit', p_unit_cost_per_unit
  );

  SELECT i.id, i.creation_payload
    INTO v_item_id, v_existing_payload
  FROM public.restaurant_inventory_items AS i
  WHERE i.restaurant_id = p_restaurant_id
    AND i.created_by = v_user_id
    AND i.creation_request_id = p_client_request_id
  FOR UPDATE;

  IF FOUND THEN
    IF v_existing_payload = v_payload THEN
      RETURN v_item_id;
    END IF;
    RAISE EXCEPTION 'Esta tentativa já foi utilizada com dados de stock diferentes.';
  END IF;

  BEGIN
    INSERT INTO public.restaurant_inventory_items
      (restaurant_id, name, unit, quantity, low_stock_threshold,
       unit_cost_per_unit, created_by, creation_request_id, creation_payload)
    VALUES
      (p_restaurant_id, v_name, v_unit, p_quantity, p_low_stock_threshold,
       p_unit_cost_per_unit, v_user_id, p_client_request_id, v_payload)
    RETURNING id INTO v_item_id;
  EXCEPTION WHEN unique_violation THEN
    SELECT i.id, i.creation_payload
      INTO v_item_id, v_existing_payload
    FROM public.restaurant_inventory_items AS i
    WHERE i.restaurant_id = p_restaurant_id
      AND i.created_by = v_user_id
      AND i.creation_request_id = p_client_request_id
    FOR UPDATE;

    IF FOUND AND v_existing_payload = v_payload THEN
      RETURN v_item_id;
    END IF;
    RAISE EXCEPTION 'Esta tentativa já foi utilizada ou não pôde ser concluída.';
  END;

  IF p_quantity > 0 THEN
    INSERT INTO public.restaurant_inventory_movements
      (restaurant_id, item_id, delta, quantity_after, note, created_by)
    VALUES
      (p_restaurant_id, v_item_id, p_quantity, p_quantity, v_note, v_user_id);
  END IF;

  RETURN v_item_id;
END;
$function$;

REVOKE ALL ON FUNCTION public.save_inventory_item_with_cost(
  uuid, uuid, text, text, numeric, numeric, text, numeric
) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.save_inventory_item_with_cost(
  uuid, uuid, text, text, numeric, numeric, text, numeric
) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.create_inventory_item_idempotent_with_cost(
  uuid, uuid, text, text, numeric, numeric, text, numeric
) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.create_inventory_item_idempotent_with_cost(
  uuid, uuid, text, text, numeric, numeric, text, numeric
) TO authenticated, service_role;

COMMIT;
