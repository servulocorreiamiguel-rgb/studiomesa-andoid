package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SupplierInvoiceLineMergePolicyTest {
    private fun line(id: String, description: String) = SupplierInvoiceLineRecord(
        id = id,
        productCode = "",
        description = description,
        quantity = "2",
        unit = "kg",
        unitPrice = "3.00",
        netAmount = "6.00",
        vatRate = null
    )

    @Test
    fun preservesLocalLinesWhenLegacyCloudRowHasNoLineItems() {
        val local = listOf(line("local-line", "Farinha"))

        assertEquals(local, SupplierInvoiceLineMergePolicy.resolve(emptyList(), local))
    }

    @Test
    fun prefersReviewedCloudLinesWhenTheyExist() {
        val local = listOf(line("local-line", "Farinha antiga"))
        val remote = listOf(line("cloud-line", "Farinha atualizada"))

        assertEquals(remote, SupplierInvoiceLineMergePolicy.resolve(remote, local))
    }
}
