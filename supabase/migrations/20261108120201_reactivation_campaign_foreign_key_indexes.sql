-- Cover foreign keys in the new marketing tables for predictable joins and cascades.
CREATE INDEX IF NOT EXISTS restaurant_customer_notifications_member_idx
  ON public.restaurant_customer_notifications (member_id);
CREATE INDEX IF NOT EXISTS restaurant_customer_notifications_restaurant_idx
  ON public.restaurant_customer_notifications (restaurant_id);
CREATE INDEX IF NOT EXISTS restaurant_reactivation_campaigns_created_by_idx
  ON public.restaurant_reactivation_campaigns (created_by);
