-- Defense in depth: prevent accidental direct access to private restaurant join codes.
-- No client-facing policies are intended. Trusted SECURITY DEFINER functions owned by
-- postgres retain the access they need; anon/authenticated have no table privileges.
ALTER TABLE private.restaurant_join_codes ENABLE ROW LEVEL SECURITY;
