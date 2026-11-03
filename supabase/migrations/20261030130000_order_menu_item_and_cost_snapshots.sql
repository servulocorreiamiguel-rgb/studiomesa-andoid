BEGIN;

-- Orders are line items, so bind new orders to a stable menu UUID while
-- retaining item_name and amount as immutable display/price snapshots.
-- This migration is intentionally compatible with the recipe migration:
-- both may add the same restaurant-scoped menu reference, but only once.
DO $constraints$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'menu_items_id_restaurant_pair_key'
      AND conrelid = 'public.menu_items'::regclass
      AND contype = 'u'
  ) THEN
    ALTER TABLE public.menu_items
      ADD CONSTRAINT menu_items_id_restaurant_pair_key
      UNIQUE (id, restaurant_id);
  END IF;
END;
$constraints$;

ALTER TABLE public.restaurant_orders
  ADD COLUMN IF NOT EXISTS menu_item_id uuid,
  ADD COLUMN IF NOT EXISTS unit_cost_snapshot numeric(10,2);

DO $constraints$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'restaurant_orders_unit_cost_snapshot_check'
      AND conrelid = 'public.restaurant_orders'::regclass
      AND contype = 'c'
  ) THEN
    ALTER TABLE public.restaurant_orders
      ADD CONSTRAINT restaurant_orders_unit_cost_snapshot_check
      CHECK (
        unit_cost_snapshot IS NULL
        OR unit_cost_snapshot BETWEEN 0 AND 99999999.99
      );
  END IF;

  -- The recipe migration creates an equivalent composite FK in the opposite
  -- column order. Its constraint has the same name and enforces the same
  -- restaurant boundary, so do not attempt to create it twice.
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'restaurant_orders_menu_item_same_restaurant_fk'
      AND conrelid = 'public.restaurant_orders'::regclass
      AND contype = 'f'
  ) THEN
    ALTER TABLE public.restaurant_orders
      ADD CONSTRAINT restaurant_orders_menu_item_same_restaurant_fk
      FOREIGN KEY (menu_item_id, restaurant_id)
      REFERENCES public.menu_items (id, restaurant_id)
      ON DELETE SET NULL (menu_item_id);
  END IF;
END;
$constraints$;

CREATE INDEX IF NOT EXISTS restaurant_orders_menu_item_restaurant_idx
  ON public.restaurant_orders (restaurant_id, menu_item_id)
  WHERE menu_item_id IS NOT NULL;

COMMIT;
