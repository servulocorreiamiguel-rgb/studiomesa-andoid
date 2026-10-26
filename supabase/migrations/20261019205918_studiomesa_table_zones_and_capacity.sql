
ALTER TABLE public.restaurant_tables
  ADD COLUMN IF NOT EXISTS area text NOT NULL DEFAULT 'Sala principal';
ALTER TABLE public.restaurant_tables
  ADD COLUMN IF NOT EXISTS seats integer NOT NULL DEFAULT 2;

ALTER TABLE public.restaurant_tables
  DROP CONSTRAINT IF EXISTS restaurant_tables_area_check;
ALTER TABLE public.restaurant_tables
  ADD CONSTRAINT restaurant_tables_area_check
  CHECK (char_length(trim(area)) BETWEEN 1 AND 80);

ALTER TABLE public.restaurant_tables
  DROP CONSTRAINT IF EXISTS restaurant_tables_seats_check;
ALTER TABLE public.restaurant_tables
  ADD CONSTRAINT restaurant_tables_seats_check
  CHECK (seats BETWEEN 1 AND 30);
