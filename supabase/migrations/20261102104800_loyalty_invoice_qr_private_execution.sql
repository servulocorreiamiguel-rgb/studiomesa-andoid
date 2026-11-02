-- Keep the Data API endpoints as SECURITY INVOKER wrappers and move
-- privileged invoice-loyalty operations to the non-exposed private schema.
BEGIN;

ALTER FUNCTION public.issue_loyalty_invoice_qr(uuid, numeric, text, uuid) SET SCHEMA private;
ALTER FUNCTION private.issue_loyalty_invoice_qr(uuid, numeric, text, uuid)
  RENAME TO issue_loyalty_invoice_qr_internal;

ALTER FUNCTION public.redeem_loyalty_invoice_qr(uuid) SET SCHEMA private;
ALTER FUNCTION private.redeem_loyalty_invoice_qr(uuid)
  RENAME TO redeem_loyalty_invoice_qr_internal;

GRANT USAGE ON SCHEMA private TO authenticated;

REVOKE ALL ON FUNCTION private.issue_loyalty_invoice_qr_internal(uuid, numeric, text, uuid)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION private.issue_loyalty_invoice_qr_internal(uuid, numeric, text, uuid)
  TO authenticated;

REVOKE ALL ON FUNCTION private.redeem_loyalty_invoice_qr_internal(uuid)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION private.redeem_loyalty_invoice_qr_internal(uuid)
  TO authenticated;

CREATE FUNCTION public.issue_loyalty_invoice_qr(
  p_member_id uuid,
  p_amount numeric,
  p_invoice_reference text,
  p_client_request_id uuid
)
RETURNS TABLE (
  qr_token uuid,
  amount numeric,
  points integer,
  invoice_reference text,
  expires_at timestamptz
)
LANGUAGE sql
SECURITY INVOKER
SET search_path TO ''
AS $function$
  SELECT *
  FROM private.issue_loyalty_invoice_qr_internal(
    p_member_id, p_amount, p_invoice_reference, p_client_request_id
  );
$function$;

CREATE FUNCTION public.redeem_loyalty_invoice_qr(p_qr_token uuid)
RETURNS TABLE (
  member_id uuid,
  member_code text,
  points integer,
  points_awarded integer,
  invoice_reference text,
  amount numeric
)
LANGUAGE sql
SECURITY INVOKER
SET search_path TO ''
AS $function$
  SELECT *
  FROM private.redeem_loyalty_invoice_qr_internal(p_qr_token);
$function$;

REVOKE ALL ON FUNCTION public.issue_loyalty_invoice_qr(uuid, numeric, text, uuid)
  FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.issue_loyalty_invoice_qr(uuid, numeric, text, uuid)
  TO authenticated;

REVOKE ALL ON FUNCTION public.redeem_loyalty_invoice_qr(uuid)
  FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.redeem_loyalty_invoice_qr(uuid)
  TO authenticated;

COMMIT;
