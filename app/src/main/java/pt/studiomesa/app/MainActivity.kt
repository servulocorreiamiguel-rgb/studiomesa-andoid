package pt.studiomesa.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.text.NumberFormat
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var store: RestaurantStore
    private lateinit var appRoot: LinearLayout
    private var activePage = "Início"

    private val navy = Color.rgb(22, 37, 55)
    private val navySoft = Color.rgb(37, 54, 73)
    private val gold = Color.rgb(193, 145, 59)
    private val paper = Color.rgb(246, 247, 244)
    private val ink = Color.rgb(35, 46, 57)
    private val muted = Color.rgb(105, 116, 125)
    private val green = Color.rgb(37, 125, 87)
    private val red = Color.rgb(174, 67, 59)
    private val white = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = RestaurantStore(this)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        renderApp()
    }

    private fun renderApp() {
        appRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(paper)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(18))
            setBackgroundColor(navy)
        }
        header.addView(label("StudioMesa", 25f, true, white))
        header.addView(label("Gestão simples para restaurantes", 13f, false, Color.rgb(218, 224, 229)))
        appRoot.addView(header, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        val pageContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(18))
        }
        scroll.addView(pageContent)
        appRoot.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        renderPage(pageContent)

        val navScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(white)
        }
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(5), dp(7), dp(5), dp(7))
        }
        val pages = listOf("Início", "Mesas", "Pedidos", "Menu", "Preço Justo", "Planos")
        pages.forEach { page ->
            val selected = activePage == page
            val item = TextView(this).apply {
                text = page
                textSize = 12.5f
                gravity = Gravity.CENTER
                setTextColor(if (selected) white else navy)
                setPadding(dp(13), dp(12), dp(13), dp(12))
                background = rounded(if (selected) navy else white, 18f)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    activePage = page
                    renderApp()
                }
            }
            val params = LinearLayout.LayoutParams(-2, -2)
            params.setMargins(dp(3), 0, dp(3), 0)
            nav.addView(item, params)
        }
        navScroll.addView(nav)
        appRoot.addView(navScroll, LinearLayout.LayoutParams(-1, -2))
        setContentView(appRoot)
    }

    private fun renderPage(container: LinearLayout) {
        when (activePage) {
            "Início" -> renderHome(container)
            "Mesas" -> renderTables(container)
            "Pedidos" -> renderOrders(container)
            "Menu" -> renderMenu(container)
            "Preço Justo" -> renderPriceCalculator(container)
            "Planos" -> renderPlans(container)
            else -> renderHome(container)
        }
    }

    private fun renderHome(container: LinearLayout) {
        container.addView(label("Visão geral", 23f, true, navy))
        container.addView(label("Resumo rápido da operação do restaurante.", 14f, false, muted))
        addGap(container, 16)

        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stats.addView(statCard("Mesas ocupadas", store.occupiedTableCount().toString() + " / 12"), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), 0) })
        val activeOrders = store.getOrders().count { it.status != "Concluído" }
        stats.addView(statCard("Pedidos ativos", activeOrders.toString()), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), 0, 0, 0) })
        container.addView(stats)

        addGap(container, 20)
        container.addView(label("Acesso rápido", 18f, true, navy))
        addGap(container, 8)
        actionCard(container, "Mesas", "Marcar mesas como livres ou ocupadas", "Abrir mesas") {
            activePage = "Mesas"
            renderApp()
        }
        actionCard(container, "Pedidos", "Registar e acompanhar pedidos", "Abrir pedidos") {
            activePage = "Pedidos"
            renderApp()
        }
        actionCard(container, "Menu e custos", "Gerir artigos, preços de venda e custos", "Abrir menu") {
            activePage = "Menu"
            renderApp()
        }
        actionCard(container, "Preço Justo de Mercado", "Estimar um preço de venda com base no custo", "Calcular preço") {
            activePage = "Preço Justo"
            renderApp()
        }

        addGap(container, 8)
        container.addView(label("Nota", 16f, true, navy))
        container.addView(label("Os dados são guardados neste dispositivo. Esta versão ainda não sincroniza dados entre telemóveis nem envia pedidos para uma cozinha.", 13f, false, muted))
    }

    private fun renderTables(container: LinearLayout) {
        container.addView(label("Mesas", 23f, true, navy))
        container.addView(label("Toca numa mesa para alternar entre livre e ocupada.", 14f, false, muted))
        addGap(container, 14)

        for (table in 1..12) {
            val occupied = store.isTableOccupied(table)
            val card = makeCard(16)
            card.orientation = LinearLayout.HORIZONTAL
            card.gravity = Gravity.CENTER_VERTICAL
            val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            details.addView(label("Mesa " + table, 17f, true, navy))
            details.addView(label(if (occupied) "Ocupada" else "Livre", 13f, true, if (occupied) red else green))
            card.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(label(if (occupied) "TOCAR  ›" else "TOCAR  ›", 12f, true, gold))
            card.isClickable = true
            card.isFocusable = true
            card.setOnClickListener {
                store.setTableOccupied(table, !occupied)
                renderApp()
            }
            container.addView(card, cardParams())
        }
    }

    private fun renderOrders(container: LinearLayout) {
        container.addView(label("Pedidos", 23f, true, navy))
        container.addView(label("Regista pedidos e acompanha o seu estado.", 14f, false, muted))
        addGap(container, 12)
        container.addView(primaryButton("+ Novo pedido") { showCreateOrderDialog() }, LinearLayout.LayoutParams(-1, -2))
        addGap(container, 14)

        val orders = store.getOrders()
        if (orders.isEmpty()) {
            container.addView(emptyCard("Ainda não existem pedidos. Usa «Novo pedido» para registar o primeiro."))
            return
        }

        orders.forEach { order ->
            val card = makeCard(15)
            card.addView(label("Mesa " + order.table + "  ·  " + order.itemName, 16f, true, navy))
            addGap(card, 5)
            card.addView(label(money(order.amount) + "  ·  " + order.status, 14f, true, if (order.status == "Concluído") green else gold))
            addGap(card, 10)
            val next = if (order.status == "Concluído") "Reabrir pedido" else "Marcar como concluído"
            card.addView(secondaryButton(next) {
                val status = if (order.status == "Concluído") "Aberto" else "Concluído"
                store.updateOrder(order.id, status)
                renderApp()
            })
            container.addView(card, cardParams())
        }
    }

    private fun renderMenu(container: LinearLayout) {
        container.addView(label("Menu e custos", 23f, true, navy))
        container.addView(label("Os valores guardados alimentam a calculadora de preço.", 14f, false, muted))
        addGap(container, 12)
        container.addView(primaryButton("+ Adicionar artigo") { showAddMenuItemDialog() }, LinearLayout.LayoutParams(-1, -2))
        addGap(container, 14)

        val items = store.getMenu()
        if (items.isEmpty()) {
            container.addView(emptyCard("O menu está vazio. Adiciona o primeiro artigo."))
        }
        items.forEachIndexed { index, item ->
            val card = makeCard(15)
            card.addView(label(item.name, 16f, true, navy))
            addGap(card, 5)
            card.addView(label("Venda: " + money(item.price) + "   ·   Custo: " + money(item.cost), 13f, false, muted))
            val ratio = if (item.price > 0.0) item.cost / item.price * 100.0 else 0.0
            addGap(card, 4)
            card.addView(label("Custo do artigo: " + String.format(Locale("pt", "PT"), "%.1f%%", ratio) + " do preço de venda", 12f, false, muted))
            addGap(card, 10)
            card.addView(secondaryButton("Remover artigo") {
                AlertDialog.Builder(this)
                    .setTitle("Remover artigo?")
                    .setMessage("Queres remover «" + item.name + "» do menu?")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Remover") { _, _ ->
                        val newItems = store.getMenu()
                        if (index in newItems.indices) {
                            newItems.removeAt(index)
                            store.saveMenu(newItems)
                        }
                        renderApp()
                    }
                    .show()
            })
            container.addView(card, cardParams())
        }
    }

    private fun renderPriceCalculator(container: LinearLayout) {
        container.addView(label("Preço Justo de Mercado", 23f, true, navy))
        container.addView(label("Calculadora de referência baseada no custo do artigo e na percentagem de custo pretendida.", 14f, false, muted))
        addGap(container, 15)

        val form = makeCard(16)
        form.addView(label("Custo por unidade (€)", 14f, true, navy))
        val costField = inputField("Ex.: 3,20", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        form.addView(costField, fieldParams())
        addGap(form, 8)
        form.addView(label("Custo pretendido (% do preço de venda)", 14f, true, navy))
        val percentageField = inputField("Ex.: 30", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        percentageField.setText("30")
        form.addView(percentageField, fieldParams())
        addGap(form, 12)

        val result = label("Introduz os valores e toca em «Calcular».", 15f, false, ink)
        form.addView(primaryButton("Calcular preço sugerido") {
            val cost = costField.text.toString().trim().replace(',', '.').toDoubleOrNull()
            val percentage = percentageField.text.toString().trim().replace(',', '.').toDoubleOrNull()
            if (cost == null || cost < 0.0) {
                costField.error = "Introduz um custo válido"
            } else if (percentage == null || percentage <= 0.0 || percentage > 100.0) {
                percentageField.error = "Usa uma percentagem entre 0 e 100"
            } else {
                val suggested = cost / (percentage / 100.0)
                val gross = suggested - cost
                result.text = "Preço de venda sugerido: " + money(suggested) +
                    "\n\nCusto por unidade: " + money(cost) +
                    "\nMargem bruta estimada: " + money(gross) +
                    "\nPercentagem de margem bruta: " + String.format(Locale("pt", "PT"), "%.1f%%", (gross / suggested) * 100.0)
                result.setTextColor(navy)
            }
        }, LinearLayout.LayoutParams(-1, -2))
        addGap(form, 12)
        form.addView(result)
        container.addView(form, cardParams())

        addGap(container, 8)
        container.addView(label("Importante", 16f, true, navy))
        container.addView(label("O cálculo não inclui automaticamente IVA, salários, renda, energia, comissões, desperdício ou preços praticados pela concorrência. Não representa uma pesquisa de mercado em tempo real: é uma estimativa matemática para apoiar a decisão.", 13f, false, muted))
    }

    private fun renderPlans(container: LinearLayout) {
        container.addView(label("Planos StudioMesa", 23f, true, navy))
        container.addView(label("Tabela inicial de preços configurados para referência.", 14f, false, muted))
        addGap(container, 14)
        planCard(container, "Plano 890", "890 €")
        planCard(container, "Plano 1 490", "1 490 €")
        planCard(container, "Plano 1 690", "1 690 €")
        addGap(container, 6)
        container.addView(label("Antes de apresentar estes valores a clientes, confirma o que cada plano inclui, o período de prestação, suporte, atualizações e condições de pagamento.", 13f, false, muted))
    }

    private fun planCard(container: LinearLayout, title: String, price: String) {
        val card = makeCard(18)
        card.addView(label(title, 17f, true, navy))
        addGap(card, 4)
        card.addView(label(price, 25f, true, gold))
        addGap(card, 4)
        card.addView(label("Preço configurado — funcionalidades e condições a definir.", 13f, false, muted))
        container.addView(card, cardParams())
    }

    private fun showCreateOrderDialog() {
        val items = store.getMenu()
        if (items.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Menu vazio")
                .setMessage("Adiciona primeiro um artigo ao menu.")
                .setPositiveButton("Abrir menu") { _, _ ->
                    activePage = "Menu"
                    renderApp()
                }
                .setNegativeButton("Fechar", null)
                .show()
            return
        }

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(4))
        }
        form.addView(label("Mesa", 14f, true, navy))
        val tableSpinner = Spinner(this)
        val tables = (1..12).map { "Mesa " + it }
        tableSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, tables)
        form.addView(tableSpinner, fieldParams())
        addGap(form, 8)
        form.addView(label("Artigo", 14f, true, navy))
        val itemSpinner = Spinner(this)
        itemSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items.map { it.name })
        form.addView(itemSpinner, fieldParams())

        val dialog = AlertDialog.Builder(this)
            .setTitle("Novo pedido")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Criar pedido", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val table = tableSpinner.selectedItemPosition + 1
                val selected = items.getOrNull(itemSpinner.selectedItemPosition)
                if (selected == null) {
                    return@setOnClickListener
                }
                val order = OrderEntry(
                    System.currentTimeMillis(),
                    table,
                    selected.name,
                    selected.price,
                    "Aberto",
                    System.currentTimeMillis()
                )
                store.addOrder(order)
                dialog.dismiss()
                activePage = "Pedidos"
                renderApp()
            }
        }
        dialog.show()
    }

    private fun showAddMenuItemDialog() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(4))
        }
        val nameField = inputField("Nome do artigo", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        val priceField = inputField("Preço de venda (€)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val costField = inputField("Custo por unidade (€)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        form.addView(label("Nome", 14f, true, navy))
        form.addView(nameField, fieldParams())
        addGap(form, 6)
        form.addView(label("Preço de venda (€)", 14f, true, navy))
        form.addView(priceField, fieldParams())
        addGap(form, 6)
        form.addView(label("Custo por unidade (€)", 14f, true, navy))
        form.addView(costField, fieldParams())

        val dialog = AlertDialog.Builder(this)
            .setTitle("Adicionar artigo")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameField.text.toString().trim()
                val price = priceField.text.toString().trim().replace(',', '.').toDoubleOrNull()
                val cost = costField.text.toString().trim().replace(',', '.').toDoubleOrNull()
                when {
                    name.isBlank() -> nameField.error = "Escreve um nome"
                    price == null || price < 0.0 -> priceField.error = "Preço inválido"
                    cost == null || cost < 0.0 -> costField.error = "Custo inválido"
                    else -> {
                        val menu = store.getMenu()
                        menu.add(MenuEntry(name, price, cost))
                        store.saveMenu(menu)
                        dialog.dismiss()
                        renderApp()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun actionCard(parent: LinearLayout, title: String, subtitle: String, actionText: String, action: () -> Unit) {
        val card = makeCard(15)
        card.addView(label(title, 16f, true, navy))
        addGap(card, 4)
        card.addView(label(subtitle, 13f, false, muted))
        addGap(card, 10)
        card.addView(secondaryButton(actionText, action))
        parent.addView(card, cardParams())
    }

    private fun statCard(title: String, value: String): LinearLayout {
        val card = makeCard(14)
        card.addView(label(title, 12f, false, muted))
        addGap(card, 5)
        card.addView(label(value, 22f, true, navy))
        return card
    }

    private fun emptyCard(message: String): LinearLayout {
        val card = makeCard(16)
        card.addView(label(message, 14f, false, muted))
        return card
    }

    private fun makeCard(padding: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
            background = rounded(white, 16f)
            elevation = dp(1).toFloat()
        }

    private fun cardParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, 0, 0, dp(11))
        }

    private fun fieldParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, dp(48)).apply {
            setMargins(0, dp(5), 0, dp(8))
        }

    private fun inputField(hintText: String, type: Int): EditText =
        EditText(this).apply {
            hint = hintText
            inputType = type
            textSize = 15f
            setTextColor(ink)
            setHintTextColor(muted)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(white, 10f)
            isSingleLine = true
        }

    private fun primaryButton(title: String, action: () -> Unit): Button =
        Button(this).apply {
            text = title
            textSize = 14f
            isAllCaps = false
            setTextColor(white)
            background = rounded(navy, 12f)
            setPadding(dp(12), dp(5), dp(12), dp(5))
            setOnClickListener { action() }
        }

    private fun secondaryButton(title: String, action: () -> Unit): Button =
        Button(this).apply {
            text = title
            textSize = 13f
            isAllCaps = false
            setTextColor(navy)
            background = rounded(Color.rgb(235, 239, 240), 10f)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            setOnClickListener { action() }
        }

    private fun label(value: String, size: Float, bold: Boolean, color: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setLineSpacing(dp(2).toFloat(), 1.0f)
        }

    private fun rounded(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius.toInt()).toFloat()
        }

    private fun addGap(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun money(value: Double): String =
        NumberFormat.getCurrencyInstance(Locale("pt", "PT")).format(value)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
