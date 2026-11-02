package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerMembershipPresentationResolverTest {
    @Test
    fun signedOutUsersKeepTheDemonstrationExperience() {
        assertEquals(
            CustomerMembershipPresentation.SIGNED_OUT,
            CustomerMembershipPresentationResolver.resolve(
                isSignedIn = false,
                isResolved = false,
                isLoading = false,
                hasError = false,
                hasMembership = false
            )
        )
    }

    @Test
    fun unresolvedOrFailedMembershipIsNeverShownAsAnUnlinkedAccount() {
        assertEquals(
            CustomerMembershipPresentation.LOADING,
            CustomerMembershipPresentationResolver.resolve(
                isSignedIn = true,
                isResolved = false,
                isLoading = true,
                hasError = false,
                hasMembership = false
            )
        )
        assertEquals(
            CustomerMembershipPresentation.ERROR,
            CustomerMembershipPresentationResolver.resolve(
                isSignedIn = true,
                isResolved = false,
                isLoading = false,
                hasError = true,
                hasMembership = false
            )
        )
    }

    @Test
    fun onlyAnAuthoritativeEmptyMembershipResultShowsTheJoinAction() {
        assertEquals(
            CustomerMembershipPresentation.UNLINKED,
            CustomerMembershipPresentationResolver.resolve(
                isSignedIn = true,
                isResolved = true,
                isLoading = false,
                hasError = false,
                hasMembership = false
            )
        )
    }

    @Test
    fun verifiedMembershipShowsTheCustomerCard() {
        assertEquals(
            CustomerMembershipPresentation.LINKED,
            CustomerMembershipPresentationResolver.resolve(
                isSignedIn = true,
                isResolved = true,
                isLoading = false,
                hasError = false,
                hasMembership = true
            )
        )
    }
}
