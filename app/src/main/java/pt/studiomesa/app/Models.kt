package pt.studiomesa.app

data class MenuEntry(
    val name: String,
    val price: Double,
    val cost: Double
)

data class OrderEntry(
    val id: Long,
    val table: Int,
    val itemName: String,
    val amount: Double,
    val status: String,
    val createdAt: Long
)
