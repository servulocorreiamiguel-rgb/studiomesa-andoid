# StudioMesa — Supabase hidden keepalive

A quiet, read-only maintenance request for the Supabase Free project. It runs in
GitHub Actions, independently of Supabase, four times a day.

## Behaviour and privacy

- Calls `public.studiomesa_keepalive()` at 01:17, 07:17, 13:17 and 19:17 UTC.
- The function is `SECURITY INVOKER` and only executes `SELECT true`.
- It does not read or write restaurant, customer, invoice, order, staff, assistant,
  memory, or authentication data.
- No visible message, notification, or app activity is created.
- The workflow uses only the project's public legacy `anon` key, never
  `service_role`.

## Limits

Supabase says a few real database requests per day are typically sufficient to
avoid inactivity pausing on the Free plan, but does not promise this threshold.
This workflow improves the chance that the project remains active; it cannot
guarantee uptime. GitHub scheduled jobs may be delayed or disabled by repository
settings or repository inactivity. A paused project may still need manual restore
from the Supabase Dashboard.

## Deployment order

1. Apply the additive migration `20261107120000_hidden_supabase_keepalive.sql`.
2. Merge this workflow into the repository's default branch.
3. Confirm the first workflow run succeeds in GitHub → Actions.
