# StudioMesa — Supabase hidden keepalive

This is a silent maintenance task for the Supabase Free project. It runs from
GitHub Actions, not from Supabase, so the scheduler remains independent if the
Supabase project is paused.

## What it does

- Calls the database RPC `public.studiomesa_keepalive()` four times per day.
- The RPC updates one timestamp in `studiomesa_internal.keepalive_state`.
- The table is outside the exposed API schema and has no privileges for
  `anon`, `authenticated`, or `service_role`.
- The public RPC returns only `true`; it does not read or expose restaurant,
  customer, invoice, order, staff, or assistant data.
- Repeated calls inside one hour do not update the timestamp again.
- No notification or visible message is shown in the Android app.

## Activation sequence

1. Merge the code containing this migration and workflow.
2. Apply migration `20261107120000_hidden_supabase_keepalive.sql` to the
   Supabase project after the project's existing migration-history drift has
   been reconciled and a recovery point has been confirmed.
3. In GitHub → Settings → Secrets and variables → Actions → Variables, add
   `SUPABASE_KEEPALIVE_ENABLED` with value `true`.
4. In GitHub → Actions, run **Maintain StudioMesa Supabase (hidden heartbeat)**
   manually once and confirm the job succeeds.
5. After that, GitHub Actions runs it automatically at 01:17, 07:17, 13:17,
   and 19:17 UTC.

The workflow contains only the project's public legacy `anon` API key. This
key is designed to be used in client applications and is not the privileged
`service_role` key. The job is restricted to the dedicated heartbeat RPC.

## Limits

Supabase says a few real database requests per day are typically enough to
avoid inactivity pausing, but does not guarantee this threshold. This heartbeat
improves the chance that the Free project remains active; it is not an uptime
guarantee. GitHub scheduled jobs can also be delayed or disabled by repository
settings or inactivity. A paused Supabase project may still need manual
restoration in the Supabase Dashboard.
