
CREATE TABLE IF NOT EXISTS public.restaurant_tables (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  restaurant_id uuid NOT NULL REFERENCES public.restaurants(id) ON DELETE CASCADE,
  table_number integer NOT NULL CHECK (table_number BETWEEN 1 AND 100),
  is_occupied boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (restaurant_id, table_number)
);

CREATE TABLE IF NOT EXISTS public.menu_items (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  restaurant_id uuid NOT NULL REFERENCES public.restaurants(id) ON DELETE CASCADE,
  name text NOT NULL CHECK (char_length(trim(name)) BETWEEN 1 AND 120),
  price numeric(10,2) NOT NULL CHECK (price >= 0),
  cost numeric(10,2) NOT NULL DEFAULT 0 CHECK (cost >= 0),
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.restaurant_orders (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  restaurant_id uuid NOT NULL REFERENCES public.restaurants(id) ON DELETE CASCADE,
  table_number integer NOT NULL CHECK (table_number BETWEEN 1 AND 100),
  item_name text NOT NULL CHECK (char_length(trim(item_name)) BETWEEN 1 AND 120),
  amount numeric(10,2) NOT NULL CHECK (amount >= 0),
  status text NOT NULL DEFAULT 'Aberto' CHECK (status IN ('Aberto', 'Em preparação', 'Pronto', 'Concluído', 'Cancelado')),
  created_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS restaurant_tables_restaurant_idx
  ON public.restaurant_tables (restaurant_id, table_number);
CREATE INDEX IF NOT EXISTS menu_items_restaurant_active_idx
  ON public.menu_items (restaurant_id, is_active, name);
CREATE INDEX IF NOT EXISTS restaurant_orders_restaurant_created_idx
  ON public.restaurant_orders (restaurant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS restaurant_orders_restaurant_status_idx
  ON public.restaurant_orders (restaurant_id, status);

ALTER TABLE public.restaurant_tables ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.menu_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.restaurant_orders ENABLE ROW LEVEL SECURITY;

GRANT SELECT, INSERT, UPDATE, DELETE ON public.restaurant_tables TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.menu_items TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.restaurant_orders TO authenticated;

DROP POLICY IF EXISTS restaurant_tables_staff_all ON public.restaurant_tables;
CREATE POLICY restaurant_tables_staff_all ON public.restaurant_tables
  FOR ALL TO authenticated
  USING (private.is_restaurant_staff(restaurant_id))
  WITH CHECK (private.is_restaurant_staff(restaurant_id));

DROP POLICY IF EXISTS menu_items_staff_all ON public.menu_items;
CREATE POLICY menu_items_staff_all ON public.menu_items
  FOR ALL TO authenticated
  USING (private.is_restaurant_staff(restaurant_id))
  WITH CHECK (private.is_restaurant_staff(restaurant_id));

DROP POLICY IF EXISTS restaurant_orders_staff_all ON public.restaurant_orders;
CREATE POLICY restaurant_orders_staff_all ON public.restaurant_orders
  FOR ALL TO authenticated
  USING (private.is_restaurant_staff(restaurant_id))
  WITH CHECK (private.is_restaurant_staff(restaurant_id));

INSERT INTO public.restaurant_tables (restaurant_id, table_number)
SELECT r.id, s.table_number
FROM public.restaurants r
CROSS JOIN generate_series(1, 12) AS s(table_number)
ON CONFLICT (restaurant_id, table_number) DO NOTHING;

CREATE OR REPLACE FUNCTION public.create_restaurant(p_name text, p_join_code text)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_restaurant_id uuid;
  v_name text := trim(coalesce(p_name, ''));
  v_code text := upper(trim(coalesce(p_join_code, '')));
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  IF char_length(v_name) < 2 OR char_length(v_name) > 100 THEN
    RAISE EXCEPTION 'O nome do restaurante deve ter entre 2 e 100 caracteres.';
  END IF;
  IF v_code !~ '^[A-Z0-9]{6,12}$' THEN
    RAISE EXCEPTION 'O código deve ter 6 a 12 letras/números.';
  END IF;

  INSERT INTO public.restaurants (name)
  VALUES (v_name)
  RETURNING id INTO v_restaurant_id;

  INSERT INTO private.restaurant_join_codes (restaurant_id, join_code)
  VALUES (v_restaurant_id, v_code);

  INSERT INTO public.restaurant_staff (restaurant_id, user_id, role)
  VALUES (v_restaurant_id, (SELECT auth.uid()), 'owner');

  INSERT INTO public.restaurant_tables (restaurant_id, table_number)
  SELECT v_restaurant_id, s.table_number
  FROM generate_series(1, 12) AS s(table_number);

  RETURN v_restaurant_id;
END;
$function$;

REVOKE ALL ON FUNCTION public.create_restaurant(text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_restaurant(text, text) TO authenticated;
