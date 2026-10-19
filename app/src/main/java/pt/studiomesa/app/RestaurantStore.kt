package pt.studiomesa.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class RestaurantStore(context: Context) {
    private val prefs = context.getSharedPreferences("studiomesa_data", Context.MODE_PRIVATE)

    fun getMenu(): MutableList<MenuEntry> {
        val raw = prefs.getString("menu_json", null)
        if (raw == null) {
            val initial = mutableListOf(
                MenuEntry("Entrada da casa · exemplo", 6.50, 2.10),
                MenuEntry("Prato principal · exemplo", 14.50, 4.80),
                MenuEntry("Bebida · exemplo", 2.50, 0.55)
            )
            saveMenu(initial)
            return initial
        }
        val result = mutableListOf<MenuEntry>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                result.add(
                    MenuEntry(
                        item.optString("name"),
                        item.optDouble("price", 0.0),
                        item.optDouble("cost", 0.0)
                    )
                )
            }
        } catch (_: Exception) {
            return mutableListOf()
        }
        return result
    }

    fun saveMenu(items: List<MenuEntry>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("name", item.name)
                    .put("price", item.price)
                    .put("cost", item.cost)
            )
        }
        prefs.edit().putString("menu_json", array.toString()).apply()
    }

    fun isTableOccupied(table: Int): Boolean =
        prefs.getBoolean("table_" + table, false)

    fun setTableOccupied(table: Int, occupied: Boolean) {
        prefs.edit().putBoolean("table_" + table, occupied).apply()
    }

    fun occupiedTableCount(): Int = (1..12).count { isTableOccupied(it) }

    fun getOrders(): MutableList<OrderEntry> {
        val raw = prefs.getString("orders_json", "[]") ?: "[]"
        val result = mutableListOf<OrderEntry>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                result.add(
                    OrderEntry(
                        item.optLong("id"),
                        item.optInt("table", 1),
                        item.optString("itemName"),
                        item.optDouble("amount", 0.0),
                        item.optString("status", "Aberto"),
                        item.optLong("createdAt")
                    )
                )
            }
        } catch (_: Exception) {
            return mutableListOf()
        }
        return result.sortedByDescending { it.createdAt }.toMutableList()
    }

    fun addOrder(order: OrderEntry) {
        val orders = getOrders()
        orders.add(0, order)
        saveOrders(orders)
    }

    fun updateOrder(orderId: Long, status: String) {
        val orders = getOrders().map { order ->
            if (order.id == orderId) order.copy(status = status) else order
        }
        saveOrders(orders)
    }

    fun saveOrders(orders: List<OrderEntry>) {
        val array = JSONArray()
        orders.forEach { order ->
            array.put(
                JSONObject()
                    .put("id", order.id)
                    .put("table", order.table)
                    .put("itemName", order.itemName)
                    .put("amount", order.amount)
                    .put("status", order.status)
                    .put("createdAt", order.createdAt)
            )
        }
        prefs.edit().putString("orders_json", array.toString()).apply()
    }

    fun getCustomers(): MutableList<LoyaltyCustomer> {
        val raw = prefs.getString("loyalty_customers_json", "[]") ?: "[]"
        val result = mutableListOf<LoyaltyCustomer>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                result.add(
                    LoyaltyCustomer(
                        item.optString("name"),
                        item.optString("code"),
                        item.optInt("points", 0).coerceAtLeast(0),
                        item.optString("backendId").takeIf { it.isNotBlank() }
                    )
                )
            }
        } catch (_: Exception) {
            return mutableListOf()
        }
        return result
    }

    fun saveCustomers(customers: List<LoyaltyCustomer>) {
        val array = JSONArray()
        customers.forEach { customer ->
            array.put(
                JSONObject()
                    .put("name", customer.name)
                    .put("code", customer.code)
                    .put("points", customer.points.coerceAtLeast(0))
                    .put("backendId", customer.backendId ?: "")
            )
        }
        prefs.edit().putString("loyalty_customers_json", array.toString()).apply()
    }

    fun addLoyaltyCustomer(name: String): LoyaltyCustomer {
        val customers = getCustomers()
        var code: String
        do {
            code = "BM" + (System.currentTimeMillis() % 1_000_000L)
                .toString().padStart(6, '0')
        } while (customers.any { it.code == code })
        val customer = LoyaltyCustomer(name.trim(), code, 0)
        customers.add(customer)
        saveCustomers(customers)
        return customer
    }

    fun adjustCustomerPoints(code: String, delta: Int): Boolean {
        val customers = getCustomers()
        val index = customers.indexOfFirst { it.code == code }
        if (index < 0) return false
        val updatedPoints = customers[index].points + delta
        if (updatedPoints < 0) return false
        customers[index] = customers[index].copy(points = updatedPoints)
        saveCustomers(customers)
        return true
    }

    fun getCustomerProfileName(): String =
        prefs.getString("customer_profile_name", "") ?: ""

    fun saveCustomerProfileName(name: String) {
        prefs.edit().putString("customer_profile_name", name.trim()).apply()
    }

    fun getCustomerMemberCode(): String {
        val existing = prefs.getString("customer_member_code", null)
        if (!existing.isNullOrBlank()) return existing
        val generated = "BM" + (System.currentTimeMillis() % 1_000_000L)
            .toString().padStart(6, '0')
        prefs.edit().putString("customer_member_code", generated).apply()
        return generated
    }

}
