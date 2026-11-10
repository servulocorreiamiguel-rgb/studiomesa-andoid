-- Cover the nullable auth-user foreign keys for safe account deletion checks.
BEGIN;
CREATE INDEX IF NOT EXISTS loyalty_invoice_qr_issued_by_idx
  ON private.loyalty_invoice_qr_codes (issued_by)
  WHERE issued_by IS NOT NULL;
CREATE INDEX IF NOT EXISTS loyalty_invoice_qr_redeemed_by_idx
  ON private.loyalty_invoice_qr_codes (redeemed_by)
  WHERE redeemed_by IS NOT NULL;
COMMIT;
