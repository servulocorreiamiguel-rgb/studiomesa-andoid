#!/usr/bin/env bash
# Authorization/idempotency regression tests against the disposable PostgreSQL
# migration replay database. This is not a substitute for testing Supabase Auth/JWT
# and REST behavior on the hosted project.
set -euo pipefail

USER_A="11111111-1111-4111-8111-111111111111"
USER_B="22222222-2222-4222-8222-222222222222"
USER_C="33333333-3333-4333-8333-333333333333"
USER_D="44444444-4444-4444-8444-444444444444"
USER_E="55555555-5555-4555-8555-555555555555"
USER_F="66666666-6666-4666-8666-666666666666"
REQUEST_A="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
REQUEST_B="bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"

# Inspect the catalog after all migrations have run, not just source text.
# Privileged functions must pin an empty search_path and must not be executable by anon.
unsafe_definer_count="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<'SQL'
SELECT count(*)::text
FROM pg_proc AS p
JOIN pg_namespace AS n ON n.oid = p.pronamespace
WHERE n.nspname IN ('public', 'private')
  AND p.prosecdef
  AND (
    NOT COALESCE(p.proconfig @> ARRAY['search_path=""']::text[], false)
    OR has_function_privilege('anon', p.oid, 'EXECUTE')
  );
SQL
)"
if [ "$unsafe_definer_count" != "0" ]; then
  echo "ERROR: found $unsafe_definer_count SECURITY DEFINER function(s) without a pinned empty search_path or executable by anon." >&2
  psql --no-psqlrc --tuples-only --no-align --set ON_ERROR_STOP=1 -c "
    SELECT n.nspname || '.' || p.proname || '(' || pg_get_function_identity_arguments(p.oid) || ')'
    FROM pg_proc AS p
    JOIN pg_namespace AS n ON n.oid = p.pronamespace
    WHERE n.nspname IN ('public', 'private')
      AND p.prosecdef
      AND (
        NOT COALESCE(p.proconfig @> ARRAY['search_path=""']::text[], false)
        OR has_function_privilege('anon', p.oid, 'EXECUTE')
      );
  " >&2
  exit 1
fi
echo "PASS: application SECURITY DEFINER functions pin an empty search_path and are not executable by anon."

# Test the effective defaults by creating disposable objects as the same postgres
# role that replays migrations. Newly-created API objects must be opt-in, not public.
psql --no-psqlrc --set ON_ERROR_STOP=1 <<'SQL'
CREATE TABLE public.__studiomesa_default_privilege_probe (id bigint);
CREATE SEQUENCE public.__studiomesa_default_privilege_probe_seq;
CREATE FUNCTION public.__studiomesa_default_privilege_probe_fn()
RETURNS integer LANGUAGE sql
AS $function$ SELECT 1; $function$;

DO $check$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM (VALUES ('anon'), ('authenticated'), ('service_role')) AS api_roles(role_name)
    CROSS JOIN (
      VALUES ('SELECT'), ('INSERT'), ('UPDATE'), ('DELETE'), ('TRUNCATE'),
             ('REFERENCES'), ('TRIGGER'), ('MAINTAIN')
    ) AS table_privileges(privilege_name)
    WHERE has_table_privilege(
      api_roles.role_name,
      'public.__studiomesa_default_privilege_probe',
      table_privileges.privilege_name
    )
  ) THEN
    RAISE EXCEPTION 'New public tables inherited unexpected API-role privileges.';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM (VALUES ('anon'), ('authenticated'), ('service_role')) AS api_roles(role_name)
    CROSS JOIN (VALUES ('USAGE'), ('SELECT'), ('UPDATE')) AS sequence_privileges(privilege_name)
    WHERE has_sequence_privilege(
      api_roles.role_name,
      'public.__studiomesa_default_privilege_probe_seq',
      sequence_privileges.privilege_name
    )
  ) THEN
    RAISE EXCEPTION 'New public sequences inherited unexpected API-role privileges.';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM (VALUES ('anon'), ('authenticated'), ('service_role')) AS api_roles(role_name)
    WHERE has_function_privilege(
      api_roles.role_name,
      'public.__studiomesa_default_privilege_probe_fn()',
      'EXECUTE'
    )
  ) THEN
    RAISE EXCEPTION 'New public functions inherited unexpected API-role or PUBLIC EXECUTE privileges.';
  END IF;
END;
$check$;

DROP FUNCTION public.__studiomesa_default_privilege_probe_fn();
DROP SEQUENCE public.__studiomesa_default_privilege_probe_seq;
DROP TABLE public.__studiomesa_default_privilege_probe;

SQL

echo "PASS: new public tables, sequences and functions require explicit grants."

psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL
INSERT INTO auth.users (id, email)
VALUES ('$USER_A', 'ci-owner-a@example.invalid'),
       ('$USER_B', 'ci-owner-b@example.invalid'),
       ('$USER_C', 'ci-staff-c@example.invalid'),
       ('$USER_D', 'ci-staff-d@example.invalid'),
       ('$USER_E', 'ci-customer-e@example.invalid'),
       ('$USER_F', 'ci-customer-f@example.invalid');
SQL

create_restaurant() {
  local user_id="$1" name="$2" code="$3"
  psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
SELECT public.create_restaurant('$name', '$code');
COMMIT;
SQL
}

create_inventory_item() {
  local user_id="$1" restaurant_id="$2" request_id="$3" item_name="$4" amount="$5"
  psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
SELECT public.create_inventory_item_idempotent(
  '$request_id'::uuid, '$restaurant_id'::uuid, '$item_name', 'kg', '$amount'::numeric, 1, 'CI isolation test'
);
COMMIT;
SQL
}

adjust_inventory_stock() {
  local user_id="$1" item_id="$2" request_id="$3" delta="$4" note="$5"
  psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
SELECT quantity FROM public.adjust_inventory_stock_idempotent(
  '$request_id'::uuid, '$item_id'::uuid, '$delta'::numeric, '$note'
);
COMMIT;
SQL
}

join_customer() {
  local user_id="$1" join_code="$2" display_name="$3"
  psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
SELECT member_id FROM public.join_restaurant('$join_code', '$display_name');
COMMIT;
SQL
}

join_staff_member() {
  local user_id="$1" staff_code="$2"
  psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
SELECT restaurant_id::text || '|' || restaurant_name || '|' || user_role
FROM public.join_restaurant_staff('$staff_code');
COMMIT;
SQL
}

adjust_loyalty_points() {
  local user_id="$1" member_id="$2" request_id="$3" delta="$4" note="$5"
  psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
SELECT points FROM public.adjust_loyalty_points_idempotent(
  '$request_id'::uuid, '$member_id'::uuid, '$delta'::integer, '$note'
);
COMMIT;
SQL
}

create_restaurant_reservation() {
  local user_id="$1" restaurant_id="$2" request_id="$3" guest_name="$4" reservation_at="$5"
  psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
SELECT public.create_restaurant_reservation_idempotent(
  '$request_id'::uuid, '$restaurant_id'::uuid, '$guest_name', '', 2,
  '$reservation_at'::timestamptz, 60, NULL::integer, 'CI reservation isolation test'
);
COMMIT;
SQL
}

RESTAURANT_A="$(create_restaurant "$USER_A" "CI Restaurant A" "SMOKEAA001")"
RESTAURANT_B="$(create_restaurant "$USER_B" "CI Restaurant B" "SMOKEBB001")"
test -n "$RESTAURANT_A"
test -n "$RESTAURANT_B"
test "$RESTAURANT_A" != "$RESTAURANT_B"

# Exercise the corrected staff-join RPC in PostgreSQL, not just migration replay.
# Repeating the same join must be safe; attempting a second restaurant must fail.
STAFF_CODE_A="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT staff_code FROM private.restaurant_join_codes WHERE restaurant_id = '$RESTAURANT_A'::uuid;")"
STAFF_CODE_B="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT staff_code FROM private.restaurant_join_codes WHERE restaurant_id = '$RESTAURANT_B'::uuid;")"
test -n "$STAFF_CODE_A"
test -n "$STAFF_CODE_B"

STAFF_JOIN_FIRST="$(join_staff_member "$USER_C" "$STAFF_CODE_A")"
STAFF_JOIN_RETRY="$(join_staff_member "$USER_C" "$STAFF_CODE_A")"
EXPECTED_STAFF_JOIN="$RESTAURANT_A|CI Restaurant A|staff"
if [ "$STAFF_JOIN_FIRST" != "$EXPECTED_STAFF_JOIN" ] || [ "$STAFF_JOIN_RETRY" != "$EXPECTED_STAFF_JOIN" ]; then
  echo "ERROR: joining a restaurant as staff failed or a repeated join returned unexpected data." >&2
  printf 'First: %s\nRetry: %s\n' "$STAFF_JOIN_FIRST" "$STAFF_JOIN_RETRY" >&2
  exit 1
fi

if join_staff_member "$USER_C" "$STAFF_CODE_B" >/dev/null 2>&1; then
  echo "ERROR: staff account unexpectedly joined a second restaurant." >&2
  exit 1
fi
STAFF_C_RESTAURANT="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT restaurant_id::text FROM public.restaurant_staff WHERE user_id = '$USER_C'::uuid;")"
if [ "$STAFF_C_RESTAURANT" != "$RESTAURANT_A" ]; then
  echo "ERROR: rejected cross-restaurant join changed the existing staff membership." >&2
  exit 1
fi
echo "PASS: staff join RPC works, repeated joins are idempotent, and a staff account cannot join a second restaurant."

# A new order must reference the stable menu UUID and retain its own name,
# sale-price and known-cost snapshots if the menu is edited or its row is deleted.
ORDER_MENU_ITEM_A="aaaaaaaa-1111-4111-8111-111111111111"
ORDER_MENU_ITEM_B="bbbbbbbb-2222-4222-8222-222222222222"
ORDER_MENU_ITEM_ARCHIVED="abababab-3333-4333-8333-333333333333"
ORDER_SNAPSHOT_ID="cccccccc-3333-4333-8333-333333333333"
ORDER_LEGACY_ID="dddddddd-4444-4444-8444-444444444444"
ORDER_FOREIGN_ID="eeeeeeee-5555-4555-8555-555555555555"
ORDER_INVALID_COST_ID="ffffffff-6666-4666-8666-666666666666"
ORDER_ARCHIVED_ORDER_ID="99999999-7777-4777-8777-777777777777"

psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
INSERT INTO public.menu_items
  (id, restaurant_id, name, category, description, price, cost, allergens, is_active)
VALUES
  ('$ORDER_MENU_ITEM_A'::uuid, '$RESTAURANT_A'::uuid, 'Bitoque original',
   'Prato principal', '', 13.50, 4.25, '', true),
  ('$ORDER_MENU_ITEM_ARCHIVED'::uuid, '$RESTAURANT_A'::uuid, 'Prato arquivado',
   'Prato principal', '', 8.00, 2.00, '', false);
INSERT INTO public.restaurant_orders
  (id, restaurant_id, table_number, item_name, amount, quantity,
   service_course, notes, status, menu_item_id, unit_cost_snapshot)
VALUES
  ('$ORDER_SNAPSHOT_ID'::uuid, '$RESTAURANT_A'::uuid, 1,
   'Preço e custo forjados pelo cliente', 0.01, 2, 'Principal', '', 'Aberto',
   '$ORDER_MENU_ITEM_A'::uuid, -0.01);
COMMIT;
SQL

psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL
UPDATE public.menu_items
SET name = 'Bitoque atualizado', price = 15.00, cost = 5.50
WHERE id = '$ORDER_MENU_ITEM_A'::uuid
  AND restaurant_id = '$RESTAURANT_A'::uuid;
SQL

order_snapshot_ok="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT menu_item_id = '$ORDER_MENU_ITEM_A'::uuid
         AND item_name = 'Bitoque original'
         AND amount = 13.50
         AND unit_cost_snapshot = 4.25
      FROM public.restaurant_orders WHERE id = '$ORDER_SNAPSHOT_ID'::uuid;")"
# A new order without an active menu UUID cannot enter the API,
# even when the client supplies an apparently complete manual line.
if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
INSERT INTO public.restaurant_orders
  (id, restaurant_id, table_number, item_name, amount, quantity,
   service_course, notes, status, menu_item_id, unit_cost_snapshot)
VALUES
  ('$ORDER_LEGACY_ID'::uuid, '$RESTAURANT_A'::uuid, 2,
   'Pedido novo sem UUID', 7.00, 1, 'Principal', '', 'Aberto', NULL, 4.25);
COMMIT;
SQL
then
  echo "ERROR: a new order without a menu item was accepted." >&2
  exit 1
fi
legacy_insert_count="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*)::text FROM public.restaurant_orders WHERE id = '$ORDER_LEGACY_ID'::uuid;")"
if [ "$order_snapshot_ok" != "t" ] || [ "$legacy_insert_count" != "0" ]; then
  echo "ERROR: menu editing changed an order snapshot or unlinked order insertion was not rejected." >&2
  exit 1
fi

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
UPDATE public.restaurant_orders
SET item_name = 'Nome alterado pelo cliente',
    amount = 0.01,
    unit_cost_snapshot = 0.01
WHERE id = '$ORDER_SNAPSHOT_ID'::uuid;
COMMIT;
SQL
then
  echo "ERROR: a client unexpectedly changed immutable order name/price/cost snapshots." >&2
  exit 1
fi

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
INSERT INTO public.restaurant_orders
  (id, restaurant_id, table_number, item_name, amount, quantity,
   service_course, notes, status, menu_item_id, unit_cost_snapshot)
VALUES
  ('$ORDER_ARCHIVED_ORDER_ID'::uuid, '$RESTAURANT_A'::uuid, 1,
   'Prato arquivado', 8.00, 1, 'Principal', '', 'Aberto',
   '$ORDER_MENU_ITEM_ARCHIVED'::uuid, 2.00);
COMMIT;
SQL
then
  echo "ERROR: order creation accepted an archived menu item." >&2
  exit 1
fi

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
INSERT INTO public.menu_items
  (id, restaurant_id, name, category, price, cost, allergens, is_active)
VALUES
  ('$ORDER_MENU_ITEM_B'::uuid, '$RESTAURANT_B'::uuid, 'Dish restaurant B',
   'Prato principal', 9.00, 2.00, '', true);
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
INSERT INTO public.restaurant_orders
  (id, restaurant_id, table_number, item_name, amount, quantity,
   service_course, notes, status, menu_item_id, unit_cost_snapshot)
VALUES
  ('$ORDER_FOREIGN_ID'::uuid, '$RESTAURANT_A'::uuid, 1,
   'Dish restaurant B', 9.00, 1, 'Principal', '', 'Aberto',
   '$ORDER_MENU_ITEM_B'::uuid, 2.00);
COMMIT;
SQL
then
  echo "ERROR: order A accepted a menu item belonging to restaurant B." >&2
  exit 1
fi

psql --no-psqlrc --set ON_ERROR_STOP=1 -c \
  "DELETE FROM public.menu_items WHERE id = '$ORDER_MENU_ITEM_A'::uuid AND restaurant_id = '$RESTAURANT_A'::uuid;" >/dev/null
deleted_menu_snapshot_ok="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT menu_item_id IS NULL
         AND item_name = 'Bitoque original'
         AND amount = 13.50
         AND unit_cost_snapshot = 4.25
      FROM public.restaurant_orders WHERE id = '$ORDER_SNAPSHOT_ID'::uuid;")"
if [ "$deleted_menu_snapshot_ok" != "t" ]; then
  echo "ERROR: deleting a menu row did not preserve the order's historical snapshots." >&2
  exit 1
fi
echo "PASS: new orders require an active menu UUID; server-owned snapshots override forged values, archived/cross-restaurant menu items are rejected, and historical values survive menu edits/deletes."

PRICE_MENU_ITEM_A="99999999-1111-4111-8111-111111111111"
PRICE_MENU_ITEM_B="99999999-2222-4222-8222-222222222222"
PRICE_ACTION_ID="99999999-3333-4333-8333-333333333333"
PRICE_OFF_ACTION_ID="99999999-4444-4444-8444-444444444444"
PRICE_LOW_ACTION_ID="99999999-5555-4555-8555-555555555555"

psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL
INSERT INTO public.menu_items (
  id, restaurant_id, name, category, description, price, cost, allergens, is_active
) VALUES
  ('$PRICE_MENU_ITEM_A'::uuid, '$RESTAURANT_A'::uuid, 'CI Price Dish A',
   'Prato principal', '', 10.00, 4.00, '', true),
  ('$PRICE_MENU_ITEM_B'::uuid, '$RESTAURANT_B'::uuid, 'CI Price Dish B',
   'Prato principal', '', 10.00, 2.00, '', true);
SQL

get_price_adviser_enabled() {
  local user_id="$1"
  psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
SELECT public.get_restaurant_price_adviser_enabled()::text;
COMMIT;
SQL
}

set_price_adviser_enabled() {
  local user_id="$1" enabled="$2"
  psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
SELECT public.set_restaurant_price_adviser_enabled('$enabled'::boolean)::text;
COMMIT;
SQL
}

apply_advised_menu_price() {
  local user_id="$1" action_id="$2" item_id="$3" expected_price="$4" new_price="$5" target_percent="$6"
  psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
SELECT action_id::text || '|' || menu_item_id::text || '|' || previous_price::text ||
       '|' || applied_price::text || '|' || unit_cost::text ||
       '|' || target_cost_percent::text || '|' || was_replay::text
FROM public.apply_restaurant_advised_menu_price(
  '$action_id'::uuid, '$item_id'::uuid, '$expected_price'::numeric,
  '$new_price'::numeric, '$target_percent'::numeric
);
COMMIT;
SQL
}

# The absent settings row must behave as OFF, not as an implicit permission.
price_setting_default="$(get_price_adviser_enabled "$USER_A")"
if [ "$price_setting_default" != "false" ]; then
  echo "ERROR: price adviser permission was not disabled by default." >&2
  exit 1
fi

# Staff cannot toggle the feature on or apply a recommendation, even when they
# belong to the restaurant and the owner is allowed to enable the feature.
if set_price_adviser_enabled "$USER_C" "true" >/dev/null 2>&1; then
  echo "ERROR: staff unexpectedly enabled owner-approved price changes." >&2
  exit 1
fi
if apply_advised_menu_price "$USER_C" "$PRICE_ACTION_ID" "$PRICE_MENU_ITEM_A" "10.00" "13.33" "30.00" >/dev/null 2>&1; then
  echo "ERROR: staff unexpectedly applied an advised price." >&2
  exit 1
fi
if apply_advised_menu_price "$USER_A" "$PRICE_ACTION_ID" "$PRICE_MENU_ITEM_A" "10.00" "13.33" "30.00" >/dev/null 2>&1; then
  echo "ERROR: owner applied a price while the default-off permission was disabled." >&2
  exit 1
fi
echo "PASS: price application is disabled by default; staff cannot enable or apply it."

owner_enabled="$(set_price_adviser_enabled "$USER_A" "true")"
owner_enabled_readback="$(get_price_adviser_enabled "$USER_A")"
if [ "$owner_enabled" != "true" ] || [ "$owner_enabled_readback" != "true" ]; then
  echo "ERROR: owner opt-in could not be persisted and read back." >&2
  exit 1
fi

# Even with opt-in, the owner cannot apply against another restaurant or a stale
# price, and the client cannot choose a new price that disagrees with live cost.
if apply_advised_menu_price "$USER_A" "$PRICE_ACTION_ID" "$PRICE_MENU_ITEM_B" "10.00" "6.67" "30.00" >/dev/null 2>&1; then
  echo "ERROR: owner A unexpectedly applied a price to restaurant B's menu item." >&2
  exit 1
fi
if apply_advised_menu_price "$USER_A" "$PRICE_ACTION_ID" "$PRICE_MENU_ITEM_A" "9.99" "13.33" "30.00" >/dev/null 2>&1; then
  echo "ERROR: a stale expected price was accepted." >&2
  exit 1
fi
if apply_advised_menu_price "$USER_A" "$PRICE_ACTION_ID" "$PRICE_MENU_ITEM_A" "10.00" "13.32" "30.00" >/dev/null 2>&1; then
  echo "ERROR: a client-supplied price that differs from the server calculation was accepted." >&2
  exit 1
fi

# The UI exposes only price increases. Enforce the same rule in the database RPC,
# even when a caller supplies a mathematically valid recommendation below the live price.
if apply_advised_menu_price "$USER_A" "$PRICE_LOW_ACTION_ID" "$PRICE_MENU_ITEM_A" "10.00" "8.00" "50.00" >/dev/null 2>&1; then
  echo "ERROR: the server unexpectedly accepted a price recommendation below the current price." >&2
  exit 1
fi
price_after_rejected_decrease="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT price::text FROM public.menu_items WHERE id = '$PRICE_MENU_ITEM_A'::uuid;")"
low_price_audit_count="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*)::text FROM private.restaurant_price_adviser_audit WHERE action_id = '$PRICE_LOW_ACTION_ID'::uuid;")"
if [ "$price_after_rejected_decrease" != "10.00" ] || [ "$low_price_audit_count" != "0" ]; then
  echo "ERROR: a rejected price decrease changed the menu or wrote an audit row." >&2
  exit 1
fi
echo "PASS: server rejects price decreases without changing the menu or writing an audit row."

PRICE_APPLY_RESULT="$(apply_advised_menu_price "$USER_A" "$PRICE_ACTION_ID" "$PRICE_MENU_ITEM_A" "10.00" "13.33" "30.00")"
EXPECTED_PRICE_APPLY="$PRICE_ACTION_ID|$PRICE_MENU_ITEM_A|10.00|13.33|4.00|30.00|false"
if [ "$PRICE_APPLY_RESULT" != "$EXPECTED_PRICE_APPLY" ]; then
  echo "ERROR: server-approved price result '$PRICE_APPLY_RESULT'; expected '$EXPECTED_PRICE_APPLY'." >&2
  exit 1
fi

PRICE_REPLAY_RESULT="$(apply_advised_menu_price "$USER_A" "$PRICE_ACTION_ID" "$PRICE_MENU_ITEM_A" "10.00" "13.33" "30.00")"
EXPECTED_PRICE_REPLAY="$PRICE_ACTION_ID|$PRICE_MENU_ITEM_A|10.00|13.33|4.00|30.00|true"
if [ "$PRICE_REPLAY_RESULT" != "$EXPECTED_PRICE_REPLAY" ]; then
  echo "ERROR: retrying a committed price action was not idempotent: '$PRICE_REPLAY_RESULT'." >&2
  exit 1
fi

if apply_advised_menu_price "$USER_A" "$PRICE_ACTION_ID" "$PRICE_MENU_ITEM_A" "10.00" "13.32" "30.00" >/dev/null 2>&1; then
  echo "ERROR: an idempotency key was accepted with a different recommended price." >&2
  exit 1
fi

price_after_apply="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT price::text FROM public.menu_items WHERE id = '$PRICE_MENU_ITEM_A'::uuid;")"
price_other_restaurant="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT price::text FROM public.menu_items WHERE id = '$PRICE_MENU_ITEM_B'::uuid;")"
price_audit_count="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*)::text FROM private.restaurant_price_adviser_audit WHERE action_id = '$PRICE_ACTION_ID'::uuid;")"
if [ "$price_after_apply" != "13.33" ] || [ "$price_other_restaurant" != "10.00" ] || [ "$price_audit_count" != "1" ]; then
  echo "ERROR: price apply/replay changed an unexpected row or did not write exactly one audit row." >&2
  exit 1
fi

price_history_count="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT count(*)::text FROM public.get_restaurant_price_adviser_audit(20);
ROLLBACK;
SQL
)"
if [ "$price_history_count" != "1" ]; then
  echo "ERROR: owner price history returned '$price_history_count' rows; expected 1." >&2
  exit 1
fi

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT count(*) FROM public.get_restaurant_price_adviser_audit(20);
COMMIT;
SQL
then
  echo "ERROR: staff unexpectedly accessed owner-only price history." >&2
  exit 1
fi
if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT count(*) FROM private.restaurant_price_adviser_audit;
COMMIT;
SQL
then
  echo "ERROR: authenticated client unexpectedly queried the private price audit table directly." >&2
  exit 1
fi

owner_disabled="$(set_price_adviser_enabled "$USER_A" "false")"
disabled_readback="$(get_price_adviser_enabled "$USER_A")"
if [ "$owner_disabled" != "false" ] || [ "$disabled_readback" != "false" ]; then
  echo "ERROR: disabling price application did not take effect." >&2
  exit 1
fi
if apply_advised_menu_price "$USER_A" "$PRICE_OFF_ACTION_ID" "$PRICE_MENU_ITEM_A" "13.33" "13.33" "30.00" >/dev/null 2>&1; then
  echo "ERROR: a new price action was accepted after the owner disabled the feature." >&2
  exit 1
fi
echo "PASS: owner opt-in, per-action server recalculation, stale/cross-restaurant rejection, idempotency, private audit and off switch."

ITEM_A="$(create_inventory_item "$USER_A" "$RESTAURANT_A" "$REQUEST_A" "Flour A" "10")"
ITEM_A_RETRY="$(create_inventory_item "$USER_A" "$RESTAURANT_A" "$REQUEST_A" "Flour A" "10")"
ITEM_B="$(create_inventory_item "$USER_B" "$RESTAURANT_B" "$REQUEST_B" "Flour B" "20")"
test -n "$ITEM_A"
test "$ITEM_A" = "$ITEM_A_RETRY"
test -n "$ITEM_B"
echo "PASS: owners can create items in their own restaurants and repeated identical requests are idempotent."

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.create_inventory_item_idempotent(
  'cccccccc-cccc-4ccc-8ccc-cccccccccccc'::uuid, '$RESTAURANT_B'::uuid,
  'Unauthorized item', 'kg', 1, 0, 'should be rejected'
);
COMMIT;
SQL
then
  echo "ERROR: user A unexpectedly created stock inside restaurant B." >&2
  exit 1
fi
echo "PASS: cross-restaurant stock creation is rejected."

foreign_rows="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT count(*) FROM public.restaurant_inventory_items WHERE restaurant_id = '$RESTAURANT_B'::uuid;
ROLLBACK;
SQL
)"
if [ "$foreign_rows" != "0" ]; then
  echo "ERROR: user A could read $foreign_rows stock rows belonging to restaurant B." >&2
  exit 1
fi
echo "PASS: row-level security hides restaurant B stock from restaurant A."

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 -c "SET ROLE anon; SELECT count(*) FROM public.restaurant_inventory_items;" >/dev/null 2>&1; then
  echo "ERROR: anon unexpectedly has direct SELECT access to stock." >&2
  exit 1
fi
echo "PASS: anon cannot directly SELECT stock."

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.create_inventory_item_idempotent(
  '$REQUEST_A'::uuid, '$RESTAURANT_A'::uuid, 'Different payload', 'kg', 10, 1, 'CI isolation test'
);
COMMIT;
SQL
then
  echo "ERROR: the same idempotency key was accepted with a different payload." >&2
  exit 1
fi
echo "PASS: changing a payload while reusing its idempotency key is rejected."

# A retried stock adjustment must not mutate the quantity a second time, and
# the idempotency key must not be reusable with different adjustment data.
STOCK_ADJUST_REQUEST="eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"
STOCK_FOREIGN_REQUEST="ffffffff-ffff-4fff-8fff-ffffffffffff"
ADJUSTMENT_FIRST="$(adjust_inventory_stock "$USER_A" "$ITEM_A" "$STOCK_ADJUST_REQUEST" "-2" "CI retry test")"
ADJUSTMENT_RETRY="$(adjust_inventory_stock "$USER_A" "$ITEM_A" "$STOCK_ADJUST_REQUEST" "-2" "CI retry test")"
if [ "$ADJUSTMENT_FIRST" != "$ADJUSTMENT_RETRY" ]; then
  echo "ERROR: retrying the same stock adjustment returned a different quantity." >&2
  exit 1
fi

stock_quantity_ok="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT quantity = 8 FROM public.restaurant_inventory_items WHERE id = '$ITEM_A'::uuid;")"
movement_count_ok="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*) = 1 FROM public.restaurant_inventory_movements WHERE created_by = '$USER_A'::uuid AND client_request_id = '$STOCK_ADJUST_REQUEST'::uuid;")"
if [ "$stock_quantity_ok" != "t" ] || [ "$movement_count_ok" != "t" ]; then
  echo "ERROR: repeated stock adjustment was applied more than once." >&2
  exit 1
fi
echo "PASS: repeated stock adjustment is idempotent and records exactly one movement."

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT * FROM public.adjust_inventory_stock_idempotent(
  '$STOCK_FOREIGN_REQUEST'::uuid, '$ITEM_B'::uuid, 1, 'Unauthorized adjustment'
);
COMMIT;
SQL
then
  echo "ERROR: user A unexpectedly adjusted stock belonging to restaurant B." >&2
  exit 1
fi
if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT * FROM public.adjust_inventory_stock_idempotent(
  '$STOCK_ADJUST_REQUEST'::uuid, '$ITEM_A'::uuid, -3, 'CI retry test'
);
COMMIT;
SQL
then
  echo "ERROR: the same stock idempotency key was accepted with a different delta." >&2
  exit 1
fi
echo "PASS: cross-restaurant stock adjustments and changed idempotency payloads are rejected."

# Cost-aware stock creation, cost history and waste valuation snapshot tests.
COST_ITEM_REQUEST="12121212-1212-4121-8121-121212121212"
COST_WASTE_REQUEST_OLD="13131313-1313-4131-8131-131313131313"
COST_WASTE_REQUEST_NEW="14141414-1414-4141-8141-141414141414"

COST_ITEM="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.create_inventory_item_idempotent_with_cost(
  '$COST_ITEM_REQUEST'::uuid, '$RESTAURANT_A'::uuid,
  'Flour with cost', 'kg', 10.000::numeric, 1.000::numeric,
  'Initial purchase', 12.3456::numeric
);
COMMIT;
SQL
)"
COST_ITEM_RETRY="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.create_inventory_item_idempotent_with_cost(
  '$COST_ITEM_REQUEST'::uuid, '$RESTAURANT_A'::uuid,
  'Flour with cost', 'kg', 10.000::numeric, 1.000::numeric,
  'Initial purchase', 12.3456::numeric
);
COMMIT;
SQL
)"
if [ -z "$COST_ITEM" ] || [ "$COST_ITEM" != "$COST_ITEM_RETRY" ]; then
  echo "ERROR: cost-aware item creation did not return the same item on an identical retry." >&2
  exit 1
fi

# First waste movement snapshots the initial purchase cost.
psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT * FROM public.adjust_inventory_stock_idempotent(
  '$COST_WASTE_REQUEST_OLD'::uuid, '$COST_ITEM'::uuid,
  -0.250::numeric, 'Desperdício · Deterioração'
);
COMMIT;
SQL

# Change the current cost without changing quantity: the first movement must
# retain its original price snapshot.
psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.save_inventory_item_with_cost(
  '$RESTAURANT_A'::uuid, '$COST_ITEM'::uuid,
  'Flour with cost', 'kg', 9.750::numeric, 1.000::numeric,
  'Supplier price update', 20.0000::numeric
);
COMMIT;
SQL

# Second waste movement must snapshot the new cost, without rewriting the first.
psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT * FROM public.adjust_inventory_stock_idempotent(
  '$COST_WASTE_REQUEST_NEW'::uuid, '$COST_ITEM'::uuid,
  -0.250::numeric, 'Desperdício · Deterioração'
);
COMMIT;
SQL

# Change the live stock unit only after movements exist. Historical movements
# must retain the unit used when each quantity was recorded.
psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.save_inventory_item_with_cost(
  '$RESTAURANT_A'::uuid, '$COST_ITEM'::uuid,
  'Flour with cost', 'g', 9.500::numeric, 1.000::numeric,
  'Unit change after historical losses', 20.0000::numeric
);
COMMIT;
SQL

cost_snapshot_history="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT string_agg(unit_cost_at_movement::numeric(12,4)::text, ',' ORDER BY created_at) FROM public.restaurant_inventory_movements WHERE item_id = '$COST_ITEM'::uuid AND note = 'Desperdício · Deterioração';")"
unit_snapshot_history="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT string_agg(unit_at_movement, ',' ORDER BY created_at) FROM public.restaurant_inventory_movements WHERE item_id = '$COST_ITEM'::uuid AND note = 'Desperdício · Deterioração';")"
current_cost_and_quantity="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT unit_cost_per_unit::numeric(12,4)::text || '|' || quantity::numeric(12,3)::text || '|' || unit FROM public.restaurant_inventory_items WHERE id = '$COST_ITEM'::uuid;")"
if [ "$cost_snapshot_history" != "12.3456,20.0000" ] || \
   [ "$unit_snapshot_history" != "kg,kg" ] || \
   [ "$current_cost_and_quantity" != "20.0000|9.500|g" ]; then
  echo "ERROR: waste movements did not preserve the historical cost and unit after the current stock unit changed." >&2
  printf 'Cost snapshots: %s; unit snapshots: %s; current item: %s\n' "$cost_snapshot_history" "$unit_snapshot_history" "$current_cost_and_quantity" >&2
  exit 1
fi

# Same idempotency key cannot be reused with a different purchase cost.
if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.create_inventory_item_idempotent_with_cost(
  '$COST_ITEM_REQUEST'::uuid, '$RESTAURANT_A'::uuid,
  'Flour with cost', 'kg', 10.000::numeric, 1.000::numeric,
  'Initial purchase', 13.0000::numeric
);
COMMIT;
SQL
then
  echo "ERROR: cost-aware creation idempotency accepted a changed unit cost." >&2
  exit 1
fi

# Cost-aware functions must keep the same restaurant boundary as legacy stock RPCs.
if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.save_inventory_item_with_cost(
  '$RESTAURANT_B'::uuid, '$ITEM_B'::uuid,
  'Flour B', 'kg', 20.000::numeric, 1.000::numeric,
  'Unauthorized cost change', 99.0000::numeric
);
COMMIT;
SQL
then
  echo "ERROR: owner A unexpectedly changed a purchase cost in restaurant B." >&2
  exit 1
fi

# Invalid precision must be rejected by PostgreSQL, not rounded silently.
if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.save_inventory_item_with_cost(
  '$RESTAURANT_A'::uuid, '$COST_ITEM'::uuid,
  'Flour with cost', 'kg', 9.500::numeric, 1.000::numeric,
  'Invalid precision', 1.23456::numeric
);
COMMIT;
SQL
then
  echo "ERROR: server accepted a unit cost with more than four decimal places." >&2
  exit 1
fi

# The uncosted legacy item stays explicitly unknown; no historical price is fabricated.
legacy_cost_is_null="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT unit_cost_per_unit IS NULL FROM public.restaurant_inventory_items WHERE id = '$ITEM_A'::uuid;")"
legacy_waste_costs_unknown="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*) = 0 FROM public.restaurant_inventory_movements WHERE item_id = '$ITEM_A'::uuid AND unit_cost_at_movement IS NOT NULL;")"
if [ "$legacy_cost_is_null" != "t" ] || [ "$legacy_waste_costs_unknown" != "t" ]; then
  echo "ERROR: legacy stock without a purchase cost was assigned an invented historical cost." >&2
  exit 1
fi

echo "PASS: purchase cost creation is idempotent, waste snapshots preserve each movement price, invalid/cross-restaurant cost changes are rejected, and legacy costs remain unknown."


# Customer-card, loyalty and reservation authorization checks use separate synthetic
# users for restaurants A/B. All records are created only in this disposable database.
MEMBER_A="$(join_customer "$USER_E" "SMOKEAA001" "CI Customer A")"
MEMBER_B="$(join_customer "$USER_F" "SMOKEBB001" "CI Customer B")"
test -n "$MEMBER_A"
test -n "$MEMBER_B"
test "$MEMBER_A" != "$MEMBER_B"

own_menu_rows="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_E', true);
SET LOCAL ROLE authenticated;
SELECT count(*) FROM public.get_member_restaurant_menu('$MEMBER_A'::uuid);
ROLLBACK;
SQL
)"
test -n "$own_menu_rows"

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_E', true);
SET LOCAL ROLE authenticated;
SELECT * FROM public.get_member_restaurant_menu('$MEMBER_B'::uuid);
COMMIT;
SQL
then
  echo "ERROR: customer A unexpectedly read the menu via customer B's loyalty card." >&2
  exit 1
fi
echo "PASS: a customer can query only the menu associated with their own loyalty card."

LOYALTY_REQUEST="77777777-7777-4777-8777-777777777777"
LOYALTY_FOREIGN_REQUEST="88888888-8888-4888-8888-888888888888"
LOYALTY_FIRST="$(adjust_loyalty_points "$USER_A" "$MEMBER_A" "$LOYALTY_REQUEST" "10" "CI loyalty retry")"
LOYALTY_RETRY="$(adjust_loyalty_points "$USER_A" "$MEMBER_A" "$LOYALTY_REQUEST" "10" "CI loyalty retry")"
if [ "$LOYALTY_FIRST" != "$LOYALTY_RETRY" ]; then
  echo "ERROR: repeating a loyalty-points request returned a different balance." >&2
  exit 1
fi
loyalty_balance_ok="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT points = 10 FROM public.loyalty_members WHERE id = '$MEMBER_A'::uuid;")"
loyalty_transaction_ok="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*) = 1 FROM public.loyalty_transactions WHERE created_by = '$USER_A'::uuid AND client_request_id = '$LOYALTY_REQUEST'::uuid;")"
if [ "$loyalty_balance_ok" != "t" ] || [ "$loyalty_transaction_ok" != "t" ]; then
  echo "ERROR: repeated loyalty adjustment changed the balance twice or recorded duplicate transactions." >&2
  exit 1
fi
echo "PASS: repeated loyalty-point adjustments are idempotent and create one transaction."

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT * FROM public.adjust_loyalty_points_idempotent(
  '$LOYALTY_FOREIGN_REQUEST'::uuid, '$MEMBER_B'::uuid, 10, 'Cross-restaurant attempt'
);
COMMIT;
SQL
then
  echo "ERROR: owner A unexpectedly adjusted points for a member of restaurant B." >&2
  exit 1
fi
if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT * FROM public.adjust_loyalty_points_idempotent(
  '$LOYALTY_REQUEST'::uuid, '$MEMBER_A'::uuid, -5, 'CI loyalty retry'
);
COMMIT;
SQL
then
  echo "ERROR: loyalty idempotency key was accepted with a changed delta." >&2
  exit 1
fi
echo "PASS: cross-restaurant loyalty changes and changed idempotency payloads are rejected."

RESERVATION_REQUEST="99999999-9999-4999-8999-999999999999"
RESERVATION_FOREIGN_REQUEST="abababab-abab-4bab-8bab-abababababab"
RESERVATION_AT="$(date -u -d '+30 days' '+%Y-%m-%d %H:%M:%S+00:00')"
RESERVATION_A="$(create_restaurant_reservation "$USER_A" "$RESTAURANT_A" "$RESERVATION_REQUEST" "CI Guest" "$RESERVATION_AT")"
RESERVATION_RETRY="$(create_restaurant_reservation "$USER_A" "$RESTAURANT_A" "$RESERVATION_REQUEST" "CI Guest" "$RESERVATION_AT")"
if [ -z "$RESERVATION_A" ] || [ "$RESERVATION_A" != "$RESERVATION_RETRY" ]; then
  echo "ERROR: retrying the same reservation request did not return the original reservation." >&2
  exit 1
fi
reservation_count_ok="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*) = 1 FROM public.restaurant_reservations WHERE id = '$RESERVATION_A'::uuid AND restaurant_id = '$RESTAURANT_A'::uuid AND created_by = '$USER_A'::uuid AND client_request_id = '$RESERVATION_REQUEST'::uuid;")"
if [ "$reservation_count_ok" != "t" ]; then
  echo "ERROR: repeated reservation creation produced a duplicate or misplaced reservation." >&2
  exit 1
fi
echo "PASS: repeated reservation creation returns the original reservation exactly once."

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.create_restaurant_reservation_idempotent(
  '$RESERVATION_FOREIGN_REQUEST'::uuid, '$RESTAURANT_B'::uuid, 'Unauthorized Guest', '',
  2, '$RESERVATION_AT'::timestamptz, 60, NULL::integer, 'Should be rejected'
);
COMMIT;
SQL
then
  echo "ERROR: user A unexpectedly created a reservation for restaurant B." >&2
  exit 1
fi

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_B', true);
SET LOCAL ROLE authenticated;
SELECT public.update_restaurant_reservation_status('$RESERVATION_A'::uuid, 'Confirmada');
COMMIT;
SQL
then
  echo "ERROR: user B unexpectedly changed a reservation belonging to restaurant A." >&2
  exit 1
fi
psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.update_restaurant_reservation_status('$RESERVATION_A'::uuid, 'Confirmada');
SELECT public.update_restaurant_reservation_status('$RESERVATION_A'::uuid, 'Confirmada');
COMMIT;
SQL
reservation_status_ok="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT status = 'Confirmada' FROM public.restaurant_reservations WHERE id = '$RESERVATION_A'::uuid;")"
if [ "$reservation_status_ok" != "t" ]; then
  echo "ERROR: owner A could not confirm their reservation or retry the same status." >&2
  exit 1
fi
echo "PASS: reservation status changes are idempotent and cross-restaurant updates are rejected."


expect_prepare_account_deletion_rejection() {
  local user_id="$1"
  local delete_restaurant_flag="$2"
  local expected_fragment="$3"
  local description="$4"
  local output

  if output="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 2>&1 <<SQL
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
SELECT public.prepare_account_deletion($delete_restaurant_flag);
COMMIT;
SQL
)"; then
    echo "ERROR: $description unexpectedly succeeded." >&2
    exit 1
  fi

  if ! grep -Fqi -- "$expected_fragment" <<<"$output"; then
    echo "ERROR: $description failed for an unexpected reason." >&2
    printf '%s\n' "$output" >&2
    exit 1
  fi
  echo "PASS: $description."
}

add_staff_member() {
  local user_id="$1"
  local restaurant_id="$2"
  local staff_code

  staff_code="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
    -c "SELECT staff_code FROM private.restaurant_join_codes WHERE restaurant_id = '$restaurant_id'::uuid;")"
  test -n "$staff_code"

  psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
SELECT * FROM public.join_restaurant_staff('$staff_code');
COMMIT;
SQL
}


# Recipe and order-to-stock contracts run only in this disposable PostgreSQL database.
# They verify tenant isolation, owner-controlled activation, atomic deductions,
# conservative three-decimal rounding, idempotency and legacy-order compatibility.
RECIPE_ITEM_C_REQUEST="dddddddd-dddd-4ddd-8ddd-dddddddddddd"
RECIPE_ITEM_D_REQUEST="cdcdcdcd-cdcd-4dcd-8dcd-cdcdcdcdcdcd"
RECIPE_ORDER_ID="eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee1"
RECIPE_INSUFFICIENT_ORDER_ID="eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee2"
RECIPE_CANCELLED_ORDER_ID="eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee3"
RECIPE_LEGACY_ORDER_ID="eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee4"
RECIPE_REVIEW_REQUIRED_ORDER_ID="eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee5"
RECIPE_UNIT_CHANGE_ORDER_ID="eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee6"
RECIPE_DELETE_CHANGE_ORDER_ID="eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee7"
RECIPE_INITIAL_PREPARING_ORDER_ID="eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee8"
RECIPE_UNLINK_ORDER_ID="eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee9"

RECIPE_ITEM_C="$(create_inventory_item "$USER_A" "$RESTAURANT_A" "$RECIPE_ITEM_C_REQUEST" "CI Butter A" "5")"
RECIPE_ITEM_D="$(create_inventory_item "$USER_A" "$RESTAURANT_A" "$RECIPE_ITEM_D_REQUEST" "CI Cream A" "5")"
test -n "$RECIPE_ITEM_C"
test -n "$RECIPE_ITEM_D"

save_recipe_ingredient() {
  local user_id="$1" restaurant_id="$2" menu_item_id="$3" inventory_item_id="$4"
  local quantity="$5" waste="$6"
  psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
SELECT public.save_restaurant_recipe_ingredient(
  '$restaurant_id'::uuid, '$menu_item_id'::uuid, '$inventory_item_id'::uuid,
  '$quantity'::numeric, '$waste'::numeric
);
COMMIT;
SQL
}

set_recipe_active() {
  local user_id="$1" restaurant_id="$2" menu_item_id="$3" enabled="$4"
  psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
SELECT public.set_restaurant_recipe_active(
  '$restaurant_id'::uuid, '$menu_item_id'::uuid, '$enabled'::boolean
)::text;
COMMIT;
SQL
}

create_recipe_order() {
  local user_id="$1" order_id="$2" menu_item_id="$3" quantity="$4"
  local status="${5:-Aberto}"
  psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
INSERT INTO public.restaurant_orders (
  id, restaurant_id, table_number, item_name, amount, status,
  quantity, service_course, notes, menu_item_id
) VALUES (
  '$order_id'::uuid, '$RESTAURANT_A'::uuid, 1, 'CI Price Dish A',
  13.33, '$status', '$quantity'::integer, 'Principal', '', '$menu_item_id'::uuid
);
COMMIT;
SQL
}

set_recipe_order_status() {
  local user_id="$1" order_id="$2" status="$3"
  psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
UPDATE public.restaurant_orders SET status = '$status'
WHERE id = '$order_id'::uuid;
COMMIT;
SQL
}

# Recipe writes are scoped to the restaurant. Saving lines does not activate
# consumption; only the restaurant owner can enable a completed recipe.
RECIPE_ID_A="$(save_recipe_ingredient "$USER_A" "$RESTAURANT_A" "$PRICE_MENU_ITEM_A" "$ITEM_A" "0.300" "5.00")"
RECIPE_ID_A_RETRY="$(save_recipe_ingredient "$USER_A" "$RESTAURANT_A" "$PRICE_MENU_ITEM_A" "$ITEM_A" "0.300" "5.00")"
if [ -z "$RECIPE_ID_A" ] || [ "$RECIPE_ID_A" != "$RECIPE_ID_A_RETRY" ]; then
  echo "ERROR: recipe ingredient retry did not resolve to the same recipe." >&2
  exit 1
fi
save_recipe_ingredient "$USER_A" "$RESTAURANT_A" "$PRICE_MENU_ITEM_A" "$RECIPE_ITEM_C" "0.100" "0.00" >/dev/null
recipe_default_active="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1   -c "SELECT is_active::text FROM public.restaurant_recipes WHERE id = '$RECIPE_ID_A'::uuid;")"
if [ "$recipe_default_active" != "false" ]; then
  echo "ERROR: recipe consumption was not disabled by default." >&2
  exit 1
fi

if set_recipe_active "$USER_C" "$RESTAURANT_A" "$PRICE_MENU_ITEM_A" "true" >/dev/null 2>&1; then
  echo "ERROR: staff unexpectedly activated automatic ingredient consumption." >&2
  exit 1
fi
if save_recipe_ingredient "$USER_A" "$RESTAURANT_B" "$PRICE_MENU_ITEM_B" "$ITEM_B" "0.100" "0" >/dev/null 2>&1; then
  echo "ERROR: owner A unexpectedly wrote a recipe in restaurant B." >&2
  exit 1
fi
foreign_recipe_rows="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_B', true);
SET LOCAL ROLE authenticated;
SELECT count(*)::text FROM public.restaurant_recipes WHERE restaurant_id = '$RESTAURANT_A'::uuid;
ROLLBACK;
SQL
)"
if [ "$foreign_recipe_rows" != "0" ]; then
  echo "ERROR: restaurant B could read restaurant A recipe rows." >&2
  exit 1
fi
if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
INSERT INTO public.restaurant_recipes (restaurant_id, menu_item_id)
VALUES ('$RESTAURANT_A'::uuid, '$PRICE_MENU_ITEM_A'::uuid);
COMMIT;
SQL
then
  echo "ERROR: authenticated client unexpectedly wrote directly to recipe tables." >&2
  exit 1
fi

owner_recipe_activated="$(set_recipe_active "$USER_A" "$RESTAURANT_A" "$PRICE_MENU_ITEM_A" "true")"
if [ "$owner_recipe_activated" != "true" ]; then
  echo "ERROR: restaurant owner could not activate a configured recipe." >&2
  exit 1
fi

# A client cannot skip the Open state, detach a linked order or skip the
# preparation stage to avoid the automatic recipe-stock trigger.
if create_recipe_order "$USER_A" "$RECIPE_INITIAL_PREPARING_ORDER_ID" "$PRICE_MENU_ITEM_A" "1" "Em preparação" >/dev/null 2>&1; then
  echo "ERROR: a new order was accepted directly in preparation." >&2
  exit 1
fi
premature_order_count="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*)::text FROM public.restaurant_orders WHERE id = '$RECIPE_INITIAL_PREPARING_ORDER_ID'::uuid;")"
if [ "$premature_order_count" != "0" ]; then
  echo "ERROR: a rejected initial-preparation order was not rolled back." >&2
  exit 1
fi

create_recipe_order "$USER_A" "$RECIPE_UNLINK_ORDER_ID" "$PRICE_MENU_ITEM_A" "1"
if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
UPDATE public.restaurant_orders SET menu_item_id = NULL
WHERE id = '$RECIPE_UNLINK_ORDER_ID'::uuid;
COMMIT;
SQL
then
  echo "ERROR: a client unexpectedly detached a linked order from its menu item." >&2
  exit 1
fi
if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
UPDATE public.restaurant_orders
SET menu_item_id = NULL, status = 'Em preparação'
WHERE id = '$RECIPE_UNLINK_ORDER_ID'::uuid;
COMMIT;
SQL
then
  echo "ERROR: a client detached a linked order while advancing it to preparation." >&2
  exit 1
fi
if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
UPDATE public.restaurant_orders SET status = 'Pronto'
WHERE id = '$RECIPE_UNLINK_ORDER_ID'::uuid;
COMMIT;
SQL
then
  echo "ERROR: a client skipped the preparation stage." >&2
  exit 1
fi
recipe_bypass_state="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT status || '|' || menu_item_id::text || '|' || (SELECT count(*)::text FROM public.restaurant_inventory_movements WHERE source_order_id = '$RECIPE_UNLINK_ORDER_ID'::uuid) FROM public.restaurant_orders WHERE id = '$RECIPE_UNLINK_ORDER_ID'::uuid;")"
if [ "$recipe_bypass_state" != "Aberto|$PRICE_MENU_ITEM_A|0" ]; then
  echo "ERROR: a rejected order-bypass attempt changed the order or consumed stock." >&2
  printf 'State: %s\n' "$recipe_bypass_state" >&2
  exit 1
fi
echo "PASS: new orders must start open; linked menu identity cannot be cleared by clients; invalid stage jumps are rejected."

# An ingredient edit by a staff member invalidates the owner's previous approval;
# reactivation must be explicit and owner-only.
save_recipe_ingredient "$USER_C" "$RESTAURANT_A" "$PRICE_MENU_ITEM_A" "$ITEM_A" "0.300" "5.00" >/dev/null
recipe_after_staff_edit="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT is_active::text FROM public.restaurant_recipes WHERE id = '$RECIPE_ID_A'::uuid;")"
if [ "$recipe_after_staff_edit" != "false" ]; then
  echo "ERROR: changing ingredients unexpectedly retained owner approval." >&2
  exit 1
fi
staff_edit_review_required="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT requires_review::text FROM public.restaurant_recipes WHERE id = '$RECIPE_ID_A'::uuid;")"
if [ "$staff_edit_review_required" != "true" ]; then
  echo "ERROR: editing an active recipe did not record a mandatory owner review." >&2
  exit 1
fi
create_recipe_order "$USER_A" "$RECIPE_REVIEW_REQUIRED_ORDER_ID" "$PRICE_MENU_ITEM_A" "1"
if set_recipe_order_status "$USER_A" "$RECIPE_REVIEW_REQUIRED_ORDER_ID" "Em preparação" >/dev/null 2>&1; then
  echo "ERROR: a ticket entered preparation while its previously active recipe required review." >&2
  exit 1
fi
review_required_order_status="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT status FROM public.restaurant_orders WHERE id = '$RECIPE_REVIEW_REQUIRED_ORDER_ID'::uuid;")"
review_required_order_movements="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*)::text FROM public.restaurant_inventory_movements WHERE source_order_id = '$RECIPE_REVIEW_REQUIRED_ORDER_ID'::uuid;")"
if [ "$review_required_order_status" != "Aberto" ] || [ "$review_required_order_movements" != "0" ]; then
  echo "ERROR: a recipe needing review changed the order or stock before approval." >&2
  exit 1
fi
if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.set_restaurant_recipe_active('$RESTAURANT_A'::uuid, '$PRICE_MENU_ITEM_A'::uuid, NULL::boolean);
COMMIT;
SQL
then
  echo "ERROR: a NULL recipe activation value was accepted." >&2
  exit 1
fi
owner_recipe_reactivated="$(set_recipe_active "$USER_A" "$RESTAURANT_A" "$PRICE_MENU_ITEM_A" "true")"
if [ "$owner_recipe_reactivated" != "true" ]; then
  echo "ERROR: restaurant owner could not reapprove the edited recipe." >&2
  exit 1
fi

# Changing a stock unit must invalidate approval and block reactivation until
# the owner explicitly saves that ingredient line again with the new unit.
psql --no-psqlrc --set ON_ERROR_STOP=1 -c \
  "UPDATE public.restaurant_inventory_items SET unit = 'g' WHERE id = '$RECIPE_ITEM_C'::uuid;" >/dev/null
unit_change_active="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT is_active::text FROM public.restaurant_recipes WHERE id = '$RECIPE_ID_A'::uuid;")"
if [ "$unit_change_active" != "false" ]; then
  echo "ERROR: changing an ingredient unit retained recipe approval." >&2
  exit 1
fi
unit_change_review_required="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT requires_review::text FROM public.restaurant_recipes WHERE id = '$RECIPE_ID_A'::uuid;")"
if [ "$unit_change_review_required" != "true" ]; then
  echo "ERROR: changing a previously approved ingredient unit did not require owner review." >&2
  exit 1
fi
create_recipe_order "$USER_A" "$RECIPE_UNIT_CHANGE_ORDER_ID" "$PRICE_MENU_ITEM_A" "1"
if set_recipe_order_status "$USER_A" "$RECIPE_UNIT_CHANGE_ORDER_ID" "Em preparação" >/dev/null 2>&1; then
  echo "ERROR: an order entered preparation after an approved recipe's unit changed." >&2
  exit 1
fi
if set_recipe_active "$USER_A" "$RESTAURANT_A" "$PRICE_MENU_ITEM_A" "true" >/dev/null 2>&1; then
  echo "ERROR: a recipe with a stale unit snapshot was reactivated." >&2
  exit 1
fi
save_recipe_ingredient "$USER_A" "$RESTAURANT_A" "$PRICE_MENU_ITEM_A" "$RECIPE_ITEM_C" "0.100" "0.00" >/dev/null
if [ "$(set_recipe_active "$USER_A" "$RESTAURANT_A" "$PRICE_MENU_ITEM_A" "true")" != "true" ]; then
  echo "ERROR: owner could not reapprove a recipe after explicitly refreshing its unit snapshot." >&2
  exit 1
fi

# Restoring the old unit is another unit change and must invalidate approval.
psql --no-psqlrc --set ON_ERROR_STOP=1 -c \
  "UPDATE public.restaurant_inventory_items SET unit = 'kg' WHERE id = '$RECIPE_ITEM_C'::uuid;" >/dev/null
if set_recipe_active "$USER_A" "$RESTAURANT_A" "$PRICE_MENU_ITEM_A" "true" >/dev/null 2>&1; then
  echo "ERROR: a recipe was reactivated after its ingredient unit changed back without review." >&2
  exit 1
fi
save_recipe_ingredient "$USER_A" "$RESTAURANT_A" "$PRICE_MENU_ITEM_A" "$RECIPE_ITEM_C" "0.100" "0.00" >/dev/null
if [ "$(set_recipe_active "$USER_A" "$RESTAURANT_A" "$PRICE_MENU_ITEM_A" "true")" != "true" ]; then
  echo "ERROR: owner could not reapprove a recipe after refreshing the restored unit." >&2
  exit 1
fi

# Removing an ingredient from inventory cascades its recipe line, but must
# invalidate the remaining recipe until the owner reviews and reapproves it.
save_recipe_ingredient "$USER_A" "$RESTAURANT_A" "$PRICE_MENU_ITEM_A" "$RECIPE_ITEM_D" "0.050" "0.00" >/dev/null
if [ "$(set_recipe_active "$USER_A" "$RESTAURANT_A" "$PRICE_MENU_ITEM_A" "true")" != "true" ]; then
  echo "ERROR: owner could not activate a recipe after adding a second test ingredient." >&2
  exit 1
fi
psql --no-psqlrc --set ON_ERROR_STOP=1 -c \
  "DELETE FROM public.restaurant_inventory_items WHERE id = '$RECIPE_ITEM_D'::uuid;" >/dev/null
delete_change_active="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT is_active::text FROM public.restaurant_recipes WHERE id = '$RECIPE_ID_A'::uuid;")"
deleted_recipe_line_count="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*)::text FROM public.restaurant_recipe_ingredients WHERE recipe_id = '$RECIPE_ID_A'::uuid AND inventory_item_id = '$RECIPE_ITEM_D'::uuid;")"
if [ "$delete_change_active" != "false" ] || [ "$deleted_recipe_line_count" != "0" ]; then
  echo "ERROR: deleting a stock ingredient did not invalidate the recipe and remove its cascaded line." >&2
  exit 1
fi
delete_change_review_required="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT requires_review::text FROM public.restaurant_recipes WHERE id = '$RECIPE_ID_A'::uuid;")"
if [ "$delete_change_review_required" != "true" ]; then
  echo "ERROR: deleting an ingredient from an approved recipe did not require owner review." >&2
  exit 1
fi
create_recipe_order "$USER_A" "$RECIPE_DELETE_CHANGE_ORDER_ID" "$PRICE_MENU_ITEM_A" "1"
if set_recipe_order_status "$USER_A" "$RECIPE_DELETE_CHANGE_ORDER_ID" "Em preparação" >/dev/null 2>&1; then
  echo "ERROR: an order entered preparation after a recipe ingredient was deleted." >&2
  exit 1
fi
if [ "$(set_recipe_active "$USER_A" "$RESTAURANT_A" "$PRICE_MENU_ITEM_A" "true")" != "true" ]; then
  echo "ERROR: owner could not explicitly reapprove the recipe after reviewing a deleted ingredient." >&2
  exit 1
fi

echo "PASS: recipe lines are tenant-isolated and RPC-only; only the owner can activate; staff edits, unit changes and ingredient deletion require explicit review."

# One two-portion order consumes 0.632 kg of ingredient A (0.300 kg per portion,
# 5% preparation waste, rounded up to 0.001 kg) and 0.200 kg of ingredient C.
# Give ingredient A a known purchase cost so the cost-snapshot trigger is
# verified for automatic recipe-consumption movements.
psql --no-psqlrc --set ON_ERROR_STOP=1 -c \
  "UPDATE public.restaurant_inventory_items SET unit_cost_per_unit = 3.5000 WHERE id = '$ITEM_A'::uuid;" >/dev/null
create_recipe_order "$USER_A" "$RECIPE_ORDER_ID" "$PRICE_MENU_ITEM_A" "2"
set_recipe_order_status "$USER_A" "$RECIPE_ORDER_ID" "Em preparação"

recipe_stock_a="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1   -c "SELECT quantity::text FROM public.restaurant_inventory_items WHERE id = '$ITEM_A'::uuid;")"
recipe_stock_c="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1   -c "SELECT quantity::text FROM public.restaurant_inventory_items WHERE id = '$RECIPE_ITEM_C'::uuid;")"
recipe_movement_count="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1   -c "SELECT count(*)::text FROM public.restaurant_inventory_movements WHERE source_order_id = '$RECIPE_ORDER_ID'::uuid;")"
if [ "$recipe_stock_a" != "7.368" ] || [ "$recipe_stock_c" != "4.800" ] || [ "$recipe_movement_count" != "2" ]; then
  echo "ERROR: recipe preparation did not deduct all ingredients using conservative rounding." >&2
  printf 'Stock A=%s, stock C=%s, movements=%s\n' "$recipe_stock_a" "$recipe_stock_c" "$recipe_movement_count" >&2
  exit 1
fi
recipe_movement_cost_a="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT unit_cost_at_movement::text FROM public.restaurant_inventory_movements WHERE source_order_id = '$RECIPE_ORDER_ID'::uuid AND item_id = '$ITEM_A'::uuid;")"
if [ "$recipe_movement_cost_a" != "3.5000" ]; then
  echo "ERROR: recipe consumption did not snapshot the current ingredient purchase cost." >&2
  exit 1
fi
if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
UPDATE public.restaurant_orders SET quantity = 3
WHERE id = '$RECIPE_ORDER_ID'::uuid;
COMMIT;
SQL
then
  echo "ERROR: a client changed order quantity after recipe stock had been consumed." >&2
  exit 1
fi
recipe_quantity_after_rejected_edit="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT quantity::text FROM public.restaurant_orders WHERE id = '$RECIPE_ORDER_ID'::uuid;")"
if [ "$recipe_quantity_after_rejected_edit" != "2" ]; then
  echo "ERROR: rejected quantity edit changed the historical number of portions." >&2
  exit 1
fi

# Reopening and preparing the same ticket again must not deduct the ingredients twice.
set_recipe_order_status "$USER_A" "$RECIPE_ORDER_ID" "Pronto"
set_recipe_order_status "$USER_A" "$RECIPE_ORDER_ID" "Aberto"
set_recipe_order_status "$USER_A" "$RECIPE_ORDER_ID" "Em preparação"
recipe_replay_count="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1   -c "SELECT count(*)::text FROM public.restaurant_inventory_movements WHERE source_order_id = '$RECIPE_ORDER_ID'::uuid;")"
recipe_stock_a_after_replay="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1   -c "SELECT quantity::text FROM public.restaurant_inventory_items WHERE id = '$ITEM_A'::uuid;")"
if [ "$recipe_replay_count" != "2" ] || [ "$recipe_stock_a_after_replay" != "7.368" ]; then
  echo "ERROR: reopening a consumed order deducted its recipe twice." >&2
  exit 1
fi
# A later purchase-cost edit must not rewrite the historical cost of this
# automatically consumed ingredient.
psql --no-psqlrc --set ON_ERROR_STOP=1 -c \
  "UPDATE public.restaurant_inventory_items SET unit_cost_per_unit = 4.2500 WHERE id = '$ITEM_A'::uuid;" >/dev/null
recipe_historical_cost_a="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT unit_cost_at_movement::text FROM public.restaurant_inventory_movements WHERE source_order_id = '$RECIPE_ORDER_ID'::uuid AND item_id = '$ITEM_A'::uuid;")"
if [ "$recipe_historical_cost_a" != "3.5000" ]; then
  echo "ERROR: changing the current purchase cost rewrote the historical recipe-consumption cost." >&2
  exit 1
fi
echo "PASS: recipe preparation consumes stock atomically, snapshots ingredient cost and cannot deduct/revalue the same ticket twice."

# An insufficient ingredient rejects the whole status transition; no earlier
# ingredient deduction or partial movement survives the failed transaction.
create_recipe_order "$USER_A" "$RECIPE_INSUFFICIENT_ORDER_ID" "$PRICE_MENU_ITEM_A" "99"
if set_recipe_order_status "$USER_A" "$RECIPE_INSUFFICIENT_ORDER_ID" "Em preparação" >/dev/null 2>&1; then
  echo "ERROR: order preparation unexpectedly succeeded with insufficient stock." >&2
  exit 1
fi
insufficient_status="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1   -c "SELECT status FROM public.restaurant_orders WHERE id = '$RECIPE_INSUFFICIENT_ORDER_ID'::uuid;")"
insufficient_movements="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1   -c "SELECT count(*)::text FROM public.restaurant_inventory_movements WHERE source_order_id = '$RECIPE_INSUFFICIENT_ORDER_ID'::uuid;")"
insufficient_stock_a="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1   -c "SELECT quantity::text FROM public.restaurant_inventory_items WHERE id = '$ITEM_A'::uuid;")"
insufficient_stock_c="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT quantity::text FROM public.restaurant_inventory_items WHERE id = '$RECIPE_ITEM_C'::uuid;")"
if [ "$insufficient_status" != "Aberto" ] || [ "$insufficient_movements" != "0" ] ||
   [ "$insufficient_stock_a" != "7.368" ] || [ "$insufficient_stock_c" != "4.800" ]; then
  echo "ERROR: failed preparation partially changed the order or stock." >&2
  exit 1
fi

# Cancelling before preparation does not consume ingredients.
create_recipe_order "$USER_A" "$RECIPE_CANCELLED_ORDER_ID" "$PRICE_MENU_ITEM_A" "1"
set_recipe_order_status "$USER_A" "$RECIPE_CANCELLED_ORDER_ID" "Cancelado"
cancelled_movements="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1   -c "SELECT count(*)::text FROM public.restaurant_inventory_movements WHERE source_order_id = '$RECIPE_CANCELLED_ORDER_ID'::uuid;")"
if [ "$cancelled_movements" != "0" ]; then
  echo "ERROR: an order cancelled before preparation consumed ingredients." >&2
  exit 1
fi

# Legacy/manual tickets without menu_item_id keep the old workflow and never
# trigger automatic stock consumption.
psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
INSERT INTO public.restaurant_orders (
  id, restaurant_id, table_number, item_name, amount, status, quantity, service_course, notes
) VALUES (
  '$RECIPE_LEGACY_ORDER_ID'::uuid, '$RESTAURANT_A'::uuid, 1, 'Legacy ticket',
  5.00, 'Aberto', 1, 'Principal', ''
);
UPDATE public.restaurant_orders SET status = 'Em preparação'
WHERE id = '$RECIPE_LEGACY_ORDER_ID'::uuid;
COMMIT;
SQL
legacy_movements="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1   -c "SELECT count(*)::text FROM public.restaurant_inventory_movements WHERE source_order_id = '$RECIPE_LEGACY_ORDER_ID'::uuid;")"
if [ "$legacy_movements" != "0" ]; then
  echo "ERROR: a legacy order without a menu item unexpectedly consumed stock." >&2
  exit 1
fi
echo "PASS: insufficient stock rolls back the whole preparation, cancellation before preparation consumes nothing, and legacy orders remain compatible."

# Account-deletion contract tests run only in the disposable PostgreSQL CI database.
# They never connect to the hosted Supabase project or delete real accounts/data.
expect_prepare_account_deletion_rejection \
  "$USER_A" false "confirma explicitamente" "owner deletion requires explicit confirmation"

queue_after_missing_confirmation="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*) FROM private.account_deletion_queue WHERE user_id = '$USER_A'::uuid;")"
test "$queue_after_missing_confirmation" = "0"

add_staff_member "$USER_C" "$RESTAURANT_A"
expect_prepare_account_deletion_rejection \
  "$USER_A" true "restantes membros da equipa" "owner deletion is blocked while another team member exists"

queue_after_team_block="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*) FROM private.account_deletion_queue WHERE user_id = '$USER_A'::uuid;")"
test "$queue_after_team_block" = "0"

psql --no-psqlrc --set ON_ERROR_STOP=1 -c \
  "DELETE FROM public.restaurant_staff WHERE user_id = '$USER_C'::uuid;" >/dev/null

owner_preparation="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.prepare_account_deletion(true);
COMMIT;
SQL
)"
if ! grep -Fq '"restaurant_data_will_be_deleted": true' <<<"$owner_preparation"; then
  echo "ERROR: confirmed owner deletion did not prepare restaurant deletion." >&2
  printf '%s\n' "$owner_preparation" >&2
  exit 1
fi

psql --no-psqlrc --set ON_ERROR_STOP=1 -c \
  "DELETE FROM auth.users WHERE id = '$USER_A'::uuid;" >/dev/null

owner_restaurant_rows="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*) FROM public.restaurants WHERE id = '$RESTAURANT_A'::uuid;")"
owner_auth_rows="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*) FROM auth.users WHERE id = '$USER_A'::uuid;")"
owner_queue_rows="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*) FROM private.account_deletion_queue WHERE user_id = '$USER_A'::uuid;")"
owner_recipe_rows="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*) FROM public.restaurant_recipes WHERE restaurant_id = '$RESTAURANT_A'::uuid;")"
owner_recipe_line_rows="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*) FROM public.restaurant_recipe_ingredients WHERE restaurant_id = '$RESTAURANT_A'::uuid;")"
if [ "$owner_restaurant_rows" != "0" ] || [ "$owner_auth_rows" != "0" ] || [ "$owner_queue_rows" != "0" ] ||
   [ "$owner_recipe_rows" != "0" ] || [ "$owner_recipe_line_rows" != "0" ]; then
  echo "ERROR: confirmed owner deletion did not remove the account, restaurant, recipes and queue entry together." >&2
  exit 1
fi
echo "PASS: confirmed owner deletion removes the restaurant, its recipe headers and ingredients atomically in the disposable database."

add_staff_member "$USER_D" "$RESTAURANT_B"
staff_preparation="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_D', true);
SET LOCAL ROLE authenticated;
SELECT public.prepare_account_deletion(false);
COMMIT;
SQL
)"
if ! grep -Fq '"restaurant_data_will_be_deleted": false' <<<"$staff_preparation"; then
  echo "ERROR: staff account deletion was not staged without deleting shared restaurant data." >&2
  printf '%s\n' "$staff_preparation" >&2
  exit 1
fi

psql --no-psqlrc --set ON_ERROR_STOP=1 -c \
  "DELETE FROM auth.users WHERE id = '$USER_D'::uuid;" >/dev/null

shared_restaurant_rows="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*) FROM public.restaurants WHERE id = '$RESTAURANT_B'::uuid;")"
deleted_staff_rows="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*) FROM public.restaurant_staff WHERE user_id = '$USER_D'::uuid;")"
staff_queue_rows="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*) FROM private.account_deletion_queue WHERE user_id = '$USER_D'::uuid;")"
if [ "$shared_restaurant_rows" != "1" ] || [ "$deleted_staff_rows" != "0" ] || [ "$staff_queue_rows" != "0" ]; then
  echo "ERROR: deleting a staff account did not preserve the shared restaurant and remove the staged request." >&2
  exit 1
fi
echo "PASS: deleting a staff account preserves the shared restaurant and clears its deletion request."
