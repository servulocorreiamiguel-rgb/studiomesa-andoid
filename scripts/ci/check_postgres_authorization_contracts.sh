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

create_customer_reservation() {
  local user_id="$1" request_id="$2" guest_name="$3" guest_phone="$4"
  local party_size="$5" reservation_at="$6" duration="$7" notes="$8"
  psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
SELECT reservation_id::text || '|' || status
FROM public.create_customer_reservation_request(
  '$request_id'::uuid, '$guest_name', '$guest_phone', '$party_size'::integer,
  '$reservation_at'::timestamptz, '$duration'::integer, '$notes'
);
COMMIT;
SQL
}

get_customer_reservation_count() {
  local user_id="$1"
  psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
SELECT count(*)::text FROM public.get_my_restaurant_reservations();
COMMIT;
SQL
}

cancel_customer_reservation() {
  local user_id="$1" reservation_id="$2"
  psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
SELECT public.cancel_my_restaurant_reservation('$reservation_id'::uuid);
COMMIT;
SQL
}

assign_reservation_table() {
  local user_id="$1" reservation_id="$2" table_number="$3"
  psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
SELECT public.assign_restaurant_reservation_table('$reservation_id'::uuid, '$table_number'::integer);
COMMIT;
SQL
}

issue_invoice_loyalty_qr() {
  local user_id="$1" member_id="$2" amount="$3" invoice_reference="$4" request_id="$5"
  psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
SELECT qr_token::text || '|' || points::text || '|' || invoice_reference
FROM public.issue_loyalty_invoice_qr(
  '$member_id'::uuid, '$amount'::numeric, '$invoice_reference', '$request_id'::uuid
);
COMMIT;
SQL
}

redeem_invoice_loyalty_qr() {
  local user_id="$1" qr_token="$2"
  psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
SELECT points::text || '|' || points_awarded::text || '|' || invoice_reference
FROM public.redeem_loyalty_invoice_qr('$qr_token'::uuid);
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

# Exercise the legacy loyalty RPC wrappers created by the conditional migration.
# The QR issue and legacy reward-queue fixtures return no rows. The award endpoint
# now has its real permission-aware implementation, so an unknown token must fail.
LEGACY_LOYALTY_WRAPPER_RESULT="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT
  (SELECT count(*) FROM public.create_loyalty_qr_token())::text || '|' ||
  (SELECT count(*) FROM public.get_restaurant_reward_redemptions('$RESTAURANT_A'::uuid))::text;
COMMIT;
SQL
)"
if [ "$LEGACY_LOYALTY_WRAPPER_RESULT" != "0|0" ]; then
  echo "ERROR: legacy QR issue or reward-redemption wrapper did not preserve its callable contract: '$LEGACY_LOYALTY_WRAPPER_RESULT'." >&2
  exit 1
fi
if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT * FROM public.award_loyalty_points_from_qr(
  '11111111-aaaa-4aaa-8aaa-111111111111'::uuid,
  1,
  '22222222-bbbb-4bbb-8bbb-222222222222'::uuid,
  NULL
);
COMMIT;
SQL
then
  echo "ERROR: the permission-aware legacy award wrapper accepted an unknown QR token." >&2
  exit 1
fi
echo "PASS: legacy QR wrapper contracts are preserved and the award RPC rejects unknown tokens."

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

# Granular staff permissions: owner-only management, per-restaurant isolation,
# backward-compatible defaults, and no self-escalation.
default_staff_permission="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT private.has_restaurant_permission('$RESTAURANT_A'::uuid, 'orders')::text
       || '|' || private.has_restaurant_permission('$RESTAURANT_A'::uuid, 'inventory')::text;
ROLLBACK;
SQL
)"
if [ "$default_staff_permission" != "true|true" ]; then
  echo "ERROR: legacy staff without a custom permission row did not retain operational access." >&2
  exit 1
fi

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT public.set_restaurant_staff_permissions('$USER_C'::uuid, ARRAY['orders']);
COMMIT;
SQL
then
  echo "ERROR: staff unexpectedly changed their own permissions." >&2
  exit 1
fi

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_B', true);
SET LOCAL ROLE authenticated;
SELECT public.set_restaurant_staff_permissions('$USER_C'::uuid, ARRAY['orders']);
COMMIT;
SQL
then
  echo "ERROR: owner B unexpectedly changed permissions for staff in restaurant A." >&2
  exit 1
fi

psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.set_restaurant_staff_permissions('$USER_C'::uuid, ARRAY['orders','reservations']);
COMMIT;
SQL

custom_staff_permission="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT private.has_restaurant_permission('$RESTAURANT_A'::uuid, 'orders')::text
       || '|' || private.has_restaurant_permission('$RESTAURANT_A'::uuid, 'reservations')::text
       || '|' || private.has_restaurant_permission('$RESTAURANT_A'::uuid, 'inventory')::text;
ROLLBACK;
SQL
)"
if [ "$custom_staff_permission" != "true|true|false" ]; then
  echo "ERROR: custom staff permissions were not enforced as configured." >&2
  exit 1
fi

owner_permission_matrix="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT array_to_string(public.get_my_restaurant_permissions(), ',');
ROLLBACK;
SQL
)"
staff_permission_matrix="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT array_to_string(public.get_my_restaurant_permissions(), ',');
ROLLBACK;
SQL
)"
if [ "$owner_permission_matrix" != "orders,tables,reservations,menu,inventory,loyalty" ] ||
   [ "$staff_permission_matrix" != "orders,reservations" ]; then
  echo "ERROR: current-user permission RPC returned an unexpected effective matrix." >&2
  printf 'Owner: %s; staff: %s\\n' "$owner_permission_matrix" "$staff_permission_matrix" >&2
  exit 1
fi

owner_readback_permissions="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT array_to_string(public.get_restaurant_staff_permissions('$USER_C'::uuid), ',');
ROLLBACK;
SQL
)"
if [ "$owner_readback_permissions" != "orders,reservations" ]; then
  echo "ERROR: owner could not read back a staff member's effective permissions." >&2
  exit 1
fi

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT public.get_restaurant_staff_permissions('$USER_D'::uuid);
COMMIT;
SQL
then
  echo "ERROR: staff unexpectedly queried another member's permissions." >&2
  exit 1
fi

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.set_restaurant_staff_permissions('$USER_C'::uuid, ARRAY['owner_access']);
COMMIT;
SQL
then
  echo "ERROR: an unknown staff permission was accepted." >&2
  exit 1
fi

psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.set_restaurant_staff_permissions('$USER_C'::uuid, ARRAY[]::text[]);
COMMIT;
SQL
empty_staff_permission="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT private.has_restaurant_permission('$RESTAURANT_A'::uuid, 'orders')::text;
ROLLBACK;
SQL
)"
if [ "$empty_staff_permission" != "false" ]; then
  echo "ERROR: an explicitly empty permission list did not deny all operational areas." >&2
  exit 1
fi

psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.set_restaurant_staff_permissions('$USER_C'::uuid, ARRAY['orders','reservations']);
COMMIT;
SQL

# A restricted employee cannot write to tables, menu or stock through direct
# table access or the stock RPC, even though the Android client is bypassed.
MENU_PERMISSION_PROBE="88888888-8888-4888-8888-888888888888"
psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
INSERT INTO public.menu_items (
  id, restaurant_id, name, category, description, price, cost, allergens, is_active
) VALUES (
  '$MENU_PERMISSION_PROBE'::uuid, '$RESTAURANT_A'::uuid, 'Permission probe',
  'Prato principal', '', 5.00, 2.00, '', true
);
SQL

table_write_rows="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
WITH changed AS (
  UPDATE public.restaurant_tables
  SET area = area
  WHERE restaurant_id = '$RESTAURANT_A'::uuid AND table_number = 1
  RETURNING id
)
SELECT count(*)::text FROM changed;
ROLLBACK;
SQL
)"
menu_write_rows="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
WITH changed AS (
  UPDATE public.menu_items SET price = price
  WHERE id = '$MENU_PERMISSION_PROBE'::uuid
  RETURNING id
)
SELECT count(*)::text FROM changed;
ROLLBACK;
SQL
)"
if [ "$table_write_rows" != "0" ] || [ "$menu_write_rows" != "0" ]; then
  echo "ERROR: a staff account wrote to an area disabled by its permission matrix." >&2
  exit 1
fi

ORDER_PERMISSION_PROBE="88888888-8888-4888-8888-888888888889"
psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
INSERT INTO public.restaurant_orders (
  id, restaurant_id, table_number, item_name, amount, quantity, service_course, notes
) VALUES (
  '$ORDER_PERMISSION_PROBE'::uuid, '$RESTAURANT_A'::uuid, 1,
  'Permission probe order', 5.00, 1, 'Principal', ''
);
SQL
order_write_rows="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
WITH changed AS (
  UPDATE public.restaurant_orders SET status = 'Em preparação'
  WHERE id = '$ORDER_PERMISSION_PROBE'::uuid
  RETURNING id
)
SELECT count(*)::text FROM changed;
ROLLBACK;
SQL
)"
if [ "$order_write_rows" != "1" ]; then
  echo "ERROR: the orders permission did not allow the employee to update an order." >&2
  exit 1
fi

psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.set_restaurant_staff_permissions('$USER_C'::uuid, ARRAY['reservations']);
COMMIT;
SQL
restricted_order_write_rows="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
WITH changed AS (
  UPDATE public.restaurant_orders SET status = 'Pronto'
  WHERE id = '$ORDER_PERMISSION_PROBE'::uuid
  RETURNING id
)
SELECT count(*)::text FROM changed;
ROLLBACK;
SQL
)"
if [ "$restricted_order_write_rows" != "0" ]; then
  echo "ERROR: the orders permission did not block direct writes after being disabled." >&2
  exit 1
fi

STAFF_RESERVATION_AT="$(date -u -d '+45 days' '+%Y-%m-%d %H:%M:%S+00:00')"
STAFF_RESERVATION_REQUEST="d5555555-5555-4555-8555-555555555555"
STAFF_RESERVATION_ID="$(create_restaurant_reservation "$USER_C" "$RESTAURANT_A" "$STAFF_RESERVATION_REQUEST" "Staff permission booking" "$STAFF_RESERVATION_AT")"
if [ -z "$STAFF_RESERVATION_ID" ]; then
  echo "ERROR: reservations permission did not allow staff to create a reservation." >&2
  exit 1
fi

psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.set_restaurant_staff_permissions('$USER_C'::uuid, ARRAY['orders']);
COMMIT;
SQL
if create_restaurant_reservation "$USER_C" "$RESTAURANT_A" "d6666666-6666-4666-8666-666666666666" "Blocked staff booking" "$STAFF_RESERVATION_AT" >/dev/null 2>&1; then
  echo "ERROR: staff created a reservation after the reservations permission was disabled." >&2
  exit 1
fi

psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.set_restaurant_staff_permissions('$USER_C'::uuid, ARRAY['orders','reservations']);
COMMIT;
SQL

psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.set_restaurant_staff_permissions('$USER_C'::uuid, ARRAY['orders','reservations']);
COMMIT;
SQL

if create_inventory_item "$USER_C" "$RESTAURANT_A" "dddddddd-dddd-4ddd-8ddd-dddddddddddd" "Blocked stock item" "1" >/dev/null 2>&1; then
  echo "ERROR: a staff account created stock while the inventory permission was disabled." >&2
  exit 1
fi

# Restore full operational access for later legacy staff-flow tests in this script.
psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.set_restaurant_staff_permissions(
  '$USER_C'::uuid, ARRAY['orders','tables','reservations','menu','inventory','loyalty']
);
COMMIT;
SQL
echo "PASS: owner/staff effective matrices and direct database denial for restricted areas."

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT public.set_restaurant_staff_permissions('$USER_D'::uuid, ARRAY['orders']);
COMMIT;
SQL
then
  echo "ERROR: staff A unexpectedly changed permissions for a different restaurant member." >&2
  exit 1
fi
echo "PASS: staff permissions default safely, are owner-managed, isolated and cannot be self-escalated."


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

cost_snapshot_history="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT string_agg(unit_cost_at_movement::numeric(12,4)::text, ',' ORDER BY created_at) FROM public.restaurant_inventory_movements WHERE item_id = '$COST_ITEM'::uuid AND note = 'Desperdício · Deterioração';")"
current_cost_and_quantity="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT unit_cost_per_unit::numeric(12,4)::text || '|' || quantity::numeric(12,3)::text FROM public.restaurant_inventory_items WHERE id = '$COST_ITEM'::uuid;")"
if [ "$cost_snapshot_history" != "12.3456,20.0000" ] || [ "$current_cost_and_quantity" != "20.0000|9.500" ]; then
  echo "ERROR: waste movements did not preserve the unit cost that was active at each movement." >&2
  printf 'Snapshots: %s; current item: %s\n' "$cost_snapshot_history" "$current_cost_and_quantity" >&2
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

# Customer reservation flow: server derives the restaurant from the customer's
# own loyalty membership; retries are idempotent and all reads/cancellations
# remain scoped to the authenticated owner.
RESERVATION_AT="$(date -u -d '+2 days' '+%Y-%m-%dT%H:%M:%SZ')"
RESERVATION_REQUEST_A="aaaaaaaa-1212-4121-8121-121212121212"
RESERVATION_REQUEST_B="bbbbbbbb-2323-4232-8232-232323232323"
RESERVATION_REQUEST_C="cccccccc-3434-4343-8343-343434343434"
RESERVATION_FIRST="$(create_customer_reservation "$USER_E" "$RESERVATION_REQUEST_A" "CI Booking A" "+351900000001" 2 "$RESERVATION_AT" 90 "Allergy note test")"
RESERVATION_RETRY="$(create_customer_reservation "$USER_E" "$RESERVATION_REQUEST_A" "CI Booking A" "+351900000001" 2 "$RESERVATION_AT" 90 "Allergy note test")"
if [ "$RESERVATION_FIRST" != "$RESERVATION_RETRY" ]; then
  echo "ERROR: retrying a customer reservation did not return the original reservation." >&2
  exit 1
fi
RESERVATION_ID="${RESERVATION_FIRST%%|*}"
RESERVATION_STATUS="${RESERVATION_FIRST##*|}"
if [ -z "$RESERVATION_ID" ] || [ "$RESERVATION_STATUS" != "Pendente" ]; then
  echo "ERROR: customer reservation was not created as pending." >&2
  exit 1
fi

if create_customer_reservation "$USER_E" "$RESERVATION_REQUEST_A" "Different booking" "+351900000001" 2 "$RESERVATION_AT" 90 "Allergy note test" >/dev/null 2>&1; then
  echo "ERROR: reservation request ID was reused with a different payload." >&2
  exit 1
fi

CUSTOMER_A_RESERVATION_COUNT="$(get_customer_reservation_count "$USER_E")"
CUSTOMER_B_RESERVATION_COUNT="$(get_customer_reservation_count "$USER_F")"
if [ "$CUSTOMER_A_RESERVATION_COUNT" != "1" ] || [ "$CUSTOMER_B_RESERVATION_COUNT" != "0" ]; then
  echo "ERROR: customer reservation history is not isolated by authenticated membership." >&2
  exit 1
fi

# Restaurant A staff can assign a suitable table, but staff from B cannot touch A.
assign_reservation_table "$USER_C" "$RESERVATION_ID" 1 >/dev/null
ASSIGNED_TABLE="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT table_number::text FROM public.restaurant_reservations WHERE id = '$RESERVATION_ID'::uuid;")"
if [ "$ASSIGNED_TABLE" != "1" ]; then
  echo "ERROR: authorized restaurant staff did not assign the requested table." >&2
  exit 1
fi
if assign_reservation_table "$USER_D" "$RESERVATION_ID" 2 >/dev/null 2>&1; then
  echo "ERROR: staff from another restaurant assigned a table to this reservation." >&2
  exit 1
fi

# The same table cannot be assigned to overlapping requests, and a table with
# insufficient seats is rejected by the server.
OVERLAP_RESULT="$(create_customer_reservation "$USER_E" "$RESERVATION_REQUEST_B" "CI Booking B" "" 2 "$RESERVATION_AT" 90 "")"
OVERLAP_ID="${OVERLAP_RESULT%%|*}"
if assign_reservation_table "$USER_C" "$OVERLAP_ID" 1 >/dev/null 2>&1; then
  echo "ERROR: overlapping reservation was assigned to an occupied table/time window." >&2
  exit 1
fi
LARGE_PARTY_AT="$(date -u -d '+3 days' '+%Y-%m-%dT%H:%M:%SZ')"
LARGE_PARTY_RESULT="$(create_customer_reservation "$USER_E" "$RESERVATION_REQUEST_C" "CI Booking C" "" 5 "$LARGE_PARTY_AT" 90 "")"
LARGE_PARTY_ID="${LARGE_PARTY_RESULT%%|*}"
if assign_reservation_table "$USER_C" "$LARGE_PARTY_ID" 2 >/dev/null 2>&1; then
  echo "ERROR: server accepted a table with insufficient seating capacity." >&2
  exit 1
fi

# A different customer's session cannot cancel this request; the owner can,
# and repeated cancellation is safe.
if cancel_customer_reservation "$USER_F" "$RESERVATION_ID" >/dev/null 2>&1; then
  echo "ERROR: a different customer cancelled another customer's reservation." >&2
  exit 1
fi
cancel_customer_reservation "$USER_E" "$RESERVATION_ID" >/dev/null
cancel_customer_reservation "$USER_E" "$RESERVATION_ID" >/dev/null
CANCELLED_STATUS="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT status FROM public.restaurant_reservations WHERE id = '$RESERVATION_ID'::uuid;")"
if [ "$CANCELLED_STATUS" != "Cancelada" ]; then
  echo "ERROR: customer's own reservation was not cancelled." >&2
  exit 1
fi

echo "PASS: customer reservation creation is idempotent, history and cancellation are private, table assignment is restaurant-scoped, and capacity/time conflicts are rejected."

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

# Invoice-linked loyalty QR: issuance, server-side points calculation, customer binding,
# idempotent retries, single redemption, cross-restaurant isolation and private table access.
INVOICE_QR_REQUEST="12121212-aaaa-4aaa-8aaa-121212121212"
INVOICE_QR_FOREIGN_REQUEST="13131313-bbbb-4bbb-8bbb-131313131313"
INVOICE_QR_TOKEN_REQUEST="14141414-cccc-4ccc-8ccc-141414141414"
INVOICE_QR_REFERENCE="FT-CI-QR-1001"

INVOICE_QR_FIRST="$(issue_invoice_loyalty_qr "$USER_A" "$MEMBER_A" "20.75" "$INVOICE_QR_REFERENCE" "$INVOICE_QR_REQUEST")"
INVOICE_QR_RETRY="$(issue_invoice_loyalty_qr "$USER_A" "$MEMBER_A" "20.75" "$INVOICE_QR_REFERENCE" "$INVOICE_QR_REQUEST")"
if [ "$INVOICE_QR_FIRST" != "$INVOICE_QR_RETRY" ]; then
  echo "ERROR: retrying invoice QR issuance returned a different token or points value." >&2
  exit 1
fi
IFS='|' read -r INVOICE_QR_TOKEN INVOICE_QR_POINTS INVOICE_QR_REF <<< "$INVOICE_QR_FIRST"
if [ -z "$INVOICE_QR_TOKEN" ] || [ "$INVOICE_QR_POINTS" != "20" ] || [ "$INVOICE_QR_REF" != "$INVOICE_QR_REFERENCE" ]; then
  echo "ERROR: invoice QR issuance did not calculate one point per complete euro." >&2
  exit 1
fi
echo "PASS: invoice QR issuance is idempotent and the server calculates points from the paid total."

# A different request cannot create another points QR for the same invoice reference.
if issue_invoice_loyalty_qr "$USER_A" "$MEMBER_A" "20.75" "$INVOICE_QR_REFERENCE" "$INVOICE_QR_TOKEN_REQUEST" >/dev/null 2>&1; then
  echo "ERROR: a duplicate invoice reference was accepted." >&2
  exit 1
fi

# An owner from another restaurant cannot issue points against MEMBER_A.
if issue_invoice_loyalty_qr "$USER_B" "$MEMBER_A" "20.75" "FT-CI-FOREIGN-1002" "$INVOICE_QR_FOREIGN_REQUEST" >/dev/null 2>&1; then
  echo "ERROR: restaurant B unexpectedly issued a loyalty QR for restaurant A's customer." >&2
  exit 1
fi

# Only the account that owns MEMBER_A may redeem the token.
INVOICE_QR_REDEEM_FIRST="$(redeem_invoice_loyalty_qr "$USER_E" "$INVOICE_QR_TOKEN")"
INVOICE_QR_REDEEM_RETRY="$(redeem_invoice_loyalty_qr "$USER_E" "$INVOICE_QR_TOKEN")"
if [ "$INVOICE_QR_REDEEM_FIRST" != "$INVOICE_QR_REDEEM_RETRY" ]; then
  echo "ERROR: repeating the same invoice QR scan changed the redemption result." >&2
  exit 1
fi
IFS='|' read -r INVOICE_QR_BALANCE INVOICE_QR_AWARDED INVOICE_QR_REDEEMED_REF <<< "$INVOICE_QR_REDEEM_FIRST"
if [ "$INVOICE_QR_BALANCE" != "20" ] || [ "$INVOICE_QR_AWARDED" != "20" ] || [ "$INVOICE_QR_REDEEMED_REF" != "$INVOICE_QR_REFERENCE" ]; then
  echo "ERROR: invoice QR redemption returned an unexpected balance or points award." >&2
  exit 1
fi

if redeem_invoice_loyalty_qr "$USER_F" "$INVOICE_QR_TOKEN" >/dev/null 2>&1; then
  echo "ERROR: a different customer account unexpectedly redeemed MEMBER_A's invoice QR." >&2
  exit 1
fi

invoice_qr_transaction_count="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*) FROM public.loyalty_transactions WHERE client_request_id = '$INVOICE_QR_TOKEN'::uuid AND member_id = '$MEMBER_A'::uuid AND points_delta = 20;")"
invoice_qr_balance_ok="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT points = 20 FROM public.loyalty_members WHERE id = '$MEMBER_A'::uuid;")"
invoice_qr_redeemed_ok="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT redeemed_at IS NOT NULL AND redeemed_by = '$USER_E'::uuid FROM private.loyalty_invoice_qr_codes WHERE token = '$INVOICE_QR_TOKEN'::uuid;")"
if [ "$invoice_qr_transaction_count" != "1" ] || [ "$invoice_qr_balance_ok" != "t" ] || [ "$invoice_qr_redeemed_ok" != "t" ]; then
  echo "ERROR: invoice QR did not produce exactly one transaction and one balance increase." >&2
  exit 1
fi

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_E', true);
SET LOCAL ROLE authenticated;
SELECT * FROM private.loyalty_invoice_qr_codes;
COMMIT;
SQL
then
  echo "ERROR: an authenticated customer unexpectedly read the private invoice QR table directly." >&2
  exit 1
fi
echo "PASS: invoice QR is private, bound to one customer and one restaurant, single-use, and cannot double-credit points."

# A staff account without the loyalty area cannot issue an invoice-linked points QR.
psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.set_restaurant_staff_permissions('$USER_C'::uuid, ARRAY['orders','reservations']);
COMMIT;
SQL
if issue_invoice_loyalty_qr "$USER_C" "$MEMBER_A" "15.00" "FT-CI-STAFF-BLOCKED" "15151515-dddd-4ddd-8ddd-151515151515" >/dev/null 2>&1; then
  echo "ERROR: staff issued an invoice QR after loyalty permission was disabled." >&2
  exit 1
fi
psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.set_restaurant_staff_permissions(
  '$USER_C'::uuid, ARRAY['orders','tables','reservations','menu','inventory','loyalty']
);
COMMIT;
SQL
echo "PASS: invoice-linked loyalty QR issuance requires the loyalty staff permission."


LOYALTY_REQUEST="77777777-7777-4777-8777-777777777777"
LOYALTY_FOREIGN_REQUEST="88888888-8888-4888-8888-888888888888"
LOYALTY_FIRST="$(adjust_loyalty_points "$USER_A" "$MEMBER_A" "$LOYALTY_REQUEST" "10" "CI loyalty retry")"
LOYALTY_RETRY="$(adjust_loyalty_points "$USER_A" "$MEMBER_A" "$LOYALTY_REQUEST" "10" "CI loyalty retry")"
if [ "$LOYALTY_FIRST" != "$LOYALTY_RETRY" ]; then
  echo "ERROR: repeating a loyalty-points request returned a different balance." >&2
  exit 1
fi
if [ "$LOYALTY_FIRST" != "30" ]; then
  echo "ERROR: the loyalty adjustment returned '$LOYALTY_FIRST'; expected a total balance of 30 after the invoice QR award." >&2
  exit 1
fi
loyalty_balance_ok="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT points = 30 FROM public.loyalty_members WHERE id = '$MEMBER_A'::uuid;")"
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

# Restaurant-specific reward catalogue, one-time point redemption and fulfilment workflow.
REWARD_ID="aaaa1111-1111-4111-8111-111111111111"
EMPTY_REWARD_ID="bbbb2222-2222-4222-8222-222222222222"
REWARD_REQUEST="cccc3333-3333-4333-8333-333333333333"
REWARD_FOREIGN_REQUEST="dddd4444-4444-4444-8444-444444444444"

save_reward() {
  local user_id="$1" restaurant_id="$2" reward_id="$3" name="$4" points="$5" stock="$6" active="$7"
  psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
SELECT public.save_loyalty_reward(
  '$restaurant_id'::uuid, '$reward_id'::uuid, '$name', 'CI reward catalogue test',
  '$points'::integer, $stock::integer, '$active'::boolean
);
COMMIT;
SQL
}

redeem_reward() {
  local user_id="$1" reward_id="$2" request_id="$3"
  psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
SELECT redemption_id::text || '|' || reward_name || '|' ||
       points_spent::text || '|' || points_remaining::text || '|' || status
FROM public.redeem_loyalty_reward('$reward_id'::uuid, '$request_id'::uuid);
COMMIT;
SQL
}

REWARD_SAVED="$(save_reward "$USER_A" "$RESTAURANT_A" "$REWARD_ID" "CI Dessert" "15" "1" "true")"
if [ "$REWARD_SAVED" != "$REWARD_ID" ]; then
  echo "ERROR: restaurant owner could not create a loyalty reward." >&2
  exit 1
fi
echo "PASS: the restaurant owner can create a reward with a points cost and stock limit."

if save_reward "$USER_C" "$RESTAURANT_A" "eeee5555-5555-4555-8555-555555555555" "Staff reward" "5" "2" "true" >/dev/null 2>&1; then
  echo "ERROR: a staff member unexpectedly created or edited a reward." >&2
  exit 1
fi
if save_reward "$USER_B" "$RESTAURANT_B" "$REWARD_ID" "Cross restaurant edit" "1" "1" "true" >/dev/null 2>&1; then
  echo "ERROR: restaurant B unexpectedly edited restaurant A's reward." >&2
  exit 1
fi

EMPTY_REWARD_SAVED="$(save_reward "$USER_A" "$RESTAURANT_A" "$EMPTY_REWARD_ID" "CI Out of Stock" "5" "0" "true")"
test "$EMPTY_REWARD_SAVED" = "$EMPTY_REWARD_ID"

CUSTOMER_A_REWARD_COUNT="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_E', true);
SET LOCAL ROLE authenticated;
SELECT count(*) FROM public.get_customer_loyalty_rewards('$MEMBER_A'::uuid);
COMMIT;
SQL
)"
CUSTOMER_B_REWARD_COUNT="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_F', true);
SET LOCAL ROLE authenticated;
SELECT count(*) FROM public.get_customer_loyalty_rewards('$MEMBER_B'::uuid);
COMMIT;
SQL
)"
if [ "$CUSTOMER_A_REWARD_COUNT" != "1" ] || [ "$CUSTOMER_B_REWARD_COUNT" != "0" ]; then
  echo "ERROR: customer reward catalogue did not isolate active and in-stock rewards by restaurant." >&2
  exit 1
fi

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_E', true);
SET LOCAL ROLE authenticated;
SELECT * FROM public.get_customer_loyalty_rewards('$MEMBER_B'::uuid);
COMMIT;
SQL
then
  echo "ERROR: customer A unexpectedly queried another customer's reward catalogue." >&2
  exit 1
fi
echo "PASS: customer catalogues are limited to their own restaurant and hide exhausted rewards."

REWARD_FIRST="$(redeem_reward "$USER_E" "$REWARD_ID" "$REWARD_REQUEST")"
REWARD_RETRY="$(redeem_reward "$USER_E" "$REWARD_ID" "$REWARD_REQUEST")"
if [ "$REWARD_FIRST" != "$REWARD_RETRY" ]; then
  echo "ERROR: repeating a reward redemption changed the result or spent points twice." >&2
  exit 1
fi
IFS='|' read -r REWARD_REDEMPTION_ID REWARD_NAME REWARD_POINTS_SPENT REWARD_POINTS_LEFT REWARD_STATUS <<< "$REWARD_FIRST"
if [ -z "$REWARD_REDEMPTION_ID" ] || [ "$REWARD_NAME" != "CI Dessert" ] ||
   [ "$REWARD_POINTS_SPENT" != "15" ] || [ "$REWARD_POINTS_LEFT" != "15" ] ||
   [ "$REWARD_STATUS" != "pending" ]; then
  echo "ERROR: reward redemption returned an unexpected result." >&2
  exit 1
fi

if redeem_reward "$USER_F" "$REWARD_ID" "$REWARD_FOREIGN_REQUEST" >/dev/null 2>&1; then
  echo "ERROR: customer B unexpectedly redeemed a reward from restaurant A." >&2
  exit 1
fi
if redeem_reward "$USER_E" "$EMPTY_REWARD_ID" "eeee6666-6666-4666-8666-666666666666" >/dev/null 2>&1; then
  echo "ERROR: an exhausted reward was redeemed." >&2
  exit 1
fi

reward_balance_ok="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT points = 15 FROM public.loyalty_members WHERE id = '$MEMBER_A'::uuid;")"
reward_stock_ok="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT redeemed_count = 1 FROM public.loyalty_rewards WHERE id = '$REWARD_ID'::uuid;")"
reward_redemption_count="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*) FROM public.loyalty_reward_redemptions WHERE member_id = '$MEMBER_A'::uuid AND reward_id = '$REWARD_ID'::uuid AND client_request_id = '$REWARD_REQUEST'::uuid;")"
reward_transaction_count="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*) FROM public.loyalty_transactions WHERE member_id = '$MEMBER_A'::uuid AND client_request_id = '$REWARD_REQUEST'::uuid AND points_delta = -15;")"
if [ "$reward_balance_ok" != "t" ] || [ "$reward_stock_ok" != "t" ] ||
   [ "$reward_redemption_count" != "1" ] || [ "$reward_transaction_count" != "1" ]; then
  echo "ERROR: reward redemption did not atomically deduct points, update stock and write one transaction." >&2
  exit 1
fi
echo "PASS: reward redemptions are idempotent, enforce stock and deduct customer points exactly once."

RESTAURANT_REWARD_QUEUE_COUNT="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT count(*) FROM public.get_restaurant_loyalty_reward_redemptions('$RESTAURANT_A'::uuid);
COMMIT;
SQL
)"
STAFF_REWARD_QUEUE_COUNT="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT count(*) FROM public.get_restaurant_loyalty_reward_redemptions('$RESTAURANT_A'::uuid);
COMMIT;
SQL
)"

# Reading restaurant reward catalog/fulfilment queue also requires the loyalty area.
psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.set_restaurant_staff_permissions('$USER_C'::uuid, ARRAY['orders','reservations']);
COMMIT;
SQL
if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT * FROM public.get_restaurant_loyalty_rewards('$RESTAURANT_A'::uuid);
COMMIT;
SQL
then
  echo "ERROR: staff read the reward catalog after loyalty permission was disabled." >&2
  exit 1
fi
if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT * FROM public.get_restaurant_loyalty_reward_redemptions('$RESTAURANT_A'::uuid);
COMMIT;
SQL
then
  echo "ERROR: staff read the fulfilment queue after loyalty permission was disabled." >&2
  exit 1
fi
psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.set_restaurant_staff_permissions(
  '$USER_C'::uuid, ARRAY['orders','tables','reservations','menu','inventory','loyalty']
);
COMMIT;
SQL

if [ "$RESTAURANT_REWARD_QUEUE_COUNT" != "1" ] || [ "$STAFF_REWARD_QUEUE_COUNT" != "1" ]; then
  echo "ERROR: the owner or staff could not view the restaurant's fulfilment queue." >&2
  exit 1
fi
if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_E', true);
SET LOCAL ROLE authenticated;
SELECT * FROM public.get_restaurant_loyalty_reward_redemptions('$RESTAURANT_A'::uuid);
COMMIT;
SQL
then
  echo "ERROR: a customer unexpectedly read the restaurant's fulfilment queue." >&2
  exit 1
fi
if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_B', true);
SET LOCAL ROLE authenticated;
SELECT * FROM public.get_restaurant_loyalty_reward_redemptions('$RESTAURANT_A'::uuid);
COMMIT;
SQL
then
  echo "ERROR: restaurant B unexpectedly read restaurant A's fulfilment queue." >&2
  exit 1
fi

CUSTOMER_REWARD_HISTORY_COUNT="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_E', true);
SET LOCAL ROLE authenticated;
SELECT count(*) FROM public.get_my_loyalty_reward_redemptions();
COMMIT;
SQL
)"
OTHER_CUSTOMER_HISTORY_COUNT="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_F', true);
SET LOCAL ROLE authenticated;
SELECT count(*) FROM public.get_my_loyalty_reward_redemptions();
COMMIT;
SQL
)"
if [ "$CUSTOMER_REWARD_HISTORY_COUNT" != "1" ] || [ "$OTHER_CUSTOMER_HISTORY_COUNT" != "0" ]; then
  echo "ERROR: customer redemption history was not scoped to the authenticated account." >&2
  exit 1
fi

psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.set_restaurant_staff_permissions('$USER_C'::uuid, ARRAY['orders','reservations']);
COMMIT;
SQL
if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT public.fulfill_loyalty_reward_redemption('$REWARD_REDEMPTION_ID'::uuid);
COMMIT;
SQL
then
  echo "ERROR: staff confirmed a reward delivery after loyalty permission was disabled." >&2
  exit 1
fi
psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.set_restaurant_staff_permissions(
  '$USER_C'::uuid, ARRAY['orders','tables','reservations','menu','inventory','loyalty']
);
COMMIT;
SQL

psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT public.fulfill_loyalty_reward_redemption('$REWARD_REDEMPTION_ID'::uuid);
SELECT public.fulfill_loyalty_reward_redemption('$REWARD_REDEMPTION_ID'::uuid);
COMMIT;
SQL
reward_fulfilled_ok="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT status = 'fulfilled' AND fulfilled_by = '$USER_C'::uuid FROM public.loyalty_reward_redemptions WHERE id = '$REWARD_REDEMPTION_ID'::uuid;")"
if [ "$reward_fulfilled_ok" != "t" ]; then
  echo "ERROR: staff fulfilment did not mark the reward as fulfilled exactly once." >&2
  exit 1
fi

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_E', true);
SET LOCAL ROLE authenticated;
SELECT * FROM public.loyalty_rewards;
SELECT * FROM public.loyalty_reward_redemptions;
COMMIT;
SQL
then
  echo "ERROR: an authenticated customer unexpectedly read loyalty reward tables directly." >&2
  exit 1
fi
echo "PASS: restaurant-scoped rewards, customer history, single-use redemption and staff fulfilment are authorized."

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

# Confirming a reservation requires a real table assignment first.
assign_reservation_table "$USER_A" "$RESERVATION_A" 1 >/dev/null
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
if [ "$owner_restaurant_rows" != "0" ] || [ "$owner_auth_rows" != "0" ] || [ "$owner_queue_rows" != "0" ]; then
  echo "ERROR: confirmed owner deletion did not remove the account, restaurant and queue entry together." >&2
  exit 1
fi
echo "PASS: confirmed owner deletion removes the restaurant atomically in the disposable database."

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
