-- Ensure a team access code can identify at most one restaurant.
-- The column is already NOT NULL; the partial index matches the live migration
-- and ensures every valid code is unique.
CREATE UNIQUE INDEX IF NOT EXISTS restaurant_join_codes_staff_code_key
  ON private.restaurant_join_codes (staff_code)
  WHERE staff_code IS NOT NULL;
