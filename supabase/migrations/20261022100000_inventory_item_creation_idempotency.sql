-- Make new inventory item creation idempotent when the response is lost.
-- Additive only: existing rows and the older update RPC remain unchanged.
BEGIN;

ALTER TABLE public.restaurant_inventory_items
  ADD COLUMN IF NOT EXISTS creation_request_id uuid,
  ADD COLUMN IF NOT EXISTS creation_payload jsonb;

CREATE UNIQUE INDEX IF NOT EXISTS restaurant_inventory_items_creation_request_uidx
  ON public.restaurant_inventory_items (restaurant_id, created_by, creation_request_id)
  WHERE creation_request_id IS NOT NULL;

CREATE OR REPLACE FUNCTION public.create_inventory_item_idempotent(
  p_client_request_id uuid,
  p_restaurant_id uuid,
  p_name text,
  p_unit text,
  p_quantity numeric,
  p_low_stock_threshold numeric,
  p_note text DEFAULT NULL::text
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

  v_payload := jsonb_build_object(
    'name', v_name,
    'unit', v_unit,
    'quantity', p_quantity,
    'low_stock_threshold', p_low_stock_threshold,
    'note', v_note
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
      (restaurant_id, name, unit, quantity, low_stock_threshold, created_by,
       creation_request_id, creation_payload)
    VALUES
      (p_restaurant_id, v_name, v_unit, p_quantity, p_low_stock_threshold, v_user_id,
       p_client_request_id, v_payload)
    RETURNING id INTO v_item_id;
  EXCEPTION WHEN unique_violation THEN
    -- Two identical requests can reach the server concurrently. Re-read the row
    -- created by the winner and return it only when the original payload matches.
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

REVOKE ALL ON FUNCTION public.create_inventory_item_idempotent(
  uuid, uuid, text, text, numeric, numeric, text
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_inventory_item_idempotent(
  uuid, uuid, text, text, numeric, numeric, text
) FROM anon;
REVOKE ALL ON FUNCTION public.create_inventory_item_idempotent(
  uuid, uuid, text, text, numeric, numeric, text
) FROM authenticated;
GRANT EXECUTE ON FUNCTION public.create_inventory_item_idempotent(
  uuid, uuid, text, text, numeric, numeric, text
) TO authenticated, service_role;

COMMIT;
