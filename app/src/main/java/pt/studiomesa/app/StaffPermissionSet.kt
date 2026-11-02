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
            all.filter { it in selected }.forEach(::put)
        }
    }
}
