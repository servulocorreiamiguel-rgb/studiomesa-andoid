package pt.studiomesa.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoyaltyRewardTest {
    private fun reward(
        active: Boolean = true,
        pointsCost: Int = 100,
        stockLimit: Int? = 10,
        redeemedCount: Int = 0
    ) = LoyaltyReward(
        id = "c9c0a19a-7da8-4c94-a12f-04a7c75c138d",
        name = "Sobremesa",
        description = "Uma sobremesa à escolha",
        pointsCost = pointsCost,
        isActive = active,
        stockLimit = stockLimit,
        redeemedCount = redeemedCount
    )

    @Test
    fun unlimitedStockRemainsAvailableAndHasNoRemainingCount() {
        val item = reward(stockLimit = null, redeemedCount = 500)

        assertNull(item.availableStock())
        assertTrue(item.canRedeem(100))
    }

    @Test
    fun exhaustedStockCannotBeRedeemed() {
        val item = reward(stockLimit = 3, redeemedCount = 3)

        assertEquals(0, item.availableStock())
        assertFalse(item.canRedeem(100))
    }

    @Test
    fun insufficientPointsCannotBeRedeemed() {
        val item = reward(pointsCost = 100)

        assertFalse(item.canRedeem(99))
        assertTrue(item.canRedeem(100))
    }

    @Test
    fun inactiveRewardCannotBeRedeemed() {
        assertFalse(reward(active = false).canRedeem(100))
    }

    @Test
    fun customerRpcPayloadDefaultsMissingActiveFieldToTrue() {
        val item = LoyaltyReward.fromJson(
            JSONObject(
                """{
                    "id":"c9c0a19a-7da8-4c94-a12f-04a7c75c138d",
                    "name":"Sobremesa",
                    "description":"Uma sobremesa à escolha",
                    "points_cost":100,
                    "stock_limit":5,
                    "redeemed_count":2
                }"""
            )
        )

        assertTrue(item.isActive)
        assertEquals(3, item.availableStock())
        assertTrue(item.canRedeem(100))
    }

    @Test
    fun remainingStockNeverBecomesNegative() {
        assertEquals(0, reward(stockLimit = 2, redeemedCount = 5).availableStock())
    }
}
