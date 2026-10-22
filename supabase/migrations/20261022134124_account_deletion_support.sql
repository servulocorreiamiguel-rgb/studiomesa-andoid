-- Support verified self-service account deletion without orphaning a restaurant.
-- Owner data is deleted only after an explicit confirmation and only when no other
-- staff members remain. Historical loyalty transactions retain their audit rows,
-- with the deleted actor anonymized to NULL.

BEGIN;

ALTER TABLE public.loyalty_transactions
  ALTER COLUMN created_by DROP NOT NULL;

ALTER TABLE public.loyalty_transactions
  DROP CONSTRAINT IF EXISTS loyalty_transactions_created_by_fkey;

ALTER TABLE public.loyalty_transactions
  ADD CONSTRAINT loyalty_transactions_created_by_fkey
  FOREIGN KEY (created_by)
  REFERENCES auth.users(id)
  ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS private.account_deletion_queue (
  user_id uuid PRIMARY KEY,
  restaurant_id uuid NULL
    REFERENCES public.restaurants(id)
    ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  CONSTRAINT account_deletion_queue_expiry_valid
    CHECK (expires_at > created_at)
);

ALTER TABLE private.account_deletion_queue ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE private.account_deletion_queue FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.prepare_account_deletion(
  p_delete_restaurant boolean DEFAULT false
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_user_id uuid := (SELECT auth.uid());
  v_restaurant_id uuid;
  v_created_at timestamptz := clock_timestamp();
BEGIN
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão para eliminar a conta.';
  END IF;

  SELECT rs.restaurant_id
    INTO v_restaurant_id
  FROM public.restaurant_staff AS rs
  WHERE rs.user_id = v_user_id
    AND rs.role = 'owner'
  ORDER BY rs.created_at ASC
  LIMIT 1;

  IF v_restaurant_id IS NOT NULL THEN
    IF p_delete_restaurant IS DISTINCT FROM true THEN
      RAISE EXCEPTION 'A tua conta é proprietária de um restaurante. Confirma explicitamente a eliminação permanente do restaurante e dos respetivos dados.';
    END IF;

    IF EXISTS (
      SELECT 1
      FROM public.restaurant_staff AS rs
      WHERE rs.restaurant_id = v_restaurant_id
        AND rs.user_id <> v_user_id
    ) THEN
      RAISE EXCEPTION 'Antes de eliminar a conta de proprietário, remove os restantes membros da equipa. O restaurante e os seus dados partilhados não serão eliminados enquanto houver outros membros.';
    END IF;
  ELSIF p_delete_restaurant IS DISTINCT FROM false THEN
    RAISE EXCEPTION 'Esta conta não é proprietária de um restaurante. Atualiza o estado da conta e tenta novamente.';
  END IF;

  INSERT INTO private.account_deletion_queue (
    user_id, restaurant_id, created_at, expires_at
  )
  VALUES (
    v_user_id, v_restaurant_id, v_created_at, v_created_at + interval '15 minutes'
  )
  ON CONFLICT (user_id)
  DO UPDATE SET
    restaurant_id = EXCLUDED.restaurant_id,
    created_at = EXCLUDED.created_at,
    expires_at = EXCLUDED.expires_at;

  RETURN jsonb_build_object(
    'ready', true,
    'restaurant_data_will_be_deleted', v_restaurant_id IS NOT NULL
  );
END;
$function$;

REVOKE ALL ON FUNCTION public.prepare_account_deletion(boolean) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.prepare_account_deletion(boolean) TO authenticated;

CREATE OR REPLACE FUNCTION public.cancel_account_deletion()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_user_id uuid := (SELECT auth.uid());
BEGIN
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;

  DELETE FROM private.account_deletion_queue AS q
  WHERE q.user_id = v_user_id;
END;
$function$;

REVOKE ALL ON FUNCTION public.cancel_account_deletion() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.cancel_account_deletion() TO authenticated;

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
    DELETE FROM public.restaurants AS r
    WHERE r.id = v_restaurant_id;
  END IF;

  RETURN OLD;
END;
$function$;

REVOKE ALL ON FUNCTION private.finalize_account_deletion_after_auth_delete()
  FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS z_studiomesa_finalize_account_deletion ON auth.users;

CREATE TRIGGER z_studiomesa_finalize_account_deletion
AFTER DELETE ON auth.users
FOR EACH ROW
EXECUTE FUNCTION private.finalize_account_deletion_after_auth_delete();

COMMIT;
