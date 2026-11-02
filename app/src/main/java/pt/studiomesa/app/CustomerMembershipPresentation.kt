package pt.studiomesa.app

enum class CustomerMembershipPresentation {
    SIGNED_OUT,
    LOADING,
    ERROR,
    UNLINKED,
    LINKED
}

object CustomerMembershipPresentationResolver {
    fun resolve(
        isSignedIn: Boolean,
        isResolved: Boolean,
        isLoading: Boolean,
        hasError: Boolean,
        hasMembership: Boolean
    ): CustomerMembershipPresentation = when {
        !isSignedIn -> CustomerMembershipPresentation.SIGNED_OUT
        hasError && !isResolved -> CustomerMembershipPresentation.ERROR
        isLoading && !isResolved -> CustomerMembershipPresentation.LOADING
        !isResolved -> CustomerMembershipPresentation.LOADING
        hasMembership -> CustomerMembershipPresentation.LINKED
        else -> CustomerMembershipPresentation.UNLINKED
    }
}
