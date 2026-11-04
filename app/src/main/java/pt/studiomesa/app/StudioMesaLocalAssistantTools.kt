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
