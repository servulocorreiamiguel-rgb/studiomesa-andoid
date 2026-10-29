-- Fail closed for new Data API objects created by the postgres migration role.
-- Existing object grants are unchanged; migrations must explicitly grant only the
-- privileges required by each new table, sequence, or function.
BEGIN;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  REVOKE ALL ON TABLES FROM anon, authenticated, service_role;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  REVOKE ALL ON SEQUENCES FROM anon, authenticated, service_role;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  REVOKE ALL ON FUNCTIONS FROM anon, authenticated, service_role;

-- PostgreSQL grants EXECUTE on new functions to PUBLIC by default. This must be
-- revoked globally: an IN SCHEMA-only revoke cannot remove a global default grant.
ALTER DEFAULT PRIVILEGES FOR ROLE postgres
  REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

COMMIT;
