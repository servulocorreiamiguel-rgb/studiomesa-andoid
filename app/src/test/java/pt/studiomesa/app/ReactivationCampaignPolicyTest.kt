package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReactivationCampaignPolicyTest {
    @Test
    fun acceptsMessageWithoutPromotion() {
        assertNull(
            ReactivationCampaignPolicy.validateDraft(
                title = "Temos saudades tuas",
                message = "Gostávamos de voltar a receber-te.",
                promotionType = "none",
                promotionTitle = "",
                promotionDetails = ""
            )
        )
    }

    @Test
    fun rejectsBlankOrOversizedMessage() {
        assertEquals(
            "O título deve ter entre 1 e 80 caracteres.",
            ReactivationCampaignPolicy.validateDraft("", "Mensagem", "none", "", "")
        )
        assertEquals(
            "A mensagem deve ter entre 1 e 500 caracteres.",
            ReactivationCampaignPolicy.validateDraft("Título", "x".repeat(501), "none", "", "")
        )
    }

    @Test
    fun requiresPromotionTitleAndDetailsWhenPromotionIsSelected() {
        assertEquals(
            "O título da promoção deve ter entre 1 e 80 caracteres.",
            ReactivationCampaignPolicy.validateDraft("Título", "Mensagem", "discount", "", "Detalhes")
        )
        assertEquals(
            "Os detalhes da promoção devem ter entre 1 e 250 caracteres.",
            ReactivationCampaignPolicy.validateDraft("Título", "Mensagem", "voucher", "Vale", "")
        )
    }

    @Test
    fun validatesPromotionType() {
        assertEquals(
            "O tipo de promoção selecionado não é válido.",
            ReactivationCampaignPolicy.validateDraft("Título", "Mensagem", "unknown", "", "")
        )
    }
}
