package pt.studiomesa.app

import org.json.JSONArray

/**
 * Stable allow-list for the six operational areas configurable by the restaurant owner.
 * This only drives navigation and RPC payload validation; the Supabase policies/RPCs
 * remain the actual authorization boundary.
 */
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

        // Economic and management screens expose internal business information
        // and remain owner-only regardless of the operational permission matrix.
        visible.remove("Assistente")
        visible.remove("Preço Justo")
        visible.remove("Análise")
        return visible
    }
}
