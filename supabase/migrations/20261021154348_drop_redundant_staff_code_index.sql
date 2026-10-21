-- Remove the redundant unique index created by 20261021154106.
-- The existing restaurant_join_codes_staff_code_idx already uniquely indexes
-- this NOT NULL column, so one unique index is sufficient.
DROP INDEX IF EXISTS private.restaurant_join_codes_staff_code_key;
