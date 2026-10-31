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

    IF NEW.table_number IS DISTINCT FROM OLD.table_number
       OR NEW.quantity IS DISTINCT FROM OLD.quantity
       OR NEW.service_course IS DISTINCT FROM OLD.service_course
       OR NEW.notes IS DISTINCT FROM OLD.notes THEN
      RAISE EXCEPTION 'A mesa, quantidade, fase de serviço e notas originais do pedido são imutáveis.';
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

-- Menu identity is part of every order's durable history. Even cancelled
-- or completed tickets can be reopened, so any linked historical order means
-- the menu item must be archived rather than physically deleted.
CREATE OR REPLACE FUNCTION private.prevent_menu_item_delete_with_orders()
RETURNS trigger
LANGUAGE plpgsql
SET search_path TO ''
AS $function$
BEGIN
  -- Account deletion cascades from the restaurant row. Let PostgreSQL remove
  -- its menu rows in that parent-deletion transaction; this guard is for
  -- isolated menu-item deletion while the restaurant itself remains present.
  IF pg_trigger_depth() > 1
     AND NOT EXISTS (
       SELECT 1 FROM public.restaurants AS r
       WHERE r.id = OLD.restaurant_id
     ) THEN
    RETURN OLD;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM public.restaurant_orders AS o
    WHERE o.restaurant_id = OLD.restaurant_id
      AND o.menu_item_id = OLD.id
  ) THEN
    RAISE EXCEPTION 'Não é possível eliminar um artigo que tenha pedidos associados. Arquiva-o para preservar o histórico.';
  END IF;

  RETURN OLD;
END;
$function$;

REVOKE ALL ON FUNCTION private.prevent_menu_item_delete_with_orders()
  FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS menu_item_delete_requires_no_orders ON public.menu_items;
CREATE TRIGGER menu_item_delete_requires_no_orders
  BEFORE DELETE ON public.menu_items
  FOR EACH ROW EXECUTE FUNCTION private.prevent_menu_item_delete_with_orders();

COMMIT;
