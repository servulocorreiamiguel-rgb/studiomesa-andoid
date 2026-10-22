-- Cover the private queue's restaurant foreign key for deletion checks and FK maintenance.
CREATE INDEX IF NOT EXISTS account_deletion_queue_restaurant_idx
  ON private.account_deletion_queue (restaurant_id)
  WHERE restaurant_id IS NOT NULL;
