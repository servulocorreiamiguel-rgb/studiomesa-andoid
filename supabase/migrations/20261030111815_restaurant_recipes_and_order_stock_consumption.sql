-- Restaurant recipes and atomic stock consumption when an order enters preparation.
-- Existing orders remain valid: menu_item_id is nullable and old orders are not backfilled.
BEGIN;

CREATE UNIQUE INDEX IF NOT EXISTS menu_items_restaurant_id_id_uidx
  ON public.menu_items (restaurant_id, id);

CREATE UNIQUE INDEX IF NOT EXISTS restaurant_inventory_items_restaurant_id_id_uidx
  ON public.restaurant_inventory_items (restaurant_id, id);

ALTER TABLE public.restaurant_orders
  ADD COLUMN IF NOT EXISTS menu_item_id uuid;

DO $migration$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'restaurant_orders_menu_item_same_restaurant_fk'
      AND conrelid = 'public.restaurant_orders'::regclass
  ) THEN
    ALTER TABLE public.restaurant_orders
      ADD CONSTRAINT restaurant_orders_menu_item_same_restaurant_fk
      FOREIGN KEY (restaurant_id, menu_item_id)
      REFERENCES public.menu_items (restaurant_id, id)
      ON DELETE SET NULL (menu_item_id);
  END IF;
END;
$migration$;

CREATE TABLE IF NOT EXISTS public.restaurant_recipes (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  restaurant_id uuid NOT NULL,
  menu_item_id uuid NOT NULL,
  is_active boolean NOT NULL DEFAULT false,
  requires_review boolean NOT NULL DEFAULT false,
  created_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
  updated_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT restaurant_recipes_restaurant_menu_key UNIQUE (restaurant_id, menu_item_id),
  CONSTRAINT restaurant_recipes_restaurant_id_key UNIQUE (restaurant_id, id),
  CONSTRAINT restaurant_recipes_menu_same_restaurant_fk
    FOREIGN KEY (restaurant_id, menu_item_id)
    REFERENCES public.menu_items (restaurant_id, id)
    ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS public.restaurant_recipe_ingredients (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  restaurant_id uuid NOT NULL,
  recipe_id uuid NOT NULL,
  inventory_item_id uuid NOT NULL,
  quantity_per_portion numeric(12,3) NOT NULL
    CHECK (quantity_per_portion > 0 AND quantity_per_portion <= 999999999),
  waste_percent numeric(5,2) NOT NULL DEFAULT 0
    CHECK (waste_percent >= 0 AND waste_percent < 100),
  inventory_unit_snapshot text NOT NULL
    CHECK (char_length(trim(inventory_unit_snapshot)) BETWEEN 1 AND 24),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT restaurant_recipe_ingredients_recipe_item_key UNIQUE (recipe_id, inventory_item_id),
  CONSTRAINT restaurant_recipe_ingredients_recipe_same_restaurant_fk
    FOREIGN KEY (restaurant_id, recipe_id)
    REFERENCES public.restaurant_recipes (restaurant_id, id)
    ON DELETE CASCADE,
  CONSTRAINT restaurant_recipe_ingredients_stock_same_restaurant_fk
    FOREIGN KEY (restaurant_id, inventory_item_id)
    REFERENCES public.restaurant_inventory_items (restaurant_id, id)
    ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS restaurant_recipes_restaurant_active_idx
  ON public.restaurant_recipes (restaurant_id, is_active, menu_item_id);
CREATE INDEX IF NOT EXISTS restaurant_recipe_ingredients_recipe_idx
  ON public.restaurant_recipe_ingredients (recipe_id, inventory_item_id);
CREATE INDEX IF NOT EXISTS restaurant_orders_restaurant_menu_item_idx
  ON public.restaurant_orders (restaurant_id, menu_item_id)
  WHERE menu_item_id IS NOT NULL;

ALTER TABLE public.restaurant_inventory_movements
  ADD COLUMN IF NOT EXISTS source_order_id uuid
  REFERENCES public.restaurant_orders(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX IF NOT EXISTS restaurant_inventory_movements_order_item_uidx
  ON public.restaurant_inventory_movements (source_order_id, item_id)
  WHERE source_order_id IS NOT NULL;

ALTER TABLE public.restaurant_recipes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.restaurant_recipe_ingredients ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS restaurant_recipes_staff_read ON public.restaurant_recipes;
CREATE POLICY restaurant_recipes_staff_read
  ON public.restaurant_recipes
  FOR SELECT TO authenticated
  USING (private.is_restaurant_staff(restaurant_id));

DROP POLICY IF EXISTS restaurant_recipe_ingredients_staff_read ON public.restaurant_recipe_ingredients;
CREATE POLICY restaurant_recipe_ingredients_staff_read
  ON public.restaurant_recipe_ingredients
  FOR SELECT TO authenticated
  USING (private.is_restaurant_staff(restaurant_id));

REVOKE ALL ON TABLE public.restaurant_recipes FROM PUBLIC, anon, authenticated;
REVOKE ALL ON TABLE public.restaurant_recipe_ingredients FROM PUBLIC, anon, authenticated;
GRANT SELECT ON TABLE public.restaurant_recipes TO authenticated;
GRANT SELECT ON TABLE public.restaurant_recipe_ingredients TO authenticated;

CREATE OR REPLACE FUNCTION public.save_restaurant_recipe_ingredient(
  p_restaurant_id uuid,
  p_menu_item_id uuid,
  p_inventory_item_id uuid,
  p_quantity_per_portion numeric,
  p_waste_percent numeric DEFAULT 0
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_user_id uuid := (SELECT auth.uid());
  v_recipe_id uuid;
  v_inventory_unit text;
BEGIN
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF p_restaurant_id IS NULL OR NOT private.is_restaurant_staff(p_restaurant_id) THEN
    RAISE EXCEPTION 'Sem autorização para gerir receitas deste restaurante.';
  END IF;
  IF p_menu_item_id IS NULL OR NOT EXISTS (
    SELECT 1 FROM public.menu_items AS mi
    WHERE mi.id = p_menu_item_id
      AND mi.restaurant_id = p_restaurant_id
      AND mi.is_active = true
  ) THEN
    RAISE EXCEPTION 'O artigo da carta não existe ou está arquivado.';
  END IF;
  SELECT ii.unit INTO v_inventory_unit
  FROM public.restaurant_inventory_items AS ii
  WHERE ii.id = p_inventory_item_id
    AND ii.restaurant_id = p_restaurant_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'O ingrediente não pertence ao inventário deste restaurante.';
  END IF;
  IF p_quantity_per_portion IS NULL
     OR p_quantity_per_portion <= 0
     OR p_quantity_per_portion > 999999999
     OR p_quantity_per_portion <> round(p_quantity_per_portion, 3) THEN
    RAISE EXCEPTION 'A quantidade por dose tem de ser positiva e admitir no máximo três casas decimais.';
  END IF;
  IF p_waste_percent IS NULL
     OR p_waste_percent < 0
     OR p_waste_percent >= 100
     OR p_waste_percent <> round(p_waste_percent, 2) THEN
    RAISE EXCEPTION 'O desperdício deve estar entre 0 e menos de 100%%, com no máximo duas casas decimais.';
  END IF;

  INSERT INTO public.restaurant_recipes AS current_recipe
    (restaurant_id, menu_item_id, created_by, updated_by)
  VALUES
    (p_restaurant_id, p_menu_item_id, v_user_id, v_user_id)
  ON CONFLICT (restaurant_id, menu_item_id)
  DO UPDATE SET
    -- Ingredient edits invalidate prior owner approval until the recipe is reviewed again.
    is_active = false,
    requires_review = current_recipe.is_active OR current_recipe.requires_review,
    updated_by = v_user_id,
    updated_at = now()
  RETURNING id INTO v_recipe_id;

  INSERT INTO public.restaurant_recipe_ingredients
    (restaurant_id, recipe_id, inventory_item_id, quantity_per_portion, waste_percent, inventory_unit_snapshot)
  VALUES
    (p_restaurant_id, v_recipe_id, p_inventory_item_id, p_quantity_per_portion, p_waste_percent, v_inventory_unit)
  ON CONFLICT (recipe_id, inventory_item_id)
  DO UPDATE SET
    quantity_per_portion = EXCLUDED.quantity_per_portion,
    waste_percent = EXCLUDED.waste_percent,
    inventory_unit_snapshot = EXCLUDED.inventory_unit_snapshot,
    updated_at = now();

  RETURN v_recipe_id;
END;
$function$;

CREATE OR REPLACE FUNCTION public.remove_restaurant_recipe_ingredient(
  p_recipe_id uuid,
  p_inventory_item_id uuid
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_restaurant_id uuid;
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF p_recipe_id IS NULL OR p_inventory_item_id IS NULL THEN
    RAISE EXCEPTION 'Seleciona uma receita e um ingrediente válidos.';
  END IF;

  SELECT r.restaurant_id INTO v_restaurant_id
  FROM public.restaurant_recipes AS r
  WHERE r.id = p_recipe_id
  FOR UPDATE;

  IF v_restaurant_id IS NULL OR NOT private.is_restaurant_staff(v_restaurant_id) THEN
    RAISE EXCEPTION 'Receita não encontrada ou sem autorização.';
  END IF;

  DELETE FROM public.restaurant_recipe_ingredients AS ri
  WHERE ri.recipe_id = p_recipe_id
    AND ri.restaurant_id = v_restaurant_id
    AND ri.inventory_item_id = p_inventory_item_id;

  -- Any ingredient removal invalidates the owner's prior approval.
  UPDATE public.restaurant_recipes
  SET is_active = false,
      requires_review = is_active OR requires_review,
      updated_by = (SELECT auth.uid()),
      updated_at = now()
  WHERE id = p_recipe_id;

  RETURN true;
END;
$function$;

CREATE OR REPLACE FUNCTION public.set_restaurant_recipe_active(
  p_restaurant_id uuid,
  p_menu_item_id uuid,
  p_is_active boolean
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_recipe_id uuid;
  v_role text;
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF p_restaurant_id IS NULL OR p_menu_item_id IS NULL OR p_is_active IS NULL THEN
    RAISE EXCEPTION 'Seleciona um restaurante, artigo e estado de receita válidos.';
  END IF;

  SELECT rs.role INTO v_role
  FROM public.restaurant_staff AS rs
  WHERE rs.restaurant_id = p_restaurant_id
    AND rs.user_id = (SELECT auth.uid());

  IF v_role IS DISTINCT FROM 'owner' THEN
    RAISE EXCEPTION 'Só o proprietário pode ativar ou desativar o consumo automático de ingredientes.';
  END IF;

  SELECT r.id INTO v_recipe_id
  FROM public.restaurant_recipes AS r
  WHERE r.restaurant_id = p_restaurant_id
    AND r.menu_item_id = p_menu_item_id
  FOR UPDATE;

  IF v_recipe_id IS NULL THEN
    RAISE EXCEPTION 'Configura primeiro pelo menos um ingrediente da receita.';
  END IF;

  IF p_is_active AND NOT EXISTS (
    SELECT 1 FROM public.restaurant_recipe_ingredients AS ri
    WHERE ri.recipe_id = v_recipe_id
  ) THEN
    RAISE EXCEPTION 'Adiciona pelo menos um ingrediente antes de ativar a receita.';
  END IF;

  IF p_is_active AND EXISTS (
    SELECT 1
    FROM public.restaurant_recipe_ingredients AS ri
    JOIN public.restaurant_inventory_items AS ii
      ON ii.id = ri.inventory_item_id
     AND ii.restaurant_id = ri.restaurant_id
    WHERE ri.recipe_id = v_recipe_id
      AND ri.inventory_unit_snapshot IS DISTINCT FROM ii.unit
  ) THEN
    RAISE EXCEPTION 'A unidade de um ingrediente foi alterada. Atualiza a linha da receita e revê as quantidades antes de a ativar.';
  END IF;

  UPDATE public.restaurant_recipes
  SET is_active = p_is_active,
      requires_review = false,
      updated_by = (SELECT auth.uid()),
      updated_at = now()
  WHERE id = v_recipe_id;

  RETURN p_is_active;
END;
$function$;

CREATE OR REPLACE FUNCTION private.consume_recipe_stock_for_order()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_user_id uuid := (SELECT auth.uid());
  v_recipe_id uuid;
  v_recipe_active boolean;
  v_recipe_requires_review boolean;
  v_line record;
  v_required_raw numeric;
  v_required numeric;
  v_new_quantity numeric(12,3);
BEGIN
  IF OLD.status <> 'Aberto'
     OR NEW.status <> 'Em preparação'
     OR NEW.menu_item_id IS NULL THEN
    RETURN NEW;
  END IF;

  IF v_user_id IS NULL OR NOT private.is_restaurant_staff(NEW.restaurant_id) THEN
    RAISE EXCEPTION 'Sem autorização para consumir stock neste pedido.';
  END IF;

  -- Read the recipe identity first without taking its row lock. Stock rows are
  -- locked before the recipe row so this trigger uses the same lock order as
  -- inventory-unit changes and cannot deadlock by reversing those locks.
  SELECT r.id, r.is_active, r.requires_review
    INTO v_recipe_id, v_recipe_active, v_recipe_requires_review
  FROM public.restaurant_recipes AS r
  WHERE r.restaurant_id = NEW.restaurant_id
    AND r.menu_item_id = NEW.menu_item_id;

  IF v_recipe_id IS NULL THEN
    RETURN NEW;
  END IF;
  IF v_recipe_requires_review THEN
    RAISE EXCEPTION 'A receita deste prato foi alterada e precisa de revisão do proprietário antes de iniciar a preparação.';
  END IF;
  IF NOT v_recipe_active THEN
    RETURN NEW;
  END IF;

  PERFORM 1
  FROM public.restaurant_recipe_ingredients AS ri
  JOIN public.restaurant_inventory_items AS ii
    ON ii.id = ri.inventory_item_id
   AND ii.restaurant_id = ri.restaurant_id
  WHERE ri.restaurant_id = NEW.restaurant_id
    AND ri.recipe_id = v_recipe_id
  ORDER BY ii.id
  FOR UPDATE OF ii;

  SELECT r.is_active, r.requires_review
    INTO v_recipe_active, v_recipe_requires_review
  FROM public.restaurant_recipes AS r
  WHERE r.id = v_recipe_id
    AND r.restaurant_id = NEW.restaurant_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RETURN NEW;
  END IF;
  IF v_recipe_requires_review THEN
    RAISE EXCEPTION 'A receita deste prato foi alterada e precisa de revisão do proprietário antes de iniciar a preparação.';
  END IF;
  IF NOT v_recipe_active THEN
    RETURN NEW;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM public.restaurant_recipe_ingredients AS ri
    JOIN public.restaurant_inventory_items AS ii
      ON ii.id = ri.inventory_item_id
     AND ii.restaurant_id = ri.restaurant_id
    WHERE ri.restaurant_id = NEW.restaurant_id
      AND ri.recipe_id = v_recipe_id
      AND ri.inventory_unit_snapshot IS DISTINCT FROM ii.unit
  ) THEN
    RAISE EXCEPTION 'A unidade de um ingrediente mudou. A receita tem de ser revista pelo proprietário antes de preparar o pedido.';
  END IF;

  -- A reopened ticket must never consume the same ingredients twice.
  IF EXISTS (
    SELECT 1 FROM public.restaurant_inventory_movements AS m
    WHERE m.source_order_id = NEW.id
  ) THEN
    RETURN NEW;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.restaurant_recipe_ingredients AS ri
    WHERE ri.recipe_id = v_recipe_id
  ) THEN
    RAISE EXCEPTION 'A receita ativa não tem ingredientes. Desativa-a ou completa-a antes de preparar o pedido.';
  END IF;

  -- Lock all affected stock rows in stable ID order. The order update and every
  -- stock movement share one transaction: any insufficient ingredient rolls back
  -- the complete order transition and all earlier deductions in this loop.
  FOR v_line IN
    SELECT
      ri.inventory_item_id,
      ri.quantity_per_portion,
      ri.waste_percent,
      ii.name AS item_name,
      ii.unit,
      ii.quantity AS current_quantity
    FROM public.restaurant_recipe_ingredients AS ri
    JOIN public.restaurant_inventory_items AS ii
      ON ii.id = ri.inventory_item_id
     AND ii.restaurant_id = ri.restaurant_id
    WHERE ri.restaurant_id = NEW.restaurant_id
      AND ri.recipe_id = v_recipe_id
    ORDER BY ii.id
    FOR UPDATE OF ii
  LOOP
    v_required_raw :=
      v_line.quantity_per_portion * NEW.quantity * 100 /
      (100 - v_line.waste_percent);
    v_required := ceil(v_required_raw * 1000) / 1000;

    IF v_required <= 0 OR v_required > 999999999 THEN
      RAISE EXCEPTION 'A quantidade calculada para o ingrediente "%" excede o limite suportado.',
        v_line.item_name;
    END IF;
    IF v_line.current_quantity < v_required THEN
      RAISE EXCEPTION 'Stock insuficiente de "%": necessário % %, disponível % %.',
        v_line.item_name,
        trim_scale(v_required),
        v_line.unit,
        trim_scale(v_line.current_quantity),
        v_line.unit;
    END IF;

    v_new_quantity := v_line.current_quantity - v_required;

    UPDATE public.restaurant_inventory_items
    SET quantity = v_new_quantity,
        updated_at = now()
    WHERE id = v_line.inventory_item_id
      AND restaurant_id = NEW.restaurant_id;

    INSERT INTO public.restaurant_inventory_movements
      (restaurant_id, item_id, delta, quantity_after, note, created_by, source_order_id)
    VALUES
      (
        NEW.restaurant_id,
        v_line.inventory_item_id,
        -v_required,
        v_new_quantity,
        'Consumo automático · pedido ' || NEW.id::text,
        v_user_id,
        NEW.id
      );
  END LOOP;

  RETURN NEW;
END;
$function$;

DROP TRIGGER IF EXISTS restaurant_order_consume_recipe_stock ON public.restaurant_orders;
CREATE TRIGGER restaurant_order_consume_recipe_stock
  AFTER UPDATE OF status ON public.restaurant_orders
  FOR EACH ROW
  WHEN (OLD.status IS DISTINCT FROM NEW.status)
  EXECUTE FUNCTION private.consume_recipe_stock_for_order();

REVOKE ALL ON FUNCTION public.save_restaurant_recipe_ingredient(uuid, uuid, uuid, numeric, numeric)
  FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.save_restaurant_recipe_ingredient(uuid, uuid, uuid, numeric, numeric)
  TO authenticated;

REVOKE ALL ON FUNCTION public.remove_restaurant_recipe_ingredient(uuid, uuid)
  FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.remove_restaurant_recipe_ingredient(uuid, uuid)
  TO authenticated;

REVOKE ALL ON FUNCTION public.set_restaurant_recipe_active(uuid, uuid, boolean)
  FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.set_restaurant_recipe_active(uuid, uuid, boolean)
  TO authenticated;

REVOKE ALL ON FUNCTION private.consume_recipe_stock_for_order()
  FROM PUBLIC, anon, authenticated;

-- Any unit change or deletion of a stock item invalidates every recipe that
-- references it. Unit snapshots below prevent reactivation until the recipe
-- line is explicitly saved again with the current unit.
CREATE OR REPLACE FUNCTION private.invalidate_restaurant_recipe_approval()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN
  IF TG_OP = 'UPDATE' AND OLD.unit IS NOT DISTINCT FROM NEW.unit THEN
    RETURN NEW;
  END IF;

  UPDATE public.restaurant_recipes AS r
  SET is_active = false,
      requires_review = r.is_active OR r.requires_review,
      updated_by = COALESCE((SELECT auth.uid()), r.updated_by),
      updated_at = now()
  WHERE r.restaurant_id = OLD.restaurant_id
    -- During deletion of a restaurant or menu item, the corresponding
    -- recipe is already being removed by its FK cascade. Do not update it
    -- after its referenced menu row has disappeared.
    AND EXISTS (
      SELECT 1
      FROM public.menu_items AS mi
      WHERE mi.id = r.menu_item_id
        AND mi.restaurant_id = r.restaurant_id
    )
    AND EXISTS (
      SELECT 1
      FROM public.restaurant_recipe_ingredients AS ri
      WHERE ri.restaurant_id = OLD.restaurant_id
        AND ri.inventory_item_id = OLD.id
        AND ri.recipe_id = r.id
    );

  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  RETURN NEW;
END;
$function$;

DROP TRIGGER IF EXISTS restaurant_recipe_unit_change_requires_reapproval
  ON public.restaurant_inventory_items;
CREATE TRIGGER restaurant_recipe_unit_change_requires_reapproval
  BEFORE UPDATE OF unit OR DELETE ON public.restaurant_inventory_items
  FOR EACH ROW
  EXECUTE FUNCTION private.invalidate_restaurant_recipe_approval();

REVOKE ALL ON FUNCTION private.invalidate_restaurant_recipe_approval()
  FROM PUBLIC, anon, authenticated;

COMMIT;
