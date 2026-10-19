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

data class LoyaltyCustomer(
    val name: String,
    val code: String,
    val points: Int,
    val backendId: String? = null
)
