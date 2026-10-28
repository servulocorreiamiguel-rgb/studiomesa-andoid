package pt.studiomesa.app

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.ColorStateList
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : Activity() {
    private lateinit var store: RestaurantStore
    private lateinit var appRoot: LinearLayout
    private var activePage = "Início"
    private var activeCustomerPage = "Cartão"
    private var assistantQuestion = ""
    private var assistantAnswer: StudioMesaAssistantAnswer? = null

    // Optional economic tools are opt-in and session-only until secure, owner-scoped
    // persistence and multi-device authorization are designed and tested.
    private var economicManagerEnabled = false
    private var economicScopeKey: String? = null
    private val economicInputs = mutableMapOf<String, String>()
    private var economicReportText = ""
    private var recipeReportText = ""
    private var reorderReportText = ""

    private val backendPrefs by lazy { getSharedPreferences("studiomesa_backend", MODE_PRIVATE) }
    private val secureSession by lazy { SecureSessionStore(this) }
    private val pendingRequestIds by lazy { PendingRequestIds(this) }
    // Reservation contacts are kept in memory only, not persisted to SharedPreferences.
    private var reservationCache = JSONArray()
    private var reservationsLoaded = false

    private val flavor = BuildConfig.FLAVOR.lowercase(Locale.ROOT)
    private val isCustomerApp = flavor.contains("cliente")
    private val isPremium = flavor.contains("premium")
    private val hasCalculator = flavor.contains("profissional") || isPremium

    private val forest = Color.rgb(57, 78, 67)
    private val forestDeep = Color.rgb(43, 59, 50)
    private val forestSoft = Color.rgb(77, 101, 87)
    private val copper = Color.rgb(211, 177, 139)
    private val copperPale = Color.rgb(245, 237, 224)
    private val paper = Color.rgb(248, 248, 244)
    private val white = Color.WHITE
    private val ink = Color.rgb(47, 57, 51)
    private val muted = Color.rgb(106, 116, 108)
    private val line = Color.rgb(230, 229, 220)
    private val paleGreen = Color.rgb(235, 242, 236)
    private val paleRed = Color.rgb(249, 237, 233)
    private val red = Color.rgb(157, 65, 56)
    private val green = Color.rgb(55, 105, 72)

    private val planTitle: String
        get() = when {
            flavor.contains("essencial") -> "ESSENCIAL"
            flavor.contains("premium") -> "PREMIUM"
            else -> "PROFISSIONAL"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Remove reservation contact data left by older demo builds which cached it on disk.
        backendPrefs.edit()
            .remove("remote_reservations_json")
            .remove("reservations_loaded")
            .apply()
        store = RestaurantStore(this)
        // Remove pre-release local demo records from accounts that were already signed in
        // when this privacy isolation was introduced.
        if (backendPrefs.getString("user_id", "").orEmpty().isNotBlank() &&
            !backendPrefs.getBoolean("local_demo_data_isolated_v1", false)) {
            store.clearLocalDemoData()
            backendPrefs.edit().putBoolean("local_demo_data_isolated_v1", true).apply()
        }
        window.statusBarColor = forestDeep
        window.navigationBarColor = forestDeep
        renderApp()
        handleAuthCallback(intent)

        // Refresh shared data when an already-authenticated user opens the app,
        // but do not start a second request while processing an OAuth callback.
        if (intent?.data == null && backendToken().isNotBlank()) {
            if (isCustomerApp && backendMemberId().isNotBlank()) {
                refreshCustomerMembership()
            } else if (!isCustomerApp && hasRemoteRestaurant()) {
                refreshRemoteOperations()
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallback(intent)
    }

    private fun parseAuthParams(raw: String?): Map<String, String> =
        AuthCallbackParser.parse(raw)

    private fun handleAuthCallback(callbackIntent: android.content.Intent?) {
        val uri = callbackIntent?.data ?: return
        if (uri.scheme != "studiomesa" || uri.host != "auth-callback") return
        // Parse the raw query/fragment defensively. getQueryParameter() and
        // URLDecoder can throw on malformed percent-encoding in an external deep link.
        val query = parseAuthParams(uri.encodedQuery)
        val fragment = parseAuthParams(uri.encodedFragment)
        val errorText = query["error_description"]
            ?: fragment["error_description"]
            ?: query["error"]
            ?: fragment["error"]

        if (!errorText.isNullOrBlank()) {
            AlertDialog.Builder(this)
                .setTitle("Autenticação não concluída")
                .setMessage(errorText.replace('+', ' ').take(500))
                .setPositiveButton("OK", null)
                .show()
            return
        }

        // Accept only the one-time PKCE authorization code. Do not accept bearer
        // access/refresh tokens supplied directly in a custom-scheme URL.
        val authCode = query["code"].orEmpty()
        val pendingFlow = secureSession.pendingCodeFlowType()
        val codeVerifier = secureSession.pendingCodeVerifier()
        if (authCode.isBlank() || codeVerifier.isBlank() ||
            pendingFlow !in setOf("oauth", "recovery", "signup")) {
            AlertDialog.Builder(this)
                .setTitle("Ligação de autenticação inválida")
                .setMessage("Não foi recebida uma ligação protegida válida. Pede uma nova ligação e abre-a neste telemóvel, com a aplicação instalada.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val isPasswordRecovery = pendingFlow == "recovery"

        runBackend({
            val session = SupabaseApi.exchangeCodeForSession(authCode, codeVerifier)
            val user = SupabaseApi.getUser(session.accessToken)
            val id = user.optString("id")
            if (id.isBlank()) throw IllegalStateException("O servidor não devolveu uma conta válida.")
            secureSession.save(session.accessToken, session.refreshToken)
            prepareForAuthenticatedUser(id)
            secureSession.clearPendingCodeVerifier()
            backendPrefs.edit()
                .putString("user_id", id)
                .putString("user_email", user.optString("email", session.email))
                .apply()
            if (isCustomerApp) restoreCustomerMembership(session.accessToken)
            else restoreOwnedRestaurant(session.accessToken)
            "SIGNED_IN"
        }, refreshSession = false) {
            if (isPasswordRecovery) {
                showSetNewPasswordDialog()
            } else {
                if (isCustomerApp) renderCustomerApp() else renderApp()
                AlertDialog.Builder(this)
                    .setTitle("Sessão iniciada")
                    .setMessage("A conta foi ligada ao StudioMesa. " +
                        if (isCustomerApp) "Agora associa-te ao restaurante com o respetivo código." else "Agora configura o restaurante para gerar um código de adesão.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun availablePages(): List<String> {
        val pages = mutableListOf("Início", "Mesas", "Pedidos", "Cozinha", "Carta", "Pontos", "Assistente", "Conta")
        if (hasCalculator) {
            pages.add("Reservas")
            pages.add("Preço Justo")
        }
        if (isPremium) {
            pages.add("Stock")
            pages.add("Análise")
        }
        return pages
    }

    private fun showNavigationDrawer() {
        val dialog = Dialog(this)
        val drawerRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(white)
        }

        val drawerHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(24), dp(14), dp(22))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(forestDeep, forest, forestSoft)
            ).apply { cornerRadius = dp(18).toFloat() }
        }
        val titleStack = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleStack.addView(label("StudioMesa", 24f, true, white))
        titleStack.addView(label(displayRestaurantName(), 14f, true, copperPale).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        titleStack.addView(label(planTitle + " · " + if (hasRemoteRestaurant()) "Restaurante configurado" else "Demonstração", 10.5f, false, copperPale))
        drawerHeader.addView(titleStack, LinearLayout.LayoutParams(0, -2, 1f))
        drawerHeader.addView(label("×", 27f, false, white).apply {
            gravity = Gravity.CENTER
            contentDescription = "Fechar menu"
            background = rippleBackground(Color.argb(40, 255, 255, 255), 12, Color.argb(70, 255, 255, 255))
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        drawerRoot.addView(drawerHeader, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(8), dp(8), dp(8), dp(6))
        })

        val listScroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(8))
        }
        val pageGroups = listOf(
            "OPERAÇÃO" to listOf("Início", "Mesas", "Pedidos", "Cozinha"),
            "GESTÃO DO RESTAURANTE" to listOf("Carta", "Pontos", "Reservas", "Preço Justo", "Stock", "Análise"),
            "ASSISTÊNCIA" to listOf("Assistente"),
            "CONTA" to listOf("Conta")
        )
        pageGroups.forEach { (groupTitle, groupPages) ->
            val visiblePages = groupPages.filter { it in availablePages() }
            if (visiblePages.isNotEmpty()) {
                list.addView(label(groupTitle, 10f, true, forestSoft).apply {
                    setPadding(dp(20), dp(13), dp(14), dp(7))
                    letterSpacing = 0.08f
                }, LinearLayout.LayoutParams(-1, -2))
                visiblePages.forEach { page ->
                    val selected = activePage == page
                    val item = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(9), dp(3), dp(12), dp(3))
                        background = rippleBackground(if (selected) paleGreen else white, 12)
                        contentDescription = "Abrir secção " + page
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            dialog.dismiss()
                            activePage = page
                            renderApp()
                        }
                    }
                    item.addView(TextView(this).apply {
                        text = pageGlyph(page)
                        textSize = 19f
                        gravity = Gravity.CENTER
                        setTextColor(if (selected) forestDeep else forestSoft)
                    }, LinearLayout.LayoutParams(dp(36), -1))
                    item.addView(label(page, 14.5f, selected, if (selected) forestDeep else ink),
                        LinearLayout.LayoutParams(0, -2, 1f))
                    list.addView(item, LinearLayout.LayoutParams(-1, dp(48)).apply {
                        setMargins(dp(10), dp(2), dp(10), dp(2))
                    })
                }
            }
        }
        listScroll.addView(list)
        drawerRoot.addView(listScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        drawerRoot.addView(View(this).apply { setBackgroundColor(line) },
            LinearLayout.LayoutParams(-1, dp(1)))
        drawerRoot.addView(label("StudioMesa · Gestão de sala", 10.5f, false, muted).apply {
            setPadding(dp(20), dp(12), dp(20), dp(16))
        }, LinearLayout.LayoutParams(-1, -2))

        dialog.setContentView(drawerRoot)
        dialog.show()
        val drawerWidth = minOf(resources.displayMetrics.widthPixels - dp(48), dp(340))
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.42f)
            setGravity(Gravity.START)
            setLayout(drawerWidth, -1)
        }
    }

    private fun pageGlyph(page: String): String = when (page) {
        "Cartão" -> "▣"
        "Início" -> "⌂"
        "Mesas" -> "▦"
        "Pedidos" -> "≡"
        "Cozinha" -> "◉"
        "Carta" -> "▤"
        "Pontos" -> "◎"
        "Conta" -> "⚙"
        "Reservas" -> "◷"
        "Preço Justo" -> "€"
        "Stock" -> "▣"
        "Análise" -> "↗"
        "Assistente" -> "✦"
        else -> "•"
    }
    /**
     * Android 15+ enforces edge-to-edge for apps targeting API 35+, and Android 16
     * removes the opt-out for apps targeting API 36. Keep interactive content out
     * from behind status/navigation bars and display cutouts on every supported version.
     *
     * Padding is always based on the root's original values so inset updates never
     * accumulate padding as the system UI changes (for example, on rotation).
     */
    private fun applySystemBarInsets(root: View) {
        // Traditional decor fitting already keeps content below the system bars on
        // Android 14 and earlier, so manual padding is only needed from API 35 onward.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return

        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom

        root.setOnApplyWindowInsetsListener { view, insets ->
            val safeInsets = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            view.setPadding(
                baseLeft + safeInsets.left,
                baseTop + safeInsets.top,
                baseRight + safeInsets.right,
                baseBottom + safeInsets.bottom
            )
            insets
        }
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
        applySystemBarInsets(appRoot)

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
        val menuButton = TextView(this).apply {
            text = "☰"
            textSize = 23f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(white)
            contentDescription = "Abrir menu de navegação"
            background = rippleBackground(Color.argb(42, 255, 255, 255), 12, Color.argb(70, 255, 255, 255))
            isClickable = true
            isFocusable = true
            setOnClickListener { showNavigationDrawer() }
        }
        brandRow.addView(menuButton, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            setMargins(0, 0, dp(8), 0)
        })

        val mark = TextView(this).apply {
            text = "SM"
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
        brandText.addView(label(displayRestaurantName(), 23f, true, white).apply {
            letterSpacing = 0.02f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        brandText.addView(label("StudioMesa · Gestão de sala", 10f, true, copperPale))
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
        header.addView(label(if (hasRemoteRestaurant()) "Restaurante configurado · dados do servidor" else "Demonstração · Dados de exemplo", 10.5f, true, copperPale))
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

        // Keep the three high-frequency destinations visible on phones.
        // Secondary destinations remain available from the hamburger drawer above.
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setBackgroundColor(white)
            elevation = dp(6).toFloat()
        }
        listOf("Início", "Mesas", "Pedidos").forEach { page ->
            val selected = activePage == page
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(3), dp(3), dp(3))
                background = rounded(if (selected) copperPale else white, 12)
                contentDescription = "Abrir secção " + page
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    activePage = page
                    renderApp()
                }
            }
            item.addView(TextView(this).apply {
                text = pageGlyph(page)
                textSize = 17f
                gravity = Gravity.CENTER
                setTextColor(if (selected) forestDeep else muted)
            }, LinearLayout.LayoutParams(-2, dp(21)))
            item.addView(label(page, 10.5f, true, if (selected) forestDeep else muted).apply {
                gravity = Gravity.CENTER
                maxLines = 1
            })
            nav.addView(item, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
        }
        appRoot.addView(nav, LinearLayout.LayoutParams(-1, dp(60)))
        setContentView(appRoot)
    }

    private fun renderPage(container: LinearLayout) {
        when (activePage) {
            "Início" -> renderHome(container)
            "Mesas" -> renderTables(container)
            "Pedidos" -> renderOrders(container)
            "Cozinha" -> renderKitchen(container)
            "Carta" -> renderMenu(container)
            "Pontos" -> renderCustomers(container)
            "Conta" -> renderBackendAccount(container)
            "Preço Justo" -> renderPriceCalculator(container)
            "Reservas" -> renderReservations(container)
            "Stock" -> renderStock(container)
            "Análise" -> renderAnalysis(container)
            "Assistente" -> renderAssistant(container)
            else -> renderHome(container)
        }
    }

    private fun renderHome(container: LinearLayout) {
        container.addView(label("Painel de operação", 10f, true, forestSoft))
        addGap(container, 5)
        container.addView(label("A sala, num relance.", 27f, true, forestDeep))
        addGap(container, 5)
        container.addView(label("Demonstração StudioMesa. Os dados, a carta e os pedidos são exemplos; configura o restaurante e introduz dados aprovados antes de qualquer utilização real.", 14f, false, muted))
        addGap(container, 18)

        val tableTotal: Int
        val occupied = if (hasRemoteRestaurant()) {
            val tables = remoteTables()
            tableTotal = tables.length()
            (0 until tables.length()).count { tables.optJSONObject(it)?.optBoolean("is_occupied", false) == true }
        } else {
            tableTotal = 12
            store.occupiedTableCount()
        }
        val activeOrders = if (hasRemoteRestaurant()) {
            val orders = remoteOrders()
            (0 until orders.length()).count {
                val status = orders.optJSONObject(it)?.optString("status").orEmpty()
                status != "Concluído" && status != "Cancelado"
            }
        } else store.getOrders().count { it.status != "Concluído" && it.status != "Cancelado" }
        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stats.addView(statCard("MESAS OCUPADAS", "$occupied / $tableTotal", if (occupied > 0) copper else forestSoft),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), 0) })
        stats.addView(statCard("PEDIDOS ATIVOS", activeOrders.toString(), if (activeOrders > 0) copper else forestSoft),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), 0, 0, 0) })
        container.addView(stats)

        addGap(container, 21)
        sectionHeading(container, "Acesso rápido", "Escolhe uma área para continuar.")
        actionCard(container, "01", "Mesas", "Consulta as " + tableTotal + " mesas do restaurante.", "ABRIR MESAS") {
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
            actionCard(container, "05", "Reservas", "Regista reservas, capacidade e disponibilidade de mesas.", "GERIR RESERVAS") {
                activePage = "Reservas"; renderApp()
            }
            actionCard(container, "06", "Preço Justo", "Calcula um preço indicativo a partir do custo.", "ABRIR CALCULADORA") {
                activePage = "Preço Justo"; renderApp()
            }
        }
        if (isPremium) {
            actionCard(container, "07", "Stock e inventário", "Controla quantidades, níveis mínimos e movimentos.", "GERIR STOCK") {
                activePage = "Stock"; renderApp()
            }
            actionCard(container, "08", "Análise operacional", "Resumo dos pedidos, artigos e custos registados.", "VER ANÁLISE") {
                activePage = "Análise"; renderApp()
            }
        }
        actionCard(container, "IA", "Assistente StudioMesa", "Sugestões de operação e preços com os dados disponíveis.", "ABRIR ASSISTENTE") {
            activePage = "Assistente"; renderApp()
        }

        addGap(container, 6)
        val note = makeCard(14, copperPale)
        note.addView(label(if (hasRemoteRestaurant()) "OPERAÇÃO PARTILHADA" else "DEMONSTRAÇÃO", 10f, true, forest))
        addGap(note, 4)
        note.addView(label(
            if (hasRemoteRestaurant()) "Mesas, carta, pedidos e estados ficam guardados no servidor e podem ser consultados pela equipa autenticada. Ainda não existe integração com caixa registadora, pagamentos ou impressora de cozinha."
            else "O modo de demonstração guarda mesas, carta e pedidos apenas neste telemóvel. Liga uma conta e configura o restaurante para usar os dados partilhados no servidor.",
            12.5f, false, ink
        ))
        container.addView(note, cardParams())
    }

    private fun renderTables(container: LinearLayout) {
        pageIntro(container, "Mesas", if (hasRemoteRestaurant())
            "Estado, zona e capacidade partilhados pela equipa."
        else "Toca numa mesa para alternar entre livre e ocupada.")
        addGap(container, 8)

        if (hasRemoteRestaurant()) {
            container.addView(primaryButton("+  Adicionar mesa") { showAddTableDialog() },
                LinearLayout.LayoutParams(-1, dp(48)))
            addGap(container, 6)
            container.addView(secondaryButton("Atualizar do servidor") { refreshRemoteOperations() },
                LinearLayout.LayoutParams(-1, dp(48)))
            addGap(container, 10)

            val tableData = remoteTables()
            if (tableData.length() == 0) {
                container.addView(emptyCard("Ainda não existem mesas sincronizadas. Atualiza os dados ou volta a iniciar sessão."))
                return
            }

            for (index in 0 until tableData.length() step 2) {
                val first = tableData.optJSONObject(index) ?: continue
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                row.addView(remoteTableCard(
                    first.optString("id"),
                    first.optInt("table_number", index + 1),
                    first.optBoolean("is_occupied", false),
                    first.optString("area", "Sala principal"),
                    first.optInt("seats", 2)
                ), LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins(0, 0, dp(6), dp(10))
                })
                if (index + 1 < tableData.length()) {
                    val second = tableData.optJSONObject(index + 1)
                    if (second != null) {
                        row.addView(remoteTableCard(
                            second.optString("id"),
                            second.optInt("table_number", index + 2),
                            second.optBoolean("is_occupied", false),
                            second.optString("area", "Sala principal"),
                            second.optInt("seats", 2)
                        ), LinearLayout.LayoutParams(0, -2, 1f).apply {
                            setMargins(dp(6), 0, 0, dp(10))
                        })
                    } else {
                        row.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))
                    }
                } else {
                    row.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))
                }
                container.addView(row)
            }
            container.addView(label("Mesas numeradas até 100. As zonas e os lugares podem ser configurados para refletir a organização real do restaurante.", 12f, false, muted))
            return
        }

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
        container.addView(label("O modo de demonstração local inclui 12 mesas. Configura um restaurante para gerir a planta no servidor.", 12f, false, muted))
    }

    private fun remoteTableCard(
        tableId: String,
        tableNumber: Int,
        occupied: Boolean,
        area: String,
        seats: Int
    ): LinearLayout {
        val card = makeCard(14, if (occupied) paleRed else white)
        card.addView(label("Mesa  " + tableNumber.toString().padStart(2, '0'), 10f, true, muted))
        addGap(card, 8)
        card.addView(label(if (occupied) "Ocupada" else "Livre", 18f, true, if (occupied) red else forest))
        addGap(card, 4)
        card.addView(label(area, 12f, true, forestSoft))
        card.addView(label("$seats lugares", 11f, false, muted))
        addGap(card, 9)
        card.addView(label(if (occupied) "MARCAR LIVRE" else "MARCAR OCUPADA", 9f, true, if (occupied) red else green).apply {
            setPadding(dp(7), dp(6), dp(7), dp(6))
            gravity = Gravity.CENTER
            background = rounded(if (occupied) Color.rgb(255, 246, 243) else paleGreen, 12)
        })
        addGap(card, 5)
        card.addView(secondaryButton("Editar zona / lugares") {
            showEditTableDialog(tableId, tableNumber, area, seats)
        }, LinearLayout.LayoutParams(-1, dp(38)))
        card.isClickable = true
        card.isFocusable = true
        card.setOnClickListener {
            if (tableId.isBlank()) return@setOnClickListener
            runBackend({
                val token = backendToken()
                SupabaseApi.updateTable(token, tableId, !occupied)
                cacheRemoteOperations(token, backendRestaurantId())
                "OK"
            }) { renderApp() }
        }
        return card
    }

    private fun showAddTableDialog() {
        if (!hasRemoteRestaurant()) return
        val existing = remoteTables()
        val usedNumbers = (0 until existing.length()).mapNotNull {
            existing.optJSONObject(it)?.optInt("table_number")
        }.toSet()
        val nextNumber = (1..100).firstOrNull { it !in usedNumbers } ?: 0

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(4))
        }
        val numberField = inputField("Número da mesa (1–100)", InputType.TYPE_CLASS_NUMBER).apply {
            if (nextNumber > 0) setText(nextNumber.toString())
        }
        val areaField = inputField("Zona: Sala principal, Terraço…", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES).apply {
            setText("Sala principal")
        }
        val seatsField = inputField("Número de lugares (1–30)", InputType.TYPE_CLASS_NUMBER).apply {
            setText("2")
        }
        form.addView(label("Número", 10f, true, forestSoft)); form.addView(numberField, fieldParams())
        addGap(form, 2)
        form.addView(label("Zona", 10f, true, forestSoft)); form.addView(areaField, fieldParams())
        addGap(form, 2)
        form.addView(label("Capacidade", 10f, true, forestSoft)); form.addView(seatsField, fieldParams())

        val dialog = AlertDialog.Builder(this)
            .setTitle("Adicionar mesa")
            .setMessage("Define o número, a zona e a capacidade para a planta do restaurante.")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Adicionar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val number = numberField.text.toString().trim().toIntOrNull()
                val area = areaField.text.toString().trim()
                val seats = seatsField.text.toString().trim().toIntOrNull()
                when {
                    number == null || number !in 1..100 -> numberField.error = "Usa um número entre 1 e 100"
                    number in usedNumbers -> numberField.error = "Esse número já está em uso"
                    area.isBlank() || area.length > 80 -> areaField.error = "Introduz uma zona com 1 a 80 caracteres"
                    seats == null || seats !in 1..30 -> seatsField.error = "Usa entre 1 e 30 lugares"
                    else -> runBackend({
                        val token = backendToken()
                        val restaurantId = backendRestaurantId()
                        SupabaseApi.addTable(token, restaurantId, number, area, seats)
                        cacheRemoteOperations(token, restaurantId)
                        "OK"
                    }) {
                        dialog.dismiss()
                        renderApp()
                    }
                }
            }
        }
        dialog.show()
        if (nextNumber == 0) {
            numberField.error = "Já foram usados os números de mesa de 1 a 100"
        }
    }

    private fun showEditTableDialog(tableId: String, tableNumber: Int, currentArea: String, currentSeats: Int) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(4))
        }
        val areaField = inputField("Zona da mesa", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES).apply {
            setText(currentArea)
        }
        val seatsField = inputField("Número de lugares (1–30)", InputType.TYPE_CLASS_NUMBER).apply {
            setText(currentSeats.toString())
        }
        form.addView(label("Mesa " + tableNumber.toString().padStart(2, '0'), 11f, true, forestSoft))
        addGap(form, 5)
        form.addView(label("Zona", 10f, true, forestSoft)); form.addView(areaField, fieldParams())
        addGap(form, 2)
        form.addView(label("Capacidade", 10f, true, forestSoft)); form.addView(seatsField, fieldParams())
        val dialog = AlertDialog.Builder(this)
            .setTitle("Configurar mesa")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val area = areaField.text.toString().trim()
                val seats = seatsField.text.toString().trim().toIntOrNull()
                when {
                    area.isBlank() || area.length > 80 -> areaField.error = "Introduz uma zona com 1 a 80 caracteres"
                    seats == null || seats !in 1..30 -> seatsField.error = "Usa entre 1 e 30 lugares"
                    else -> runBackend({
                        val token = backendToken()
                        SupabaseApi.updateTableDetails(token, tableId, tableNumber, area, seats)
                        cacheRemoteOperations(token, backendRestaurantId())
                        "OK"
                    }) {
                        dialog.dismiss()
                        renderApp()
                    }
                }
            }
        }
        dialog.show()
    }



    private fun tableCard(table: Int): LinearLayout {
        val occupied = store.isTableOccupied(table)
        val card = makeCard(15, if (occupied) paleRed else white)
        card.addView(label("Mesa  " + table.toString().padStart(2, '0'), 10f, true, muted))
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

    private fun showOrderHistory(orderId: String) {
        if (orderId.isBlank() || !hasRemoteRestaurant()) return
        runBackend({
            val events = SupabaseApi.listOrderEvents(backendToken(), orderId)
            if (events.length() == 0) {
                "Ainda não há eventos de histórico para este pedido."
            } else {
                buildString {
                    for (index in 0 until events.length()) {
                        val event = events.optJSONObject(index) ?: continue
                        val type = event.optString("event_type")
                        val oldStatus = event.optString("old_status")
                        val newStatus = event.optString("new_status")
                        val description = when (type) {
                            "created" -> "Pedido criado · estado inicial: " + newStatus.ifBlank { "Aberto" }
                            "status_changed" -> (oldStatus.ifBlank { "—" }) + " → " + newStatus.ifBlank { "—" }
                            else -> "Evento registado: " + type
                        }
                        val timestamp = event.optString("created_at")
                        val readableTime = timestamp.replace('T', ' ').take(19)
                        val zone = when {
                            timestamp.endsWith("Z") || timestamp.endsWith("+00:00") -> " UTC"
                            timestamp.matches(Regex(".*[+-]\\d{2}:\\d{2}$")) -> " " + timestamp.takeLast(6)
                            else -> ""
                        }
                        append(readableTime).append(zone).append("\n")
                        append(description)
                        val actor = event.optString("actor_user_id")
                        if (actor.isNotBlank()) append("\nConta: …").append(actor.takeLast(8))
                        if (index < events.length() - 1) append("\n\n")
                    }
                }
            }
        }) { history ->
            val textView = TextView(this).apply {
                text = history
                textSize = 13f
                setTextColor(ink)
                setPadding(dp(20), dp(12), dp(20), dp(12))
                setLineSpacing(dp(3).toFloat(), 1.0f)
            }
            val scroll = ScrollView(this).apply { addView(textView) }
            AlertDialog.Builder(this)
                .setTitle("Histórico do pedido")
                .setView(scroll)
                .setPositiveButton("Fechar", null)
                .show()
        }
    }

    private fun confirmCancelOrder(orderId: String, tableNumber: Int, itemName: String) {
        AlertDialog.Builder(this)
            .setTitle("Cancelar pedido?")
            .setMessage("Mesa $tableNumber · $itemName\n\nO cancelamento ficará registado no histórico do pedido.")
            .setNegativeButton("Manter pedido", null)
            .setPositiveButton("Cancelar pedido") { _, _ ->
                runBackend({
                    val token = backendToken()
                    SupabaseApi.updateOrder(token, orderId, "Cancelado")
                    cacheRemoteOperations(token, backendRestaurantId())
                    "OK"
                }) { renderApp() }
            }
            .show()
    }

    private fun renderOrders(container: LinearLayout) {
        pageIntro(container, "Pedidos", if (hasRemoteRestaurant())
            "Pedidos partilhados com o resto da equipa."
        else "Registo simples de pedidos por mesa.")
        addGap(container, 10)
        if (hasRemoteRestaurant()) {
            container.addView(secondaryButton("Atualizar do servidor") { refreshRemoteOperations() },
                LinearLayout.LayoutParams(-1, dp(48)))
            addGap(container, 7)
        }
        container.addView(primaryButton("+  Novo pedido") { showCreateOrderDialog() }, LinearLayout.LayoutParams(-1, dp(50)))
        addGap(container, 15)

        if (hasRemoteRestaurant()) {
            val orders = remoteOrders()
            if (orders.length() == 0) {
                container.addView(emptyCard("Ainda não há pedidos registados no servidor. Cria o primeiro pedido para testar o fluxo."))
                return
            }
            for (index in 0 until orders.length()) {
                val order = orders.optJSONObject(index) ?: continue
                val status = order.optString("status", "Aberto")
                val closed = status == "Concluído" || status == "Cancelado"
                val nextStatus = when (status) {
                    "Aberto" -> "Em preparação"
                    "Em preparação" -> "Pronto"
                    "Pronto" -> "Concluído"
                    else -> "Aberto"
                }
                val card = makeCard(16)
                val top = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                top.addView(label("Mesa " + order.optInt("table_number", 1).toString().padStart(2, '0'), 10f, true, forestSoft),
                    LinearLayout.LayoutParams(0, -2, 1f))
                top.addView(label(status.uppercase(Locale("pt", "PT")), 9f, true, if (closed) green else forestSoft).apply {
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    background = rounded(if (closed) paleGreen else copperPale, 12)
                })
                card.addView(top)
                addGap(card, 10)
                val itemName = order.optString("item_name", "Artigo")
                val quantity = order.optInt("quantity", 1).coerceIn(1, 99)
                card.addView(label((if (quantity > 1) "$quantity × " else "") + itemName, 17f, true, ink))
                addGap(card, 3)
                card.addView(label(
                    order.optString("service_course", "Principal").uppercase(Locale("pt", "PT")),
                    10f, true, forestSoft
                ))
                val notes = order.optString("notes").trim()
                if (notes.isNotBlank()) {
                    addGap(card, 5)
                    card.addView(label("COZINHA · $notes", 11.5f, false, muted))
                }
                addGap(card, 5)
                card.addView(label(money(order.optDouble("amount", 0.0) * quantity), 14f, true, forest))
                addGap(card, 12)
                card.addView(primaryButton(if (closed) "Reabrir pedido" else when (status) {
                    "Aberto" -> "Enviar para preparação"
                    "Em preparação" -> "Marcar como pronto"
                    else -> "Concluir pedido"
                }) {
                    runBackend({
                        val token = backendToken()
                        SupabaseApi.updateOrder(token, order.optString("id"), nextStatus)
                        cacheRemoteOperations(token, backendRestaurantId())
                        "OK"
                    }) { renderApp() }
                }, LinearLayout.LayoutParams(-1, dp(48)))
                addGap(card, 5)
                if (!closed) {
                    card.addView(secondaryButton("Cancelar pedido") {
                        confirmCancelOrder(
                            order.optString("id"),
                            order.optInt("table_number", 1),
                            order.optString("item_name", "Artigo")
                        )
                    }, LinearLayout.LayoutParams(-1, dp(48)))
                    addGap(card, 5)
                }
                card.addView(secondaryButton("Ver histórico") {
                    showOrderHistory(order.optString("id"))
                }, LinearLayout.LayoutParams(-1, dp(48)))
                container.addView(card, cardParams())
            }
            return
        }

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
            top.addView(label("Mesa " + order.table.toString().padStart(2, '0'), 10f, true, forestSoft), LinearLayout.LayoutParams(0, -2, 1f))
            top.addView(label(if (done) "CONCLUÍDO" else "EM ABERTO", 9f, true, if (done) green else forestSoft).apply {
                setPadding(dp(8), dp(6), dp(8), dp(6))
                background = rounded(if (done) paleGreen else copperPale, 12)
            })
            card.addView(top)
            addGap(card, 10)
            card.addView(label((if (order.quantity > 1) "${order.quantity} × " else "") + order.itemName, 17f, true, ink))
            addGap(card, 3)
            card.addView(label(order.serviceCourse.uppercase(Locale("pt", "PT")), 10f, true, forestSoft))
            if (order.notes.isNotBlank()) {
                addGap(card, 5)
                card.addView(label("Cozinha · " + order.notes, 11.5f, false, muted))
            }
            addGap(card, 5)
            card.addView(label(money(order.amount * order.quantity), 14f, true, forest))
            addGap(card, 12)
            card.addView(secondaryButton(if (done) "Reabrir pedido" else "Marcar como concluído") {
                store.updateOrder(order.id, if (done) "Aberto" else "Concluído")
                renderApp()
            }, LinearLayout.LayoutParams(-1, dp(48)))
            container.addView(card, cardParams())
        }
    }


    private fun parseOrderTimestamp(value: String): Long {
        val normalized = value.replace(Regex("\\.\\d+(?=(Z|[+-]\\d{2}:?\\d{2})$)"), "")
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
                isLenient = false
            }.parse(normalized)?.time ?: 0L
        }.getOrDefault(0L)
    }

    private fun orderWaitLabel(createdAtMillis: Long): String {
        if (createdAtMillis <= 0L) return "Tempo de espera indisponível"
        val minutes = ((System.currentTimeMillis() - createdAtMillis) / 60_000L).coerceAtLeast(0L)
        return when {
            minutes < 1L -> "Acabado de entrar"
            minutes < 60L -> "$minutes min em espera"
            else -> "${minutes / 60L} h ${minutes % 60L} min em espera"
        }
    }

    private fun renderKitchen(container: LinearLayout) {
        pageIntro(container, "Cozinha", "Fila de preparação por estado e tempo de espera.")
        addGap(container, 8)
        if (hasRemoteRestaurant()) {
            container.addView(secondaryButton("Atualizar fila do servidor") { refreshRemoteOperations() },
                LinearLayout.LayoutParams(-1, dp(48)))
        } else {
            container.addView(label("Modo de demonstração · os pedidos não são partilhados com outros dispositivos.", 11f, false, muted))
        }
        addGap(container, 12)

        if (hasRemoteRestaurant()) {
            val orders = remoteOrders()
            val active = (0 until orders.length())
                .mapNotNull { orders.optJSONObject(it) }
                .filter { it.optString("status", "Aberto") !in setOf("Concluído", "Cancelado") }
                .sortedWith(compareBy<JSONObject>(
                    { when (it.optString("status", "Aberto")) {
                        "Aberto" -> 0
                        "Em preparação" -> 1
                        "Pronto" -> 2
                        else -> 3
                    } },
                    { parseOrderTimestamp(it.optString("created_at")) }
                ))

            if (active.isEmpty()) {
                container.addView(emptyCard("A fila está vazia. Os novos pedidos aparecerão aqui depois de atualizares os dados."))
                return
            }
            container.addView(label("${active.size} pedido(s) ativo(s)", 12f, true, forestSoft))
            addGap(container, 8)
            for (order in active) {
                val status = order.optString("status", "Aberto")
                val createdAt = parseOrderTimestamp(order.optString("created_at"))
                val nextStatus = when (status) {
                    "Aberto" -> "Em preparação"
                    "Em preparação" -> "Pronto"
                    "Pronto" -> "Concluído"
                    else -> status
                }
                renderKitchenTicket(
                    container,
                    order.optInt("table_number", 1),
                    order.optString("item_name", "Artigo"),
                    order.optInt("quantity", 1).coerceIn(1, 99),
                    order.optString("service_course", "Principal"),
                    order.optString("notes").trim(),
                    status,
                    orderWaitLabel(createdAt)
                ) {
                    runBackend({
                        val token = backendToken()
                        SupabaseApi.updateOrder(token, order.optString("id"), nextStatus)
                        cacheRemoteOperations(token, backendRestaurantId())
                        "OK"
                    }) { renderApp() }
                }
            }
        } else {
            val active = store.getOrders()
                .filter { it.status !in setOf("Concluído", "Cancelado") }
                .sortedBy { it.createdAt }
            if (active.isEmpty()) {
                container.addView(emptyCard("A fila está vazia. Cria um pedido para testar o fluxo da cozinha."))
                return
            }
            container.addView(label("${active.size} pedido(s) ativo(s)", 12f, true, forestSoft))
            addGap(container, 8)
            active.forEach { order ->
                val nextStatus = when (order.status) {
                    "Aberto" -> "Em preparação"
                    "Em preparação" -> "Pronto"
                    "Pronto" -> "Concluído"
                    else -> order.status
                }
                renderKitchenTicket(
                    container,
                    order.table,
                    order.itemName,
                    order.quantity,
                    order.serviceCourse,
                    order.notes,
                    order.status,
                    orderWaitLabel(order.createdAt)
                ) {
                    store.updateOrder(order.id, nextStatus)
                    renderApp()
                }
            }
        }
    }

    private fun renderKitchenTicket(
        container: LinearLayout,
        tableNumber: Int,
        itemName: String,
        quantity: Int,
        serviceCourse: String,
        notes: String,
        status: String,
        waitLabel: String,
        onAdvance: () -> Unit
    ) {
        val card = makeCard(16)
        val statusColor = when (status) {
            "Pronto" -> green
            "Em preparação" -> forestSoft
            else -> copper
        }
        val statusBackground = when (status) {
            "Pronto" -> paleGreen
            "Em preparação" -> paleGreen
            else -> copperPale
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(label("MESA ${tableNumber.toString().padStart(2, '0')}", 12f, true, forestSoft),
            LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(label(status.uppercase(Locale("pt", "PT")), 10f, true, statusColor).apply {
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = rounded(statusBackground, 12)
        })
        card.addView(top)
        addGap(card, 8)
        card.addView(label((if (quantity > 1) "$quantity × " else "") + itemName, 19f, true, forestDeep))
        addGap(card, 4)
        card.addView(label(serviceCourse.uppercase(Locale("pt", "PT")), 11f, true, forestSoft))
        if (notes.isNotBlank()) {
            addGap(card, 8)
            card.addView(label("NOTAS PARA A COZINHA", 9.5f, true, muted))
            card.addView(label(notes, 14f, false, ink))
        }
        addGap(card, 8)
        card.addView(label(waitLabel, 11.5f, true, if (waitLabel.contains("min em espera") || waitLabel.contains("h ")) red else muted))
        addGap(card, 12)
        card.addView(primaryButton(when (status) {
            "Aberto" -> "Iniciar preparação"
            "Em preparação" -> "Marcar como pronto"
            "Pronto" -> "Concluir pedido"
            else -> "Atualizar pedido"
        }, onAdvance), LinearLayout.LayoutParams(-1, dp(48)))
        container.addView(card, cardParams())
    }

    private fun renderMenu(container: LinearLayout) {
        pageIntro(container, "Carta e custos", if (hasRemoteRestaurant())
            "Carta e custos privados, guardados no servidor."
        else "Artigos de exemplo para testar a gestão.")
        addGap(container, 7)
        if (hasRemoteRestaurant()) {
            container.addView(makeCard(13, copperPale).apply {
                addView(label("Carta do restaurante", 10f, true, forest))
                addGap(this, 4)
                addView(label("A carta é partilhada pela equipa. Os custos e margens só são apresentados na aplicação de gestão, não no cartão de cliente.", 12.5f, false, ink))
            }, cardParams())
            container.addView(secondaryButton("Atualizar do servidor") { refreshRemoteOperations() },
                LinearLayout.LayoutParams(-1, dp(48)))
            addGap(container, 6)
            container.addView(primaryButton("+  Adicionar artigo") { showAddMenuItemDialog() }, LinearLayout.LayoutParams(-1, dp(48)))
            addGap(container, 15)
            val items = remoteMenuItems()
            if (items.length() == 0) {
                container.addView(emptyCard("A carta do servidor está vazia. Adiciona os primeiros artigos e preços aprovados pelo restaurante."))
                return
            }
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val card = makeCard(15)
                card.addView(label(item.optString("name"), 17f, true, forestDeep))
                addGap(card, 3)
                card.addView(label(item.optString("category", "Prato principal"), 10.5f, true, forestSoft))
                val description = item.optString("description").trim()
                if (description.isNotBlank()) {
                    addGap(card, 5)
                    card.addView(label(description, 12.5f, false, ink))
                }
                val allergens = item.optString("allergens").trim()
                addGap(card, 5)
                card.addView(label(
                    if (allergens.isBlank()) "Alergénios: confirmar com a equipa."
                    else "Alergénios declarados: $allergens. Confirma com a equipa se tiveres uma alergia.",
                    11f, false, muted
                ))
                addGap(card, 8)
                val prices = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                val price = item.optDouble("price", 0.0)
                val cost = item.optDouble("cost", 0.0)
                prices.addView(priceColumn("PREÇO DE VENDA", money(price)), LinearLayout.LayoutParams(0, -2, 1f))
                prices.addView(priceColumn("CUSTO ESTIMADO", money(cost)), LinearLayout.LayoutParams(0, -2, 1f))
                card.addView(prices)
                val ratio = if (price > 0.0) cost / price * 100.0 else 0.0
                addGap(card, 9)
                card.addView(label("Custo do artigo: " + String.format(Locale("pt", "PT"), "%.1f%%", ratio) + " do preço de venda", 11.5f, false, muted))
                addGap(card, 9)
                card.addView(secondaryButton("Editar artigo") {
                    showEditMenuItemDialog(
                        item.optString("id"),
                        MenuEntry(
                            item.optString("name"),
                            item.optDouble("price", 0.0),
                            item.optDouble("cost", 0.0),
                            item.optString("category", "Prato principal"),
                            item.optString("description", ""),
                            item.optString("allergens", "")
                        )
                    )
                }, LinearLayout.LayoutParams(-1, dp(48)))
                addGap(card, 5)
                card.addView(secondaryButton("Arquivar artigo") {
                    AlertDialog.Builder(this)
                        .setTitle("Arquivar artigo?")
                        .setMessage("«" + item.optString("name") + "» deixa de aparecer na carta ativa. Os pedidos antigos mantêm o registo.")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Arquivar") { _, _ ->
                            runBackend({
                                val token = backendToken()
                                SupabaseApi.deactivateMenuItem(token, item.optString("id"))
                                cacheRemoteOperations(token, backendRestaurantId())
                                "OK"
                            }) { renderApp() }
                        }
                        .show()
                }, LinearLayout.LayoutParams(-1, dp(48)))
                container.addView(card, cardParams())
            }
            return
        }

        container.addView(makeCard(13, copperPale).apply {
            addView(label("Carta de demonstração", 10f, true, forest))
            addGap(this, 4)
            addView(label("Os nomes e valores abaixo são ilustrativos; não representam a carta real do restaurante.", 12.5f, false, ink))
        }, cardParams())
        container.addView(primaryButton("+  Adicionar artigo") { showAddMenuItemDialog() }, LinearLayout.LayoutParams(-1, dp(48)))
        addGap(container, 15)

        val items = store.getMenu()
        if (items.isEmpty()) {
            container.addView(emptyCard("A carta está vazia. Adiciona o primeiro artigo de demonstração."))
        }
        items.forEach { item ->
            val card = makeCard(15)
            card.addView(label(item.name, 17f, true, forestDeep))
            addGap(card, 3)
            card.addView(label(item.category, 10.5f, true, forestSoft))
            if (item.description.isNotBlank()) {
                addGap(card, 5)
                card.addView(label(item.description, 12.5f, false, ink))
            }
            addGap(card, 5)
            card.addView(label(
                if (item.allergens.isBlank()) "Alergénios: confirmar com a equipa."
                else "Alergénios declarados: " + item.allergens + ". Confirma com a equipa se tiveres uma alergia.",
                11f, false, muted
            ))
            addGap(card, 8)
            val prices = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            prices.addView(priceColumn("PREÇO DE VENDA", money(item.price)), LinearLayout.LayoutParams(0, -2, 1f))
            prices.addView(priceColumn("CUSTO ESTIMADO", money(item.cost)), LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(prices)
            val ratio = if (item.price > 0.0) item.cost / item.price * 100.0 else 0.0
            addGap(card, 9)
            card.addView(label("Custo do artigo: " + String.format(Locale("pt", "PT"), "%.1f%%", ratio) + " do preço de venda", 11.5f, false, muted))
            addGap(card, 9)
            card.addView(secondaryButton("Editar artigo") {
                showEditMenuItemDialog(null, item)
            }, LinearLayout.LayoutParams(-1, dp(48)))
            addGap(card, 5)
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
            }, LinearLayout.LayoutParams(-1, dp(48)))
            container.addView(card, cardParams())
        }
    }

    private fun renderPriceCalculator(container: LinearLayout) {
        pageIntro(container, "Preço Justo", "Uma estimativa matemática baseada no custo.")
        addGap(container, 5)
        container.addView(label("Não consulta preços concorrentes em tempo real e não substitui uma análise completa de custos.", 12.5f, false, muted))
        addGap(container, 15)

        val form = makeCard(17)
        form.addView(label("Custo por unidade (€)", 10f, true, forestSoft))
        val costField = inputField("Ex.: 3,20", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        form.addView(costField, fieldParams())
        addGap(form, 7)
        form.addView(label("Custo pretendido (% do preço)", 10f, true, forestSoft))
        val percentageField = inputField("Ex.: 30", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        percentageField.setText("30")
        form.addView(percentageField, fieldParams())
        addGap(form, 10)

        val result = label("Introduz os valores e calcula um preço indicativo.", 14f, false, ink).apply {
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(paper, 12)
        }
        // Never leave an old suggested price visible after the user edits the inputs.
        val inputChangeWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                result.text = "Valores alterados. Calcula novamente para atualizar o preço indicativo."
                result.setTextColor(ink)
            }

            override fun afterTextChanged(s: android.text.Editable?) = Unit
        }
        costField.addTextChangedListener(inputChangeWatcher)
        percentageField.addTextChangedListener(inputChangeWatcher)

        // Clear only the edited field's validation error as soon as the user corrects it.
        fun onCalculatorInputEdited(field: EditText) {
            field.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    field.error = null
                    // A result belongs to the exact input pair that produced it. Do not leave
                    // an old price visible after either input changes.
                    result.text = "Valores alterados. Calcula novamente para atualizar o preço indicativo."
                    result.setTextColor(ink)
                }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }
        onCalculatorInputEdited(costField)
        onCalculatorInputEdited(percentageField)

        form.addView(primaryButton("Calcular preço sugerido") {
            val calculation = try {
                PriceCalculator.calculate(
                    costField.text.toString(),
                    percentageField.text.toString()
                )
            } catch (error: PriceCalculationException) {
                result.text = "Corrige os valores assinalados para atualizar o preço indicativo."
                result.setTextColor(red)
                when (error.field) {
                    PriceInputField.COST -> costField.error = error.message
                    PriceInputField.TARGET_COST_PERCENT -> percentageField.error = error.message
                }
                return@primaryButton
            }

            result.text = "PREÇO INDICATIVO\n" + money(calculation.suggestedPrice) +
                "\n\nCusto por unidade: " + money(calculation.cost) +
                "\nCusto sobre o preço final: " + formatPercentage(calculation.actualCostPercent) +
                "\nMargem bruta estimada: " + money(calculation.grossProfit) +
                "\nMargem bruta: " + formatPercentage(calculation.grossMarginPercent)
            result.setTextColor(forest)
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

    private fun displayRestaurantName(): String =
        backendPrefs.getString("restaurant_name", null)?.trim()?.takeIf { it.isNotEmpty() }
            ?: "StudioMesa"

    private fun displayCustomerRestaurantName(): String =
        backendPrefs.getString("member_restaurant_name", null)?.trim()?.takeIf { it.isNotEmpty() }
            ?: "StudioMesa"

    private fun refreshSessionIfNeeded() {
        val accessToken = backendToken()
        val refreshToken = secureSession.refreshToken()
        if (accessToken.isBlank() || refreshToken.isBlank()) return

        val expiresAt = SupabaseApi.tokenExpiresAtMillis(accessToken)
        if (expiresAt > System.currentTimeMillis() + 120_000L) return

        val session = SupabaseApi.refreshSession(refreshToken)
        secureSession.save(session.accessToken, session.refreshToken)
        backendPrefs.edit()
            .putString("user_id", session.userId)
            .putString("user_email", session.email)
            .apply()

        if (isCustomerApp) restoreCustomerMembership(session.accessToken) else restoreOwnedRestaurant(session.accessToken)
    }

    private fun restoreOwnedRestaurant(token: String) {
        if (isCustomerApp || token.isBlank()) return

        try {
            val restaurant = SupabaseApi.getMyRestaurant(token)
            val editor = backendPrefs.edit()
                .remove("restaurant_id")
                .remove("restaurant_name")
                .remove("join_code")
                .remove("staff_join_code")
                .remove("restaurant_role")
            if (restaurant != null) {
                val restaurantId = restaurant.optString("restaurant_id")
                editor.putString("restaurant_id", restaurantId)
                    .putString("restaurant_name", restaurant.optString("restaurant_name"))
                    .putString("join_code", restaurant.optString("join_code"))
                    .putString("staff_join_code", restaurant.optString("staff_code"))
                    .putString("restaurant_role", restaurant.optString("user_role", "staff"))
            }
            editor.apply()
            if (restaurant != null) {
                cacheRemoteOperations(token, restaurant.optString("restaurant_id"))
            } else {
                // The server confirmed this account no longer belongs to a restaurant.
                // Remove data that would otherwise remain on this device after revocation.
                backendPrefs.edit()
                    .remove("remote_members_json")
                    .remove("remote_tables_json")
                    .remove("remote_menu_json")
                    .remove("remote_orders_json")
                    .remove("remote_inventory_json")
                    .remove("inventory_loaded")
                    .remove("remote_reservations_json")
                    .remove("reservations_loaded")
                    .apply()
                reservationCache = JSONArray()
                reservationsLoaded = false
            }
        } catch (_: Exception) {
            // Keep authentication successful if restoring cloud data temporarily fails.
        }
    }



    private fun hasRemoteRestaurant(): Boolean =
        backendToken().isNotBlank() && backendRestaurantId().isNotBlank()

    private fun savedRemoteArray(key: String): org.json.JSONArray {
        return try {
            org.json.JSONArray(backendPrefs.getString(key, "[]") ?: "[]")
        } catch (_: Exception) {
            org.json.JSONArray()
        }
    }

    private fun remoteTables(): org.json.JSONArray = savedRemoteArray("remote_tables_json")
    private fun remoteMenuItems(): org.json.JSONArray = savedRemoteArray("remote_menu_json")
    private fun remoteOrders(): org.json.JSONArray = savedRemoteArray("remote_orders_json")

    private fun remoteMenuEntries(): MutableList<MenuEntry> {
        val result = mutableListOf<MenuEntry>()
        val array = remoteMenuItems()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val name = item.optString("name").trim()
            if (name.isNotBlank()) {
                result.add(MenuEntry(
                    name,
                    item.optDouble("price", 0.0).coerceAtLeast(0.0),
                    item.optDouble("cost", 0.0).coerceAtLeast(0.0),
                    item.optString("category", "Prato principal"),
                    item.optString("description", ""),
                    item.optString("allergens", "")
                ))
            }
        }
        return result
    }

    private fun cacheRemoteOperations(token: String, restaurantId: String) {
        if (token.isBlank() || restaurantId.isBlank()) return
        val tables = SupabaseApi.listTables(token, restaurantId)
        val menu = SupabaseApi.listMenuItems(token, restaurantId)
        val orders = SupabaseApi.listOrders(token, restaurantId)
        backendPrefs.edit()
            .putString("remote_tables_json", tables.toString())
            .putString("remote_menu_json", menu.toString())
            .putString("remote_orders_json", orders.toString())
            .apply()
    }

    private fun refreshRemoteOperations() {
        val restaurantId = backendRestaurantId()
        if (!hasRemoteRestaurant() || restaurantId.isBlank()) {
            AlertDialog.Builder(this)
                .setMessage("Inicia sessão e configura o restaurante na secção Conta para sincronizar os dados.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        runBackend({
            cacheRemoteOperations(backendToken(), restaurantId)
            "OK"
        }) {
            renderApp()
        }
    }

    private fun restoreCustomerMembership(token: String) {
        if (!isCustomerApp || token.isBlank()) return
        try {
            val member = SupabaseApi.getMyLoyaltyMembership(token)
            if (member == null) {
                backendPrefs.edit()
                    .remove("member_id")
                    .remove("member_code")
                    .remove("member_points")
                    .remove("member_restaurant_name")
                    .putString("customer_menu_json", "[]")
                    .apply()
                return
            }

            val memberId = member.optString("member_id")
            val menu = if (memberId.isNotBlank()) SupabaseApi.listCustomerMenu(token, memberId) else org.json.JSONArray()
            backendPrefs.edit()
                .putString("member_id", memberId)
                .putString("member_code", member.optString("member_code"))
                .putInt("member_points", member.optInt("points", 0))
                .putString("member_restaurant_name", member.optString("restaurant_name"))
                .putString("customer_menu_json", menu.toString())
                .apply()
            val displayName = member.optString("display_name").trim()
            if (displayName.isNotBlank()) store.saveCustomerProfileName(displayName)
        } catch (_: Exception) {
            // Do not discard a valid login if the membership is temporarily unavailable.
        }
    }


    /**
     * A freshly established session must not inherit cached operational data.
     * Clear it even if the account ID matches: restaurant membership or permissions
     * may have changed while signed out, and the server must repopulate authoritative data.
     */
    private fun prepareForAuthenticatedUser(newUserId: String) {
        if (newUserId.isBlank()) {
            throw IllegalStateException("O servidor não devolveu um identificador de conta válido.")
        }

        clearEconomicManagerState()
        store.clearLocalDemoData()
        backendPrefs.edit()
            .putBoolean("local_demo_data_isolated_v1", true)
            .remove("user_id").remove("user_email")
            .remove("access_token").remove("refresh_token")
            .remove("restaurant_id").remove("restaurant_name").remove("join_code")
            .remove("staff_join_code").remove("restaurant_role")
            .remove("member_id").remove("member_code").remove("member_points")
            .remove("member_restaurant_name")
            .remove("remote_members_json").remove("remote_tables_json")
            .remove("remote_menu_json").remove("remote_orders_json")
            .remove("customer_menu_json")
            .remove("remote_inventory_json").remove("inventory_loaded")
            .remove("remote_reservations_json").remove("reservations_loaded")
            .apply()

        reservationCache = JSONArray()
        reservationsLoaded = false
    }

    private fun backendToken(): String = secureSession.accessToken()
    private fun backendRestaurantId(): String = backendPrefs.getString("restaurant_id", "") ?: ""
    private fun backendMemberId(): String = backendPrefs.getString("member_id", "") ?: ""

    private fun friendlyBackendError(rawMessage: String?): String {
        val message = rawMessage?.trim().orEmpty()
        val normalized = message.lowercase(Locale.ROOT)
        return when {
            normalized.contains("invalid login credentials") ->
                "Não foi possível iniciar sessão com estes dados. Se é a primeira utilização, volta atrás e escolhe «Criar conta». Se já tens conta, confirma o email e verifica o endereço e a palavra-passe."
            normalized.contains("email not confirmed") || normalized.contains("email_not_confirmed") ->
                "O email ainda não foi confirmado. Abre a mensagem de confirmação enviada pelo Supabase (verifica também Spam) e, depois, escolhe «Iniciar sessão»."
            normalized.contains("user already registered") || normalized.contains("already been registered") ->
                "Este email já tem uma conta. Volta atrás e escolhe «Iniciar sessão» em vez de «Criar conta»."
            normalized.contains("email rate limit exceeded") || normalized.contains("rate limit") ->
                "O serviço limitou temporariamente os pedidos. Aguarda um pouco antes de tentares novamente."
            normalized.contains("unable to resolve host") || normalized.contains("failed to connect") ||
                normalized.contains("network is unreachable") || normalized.contains("timeout") ->
                "Não foi possível ligar ao servidor. Confirma que o telemóvel tem internet e tenta novamente."
            else -> message.take(500).ifBlank {
                "Erro de ligação. Confirma a internet e tenta novamente."
            }
        }
    }

    private fun runBackend(
        task: () -> String,
        refreshSession: Boolean = true,
        onSuccess: (String) -> Unit
    ) {
        val progress = AlertDialog.Builder(this)
            .setTitle("A ligar ao StudioMesa")
            .setMessage("A validar a conta no servidor. Aguarda, por favor…")
            .setCancelable(false)
            .create()
        progress.show()
        Thread {
            try {
                if (refreshSession) refreshSessionIfNeeded()
                val result = task()
                runOnUiThread {
                    if (!isFinishing) {
                        progress.dismiss()
                        onSuccess(result)
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!isFinishing) {
                        progress.dismiss()
                        AlertDialog.Builder(this)
                            .setTitle("Não foi possível concluir")
                            .setMessage(friendlyBackendError(error.message))
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }.start()
    }

    private fun showSetNewPasswordDialog() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(4))
        }
        val passwordField = inputField(
            "Nova palavra-passe (mínimo 8 caracteres)",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        val repeatField = inputField(
            "Confirmar nova palavra-passe",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        form.addView(label("Nova palavra-passe", 10f, true, forestSoft))
        form.addView(passwordField, fieldParams())
        addGap(form, 4)
        form.addView(label("Confirmar palavra-passe", 10f, true, forestSoft))
        form.addView(repeatField, fieldParams())

        val dialog = AlertDialog.Builder(this)
            .setTitle("Definir nova palavra-passe")
            .setMessage("Escolhe uma palavra-passe nova e não a partilhes.")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar palavra-passe", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val password = passwordField.text.toString()
                val repeated = repeatField.text.toString()
                when {
                    password.length < 8 -> passwordField.error = "Usa pelo menos 8 caracteres"
                    password != repeated -> repeatField.error = "As palavras-passe não coincidem"
                    backendToken().isBlank() -> {
                        dialog.dismiss()
                        AlertDialog.Builder(this)
                            .setTitle("Ligação expirada")
                            .setMessage("Abre novamente a ligação de recuperação mais recente que recebeste por email.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    else -> runBackend({
                        SupabaseApi.updatePassword(backendToken(), password)
                        "UPDATED"
                    }) {
                        dialog.dismiss()
                        AlertDialog.Builder(this)
                            .setTitle("Palavra-passe atualizada")
                            .setMessage("A palavra-passe foi alterada. Guarda-a num gestor de palavras-passe e não a partilhes.")
                            .setPositiveButton("Continuar") { _, _ ->
                                if (isCustomerApp) renderCustomerApp() else renderApp()
                            }
                            .show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showAuthChooser(afterAuth: () -> Unit) {
        // Use explicit buttons in a custom view. Combining setMessage and setItems
        // can render inconsistently across Android dialog implementations.
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), dp(6))
        }
        form.addView(label(
            "Primeira utilização? Escolhe «Criar conta». Se já tens uma conta, escolhe «Iniciar sessão».",
            13f, false, muted
        ))
        addGap(form, 14)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Conta StudioMesa")
            .setView(form)
            .setNegativeButton("Fechar", null)
            .create()

        form.addView(primaryButton("Criar conta") {
            dialog.dismiss()
            showCredentialsDialog(true, afterAuth)
        }, LinearLayout.LayoutParams(-1, dp(48)))
        addGap(form, 8)
        form.addView(secondaryButton("Iniciar sessão") {
            dialog.dismiss()
            showCredentialsDialog(false, afterAuth)
        }, LinearLayout.LayoutParams(-1, dp(48)))
        addGap(form, 14)
        form.addView(label("Ou continuar com uma conta externa:", 11.5f, true, muted))
        addGap(form, 6)
        form.addView(secondaryButton("Continuar com Google") {
            dialog.dismiss()
            startOAuth("google")
        }, LinearLayout.LayoutParams(-1, dp(48)))
        addGap(form, 6)
        form.addView(secondaryButton("Continuar com Microsoft (Outlook)") {
            dialog.dismiss()
            startOAuth("azure")
        }, LinearLayout.LayoutParams(-1, dp(48)))
        addGap(form, 7)
        form.addView(label(
            "Google e Outlook só podem ser usados depois de os fornecedores estarem ativados no servidor.",
            10.5f, false, muted
        ))

        dialog.show()
    }

    private fun createPkceVerifier(): String {
        val random = ByteArray(32)
        java.security.SecureRandom().nextBytes(random)
        return android.util.Base64.encodeToString(
            random,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
    }

    private fun createPkceChallenge(verifier: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return android.util.Base64.encodeToString(
            digest,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
    }

    private fun startOAuth(provider: String) {
        runBackend({
            if (!SupabaseApi.isOAuthProviderEnabled(provider)) {
                throw IllegalStateException(
                    if (provider == "google") {
                        "O início de sessão com Google ainda não está ativado no Supabase. É necessário configurar as credenciais OAuth do Google no servidor."
                    } else {
                        "O início de sessão com Microsoft/Outlook ainda não está ativado no Supabase. É necessário configurar as credenciais OAuth da Microsoft no servidor."
                    }
                )
            }
            val verifier = createPkceVerifier()
            val challenge = createPkceChallenge(verifier)
            secureSession.savePendingCodeVerifier(verifier, "oauth")
            SupabaseApi.oauthUrl(provider, challenge)
        }, refreshSession = false) { authUrl ->
            try {
                startActivity(android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(authUrl)
                ))
            } catch (_: Exception) {
                runCatching { secureSession.clearPendingCodeVerifier() }
                AlertDialog.Builder(this)
                    .setTitle("Não foi possível abrir o início de sessão")
                    .setMessage("Confirma que tens um navegador instalado e tenta novamente.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun showCredentialsDialog(createAccount: Boolean, afterAuth: () -> Unit) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
        }
        val emailField = inputField("Email", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val passwordField = inputField("Palavra-passe (mínimo 8 caracteres)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        form.addView(label("Email", 10f, true, forestSoft))
        form.addView(emailField, fieldParams())
        addGap(form, 4)
        form.addView(label("Palavra-passe", 10f, true, forestSoft))
        form.addView(passwordField, fieldParams())
        // Keep the instructions inside the custom form so Android always
        // shows the email and password fields together with the message.
        form.addView(label(
            "Usa um email a que tenhas acesso e uma palavra-passe com pelo menos 8 caracteres. Não partilhes estes dados.",
            12.5f, false, muted
        ), 0)
        addGap(form, 10)
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (createAccount) "Criar conta" else "Iniciar sessão")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton(if (createAccount) "Criar conta" else "Entrar", null)
            .create()
        if (!createAccount) {
            addGap(form, 2)
            form.addView(secondaryButton("Esqueci-me da palavra-passe") {
                val email = emailField.text.toString().trim()
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    emailField.error = "Introduz primeiro o email da tua conta"
                } else {
                    runBackend({
                        val verifier = createPkceVerifier()
                        val challenge = createPkceChallenge(verifier)
                        secureSession.savePendingCodeVerifier(verifier, "recovery")
                        SupabaseApi.requestPasswordRecovery(email, challenge)
                        "SENT"
                    }, refreshSession = false) {
                        dialog.dismiss()
                        AlertDialog.Builder(this)
                            .setTitle("Verifica o teu email")
                            .setMessage("Se existir uma conta com este endereço, será enviado um email para recuperar o acesso. Abre a ligação no mesmo telemóvel para definires uma nova palavra-passe. Verifica também Spam.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }, LinearLayout.LayoutParams(-1, dp(48)))
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val email = emailField.text.toString().trim()
                val password = passwordField.text.toString()
                when {
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                        emailField.error = "Introduz um email válido"
                    password.length < 8 ->
                        passwordField.error = "Usa pelo menos 8 caracteres"
                    else -> {
                        runBackend({
                            if (createAccount) {
                                val verifier = createPkceVerifier()
                                val challenge = createPkceChallenge(verifier)
                                secureSession.savePendingCodeVerifier(verifier, "signup")
                                val result = SupabaseApi.signUp(email, password, challenge)
                                val token = result.optString("access_token")
                                val user = result.optJSONObject("user")
                                when {
                                    token.isNotBlank() && user != null -> {
                                        secureSession.save(token, result.optString("refresh_token"))
                                        prepareForAuthenticatedUser(user.optString("id"))
                                        secureSession.clearPendingCodeVerifier()
                                        backendPrefs.edit()
                                            .putString("user_id", user.optString("id"))
                                            .putString("user_email", email)
                                            .apply()
                                        if (isCustomerApp) restoreCustomerMembership(token) else restoreOwnedRestaurant(token)
                                        "SIGNED_IN"
                                    }
                                    user != null -> {
                                        backendPrefs.edit()
                                            .putString("user_id", user.optString("id"))
                                            .putString("user_email", email)
                                            .apply()
                                        "CONFIRM_EMAIL"
                                    }
                                    else -> throw IllegalStateException(
                                        "O servidor não confirmou a criação da conta. Tenta novamente; se o erro continuar, regista a mensagem que aparecer."
                                    )
                                }
                            } else {
                                val session = SupabaseApi.signIn(email, password)
                                secureSession.save(session.accessToken, session.refreshToken)
                                prepareForAuthenticatedUser(session.userId)
                                backendPrefs.edit()
                                    .putString("user_id", session.userId)
                                    .putString("user_email", session.email)
                                    .apply()
                                if (isCustomerApp) restoreCustomerMembership(session.accessToken) else restoreOwnedRestaurant(session.accessToken)
                                "SIGNED_IN"
                            }
                        }, refreshSession = false) { result ->
                            dialog.dismiss()
                            if (result == "CONFIRM_EMAIL") {
                                AlertDialog.Builder(this)
                                    .setTitle("Falta confirmar o email")
                                    .setMessage("A conta foi criada, mas ainda não foi iniciada sessão. Abre o email de confirmação no mesmo telemóvel e toca na ligação; deverá abrir o StudioMesa. Verifica também Spam/Promoções. Se a ligação não concluir a sessão, pede uma nova. Não cries outra conta com o mesmo email.")
                                    .setPositiveButton("OK", null)
                                    .show()
                            } else {
                                afterAuth()
                            }
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun generateSecureCode(length: Int): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val secureRandom = java.security.SecureRandom()
        return buildString(length) {
            repeat(length) { append(alphabet[secureRandom.nextInt(alphabet.length)]) }
        }
    }

    private fun showCreateRestaurantDialog() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
        }
        val nameField = inputField("Nome do restaurante", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        val code = generateSecureCode(12)
        form.addView(label("Nome", 10f, true, forestSoft))
        form.addView(nameField, fieldParams())
        addGap(form, 5)
        form.addView(label("Código de adesão gerado", 10f, true, forestSoft))
        form.addView(label(code, 23f, true, forest).apply { setPadding(dp(4), dp(10), dp(4), dp(10)) })
        form.addView(label("Guarda este código e partilha-o apenas com os clientes que devem aderir ao programa.", 12f, false, muted))
        val dialog = AlertDialog.Builder(this)
            .setTitle("Configurar restaurante")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Criar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameField.text.toString().trim()
                if (name.length !in 2..100) {
                    nameField.error = "Usa entre 2 e 100 caracteres"
                } else {
                    runBackend({
                        val token = backendToken()
                        val restaurantId = SupabaseApi.createRestaurant(token, name, code)
                        restoreOwnedRestaurant(token)
                        restaurantId
                    }) { restaurantId ->
                        dialog.dismiss()
                        // A successful create_restaurant RPC creates the current user as owner.
                        // Keep owner controls available even if the follow-up refresh briefly fails.
                        backendPrefs.edit().putString("restaurant_id", restaurantId)
                            .putString("restaurant_name", name).putString("join_code", code)
                            .putString("restaurant_role", "owner").apply()
                        renderApp()
                        AlertDialog.Builder(this)
                            .setTitle("Restaurante criado")
                            .setMessage("Código de adesão: $code\n\nOs clientes podem usar este código na aplicação de cliente depois de criarem conta e iniciarem sessão.")
                            .setPositiveButton("OK", null).show()
                    }
                }
            }
        }
        dialog.show()
    }


    private fun showRestaurantTeam() {
        if (backendToken().isBlank() || (backendPrefs.getString("restaurant_role", "") ?: "") != "owner") {
            AlertDialog.Builder(this)
                .setTitle("Acesso reservado")
                .setMessage("Só o proprietário do restaurante pode consultar ou remover membros da equipa.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        runBackend({
            SupabaseApi.listRestaurantTeam(backendToken()).toString()
        }) { raw ->
            val members = JSONArray(raw)
            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(8), dp(14), dp(8))
            }
            if (members.length() == 0) {
                content.addView(emptyCard("O servidor não devolveu membros para este restaurante."))
            }

            lateinit var teamDialog: AlertDialog
            for (index in 0 until members.length()) {
                val member = members.optJSONObject(index) ?: continue
                val userId = member.optString("member_user_id")
                val email = member.optString("member_email").trim().ifBlank { "Conta " + userId.take(8) }
                val role = member.optString("member_role", "staff")
                val joined = member.optString("joined_at")
                val joinedText = runCatching {
                    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                    val formatter = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "PT"))
                    formatter.format(parser.parse(joined) ?: Date())
                }.getOrElse { "" }

                val card = makeCard(12)
                card.addView(label(email, 14f, true, forestDeep))
                addGap(card, 3)
                card.addView(label(
                    (if (role == "owner") "Proprietário · acesso protegido" else "Funcionário · acesso operacional") +
                        if (joinedText.isNotBlank()) " · desde $joinedText" else "",
                    10.5f, false, muted
                ))
                if (role == "staff") {
                    addGap(card, 8)
                    card.addView(secondaryButton("Remover acesso") {
                        AlertDialog.Builder(this)
                            .setTitle("Remover acesso à equipa?")
                            .setMessage("A conta $email deixará de pertencer a este restaurante. O utilizador poderá voltar a aderir se receber um novo código de acesso.")
                            .setNegativeButton("Cancelar", null)
                            .setPositiveButton("Remover") { _, _ ->
                                runBackend({
                                    val token = backendToken()
                                    SupabaseApi.removeRestaurantStaff(token, userId)
                                    // The server rotates the shared code when access is revoked.
                                    backendPrefs.edit().remove("staff_join_code").apply()
                                    runCatching {
                                        val latestRestaurant = SupabaseApi.getMyRestaurant(token)
                                        if (latestRestaurant != null) {
                                            backendPrefs.edit()
                                                .putString("staff_join_code", latestRestaurant.optString("staff_code"))
                                                .apply()
                                        }
                                    }
                                    "OK"
                                }) {
                                    teamDialog.dismiss()
                                    AlertDialog.Builder(this)
                                        .setTitle("Acesso removido")
                                        .setMessage("O acesso foi revogado e o código antigo da equipa deixou de ser válido. Usa «Ver código de acesso à equipa» para obter o novo código antes de o partilhares com funcionários autorizados.")
                                        .setPositiveButton("OK") { _, _ -> showRestaurantTeam() }
                                        .show()
                                }
                            }
                            .show()
                    }, LinearLayout.LayoutParams(-1, dp(48)))
                }
                content.addView(card, cardParams())
            }

            val scroll = ScrollView(this).apply { addView(content) }
            teamDialog = AlertDialog.Builder(this)
                .setTitle("Membros da equipa")
                .setView(scroll)
                .setPositiveButton("Fechar", null)
                .create()
            teamDialog.show()
            teamDialog.window?.setLayout(-1, dp(560))
        }
    }

    private fun showStaffJoinCode() {
        val role = backendPrefs.getString("restaurant_role", "") ?: ""
        if (role != "owner") {
            AlertDialog.Builder(this)
                .setTitle("Código não disponível")
                .setMessage("Só o proprietário pode consultar o código de acesso à equipa.")
                .setPositiveButton("OK", null).show()
            return
        }
        // Always fetch the current code. A staff revocation rotates it on the server.
        runBackend({
            val restaurant = SupabaseApi.getMyRestaurant(backendToken())
                ?: throw IllegalStateException("Não foi possível obter os dados do restaurante.")
            val code = restaurant.optString("staff_code").trim()
            if (code.isBlank()) throw IllegalStateException("O servidor não devolveu um código válido.")
            backendPrefs.edit().putString("staff_join_code", code).apply()
            code
        }) { code ->
            AlertDialog.Builder(this)
                .setTitle("Código de acesso à equipa")
                .setMessage("Código: $code\n\nPartilha-o apenas com funcionários autorizados. Cada funcionário deve criar a sua própria conta no StudioMesa e escolher «Aderir à equipa de um restaurante».")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun confirmCodeRotation(staffCode: Boolean) {
        val title = if (staffCode) "Renovar código da equipa?" else "Renovar código dos clientes?"
        val message = if (staffCode) {
            "O código atual deixa de aceitar novas adesões da equipa. Os funcionários já associados mantêm o acesso."
        } else {
            "O código atual deixa de aceitar novas adesões de clientes. Os cartões já associados mantêm os pontos."
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Gerar novo código", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                runBackend({
                    val token = backendToken()
                    val code = if (staffCode) {
                        SupabaseApi.rotateStaffJoinCode(token)
                    } else {
                        SupabaseApi.rotateCustomerJoinCode(token)
                    }
                    val key = if (staffCode) "staff_join_code" else "join_code"
                    backendPrefs.edit().putString(key, code).apply()
                    code
                }) { code ->
                    dialog.dismiss()
                    renderApp()
                    AlertDialog.Builder(this)
                        .setTitle("Novo código gerado")
                        .setMessage((if (staffCode) "Código da equipa: " else "Código dos clientes: ") + code +
                            "\n\nGuarda-o e partilha-o apenas com as pessoas autorizadas.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
        dialog.show()
    }

    private fun showJoinStaffDialog() {
        if (backendToken().isBlank()) {
            showAuthChooser { renderApp() }
            return
        }
        val field = inputField("Código de acesso à equipa", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
        }
        form.addView(label("Código fornecido pelo proprietário", 10f, true, forestSoft))
        form.addView(field, fieldParams())
        val dialog = AlertDialog.Builder(this)
            .setTitle("Aderir à equipa")
            .setMessage("Usa o teu próprio email e a tua própria conta. Nunca partilhes a palavra-passe do proprietário.")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Aderir", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = field.text.toString().trim().uppercase(Locale.ROOT)
                if (code.length !in 6..16) {
                    field.error = "Introduz um código válido"
                } else {
                    runBackend({
                        val token = backendToken()
                        SupabaseApi.joinRestaurantStaff(token, code)
                        restoreOwnedRestaurant(token)
                        "OK"
                    }) {
                        dialog.dismiss()
                        activePage = "Conta"
                        renderApp()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun renderBackendAccount(container: LinearLayout) {
        pageIntro(container, "Conta e sincronização", "Conta, restaurante e acesso da equipa.")
        addGap(container, 12)
        val token = backendToken()
        val card = makeCard(17)
        if (token.isBlank()) {
            card.addView(label("Ainda sem sessão", 10f, true, forestSoft))
            addGap(card, 6)
            card.addView(label("Cria uma conta ou inicia sessão para configurar um restaurante ou aderir à equipa de um restaurante existente.", 13f, false, ink))
            addGap(card, 12)
            card.addView(primaryButton("Criar conta / iniciar sessão") { showAuthChooser { renderApp() } },
                LinearLayout.LayoutParams(-1, dp(48)))
        } else if (backendRestaurantId().isBlank()) {
            card.addView(label("Sessão iniciada", 10f, true, green))
            addGap(card, 5)
            card.addView(label(backendPrefs.getString("user_email", "") ?: "", 13f, false, muted))
            addGap(card, 12)
            card.addView(primaryButton("Configurar novo restaurante") { showCreateRestaurantDialog() },
                LinearLayout.LayoutParams(-1, dp(48)))
            addGap(card, 7)
            card.addView(secondaryButton("Aderir à equipa de um restaurante") { showJoinStaffDialog() },
                LinearLayout.LayoutParams(-1, dp(48)))
        } else {
            val restaurantName = backendPrefs.getString("restaurant_name", "Restaurante") ?: "Restaurante"
            val role = backendPrefs.getString("restaurant_role", "staff") ?: "staff"
            card.addView(label(if (role == "owner") "CONTA DO PROPRIETÁRIO" else "ACESSO DE EQUIPA", 10f, true, green))
            addGap(card, 6)
            card.addView(label(restaurantName, 20f, true, forestDeep))
            addGap(card, 5)
            if (role == "owner") {
                card.addView(label("Código de adesão dos clientes", 11f, false, muted))
                card.addView(label(backendPrefs.getString("join_code", "") ?: "", 23f, true, forest))
                addGap(card, 8)
                card.addView(label("Partilha este código apenas com clientes que devem aderir ao programa de fidelização.", 12f, false, ink))
                addGap(card, 8)
                card.addView(secondaryButton("Renovar código de adesão dos clientes") { confirmCodeRotation(false) },
                    LinearLayout.LayoutParams(-1, dp(48)))
                addGap(card, 5)
                card.addView(secondaryButton("Ver código de acesso à equipa") { showStaffJoinCode() },
                    LinearLayout.LayoutParams(-1, dp(48)))
                addGap(card, 5)
                card.addView(secondaryButton("Gerir membros e acessos da equipa") { showRestaurantTeam() },
                    LinearLayout.LayoutParams(-1, dp(48)))
                addGap(card, 5)
                card.addView(secondaryButton("Renovar código da equipa") { confirmCodeRotation(true) },
                    LinearLayout.LayoutParams(-1, dp(48)))
            } else {
                card.addView(label("Esta conta está associada à equipa deste restaurante. As alterações operacionais são guardadas no servidor.", 12f, false, ink))
            }
            addGap(card, 12)
            card.addView(primaryButton("Atualizar lista de clientes") { refreshRemoteCustomers() },
                LinearLayout.LayoutParams(-1, dp(48)))
        }
        container.addView(card, cardParams())
        addGap(container, 3)
        if (token.isNotBlank()) {
            container.addView(secondaryButton("Eliminar conta permanentemente") {
                showDeleteAccountConfirmation()
            }, LinearLayout.LayoutParams(-1, dp(48)))
            addGap(container, 7)
        }
        package pt.studiomesa.app

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.ColorStateList
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : Activity() {
    private lateinit var store: RestaurantStore
    private lateinit var appRoot: LinearLayout
    private var activePage = "Início"
    private var activeCustomerPage = "Cartão"
    private var assistantQuestion = ""
    private var assistantAnswer: StudioMesaAssistantAnswer? = null

    // Optional economic tools are opt-in and session-only until secure, owner-scoped
    // persistence and multi-device authorization are designed and tested.
    private var economicManagerEnabled = false
    private var economicScopeKey: String? = null
    private val economicInputs = mutableMapOf<String, String>()
    private var economicReportText = ""
    private var recipeReportText = ""
    private var reorderReportText = ""

    private val backendPrefs by lazy { getSharedPreferences("studiomesa_backend", MODE_PRIVATE) }
    private val secureSession by lazy { SecureSessionStore(this) }
    private val pendingRequestIds by lazy { PendingRequestIds(this) }
    // Reservation contacts are kept in memory only, not persisted to SharedPreferences.
    private var reservationCache = JSONArray()
    private var reservationsLoaded = false

    private val flavor = BuildConfig.FLAVOR.lowercase(Locale.ROOT)
    private val isCustomerApp = flavor.contains("cliente")
    private val isPremium = flavor.contains("premium")
    private val hasCalculator = flavor.contains("profissional") || isPremium

    private val forest = Color.rgb(57, 78, 67)
    private val forestDeep = Color.rgb(43, 59, 50)
    private val forestSoft = Color.rgb(77, 101, 87)
    private val copper = Color.rgb(211, 177, 139)
    private val copperPale = Color.rgb(245, 237, 224)
    private val paper = Color.rgb(248, 248, 244)
    private val white = Color.WHITE
    private val ink = Color.rgb(47, 57, 51)
    private val muted = Color.rgb(106, 116, 108)
    private val line = Color.rgb(230, 229, 220)
    private val paleGreen = Color.rgb(235, 242, 236)
    private val paleRed = Color.rgb(249, 237, 233)
    private val red = Color.rgb(157, 65, 56)
    private val green = Color.rgb(55, 105, 72)

    private val planTitle: String
        get() = when {
            flavor.contains("essencial") -> "ESSENCIAL"
            flavor.contains("premium") -> "PREMIUM"
            else -> "PROFISSIONAL"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Remove reservation contact data left by older demo builds which cached it on disk.
        backendPrefs.edit()
            .remove("remote_reservations_json")
            .remove("reservations_loaded")
            .apply()
        store = RestaurantStore(this)
        // Remove pre-release local demo records from accounts that were already signed in
        // when this privacy isolation was introduced.
        if (backendPrefs.getString("user_id", "").orEmpty().isNotBlank() &&
            !backendPrefs.getBoolean("local_demo_data_isolated_v1", false)) {
            store.clearLocalDemoData()
            backendPrefs.edit().putBoolean("local_demo_data_isolated_v1", true).apply()
        }
        window.statusBarColor = forestDeep
        window.navigationBarColor = forestDeep
        renderApp()
        handleAuthCallback(intent)

        // Refresh shared data when an already-authenticated user opens the app,
        // but do not start a second request while processing an OAuth callback.
        if (intent?.data == null && backendToken().isNotBlank()) {
            if (isCustomerApp && backendMemberId().isNotBlank()) {
                refreshCustomerMembership()
            } else if (!isCustomerApp && hasRemoteRestaurant()) {
                refreshRemoteOperations()
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallback(intent)
    }

    private fun parseAuthParams(raw: String?): Map<String, String> =
        AuthCallbackParser.parse(raw)

    private fun handleAuthCallback(callbackIntent: android.content.Intent?) {
        val uri = callbackIntent?.data ?: return
        if (uri.scheme != "studiomesa" || uri.host != "auth-callback") return
        // Parse the raw query/fragment defensively. getQueryParameter() and
        // URLDecoder can throw on malformed percent-encoding in an external deep link.
        val query = parseAuthParams(uri.encodedQuery)
        val fragment = parseAuthParams(uri.encodedFragment)
        val errorText = query["error_description"]
            ?: fragment["error_description"]
            ?: query["error"]
            ?: fragment["error"]

        if (!errorText.isNullOrBlank()) {
            AlertDialog.Builder(this)
                .setTitle("Autenticação não concluída")
                .setMessage(errorText.replace('+', ' ').take(500))
                .setPositiveButton("OK", null)
                .show()
            return
        }

        // Accept only the one-time PKCE authorization code. Do not accept bearer
        // access/refresh tokens supplied directly in a custom-scheme URL.
        val authCode = query["code"].orEmpty()
        val pendingFlow = secureSession.pendingCodeFlowType()
        val codeVerifier = secureSession.pendingCodeVerifier()
        if (authCode.isBlank() || codeVerifier.isBlank() ||
            pendingFlow !in setOf("oauth", "recovery", "signup")) {
            AlertDialog.Builder(this)
                .setTitle("Ligação de autenticação inválida")
                .setMessage("Não foi recebida uma ligação protegida válida. Pede uma nova ligação e abre-a neste telemóvel, com a aplicação instalada.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val isPasswordRecovery = pendingFlow == "recovery"

        runBackend({
            val session = SupabaseApi.exchangeCodeForSession(authCode, codeVerifier)
            val user = SupabaseApi.getUser(session.accessToken)
            val id = user.optString("id")
            if (id.isBlank()) throw IllegalStateException("O servidor não devolveu uma conta válida.")
            secureSession.save(session.accessToken, session.refreshToken)
            prepareForAuthenticatedUser(id)
            secureSession.clearPendingCodeVerifier()
            backendPrefs.edit()
                .putString("user_id", id)
                .putString("user_email", user.optString("email", session.email))
                .apply()
            if (isCustomerApp) restoreCustomerMembership(session.accessToken)
            else restoreOwnedRestaurant(session.accessToken)
            "SIGNED_IN"
        }, refreshSession = false) {
            if (isPasswordRecovery) {
                showSetNewPasswordDialog()
            } else {
                if (isCustomerApp) renderCustomerApp() else renderApp()
                AlertDialog.Builder(this)
                    .setTitle("Sessão iniciada")
                    .setMessage("A conta foi ligada ao StudioMesa. " +
                        if (isCustomerApp) "Agora associa-te ao restaurante com o respetivo código." else "Agora configura o restaurante para gerar um código de adesão.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun availablePages(): List<String> {
        val pages = mutableListOf("Início", "Mesas", "Pedidos", "Cozinha", "Carta", "Pontos", "Assistente", "Conta")
        if (hasCalculator) {
            pages.add("Reservas")
            pages.add("Preço Justo")
        }
        if (isPremium) {
            pages.add("Stock")
            pages.add("Análise")
        }
        return pages
    }

    private fun showNavigationDrawer() {
        val dialog = Dialog(this)
        val drawerRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(white)
        }

        val drawerHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(24), dp(14), dp(22))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(forestDeep, forest, forestSoft)
            ).apply { cornerRadius = dp(18).toFloat() }
        }
        val titleStack = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleStack.addView(label("StudioMesa", 24f, true, white))
        titleStack.addView(label(displayRestaurantName(), 14f, true, copperPale).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        titleStack.addView(label(planTitle + " · " + if (hasRemoteRestaurant()) "Restaurante configurado" else "Demonstração", 10.5f, false, copperPale))
        drawerHeader.addView(titleStack, LinearLayout.LayoutParams(0, -2, 1f))
        drawerHeader.addView(label("×", 27f, false, white).apply {
            gravity = Gravity.CENTER
            contentDescription = "Fechar menu"
            background = rippleBackground(Color.argb(40, 255, 255, 255), 12, Color.argb(70, 255, 255, 255))
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        drawerRoot.addView(drawerHeader, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(8), dp(8), dp(8), dp(6))
        })

        val listScroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(8))
        }
        val pageGroups = listOf(
            "OPERAÇÃO" to listOf("Início", "Mesas", "Pedidos", "Cozinha"),
            "GESTÃO DO RESTAURANTE" to listOf("Carta", "Pontos", "Reservas", "Preço Justo", "Stock", "Análise"),
            "ASSISTÊNCIA" to listOf("Assistente"),
            "CONTA" to listOf("Conta")
        )
        pageGroups.forEach { (groupTitle, groupPages) ->
            val visiblePages = groupPages.filter { it in availablePages() }
            if (visiblePages.isNotEmpty()) {
                list.addView(label(groupTitle, 10f, true, forestSoft).apply {
                    setPadding(dp(20), dp(13), dp(14), dp(7))
                    letterSpacing = 0.08f
                }, LinearLayout.LayoutParams(-1, -2))
                visiblePages.forEach { page ->
                    val selected = activePage == page
                    val item = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(9), dp(3), dp(12), dp(3))
                        background = rippleBackground(if (selected) paleGreen else white, 12)
                        contentDescription = "Abrir secção " + page
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            dialog.dismiss()
                            activePage = page
                            renderApp()
                        }
                    }
                    item.addView(TextView(this).apply {
                        text = pageGlyph(page)
                        textSize = 19f
                        gravity = Gravity.CENTER
                        setTextColor(if (selected) forestDeep else forestSoft)
                    }, LinearLayout.LayoutParams(dp(36), -1))
                    item.addView(label(page, 14.5f, selected, if (selected) forestDeep else ink),
                        LinearLayout.LayoutParams(0, -2, 1f))
                    list.addView(item, LinearLayout.LayoutParams(-1, dp(48)).apply {
                        setMargins(dp(10), dp(2), dp(10), dp(2))
                    })
                }
            }
        }
        listScroll.addView(list)
        drawerRoot.addView(listScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        drawerRoot.addView(View(this).apply { setBackgroundColor(line) },
            LinearLayout.LayoutParams(-1, dp(1)))
        drawerRoot.addView(label("StudioMesa · Gestão de sala", 10.5f, false, muted).apply {
            setPadding(dp(20), dp(12), dp(20), dp(16))
        }, LinearLayout.LayoutParams(-1, -2))

        dialog.setContentView(drawerRoot)
        dialog.show()
        val drawerWidth = minOf(resources.displayMetrics.widthPixels - dp(48), dp(340))
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.42f)
            setGravity(Gravity.START)
            setLayout(drawerWidth, -1)
        }
    }

    private fun pageGlyph(page: String): String = when (page) {
        "Cartão" -> "▣"
        "Início" -> "⌂"
        "Mesas" -> "▦"
        "Pedidos" -> "≡"
        "Cozinha" -> "◉"
        "Carta" -> "▤"
        "Pontos" -> "◎"
        "Conta" -> "⚙"
        "Reservas" -> "◷"
        "Preço Justo" -> "€"
        "Stock" -> "▣"
        "Análise" -> "↗"
        "Assistente" -> "✦"
        else -> "•"
    }
    /**
     * Android 15+ enforces edge-to-edge for apps targeting API 35+, and Android 16
     * removes the opt-out for apps targeting API 36. Keep interactive content out
     * from behind status/navigation bars and display cutouts on every supported version.
     *
     * Padding is always based on the root's original values so inset updates never
     * accumulate padding as the system UI changes (for example, on rotation).
     */
    private fun applySystemBarInsets(root: View) {
        // Traditional decor fitting already keeps content below the system bars on
        // Android 14 and earlier, so manual padding is only needed from API 35 onward.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return

        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom

        root.setOnApplyWindowInsetsListener { view, insets ->
            val safeInsets = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            view.setPadding(
                baseLeft + safeInsets.left,
                baseTop + safeInsets.top,
                baseRight + safeInsets.right,
                baseBottom + safeInsets.bottom
            )
            insets
        }
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
        applySystemBarInsets(appRoot)

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
        val menuButton = TextView(this).apply {
            text = "☰"
            textSize = 23f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(white)
            contentDescription = "Abrir menu de navegação"
            background = rippleBackground(Color.argb(42, 255, 255, 255), 12, Color.argb(70, 255, 255, 255))
            isClickable = true
            isFocusable = true
            setOnClickListener { showNavigationDrawer() }
        }
        brandRow.addView(menuButton, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            setMargins(0, 0, dp(8), 0)
        })

        val mark = TextView(this).apply {
            text = "SM"
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
        brandText.addView(label(displayRestaurantName(), 23f, true, white).apply {
            letterSpacing = 0.02f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        brandText.addView(label("StudioMesa · Gestão de sala", 10f, true, copperPale))
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
        header.addView(label(if (hasRemoteRestaurant()) "Restaurante configurado · dados do servidor" else "Demonstração · Dados de exemplo", 10.5f, true, copperPale))
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

        // Keep the three high-frequency destinations visible on phones.
        // Secondary destinations remain available from the hamburger drawer above.
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setBackgroundColor(white)
            elevation = dp(6).toFloat()
        }
        listOf("Início", "Mesas", "Pedidos").forEach { page ->
            val selected = activePage == page
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(3), dp(3), dp(3))
                background = rounded(if (selected) copperPale else white, 12)
                contentDescription = "Abrir secção " + page
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    activePage = page
                    renderApp()
                }
            }
            item.addView(TextView(this).apply {
                text = pageGlyph(page)
                textSize = 17f
                gravity = Gravity.CENTER
                setTextColor(if (selected) forestDeep else muted)
            }, LinearLayout.LayoutParams(-2, dp(21)))
            item.addView(label(page, 10.5f, true, if (selected) forestDeep else muted).apply {
                gravity = Gravity.CENTER
                maxLines = 1
            })
            nav.addView(item, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
        }
        appRoot.addView(nav, LinearLayout.LayoutParams(-1, dp(60)))
        setContentView(appRoot)
    }

    private fun renderPage(container: LinearLayout) {
        when (activePage) {
            "Início" -> renderHome(container)
            "Mesas" -> renderTables(container)
            "Pedidos" -> renderOrders(container)
            "Cozinha" -> renderKitchen(container)
            "Carta" -> renderMenu(container)
            "Pontos" -> renderCustomers(container)
            "Conta" -> renderBackendAccount(container)
            "Preço Justo" -> renderPriceCalculator(container)
            "Reservas" -> renderReservations(container)
            "Stock" -> renderStock(container)
            "Análise" -> renderAnalysis(container)
            "Assistente" -> renderAssistant(container)
            else -> renderHome(container)
        }
    }

    private fun renderHome(container: LinearLayout) {
        container.addView(label("Painel de operação", 10f, true, forestSoft))
        addGap(container, 5)
        container.addView(label("A sala, num relance.", 27f, true, forestDeep))
        addGap(container, 5)
        container.addView(label("Demonstração StudioMesa. Os dados, a carta e os pedidos são exemplos; configura o restaurante e introduz dados aprovados antes de qualquer utilização real.", 14f, false, muted))
        addGap(container, 18)

        val tableTotal: Int
        val occupied = if (hasRemoteRestaurant()) {
            val tables = remoteTables()
            tableTotal = tables.length()
            (0 until tables.length()).count { tables.optJSONObject(it)?.optBoolean("is_occupied", false) == true }
        } else {
            tableTotal = 12
            store.occupiedTableCount()
        }
        val activeOrders = if (hasRemoteRestaurant()) {
            val orders = remoteOrders()
            (0 until orders.length()).count {
                val status = orders.optJSONObject(it)?.optString("status").orEmpty()
                status != "Concluído" && status != "Cancelado"
            }
        } else store.getOrders().count { it.status != "Concluído" && it.status != "Cancelado" }
        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stats.addView(statCard("MESAS OCUPADAS", "$occupied / $tableTotal", if (occupied > 0) copper else forestSoft),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), 0) })
        stats.addView(statCard("PEDIDOS ATIVOS", activeOrders.toString(), if (activeOrders > 0) copper else forestSoft),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), 0, 0, 0) })
        container.addView(stats)

        addGap(container, 21)
        sectionHeading(container, "Acesso rápido", "Escolhe uma área para continuar.")
        actionCard(container, "01", "Mesas", "Consulta as " + tableTotal + " mesas do restaurante.", "ABRIR MESAS") {
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
            actionCard(container, "05", "Reservas", "Regista reservas, capacidade e disponibilidade de mesas.", "GERIR RESERVAS") {
                activePage = "Reservas"; renderApp()
            }
            actionCard(container, "06", "Preço Justo", "Calcula um preço indicativo a partir do custo.", "ABRIR CALCULADORA") {
                activePage = "Preço Justo"; renderApp()
            }
        }
        if (isPremium) {
            actionCard(container, "07", "Stock e inventário", "Controla quantidades, níveis mínimos e movimentos.", "GERIR STOCK") {
                activePage = "Stock"; renderApp()
            }
            actionCard(container, "08", "Análise operacional", "Resumo dos pedidos, artigos e custos registados.", "VER ANÁLISE") {
                activePage = "Análise"; renderApp()
            }
        }
        actionCard(container, "IA", "Assistente StudioMesa", "Sugestões de operação e preços com os dados disponíveis.", "ABRIR ASSISTENTE") {
            activePage = "Assistente"; renderApp()
        }

        addGap(container, 6)
        val note = makeCard(14, copperPale)
        note.addView(label(if (hasRemoteRestaurant()) "OPERAÇÃO PARTILHADA" else "DEMONSTRAÇÃO", 10f, true, forest))
        addGap(note, 4)
        note.addView(label(
            if (hasRemoteRestaurant()) "Mesas, carta, pedidos e estados ficam guardados no servidor e podem ser consultados pela equipa autenticada. Ainda não existe integração com caixa registadora, pagamentos ou impressora de cozinha."
            else "O modo de demonstração guarda mesas, carta e pedidos apenas neste telemóvel. Liga uma conta e configura o restaurante para usar os dados partilhados no servidor.",
            12.5f, false, ink
        ))
        container.addView(note, cardParams())
    }

    private fun renderTables(container: LinearLayout) {
        pageIntro(container, "Mesas", if (hasRemoteRestaurant())
            "Estado, zona e capacidade partilhados pela equipa."
        else "Toca numa mesa para alternar entre livre e ocupada.")
        addGap(container, 8)

        if (hasRemoteRestaurant()) {
            container.addView(primaryButton("+  Adicionar mesa") { showAddTableDialog() },
                LinearLayout.LayoutParams(-1, dp(48)))
            addGap(container, 6)
            container.addView(secondaryButton("Atualizar do servidor") { refreshRemoteOperations() },
                LinearLayout.LayoutParams(-1, dp(48)))
            addGap(container, 10)

            val tableData = remoteTables()
            if (tableData.length() == 0) {
                container.addView(emptyCard("Ainda não existem mesas sincronizadas. Atualiza os dados ou volta a iniciar sessão."))
                return
            }

            for (index in 0 until tableData.length() step 2) {
                val first = tableData.optJSONObject(index) ?: continue
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                row.addView(remoteTableCard(
                    first.optString("id"),
                    first.optInt("table_number", index + 1),
                    first.optBoolean("is_occupied", false),
                    first.optString("area", "Sala principal"),
                    first.optInt("seats", 2)
                ), LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins(0, 0, dp(6), dp(10))
                })
                if (index + 1 < tableData.length()) {
                    val second = tableData.optJSONObject(index + 1)
                    if (second != null) {
                        row.addView(remoteTableCard(
                            second.optString("id"),
                            second.optInt("table_number", index + 2),
                            second.optBoolean("is_occupied", false),
                            second.optString("area", "Sala principal"),
                            second.optInt("seats", 2)
                        ), LinearLayout.LayoutParams(0, -2, 1f).apply {
                            setMargins(dp(6), 0, 0, dp(10))
                        })
                    } else {
                        row.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))
                    }
                } else {
                    row.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))
                }
                container.addView(row)
            }
            container.addView(label("Mesas numeradas até 100. As zonas e os lugares podem ser configurados para refletir a organização real do restaurante.", 12f, false, muted))
            return
        }

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
        container.addView(label("O modo de demonstração local inclui 12 mesas. Configura um restaurante para gerir a planta no servidor.", 12f, false, muted))
    }

    private fun remoteTableCard(
        tableId: String,
        tableNumber: Int,
        occupied: Boolean,
        area: String,
        seats: Int
    ): LinearLayout {
        val card = makeCard(14, if (occupied) paleRed else white)
        card.addView(label("Mesa  " + tableNumber.toString().padStart(2, '0'), 10f, true, muted))
        addGap(card, 8)
        card.addView(label(if (occupied) "Ocupada" else "Livre", 18f, true, if (occupied) red else forest))
        addGap(card, 4)
        card.addView(label(area, 12f, true, forestSoft))
        card.addView(label("$seats lugares", 11f, false, muted))
        addGap(card, 9)
        card.addView(label(if (occupied) "MARCAR LIVRE" else "MARCAR OCUPADA", 9f, true, if (occupied) red else green).apply {
            setPadding(dp(7), dp(6), dp(7), dp(6))
            gravity = Gravity.CENTER
            background = rounded(if (occupied) Color.rgb(255, 246, 243) else paleGreen, 12)
        })
        addGap(card, 5)
        card.addView(secondaryButton("Editar zona / lugares") {
            showEditTableDialog(tableId, tableNumber, area, seats)
        }, LinearLayout.LayoutParams(-1, dp(38)))
        card.isClickable = true
        card.isFocusable = true
        card.setOnClickListener {
            if (tableId.isBlank()) return@setOnClickListener
            runBackend({
                val token = backendToken()
                SupabaseApi.updateTable(token, tableId, !occupied)
                cacheRemoteOperations(token, backendRestaurantId())
                "OK"
            }) { renderApp() }
        }
        return card
    }

    private fun showAddTableDialog() {
        if (!hasRemoteRestaurant()) return
        val existing = remoteTables()
        val usedNumbers = (0 until existing.length()).mapNotNull {
            existing.optJSONObject(it)?.optInt("table_number")
        }.toSet()
        val nextNumber = (1..100).firstOrNull { it !in usedNumbers } ?: 0

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(4))
        }
        val numberField = inputField("Número da mesa (1–100)", InputType.TYPE_CLASS_NUMBER).apply {
            if (nextNumber > 0) setText(nextNumber.toString())
        }
        val areaField = inputField("Zona: Sala principal, Terraço…", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES).apply {
            setText("Sala principal")
        }
        val seatsField = inputField("Número de lugares (1–30)", InputType.TYPE_CLASS_NUMBER).apply {
            setText("2")
        }
        form.addView(label("Número", 10f, true, forestSoft)); form.addView(numberField, fieldParams())
        addGap(form, 2)
        form.addView(label("Zona", 10f, true, forestSoft)); form.addView(areaField, fieldParams())
        addGap(form, 2)
        form.addView(label("Capacidade", 10f, true, forestSoft)); form.addView(seatsField, fieldParams())

        val dialog = AlertDialog.Builder(this)
            .setTitle("Adicionar mesa")
            .setMessage("Define o número, a zona e a capacidade para a planta do restaurante.")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Adicionar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val number = numberField.text.toString().trim().toIntOrNull()
                val area = areaField.text.toString().trim()
                val seats = seatsField.text.toString().trim().toIntOrNull()
                when {
                    number == null || number !in 1..100 -> numberField.error = "Usa um número entre 1 e 100"
                    number in usedNumbers -> numberField.error = "Esse número já está em uso"
                    area.isBlank() || area.length > 80 -> areaField.error = "Introduz uma zona com 1 a 80 caracteres"
                    seats == null || seats !in 1..30 -> seatsField.error = "Usa entre 1 e 30 lugares"
                    else -> runBackend({
                        val token = backendToken()
                        val restaurantId = backendRestaurantId()
                        SupabaseApi.addTable(token, restaurantId, number, area, seats)
                        cacheRemoteOperations(token, restaurantId)
                        "OK"
                    }) {
                        dialog.dismiss()
                        renderApp()
                    }
                }
            }
        }
        dialog.show()
        if (nextNumber == 0) {
            numberField.error = "Já foram usados os números de mesa de 1 a 100"
        }
    }

    private fun showEditTableDialog(tableId: String, tableNumber: Int, currentArea: String, currentSeats: Int) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(4))
        }
        val areaField = inputField("Zona da mesa", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES).apply {
            setText(currentArea)
        }
        val seatsField = inputField("Número de lugares (1–30)", InputType.TYPE_CLASS_NUMBER).apply {
            setText(currentSeats.toString())
        }
        form.addView(label("Mesa " + tableNumber.toString().padStart(2, '0'), 11f, true, forestSoft))
        addGap(form, 5)
        form.addView(label("Zona", 10f, true, forestSoft)); form.addView(areaField, fieldParams())
        addGap(form, 2)
        form.addView(label("Capacidade", 10f, true, forestSoft)); form.addView(seatsField, fieldParams())
        val dialog = AlertDialog.Builder(this)
            .setTitle("Configurar mesa")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val area = areaField.text.toString().trim()
                val seats = seatsField.text.toString().trim().toIntOrNull()
                when {
                    area.isBlank() || area.length > 80 -> areaField.error = "Introduz uma zona com 1 a 80 caracteres"
                    seats == null || seats !in 1..30 -> seatsField.error = "Usa entre 1 e 30 lugares"
                    else -> runBackend({
                        val token = backendToken()
                        SupabaseApi.updateTableDetails(token, tableId, tableNumber, area, seats)
                        cacheRemoteOperations(token, backendRestaurantId())
                        "OK"
                    }) {
                        dialog.dismiss()
                        renderApp()
                    }
                }
            }
        }
        dialog.show()
    }



    private fun tableCard(table: Int): LinearLayout {
        val occupied = store.isTableOccupied(table)
        val card = makeCard(15, if (occupied) paleRed else white)
        card.addView(label("Mesa  " + table.toString().padStart(2, '0'), 10f, true, muted))
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

    private fun showOrderHistory(orderId: String) {
        if (orderId.isBlank() || !hasRemoteRestaurant()) return
        runBackend({
            val events = SupabaseApi.listOrderEvents(backendToken(), orderId)
            if (events.length() == 0) {
                "Ainda não há eventos de histórico para este pedido."
            } else {
                buildString {
                    for (index in 0 until events.length()) {
                        val event = events.optJSONObject(index) ?: continue
                        val type = event.optString("event_type")
                        val oldStatus = event.optString("old_status")
                        val newStatus = event.optString("new_status")
                        val description = when (type) {
                            "created" -> "Pedido criado · estado inicial: " + newStatus.ifBlank { "Aberto" }
                            "status_changed" -> (oldStatus.ifBlank { "—" }) + " → " + newStatus.ifBlank { "—" }
                            else -> "Evento registado: " + type
                        }
                        val timestamp = event.optString("created_at")
                        val readableTime = timestamp.replace('T', ' ').take(19)
                        val zone = when {
                            timestamp.endsWith("Z") || timestamp.endsWith("+00:00") -> " UTC"
                            timestamp.matches(Regex(".*[+-]\\d{2}:\\d{2}$")) -> " " + timestamp.takeLast(6)
                            else -> ""
                        }
                        append(readableTime).append(zone).append("\n")
                        append(description)
                        val actor = event.optString("actor_user_id")
                        if (actor.isNotBlank()) append("\nConta: …").append(actor.takeLast(8))
                        if (index < events.length() - 1) append("\n\n")
                    }
                }
            }
        }) { history ->
            val textView = TextView(this).apply {
                text = history
                textSize = 13f
                setTextColor(ink)
                setPadding(dp(20), dp(12), dp(20), dp(12))
                setLineSpacing(dp(3).toFloat(), 1.0f)
            }
            val scroll = ScrollView(this).apply { addView(textView) }
            AlertDialog.Builder(this)
                .setTitle("Histórico do pedido")
                .setView(scroll)
                .setPositiveButton("Fechar", null)
                .show()
        }
    }

    private fun confirmCancelOrder(orderId: String, tableNumber: Int, itemName: String) {
        AlertDialog.Builder(this)
            .setTitle("Cancelar pedido?")
            .setMessage("Mesa $tableNumber · $itemName\n\nO cancelamento ficará registado no histórico do pedido.")
            .setNegativeButton("Manter pedido", null)
            .setPositiveButton("Cancelar pedido") { _, _ ->
                runBackend({
                    val token = backendToken()
                    SupabaseApi.updateOrder(token, orderId, "Cancelado")
                    cacheRemoteOperations(token, backendRestaurantId())
                    "OK"
                }) { renderApp() }
            }
            .show()
    }

    private fun renderOrders(container: LinearLayout) {
        pageIntro(container, "Pedidos", if (hasRemoteRestaurant())
            "Pedidos partilhados com o resto da equipa."
        else "Registo simples de pedidos por mesa.")
        addGap(container, 10)
        if (hasRemoteRestaurant()) {
            container.addView(secondaryButton("Atualizar do servidor") { refreshRemoteOperations() },
                LinearLayout.LayoutParams(-1, dp(48)))
            addGap(container, 7)
        }
        container.addView(primaryButton("+  Novo pedido") { showCreateOrderDialog() }, LinearLayout.LayoutParams(-1, dp(50)))
        addGap(container, 15)

        if (hasRemoteRestaurant()) {
            val orders = remoteOrders()
            if (orders.length() == 0) {
                container.addView(emptyCard("Ainda não há pedidos registados no servidor. Cria o primeiro pedido para testar o fluxo."))
                return
            }
            for (index in 0 until orders.length()) {
                val order = orders.optJSONObject(index) ?: continue
                val status = order.optString("status", "Aberto")
                val closed = status == "Concluído" || status == "Cancelado"
                val nextStatus = when (status) {
                    "Aberto" -> "Em preparação"
                    "Em preparação" -> "Pronto"
                    "Pronto" -> "Concluído"
                    else -> "Aberto"
                }
                val card = makeCard(16)
                val top = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                top.addView(label("Mesa " + order.optInt("table_number", 1).toString().padStart(2, '0'), 10f, true, forestSoft),
                    LinearLayout.LayoutParams(0, -2, 1f))
                top.addView(label(status.uppercase(Locale("pt", "PT")), 9f, true, if (closed) green else forestSoft).apply {
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    background = rounded(if (closed) paleGreen else copperPale, 12)
                })
                card.addView(top)
                addGap(card, 10)
                val itemName = order.optString("item_name", "Artigo")
                val quantity = order.optInt("quantity", 1).coerceIn(1, 99)
                card.addView(label((if (quantity > 1) "$quantity × " else "") + itemName, 17f, true, ink))
                addGap(card, 3)
                card.addView(label(
                    order.optString("service_course", "Principal").uppercase(Locale("pt", "PT")),
                    10f, true, forestSoft
                ))
                val notes = order.optString("notes").trim()
                if (notes.isNotBlank()) {
                    addGap(card, 5)
                    card.addView(label("COZINHA · $notes", 11.5f, false, muted))
                }
                addGap(card, 5)
                card.addView(label(money(order.optDouble("amount", 0.0) * quantity), 14f, true, forest))
                addGap(card, 12)
                card.addView(primaryButton(if (closed) "Reabrir pedido" else when (status) {
                    "Aberto" -> "Enviar para preparação"
                    "Em preparação" -> "Marcar como pronto"
                    else -> "Concluir pedido"
                }) {
                    runBackend({
                        val token = backendToken()
                        SupabaseApi.updateOrder(token, order.optString("id"), nextStatus)
                        cacheRemoteOperations(token, backendRestaurantId())
                        "OK"
                    }) { renderApp() }
                }, LinearLayout.LayoutParams(-1, dp(48)))
                addGap(card, 5)
                if (!closed) {
                    card.addView(secondaryButton("Cancelar pedido") {
                        confirmCancelOrder(
                            order.optString("id"),
                            order.optInt("table_number", 1),
                            order.optString("item_name", "Artigo")
                        )
                    }, LinearLayout.LayoutParams(-1, dp(48)))
                    addGap(card, 5)
                }
                card.addView(secondaryButton("Ver histórico") {
                    showOrderHistory(order.optString("id"))
                }, LinearLayout.LayoutParams(-1, dp(48)))
                container.addView(card, cardParams())
            }
            return
        }

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
            top.addView(label("Mesa " + order.table.toString().padStart(2, '0'), 10f, true, forestSoft), LinearLayout.LayoutParams(0, -2, 1f))
            top.addView(label(if (done) "CONCLUÍDO" else "EM ABERTO", 9f, true, if (done) green else forestSoft).apply {
                setPadding(dp(8), dp(6), dp(8), dp(6))
                background = rounded(if (done) paleGreen else copperPale, 12)
            })
            card.addView(top)
            addGap(card, 10)
            card.addView(label((if (order.quantity > 1) "${order.quantity} × " else "") + order.itemName, 17f, true, ink))
            addGap(card, 3)
            card.addView(label(order.serviceCourse.uppercase(Locale("pt", "PT")), 10f, true, forestSoft))
            if (order.notes.isNotBlank()) {
                addGap(card, 5)
                card.addView(label("Cozinha · " + order.notes, 11.5f, false, muted))
            }
            addGap(card, 5)
            card.addView(label(money(order.amount * order.quantity), 14f, true, forest))
            addGap(card, 12)
            card.addView(secondaryButton(if (done) "Reabrir pedido" else "Marcar como concluído") {
                store.updateOrder(order.id, if (done) "Aberto" else "Concluído")
                renderApp()
            }, LinearLayout.LayoutParams(-1, dp(48)))
            container.addView(card, cardParams())
        }
    }


    private fun parseOrderTimestamp(value: String): Long {
        val normalized = value.replace(Regex("\\.\\d+(?=(Z|[+-]\\d{2}:?\\d{2})$)"), "")
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
                isLenient = false
            }.parse(normalized)?.time ?: 0L
        }.getOrDefault(0L)
    }

    private fun orderWaitLabel(createdAtMillis: Long): String {
        if (createdAtMillis <= 0L) return "Tempo de espera indisponível"
        val minutes = ((System.currentTimeMillis() - createdAtMillis) / 60_000L).coerceAtLeast(0L)
        return when {
            minutes < 1L -> "Acabado de entrar"
            minutes < 60L -> "$minutes min em espera"
            else -> "${minutes / 60L} h ${minutes % 60L} min em espera"
        }
    }

    private fun renderKitchen(container: LinearLayout) {
        pageIntro(container, "Cozinha", "Fila de preparação por estado e tempo de espera.")
        addGap(container, 8)
        if (hasRemoteRestaurant()) {
            container.addView(secondaryButton("Atualizar fila do servidor") { refreshRemoteOperations() },
                LinearLayout.LayoutParams(-1, dp(48)))
        } else {
            container.addView(label("Modo de demonstração · os pedidos não são partilhados com outros dispositivos.", 11f, false, muted))
        }
        addGap(container, 12)

        if (hasRemoteRestaurant()) {
            val orders = remoteOrders()
            val active = (0 until orders.length())
                .mapNotNull { orders.optJSONObject(it) }
                .filter { it.optString("status", "Aberto") !in setOf("Concluído", "Cancelado") }
                .sortedWith(compareBy<JSONObject>(
                    { when (it.optString("status", "Aberto")) {
                        "Aberto" -> 0
                        "Em preparação" -> 1
                        "Pronto" -> 2
                        else -> 3
                    } },
                    { parseOrderTimestamp(it.optString("created_at")) }
                ))

            if (active.isEmpty()) {
                container.addView(emptyCard("A fila está vazia. Os novos pedidos aparecerão aqui depois de atualizares os dados."))
                return
            }
            container.addView(label("${active.size} pedido(s) ativo(s)", 12f, true, forestSoft))
            addGap(container, 8)
            for (order in active) {
                val status = order.optString("status", "Aberto")
                val createdAt = parseOrderTimestamp(order.optString("created_at"))
                val nextStatus = when (status) {
                    "Aberto" -> "Em preparação"
                    "Em preparação" -> "Pronto"
                    "Pronto" -> "Concluído"
                    else -> status
                }
                renderKitchenTicket(
                    container,
                    order.optInt("table_number", 1),
                    order.optString("item_name", "Artigo"),
                    order.optInt("quantity", 1).coerceIn(1, 99),
                    order.optString("service_course", "Principal"),
                    order.optString("notes").trim(),
                    status,
                    orderWaitLabel(createdAt)
                ) {
                    runBackend({
                        val token = backendToken()
                        SupabaseApi.updateOrder(token, order.optString("id"), nextStatus)
                        cacheRemoteOperations(token, backendRestaurantId())
                        "OK"
                    }) { renderApp() }
                }
            }
        } else {
            val active = store.getOrders()
                .filter { it.status !in setOf("Concluído", "Cancelado") }
                .sortedBy { it.createdAt }
            if (active.isEmpty()) {
                container.addView(emptyCard("A fila está vazia. Cria um pedido para testar o fluxo da cozinha."))
                return
            }
            container.addView(label("${active.size} pedido(s) ativo(s)", 12f, true, forestSoft))
            addGap(container, 8)
            active.forEach { order ->
                val nextStatus = when (order.status) {
                    "Aberto" -> "Em preparação"
                    "Em preparação" -> "Pronto"
                    "Pronto" -> "Concluído"
                    else -> order.status
                }
                renderKitchenTicket(
                    container,
                    order.table,
                    order.itemName,
                    order.quantity,
                    order.serviceCourse,
                    order.notes,
                    order.status,
                    orderWaitLabel(order.createdAt)
                ) {
                    store.updateOrder(order.id, nextStatus)
                    renderApp()
                }
            }
        }
    }

    private fun renderKitchenTicket(
        container: LinearLayout,
        tableNumber: Int,
        itemName: String,
        quantity: Int,
        serviceCourse: String,
        notes: String,
        status: String,
        waitLabel: String,
        onAdvance: () -> Unit
    ) {
        val card = makeCard(16)
        val statusColor = when (status) {
            "Pronto" -> green
            "Em preparação" -> forestSoft
            else -> copper
        }
        val statusBackground = when (status) {
            "Pronto" -> paleGreen
            "Em preparação" -> paleGreen
            else -> copperPale
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(label("MESA ${tableNumber.toString().padStart(2, '0')}", 12f, true, forestSoft),
            LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(label(status.uppercase(Locale("pt", "PT")), 10f, true, statusColor).apply {
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = rounded(statusBackground, 12)
        })
        card.addView(top)
        addGap(card, 8)
        card.addView(label((if (quantity > 1) "$quantity × " else "") + itemName, 19f, true, forestDeep))
        addGap(card, 4)
        card.addView(label(serviceCourse.uppercase(Locale("pt", "PT")), 11f, true, forestSoft))
        if (notes.isNotBlank()) {
            addGap(card, 8)
            card.addView(label("NOTAS PARA A COZINHA", 9.5f, true, muted))
            card.addView(label(notes, 14f, false, ink))
        }
        addGap(card, 8)
        card.addView(label(waitLabel, 11.5f, true, if (waitLabel.contains("min em espera") || waitLabel.contains("h ")) red else muted))
        addGap(card, 12)
        card.addView(primaryButton(when (status) {
            "Aberto" -> "Iniciar preparação"
            "Em preparação" -> "Marcar como pronto"
            "Pronto" -> "Concluir pedido"
            else -> "Atualizar pedido"
        }, onAdvance), LinearLayout.LayoutParams(-1, dp(48)))
        container.addView(card, cardParams())
    }

    private fun renderMenu(container: LinearLayout) {
        pageIntro(container, "Carta e custos", if (hasRemoteRestaurant())
            "Carta e custos privados, guardados no servidor."
        else "Artigos de exemplo para testar a gestão.")
        addGap(container, 7)
        if (hasRemoteRestaurant()) {
            container.addView(makeCard(13, copperPale).apply {
                addView(label("Carta do restaurante", 10f, true, forest))
                addGap(this, 4)
                addView(label("A carta é partilhada pela equipa. Os custos e margens só são apresentados na aplicação de gestão, não no cartão de cliente.", 12.5f, false, ink))
            }, cardParams())
            container.addView(secondaryButton("Atualizar do servidor") { refreshRemoteOperations() },
                LinearLayout.LayoutParams(-1, dp(48)))
            addGap(container, 6)
            container.addView(primaryButton("+  Adicionar artigo") { showAddMenuItemDialog() }, LinearLayout.LayoutParams(-1, dp(48)))
            addGap(container, 15)
            val items = remoteMenuItems()
            if (items.length() == 0) {
                container.addView(emptyCard("A carta do servidor está vazia. Adiciona os primeiros artigos e preços aprovados pelo restaurante."))
                return
            }
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val card = makeCard(15)
                card.addView(label(item.optString("name"), 17f, true, forestDeep))
                addGap(card, 3)
                card.addView(label(item.optString("category", "Prato principal"), 10.5f, true, forestSoft))
                val description = item.optString("description").trim()
                if (description.isNotBlank()) {
                    addGap(card, 5)
                    card.addView(label(description, 12.5f, false, ink))
                }
                val allergens = item.optString("allergens").trim()
                addGap(card, 5)
                card.addView(label(
                    if (allergens.isBlank()) "Alergénios: confirmar com a equipa."
                    else "Alergénios declarados: $allergens. Confirma com a equipa se tiveres uma alergia.",
                    11f, false, muted
                ))
                addGap(card, 8)
                val prices = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                val price = item.optDouble("price", 0.0)
                val cost = item.optDouble("cost", 0.0)
                prices.addView(priceColumn("PREÇO DE VENDA", money(price)), LinearLayout.LayoutParams(0, -2, 1f))
                prices.addView(priceColumn("CUSTO ESTIMADO", money(cost)), LinearLayout.LayoutParams(0, -2, 1f))
                card.addView(prices)
                val ratio = if (price > 0.0) cost / price * 100.0 else 0.0
                addGap(card, 9)
                card.addView(label("Custo do artigo: " + String.format(Locale("pt", "PT"), "%.1f%%", ratio) + " do preço de venda", 11.5f, false, muted))
                addGap(card, 9)
                card.addView(secondaryButton("Editar artigo") {
                    showEditMenuItemDialog(
                        item.optString("id"),
                        MenuEntry(
                            item.optString("name"),
                            item.optDouble("price", 0.0),
                            item.optDouble("cost", 0.0),
                            item.optString("category", "Prato principal"),
                            item.optString("description", ""),
                            item.optString("allergens", "")
                        )
                    )
                }, LinearLayout.LayoutParams(-1, dp(48)))
                addGap(card, 5)
                card.addView(secondaryButton("Arquivar artigo") {
                    AlertDialog.Builder(this)
                        .setTitle("Arquivar artigo?")
                        .setMessage("«" + item.optString("name") + "» deixa de aparecer na carta ativa. Os pedidos antigos mantêm o registo.")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Arquivar") { _, _ ->
                            runBackend({
                                val token = backendToken()
                                SupabaseApi.deactivateMenuItem(token, item.optString("id"))
                                cacheRemoteOperations(token, backendRestaurantId())
                                "OK"
                            }) { renderApp() }
                        }
                        .show()
                }, LinearLayout.LayoutParams(-1, dp(48)))
                container.addView(card, cardParams())
            }
            return
        }

        container.addView(makeCard(13, copperPale).apply {
            addView(label("Carta de demonstração", 10f, true, forest))
            addGap(this, 4)
            addView(label("Os nomes e valores abaixo são ilustrativos; não representam a carta real do restaurante.", 12.5f, false, ink))
        }, cardParams())
        container.addView(primaryButton("+  Adicionar artigo") { showAddMenuItemDialog() }, LinearLayout.LayoutParams(-1, dp(48)))
        addGap(container, 15)

        val items = store.getMenu()
        if (items.isEmpty()) {
            container.addView(emptyCard("A carta está vazia. Adiciona o primeiro artigo de demonstração."))
        }
        items.forEach { item ->
            val card = makeCard(15)
            card.addView(label(item.name, 17f, true, forestDeep))
            addGap(card, 3)
            card.addView(label(item.category, 10.5f, true, forestSoft))
            if (item.description.isNotBlank()) {
                addGap(card, 5)
                card.addView(label(item.description, 12.5f, false, ink))
            }
            addGap(card, 5)
            card.addView(label(
                if (item.allergens.isBlank()) "Alergénios: confirmar com a equipa."
                else "Alergénios declarados: " + item.allergens + ". Confirma com a equipa se tiveres uma alergia.",
                11f, false, muted
            ))
            addGap(card, 8)
            val prices = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            prices.addView(priceColumn("PREÇO DE VENDA", money(item.price)), LinearLayout.LayoutParams(0, -2, 1f))
            prices.addView(priceColumn("CUSTO ESTIMADO", money(item.cost)), LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(prices)
            val ratio = if (item.price > 0.0) item.cost / item.price * 100.0 else 0.0
            addGap(card, 9)
            card.addView(label("Custo do artigo: " + String.format(Locale("pt", "PT"), "%.1f%%", ratio) + " do preço de venda", 11.5f, false, muted))
            addGap(card, 9)
            card.addView(secondaryButton("Editar artigo") {
                showEditMenuItemDialog(null, item)
            }, LinearLayout.LayoutParams(-1, dp(48)))
            addGap(card, 5)
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
            }, LinearLayout.LayoutParams(-1, dp(48)))
            container.addView(card, cardParams())
        }
    }

    private fun renderPriceCalculator(container: LinearLayout) {
        pageIntro(container, "Preço Justo", "Uma estimativa matemática baseada no custo.")
        addGap(container, 5)
        container.addView(label("Não consulta preços concorrentes em tempo real e não substitui uma análise completa de custos.", 12.5f, false, muted))
        addGap(container, 15)

        val form = makeCard(17)
        form.addView(label("Custo por unidade (€)", 10f, true, forestSoft))
        val costField = inputField("Ex.: 3,20", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        form.addView(costField, fieldParams())
        addGap(form, 7)
        form.addView(label("Custo pretendido (% do preço)", 10f, true, forestSoft))
        val percentageField = inputField("Ex.: 30", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        percentageField.setText("30")
        form.addView(percentageField, fieldParams())
        addGap(form, 10)

        val result = label("Introduz os valores e calcula um preço indicativo.", 14f, false, ink).apply {
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(paper, 12)
        }
        // Never leave an old suggested price visible after the user edits the inputs.
        val inputChangeWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                result.text = "Valores alterados. Calcula novamente para atualizar o preço indicativo."
                result.setTextColor(ink)
            }

            override fun afterTextChanged(s: android.text.Editable?) = Unit
        }
        costField.addTextChangedListener(inputChangeWatcher)
        percentageField.addTextChangedListener(inputChangeWatcher)

        // Clear only the edited field's validation error as soon as the user corrects it.
        fun onCalculatorInputEdited(field: EditText) {
            field.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    field.error = null
                    // A result belongs to the exact input pair that produced it. Do not leave
                    // an old price visible after either input changes.
                    result.text = "Valores alterados. Calcula novamente para atualizar o preço indicativo."
                    result.setTextColor(ink)
                }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }
        onCalculatorInputEdited(costField)
        onCalculatorInputEdited(percentageField)

        form.addView(primaryButton("Calcular preço sugerido") {
            val calculation = try {
                PriceCalculator.calculate(
                    costField.text.toString(),
                    percentageField.text.toString()
                )
            } catch (error: PriceCalculationException) {
                result.text = "Corrige os valores assinalados para atualizar o preço indicativo."
                result.setTextColor(red)
                when (error.field) {
                    PriceInputField.COST -> costField.error = error.message
                    PriceInputField.TARGET_COST_PERCENT -> percentageField.error = error.message
                }
                return@primaryButton
            }

            result.text = "PREÇO INDICATIVO\n" + money(calculation.suggestedPrice) +
                "\n\nCusto por unidade: " + money(calculation.cost) +
                "\nCusto sobre o preço final: " + formatPercentage(calculation.actualCostPercent) +
                "\nMargem bruta estimada: " + money(calculation.grossProfit) +
                "\nMargem bruta: " + formatPercentage(calculation.grossMarginPercent)
            result.setTextColor(forest)
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

    private fun displayRestaurantName(): String =
        backendPrefs.getString("restaurant_name", null)?.trim()?.takeIf { it.isNotEmpty() }
            ?: "StudioMesa"

    private fun displayCustomerRestaurantName(): String =
        backendPrefs.getString("member_restaurant_name", null)?.trim()?.takeIf { it.isNotEmpty() }
            ?: "StudioMesa"

    private fun refreshSessionIfNeeded() {
        val accessToken = backendToken()
        val refreshToken = secureSession.refreshToken()
        if (accessToken.isBlank() || refreshToken.isBlank()) return

        val expiresAt = SupabaseApi.tokenExpiresAtMillis(accessToken)
        if (expiresAt > System.currentTimeMillis() + 120_000L) return

        val session = SupabaseApi.refreshSession(refreshToken)
        secureSession.save(session.accessToken, session.refreshToken)
        backendPrefs.edit()
            .putString("user_id", session.userId)
            .putString("user_email", session.email)
            .apply()

        if (isCustomerApp) restoreCustomerMembership(session.accessToken) else restoreOwnedRestaurant(session.accessToken)
    }

    private fun restoreOwnedRestaurant(token: String) {
        if (isCustomerApp || token.isBlank()) return

        try {
            val restaurant = SupabaseApi.getMyRestaurant(token)
            val editor = backendPrefs.edit()
                .remove("restaurant_id")
                .remove("restaurant_name")
                .remove("join_code")
                .remove("staff_join_code")
                .remove("restaurant_role")
            if (restaurant != null) {
                val restaurantId = restaurant.optString("restaurant_id")
                editor.putString("restaurant_id", restaurantId)
                    .putString("restaurant_name", restaurant.optString("restaurant_name"))
                    .putString("join_code", restaurant.optString("join_code"))
                    .putString("staff_join_code", restaurant.optString("staff_code"))
                    .putString("restaurant_role", restaurant.optString("user_role", "staff"))
            }
            editor.apply()
            if (restaurant != null) {
                cacheRemoteOperations(token, restaurant.optString("restaurant_id"))
            } else {
                // The server confirmed this account no longer belongs to a restaurant.
                // Remove data that would otherwise remain on this device after revocation.
                backendPrefs.edit()
                    .remove("remote_members_json")
                    .remove("remote_tables_json")
                    .remove("remote_menu_json")
                    .remove("remote_orders_json")
                    .remove("remote_inventory_json")
                    .remove("inventory_loaded")
                    .remove("remote_reservations_json")
                    .remove("reservations_loaded")
                    .apply()
                reservationCache = JSONArray()
                reservationsLoaded = false
            }
        } catch (_: Exception) {
            // Keep authentication successful if restoring cloud data temporarily fails.
        }
    }



    private fun hasRemoteRestaurant(): Boolean =
        backendToken().isNotBlank() && backendRestaurantId().isNotBlank()

    private fun savedRemoteArray(key: String): org.json.JSONArray {
        return try {
            org.json.JSONArray(backendPrefs.getString(key, "[]") ?: "[]")
        } catch (_: Exception) {
            org.json.JSONArray()
        }
    }

    private fun remoteTables(): org.json.JSONArray = savedRemoteArray("remote_tables_json")
    private fun remoteMenuItems(): org.json.JSONArray = savedRemoteArray("remote_menu_json")
    private fun remoteOrders(): org.json.JSONArray = savedRemoteArray("remote_orders_json")

    private fun remoteMenuEntries(): MutableList<MenuEntry> {
        val result = mutableListOf<MenuEntry>()
        val array = remoteMenuItems()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val name = item.optString("name").trim()
            if (name.isNotBlank()) {
                result.add(MenuEntry(
                    name,
                    item.optDouble("price", 0.0).coerceAtLeast(0.0),
                    item.optDouble("cost", 0.0).coerceAtLeast(0.0),
                    item.optString("category", "Prato principal"),
                    item.optString("description", ""),
                    item.optString("allergens", "")
                ))
            }
        }
        return result
    }

    private fun cacheRemoteOperations(token: String, restaurantId: String) {
        if (token.isBlank() || restaurantId.isBlank()) return
        val tables = SupabaseApi.listTables(token, restaurantId)
        val menu = SupabaseApi.listMenuItems(token, restaurantId)
        val orders = SupabaseApi.listOrders(token, restaurantId)
        backendPrefs.edit()
            .putString("remote_tables_json", tables.toString())
            .putString("remote_menu_json", menu.toString())
            .putString("remote_orders_json", orders.toString())
            .apply()
    }

    private fun refreshRemoteOperations() {
        val restaurantId = backendRestaurantId()
        if (!hasRemoteRestaurant() || restaurantId.isBlank()) {
            AlertDialog.Builder(this)
                .setMessage("Inicia sessão e configura o restaurante na secção Conta para sincronizar os dados.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        runBackend({
            cacheRemoteOperations(backendToken(), restaurantId)
            "OK"
        }) {
            renderApp()
        }
    }

    private fun restoreCustomerMembership(token: String) {
        if (!isCustomerApp || token.isBlank()) return
        try {
            val member = SupabaseApi.getMyLoyaltyMembership(token)
            if (member == null) {
                backendPrefs.edit()
                    .remove("member_id")
                    .remove("member_code")
                    .remove("member_points")
                    .remove("member_restaurant_name")
                    .putString("customer_menu_json", "[]")
                    .apply()
                return
            }

            val memberId = member.optString("member_id")
            val menu = if (memberId.isNotBlank()) SupabaseApi.listCustomerMenu(token, memberId) else org.json.JSONArray()
            backendPrefs.edit()
                .putString("member_id", memberId)
                .putString("member_code", member.optString("member_code"))
                .putInt("member_points", member.optInt("points", 0))
                .putString("member_restaurant_name", member.optString("restaurant_name"))
                .putString("customer_menu_json", menu.toString())
                .apply()
            val displayName = member.optString("display_name").trim()
            if (displayName.isNotBlank()) store.saveCustomerProfileName(displayName)
        } catch (_: Exception) {
            // Do not discard a valid login if the membership is temporarily unavailable.
        }
    }


    /**
     * A freshly established session must not inherit cached operational data.
     * Clear it even if the account ID matches: restaurant membership or permissions
     * may have changed while signed out, and the server must repopulate authoritative data.
     */
    private fun prepareForAuthenticatedUser(newUserId: String) {
        if (newUserId.isBlank()) {
            throw IllegalStateException("O servidor não devolveu um identificador de conta válido.")
        }

        clearEconomicManagerState()
        store.clearLocalDemoData()
        backendPrefs.edit()
            .putBoolean("local_demo_data_isolated_v1", true)
            .remove("user_id").remove("user_email")
            .remove("access_token").remove("refresh_token")
            .remove("restaurant_id").remove("restaurant_name").remove("join_code")
            .remove("staff_join_code").remove("restaurant_role")
            .remove("member_id").remove("member_code").remove("member_points")
            .remove("member_restaurant_name")
            .remove("remote_members_json").remove("remote_tables_json")
            .remove("remote_menu_json").remove("remote_orders_json")
            .remove("customer_menu_json")
            .remove("remote_inventory_json").remove("inventory_loaded")
            .remove("remote_reservations_json").remove("reservations_loaded")
            .apply()

        reservationCache = JSONArray()
        reservationsLoaded = false
    }

    private fun backendToken(): String = secureSession.accessToken()
    private fun backendRestaurantId(): String = backendPrefs.getString("restaurant_id", "") ?: ""
    private fun backendMemberId(): String = backendPrefs.getString("member_id", "") ?: ""

    private fun friendlyBackendError(rawMessage: String?): String {
        val message = rawMessage?.trim().orEmpty()
        val normalized = message.lowercase(Locale.ROOT)
        return when {
            normalized.contains("invalid login credentials") ->
                "Não foi possível iniciar sessão com estes dados. Se é a primeira utilização, volta atrás e escolhe «Criar conta». Se já tens conta, confirma o email e verifica o endereço e a palavra-passe."
            normalized.contains("email not confirmed") || normalized.contains("email_not_confirmed") ->
                "O email ainda não foi confirmado. Abre a mensagem de confirmação enviada pelo Supabase (verifica também Spam) e, depois, escolhe «Iniciar sessão»."
            normalized.contains("user already registered") || normalized.contains("already been registered") ->
                "Este email já tem uma conta. Volta atrás e escolhe «Iniciar sessão» em vez de «Criar conta»."
            normalized.contains("email rate limit exceeded") || normalized.contains("rate limit") ->
                "O serviço limitou temporariamente os pedidos. Aguarda um pouco antes de tentares novamente."
            normalized.contains("unable to resolve host") || normalized.contains("failed to connect") ||
                normalized.contains("network is unreachable") || normalized.contains("timeout") ->
                "Não foi possível ligar ao servidor. Confirma que o telemóvel tem internet e tenta novamente."
            else -> message.take(500).ifBlank {
                "Erro de ligação. Confirma a internet e tenta novamente."
            }
        }
    }

    private fun runBackend(
        task: () -> String,
        refreshSession: Boolean = true,
        onSuccess: (String) -> Unit
    ) {
        val progress = AlertDialog.Builder(this)
            .setTitle("A ligar ao StudioMesa")
            .setMessage("A validar a conta no servidor. Aguarda, por favor…")
            .setCancelable(false)
            .create()
        progress.show()
        Thread {
            try {
                if (refreshSession) refreshSessionIfNeeded()
                val result = task()
                runOnUiThread {
                    if (!isFinishing) {
                        progress.dismiss()
                        onSuccess(result)
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!isFinishing) {
                        progress.dismiss()
                        AlertDialog.Builder(this)
                            .setTitle("Não foi possível concluir")
                            .setMessage(friendlyBackendError(error.message))
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }.start()
    }

    private fun showSetNewPasswordDialog() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(4))
        }
        val passwordField = inputField(
            "Nova palavra-passe (mínimo 8 caracteres)",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        val repeatField = inputField(
            "Confirmar nova palavra-passe",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        form.addView(label("Nova palavra-passe", 10f, true, forestSoft))
        form.addView(passwordField, fieldParams())
        addGap(form, 4)
        form.addView(label("Confirmar palavra-passe", 10f, true, forestSoft))
        form.addView(repeatField, fieldParams())

        val dialog = AlertDialog.Builder(this)
            .setTitle("Definir nova palavra-passe")
            .setMessage("Escolhe uma palavra-passe nova e não a partilhes.")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar palavra-passe", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val password = passwordField.text.toString()
                val repeated = repeatField.text.toString()
                when {
                    password.length < 8 -> passwordField.error = "Usa pelo menos 8 caracteres"
                    password != repeated -> repeatField.error = "As palavras-passe não coincidem"
                    backendToken().isBlank() -> {
                        dialog.dismiss()
                        AlertDialog.Builder(this)
                            .setTitle("Ligação expirada")
                            .setMessage("Abre novamente a ligação de recuperação mais recente que recebeste por email.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    else -> runBackend({
                        SupabaseApi.updatePassword(backendToken(), password)
                        "UPDATED"
                    }) {
                        dialog.dismiss()
                        AlertDialog.Builder(this)
                            .setTitle("Palavra-passe atualizada")
                            .setMessage("A palavra-passe foi alterada. Guarda-a num gestor de palavras-passe e não a partilhes.")
                            .setPositiveButton("Continuar") { _, _ ->
                                if (isCustomerApp) renderCustomerApp() else renderApp()
                            }
                            .show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showAuthChooser(afterAuth: () -> Unit) {
        // Use explicit buttons in a custom view. Combining setMessage and setItems
        // can render inconsistently across Android dialog implementations.
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), dp(6))
        }
        form.addView(label(
            "Primeira utilização? Escolhe «Criar conta». Se já tens uma conta, escolhe «Iniciar sessão».",
            13f, false, muted
        ))
        addGap(form, 14)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Conta StudioMesa")
            .setView(form)
            .setNegativeButton("Fechar", null)
            .create()

        form.addView(primaryButton("Criar conta") {
            dialog.dismiss()
            showCredentialsDialog(true, afterAuth)
        }, LinearLayout.LayoutParams(-1, dp(48)))
        addGap(form, 8)
        form.addView(secondaryButton("Iniciar sessão") {
            dialog.dismiss()
            showCredentialsDialog(false, afterAuth)
        }, LinearLayout.LayoutParams(-1, dp(48)))
        addGap(form, 14)
        form.addView(label("Ou continuar com uma conta externa:", 11.5f, true, muted))
        addGap(form, 6)
        form.addView(secondaryButton("Continuar com Google") {
            dialog.dismiss()
            startOAuth("google")
        }, LinearLayout.LayoutParams(-1, dp(48)))
        addGap(form, 6)
        form.addView(secondaryButton("Continuar com Microsoft (Outlook)") {
            dialog.dismiss()
            startOAuth("azure")
        }, LinearLayout.LayoutParams(-1, dp(48)))
        addGap(form, 7)
        form.addView(label(
            "Google e Outlook só podem ser usados depois de os fornecedores estarem ativados no servidor.",
            10.5f, false, muted
        ))

        dialog.show()
    }

    private fun createPkceVerifier(): String {
        val random = ByteArray(32)
        java.security.SecureRandom().nextBytes(random)
        return android.util.Base64.encodeToString(
            random,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
    }

    private fun createPkceChallenge(verifier: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return android.util.Base64.encodeToString(
            digest,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
    }

    private fun startOAuth(provider: String) {
        runBackend({
            if (!SupabaseApi.isOAuthProviderEnabled(provider)) {
                throw IllegalStateException(
                    if (provider == "google") {
                        "O início de sessão com Google ainda não está ativado no Supabase. É necessário configurar as credenciais OAuth do Google no servidor."
                    } else {
                        "O início de sessão com Microsoft/Outlook ainda não está ativado no Supabase. É necessário configurar as credenciais OAuth da Microsoft no servidor."
                    }
                )
            }
            val verifier = createPkceVerifier()
            val challenge = createPkceChallenge(verifier)
            secureSession.savePendingCodeVerifier(verifier, "oauth")
            SupabaseApi.oauthUrl(provider, challenge)
        }, refreshSession = false) { authUrl ->
            try {
                startActivity(android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(authUrl)
                ))
            } catch (_: Exception) {
                runCatching { secureSession.clearPendingCodeVerifier() }
                AlertDialog.Builder(this)
                    .setTitle("Não foi possível abrir o início de sessão")
                    .setMessage("Confirma que tens um navegador instalado e tenta novamente.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun showCredentialsDialog(createAccount: Boolean, afterAuth: () -> Unit) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
        }
        val emailField = inputField("Email", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val passwordField = inputField("Palavra-passe (mínimo 8 caracteres)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        form.addView(label("Email", 10f, true, forestSoft))
        form.addView(emailField, fieldParams())
        addGap(form, 4)
        form.addView(label("Palavra-passe", 10f, true, forestSoft))
        form.addView(passwordField, fieldParams())
        // Keep the instructions inside the custom form so Android always
        // shows the email and password fields together with the message.
        form.addView(label(
            "Usa um email a que tenhas acesso e uma palavra-passe com pelo menos 8 caracteres. Não partilhes estes dados.",
            12.5f, false, muted
        ), 0)
        addGap(form, 10)
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (createAccount) "Criar conta" else "Iniciar sessão")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton(if (createAccount) "Criar conta" else "Entrar", null)
            .create()
        if (!createAccount) {
            addGap(form, 2)
            form.addView(secondaryButton("Esqueci-me da palavra-passe") {
                val email = emailField.text.toString().trim()
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    emailField.error = "Introduz primeiro o email da tua conta"
                } else {
                    runBackend({
                        val verifier = createPkceVerifier()
                        val challenge = createPkceChallenge(verifier)
                        secureSession.savePendingCodeVerifier(verifier, "recovery")
                        SupabaseApi.requestPasswordRecovery(email, challenge)
                        "SENT"
                    }, refreshSession = false) {
                        dialog.dismiss()
                        AlertDialog.Builder(this)
                            .setTitle("Verifica o teu email")
                            .setMessage("Se existir uma conta com este endereço, será enviado um email para recuperar o acesso. Abre a ligação no mesmo telemóvel para definires uma nova palavra-passe. Verifica também Spam.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }, LinearLayout.LayoutParams(-1, dp(48)))
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val email = emailField.text.toString().trim()
                val password = passwordField.text.toString()
                when {
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                        emailField.error = "Introduz um email válido"
                    password.length < 8 ->
                        passwordField.error = "Usa pelo menos 8 caracteres"
                    else -> {
                        runBackend({
                            if (createAccount) {
                                val verifier = createPkceVerifier()
                                val challenge = createPkceChallenge(verifier)
                                secureSession.savePendingCodeVerifier(verifier, "signup")
                                val result = SupabaseApi.signUp(email, password, challenge)
                                val token = result.optString("access_token")
                                val user = result.optJSONObject("user")
                                when {
                                    token.isNotBlank() && user != null -> {
                                        secureSession.save(token, result.optString("refresh_token"))
                                        prepareForAuthenticatedUser(user.optString("id"))
                                        secureSession.clearPendingCodeVerifier()
                                        backendPrefs.edit()
                                            .putString("user_id", user.optString("id"))
                                            .putString("user_email", email)
                                            .apply()
                                        if (isCustomerApp) restoreCustomerMembership(token) else restoreOwnedRestaurant(token)
                                        "SIGNED_IN"
                                    }
                                    user != null -> {
                                        backendPrefs.edit()
                                            .putString("user_id", user.optString("id"))
                                            .putString("user_email", email)
                                            .apply()
                                        "CONFIRM_EMAIL"
                                    }
                                    else -> throw IllegalStateException(
                                        "O servidor não confirmou a criação da conta. Tenta novamente; se o erro continuar, regista a mensagem que aparecer."
                                    )
                                }
                            } else {
                                val session = SupabaseApi.signIn(email, password)
                                secureSession.save(session.accessToken, session.refreshToken)
                                prepareForAuthenticatedUser(session.userId)
                                backendPrefs.edit()
                                    .putString("user_id", session.userId)
                                    .putString("user_email", session.email)
                                    .apply()
                                if (isCustomerApp) restoreCustomerMembership(session.accessToken) else restoreOwnedRestaurant(session.accessToken)
                                "SIGNED_IN"
                            }
                        }, refreshSession = false) { result ->
                            dialog.dismiss()
                            if (result == "CONFIRM_EMAIL") {
                                AlertDialog.Builder(this)
                                    .setTitle("Falta confirmar o email")
                                    .setMessage("A conta foi criada, mas ainda não foi iniciada sessão. Abre o email de confirmação no mesmo telemóvel e toca na ligação; deverá abrir o StudioMesa. Verifica também Spam/Promoções. Se a ligação não concluir a sessão, pede uma nova. Não cries outra conta com o mesmo email.")
                                    .setPositiveButton("OK", null)
                                    .show()
                            } else {
                                afterAuth()
                            }
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun generateSecureCode(length: Int): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val secureRandom = java.security.SecureRandom()
        return buildString(length) {
            repeat(length) { append(alphabet[secureRandom.nextInt(alphabet.length)]) }
        }
    }

    private fun showCreateRestaurantDialog() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
        }
        val nameField = inputField("Nome do restaurante", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        val code = generateSecureCode(12)
        form.addView(label("Nome", 10f, true, forestSoft))
        form.addView(nameField, fieldParams())
        addGap(form, 5)
        form.addView(label("Código de adesão gerado", 10f, true, forestSoft))
        form.addView(label(code, 23f, true, forest).apply { setPadding(dp(4), dp(10), dp(4), dp(10)) })
        form.addView(label("Guarda este código e partilha-o apenas com os clientes que devem aderir ao programa.", 12f, false, muted))
        val dialog = AlertDialog.Builder(this)
            .setTitle("Configurar restaurante")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Criar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameField.text.toString().trim()
                if (name.length !in 2..100) {
                    nameField.error = "Usa entre 2 e 100 caracteres"
                } else {
                    runBackend({
                        val token = backendToken()
                        val restaurantId = SupabaseApi.createRestaurant(token, name, code)
                        restoreOwnedRestaurant(token)
                        restaurantId
                    }) { restaurantId ->
                        dialog.dismiss()
                        // A successful create_restaurant RPC creates the current user as owner.
                        // Keep owner controls available even if the follow-up refresh briefly fails.
                        backendPrefs.edit().putString("restaurant_id", restaurantId)
                            .putString("restaurant_name", name).putString("join_code", code)
                            .putString("restaurant_role", "owner").apply()
                        renderApp()
                        AlertDialog.Builder(this)
                            .setTitle("Restaurante criado")
                            .setMessage("Código de adesão: $code\n\nOs clientes podem usar este código na aplicação de cliente depois de criarem conta e iniciarem sessão.")
                            .setPositiveButton("OK", null).show()
                    }
                }
            }
        }
        dialog.show()
    }


    private fun showRestaurantTeam() {
        if (backendToken().isBlank() || (backendPrefs.getString("restaurant_role", "") ?: "") != "owner") {
            AlertDialog.Builder(this)
                .setTitle("Acesso reservado")
                .setMessage("Só o proprietário do restaurante pode consultar ou remover membros da equipa.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        runBackend({
            SupabaseApi.listRestaurantTeam(backendToken()).toString()
        }) { raw ->
            val members = JSONArray(raw)
            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(8), dp(14), dp(8))
            }
            if (members.length() == 0) {
                content.addView(emptyCard("O servidor não devolveu membros para este restaurante."))
            }

            lateinit var teamDialog: AlertDialog
            for (index in 0 until members.length()) {
                val member = members.optJSONObject(index) ?: continue
                val userId = member.optString("member_user_id")
                val email = member.optString("member_email").trim().ifBlank { "Conta " + userId.take(8) }
                val role = member.optString("member_role", "staff")
                val joined = member.optString("joined_at")
                val joinedText = runCatching {
                    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                    val formatter = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "PT"))
                    formatter.format(parser.parse(joined) ?: Date())
                }.getOrElse { "" }

                val card = makeCard(12)
                card.addView(label(email, 14f, true, forestDeep))
                addGap(card, 3)
                card.addView(label(
                    (if (role == "owner") "Proprietário · acesso protegido" else "Funcionário · acesso operacional") +
                        if (joinedText.isNotBlank()) " · desde $joinedText" else "",
                    10.5f, false, muted
                ))
                if (role == "staff") {
                    addGap(card, 8)
                    card.addView(secondaryButton("Remover acesso") {
                        AlertDialog.Builder(this)
                            .setTitle("Remover acesso à equipa?")
                            .setMessage("A conta $email deixará de pertencer a este restaurante. O utilizador poderá voltar a aderir se receber um novo código de acesso.")
                            .setNegativeButton("Cancelar", null)
                            .setPositiveButton("Remover") { _, _ ->
                                runBackend({
                                    val token = backendToken()
                                    SupabaseApi.removeRestaurantStaff(token, userId)
                                    // The server rotates the shared code when access is revoked.
                                    backendPrefs.edit().remove("staff_join_code").apply()
                                    runCatching {
                                        val latestRestaurant = SupabaseApi.getMyRestaurant(token)
                                        if (latestRestaurant != null) {
                                            backendPrefs.edit()
                                                .putString("staff_join_code", latestRestaurant.optString("staff_code"))
                                                .apply()
                                        }
                                    }
                                    "OK"
                                }) {
                                    teamDialog.dismiss()
                                    AlertDialog.Builder(this)
                                        .setTitle("Acesso removido")
                                        .setMessage("O acesso foi revogado e o código antigo da equipa deixou de ser válido. Usa «Ver código de acesso à equipa» para obter o novo código antes de o partilhares com funcionários autorizados.")
                                        .setPositiveButton("OK") { _, _ -> showRestaurantTeam() }
                                        .show()
                                }
                            }
                            .show()
                    }, LinearLayout.LayoutParams(-1, dp(48)))
                }
                content.addView(card, cardParams())
            }

            val scroll = ScrollView(this).apply { addView(content) }
            teamDialog = AlertDialog.Builder(this)
                .setTitle("Membros da equipa")
                .setView(scroll)
                .setPositiveButton("Fechar", null)
                .create()
            teamDialog.show()
            teamDialog.window?.setLayout(-1, dp(560))
        }
    }

    private fun showStaffJoinCode() {
        val role = backendPrefs.getString("restaurant_role", "") ?: ""
        if (role != "owner") {
            AlertDialog.Builder(this)
                .setTitle("Código não disponível")
                .setMessage("Só o proprietário pode consultar o código de acesso à equipa.")
                .setPositiveButton("OK", null).show()
            return
        }
        // Always fetch the current code. A staff revocation rotates it on the server.
        runBackend({
            val restaurant = SupabaseApi.getMyRestaurant(backendToken())
                ?: throw IllegalStateException("Não foi possível obter os dados do restaurante.")
            val code = restaurant.optString("staff_code").trim()
            if (code.isBlank()) throw IllegalStateException("O servidor não devolveu um código válido.")
            backendPrefs.edit().putString("staff_join_code", code).apply()
            code
        }) { code ->
            AlertDialog.Builder(this)
                .setTitle("Código de acesso à equipa")
                .setMessage("Código: $code\n\nPartilha-o apenas com funcionários autorizados. Cada funcionário deve criar a sua própria conta no StudioMesa e escolher «Aderir à equipa de um restaurante».")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun confirmCodeRotation(staffCode: Boolean) {
        val title = if (staffCode) "Renovar código da equipa?" else "Renovar código dos clientes?"
        val message = if (staffCode) {
            "O código atual deixa de aceitar novas adesões da equipa. Os funcionários já associados mantêm o acesso."
        } else {
            "O código atual deixa de aceitar novas adesões de clientes. Os cartões já associados mantêm os pontos."
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Gerar novo código", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                runBackend({
                    val token = backendToken()
                    val code = if (staffCode) {
                        SupabaseApi.rotateStaffJoinCode(token)
                    } else {
                        SupabaseApi.rotateCustomerJoinCode(token)
                    }
                    val key = if (staffCode) "staff_join_code" else "join_code"
                    backendPrefs.edit().putString(key, code).apply()
                    code
                }) { code ->
                    dialog.dismiss()
                    renderApp()
                    AlertDialog.Builder(this)
                        .setTitle("Novo código gerado")
                        .setMessage((if (staffCode) "Código da equipa: " else "Código dos clientes: ") + code +
                            "\n\nGuarda-o e partilha-o apenas com as pessoas autorizadas.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
        dialog.show()
    }

    private fun showJoinStaffDialog() {
        if (backendToken().isBlank()) {
            showAuthChooser { renderApp() }
            return
        }
        val field = inputField("Código de acesso à equipa", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
        }
        form.addView(label("Código fornecido pelo proprietário", 10f, true, forestSoft))
        form.addView(field, fieldParams())
        val dialog = AlertDialog.Builder(this)
            .setTitle("Aderir à equipa")
            .setMessage("Usa o teu próprio email e a tua própria conta. Nunca partilhes a palavra-passe do proprietário.")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Aderir", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = field.text.toString().trim().uppercase(Locale.ROOT)
                if (code.length !in 6..16) {
                    field.error = "Introduz um código válido"
                } else {
                    runBackend({
                        val token = backendToken()
                        SupabaseApi.joinRestaurantStaff(token, code)
                        restoreOwnedRestaurant(token)
                        "OK"
                    }) {
                        dialog.dismiss()
                        activePage = "Conta"
                        renderApp()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun renderBackendAccount(container: LinearLayout) {
        pageIntro(container, "Conta e sincronização", "Conta, restaurante e acesso da equipa.")
        addGap(container, 12)
        val token = backendToken()
        val card = makeCard(17)
        if (token.isBlank()) {
            card.addView(label("Ainda sem sessão", 10f, true, forestSoft))
            addGap(card, 6)
            card.addView(label("Cria uma conta ou inicia sessão para configurar um restaurante ou aderir à equipa de um restaurante existente.", 13f, false, ink))
            addGap(card, 12)
            card.addView(primaryButton("Criar conta / iniciar sessão") { showAuthChooser { renderApp() } },
                LinearLayout.LayoutParams(-1, dp(48)))
        } else if (backendRestaurantId().isBlank()) {
            card.addView(label("Sessão iniciada", 10f, true, green))
            addGap(card, 5)
            card.addView(label(backendPrefs.getString("user_email", "") ?: "", 13f, false, muted))
            addGap(card, 12)
            card.addView(primaryButton("Configurar novo restaurante") { showCreateRestaurantDialog() },
                LinearLayout.LayoutParams(-1, dp(48)))
            addGap(card, 7)
            card.addView(secondaryButton("Aderir à equipa de um restaurante") { showJoinStaffDialog() },
                LinearLayout.LayoutParams(-1, dp(48)))
        } else {
            val restaurantName = backendPrefs.getString("restaurant_name", "Restaurante") ?: "Restaurante"
            val role = backendPrefs.getString("restaurant_role", "staff") ?: "staff"
            card.addView(label(if (role == "owner") "CONTA DO PROPRIETÁRIO" else "ACESSO DE EQUIPA", 10f, true, green))
            addGap(card, 6)
            card.addView(label(restaurantName, 20f, true, forestDeep))
            addGap(card, 5)
            if (role == "owner") {
                card.addView(label("Código de adesão dos clientes", 11f, false, muted))
                card.addView(label(backendPrefs.getString("join_code", "") ?: "", 23f, true, forest))
                addGap(card, 8)
                card.addView(label("Partilha este código apenas com clientes que devem aderir ao programa de fidelização.", 12f, false, ink))
                addGap(card, 8)
                card.addView(secondaryButton("Renovar código de adesão dos clientes") { confirmCodeRotation(false) },
                    LinearLayout.LayoutParams(-1, dp(48)))
                addGap(card, 5)
                card.addView(secondaryButton("Ver código de acesso à equipa") { showStaffJoinCode() },
                    LinearLayout.LayoutParams(-1, dp(48)))
                addGap(card, 5)
                card.addView(secondaryButton("Gerir membros e acessos da equipa") { showRestaurantTeam() },
                    LinearLayout.LayoutParams(-1, dp(48)))
                addGap(card, 5)
                card.addView(secondaryButton("Renovar código da equipa") { confirmCodeRotation(true) },
                    LinearLayout.LayoutParams(-1, dp(48)))
            } else {
                card.addView(label("Esta conta está associada à equipa deste restaurante. As alterações operacionais são guardadas no servidor.", 12f, false, ink))
            }
            addGap(card, 12)
            card.addView(primaryButton("Atualizar lista de clientes") { refreshRemoteCustomers() },
                LinearLayout.LayoutParams(-1, dp(48)))
        }
        container.addView(card, cardParams())
        addGap(container, 3)
        if (token.isNotBlank()) {
            container.addView(secondaryButton("Eliminar conta permanentemente") {
                showDeleteAccountConfirmation()
            }, LinearLayout.LayoutParams(-1, dp(48)))
            addGap(container, 7)
        }
        