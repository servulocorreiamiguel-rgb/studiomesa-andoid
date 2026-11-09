package pt.studiomesa.app

import com.google.ai.edge.litertlm.OpenApiTool
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Read-only tools exposed to the on-device LFM2.5 model.
 *
 * These tools are bound to an already authenticated/loaded Activity snapshot.
 * They deliberately have no write operations and never receive customer PII.
 */
internal class StudioMesaRestaurantDataTool(
    snapshot: StudioMesaAssistantSnapshot
) : OpenApiTool {

    @Volatile private var snapshot = snapshot

    fun updateSnapshot(snapshot: StudioMesaAssistantSnapshot) {
        this.snapshot = snapshot
    }

    override fun getToolDescriptionJsonString(): String {
        val areas = JSONArray()
            .put("summary")
            .put("menu")
            .put("orders")
            .put("stock")
            .put("reservations")
        val area = JSONObject()
            .put("type", "string")
            .put("enum", areas)
            .put("description", "Que área do restaurante consultar.")
        val query = JSONObject()
            .put("type", "string")
            .put("description", "Filtro opcional por nome de artigo. Usa string vazia para todos.")
        val properties = JSONObject()
            .put("area", area)
            .put("query", query)
        val parameters = JSONObject()
            .put("type", "object")
            .put("properties", properties)
            .put("required", JSONArray().put("area"))
        return JSONObject()
            .put("name", "get_restaurant_data")
            .put(
                "description",
                "Consulta apenas dados operacionais já carregados nesta sessão da StudioMesa. Áreas disponíveis: summary, menu, orders, stock e reservations. Nunca altera registos. Se stock ou reservas não estiverem carregados, informa isso em vez de inventar valores."
            )
            .put("parameters", parameters)
            .toString()
    }

    override fun execute(paramsJsonString: String): String {
        return runCatching {
            val params = JSONObject(paramsJsonString)
            val area = params.optString("area").trim().lowercase(Locale.ROOT)
            val query = params.optString("query").trim()
            when (area) {
                "summary" -> summary()
                "menu" -> menu(query)
                "orders" -> orders(query)
                "stock" -> stock(query)
                "reservations" -> reservations()
                else -> JSONObject()
                    .put("error", "Área inválida. Usa summary, menu, orders, stock ou reservations.")
                    .toString()
            }
        }.getOrElse {
            JSONObject().put("error", "Não foi possível consultar os dados operacionais carregados.").toString()
        }
    }

    private fun summary(): String = JSONObject()
        .put("mode", if (snapshot.isDemo) "demo" else "authenticated_restaurant")
        .put("restaurant_configured", snapshot.restaurantConfigured)
        .put("menu_items_loaded", snapshot.menuItems.size)
        .put("active_orders", snapshot.activeOrderCount)
        .put("orders_in_preparation", snapshot.preparationOrderCount)
        .put("stock_available", snapshot.stockFeatureAvailable)
        .put("stock_loaded", snapshot.stockLoaded)
        .put("reservations_available", snapshot.reservationsFeatureAvailable)
        .put("reservations_loaded", snapshot.reservationsLoaded)
        .put("pending_reservations", if (snapshot.reservationsLoaded) snapshot.pendingReservations else JSONObject.NULL)
        .put("read_only", true)
        .toString()

    private fun menu(query: String): String {
        val filtered = snapshot.menuItems.filter {
            query.isBlank() || it.name.contains(query, ignoreCase = true)
        }.take(MAX_TOOL_ROWS)
        val rows = JSONArray()
        filtered.forEach { item ->
            val price = item.sellingPrice
            val cost = item.unitCost
            val directDifference = price.subtract(cost)
            val costShare = if (price > BigDecimal.ZERO && cost > BigDecimal.ZERO) {
                cost.multiply(HUNDRED).divide(price, 2, RoundingMode.HALF_UP)
            } else null
            rows.put(
                JSONObject()
                    .put("name", item.name)
                    .put("category", item.category)
                    .put("selling_price_eur", price.toPlainString())
                    .put("registered_unit_cost_eur", if (cost > BigDecimal.ZERO) cost.toPlainString() else JSONObject.NULL)
                    .put("gross_difference_before_other_costs_eur", if (cost > BigDecimal.ZERO) directDifference.toPlainString() else JSONObject.NULL)
                    .put("direct_cost_share_percent", costShare?.toPlainString() ?: JSONObject.NULL)
            )
        }
        return JSONObject()
            .put("area", "menu")
            .put("items", rows)
            .put("matching_items", filtered.size)
            .put("truncated", snapshot.menuItems.count { query.isBlank() || it.name.contains(query, ignoreCase = true) } > MAX_TOOL_ROWS)
            .put("read_only", true)
            .toString()
    }

    private fun orders(query: String): String {
        val rows = JSONArray()
        snapshot.salesLines.asSequence()
            .filter { query.isBlank() || it.itemName.contains(query, ignoreCase = true) }
            .take(MAX_TOOL_ROWS)
            .forEach { row ->
                rows.put(
                    JSONObject()
                        .put("item_name", row.itemName)
                        .put("quantity", row.quantity)
                        .put("status", row.status)
                )
            }
        return JSONObject()
            .put("area", "orders")
            .put("active_orders", snapshot.activeOrderCount)
            .put("orders_in_preparation", snapshot.preparationOrderCount)
            .put("items", rows)
            .put("read_only", true)
            .toString()
    }

    private fun stock(query: String): String {
        if (!snapshot.stockFeatureAvailable) {
            return JSONObject()
                .put("area", "stock")
                .put("available", false)
                .put("message", "O stock não está incluído no plano ativo.")
                .put("read_only", true)
                .toString()
        }
        if (!snapshot.restaurantConfigured || !snapshot.stockLoaded) {
            return JSONObject()
                .put("area", "stock")
                .put("available", true)
                .put("loaded", false)
                .put("message", "O stock não está carregado ou não foi atualizado nesta sessão. Abre Stock e atualiza antes de tomar decisões.")
                .put("read_only", true)
                .toString()
        }
        val rows = JSONArray()
        val filtered = snapshot.stockItems.filter {
            query.isBlank() || it.name.contains(query, ignoreCase = true)
        }.take(MAX_TOOL_ROWS)
        filtered.forEach { item ->
            rows.put(
                JSONObject()
                    .put("name", item.name)
                    .put("quantity", item.quantity.toPlainString())
                    .put("unit", item.unit)
                    .put("minimum_quantity", item.minimumQuantity.toPlainString())
                    .put("unit_cost_eur", item.unitCostPerUnit?.toPlainString() ?: JSONObject.NULL)
                    .put("below_minimum", item.quantity <= item.minimumQuantity)
            )
        }
        return JSONObject()
            .put("area", "stock")
            .put("loaded", true)
            .put("items", rows)
            .put("truncated", filtered.size < snapshot.stockItems.count { query.isBlank() || it.name.contains(query, ignoreCase = true) })
            .put("read_only", true)
            .toString()
    }

    private fun reservations(): String {
        if (!snapshot.reservationsFeatureAvailable) {
            return JSONObject()
                .put("area", "reservations")
                .put("available", false)
                .put("message", "As reservas não estão incluídas no plano ativo.")
                .put("read_only", true)
                .toString()
        }
        if (!snapshot.restaurantConfigured || !snapshot.reservationsLoaded) {
            return JSONObject()
                .put("area", "reservations")
                .put("available", true)
                .put("loaded", false)
                .put("message", "As reservas não estão carregadas nesta sessão. Atualiza a secção Reservas.")
                .put("read_only", true)
                .toString()
        }
        return JSONObject()
            .put("area", "reservations")
            .put("loaded", true)
            .put("pending_reservations", snapshot.pendingReservations)
            .put("read_only", true)
            .toString()
    }

    private companion object {
        const val MAX_TOOL_ROWS = 25
        val HUNDRED = BigDecimal("100")
    }
}

/** Read-only arithmetic based on costs already registered in the current menu snapshot. */
internal class StudioMesaPriceReferenceTool(
    snapshot: StudioMesaAssistantSnapshot
) : OpenApiTool {

    @Volatile private var snapshot = snapshot

    fun updateSnapshot(snapshot: StudioMesaAssistantSnapshot) {
        this.snapshot = snapshot
    }

    override fun getToolDescriptionJsonString(): String {
        val itemName = JSONObject()
            .put("type", "string")
            .put("description", "Nome exato ou parcial do artigo da carta.")
        val targetPercent = JSONObject()
            .put("type", "number")
            .put("minimum", 1)
            .put("maximum", 100)
            .put("description", "Percentagem de custo direto pretendida, entre 1 e 100. Não é uma recomendação universal.")
        val properties = JSONObject()
            .put("item_name", itemName)
            .put("target_cost_percent", targetPercent)
        val parameters = JSONObject()
            .put("type", "object")
            .put("properties", properties)
            .put("required", JSONArray().put("item_name").put("target_cost_percent"))
        return JSONObject()
            .put("name", "calculate_menu_price_reference")
            .put(
                "description",
                "Calcula uma referência matemática de preço a partir do custo unitário já registado e de uma percentagem de custo direto definida pelo utilizador. É só cálculo de leitura: não altera a carta. Não inclui automaticamente IVA, mão de obra, renda, energia, desperdício, taxas, procura ou concorrência."
            )
            .put("parameters", parameters)
            .toString()
    }

    override fun execute(paramsJsonString: String): String {
        return runCatching {
            val params = JSONObject(paramsJsonString)
            val query = params.optString("item_name").trim()
            val target = params.optDouble("target_cost_percent", Double.NaN)
            if (query.isBlank() || !target.isFinite() || target !in 1.0..100.0) {
                return@runCatching JSONObject()
                    .put("error", "Indica um artigo e uma percentagem de custo direto válida, entre 1 e 100.")
                    .toString()
            }
            val matches = snapshot.menuItems.filter { it.name.contains(query, ignoreCase = true) }
            if (matches.isEmpty()) {
                return@runCatching JSONObject()
                    .put("error", "Não foi encontrado um artigo correspondente na carta carregada.")
                    .put("read_only", true)
                    .toString()
            }
            if (matches.size > 1) {
                return@runCatching JSONObject()
                    .put("error", "O nome é ambíguo. Especifica melhor o artigo.")
                    .put("matches", JSONArray(matches.take(10).map { it.name }))
                    .put("read_only", true)
                    .toString()
            }
            val item = matches.single()
            if (item.unitCost <= BigDecimal.ZERO) {
                return@runCatching JSONObject()
                    .put("item_name", item.name)
                    .put("error", "O artigo não tem um custo unitário válido registado.")
                    .put("read_only", true)
                    .toString()
            }
            val percent = BigDecimal.valueOf(target)
            val reference = item.unitCost.multiply(HUNDRED)
                .divide(percent, 2, RoundingMode.HALF_UP)
            val difference = reference.subtract(item.sellingPrice)
            JSONObject()
                .put("item_name", item.name)
                .put("registered_unit_cost_eur", item.unitCost.toPlainString())
                .put("current_selling_price_eur", item.sellingPrice.toPlainString())
                .put("target_direct_cost_percent", percent.toPlainString())
                .put("calculated_reference_price_eur", reference.toPlainString())
                .put("difference_vs_current_eur", difference.toPlainString())
                .put("formula", "custo unitário × 100 ÷ percentagem de custo direto")
                .put("assumptions", JSONArray()
                    .put("Usa apenas o custo unitário registado.")
                    .put("Não inclui automaticamente IVA, mão de obra, renda, energia, desperdício, taxas, procura ou concorrência.")
                    .put("É uma referência matemática, não uma decisão nem uma alteração de preço."))
                .put("read_only", true)
                .toString()
        }.getOrElse {
            JSONObject().put("error", "Não foi possível calcular a referência de preço.").toString()
        }
    }

    private companion object {
        val HUNDRED = BigDecimal("100")
    }
}


/** Read-only account of supplier invoices already reviewed or awaiting review. */
internal class StudioMesaSupplierInvoiceTool(
    snapshot: StudioMesaAssistantSnapshot
) : OpenApiTool {
    @Volatile private var snapshot = snapshot

    fun updateSnapshot(snapshot: StudioMesaAssistantSnapshot) {
        this.snapshot = snapshot
    }

    override fun getToolDescriptionJsonString(): String {
        val supplier = JSONObject()
            .put("type", "string")
            .put("description", "Filtro opcional pelo nome do fornecedor.")
        val status = JSONObject()
            .put("type", "string")
            .put("enum", JSONArray().put("all").put("pending_review").put("approved").put("rejected"))
            .put("description", "Estado da revisão. Por omissão usa all.")
        val dateFrom = JSONObject()
            .put("type", "string")
            .put("description", "Data inicial inclusiva da emissão, em AAAA-MM-DD. Omitir se não for pedida.")
            .put("pattern", "^\\d{4}-\\d{2}-\\d{2}$")
        val dateTo = JSONObject()
            .put("type", "string")
            .put("description", "Data final inclusiva da emissão, em AAAA-MM-DD. Omitir se não for pedida.")
            .put("pattern", "^\\d{4}-\\d{2}-\\d{2}$")
        val parameters = JSONObject()
            .put("type", "object")
            .put("properties", JSONObject()
                .put("supplier", supplier)
                .put("status", status)
                .put("issue_date_from", dateFrom)
                .put("issue_date_to", dateTo))
        return JSONObject()
            .put("name", "get_supplier_invoice_summary")
            .put(
                "description",
                "Consulta apenas metadados das faturas de fornecedores importadas para o arquivo do restaurante. " +
                    "Pode filtrar por fornecedor, estado de revisão e intervalo de datas de emissão (limites inclusivos, AAAA-MM-DD). " +
                    "É apenas leitura: não aprova, rejeita, altera, paga ou apaga faturas. Não revela texto OCR, linhas de artigos nem caminhos privados. " +
                    "Distingue sempre valores de faturas aprovadas, pendentes e rejeitadas. Nunca somes documentos rejeitados como despesas aprovadas; " +
                    "pendentes devem ser apresentados à parte. Uma fatura aprovada pelo proprietário não prova pagamento, reconciliação bancária ou dedutibilidade fiscal."
            )
            .put("parameters", parameters)
            .toString()
    }

    override fun execute(paramsJsonString: String): String = runCatching {
        val params = JSONObject(paramsJsonString)
        val supplierQuery = params.optString("supplier").trim()
        val statusFilter = params.optString("status", "all").ifBlank { "all" }
        if (statusFilter !in setOf("all", "pending_review", "approved", "rejected")) {
            return@runCatching JSONObject().put("error", "Estado de fatura inválido.").toString()
        }

        val fromDate = parseOptionalIssueDate(params.optString("issue_date_from"))
        val toDate = parseOptionalIssueDate(params.optString("issue_date_to"))
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            return@runCatching JSONObject()
                .put("error", "A data inicial não pode ser posterior à data final.")
                .put("read_only", true)
                .toString()
        }

        val rows = snapshot.supplierInvoices.filter { invoice ->
            val issueDate = runCatching { java.time.LocalDate.parse(invoice.issueDate) }.getOrNull()
            (supplierQuery.isBlank() || invoice.supplierName.contains(supplierQuery, ignoreCase = true)) &&
                (statusFilter == "all" || invoice.reviewStatus == statusFilter) &&
                (fromDate == null || (issueDate != null && !issueDate.isBefore(fromDate))) &&
                (toDate == null || (issueDate != null && !issueDate.isAfter(toDate)))
        }.sortedByDescending { it.issueDate }

        fun sumGross(selected: List<SupplierInvoiceRecord>) = selected.fold(BigDecimal.ZERO) { total, invoice ->
            total.add(invoice.totalAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO)
        }.setScale(2, RoundingMode.HALF_UP)

        fun sumNet(selected: List<SupplierInvoiceRecord>) = selected.fold(BigDecimal.ZERO) { total, invoice ->
            total.add(invoice.netAmount?.toBigDecimalOrNull() ?: BigDecimal.ZERO)
        }.setScale(2, RoundingMode.HALF_UP)

        fun sumVat(selected: List<SupplierInvoiceRecord>) = selected.fold(BigDecimal.ZERO) { total, invoice ->
            total.add(invoice.vatAmount?.toBigDecimalOrNull() ?: BigDecimal.ZERO)
        }.setScale(2, RoundingMode.HALF_UP)

        val approvedRows = rows.filter { it.reviewStatus == "approved" }
        val pendingRows = rows.filter { it.reviewStatus == "pending_review" }
        val rejectedRows = rows.filter { it.reviewStatus == "rejected" }
        val items = JSONArray()
        rows.take(20).forEach { invoice ->
            items.put(
                JSONObject()
                    .put("supplier", invoice.supplierName)
                    .put("invoice_number", invoice.invoiceNumber)
                    .put("issue_date", invoice.issueDate)
                    .put("due_date", if (invoice.dueDate.isBlank()) JSONObject.NULL else invoice.dueDate)
                    .put("net_amount_eur", invoice.netAmount ?: JSONObject.NULL)
                    .put("vat_amount_eur", invoice.vatAmount ?: JSONObject.NULL)
                    .put("total_amount_eur", invoice.totalAmount)
                    .put("status", invoice.reviewStatus)
                    .put("document_verified_by_owner", invoice.reviewStatus == "approved")
            )
        }

        JSONObject()
            .put("count", rows.size)
            .put("pending_review", pendingRows.size)
            .put("approved", approvedRows.size)
            .put("rejected", rejectedRows.size)
            .put("gross_total_eur", sumGross(rows).toPlainString())
            .put("net_total_eur", sumNet(rows).toPlainString())
            .put("vat_total_eur", sumVat(rows).toPlainString())
            .put("approved_gross_total_eur", sumGross(approvedRows).toPlainString())
            .put("pending_review_gross_total_eur", sumGross(pendingRows).toPlainString())
            .put("rejected_gross_total_eur", sumGross(rejectedRows).toPlainString())
            .put("supplier_filter", supplierQuery)
            .put("status_filter", statusFilter)
            .put("issue_date_from", params.optString("issue_date_from").ifBlank { JSONObject.NULL })
            .put("issue_date_to", params.optString("issue_date_to").ifBlank { JSONObject.NULL })
            .put("documents", items)
            .put("truncated", rows.size > 20)
            .put("read_only", true)
            .put(
                "warning",
                "gross_total_eur soma todos os estados selecionados. Para análise financeira, approved_gross_total_eur, " +
                    "pending_review_gross_total_eur e rejected_gross_total_eur estão separados. Uma fatura aprovada não prova pagamento, " +
                    "dedutibilidade fiscal ou reconciliação bancária."
            )
            .toString()
    }.getOrElse {
        JSONObject().put("error", "Não foi possível consultar as faturas importadas.").put("read_only", true).toString()
    }

    private fun parseOptionalIssueDate(raw: String): java.time.LocalDate? {
        val value = raw.trim()
        if (value.isBlank()) return null
        require(value.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
            "Usa datas de emissão no formato AAAA-MM-DD."
        }
        return runCatching { java.time.LocalDate.parse(value) }
            .getOrElse { throw IllegalArgumentException("A data de emissão não é válida.") }
    }
}

/**
 * A narrow bridge from the general assistant to the economic assistant.
 * It delegates only to the existing read-only invoice summary tool. No invoice
 * document bytes, OCR text, or mutation operations are exposed.
 */
internal class StudioMesaEconomicAssistantBridgeTool(
    private val invoiceTool: StudioMesaSupplierInvoiceTool
) : OpenApiTool {

    fun updateSnapshot(snapshot: StudioMesaAssistantSnapshot) {
        invoiceTool.updateSnapshot(snapshot)
    }

    override fun getToolDescriptionJsonString(): String {
        val definition = JSONObject(invoiceTool.getToolDescriptionJsonString())
        definition.put("name", "consult_economic_assistant")
        definition.put(
            "description",
            "Encaminha uma pergunta financeira ao Assistente económico através de uma ligação interna e só de leitura. Consulta apenas metadados das faturas já importadas e disponíveis nesta sessão, podendo filtrar por fornecedor ou estado. Não consulta PDFs, texto OCR, contas bancárias ou pagamentos e não pode aprovar, rejeitar, alterar, eliminar ou pagar documentos. Usa apenas quando o utilizador pede informação sobre faturas, despesas ou fornecedores."
        )
        return definition.toString()
    }

    override fun execute(paramsJsonString: String): String = runCatching {
        val result = JSONObject(invoiceTool.execute(paramsJsonString))
        result.put("delegated_to", "Assistente económico")
            .put("delegation_mode", "read_only")
            .put("read_only", true)
            .put(
                "delegation_warning",
                "A resposta veio do arquivo local de metadados de faturas. Não constitui prova de pagamento, reconciliação bancária ou lançamento contabilístico."
            )
            .toString()
    }.getOrElse {
        JSONObject()
            .put("error", "O Assistente económico não conseguiu consultar o arquivo local de faturas.")
            .put("delegated_to", "Assistente económico")
            .put("read_only", true)
            .toString()
    }
}
