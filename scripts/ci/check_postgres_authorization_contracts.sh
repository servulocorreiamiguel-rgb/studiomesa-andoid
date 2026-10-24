#!/usr/bin/env bash
# Authorization/idempotency regression tests against the disposable PostgreSQL
# migration replay database. This is not a substitute for testing Supabase Auth/JWT
# and REST behavior on the hosted project.
set -euo pipefail

USER_A="11111111-1111-4111-8111-111111111111"
USER_B="22222222-2222-4222-8222-222222222222"
USER_C="33333333-3333-4333-8333-333333333333"
USER_D="44444444-4444-4444-8444-444444444444"
REQUEST_A="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
REQUEST_B="bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"

psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL
INSERT INTO auth.users (id, email)
VALUES ('$USER_A', 'ci-owner-a@example.invalid'),
       ('$USER_B', 'ci-owner-b@example.invalid'),
       ('$USER_C', 'ci-staff-c@example.invalid'),
       ('$USER_D', 'ci-staff-d@example.invalid');
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

RESTAURANT_A="$(create_restaurant "$USER_A" "CI Restaurant A" "SMOKEAA001")"
RESTAURANT_B="$(create_restaurant "$USER_B" "CI Restaurant B" "SMOKEBB001")"
test -n "$RESTAURANT_A"
test -n "$RESTAURANT_B"
test "$RESTAURANT_A" != "$RESTAURANT_B"

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
