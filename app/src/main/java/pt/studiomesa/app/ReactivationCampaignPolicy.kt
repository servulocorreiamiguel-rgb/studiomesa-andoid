package pt.studiomesa.app

/**
 * Validation shared by the restaurant reactivation campaign editor and its unit tests.
 * Server-side validation remains authoritative.
 */
internal object ReactivationCampaignPolicy {
    const val MIN_INACTIVITY_DAYS = 30
    const val MAX_INACTIVITY_DAYS = 365
    const val MAX_TITLE_LENGTH = 80
    const val MAX_MESSAGE_LENGTH = 500
    const val MAX_PROMOTION_TITLE_LENGTH = 80
    const val MAX_PROMOTION_DETAILS_LENGTH = 250

    val promotionTypes = listOf(
        "none",
        "discount",
        "cashback",
        "extra_points",
        "voucher",
        "free_item",
        "custom"
    )

    fun validateDraft(
        title: String,
        message: String,
        promotionType: String,
        promotionTitle: String,
        promotionDetails: String
    ): String? {
        val cleanTitle = title.trim()
        val cleanMessage = message.trim()
        val cleanPromotionTitle = promotionTitle.trim()
        val cleanPromotionDetails = promotionDetails.trim()

        if (cleanTitle.isEmpty() || cleanTitle.length > MAX_TITLE_LENGTH) {
            return "O título deve ter entre 1 e $MAX_TITLE_LENGTH caracteres."
        }
        if (cleanMessage.isEmpty() || cleanMessage.length > MAX_MESSAGE_LENGTH) {
            return "A mensagem deve ter entre 1 e $MAX_MESSAGE_LENGTH caracteres."
        }
        if (promotionType !in promotionTypes) {
            return "O tipo de promoção selecionado não é válido."
        }
        if (promotionType != "none") {
            if (cleanPromotionTitle.isEmpty() || cleanPromotionTitle.length > MAX_PROMOTION_TITLE_LENGTH) {
                return "O título da promoção deve ter entre 1 e $MAX_PROMOTION_TITLE_LENGTH caracteres."
            }
            if (cleanPromotionDetails.isEmpty() || cleanPromotionDetails.length > MAX_PROMOTION_DETAILS_LENGTH) {
                return "Os detalhes da promoção devem ter entre 1 e $MAX_PROMOTION_DETAILS_LENGTH caracteres."
            }
        }
        return null
    }
}
