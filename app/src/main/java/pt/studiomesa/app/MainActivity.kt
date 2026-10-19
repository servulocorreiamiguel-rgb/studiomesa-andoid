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
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
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
    private val backendPrefs by lazy { getSharedPreferences("studiomesa_backend", MODE_PRIVATE) }

    private val flavor = BuildConfig.FLAVOR.lowercase(Locale.ROOT)
    private val isCustomerApp = flavor.contains("cliente")
    private val isPremium = flavor.contains("premium")
    private val hasCalculator = flavor.contains("profissional") || isPremium

    private val forest = Color.rgb(24, 57, 47)
    private val forestDeep = Color.rgb(15, 39, 32)
    private val forestSoft = Color.rgb(49, 91, 74)
    private val copper = Color.rgb(201, 145, 99)
    private val copperPale = Color.rgb(246, 231, 215)
    private val paper = Color.rgb(248, 246, 240)
    private val white = Color.WHITE
    private val ink = Color.rgb(39, 49, 43)
    private val muted = Color.rgb(110, 120, 112)
    private val line = Color.rgb(228, 226, 216)
    private val paleGreen = Color.rgb(229, 239, 231)
    private val paleRed = Color.rgb(249, 231, 226)
    private val red = Color.rgb(164, 68, 56)
    private val green = Color.rgb(48, 112, 78)

    private val planTitle: String
        get() = when {
            flavor.contains("essencial") -> "ESSENCIAL"
            flavor.contains("premium") -> "PREMIUM"
            else -> "PROFISSIONAL"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = RestaurantStore(this)
        window.statusBarColor = forestDeep
        window.navigationBarColor = forestDeep
        renderApp()
    }

    private fun availablePages(): List<String> {
        val pages = mutableListOf("Início", "Mesas", "Pedidos", "Carta", "Pontos", "Conta")
        if (hasCalculator) pages.add("Preço Justo")
        if (isPremium) pages.add("Análise")
        return pages
    }

    private fun renderApp() {
        if (isCustomerApp) {
            renderCustomerApp()
            return
        }
        if (activePage !in availablePages()) activePage = "Início"

        appRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(paper)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(17))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(forestDeep, forest, forestSoft)
            ).apply { cornerRadius = dp(22).toFloat() }
        }

        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val mark = TextView(this).apply {
            text = "BM"
            textSize = 15f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(forestDeep)
            background = rounded(copper, 14)
            elevation = dp(2).toFloat()
        }
        brandRow.addView(mark, LinearLayout.LayoutParams(dp(44), dp(44)))

        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, dp(5), 0)
        }
        brandText.addView(label("Bicho Mau", 23f, true, white))
        brandText.addView(label("STUDIOMESA  /  GESTÃO DE SALA", 9.5f, true, copper))
        brandRow.addView(brandText, LinearLayout.LayoutParams(0, -2, 1f))

        val planChip = label(planTitle, 9.5f, true, forestDeep).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(copper, 18)
        }
        brandRow.addView(planChip)
        header.addView(brandRow)

        addGap(header, 15)
        val separator = View(this).apply { setBackgroundColor(Color.argb(65, 255, 255, 255)) }
        header.addView(separator, LinearLayout.LayoutParams(-1, dp(1)))
        addGap(header, 12)
        header.addView(label("PROTÓTIPO DEMONSTRATIVO  ·  NÃO OFICIAL", 10f, true, copperPale))
        header.addView(label("Uma experiência de gestão pensada para o ritmo da sala.", 13f, false, Color.rgb(229, 235, 228)))

        val headerParams = LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(12), dp(10), dp(12), dp(5))
        }
        appRoot.addView(header, headerParams)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(22))
        }
        scroll.addView(content)
        appRoot.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        renderPage(content)

        val navScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(white)
        }
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), dp(7), dp(7), dp(7))
        }
        availablePages().forEach { page ->
            val selected = activePage == page
            val item = TextView(this).apply {
                text = when (page) {
                    "Início" -> "INÍCIO"
                    "Mesas" -> "MESAS"
                    "Pedidos" -> "PEDIDOS"
                    "Carta" -> "CARTA"
                    "Pontos" -> "PONTOS"
                    "Conta" -> "CONTA"
                    "Preço Justo" -> "PREÇO JUSTO"
                    "Análise" -> "ANÁLISE"
                    else -> page.uppercase(Locale.ROOT)
                }
                textSize = 10.5f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(if (selected) forestDeep else muted)
                setPadding(dp(13), dp(12), dp(13), dp(12))
                background = rounded(if (selected) copper else paper, 18)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    activePage = page
                    renderApp()
                }
            }
            nav.addView(item, LinearLayout.LayoutParams(-2, -2).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
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
            "Carta" -> renderMenu(container)
            "Pontos" -> renderCustomers(container)
            "Conta" -> renderBackendAccount(container)
            "Preço Justo" -> renderPriceCalculator(container)
            "Análise" -> renderAnalysis(container)
            else -> renderHome(container)
        }
    }

    private fun renderHome(container: LinearLayout) {
        container.addView(label("PAINEL DE OPERAÇÃO", 10f, true, forestSoft))
        addGap(container, 5)
        container.addView(label("A sala, num relance.", 27f, true, forestDeep))
        addGap(container, 5)
        container.addView(label("Uma demonstração visual personalizada para o Bistrô Bicho Mau. Os dados e a carta são exemplos, não informação oficial do restaurante.", 14f, false, muted))
        addGap(container, 18)

        val occupied = store.occupiedTableCount()
        val orders = store.getOrders()
        val activeOrders = orders.count { it.status != "Concluído" }
        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stats.addView(statCard("MESAS OCUPADAS", "$occupied / 12", if (occupied > 0) copper else forestSoft),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), 0) })
        stats.addView(statCard("PEDIDOS ATIVOS", activeOrders.toString(), if (activeOrders > 0) copper else forestSoft),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), 0, 0, 0) })
        container.addView(stats)

        addGap(container, 21)
        sectionHeading(container, "Acesso rápido", "Escolhe uma área para continuar.")
        actionCard(container, "01", "Mesas", "Atualiza o estado das 12 mesas.", "ABRIR MESAS") {
            activePage = "Mesas"; renderApp()
        }
        actionCard(container, "02", "Pedidos", "Regista pedidos e acompanha a conclusão.", "ABRIR PEDIDOS") {
            activePage = "Pedidos"; renderApp()
        }
        actionCard(container, "03", "Carta e custos", "Artigos de exemplo, preços e custos.", "ABRIR CARTA") {
            activePage = "Carta"; renderApp()
        }
        actionCard(container, "04", "Pontos de fidelização", "Cria cartões de cliente e ajusta saldos de pontos.", "GERIR CLIENTES") {
            activePage = "Pontos"; renderApp()
        }
        if (hasCalculator) {
            actionCard(container, "05", "Preço Justo", "Calcula um preço indicativo a partir do custo.", "ABRIR CALCULADORA") {
                activePage = "Preço Justo"; renderApp()
            }
        }
        if (isPremium) {
            actionCard(container, "06", "Análise local", "Resumo dos dados registados neste dispositivo.", "VER ANÁLISE") {
                activePage = "Análise"; renderApp()
            }
        }

        addGap(container, 6)
        val note = makeCard(14, copperPale)
        note.addView(label("DEMONSTRAÇÃO", 10f, true, forest))
        addGap(note, 4)
        note.addView(label("Não está ligado a um sistema de caixa, não sincroniza entre dispositivos e não envia pedidos para a cozinha. Substitui os exemplos pelos dados aprovados pelo restaurante antes de qualquer utilização real.", 12.5f, false, ink))
        container.addView(note, cardParams())
    }

    private fun renderTables(container: LinearLayout) {
        pageIntro(container, "Mesas", "Toca numa mesa para alternar entre livre e ocupada.")
        addGap(container, 8)

        val legend = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        legend.addView(statusDot(green), LinearLayout.LayoutParams(dp(8), dp(8)))
        legend.addView(label("  Livre", 12f, false, muted))
        addGap(legend, 1)
        legend.addView(statusDot(red), LinearLayout.LayoutParams(dp(8), dp(8)))
        legend.addView(label("  Ocupada", 12f, false, muted))
        container.addView(legend)
        addGap(container, 14)

        for (table in 1..12 step 2) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(tableCard(table), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), dp(10)) })
            row.addView(tableCard(table + 1), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), 0, 0, dp(10)) })
            container.addView(row)
        }
        addGap(container, 3)
        container.addView(label("O estado das mesas fica guardado localmente neste telemóvel.", 12f, false, muted))
    }

    private fun tableCard(table: Int): LinearLayout {
        val occupied = store.isTableOccupied(table)
        val card = makeCard(15, if (occupied) paleRed else white)
        card.addView(label("MESA  " + table.toString().padStart(2, '0'), 10f, true, muted))
        addGap(card, 11)
        card.addView(label(if (occupied) "Ocupada" else "Livre", 18f, true, if (occupied) red else forest))
        addGap(card, 8)
        val badge = label(if (occupied) "MARCAR LIVRE" else "MARCAR OCUPADA", 9f, true, if (occupied) red else green).apply {
            setPadding(dp(7), dp(6), dp(7), dp(6))
            gravity = Gravity.CENTER
            background = rounded(if (occupied) Color.rgb(255, 246, 243) else paleGreen, 12)
        }
        card.addView(badge)
        card.isClickable = true
        card.isFocusable = true
        card.setOnClickListener {
            store.setTableOccupied(table, !occupied)
            renderApp()
        }
        return card
    }

    private fun renderOrders(container: LinearLayout) {
        pageIntro(container, "Pedidos", "Registo simples de pedidos por mesa.")
        addGap(container, 10)
        container.addView(primaryButton("+  Novo pedido") { showCreateOrderDialog() }, LinearLayout.LayoutParams(-1, dp(50)))
        addGap(container, 15)

        val orders = store.getOrders()
        if (orders.isEmpty()) {
            container.addView(emptyCard("Ainda não há pedidos registados. Cria um pedido de demonstração para experimentar o fluxo."))
            return
        }

        orders.forEach { order ->
            val done = order.status == "Concluído"
            val card = makeCard(16)
            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(label("MESA " + order.table.toString().padStart(2, '0'), 10f, true, forestSoft), LinearLayout.LayoutParams(0, -2, 1f))
            top.addView(label(if (done) "CONCLUÍDO" else "EM ABERTO", 9f, true, if (done) green else copper).apply {
                setPadding(dp(8), dp(6), dp(8), dp(6))
                background = rounded(if (done) paleGreen else copperPale, 12)
            })
            card.addView(top)
            addGap(card, 10)
            card.addView(label(order.itemName, 17f, true, ink))
            addGap(card, 4)
            card.addView(label(money(order.amount), 14f, true, forest))
            addGap(card, 12)
            card.addView(secondaryButton(if (done) "Reabrir pedido" else "Marcar como concluído") {
                store.updateOrder(order.id, if (done) "Aberto" else "Concluído")
                renderApp()
            }, LinearLayout.LayoutParams(-1, dp(42)))
            container.addView(card, cardParams())
        }
    }

    private fun renderMenu(container: LinearLayout) {
        pageIntro(container, "Carta e custos", "Artigos de exemplo para testar a gestão.")
        addGap(container, 7)
        container.addView(makeCard(13, copperPale).apply {
            addView(label("CARTA FICTÍCIA", 10f, true, forest))
            addGap(this, 4)
            addView(label("Os nomes e valores abaixo são ilustrativos; não representam a carta real do Bistrô Bicho Mau.", 12.5f, false, ink))
        }, cardParams())
        container.addView(primaryButton("+  Adicionar artigo") { showAddMenuItemDialog() }, LinearLayout.LayoutParams(-1, dp(48)))
        addGap(container, 15)

        val items = store.getMenu()
        if (items.isEmpty()) {
            container.addView(emptyCard("A carta está vazia. Adiciona o primeiro artigo de demonstração."))
        }
        items.forEach { item ->
            val card = makeCard(15)
            card.addView(label(item.name, 16f, true, ink))
            addGap(card, 7)
            val prices = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            prices.addView(priceColumn("PREÇO DE VENDA", money(item.price)), LinearLayout.LayoutParams(0, -2, 1f))
            prices.addView(priceColumn("CUSTO ESTIMADO", money(item.cost)), LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(prices)
            val ratio = if (item.price > 0.0) item.cost / item.price * 100.0 else 0.0
            addGap(card, 9)
            val ratioLabel = label("Custo do artigo: " + String.format(Locale("pt", "PT"), "%.1f%%", ratio) + " do preço de venda", 11.5f, false, muted)
            card.addView(ratioLabel)
            addGap(card, 9)
            card.addView(secondaryButton("Remover artigo") {
                AlertDialog.Builder(this)
                    .setTitle("Remover artigo?")
                    .setMessage("Queres remover «" + item.name + "» da carta de demonstração?")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Remover") { _, _ ->
                        val newItems = store.getMenu()
                        val index = newItems.indexOfFirst { it.name == item.name && it.price == item.price && it.cost == item.cost }
                        if (index >= 0) newItems.removeAt(index)
                        store.saveMenu(newItems)
                        renderApp()
                    }
                    .show()
            }, LinearLayout.LayoutParams(-1, dp(40)))
            container.addView(card, cardParams())
        }
    }

    private fun renderPriceCalculator(container: LinearLayout) {
        pageIntro(container, "Preço Justo", "Uma estimativa matemática baseada no custo.")
        addGap(container, 5)
        container.addView(label("Não consulta preços concorrentes em tempo real e não substitui uma análise completa de custos.", 12.5f, false, muted))
        addGap(container, 15)

        val form = makeCard(17)
        form.addView(label("CUSTO POR UNIDADE (€)", 10f, true, forestSoft))
        val costField = inputField("Ex.: 3,20", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        form.addView(costField, fieldParams())
        addGap(form, 7)
        form.addView(label("CUSTO PRETENDIDO (% DO PREÇO)", 10f, true, forestSoft))
        val percentageField = inputField("Ex.: 30", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        percentageField.setText("30")
        form.addView(percentageField, fieldParams())
        addGap(form, 10)

        val result = label("Introduz os valores e calcula um preço indicativo.", 14f, false, ink).apply {
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(paper, 12)
        }
        form.addView(primaryButton("Calcular preço sugerido") {
            val cost = costField.text.toString().trim().replace(',', '.').toDoubleOrNull()
            val percentage = percentageField.text.toString().trim().replace(',', '.').toDoubleOrNull()
            when {
                cost == null || cost < 0.0 -> costField.error = "Introduz um custo válido"
                percentage == null || percentage <= 0.0 || percentage > 100.0 -> percentageField.error = "Usa uma percentagem entre 0 e 100"
                else -> {
                    val suggested = cost / (percentage / 100.0)
                    val gross = suggested - cost
                    result.text = "PREÇO INDICATIVO\n" + money(suggested) +
                        "\n\nCusto por unidade: " + money(cost) +
                        "\nMargem bruta estimada: " + money(gross) +
                        "\nMargem bruta: " + String.format(Locale("pt", "PT"), "%.1f%%", (gross / suggested) * 100.0)
                    result.setTextColor(forest)
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(48)))
        addGap(form, 10)
        form.addView(result)
        container.addView(form, cardParams())

        addGap(container, 5)
        val note = makeCard(14, copperPale)
        note.addView(label("Atenção aos custos indiretos", 14f, true, forest))
        addGap(note, 5)
        note.addView(label("IVA, salários, renda, energia, comissões, desperdício e outros encargos não são incluídos automaticamente. Confirma esses valores antes de definir preços reais.", 12.5f, false, ink))
        container.addView(note, cardParams())
    }

    private fun renderCustomers(container: LinearLayout) {
        pageIntro(container, "Fidelização por pontos", "Regista clientes e gere pontos manualmente.")
        addGap(container, 9)

        val notice = makeCard(14, copperPale)
        notice.addView(label("REGRAS A DEFINIR COM O RESTAURANTE", 10f, true, forest))
        addGap(notice, 5)
        notice.addView(label("Por agora, a equipa atribui e desconta pontos manualmente. A acumulação automática por compra, os prémios e a sincronização com a app do cliente ainda não estão ligados.", 12.5f, false, ink))
        container.addView(notice, cardParams())

        container.addView(primaryButton("+  Adicionar cliente") { showAddCustomerDialog() }, LinearLayout.LayoutParams(-1, dp(48)))
        addGap(container, 13)

        val customers = store.getCustomers()
        if (customers.isEmpty()) {
            container.addView(emptyCard("Ainda não há cartões de fidelização. Adiciona um cliente para começar a testar os pontos."))
            return
        }

        customers.forEach { customer ->
            val card = makeCard(15)
            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            details.addView(label(customer.name, 16f, true, forestDeep))
            addGap(details, 3)
            details.addView(label("Cartão " + customer.code, 11f, false, muted))
            top.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            top.addView(label(customer.points.toString(), 25f, true, forest))
            card.addView(top)
            addGap(card, 2)
            card.addView(label("PONTOS DISPONÍVEIS", 9f, true, forestSoft))
            addGap(card, 12)

            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(secondaryButton("+ Atribuir") {
                showCustomerPointsDialog(customer, false)
            }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(0, 0, dp(5), 0) })
            actions.addView(secondaryButton("− Descontar") {
                showCustomerPointsDialog(customer, true)
            }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(5), 0, 0, 0) })
            card.addView(actions)
            container.addView(card, cardParams())
        }
    }

    private fun showAddCustomerDialog() {
        val nameField = inputField("Nome do cliente", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(2))
        }
        form.addView(label("NOME PARA IDENTIFICAR O CARTÃO", 10f, true, forestSoft))
        form.addView(nameField, fieldParams())

        val dialog = AlertDialog.Builder(this)
            .setTitle("Novo cartão de fidelização")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Criar cartão", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameField.text.toString().trim()
                if (name.isBlank()) {
                    nameField.error = "Escreve o nome do cliente"
                } else {
                    val customer = store.addLoyaltyCustomer(name)
                    dialog.dismiss()
                    renderApp()
                    AlertDialog.Builder(this)
                        .setTitle("Cartão criado")
                        .setMessage("Código do cartão: " + customer.code + "\nSaldo inicial: 0 pontos.\nGuarda o código para referência.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
        dialog.show()
    }

    private fun showCustomerPointsDialog(customer: LoyaltyCustomer, redeem: Boolean) {
        val amountField = inputField("Número inteiro de pontos", InputType.TYPE_CLASS_NUMBER)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(2))
        }
        form.addView(label(if (redeem) "PONTOS A DESCONTAR" else "PONTOS A ATRIBUIR", 10f, true, forestSoft))
        form.addView(amountField, fieldParams())

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (redeem) "Descontar pontos" else "Atribuir pontos")
            .setMessage(customer.name + " · saldo atual: " + customer.points + " pontos")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Confirmar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val amount = amountField.text.toString().trim().toIntOrNull()
                when {
                    amount == null || amount <= 0 -> amountField.error = "Introduz um número superior a zero"
                    redeem && amount > customer.points -> amountField.error = "O saldo não chega para esse desconto"
                    else -> {
                        val delta = if (redeem) -amount else amount
                        if (store.adjustCustomerPoints(customer.code, delta)) {
                            dialog.dismiss()
                            renderApp()
                        } else {
                            amountField.error = "Não foi possível atualizar o saldo"
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun renderCustomerApp() {
        appRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(paper)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(forestDeep, forest, forestSoft)
            ).apply { cornerRadius = dp(22).toFloat() }
        }
        header.addView(label("Bicho Mau", 25f, true, white))
        addGap(header, 4)
        header.addView(label("CARTÃO DIGITAL DE CLIENTE  ·  " + planTitle, 10f, true, copper))
        addGap(header, 13)
        header.addView(label("A tua experiência, num só lugar.", 17f, true, white))
        header.addView(label("Protótipo demonstrativo, não oficial.", 12f, false, copperPale))
        appRoot.addView(header, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(12), dp(10), dp(12), dp(4))
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(24))
        }
        scroll.addView(content)
        appRoot.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val savedName = store.getCustomerProfileName()
        content.addView(label(if (savedName.isBlank()) "Olá!" else "Olá, " + savedName + "!", 25f, true, forestDeep))
        addGap(content, 5)
        content.addView(label("Este é o ecrã de cliente que poderá acompanhar o programa de fidelização do restaurante.", 13.5f, false, muted))
        addGap(content, 15)

        val loyalty = makeCard(18, forestDeep)
        loyalty.addView(label("O MEU CARTÃO", 10f, true, copper))
        addGap(loyalty, 10)
        loyalty.addView(label("0", 42f, true, white))
        loyalty.addView(label("PONTOS DE DEMONSTRAÇÃO", 10f, true, copperPale))
        addGap(loyalty, 13)
        val code = store.getCustomerMemberCode()
        loyalty.addView(label("Código de cliente", 11f, false, copperPale))
        addGap(loyalty, 3)
        loyalty.addView(label(code, 20f, true, white))
        addGap(loyalty, 9)
        loyalty.addView(label("Este saldo é apenas ilustrativo. Ainda não está ligado ao registo do restaurante e não permite reclamar prémios reais.", 12f, false, Color.rgb(229, 235, 228)))
        content.addView(loyalty, cardParams())

        content.addView(secondaryButton("Definir o meu nome") { showCustomerProfileDialog() }, LinearLayout.LayoutParams(-1, dp(44)))
        addGap(content, 15)

        val menuCard = makeCard(16)
        menuCard.addView(label("A CARTA", 10f, true, forestSoft))
        addGap(menuCard, 7)
        menuCard.addView(label("Exemplos de artigos", 18f, true, forestDeep))
        addGap(menuCard, 4)
        menuCard.addView(label("Nomes e preços fictícios; não correspondem à carta oficial.", 12f, false, muted))
        addGap(menuCard, 10)
        store.getMenu().forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(label(item.name, 13f, false, ink), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(label(money(item.price), 13f, true, forest))
            menuCard.addView(row)
            addGap(menuCard, 8)
        }
        content.addView(menuCard, cardParams())

        val rewards = makeCard(16, copperPale)
        rewards.addView(label("PRÓXIMOS PRÉMIOS", 10f, true, forest))
        addGap(rewards, 6)
        rewards.addView(label("Os prémios e as regras de acumulação serão definidos com o restaurante antes do lançamento.", 13f, false, ink))
        content.addView(rewards, cardParams())

        val syncNote = makeCard(13)
        syncNote.addView(label("SINCRONIZAÇÃO AINDA NÃO LIGADA", 10f, true, red))
        addGap(syncNote, 5)
        syncNote.addView(label("Esta aplicação de cliente funciona em modo de demonstração. Para ser vendida como serviço real, precisamos de uma base de dados partilhada, login seguro, regras de pontos e testes de privacidade.", 12f, false, muted))
        content.addView(syncNote, cardParams())

        setContentView(appRoot)
    }

    private fun showCustomerProfileDialog() {
        val field = inputField("O teu nome", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        field.setText(store.getCustomerProfileName())
        val dialog = AlertDialog.Builder(this)
            .setTitle("Perfil de demonstração")
            .setMessage("O nome fica guardado apenas nesta instalação.")
            .setView(field)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = field.text.toString().trim()
                if (name.isBlank()) {
                    field.error = "Escreve o teu nome"
                } else {
                    store.saveCustomerProfileName(name)
                    dialog.dismiss()
                    renderCustomerApp()
                }
            }
        }
        dialog.show()
    }

    private fun renderAnalysis(container: LinearLayout) {
        pageIntro(container, "Análise local", "Resumo dos dados que foram registados nesta instalação.")
        addGap(container, 6)
        val orders = store.getOrders()
        val activeOrders = orders.filter { it.status != "Concluído" }
        val completedOrders = orders.filter { it.status == "Concluído" }
        val menu = store.getMenu()
        val valueRecorded = orders.sumOf { it.amount }
        val avgCostRatio = menu.filter { it.price > 0.0 }.map { it.cost / it.price * 100.0 }.average()
        val summary = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        summary.addView(statCard("PEDIDOS REGISTADOS", orders.size.toString(), forestSoft),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), dp(10)) })
        summary.addView(statCard("EM ABERTO", activeOrders.size.toString(), copper),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), 0, 0, dp(10)) })
        container.addView(summary)

        val card = makeCard(17)
        addMetric(card, "Valor total registado em pedidos", money(valueRecorded))
        addDivider(card)
        addMetric(card, "Pedidos concluídos", completedOrders.size.toString())
        addDivider(card)
        addMetric(card, "Artigos na carta", menu.size.toString())
        addDivider(card)
        addMetric(card, "Custo médio dos artigos", if (avgCostRatio.isNaN()) "Sem dados" else String.format(Locale("pt", "PT"), "%.1f%%", avgCostRatio))
        container.addView(card, cardParams())

        addGap(container, 4)
        val note = makeCard(14, copperPale)
        note.addView(label("Indicadores de demonstração", 13f, true, forest))
        addGap(note, 5)
        note.addView(label("Os valores representam os registos guardados neste dispositivo. Não são vendas verificadas, não incluem pagamentos ou IVA e não constituem contabilidade nem relatório financeiro oficial.", 12.5f, false, ink))
        container.addView(note, cardParams())
    }

    private fun showCreateOrderDialog() {
        val items = store.getMenu()
        if (items.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Carta vazia")
                .setMessage("Adiciona primeiro um artigo à carta de demonstração.")
                .setPositiveButton("Abrir carta") { _, _ ->
                    activePage = "Carta"; renderApp()
                }
                .setNegativeButton("Fechar", null)
                .show()
            return
        }

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(4))
        }
        form.addView(label("MESA", 10f, true, forestSoft))
        val tableSpinner = Spinner(this)
        tableSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, (1..12).map { "Mesa " + it })
        form.addView(tableSpinner, fieldParams())
        addGap(form, 8)
        form.addView(label("ARTIGO DA CARTA", 10f, true, forestSoft))
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
                if (selected != null) {
                    val now = System.currentTimeMillis()
                    store.addOrder(OrderEntry(now, table, selected.name, selected.price, "Aberto", now))
                    dialog.dismiss()
                    activePage = "Pedidos"
                    renderApp()
                }
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
        form.addView(label("NOME", 10f, true, forestSoft))
        form.addView(nameField, fieldParams())
        addGap(form, 4)
        form.addView(label("PREÇO DE VENDA (€)", 10f, true, forestSoft))
        form.addView(priceField, fieldParams())
        addGap(form, 4)
        form.addView(label("CUSTO POR UNIDADE (€)", 10f, true, forestSoft))
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

    private fun pageIntro(container: LinearLayout, title: String, subtitle: String) {
        container.addView(label("GESTÃO DE RESTAURANTE", 10f, true, forestSoft))
        addGap(container, 5)
        container.addView(label(title, 27f, true, forestDeep))
        addGap(container, 5)
        container.addView(label(subtitle, 14f, false, muted))
    }

    private fun sectionHeading(container: LinearLayout, title: String, subtitle: String) {
        container.addView(label(title, 18f, true, forestDeep))
        addGap(container, 3)
        container.addView(label(subtitle, 12.5f, false, muted))
        addGap(container, 10)
    }

    private fun actionCard(parent: LinearLayout, number: String, title: String, subtitle: String, actionText: String, action: () -> Unit) {
        val card = makeCard(14)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(label(number, 11f, true, copper), LinearLayout.LayoutParams(dp(28), -2))
        val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        text.addView(label(title, 16f, true, ink))
        addGap(text, 3)
        text.addView(label(subtitle, 12.5f, false, muted))
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(row)
        addGap(card, 12)
        card.addView(secondaryButton(actionText, action), LinearLayout.LayoutParams(-1, dp(40)))
        parent.addView(card, cardParams())
    }

    private fun statCard(title: String, value: String, accent: Int): LinearLayout {
        val card = makeCard(14)
        card.addView(label(title, 9.5f, true, muted))
        addGap(card, 7)
        card.addView(label(value, 24f, true, forestDeep))
        addGap(card, 7)
        card.addView(View(this).apply { setBackgroundColor(accent) }, LinearLayout.LayoutParams(dp(26), dp(3)))
        return card
    }

    private fun priceColumn(title: String, value: String): LinearLayout {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(label(title, 9f, true, muted))
        addGap(column, 4)
        column.addView(label(value, 14f, true, forest))
        return column
    }

    private fun addMetric(parent: LinearLayout, title: String, value: String) {
        parent.addView(label(title.uppercase(Locale.ROOT), 9.5f, true, muted))
        addGap(parent, 4)
        parent.addView(label(value, 21f, true, forest))
        addGap(parent, 13)
    }

    private fun addDivider(parent: LinearLayout) {
        parent.addView(View(this).apply { setBackgroundColor(line) }, LinearLayout.LayoutParams(-1, dp(1)))
        addGap(parent, 12)
    }

    private fun statusDot(color: Int): View =
        View(this).apply { background = rounded(color, 9) }

    private fun emptyCard(message: String): LinearLayout {
        val card = makeCard(18, white)
        card.addView(label("Ainda sem registos", 15f, true, forest))
        addGap(card, 6)
        card.addView(label(message, 13f, false, muted))
        return card
    }

    private fun makeCard(padding: Int, color: Int = white): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
            background = rounded(color, 17)
            elevation = dp(2).toFloat()
        }

    private fun cardParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(11)) }

    private fun fieldParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0, dp(5), 0, dp(8)) }

    private fun inputField(hintText: String, type: Int): EditText =
        EditText(this).apply {
            hint = hintText
            inputType = type
            textSize = 15f
            setTextColor(ink)
            setHintTextColor(muted)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(white, 10)
            isSingleLine = true
        }

    private fun primaryButton(title: String, action: () -> Unit): Button =
        Button(this).apply {
            text = title
            textSize = 13f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            isAllCaps = false
            setTextColor(forestDeep)
            background = rounded(copper, 13)
            setPadding(dp(12), dp(3), dp(12), dp(3))
            setOnClickListener { action() }
        }

    private fun secondaryButton(title: String, action: () -> Unit): Button =
        Button(this).apply {
            text = title
            textSize = 11.5f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            isAllCaps = false
            setTextColor(forest)
            background = rounded(paleGreen, 12)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            setOnClickListener { action() }
        }

    private fun label(value: String, size: Float, bold: Boolean, color: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setLineSpacing(dp(2).toFloat(), 1.0f)
        }

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun addGap(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun money(value: Double): String =
        NumberFormat.getCurrencyInstance(Locale("pt", "PT")).format(value)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
