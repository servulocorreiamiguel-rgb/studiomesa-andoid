-- Serialize owner account deletion with staff membership changes.
-- An existence check alone is insufficient: a second staff member could join just
-- after the check and before the restaurant delete. FOR UPDATE on the parent row
-- conflicts with the KEY SHARE lock acquired by the staff table's foreign key.

CREATE OR REPLACE FUNCTION private.finalize_account_deletion_after_auth_delete()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_restaurant_id uuid;
  v_locked_restaurant_id uuid;
BEGIN
  SELECT q.restaurant_id
    INTO v_restaurant_id
  FROM private.account_deletion_queue AS q
  WHERE q.user_id = OLD.id
    AND q.expires_at > clock_timestamp()
  FOR UPDATE;

  DELETE FROM private.account_deletion_queue AS q
  WHERE q.user_id = OLD.id;

  IF v_restaurant_id IS NOT NULL THEN
    -- Serialize against concurrent inserts into restaurant_staff. If another
    -- member joins first, the lock waits and the following EXISTS detects them.
    SELECT r.id
      INTO v_locked_restaurant_id
    FROM public.restaurants AS r
    WHERE r.id = v_restaurant_id
    FOR UPDATE;

    IF v_locked_restaurant_id IS NOT NULL THEN
      IF EXISTS (
        SELECT 1
        FROM public.restaurant_staff AS rs
        WHERE rs.restaurant_id = v_locked_restaurant_id
      ) THEN
        RAISE EXCEPTION
          'A equipa do restaurante mudou durante o pedido. A conta não foi eliminada; confirma os membros e tenta novamente.';
      END IF;

      DELETE FROM public.restaurants AS r
      WHERE r.id = v_locked_restaurant_id;
    END IF;
  END IF;

  RETURN OLD;
END;
$function$;

REVOKE ALL ON FUNCTION private.finalize_account_deletion_after_auth_delete()
  FROM PUBLIC, anon, authenticated;
