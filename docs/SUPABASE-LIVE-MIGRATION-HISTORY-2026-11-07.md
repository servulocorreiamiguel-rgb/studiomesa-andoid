# Supabase live migration reconciliation — 7 November 2026

The following feature migrations have been applied independently to the live
project and their filenames are aligned to the versions recorded by Supabase:

| Live migration version | Purpose |
|---|---|
| `20261107144901_studiomesa_hidden_keepalive` | Read-only heartbeat RPC used by GitHub Actions |
| `20261107145135_studiomesa_assistant_sync_and_supplier_invoices` | Owner-only assistant history, memory and supplier invoice archive |
| `20261107145308_studiomesa_assistant_invoice_fk_indexes` | Covering indexes for assistant and invoice foreign keys |

The invoice migration was first validated in a transaction and rolled back, then
applied as an additive change. It created four tables with RLS enabled, eight
owner-scoped policies, an invoker-only conversation deletion/review boundary,
and the private `studiomesa-invoices` Storage bucket. The keepalive is a
`SECURITY INVOKER` function that performs only `SELECT true`.

## Remaining history drift

The full repository and hosted migration history are still not identical. As of
this check, there are 55 migration files in the branch and
57 versions recorded by the live project.

**Repository-only versions:**
- `20261030111815_restaurant_recipes_and_order_stock_consumption`
- `20261030120000_inventory_unit_cost_and_waste_snapshots`
- `20261102103000_granular_restaurant_staff_permissions`
- `20261104100000_price_adviser_actor_indexes`

**Live-only versions:**
- `20261102082151_reconcile_inventory_unit_cost_and_waste_snapshots`
- `20261102103246_loyalty_invoice_qr`
- `20261102103627_loyalty_invoice_qr_foreign_key_indexes`
- `20261102104650_loyalty_invoice_qr_private_execution`
- `20261102113719_loyalty_shop_git_reconciliation_and_redemption_queue`
- `20261102113944_loyalty_shop_private_function_boundary`

The remaining versions are outside this feature's scope. Do not bulk-apply,
delete, or mark them as reconciled without reviewing their SQL and dependencies.
Future database deployments should run the repository-vs-live reconciliation
procedure before applying additional migrations.
