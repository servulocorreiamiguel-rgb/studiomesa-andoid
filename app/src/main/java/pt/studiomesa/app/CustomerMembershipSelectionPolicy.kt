package pt.studiomesa.app

/**
 * Keeps the customer's selected restaurant stable when membership data refreshes.
 * Falls back to the first available card only when the previous selection disappeared.
 */
internal object CustomerMembershipSelectionPolicy {
    fun selectMemberId(memberIds: List<String>, preferredMemberId: String?): String? {
        val validIds = memberIds.filter { it.isNotBlank() }.distinct()
        if (validIds.isEmpty()) return null
        return preferredMemberId?.takeIf { it in validIds } ?: validIds.first()
    }
}
