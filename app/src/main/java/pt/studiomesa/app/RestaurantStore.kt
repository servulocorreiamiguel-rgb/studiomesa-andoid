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
}
