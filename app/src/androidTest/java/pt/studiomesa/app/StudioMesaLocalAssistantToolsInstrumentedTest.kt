package pt.studiomesa.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.math.BigDecimal
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioMesaLocalAssistantToolsInstrumentedTest {

    @Test
    fun publicWebSearchRejectsPrivateDataBeforeCallingTheProvider() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tool = StudioMesaWebSearchTool(context)
        val privateQueries = listOf(
            "Procura informação sobre miguel@example.com",
            "Procura informação sobre o NIF 501234560",
            "Procura informação sobre o telefone 912345678",
            "Procura informação sobre o IBAN PT50000000000000000000000"
        )

        privateQueries.forEach { query ->
            val result = JSONObject(tool.execute(JSONObject().put("query", query).toString()))
            assertTrue("A pesquisa não devia aceitar dados privados: $query", result.has("error"))
        }

        val definition = JSONObject(tool.getToolDescriptionJsonString())
        assertTrue(definition.getString("description").contains("Nunca envies dados privados"))
    }

    @Test
    fun wikipediaSearchRejectsPrivateDataBeforeNetworkAccess() {
        val tool = StudioMesaWikipediaSearchTool()
        val privateQueries = listOf(
            "Procura miguel@example.com",
            "Procura o NIF 501234560",
            "Procura o telefone 912345678",
            "Procura o IBAN PT50000000000000000000000"
        )

        privateQueries.forEach { query ->
            val result = JSONObject(tool.execute(JSONObject().put("query", query).toString()))
            assertTrue("A Wikipédia não devia receber dados privados: $query", result.has("error"))
        }

        val definition = JSONObject(tool.getToolDescriptionJsonString())
        assertTrue(definition.getString("description").contains("nunca dados internos"))
    }

    @Test
    fun economicCalculationsToolAnalyzesMonthlyMetricsDeterministically() {
        val tool = StudioMesaEconomicCalculationsTool()
        val result = JSONObject(
            tool.execute(
                JSONObject()
                    .put("operation", "monthly_analysis")
                    .put("monthly_net_sales", 10000)
                    .put("food_cost_consumed_including_waste", 3500)
                    .put("monthly_payroll", 3000)
                    .put("other_fixed_costs", 1000)
                    .put("other_variable_costs", 500)
                    .put("target_food_cost_percent", 30)
                    .put("recorded_waste_cost", 200)
                    .put("cash_available_now", 500)
                    .put("expected_receipts_next_30_days", 1000)
                    .put("payments_due_next_30_days", 1200)
                    .put("weekly_net_sales_history", "1000;1100;1200;1300")
                    .toString()
            )
        )

        val data = result.getJSONObject("result")
        assertTrue(result.getBoolean("read_only"))
        assertTrue(result.getString("data_source").contains("origem dos valores não é verificada"))
        assertTrue(result.getBoolean("confirmation_required"))
        assertEquals("35.0", data.getString("food_cost_percent"))
        assertEquals("30.0", data.getString("payroll_percent"))
        assertEquals("65.0", data.getString("prime_cost_percent"))
        assertEquals("2000.00", data.getString("estimated_operating_result_eur"))
        assertEquals("6666.67", data.getString("estimated_break_even_sales_eur"))
        assertEquals("300.00", data.getString("projected_cash_in_30_days_eur"))
        assertEquals("1150.00", data.getString("forecast_next_week_sales_eur"))
        assertTrue(data.getJSONArray("warnings").length() >= 3)
    }

    @Test
    fun economicCalculationsToolSupportsRecipeWasteQuotesBudgetUsageLabourAndReorder() {
        val tool = StudioMesaEconomicCalculationsTool()
        fun execute(operation: String, rawLines: String, target: BigDecimal? = null): JSONObject {
            val params = JSONObject().put("operation", operation).put("raw_lines", rawLines)
            if (target != null) params.put("target_food_cost_percent", target)
            return JSONObject(tool.execute(params.toString()))
        }

        val recipe = execute(
            "recipe_cost",
            "carne | 0.2 | 10 | 5\nbatata | 0.15 | 2 | 0",
            BigDecimal("30")
        ).getJSONObject("result")
        assertEquals("2.41", recipe.getString("total_cost_per_portion_eur"))
        assertEquals("8.02", recipe.getString("suggested_price_at_target_eur"))

        val waste = execute("waste_log", "arroz | 2 | 3 | estragado\nfrango | 1 | 5 | validade")
            .getJSONObject("result")
        assertEquals("11.00", waste.getString("total_recorded_waste_eur"))

        val suppliers = execute(
            "supplier_quotes",
            "Arroz | Fornecedor A | kg | 5 | 10\nArroz | Fornecedor B | kg | 10 | 18"
        ).getJSONObject("result").getJSONArray("comparisons").getJSONObject(0)
        assertEquals("Fornecedor B", suppliers.getString("lowest_unit_price_supplier"))

        val budget = execute("budget_variance", "Alimentação | 1000 | 1200")
            .getJSONObject("result").getJSONArray("categories").getJSONObject(0)
        assertEquals("200.00", budget.getString("variance_eur"))

        val usage = execute("usage_variance", "Arroz | 10 | 12 | 2")
            .getJSONObject("result").getJSONArray("items").getJSONObject(0)
        assertEquals("4.00", usage.getString("variance_cost_eur"))

        val labour = execute("labour_plan", "160 | 12 | 10000 | 30")
            .getJSONObject("result")
        assertEquals("19.2", labour.getString("estimated_payroll_percent"))

        val reorder = execute("reorder", "5 | 2 | 3 | 2")
            .getJSONObject("result")
        assertEquals("5.000", reorder.getString("suggested_order_quantity"))
    }

    @Test
    fun economicCalculationsToolRejectsMissingOrInvalidInputsWithoutMutation() {
        val tool = StudioMesaEconomicCalculationsTool()
        val missing = JSONObject(tool.execute(JSONObject().put("operation", "monthly_analysis").toString()))
        assertTrue(missing.has("error"))
        assertTrue(missing.getBoolean("read_only"))

        val invalid = JSONObject(
            tool.execute(
                JSONObject()
                    .put("operation", "recipe_cost")
                    .put("raw_lines", "carne | 0.2 | 10 | 110")
                    .put("target_food_cost_percent", 30)
                    .toString()
            )
        )
        assertTrue(invalid.has("error"))
        assertTrue(invalid.getBoolean("read_only"))
    }

    @Test
    fun priceReferenceToolCalculatesWithoutChangingTheMenu() {
        val item = AssistantMenuItem(
            name = "Bife da casa",
            sellingPrice = BigDecimal("10.00"),
            unitCost = BigDecimal("2.50")
        )
        val snapshot = StudioMesaAssistantSnapshot(
            isDemo = true,
            restaurantConfigured = false,
            menuItems = listOf(item)
        )
        val tool = StudioMesaPriceReferenceTool(snapshot)
        val result = JSONObject(
            tool.execute(
                JSONObject()
                    .put("item_name", "Bife da casa")
                    .put("target_cost_percent", 25)
                    .toString()
            )
        )

        assertEquals("10.00", result.getString("calculated_reference_price_eur"))
        assertEquals("10.00", result.getString("current_selling_price_eur"))
        assertTrue(result.getBoolean("read_only"))
        assertEquals("10.00", snapshot.menuItems.single().sellingPrice.toPlainString())
    }

    @Test
    fun priceReferenceToolRejectsAmbiguousAndMissingCosts() {
        val ambiguous = StudioMesaAssistantSnapshot(
            isDemo = true,
            restaurantConfigured = false,
            menuItems = listOf(
                AssistantMenuItem("Bife grelhado", BigDecimal("12"), BigDecimal("3")),
                AssistantMenuItem("Bife da casa", BigDecimal("14"), BigDecimal("4"))
            )
        )
        val ambiguousResult = JSONObject(
            StudioMesaPriceReferenceTool(ambiguous).execute(
                JSONObject().put("item_name", "Bife").put("target_cost_percent", 30).toString()
            )
        )
        assertTrue(ambiguousResult.has("error"))

        val missingCost = StudioMesaAssistantSnapshot(
            isDemo = true,
            restaurantConfigured = false,
            menuItems = listOf(
                AssistantMenuItem("Sopa", BigDecimal("3.50"), BigDecimal.ZERO)
            )
        )
        val missingResult = JSONObject(
            StudioMesaPriceReferenceTool(missingCost).execute(
                JSONObject().put("item_name", "Sopa").put("target_cost_percent", 30).toString()
            )
        )
        assertTrue(missingResult.has("error"))
    }

    @Test
    fun stockToolNeverPretendsAnUnloadedSnapshotIsCurrent() {
        val snapshot = StudioMesaAssistantSnapshot(
            isDemo = false,
            restaurantConfigured = true,
            menuItems = emptyList(),
            stockFeatureAvailable = true,
            stockLoaded = false
        )
        val result = JSONObject(
            StudioMesaRestaurantDataTool(snapshot).execute(
                JSONObject().put("area", "stock").toString()
            )
        )

        assertFalse(result.getBoolean("loaded"))
        assertTrue(result.getString("message").contains("não está carregado"))
        assertTrue(result.getBoolean("read_only"))
    }
    @Test
    fun invoiceToolSummarisesOnlyImportedDocumentsAndNeverWrites() {
        val invoice = SupplierInvoiceRecord(
            id = "00000000-0000-0000-0000-000000000001",
            supplierName = "Mercearia Central",
            supplierVatNumber = "501234560",
            invoiceNumber = "FT/2026/001",
            issueDate = "2026-11-05",
            dueDate = "",
            netAmount = "100.00",
            vatAmount = "23.00",
            totalAmount = "123.00",
            currency = "EUR",
            mimeType = "application/pdf",
            byteSize = 1024,
            documentSha256 = "a".repeat(64),
            documentUri = null,
            documentPath = null,
            sourceType = "pdf_ocr",
            reviewStatus = "approved",
            warnings = emptyList(),
            createdAtMillis = 1_700_000_000_000L,
            updatedAtMillis = 1_700_000_000_000L
        )
        val snapshot = StudioMesaAssistantSnapshot(
            isDemo = false,
            restaurantConfigured = true,
            menuItems = emptyList(),
            supplierInvoices = listOf(invoice)
        )
        val tool = StudioMesaSupplierInvoiceTool(snapshot)
        val result = JSONObject(
            tool.execute(JSONObject().put("status", "approved").toString())
        )
        assertEquals(1, result.getInt("count"))
        assertEquals("123.00", result.getString("gross_total_eur"))
        assertEquals(1, result.getInt("approved"))
        assertTrue(result.getBoolean("read_only"))
        assertTrue(result.getString("warning").contains("Não prova pagamento"))
        val filtered = JSONObject(
            tool.execute(JSONObject().put("supplier", "outro").toString())
        )
        assertEquals(0, filtered.getInt("count"))
    }

    @Test
    fun localToolsUseTheLatestSnapshotAfterEachChatTurn() {
        val firstSnapshot = StudioMesaAssistantSnapshot(
            isDemo = true,
            restaurantConfigured = false,
            menuItems = listOf(
                AssistantMenuItem("Bife", BigDecimal("10.00"), BigDecimal("2.50"))
            )
        )
        val refreshedSnapshot = StudioMesaAssistantSnapshot(
            isDemo = true,
            restaurantConfigured = false,
            menuItems = listOf(
                AssistantMenuItem("Bife", BigDecimal("12.00"), BigDecimal("3.00"))
            )
        )

        val priceTool = StudioMesaPriceReferenceTool(firstSnapshot)
        val dataTool = StudioMesaRestaurantDataTool(firstSnapshot)
        priceTool.updateSnapshot(refreshedSnapshot)
        dataTool.updateSnapshot(refreshedSnapshot)

        val price = JSONObject(
            priceTool.execute(
                JSONObject()
                    .put("item_name", "Bife")
                    .put("target_cost_percent", 25)
                    .toString()
            )
        )
        val menu = JSONObject(
            dataTool.execute(
                JSONObject()
                    .put("area", "menu")
                    .toString()
            )
        ).getJSONArray("items").getJSONObject(0)

        assertEquals("12.00", price.getString("current_selling_price_eur"))
        assertEquals("3.00", price.getString("registered_unit_cost_eur"))
        assertEquals("12.00", menu.getString("selling_price_eur"))
    }


    @Test
    fun economicAssistantBridgeDelegatesInvoiceQueriesReadOnly() {
        val invoice = SupplierInvoiceRecord(
            id = "00000000-0000-0000-0000-000000000002",
            supplierName = "Distribuidor Norte",
            supplierVatNumber = "501234560",
            invoiceNumber = "FT-2026-020",
            issueDate = "2026-11-06",
            dueDate = "",
            netAmount = "80.00",
            vatAmount = "18.40",
            totalAmount = "98.40",
            currency = "EUR",
            mimeType = "application/pdf",
            byteSize = 2048,
            documentSha256 = "b".repeat(64),
            documentUri = null,
            documentPath = "private/path/never-exposed.pdf",
            sourceType = "pdf_ocr",
            reviewStatus = "pending_review",
            warnings = emptyList(),
            createdAtMillis = 1_700_000_000_000L,
            updatedAtMillis = 1_700_000_000_000L
        )
        val firstSnapshot = StudioMesaAssistantSnapshot(
            isDemo = false,
            restaurantConfigured = true,
            menuItems = emptyList()
        )
        val refreshedSnapshot = firstSnapshot.copy(supplierInvoices = listOf(invoice))
        val invoiceTool = StudioMesaSupplierInvoiceTool(firstSnapshot)
        val bridge = StudioMesaEconomicAssistantBridgeTool(invoiceTool)

        val definition = JSONObject(bridge.getToolDescriptionJsonString())
        assertEquals("consult_economic_assistant", definition.getString("name"))
        assertTrue(definition.getString("description").contains("só de leitura"))

        bridge.updateSnapshot(refreshedSnapshot)
        val result = JSONObject(
            bridge.execute(JSONObject().put("status", "pending_review").toString())
        )

        assertEquals("Assistente económico", result.getString("delegated_to"))
        assertEquals("read_only", result.getString("delegation_mode"))
        assertEquals(1, result.getInt("count"))
        assertEquals("98.40", result.getString("gross_total_eur"))
        assertTrue(result.getBoolean("read_only"))
        assertFalse(result.toString().contains("private/path"))
        assertFalse(result.toString().contains("texto OCR"))
    }

}
