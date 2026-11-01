# Supabase health check — StudioMesa

## What this does

The scheduled GitHub Actions workflow runs a tiny PostgreSQL `SELECT 1` query every 8 hours. It does not insert orders, reservations, payments, stock movements, customers, or any other business records. Nothing is displayed in the restaurant app, and no operational metrics are changed.

The workflow is a technical availability check, not a guarantee that Supabase will classify the project as active. Supabase's Free-plan inactivity classifier is not a published fixed request-count threshold. Its current guidance says low database activity over a 7-day period can trigger a pause and that a few user requests per day are typically enough, but not guaranteed. A project used by real restaurants should move to a paid plan before it becomes business-critical.

## One-time setup

1. In Supabase, create a dedicated PostgreSQL login role for the health check. Do not use the app's publishable key or a `service_role` secret.
2. Give that role only permission to connect to the `postgres` database. It does not need access to application schemas or tables:

   ```sql
   CREATE ROLE studiomesa_healthcheck
     LOGIN
     PASSWORD 'replace-with-a-strong-unique-password'
     NOSUPERUSER
     NOCREATEDB
     NOCREATEROLE
     NOINHERIT;

   GRANT CONNECT ON DATABASE postgres TO studiomesa_healthcheck;
   ALTER ROLE studiomesa_healthcheck SET default_transaction_read_only = on;
   ```

   Run this once in the Supabase SQL Editor as an administrator, after confirming the project's backup/recovery position. Keep the password private.
3. In the Supabase Dashboard, open **Connect** and copy the **Session pooler** PostgreSQL connection string. Use the dedicated role above in the connection string. GitHub-hosted runners use IPv4, so the session pooler is generally the compatible option unless the project has IPv4 enabled for direct connections.
4. In GitHub, open **Settings → Secrets and variables → Actions → New repository secret**. Name it `SUPABASE_HEALTHCHECK_DB_URL` and paste the connection string. Do not put it in the repository, Android app, issue comments, or chat.
5. Merge this workflow to the repository's default branch (`main`). GitHub scheduled workflows run from the default branch. You can also use **Actions → StudioMesa Supabase health check → Run workflow** for a manual check.

## Limits and safety

- The check runs every 8 hours and only executes `SELECT 1`; it does not create or alter restaurant data.
- The connection role has no grants on application tables or schemas.
- The workflow must be on `main` for its schedule to run. GitHub may disable scheduled workflows in public repositories after prolonged repository inactivity.
- A successful run proves that the database accepted that query at that moment. It does not prove that Auth, Storage, Realtime, Edge Functions, or the Android app are all healthy.
- Supabase does not promise that this synthetic health check alone prevents Free-plan pausing. The authoritative way to avoid inactivity pausing for a production service is a paid plan.
