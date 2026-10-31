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
    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.restaurant_id IS DISTINCT FROM OLD.restaurant_id
       OR NEW.created_at IS DISTINCT FROM OLD.created_at
       OR (
         NEW.created_by IS DISTINCT FROM OLD.created_by
         AND NOT (NEW.created_by IS NULL AND pg_trigger_depth() > 1)
       ) THEN
      RAISE EXCEPTION 'A identidade, o restaurante, o autor e a data original do pedido são imutáveis.';
    END IF;

    IF NEW.item_name IS DISTINCT FROM OLD.item_name
       OR NEW.amount IS DISTINCT FROM OLD.amount
       OR NEW.unit_cost_snapshot IS DISTINCT FROM OLD.unit_cost_snapshot THEN
      RAISE EXCEPTION 'O nome, preço e custo fotografados de um pedido são imutáveis.';
    END IF;

    -- A menu FK may clear this value only as a nested referential action.
    -- A direct client UPDATE to NULL would otherwise bypass recipe consumption.
    IF NEW.menu_item_id IS DISTINCT FROM OLD.menu_item_id
       AND NOT (NEW.menu_item_id IS NULL AND pg_trigger_depth() > 1) THEN
      RAISE EXCEPTION 'A associação do pedido à carta só pode ser removida quando o artigo é eliminado.';
    END IF;

    IF NEW.quantity IS DISTINCT FROM OLD.quantity
       AND EXISTS (
         SELECT 1 FROM public.restaurant_inventory_movements AS m
         WHERE m.source_order_id = OLD.id
       ) THEN
      RAISE EXCEPTION 'A quantidade de um pedido não pode mudar depois de o stock ter sido consumido.';
    END IF;

    -- Enforce the same kitchen workflow on the server as in the Android UI.
    -- Reopening a closed ticket is allowed, but a new ticket always starts open.
    IF NEW.status IS DISTINCT FROM OLD.status
       AND NOT (
         (OLD.status = 'Aberto' AND NEW.status IN ('Em preparação', 'Cancelado'))
         OR (OLD.status = 'Em preparação' AND NEW.status IN ('Pronto', 'Cancelado'))
         OR (OLD.status = 'Pronto' AND NEW.status IN ('Concluído', 'Aberto', 'Cancelado'))
         OR (OLD.status IN ('Concluído', 'Cancelado') AND NEW.status = 'Aberto')
       ) THEN
      RAISE EXCEPTION 'Transição de estado inválida. Segue a sequência Aberto → Em preparação → Pronto → Concluído; um pedido pode ser cancelado ou reaberto.';
    END IF;

    RETURN NEW;
  END IF;

  IF NEW.status IS DISTINCT FROM 'Aberto' THEN
    RAISE EXCEPTION 'Um pedido novo tem de começar no estado Aberto.';
  END IF;

  IF NEW.menu_item_id IS NULL THEN
    RAISE EXCEPTION 'Um pedido novo tem de estar ligado a um artigo ativo da carta.';
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

-- Replace the earlier INSERT-only creator trigger. The combined trigger
-- below now owns the full INSERT and UPDATE contract exactly once.
DROP TRIGGER IF EXISTS restaurant_order_set_creator ON public.restaurant_orders;
DROP TRIGGER IF EXISTS restaurant_order_snapshot_immutable ON public.restaurant_orders;
CREATE TRIGGER restaurant_order_snapshot_immutable
  BEFORE INSERT OR UPDATE ON public.restaurant_orders
  FOR EACH ROW EXECUTE FUNCTION private.prepare_restaurant_order();

COMMIT;
