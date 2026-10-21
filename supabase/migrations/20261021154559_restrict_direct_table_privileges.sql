-- Keep client access to operational tables least-privileged.
-- The app uses authenticated REST calls for SELECT/INSERT/UPDATE; deletes and
-- administrative table privileges are not required by the client.
REVOKE ALL PRIVILEGES ON TABLE
  public.menu_items,
  public.restaurant_orders,
  public.restaurant_tables
FROM PUBLIC, anon, authenticated;

GRANT SELECT, INSERT, UPDATE ON TABLE
  public.menu_items,
  public.restaurant_orders,
  public.restaurant_tables
TO authenticated;
