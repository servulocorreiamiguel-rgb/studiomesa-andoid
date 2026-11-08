package pt.studiomesa.app

/**
 * Convenient role templates for the owner-managed permission matrix.
 * The backend still stores and enforces the underlying module permissions;
 * these labels are presets, not trusted authorization claims.
 */
internal object StaffRolePresets {
    const val CUSTOM = "Personalizado"

    private val presets: LinkedHashMap<String, Set<String>> = linkedMapOf(
        "Gerente" to StaffPermissionSet.all.toSet(),
        "Empregado de mesa" to setOf("orders", "tables", "reservations", "loyalty"),
        "Cozinha" to setOf("orders"),
        "Caixa" to setOf("orders", "loyalty")
    )

    val labels: List<String> = listOf(CUSTOM) + presets.keys.toList()

    fun permissionsFor(label: String): Set<String>? = presets[label]?.toSet()

    fun labelFor(permissions: Set<String>): String =
        presets.entries.firstOrNull { it.value == permissions }?.key ?: CUSTOM

    fun indexFor(permissions: Set<String>): Int =
        labels.indexOf(labelFor(permissions)).coerceAtLeast(0)
}
