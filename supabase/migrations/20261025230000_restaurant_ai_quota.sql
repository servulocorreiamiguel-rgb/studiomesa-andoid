-- Restaurant AI quota. The function is intentionally dormant until the
-- server-side OPENAI_API_KEY and STUDIOMESA_AI_ENABLED flag are configured.
BEGIN;

CREATE TABLE IF NOT EXISTS private.restaurant_ai_usage (
  restaurant_id uuid NOT NULL REFERENCES public.restaurants(id) ON DELETE CASCADE,
  usage_date date NOT NULL,
  requests_used integer NOT NULL DEFAULT 0 CHECK (requests_used BETWEEN 0 AND 10),
  PRIMARY KEY (restaurant_id, usage_date)
);

ALTER TABLE private.restaurant_ai_usage ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE private.restaurant_ai_usage FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.consume_restaurant_ai_quota()
RETURNS TABLE(allowed boolean, requests_used integer, daily_limit integer)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_user_id uuid := (SELECT auth.uid());
  v_restaurant_id uuid;
  v_usage_date date := (pg_catalog.now() AT TIME ZONE 'UTC')::date;
  v_requests_used integer;
  v_daily_limit CONSTANT integer := 10;
BEGIN
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;

  SELECT rs.restaurant_id
    INTO v_restaurant_id
    FROM public.restaurant_staff AS rs
   WHERE rs.user_id = v_user_id
     AND rs.role = 'owner'
   ORDER BY rs.created_at ASC
   LIMIT 1;

  IF v_restaurant_id IS NULL THEN
    RAISE EXCEPTION 'Só o proprietário do restaurante pode usar o assistente de IA.';
  END IF;

  INSERT INTO private.restaurant_ai_usage AS usage (
    restaurant_id, usage_date, requests_used
  )
  VALUES (v_restaurant_id, v_usage_date, 1)
  ON CONFLICT (restaurant_id, usage_date)
  DO UPDATE
     SET requests_used = usage.requests_used + 1
   WHERE usage.requests_used < v_daily_limit
  RETURNING usage.requests_used INTO v_requests_used;

  IF NOT FOUND THEN
    SELECT usage.requests_used
      INTO v_requests_used
      FROM private.restaurant_ai_usage AS usage
     WHERE usage.restaurant_id = v_restaurant_id
       AND usage.usage_date = v_usage_date;

    RETURN QUERY SELECT false, v_requests_used, v_daily_limit;
    RETURN;
  END IF;

  RETURN QUERY SELECT true, v_requests_used, v_daily_limit;
END;
$function$;

REVOKE ALL ON FUNCTION public.consume_restaurant_ai_quota() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.consume_restaurant_ai_quota() TO authenticated;

COMMIT;
