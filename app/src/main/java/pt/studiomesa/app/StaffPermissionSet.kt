package pt.studiomesa.app

import org.json.JSONArray

internal object StaffPermissionSet {
    val all: List<String> = listOf(
        "orders", "tables", "reservations", "menu", "inventory", "loyalty"
    )

    fun fromJson(value: JSONArray): Set<String> {
        val parsed = linkedSetOf<String>()
        for (index in 0 until value.length()) {
            val permission = value.optString(index).trim()
            if (permission in all) parsed.add(permission)
        }
        return parsed
    }

    fun toJsonArray(selected: Set<String>): JSONArray {
        require(selected.all { it in all }) { "A lista contém permissões desconhecidas." }
        return JSONArray().apply {
            all.filter { it in selected }.forEach { put(it) }
        }
    }

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

        // These screens can expose internal costs or business indicators.
        visible.remove("Assistente")
        visible.remove("Preço Justo")
        visible.remove("Análise")
        return visible
    }
}
