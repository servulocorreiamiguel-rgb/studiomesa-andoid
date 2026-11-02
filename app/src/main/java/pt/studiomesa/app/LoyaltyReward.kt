package pt.studiomesa.app

import org.json.JSONObject

/**
 * One reward from a restaurant's loyalty catalogue.
 *
 * The model supports both RPC shapes: the restaurant RPC includes is_active,
 * while the customer RPC only returns active rewards and omits that field.
 */
internal data class LoyaltyReward(
    val id: String,
    val name: String,
    val description: String,
    val pointsCost: Int,
    val isActive: Boolean,
    val stockLimit: Int?,
    val redeemedCount: Int
) {
    fun availableStock(): Int? =
        stockLimit?.let { (it - redeemedCount).coerceAtLeast(0) }

    fun canRedeem(currentPoints: Int): Boolean =
        isActive &&
            currentPoints >= pointsCost &&
            (stockLimit == null || redeemedCount < stockLimit)

    companion object {
        fun fromJson(json: JSONObject): LoyaltyReward {
            val id = json.optString("id").trim()
            val name = json.optString("name").trim()
            val description = json.optString("description", "")
            val pointsCost = json.optInt("points_cost", -1)
            val isActive = json.optBoolean("is_active", true)
            val stockLimit = if (json.isNull("stock_limit")) {
                null
            } else {
                json.optInt("stock_limit", -1)
            }
            val redeemedCount = json.optInt("redeemed_count", 0)

            require(id.isNotBlank()) { "A recompensa não tem identificador." }
            require(name.length in 1..100) { "O nome da recompensa não é válido." }
            require(description.length <= 500) { "A descrição da recompensa é demasiado longa." }
            require(pointsCost in 1..1_000_000) { "O custo em pontos da recompensa não é válido." }
            require(stockLimit == null || stockLimit >= 0) { "O limite de stock não é válido." }
            require(redeemedCount >= 0) { "O número de resgates não é válido." }

            return LoyaltyReward(
                id = id,
                name = name,
                description = description,
                pointsCost = pointsCost,
                isActive = isActive,
                stockLimit = stockLimit,
                redeemedCount = redeemedCount
            )
        }
    }
}
