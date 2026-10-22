-- Re-check restaurant membership inside the auth.users deletion transaction.
-- The first check happens when the request is prepared, while Auth deletes the user
-- in a separate transaction. A staff member could join in between those two steps.
-- Abort Auth deletion if anybody other than the deleting owner is still associated
-- with the restaurant; this preserves the account and all restaurant data.

CREATE OR REPLACE FUNCTION private.finalize_account_deletion_after_auth_delete()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_restaurant_id uuid;
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
    IF EXISTS (
      SELECT 1
      FROM public.restaurant_staff AS rs
      WHERE rs.restaurant_id = v_restaurant_id
    ) THEN
      RAISE EXCEPTION
        'A equipa do restaurante mudou durante o pedido. A conta não foi eliminada; confirma os membros e tenta novamente.';
    END IF;

    DELETE FROM public.restaurants AS r
    WHERE r.id = v_restaurant_id;
  END IF;

  RETURN OLD;
END;
$function$;

REVOKE ALL ON FUNCTION private.finalize_account_deletion_after_auth_delete()
  FROM PUBLIC, anon, authenticated;
