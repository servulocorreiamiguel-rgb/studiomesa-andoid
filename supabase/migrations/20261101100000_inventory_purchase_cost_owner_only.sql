-- Restrict purchase-cost disclosure to restaurant owners.
-- Staff retain operational inventory reads but cannot select purchase-cost columns.
-- Owner-only RPCs expose current and historical costs after checking restaurant_staff.role.
BEGIN;

CREATE OR REPLACE FUNCTION private.is_restaurant_owner(p_restaurant_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
  SELECT EXISTS (
    SELECT 1
    FROM public.restaurant_staff AS rs
    WHERE rs.restaurant_id = p_restaurant_id
      AND rs.user_id = (SELECT auth.uid())
      AND rs.role = 'owner'
  );
$function$;

REVOKE ALL ON FUNCTION private.is_restaurant_owner(uuid)
  FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION private.guard_inventory_cost_owner_only()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    IF current_setting('request.jwt.claim.role', true) = 'service_role' THEN
      RETURN NEW;
    END IF;
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;

  IF TG_OP = 'INSERT' THEN
    IF NEW.unit_cost_per_unit IS NOT NULL
       AND NOT private.is_restaurant_owner(NEW.restaurant_id) THEN
      RAISE EXCEPTION 'Só o proprietário pode definir o custo de compra do stock.';
    END IF;
    RETURN NEW;
  END IF;

  IF NOT private.is_restaurant_owner(NEW.restaurant_id) THEN
    IF NEW.unit IS DISTINCT FROM OLD.unit THEN
      RAISE EXCEPTION 'Só o proprietário pode alterar a unidade base de um artigo de stock existente.';
    END IF;
    -- Staff may edit operational quantities and names, but cannot clear,
    -- replace, or infer the owner's purchase cost.
    NEW.unit_cost_per_unit := OLD.unit_cost_per_unit;
  END IF;

  RETURN NEW;
END;
$function$;

REVOKE ALL ON FUNCTION private.guard_inventory_cost_owner_only()
  FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS restaurant_inventory_cost_owner_guard
  ON public.restaurant_inventory_items;

CREATE TRIGGER restaurant_inventory_cost_owner_guard
  BEFORE INSERT OR UPDATE ON public.restaurant_inventory_items
  FOR EACH ROW
  EXECUTE FUNCTION private.guard_inventory_cost_owner_only();

-- Remove table-wide SELECT so column-level grants can hide financial fields.
REVOKE SELECT ON TABLE public.restaurant_inventory_items FROM authenticated;
GRANT SELECT (
  id,
  restaurant_id,
  name,
  unit,
  quantity,
  low_stock_threshold,
  updated_at
) ON TABLE public.restaurant_inventory_items TO authenticated;

REVOKE SELECT ON TABLE public.restaurant_inventory_movements FROM authenticated;
GRANT SELECT (
  restaurant_id,
  item_id,
  delta,
  quantity_after,
  note,
  created_at,
  unit_at_movement
) ON TABLE public.restaurant_inventory_movements TO authenticated;

CREATE OR REPLACE FUNCTION public.list_inventory_items_with_costs(
  p_restaurant_id uuid
)
RETURNS TABLE (
  id uuid,
  name text,
  unit text,
  quantity numeric(12,3),
  low_stock_threshold numeric(12,3),
  unit_cost_per_unit numeric(12,4),
  updated_at timestamptz
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF p_restaurant_id IS NULL OR NOT private.is_restaurant_owner(p_restaurant_id) THEN
    RAISE EXCEPTION 'Só o proprietário pode consultar custos de compra do inventário.';
  END IF;

  RETURN QUERY
  SELECT
    i.id,
    i.name,
    i.unit,
    i.quantity,
    i.low_stock_threshold,
    i.unit_cost_per_unit,
    i.updated_at
  FROM public.restaurant_inventory_items AS i
  WHERE i.restaurant_id = p_restaurant_id
  ORDER BY i.name ASC, i.id ASC;
END;
$function$;

REVOKE ALL ON FUNCTION public.list_inventory_items_with_costs(uuid)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.list_inventory_items_with_costs(uuid)
  TO authenticated;

CREATE OR REPLACE FUNCTION public.list_inventory_waste_movements_with_costs(
  p_restaurant_id uuid,
  p_limit integer DEFAULT 100
)
RETURNS TABLE (
  item_id uuid,
  delta numeric(12,3),
  note text,
  created_at timestamptz,
  unit_at_movement text,
  unit_cost_at_movement numeric(12,4)
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF p_restaurant_id IS NULL OR NOT private.is_restaurant_owner(p_restaurant_id) THEN
    RAISE EXCEPTION 'Só o proprietário pode consultar custos históricos de desperdício.';
  END IF;
  IF p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 200 THEN
    RAISE EXCEPTION 'O limite do histórico deve estar entre 1 e 200.';
  END IF;

  RETURN QUERY
  SELECT
    m.item_id,
    m.delta,
    m.note,
    m.created_at,
    m.unit_at_movement,
    m.unit_cost_at_movement
  FROM public.restaurant_inventory_movements AS m
  WHERE m.restaurant_id = p_restaurant_id
    AND m.delta < 0
    AND left(m.note, char_length('Desperdício · ')) = 'Desperdício · '
  ORDER BY m.created_at DESC
  LIMIT p_limit;
END;
$function$;

REVOKE ALL ON FUNCTION public.list_inventory_waste_movements_with_costs(uuid, integer)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.list_inventory_waste_movements_with_costs(uuid, integer)
  TO authenticated;


CREATE OR REPLACE FUNCTION public.get_inventory_stock_request_result(
  p_restaurant_id uuid,
  p_item_id uuid,
  p_client_request_id uuid,
  p_delta numeric,
  p_note text DEFAULT NULL::text
)
RETURNS TABLE(item_id uuid, quantity numeric)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_user_id uuid := (SELECT auth.uid());
  v_note text := left(trim(coalesce(p_note, 'Ajuste de stock')), 500);
  v_current_quantity numeric(12,3);
BEGIN
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF p_restaurant_id IS NULL OR NOT private.is_restaurant_staff(p_restaurant_id) THEN
    RAISE EXCEPTION 'Sem autorização para consultar movimentos deste restaurante.';
  END IF;
  IF p_item_id IS NULL OR p_client_request_id IS NULL
     OR p_delta IS NULL OR p_delta = 0 THEN
    RAISE EXCEPTION 'A referência da operação de stock não é válida.';
  END IF;

  SELECT i.quantity
    INTO v_current_quantity
  FROM public.restaurant_inventory_items AS i
  WHERE i.id = p_item_id
    AND i.restaurant_id = p_restaurant_id;

  IF NOT FOUND THEN
    RETURN;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM public.restaurant_inventory_movements AS m
    WHERE m.restaurant_id = p_restaurant_id
      AND m.item_id = p_item_id
      AND m.created_by = v_user_id
      AND m.client_request_id = p_client_request_id
      AND m.delta = p_delta
      AND m.note = v_note
  ) THEN
    RETURN QUERY SELECT p_item_id, v_current_quantity::numeric;
  END IF;
END;
$function$;

REVOKE ALL ON FUNCTION public.get_inventory_stock_request_result(
  uuid, uuid, uuid, numeric, text
) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_inventory_stock_request_result(
  uuid, uuid, uuid, numeric, text
) TO authenticated;

COMMIT;
