-- A silent, least-privilege heartbeat for Supabase Free projects.
-- The table is kept outside the exposed public schema. The public RPC only
-- updates one timestamp and returns a constant boolean; it exposes no data.

BEGIN;

CREATE SCHEMA IF NOT EXISTS studiomesa_internal;

REVOKE ALL ON SCHEMA studiomesa_internal
  FROM PUBLIC, anon, authenticated, service_role;

CREATE TABLE IF NOT EXISTS studiomesa_internal.keepalive_state (
  singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton IS TRUE),
  last_ping_at timestamptz NOT NULL
);

REVOKE ALL ON TABLE studiomesa_internal.keepalive_state
  FROM PUBLIC, anon, authenticated, service_role;

INSERT INTO studiomesa_internal.keepalive_state (singleton, last_ping_at)
VALUES (true, pg_catalog.clock_timestamp())
ON CONFLICT (singleton) DO NOTHING;

CREATE OR REPLACE FUNCTION public.studiomesa_keepalive()
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog
AS $function$
BEGIN
  INSERT INTO studiomesa_internal.keepalive_state AS current_row
    (singleton, last_ping_at)
  VALUES
    (true, pg_catalog.clock_timestamp())
  ON CONFLICT (singleton) DO UPDATE
    SET last_ping_at = EXCLUDED.last_ping_at
    WHERE current_row.last_ping_at <
      pg_catalog.clock_timestamp() - interval '1 hour';

  RETURN true;
END;
$function$;

REVOKE ALL ON FUNCTION public.studiomesa_keepalive()
  FROM PUBLIC, anon, authenticated, service_role;

GRANT EXECUTE ON FUNCTION public.studiomesa_keepalive() TO anon;

NOTIFY pgrst, 'reload schema';

COMMIT;
