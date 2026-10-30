BEGIN;

-- Orders are line items, so bind new orders to a stable menu UUID while
-- retaining item_name and amount as immutable display/price snapshots.
-- The composite key makes cross-restaurant references impossible at the DB layer.
ALTER TABLE public.menu_items
  ADD CONSTRAINT menu_items_id_restaurant_pair_key
  UNIQUE (id, restaurant_id);

ALTER TABLE public.restaurant_orders
  ADD COLUMN menu_item_id uuid,
  ADD COLUMN unit_cost_snapshot numeric(10,2);

ALTER TABLE public.restaurant_orders
  ADD CONSTRAINT restaurant_orders_unit_cost_snapshot_check
  CHECK (
    unit_cost_snapshot IS NULL
    OR unit_cost_snapshot BETWEEN 0 AND 99999999.99
  );

ALTER TABLE public.restaurant_orders
  ADD CONSTRAINT restaurant_orders_menu_item_same_restaurant_fk
  FOREIGN KEY (menu_item_id, restaurant_id)
  REFERENCES public.menu_items (id, restaurant_id)
  ON DELETE SET NULL (menu_item_id);

CREATE INDEX restaurant_orders_menu_item_restaurant_idx
  ON public.restaurant_orders (restaurant_id, menu_item_id)
  WHERE menu_item_id IS NOT NULL;

COMMIT;
