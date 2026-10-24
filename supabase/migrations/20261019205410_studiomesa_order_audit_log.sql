
CREATE TABLE IF NOT EXISTS public.restaurant_order_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  restaurant_id uuid NOT NULL REFERENCES public.restaurants(id) ON DELETE CASCADE,
  order_id uuid NOT NULL REFERENCES public.restaurant_orders(id) ON DELETE CASCADE,
  event_type text NOT NULL CHECK (event_type IN ('created', 'status_changed')),
  old_status text,
  new_status text,
  actor_user_id uuid REFERENCES auth.users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS restaurant_order_events_restaurant_created_idx
  ON public.restaurant_order_events (restaurant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS restaurant_order_events_order_idx
  ON public.restaurant_order_events (order_id, created_at ASC);

ALTER TABLE public.restaurant_order_events ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.restaurant_order_events FROM PUBLIC, anon, authenticated;
GRANT SELECT ON TABLE public.restaurant_order_events TO authenticated;

DROP POLICY IF EXISTS restaurant_order_events_staff_read ON public.restaurant_order_events;
CREATE POLICY restaurant_order_events_staff_read
  ON public.restaurant_order_events
  FOR SELECT TO authenticated
  USING (private.is_restaurant_staff(restaurant_id));

CREATE OR REPLACE FUNCTION private.prepare_restaurant_order()
RETURNS trigger
LANGUAGE plpgsql
SET search_path TO ''
AS $function$
BEGIN
  NEW.created_by := (SELECT auth.uid());
  RETURN NEW;
END;
$function$;

CREATE OR REPLACE FUNCTION private.audit_restaurant_order()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_event_type text;
  v_old_status text;
  v_new_status text;
BEGIN
  IF TG_OP = 'INSERT' THEN
    v_event_type := 'created';
    v_old_status := NULL;
    v_new_status := NEW.status;
  ELSIF TG_OP = 'UPDATE' AND NEW.status IS DISTINCT FROM OLD.status THEN
    v_event_type := 'status_changed';
    v_old_status := OLD.status;
    v_new_status := NEW.status;
  ELSE
    RETURN NEW;
  END IF;

  INSERT INTO public.restaurant_order_events
    (restaurant_id, order_id, event_type, old_status, new_status, actor_user_id)
  VALUES
    (NEW.restaurant_id, NEW.id, v_event_type, v_old_status, v_new_status, (SELECT auth.uid()));

  RETURN NEW;
END;
$function$;

DROP TRIGGER IF EXISTS restaurant_order_set_creator ON public.restaurant_orders;
CREATE TRIGGER restaurant_order_set_creator
  BEFORE INSERT ON public.restaurant_orders
  FOR EACH ROW EXECUTE FUNCTION private.prepare_restaurant_order();

DROP TRIGGER IF EXISTS restaurant_order_audit_log ON public.restaurant_orders;
CREATE TRIGGER restaurant_order_audit_log
  AFTER INSERT OR UPDATE ON public.restaurant_orders
  FOR EACH ROW EXECUTE FUNCTION private.audit_restaurant_order();

REVOKE ALL ON FUNCTION private.prepare_restaurant_order() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private.audit_restaurant_order() FROM PUBLIC, anon, authenticated;
