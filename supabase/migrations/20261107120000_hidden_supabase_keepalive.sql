-- Silent, low-impact keepalive request for Supabase Free projects.
-- This is a SECURITY INVOKER function: it reads no application data and writes nothing.
-- GitHub Actions calls it several times a day to generate real database API activity.

BEGIN;

CREATE OR REPLACE FUNCTION public.studiomesa_keepalive()
RETURNS boolean
LANGUAGE sql
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
  SELECT true;
$function$;

REVOKE ALL ON FUNCTION public.studiomesa_keepalive() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.studiomesa_keepalive() TO anon;

NOTIFY pgrst, 'reload schema';

COMMIT;
