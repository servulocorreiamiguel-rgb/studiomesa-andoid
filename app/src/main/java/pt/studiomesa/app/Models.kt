package pt.studiomesa.app

data class MenuEntry(
    val name: String,
    val price: Double,
    val cost: Double,
    val category: String = "Prato principal",
    val description: String = "",
    val allergens: String = ""
)

data class OrderEntry(
    val id: Long,
    val table: Int,
    val itemName: String,
    val amount: Double,
    val status: String,
    val createdAt: Long,
    val quantity: Int = 1,
    val serviceCourse: String = "Principal",
    val notes: String = ""
)

data class LoyaltyCustomer(
    val name: String,
    val code: String,
    val points: Int,
    val backendId: String? = null
)
