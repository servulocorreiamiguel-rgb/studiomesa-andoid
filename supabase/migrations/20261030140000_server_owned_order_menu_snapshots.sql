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
  NEW.created_by := (SELECT auth.uid());

  IF NEW.menu_item_id IS NOT NULL THEN
    SELECT mi.name, mi.price, mi.cost, mi.is_active
      INTO v_name, v_price, v_cost, v_is_active
    FROM public.menu_items AS mi
    WHERE mi.id = NEW.menu_item_id
      AND mi.restaurant_id = NEW.restaurant_id;

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

COMMIT;
