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

consume_ai_quota() {
  local user_id="$1"
  psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
SELECT allowed::text || '|' || requests_used::text || '|' || daily_limit::text
FROM public.consume_restaurant_ai_quota();
COMMIT;
SQL
}

# AI quota is scoped to a restaurant, permits only owners, and enforces a daily
# maximum atomically. This runs only against the disposable PostgreSQL test DB.
for expected_used in $(seq 1 10); do
  quota_result="$(consume_ai_quota "$USER_A")"
  expected_result="t|$expected_used|10"
  if [ "$quota_result" != "$expected_result" ]; then
    echo "ERROR: restaurant A AI quota returned '$quota_result'; expected '$expected_result'." >&2
    exit 1
  fi
done
quota_blocked="$(consume_ai_quota "$USER_A")"
if [ "$quota_blocked" != "f|10|10" ]; then
  echo "ERROR: restaurant A's eleventh daily AI request was not blocked." >&2
  exit 1
fi
quota_restaurant_b="$(consume_ai_quota "$USER_B")"
if [ "$quota_restaurant_b" != "t|1|10" ]; then
  echo "ERROR: restaurant B did not receive an independent AI quota." >&2
  exit 1
fi
if consume_ai_quota "$USER_C" >/dev/null 2>&1; then
  echo "ERROR: a staff member unexpectedly consumed AI quota." >&2
  exit 1
fi
quota_rows="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*) FROM private.restaurant_ai_usage WHERE usage_date = (now() AT TIME ZONE 'UTC')::date;")"
if [ "$quota_rows" != "2" ]; then
  echo "ERROR: expected one quota row per restaurant, found $quota_rows." >&2
  exit 1
fi
echo "PASS: AI quota is owner-only, independently scoped per restaurant, and blocks requests above the daily cap."

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
