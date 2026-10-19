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

data class ReservationEntry(
    val id: Long,
    val customerName: String,
    val phone: String,
    val dateTime: Long,
    val partySize: Int,
    val notes: String,
    val status: String,
    val createdAt: Long
)

data class RestaurantProfile(
    val name: String = "Restaurante de demonstração",
    val tagline: String = "Boa comida. Boas experiências.",
    val phone: String = "",
    val address: String = "",
    val accent: String = "Dourado"
)
