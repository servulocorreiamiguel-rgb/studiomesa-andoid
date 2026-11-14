package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomerMembershipSelectionPolicyTest {
    @Test
    fun preservesSelectedRestaurantWhenMembershipsRefresh() {
        assertEquals(
            "member-b",
            CustomerMembershipSelectionPolicy.selectMemberId(
                listOf("member-a", "member-b"),
                "member-b"
            )
        )
    }

    @Test
    fun selectsFirstAvailableCardWhenPreviousSelectionNoLongerExists() {
        assertEquals(
            "member-c",
            CustomerMembershipSelectionPolicy.selectMemberId(
                listOf("member-c", "member-d"),
                "member-b"
            )
        )
    }

    @Test
    fun ignoresBlankAndDuplicateIdentifiers() {
        assertEquals(
            "member-a",
            CustomerMembershipSelectionPolicy.selectMemberId(
                listOf("", "member-a", "member-a", "member-b"),
                ""
            )
        )
    }

    @Test
    fun returnsNullWhenAccountHasNoLoyaltyCards() {
        assertNull(CustomerMembershipSelectionPolicy.selectMemberId(emptyList(), null))
        assertNull(CustomerMembershipSelectionPolicy.selectMemberId(listOf("", "  "), null))
    }
}
