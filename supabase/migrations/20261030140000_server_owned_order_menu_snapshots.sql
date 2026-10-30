BEGIN;

-- A client may choose a menu item, but it must never be trusted to supply
-- the historical name, selling price or internal cost stored on the order.
-- Keep this trigger SECURITY INVOKER so menu_items RLS remains effective.
CREATE OR REPLACE FUNCTION private.prepare_restaurant_order()
RETURNS trigger
LANGUAGE plpgsql
SET search_path TO ''
AS $function$
DECLARE
  v_name text;
  v_price numeric(10,2);
  v_cost numeric(10,2);
  v_is_active boolean;
BEGIN
  IF TG_OP = 'UPDATE' THEN
    IF NEW.item_name IS DISTINCT FROM OLD.item_name
       OR NEW.amount IS DISTINCT FROM OLD.amount
       OR NEW.unit_cost_snapshot IS DISTINCT FROM OLD.unit_cost_snapshot THEN
      RAISE EXCEPTION 'O nome, preço e custo fotografados de um pedido são imutáveis.';
    END IF;

    -- A menu item can only be unlinked (e.g. when the menu row is deleted).
    -- Reassigning an existing order to another dish would rewrite its history.
    IF NEW.menu_item_id IS DISTINCT FROM OLD.menu_item_id
       AND NEW.menu_item_id IS NOT NULL THEN
      RAISE EXCEPTION 'Não é possível voltar a associar um pedido existente a outro artigo da carta.';
    END IF;

    RETURN NEW;
  END IF;

  NEW.created_by := (SELECT auth.uid());

  IF NEW.menu_item_id IS NOT NULL THEN
    SELECT mi.name, mi.price, mi.cost, mi.is_active
      INTO v_name, v_price, v_cost, v_is_active
    FROM public.menu_items AS mi
    WHERE mi.id = NEW.menu_item_id
      AND mi.restaurant_id = NEW.restaurant_id
    FOR SHARE;

    IF NOT FOUND THEN
      RAISE EXCEPTION 'O artigo selecionado não existe neste restaurante.';
    END IF;

    IF NOT v_is_active THEN
      RAISE EXCEPTION 'O artigo selecionado está arquivado e não pode receber novos pedidos.';
    END IF;

    -- Server-authoritative immutable snapshots; cost zero means unknown,
    -- never a known zero-cost ingredient/meal.
    NEW.item_name := v_name;
    NEW.amount := v_price;
    NEW.unit_cost_snapshot := NULLIF(v_cost, 0);
  END IF;

  RETURN NEW;
END;
$function$;

DROP TRIGGER IF EXISTS restaurant_order_snapshot_immutable ON public.restaurant_orders;
CREATE TRIGGER restaurant_order_snapshot_immutable
  BEFORE UPDATE ON public.restaurant_orders
  FOR EACH ROW EXECUTE FUNCTION private.prepare_restaurant_order();

COMMIT;
