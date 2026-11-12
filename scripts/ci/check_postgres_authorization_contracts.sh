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

# Cross-device assistant deletion keeps a content-free cloud tombstone.
# Only the owner of that restaurant may request the deletion.
ASSISTANT_CONVERSATION_ID="77777777-7777-4777-8777-777777777777"
ASSISTANT_MESSAGE_ID="88888888-8888-4888-8888-888888888888"
psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
INSERT INTO public.restaurant_assistant_conversations (
  id, restaurant_id, title, created_by
) VALUES (
  '$ASSISTANT_CONVERSATION_ID'::uuid, '$RESTAURANT_A'::uuid,
  'Conversa privada de teste', '$USER_A'::uuid
);
INSERT INTO public.restaurant_assistant_messages (
  id, restaurant_id, conversation_id, role, content, created_by
) VALUES (
  '$ASSISTANT_MESSAGE_ID'::uuid, '$RESTAURANT_A'::uuid,
  '$ASSISTANT_CONVERSATION_ID'::uuid, 'user',
  'Conteúdo que deve desaparecer depois da eliminação.', '$USER_A'::uuid
);
COMMIT;
SQL

if psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_B', true);
SET LOCAL ROLE authenticated;
SELECT public.delete_assistant_conversation('$RESTAURANT_A'::uuid, '$ASSISTANT_CONVERSATION_ID'::uuid);
COMMIT;
SQL
then
  echo "ERROR: another restaurant owner deleted this restaurant's assistant conversation." >&2
  exit 1
fi

if psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT public.delete_assistant_conversation('$RESTAURANT_A'::uuid, '$ASSISTANT_CONVERSATION_ID'::uuid);
COMMIT;
SQL
then
  echo "ERROR: a staff member deleted the owner's assistant conversation." >&2
  exit 1
fi

psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.delete_assistant_conversation('$RESTAURANT_A'::uuid, '$ASSISTANT_CONVERSATION_ID'::uuid);
COMMIT;
SQL

assistant_deleted_at="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1  -c "SELECT (deleted_at IS NOT NULL)::text FROM public.restaurant_assistant_conversations WHERE id = '$ASSISTANT_CONVERSATION_ID'::uuid;")"
assistant_deleted_title="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1  -c "SELECT title FROM public.restaurant_assistant_conversations WHERE id = '$ASSISTANT_CONVERSATION_ID'::uuid;")"
assistant_deleted_messages="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1  -c "SELECT count(*)::text FROM public.restaurant_assistant_messages WHERE conversation_id = '$ASSISTANT_CONVERSATION_ID'::uuid;")"
if [ "$assistant_deleted_at" != "true" ] || [ "$assistant_deleted_title" != "Conversa eliminada" ] || [ "$assistant_deleted_messages" != "0" ]; then
  echo "ERROR: assistant deletion did not retain only a content-free tombstone." >&2
  exit 1
fi

if psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
INSERT INTO public.restaurant_assistant_conversations (
  id, restaurant_id, title, created_by
) VALUES (
  '$ASSISTANT_CONVERSATION_ID'::uuid, '$RESTAURANT_A'::uuid,
  'Conversa restaurada por cópia antiga', '$USER_A'::uuid
)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, updated_at = now();
COMMIT;
SQL
then
  echo "ERROR: a stale client restored a deleted assistant conversation." >&2
  exit 1
fi

if psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
INSERT INTO public.restaurant_assistant_messages (
  id, restaurant_id, conversation_id, role, content, created_by
) VALUES (
  '99999999-9999-4999-8999-999999999999'::uuid,
  '$RESTAURANT_A'::uuid, '$ASSISTANT_CONVERSATION_ID'::uuid,
  'user', 'Mensagem tardia após eliminação.', '$USER_A'::uuid
);
COMMIT;
SQL
then
  echo "ERROR: a message was inserted into a deleted assistant conversation." >&2
  exit 1
fi

assistant_messages_after_replay="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1  -c "SELECT count(*)::text FROM public.restaurant_assistant_messages WHERE conversation_id = '$ASSISTANT_CONVERSATION_ID'::uuid;")"
if [ "$assistant_messages_after_replay" != "0" ]; then
  echo "ERROR: stale client activity restored assistant message content after deletion." >&2
  exit 1
fi

echo "PASS: assistant deletion is owner-only, removes message content, and cannot be undone by stale clients."

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

if psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT public.delete_assistant_conversation('$RESTAURANT_A'::uuid, '$ASSISTANT_CONVERSATION_ID'::uuid);
COMMIT;
SQL
then
  echo "ERROR: a staff member deleted the owner's assistant conversation." >&2
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

# Set a deliberately partial matrix for the read/insert dependency checks.
psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.set_restaurant_staff_permissions('$USER_C'::uuid, ARRAY['orders','reservations']);
COMMIT;
SQL

# Menu and table data needed by order/reservation workflows is read-only.
# The menu permission is still required to read priced menu items and their
# private cost fields; reservations can read table options without editing tables.
table_read_rows="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT count(*)::text FROM public.restaurant_tables
WHERE restaurant_id = '$RESTAURANT_A'::uuid AND table_number = 1;
ROLLBACK;
SQL
)"
menu_read_rows="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT count(*)::text FROM public.menu_items WHERE id = '$MENU_PERMISSION_PROBE'::uuid;
ROLLBACK;
SQL
)"
if [ "$table_read_rows" != "1" ] || [ "$menu_read_rows" != "0" ]; then
  echo "ERROR: reservation permission should allow table options only, not menu data." >&2
  exit 1
fi

ORDER_INSERT_PERMISSION_PROBE="88888888-8888-4888-8888-88888888888a"
if psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
INSERT INTO public.restaurant_orders (
  id, restaurant_id, menu_item_id, table_number, item_name, amount, quantity, service_course, notes, status
) VALUES (
  '$ORDER_INSERT_PERMISSION_PROBE'::uuid, '$RESTAURANT_A'::uuid, '$MENU_PERMISSION_PROBE'::uuid,
  1, 'Permission-gated order', 5.00, 1, 'Principal', '', 'Aberto'
);
COMMIT;
SQL
then
  echo "ERROR: an employee without the menu permission inserted an order through the Data API." >&2
  exit 1
fi

# Grant the menu area; existing reservations access already grants read-only
# table options. The employee may now create orders without being allowed to
# modify table definitions.
psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.set_restaurant_staff_permissions('$USER_C'::uuid, ARRAY['orders','reservations','menu']);
COMMIT;
SQL
psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
INSERT INTO public.restaurant_orders (
  id, restaurant_id, menu_item_id, table_number, item_name, amount, quantity, service_course, notes, status
) VALUES (
  '$ORDER_INSERT_PERMISSION_PROBE'::uuid, '$RESTAURANT_A'::uuid, '$MENU_PERMISSION_PROBE'::uuid,
  1, 'Permission-gated order', 5.00, 1, 'Principal', '', 'Aberto'
);
ROLLBACK;
SQL
echo "PASS: staff order creation needs menu and read-only table access; existing order updates stay separate."

# Restore the full matrix for downstream loyalty and owner-operation tests.
psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.set_restaurant_staff_permissions(
  '$USER_C'::uuid, ARRAY['orders','tables','reservations','menu','inventory','loyalty']
);
COMMIT;
SQL

# A separate staff user belongs to restaurant B for cross-restaurant QR/rescue tests.
STAFF_JOIN_D="$(join_staff_member "$USER_D" "$STAFF_CODE_B")"
EXPECTED_STAFF_JOIN_D="$RESTAURANT_B|CI Restaurant B|staff"
if [ "$STAFF_JOIN_D" != "$EXPECTED_STAFF_JOIN_D" ]; then
  echo "ERROR: staff D could not join restaurant B for cross-restaurant authorization tests." >&2
  exit 1
fi

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

# Loyalty security and QR lifecycle: points are credited only from a single-use
# customer QR. The test data lives in this disposable PostgreSQL CI database.
LOYALTY_QR_REQUEST="77777777-7777-4777-8777-777777777777"
LOYALTY_QR_SECOND_REQUEST="88888888-8888-4888-8888-888888888888"
LOYALTY_REDEEM_REQUEST="99999999-1111-4111-8111-111111111111"
LOYALTY_REWARD_ID="99999999-2222-4222-8222-222222222222"

# The previous generic adjustment RPC must no longer be callable by clients.
if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT * FROM public.adjust_loyalty_points_idempotent(
  '$LOYALTY_QR_REQUEST'::uuid, '$MEMBER_A'::uuid, 10, 'Bypass attempt'
);
COMMIT;
SQL
then
  echo "ERROR: legacy direct loyalty point adjustment is still executable by authenticated users." >&2
  exit 1
fi

# Owner creates the reward; staff cannot change the catalogue.
psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.save_loyalty_reward(
  '$RESTAURANT_A'::uuid, '$LOYALTY_REWARD_ID'::uuid,
  'CI Coffee Reward', 'One coffee for the customer', 10, 1, true
);
COMMIT;
SQL

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT public.save_loyalty_reward(
  '$RESTAURANT_A'::uuid, '$LOYALTY_REWARD_ID'::uuid,
  'Changed by staff', 'Unauthorized', 1, 1, true
);
COMMIT;
SQL
then
  echo "ERROR: staff unexpectedly changed the owner-managed loyalty shop." >&2
  exit 1
fi

customer_reward_count="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_E', true);
SET LOCAL ROLE authenticated;
SELECT count(*)::text FROM public.get_customer_loyalty_rewards('$MEMBER_A'::uuid);
ROLLBACK;
SQL
)"
if [ "$customer_reward_count" != "1" ]; then
  echo "ERROR: customer A did not see exactly their restaurant's active reward." >&2
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
  echo "ERROR: customer A unexpectedly queried the loyalty catalogue through customer B's card." >&2
  exit 1
fi

# A new QR replaces a previous unused QR. A staff member from another restaurant
# cannot use the current token.
LOYALTY_QR_OLD="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_E', true);
SET LOCAL ROLE authenticated;
SELECT qr_token::text FROM public.create_loyalty_qr_token();
COMMIT;
SQL
)"
LOYALTY_QR_CURRENT="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_E', true);
SET LOCAL ROLE authenticated;
SELECT qr_token::text FROM public.create_loyalty_qr_token();
COMMIT;
SQL
)"
test -n "$LOYALTY_QR_OLD"
test -n "$LOYALTY_QR_CURRENT"
test "$LOYALTY_QR_OLD" != "$LOYALTY_QR_CURRENT"

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT * FROM public.award_loyalty_points_from_qr(
  '$LOYALTY_QR_OLD'::uuid, 10, '$LOYALTY_QR_REQUEST'::uuid, 'CI receipt'
);
COMMIT;
SQL
then
  echo "ERROR: a superseded customer QR was accepted." >&2
  exit 1
fi

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_D', true);
SET LOCAL ROLE authenticated;
SELECT * FROM public.award_loyalty_points_from_qr(
  '$LOYALTY_QR_CURRENT'::uuid, 10, '$LOYALTY_QR_REQUEST'::uuid, 'CI receipt'
);
COMMIT;
SQL
then
  echo "ERROR: staff from restaurant B unexpectedly credited a QR belonging to restaurant A." >&2
  exit 1
fi

# A staff member with the loyalty area disabled cannot use a valid customer QR.
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
SELECT * FROM public.award_loyalty_points_from_qr(
  '$LOYALTY_QR_CURRENT'::uuid, 10, '77777777-7777-4777-8777-777777777778'::uuid, 'Blocked loyalty permission'
);
COMMIT;
SQL
then
  echo "ERROR: a staff member used the loyalty QR flow after the loyalty permission was disabled." >&2
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

LOYALTY_INVOICE_FIRST="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT qr_token::text || '|' || points::text
FROM public.issue_loyalty_invoice_qr(
  '$MEMBER_A'::uuid, 20.00, 'CI-valid-loyalty-permission', '77777777-7777-4777-8777-777777777780'::uuid
);
COMMIT;
SQL
)"
LOYALTY_INVOICE_RETRY="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT qr_token::text || '|' || points::text
FROM public.issue_loyalty_invoice_qr(
  '$MEMBER_A'::uuid, 20.00, 'CI-valid-loyalty-permission', '77777777-7777-4777-8777-777777777780'::uuid
);
COMMIT;
SQL
)"
if [ "$LOYALTY_INVOICE_FIRST" != "$LOYALTY_INVOICE_RETRY" ] ||
   [[ "$LOYALTY_INVOICE_FIRST" != *"|20" ]]; then
  echo "ERROR: permitted invoice-QR issuance was not successful and idempotent." >&2
  printf 'First: %s\nRetry: %s\n' "$LOYALTY_INVOICE_FIRST" "$LOYALTY_INVOICE_RETRY" >&2
  exit 1
fi

# Correct staff member credits points. Same request ID is safe to replay;
# a different request ID cannot consume the QR a second time.
LOYALTY_FIRST="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT points::text FROM public.award_loyalty_points_from_qr(
  '$LOYALTY_QR_CURRENT'::uuid, 10, '$LOYALTY_QR_REQUEST'::uuid, 'CI receipt'
);
COMMIT;
SQL
)"
LOYALTY_RETRY="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT points::text FROM public.award_loyalty_points_from_qr(
  '$LOYALTY_QR_CURRENT'::uuid, 10, '$LOYALTY_QR_REQUEST'::uuid, 'CI receipt'
);
COMMIT;
SQL
)"
if [ "$LOYALTY_FIRST" != "10" ] || [ "$LOYALTY_RETRY" != "$LOYALTY_FIRST" ]; then
  echo "ERROR: the QR award or its safe retry returned an unexpected balance." >&2
  exit 1
fi

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT * FROM public.award_loyalty_points_from_qr(
  '$LOYALTY_QR_CURRENT'::uuid, 10, '$LOYALTY_QR_SECOND_REQUEST'::uuid, 'Second use'
);
COMMIT;
SQL
then
  echo "ERROR: the same single-use QR was accepted with a second request identifier." >&2
  exit 1
fi

loyalty_balance_ok="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT points = 10 FROM public.loyalty_members WHERE id = '$MEMBER_A'::uuid;")"
loyalty_transaction_ok="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*) = 1 FROM public.loyalty_transactions WHERE member_id = '$MEMBER_A'::uuid AND created_by = '$USER_C'::uuid AND client_request_id = '$LOYALTY_QR_REQUEST'::uuid;")"
qr_consumed="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT used_at IS NOT NULL FROM private.loyalty_qr_tokens WHERE token = '$LOYALTY_QR_CURRENT'::uuid;")"
if [ "$loyalty_balance_ok" != "t" ] || [ "$loyalty_transaction_ok" != "t" ] || [ "$qr_consumed" != "t" ]; then
  echo "ERROR: QR award did not atomically update the balance, ledger and one-use token." >&2
  exit 1
fi
echo "PASS: QR replacement, restaurant authorization, one-use enforcement and safe retry."

# Customer redeems the reward once. The same request key returns the original
# redemption; points and stock are updated atomically.
LOYALTY_REDEMPTION_FIRST="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_E', true);
SET LOCAL ROLE authenticated;
SELECT redemption_id::text || '|' || points_remaining::text || '|' || status
FROM public.redeem_loyalty_reward('$LOYALTY_REWARD_ID'::uuid, '$LOYALTY_REDEEM_REQUEST'::uuid);
COMMIT;
SQL
)"
LOYALTY_REDEMPTION_RETRY="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_E', true);
SET LOCAL ROLE authenticated;
SELECT redemption_id::text || '|' || points_remaining::text || '|' || status
FROM public.redeem_loyalty_reward('$LOYALTY_REWARD_ID'::uuid, '$LOYALTY_REDEEM_REQUEST'::uuid);
COMMIT;
SQL
)"
if [ "$LOYALTY_REDEMPTION_FIRST" != "$LOYALTY_REDEMPTION_RETRY" ] ||
   [[ "$LOYALTY_REDEMPTION_FIRST" != *"|0|pending" ]]; then
  echo "ERROR: reward redemption was not idempotent or did not deduct the expected points." >&2
  printf 'First: %s\nRetry: %s\n' "$LOYALTY_REDEMPTION_FIRST" "$LOYALTY_REDEMPTION_RETRY" >&2
  exit 1
fi
LOYALTY_REDEMPTION_ID="$(cut -d '|' -f 1 <<<"$LOYALTY_REDEMPTION_FIRST")"

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_F', true);
SET LOCAL ROLE authenticated;
SELECT * FROM public.redeem_loyalty_reward('$LOYALTY_REWARD_ID'::uuid, '$LOYALTY_QR_SECOND_REQUEST'::uuid);
COMMIT;
SQL
then
  echo "ERROR: a customer from restaurant B unexpectedly redeemed restaurant A's reward." >&2
  exit 1
fi

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_D', true);
SET LOCAL ROLE authenticated;
SELECT public.fulfill_loyalty_reward_redemption('$LOYALTY_REDEMPTION_ID'::uuid);
COMMIT;
SQL
then
  echo "ERROR: staff from restaurant B unexpectedly fulfilled restaurant A's redemption." >&2
  exit 1
fi

# The loyalty permission also controls delivery confirmation.
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
SELECT public.fulfill_loyalty_reward_redemption('$LOYALTY_REDEMPTION_ID'::uuid);
COMMIT;
SQL
then
  echo "ERROR: a staff member confirmed a reward delivery after loyalty permission was disabled." >&2
  exit 1
fi

# The same permission protects invoice-QR issuance and the staff-facing loyalty catalogue/history.
if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT * FROM public.issue_loyalty_invoice_qr(
  '$MEMBER_A'::uuid, 20.00, 'CI-revoked-loyalty-permission', '77777777-7777-4777-8777-777777777779'::uuid
);
COMMIT;
SQL
then
  echo "ERROR: staff issued an invoice QR after loyalty permission was disabled." >&2
  exit 1
fi

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT count(*) FROM public.get_restaurant_loyalty_rewards('$RESTAURANT_A'::uuid);
COMMIT;
SQL
then
  echo "ERROR: staff read the restaurant loyalty catalogue after loyalty permission was disabled." >&2
  exit 1
fi

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT count(*) FROM public.get_restaurant_loyalty_reward_redemptions('$RESTAURANT_A'::uuid);
COMMIT;
SQL
then
  echo "ERROR: staff read customer reward redemptions after loyalty permission was disabled." >&2
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

# The correct restaurant's staff confirms delivery; a second confirmation is a no-op.
psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT public.fulfill_loyalty_reward_redemption('$LOYALTY_REDEMPTION_ID'::uuid);
SELECT public.fulfill_loyalty_reward_redemption('$LOYALTY_REDEMPTION_ID'::uuid);
COMMIT;
SQL

loyalty_redemption_state="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT status || '|' || fulfilled_by::text FROM public.loyalty_reward_redemptions WHERE id = '$LOYALTY_REDEMPTION_ID'::uuid;")"
loyalty_final_balance="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT points::text FROM public.loyalty_members WHERE id = '$MEMBER_A'::uuid;")"
loyalty_ledger_count="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT count(*) FROM public.loyalty_transactions WHERE member_id = '$MEMBER_A'::uuid;")"
reward_stock_count="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 \
  -c "SELECT redeemed_count FROM public.loyalty_rewards WHERE id = '$LOYALTY_REWARD_ID'::uuid;")"
if [ "$loyalty_redemption_state" != "fulfilled|$USER_C" ] ||
   [ "$loyalty_final_balance" != "0" ] ||
   [ "$loyalty_ledger_count" != "2" ] ||
   [ "$reward_stock_count" != "1" ]; then
  echo "ERROR: reward fulfillment, points ledger, balance or stock count is inconsistent." >&2
  printf 'Redemption: %s; balance: %s; ledger rows: %s; stock count: %s\n' \
    "$loyalty_redemption_state" "$loyalty_final_balance" "$loyalty_ledger_count" "$reward_stock_count" >&2
  exit 1
fi
echo "PASS: reward redemption is one-time, stock-limited, customer-scoped and staff-fulfilled."

# Customer booking requests are account-scoped and idempotent; the restaurant assigns
# a suitable table and must do so before confirming the request.
CUSTOMER_BOOKING_AT="$(date -u -d '+40 days' '+%Y-%m-%d %H:%M:%S+00:00')"
CUSTOMER_BOOKING_LATER="$(date -u -d '+40 days 3 hours' '+%Y-%m-%d %H:%M:%S+00:00')"
CUSTOMER_BOOKING_REQUEST_A="a1111111-1111-4111-8111-111111111111"
CUSTOMER_BOOKING_REQUEST_B="b2222222-2222-4222-8222-222222222222"
CUSTOMER_BOOKING_REQUEST_C="c3333333-3333-4333-8333-333333333333"
CUSTOMER_BOOKING_REQUEST_D="d4444444-4444-4444-8444-444444444444"

psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
INSERT INTO public.restaurant_tables (restaurant_id, table_number, is_occupied, area, seats)
VALUES ('$RESTAURANT_A'::uuid, 99, false, 'CI Booking', 2);
SQL

create_customer_booking() {
  local user_id="$1" request_id="$2" guest_name="$3" booking_at="$4"
  psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
SELECT reservation_id::text || '|' || status
FROM public.create_customer_reservation_request(
  '$request_id'::uuid, '$guest_name', '', 2, '$booking_at'::timestamptz, 90, 'CI customer booking'
);
COMMIT;
SQL
}

my_customer_booking_count() {
  local user_id="$1"
  psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$user_id', true);
SET LOCAL ROLE authenticated;
SELECT count(*)::text FROM public.get_my_restaurant_reservations();
ROLLBACK;
SQL
}

CUSTOMER_BOOKING_FIRST="$(create_customer_booking "$USER_E" "$CUSTOMER_BOOKING_REQUEST_A" "CI Customer A" "$CUSTOMER_BOOKING_AT")"
CUSTOMER_BOOKING_RETRY="$(create_customer_booking "$USER_E" "$CUSTOMER_BOOKING_REQUEST_A" "CI Customer A" "$CUSTOMER_BOOKING_AT")"
if [ "$CUSTOMER_BOOKING_FIRST" != "$CUSTOMER_BOOKING_RETRY" ] ||
   [[ "$CUSTOMER_BOOKING_FIRST" != *"|Pendente" ]]; then
  echo "ERROR: customer booking retry did not return the original pending request." >&2
  exit 1
fi
CUSTOMER_BOOKING_ID="$(cut -d '|' -f 1 <<<"$CUSTOMER_BOOKING_FIRST")"
booking_request_count="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1   -c "SELECT count(*)::text FROM public.restaurant_reservations WHERE restaurant_id='$RESTAURANT_A'::uuid AND created_by='$USER_E'::uuid AND client_request_id='$CUSTOMER_BOOKING_REQUEST_A'::uuid;")"
if [ "$booking_request_count" != "1" ] ||
   [ "$(my_customer_booking_count "$USER_E")" != "1" ] ||
   [ "$(my_customer_booking_count "$USER_F")" != "0" ]; then
  echo "ERROR: customer booking was duplicated or visible to another customer's account." >&2
  exit 1
fi

CUSTOMER_BOOKING_B="$(create_customer_booking "$USER_F" "$CUSTOMER_BOOKING_REQUEST_B" "CI Customer B" "$CUSTOMER_BOOKING_AT")"
CUSTOMER_BOOKING_B_ID="$(cut -d '|' -f 1 <<<"$CUSTOMER_BOOKING_B")"
if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_E', true);
SET LOCAL ROLE authenticated;
SELECT public.cancel_my_restaurant_reservation('$CUSTOMER_BOOKING_B_ID'::uuid);
COMMIT;
SQL
then
  echo "ERROR: customer A unexpectedly cancelled customer B's reservation." >&2
  exit 1
fi

psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_E', true);
SET LOCAL ROLE authenticated;
SELECT public.cancel_my_restaurant_reservation('$CUSTOMER_BOOKING_ID'::uuid);
SELECT public.cancel_my_restaurant_reservation('$CUSTOMER_BOOKING_ID'::uuid);
COMMIT;
SQL
customer_cancel_status="$(psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1   -c "SELECT status FROM public.restaurant_reservations WHERE id='$CUSTOMER_BOOKING_ID'::uuid;")"
if [ "$customer_cancel_status" != "Cancelada" ]; then
  echo "ERROR: customer's own cancellation did not persist or was not idempotent." >&2
  exit 1
fi

CUSTOMER_BOOKING_C="$(create_customer_booking "$USER_E" "$CUSTOMER_BOOKING_REQUEST_C" "CI Customer A" "$CUSTOMER_BOOKING_LATER")"
CUSTOMER_BOOKING_C_ID="$(cut -d '|' -f 1 <<<"$CUSTOMER_BOOKING_C")"
if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_C', true);
SET LOCAL ROLE authenticated;
SELECT public.update_restaurant_reservation_status('$CUSTOMER_BOOKING_C_ID'::uuid, 'Confirmada');
COMMIT;
SQL
then
  echo "ERROR: staff confirmed a customer booking without assigning a table." >&2
  exit 1
fi
# Preserve the original restaurant-side reservation authorization checks.
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

# The reservation confirmation rule now requires a real table assignment.
psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.assign_restaurant_reservation_table('$RESERVATION_A'::uuid, 99);
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

# Regression coverage for table assignment, exact time boundaries and customer retries.
# All records remain in this disposable PostgreSQL CI database.
create_restaurant_booking_on_table() {
  local request_id="$1" guest_name="$2" booking_at="$3"
  local party_size="$4" table_number="$5"
  psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 <<SQL | tail -n 1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.create_restaurant_reservation_idempotent(
  '$request_id'::uuid, '$RESTAURANT_A'::uuid, '$guest_name', '',
  '$party_size'::integer, '$booking_at'::timestamptz, 60,
  '$table_number'::integer, 'CI table allocation regression'
);
COMMIT;
SQL
}

# The first booking ends at RESERVATION_AT + 60 minutes. A booking beginning
# exactly then must be accepted; an overlapping booking must be rejected.
BOUNDARY_BOOKING_REQUEST="f1111111-1111-4111-8111-111111111111"
BOUNDARY_BOOKING_AT="$(date -u -d "$RESERVATION_AT + 60 minutes" '+%Y-%m-%d %H:%M:%S+00:00')"
BOUNDARY_BOOKING_FIRST="$(create_restaurant_booking_on_table "$BOUNDARY_BOOKING_REQUEST" "CI Adjacent Booking" "$BOUNDARY_BOOKING_AT" 2 99)"
BOUNDARY_BOOKING_RETRY="$(create_restaurant_booking_on_table "$BOUNDARY_BOOKING_REQUEST" "CI Adjacent Booking" "$BOUNDARY_BOOKING_AT" 2 99)"
if [ -z "$BOUNDARY_BOOKING_FIRST" ] || [ "$BOUNDARY_BOOKING_FIRST" != "$BOUNDARY_BOOKING_RETRY" ]; then
  echo "ERROR: an adjacent table reservation was rejected or its retry created a duplicate." >&2
  exit 1
fi
echo "PASS: a reservation starting exactly when the previous one ends is accepted and idempotent."

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.create_restaurant_reservation_idempotent(
  'f2222222-2222-4222-8222-222222222222'::uuid, '$RESTAURANT_A'::uuid,
  'CI Overlapping Booking', '', 2, ('$RESERVATION_AT'::timestamptz + interval '30 minutes'),
  60, 99, 'Expected overlap rejection'
);
COMMIT;
SQL
then
  echo "ERROR: the server accepted two overlapping active reservations on table 99." >&2
  exit 1
fi
echo "PASS: overlapping reservations for the same table are rejected."

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.create_restaurant_reservation_idempotent(
  'f3333333-3333-4333-8333-333333333333'::uuid, '$RESTAURANT_A'::uuid,
  'CI Too Many Guests', '', 3, ('$RESERVATION_AT'::timestamptz + interval '3 hours'),
  60, 99, 'Expected capacity rejection'
);
COMMIT;
SQL
then
  echo "ERROR: the server assigned a two-seat table to three guests." >&2
  exit 1
fi
echo "PASS: table capacity is enforced by the database."

if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.create_restaurant_reservation_idempotent(
  'f4444444-4444-4444-8444-444444444444'::uuid, '$RESTAURANT_A'::uuid,
  'CI Missing Table', '', 2, ('$RESERVATION_AT'::timestamptz + interval '4 hours'),
  60, 98, 'Expected missing table rejection'
);
COMMIT;
SQL
then
  echo "ERROR: the server accepted a reservation on a table that does not exist." >&2
  exit 1
fi
echo "PASS: reservations cannot reference a table outside the restaurant."

# Customer retry must still return the original booking after the restaurant
# assigns a table and confirms it.
psql --no-psqlrc --set ON_ERROR_STOP=1 <<SQL >/dev/null
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.assign_restaurant_reservation_table('$CUSTOMER_BOOKING_C_ID'::uuid, 99);
SELECT public.update_restaurant_reservation_status('$CUSTOMER_BOOKING_C_ID'::uuid, 'Confirmada');
COMMIT;
SQL
CUSTOMER_BOOKING_C_RETRY="$(create_customer_booking "$USER_E" "$CUSTOMER_BOOKING_REQUEST_C" "CI Customer A" "$CUSTOMER_BOOKING_LATER")"
CUSTOMER_BOOKING_C_RETRY_ID="$(cut -d '|' -f 1 <<<"$CUSTOMER_BOOKING_C_RETRY")"
if [ "$(cut -d '|' -f 1 <<<"$CUSTOMER_BOOKING_C")" != "$CUSTOMER_BOOKING_C_RETRY_ID" ] ||
   [[ "$CUSTOMER_BOOKING_C_RETRY" != *"|Confirmada" ]]; then
  echo "ERROR: customer retry did not return the same confirmed reservation after table assignment." >&2
  exit 1
fi
echo "PASS: customer retries remain idempotent after table assignment and confirmation."

# The restaurant must also reject assigning an unallocated customer request
# to a table already occupied during that time window.
CUSTOMER_BOOKING_D_AT="$(date -u -d "$RESERVATION_AT + 30 minutes" '+%Y-%m-%d %H:%M:%S+00:00')"
CUSTOMER_BOOKING_D="$(create_customer_booking "$USER_E" "$CUSTOMER_BOOKING_REQUEST_D" "CI Customer A" "$CUSTOMER_BOOKING_D_AT")"
CUSTOMER_BOOKING_D_ID="$(cut -d '|' -f 1 <<<"$CUSTOMER_BOOKING_D")"
if psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
BEGIN;
SELECT set_config('request.jwt.claim.sub', '$USER_A', true);
SET LOCAL ROLE authenticated;
SELECT public.assign_restaurant_reservation_table('$CUSTOMER_BOOKING_D_ID'::uuid, 99);
COMMIT;
SQL
then
  echo "ERROR: the server assigned an overlapping customer request to table 99." >&2
  exit 1
fi
echo "PASS: assigning a table to an existing customer request rechecks schedule conflicts."



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
