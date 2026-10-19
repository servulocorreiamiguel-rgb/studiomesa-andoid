package pt.studiomesa.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class RestaurantStore(context: Context) {
    private val prefs = context.getSharedPreferences("studiomesa_data", Context.MODE_PRIVATE)

    fun getRestaurantProfile(): RestaurantProfile = RestaurantProfile(
        name = prefs.getString("restaurant_name", "Restaurante de demonstração") ?: "Restaurante de demonstração",
        tagline = prefs.getString("restaurant_tagline", "Boa comida. Boas experiências.") ?: "Boa comida. Boas experiências.",
        phone = prefs.getString("restaurant_phone", "") ?: "",
        address = prefs.getString("restaurant_address", "") ?: "",
        accent = prefs.getString("restaurant_accent", "Dourado") ?: "Dourado"
    )

    fun saveRestaurantProfile(profile: RestaurantProfile) {
        prefs.edit()
            .putString("restaurant_name", profile.name)
            .putString("restaurant_tagline", profile.tagline)
            .putString("restaurant_phone", profile.phone)
            .putString("restaurant_address", profile.address)
            .putString("restaurant_accent", profile.accent)
            .apply()
    }

    fun getMenu(): MutableList<MenuEntry> {
        val raw = prefs.getString("menu_json", null)
        if (raw == null) {
            val initial = mutableListOf(
                MenuEntry("Hambúrguer da casa", 12.50, 3.80),
                MenuEntry("Bitoque", 11.90, 4.10),
                MenuEntry("Limonada", 2.50, 0.55)
            )
            saveMenu(initial)
            return initial
        }
        val result = mutableListOf<MenuEntry>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                result.add(MenuEntry(item.optString("name"), item.optDouble("price", 0.0), item.optDouble("cost", 0.0)))
            }
        } catch (_: Exception) {
            return mutableListOf()
        }
        return result
    }

    fun saveMenu(items: List<MenuEntry>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().put("name", item.name).put("price", item.price).put("cost", item.cost))
        }
        prefs.edit().putString("menu_json", array.toString()).apply()
    }

    fun isTableOccupied(table: Int): Boolean = prefs.getBoolean("table_" + table, false)

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

    fun getReservations(): MutableList<ReservationEntry> {
        val raw = prefs.getString("reservations_json", "[]") ?: "[]"
        val result = mutableListOf<ReservationEntry>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                result.add(
                    ReservationEntry(
                        id = item.optLong("id"),
                        customerName = item.optString("customerName"),
                        phone = item.optString("phone"),
                        dateTime = item.optLong("dateTime"),
                        partySize = item.optInt("partySize", 2),
                        notes = item.optString("notes"),
                        status = item.optString("status", "Pendente"),
                        createdAt = item.optLong("createdAt")
                    )
                )
            }
        } catch (_: Exception) {
            return mutableListOf()
        }
        return result.sortedBy { it.dateTime }.toMutableList()
    }

    fun addReservation(reservation: ReservationEntry) {
        val reservations = getReservations()
        reservations.add(reservation)
        saveReservations(reservations)
    }

    fun updateReservation(reservationId: Long, status: String) {
        val reservations = getReservations().map { reservation ->
            if (reservation.id == reservationId) reservation.copy(status = status) else reservation
        }
        saveReservations(reservations)
    }

    private fun saveReservations(reservations: List<ReservationEntry>) {
        val array = JSONArray()
        reservations.forEach { reservation ->
            array.put(
                JSONObject()
                    .put("id", reservation.id)
                    .put("customerName", reservation.customerName)
                    .put("phone", reservation.phone)
                    .put("dateTime", reservation.dateTime)
                    .put("partySize", reservation.partySize)
                    .put("notes", reservation.notes)
                    .put("status", reservation.status)
                    .put("createdAt", reservation.createdAt)
            )
        }
        prefs.edit().putString("reservations_json", array.toString()).apply()
    }
}
