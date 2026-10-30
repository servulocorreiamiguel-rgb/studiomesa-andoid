package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryWasteNotesTest {
    @Test
    fun buildsPersistentReasonAndDetailNote() {
        assertEquals(
            "Desperdício · Validade expirada · embalagem aberta",
            InventoryWasteNotes.build("Validade expirada", "  embalagem   aberta  ")
        )
    }

    @Test
    fun buildsNoteWithoutOptionalDetail() {
        assertEquals(
            "Desperdício · Outro",
            InventoryWasteNotes.build("Outro", "")
        )
    }

    @Test
    fun normalizesWhitespaceInDetails() {
        assertEquals(
            "Desperdício · Erro de preparação · prato devolvido",
            InventoryWasteNotes.build("Erro de preparação", "prato\n devolvido")
        )
    }

    @Test
    fun identifiesOnlyNotesWithWastePrefix() {
        assertTrue(InventoryWasteNotes.isWaste("Desperdício · Deterioração"))
        assertFalse(InventoryWasteNotes.isWaste("Consumo normal"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownReason() {
        InventoryWasteNotes.build("Razão inventada", "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedDetails() {
        InventoryWasteNotes.build("Outro", "x".repeat(351))
    }
}
