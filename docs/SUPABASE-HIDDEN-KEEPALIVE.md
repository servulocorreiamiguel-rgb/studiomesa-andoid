# StudioMesa — Supabase hidden keepalive

The silent keepalive RPC is already deployed on the live Supabase project
`svwvkfsyevuyabelrtjk`. It is called by a GitHub Actions workflow in the default
branch, independently of Supabase, four times per day.

## Behaviour and privacy

- Calls `public.studiomesa_keepalive()` at 01:17, 07:17, 13:17 and 19:17 UTC.
- The function is `SECURITY INVOKER` and only executes `SELECT true`.
- It does not read or write restaurant, customer, invoice, order, staff, assistant,
  memory, or authentication data.
- No notification or visible message is shown in the Android app.
- The workflow uses only the project's public legacy `anon` key, never
  `service_role`.

## Deployment and validation

- Live migration version: `20261107144901_studiomesa_hidden_keepalive`.
- Workflow file: `.github/workflows/supabase-keepalive.yml`.
- The actual production RPC was tested as role `anon` and returned `true`.
- Supabase project status was `ACTIVE_HEALTHY` at last verification.

## Limits

Supabase says a few real database requests per day are typically sufficient to
avoid inactivity pausing on the Free plan, but does not promise this threshold.
This workflow improves the chance that the project remains active; it cannot
guarantee uptime. GitHub scheduled jobs may be delayed or disabled, and an
inactive repository may have schedules disabled. A paused project may still need
manual restoration in the Supabase Dashboard.
