package pt.studiomesa.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class RestaurantStore(context: Context) {
    private val prefs = context.getSharedPreferences("studiomesa_data", Context.MODE_PRIVATE)

    init {
        seedDemoTablesIfNeeded()
    }

    fun resetDemoData() {
        prefs.edit().clear().apply()
        seedDemoTablesIfNeeded()
        getRestaurantProfile()
        getMenu()
        getOrders()
        getReservations()
    }

    private fun seedDemoTablesIfNeeded() {
        if (prefs.getBoolean("demo_tables_seeded", false)) return
        prefs.edit()
            .putBoolean("table_2", true)
            .putBoolean("table_4", true)
            .putBoolean("table_7", true)
            .putBoolean("demo_tables_seeded", true)
            .apply()
    }

    fun getRestaurantProfile(): RestaurantProfile = RestaurantProfile(
        name = prefs.getString("restaurant_name", "Casa do Bairro") ?: "Casa do Bairro",
        tagline = prefs.getString("restaurant_tagline", "Cozinha portuguesa · À mesa com tempo.") ?: "Cozinha portuguesa · À mesa com tempo.",
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
                MenuEntry("Hambúrguer da Casa", 12.50, 3.80),
                MenuEntry("Bitoque à Portuguesa", 13.50, 4.20),
                MenuEntry("Bacalhau no Forno", 18.50, 6.10),
                MenuEntry("Polvo à Lagareiro", 20.50, 7.40),
                MenuEntry("Limonada Caseira", 3.00, 0.65)
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
        val raw = prefs.getString("orders_json", null)
        if (raw == null) {
            val now = System.currentTimeMillis()
            val initial = mutableListOf(
                OrderEntry(now - 10_000L, 2, "Bitoque à Portuguesa", 13.50, "Aberto", now - 10 * 60_000L),
                OrderEntry(now - 20_000L, 4, "Hambúrguer da Casa", 12.50, "Concluído", now - 40 * 60_000L),
                OrderEntry(now - 30_000L, 7, "Bacalhau no Forno", 18.50, "Aberto", now - 4 * 60_000L)
            )
            saveOrders(initial)
            return initial.sortedByDescending { it.createdAt }.toMutableList()
        }
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

    private fun demoBookingTime(daysAhead: Int, hour: Int, minute: Int): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, daysAhead)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, hour)
        calendar.set(java.util.Calendar.MINUTE, minute)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun getReservations(): MutableList<ReservationEntry> {
        val raw = prefs.getString("reservations_json", null)
        if (raw == null) {
            val now = System.currentTimeMillis()
            val initial = mutableListOf(
                ReservationEntry(now - 1_000L, "Mariana Costa", "000 000 001", demoBookingTime(1, 19, 0), 2, "Mesa junto à janela", "Pendente", now - 5 * 60_000L),
                ReservationEntry(now - 2_000L, "João Ferreira", "000 000 002", demoBookingTime(1, 20, 0), 4, "Aniversário", "Confirmada", now - 25 * 60_000L),
                ReservationEntry(now - 3_000L, "Inês Almeida", "000 000 003", demoBookingTime(2, 13, 0), 3, "Cadeira de bebé", "Pendente", now - 50 * 60_000L)
            )
            saveReservations(initial)
            return initial.sortedBy { it.dateTime }.toMutableList()
        }
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
