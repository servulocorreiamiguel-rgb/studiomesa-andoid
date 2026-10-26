
ALTER TABLE public.restaurant_orders
  ADD COLUMN IF NOT EXISTS quantity integer NOT NULL DEFAULT 1;
ALTER TABLE public.restaurant_orders
  ADD COLUMN IF NOT EXISTS service_course text NOT NULL DEFAULT 'Principal';
ALTER TABLE public.restaurant_orders
  ADD COLUMN IF NOT EXISTS notes text NOT NULL DEFAULT '';

ALTER TABLE public.restaurant_orders
  DROP CONSTRAINT IF EXISTS restaurant_orders_quantity_check;
ALTER TABLE public.restaurant_orders
  ADD CONSTRAINT restaurant_orders_quantity_check
  CHECK (quantity BETWEEN 1 AND 99);

ALTER TABLE public.restaurant_orders
  DROP CONSTRAINT IF EXISTS restaurant_orders_service_course_check;
ALTER TABLE public.restaurant_orders
  ADD CONSTRAINT restaurant_orders_service_course_check
  CHECK (service_course IN ('Bebidas', 'Aperitivo', 'Entrada', 'Principal', 'Sobremesa'));

ALTER TABLE public.restaurant_orders
  DROP CONSTRAINT IF EXISTS restaurant_orders_notes_check;
ALTER TABLE public.restaurant_orders
  ADD CONSTRAINT restaurant_orders_notes_check
  CHECK (char_length(notes) <= 500);
