-- Index foreign-key columns used for inventory, movements and reservations.
-- This helps parent-row deletion checks and common filtering as restaurant data grows.
CREATE INDEX IF NOT EXISTS restaurant_inventory_items_created_by_idx
  ON public.restaurant_inventory_items (created_by);

CREATE INDEX IF NOT EXISTS restaurant_inventory_movements_created_by_idx
  ON public.restaurant_inventory_movements (created_by);

CREATE INDEX IF NOT EXISTS restaurant_inventory_movements_restaurant_id_idx
  ON public.restaurant_inventory_movements (restaurant_id);

CREATE INDEX IF NOT EXISTS restaurant_reservations_created_by_idx
  ON public.restaurant_reservations (created_by);
