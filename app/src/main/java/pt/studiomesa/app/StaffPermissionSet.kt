package pt.studiomesa.app

import org.json.JSONArray

internal object StaffPermissionSet {
    val all: List<String> = listOf(
        "orders", "tables", "reservations", "menu", "inventory", "loyalty"
    )

    fun fromValues(values: Iterable<String>): Set<String> {
        val parsed = linkedSetOf<String>()
        values.forEach { raw ->
            val permission = raw.trim()
            if (permission in all) parsed.add(permission)
        }
        return parsed
    }

    fun fromJson(value: JSONArray): Set<String> =
        fromValues((0 until value.length()).map { value.optString(it) })

    fun orderedValues(selected: Set<String>): List<String> {
        require(selected.all { it in all }) { "A lista contém permissões desconhecidas." }
        return all.filter { it in selected }
    }

    fun canCreateOrder(permissions: Set<String>, isStaff: Boolean): Boolean =
        !isStaff || (
            "orders" in permissions &&
                "menu" in permissions &&
                ("tables" in permissions || "reservations" in permissions)
            )

    fun cacheReadPermissions(permissions: Set<String>, isStaff: Boolean): Set<String> {
        if (!isStaff) return all.toSet()
        return buildSet {
            if ("orders" in permissions) add("orders")
            if ("menu" in permissions) add("menu")
            if ("tables" in permissions || "reservations" in permissions) add("tables")
        }
    }

    fun toJsonArray(selected: Set<String>): JSONArray =
        JSONArray().apply { orderedValues(selected).forEach { put(it) } }

    fun filterPages(pages: List<String>, permissions: Set<String>, isStaff: Boolean): List<String> {
        if (!isStaff) return pages
        val visible = pages.toMutableList()
        if ("tables" !in permissions) visible.remove("Mesas")
        if ("orders" !in permissions) {
            visible.remove("Pedidos")
            visible.remove("Cozinha")
        }
        if ("menu" !in permissions) visible.remove("Carta")
        if ("loyalty" !in permissions) visible.remove("Pontos")
        if ("reservations" !in permissions) visible.remove("Reservas")
        if ("inventory" !in permissions) visible.remove("Stock")

        // Assistants can read private restaurant information and financial indicators.
        visible.remove("IA")
        visible.remove("Assistente económico")
        visible.remove("Assistente")
        visible.remove("Preço Justo")
        visible.remove("Análise")
        return visible
    }
}