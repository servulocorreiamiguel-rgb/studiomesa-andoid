package pt.studiomesa.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
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
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : Activity() {
    private data class IngredientFields(
        val container: LinearLayout,
        val name: EditText,
        val quantity: EditText,
        val unitCost: EditText
    )

    private lateinit var store: RestaurantStore
    private lateinit var appRoot: LinearLayout
    private var activePage = "Início"
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
    private val red = Color.rgb(169, 89, 78)
    private val green = Color.rgb(84, 123, 98)

    private val planTitle: String
        get() = when {
            flavor.contains("essencial") -> "ESSENCIAL"
            flavor.contains("premium") -> "PREMIUM"
            else -> "PROFISSIONAL"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // One-time cleanup for reservation contact data stored by older demo builds.
        // Current reservations stay in memory; this removes only legacy local preference keys,
        // not reservations stored on the server. The marker and removals are committed together.
        if (!backendPrefs.getBoolean("legacy_reservation_cache_removed_v1", false)) {
            backendPrefs.edit()
                .remove("remote_reservations_json")
                .remove("reservations_loaded")
                .putBoolean("legacy_reservation_cache_removed_v1", true)
                .commit()
        }
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

        // Never render account-specific cached data until the server has confirmed
        // this session and its current restaurant/customer membership.
        if (isAuthCallbackIntent(intent)) {
            clearCachedTenantState()
            renderSessionVerificationScreen(isVerifying = true)
            handleAuthCallback(intent)
        } else if (hasStoredSession()) {
            verifyExistingSessionAndRender()
        } else {
            clearCachedTenantState()
            backendPrefs.edit()
                .remove("user_id")
                .remove("user_email")
                .remove("access_token")
                .remove("refresh_token")
                .apply()
            renderApp()
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isAuthCallbackIntent(intent)) {
            clearCachedTenantState()
            renderSessionVerificationScreen(isVerifying = true)
            handleAuthCallback(intent)
        } else if (hasStoredSession()) {
            verifyExistingSessionAndRender()
        } else {
            clearCachedTenantState()
            renderApp()
        }
    }

    private fun parseAuthParams(raw: String?): Map<String, String> =
        AuthCallbackParser.parse(raw)

    private fun isAuthCallbackIntent(callbackIntent: android.content.Intent?): Boolean {
        val uri = callbackIntent?.data ?: return false
        return uri.scheme == "studiomesa" && uri.host == "auth-callback"
    }

    private fun hasStoredSession(): Boolean =
        backendToken().isNotBlank() || secureSession.refreshToken().isNotBlank()

    private fun recoverAfterAuthCallbackFailure() {
        if (hasStoredSession()) {
            verifyExistingSessionAndRender()
        } else {
            clearCachedTenantState()
            backendPrefs.edit().remove("user_id").remove("user_email").apply()
            renderApp()
        }
    }


    private fun handleAuthCallback(callbackIntent: android.content.Intent?) {
        val uri = callbackIntent?.data ?: return
        if (uri.scheme != "studiomesa" || uri.host != "auth-callback") return
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
                .setPositiveButton("OK") { _, _ -> recoverAfterAuthCallbackFailure() }
                .show()
            return
        }

        // Accept only a one-time PKCE code, never bearer tokens supplied in a deep link.
        val authCode = query["code"].orEmpty()
        val pendingFlow = secureSession.pendingCodeFlowType()
        val codeVerifier = secureSession.pendingCodeVerifier()
        if (authCode.isBlank() || codeVerifier.isBlank() ||
            pendingFlow !in setOf("oauth", "recovery", "signup")) {
            AlertDialog.Builder(this)
                .setTitle("Ligação de autenticação inválida")
                .setMessage("Não foi recebida uma ligação protegida válida. Pede uma nova ligação e abre-a neste telemóvel, com a aplicação instalada.")
                .setPositiveButton("OK") { _, _ -> recoverAfterAuthCallbackFailure() }
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
            val restored = if (isCustomerApp) {
                restoreCustomerMembership(session.accessToken)
            } else {
                restoreOwnedRestaurant(session.accessToken)
            }
            if (!restored) {
                throw IllegalStateException("Não foi possível confirmar o acesso aos dados da conta. Verifica a ligação e tenta novamente.")
            }
            "SIGNED_IN"
        }, refreshSession = false, onError = { error ->
            AlertDialog.Builder(this)
                .setTitle("Não foi possível concluir a autenticação")
                .setMessage(friendlyBackendError(error.message))
                .setPositiveButton("OK") { _, _ -> recoverAfterAuthCallbackFailure() }
                .show()
        }) {
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
        val pages = mutableListOf("Início", "Mesas", "Pedidos", "Cozinha", "Carta", "Pontos", "Conta")
        if (hasCalculator) {
            pages.add("Reservas")
            pages.add("Preço Justo")
        }
        if (isPremium && !isCustomerApp) {
            pages.add("Stock")
            pages.add("Análise")
            pages.add("Conteúdo IA")
        }
        return pages
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
        brandText.addView(label("StudioMesa · Gestão de sala", 10f, true, copper))
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
        header.addView(label(if (hasRemoteRestaurant()) "Sistema ligado · Gestão de restaurante" else "Demonstração · Dados de exemplo", 10.5f, true, copperPale))
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
                    "Início" -> "Início"
                    "Mesas" -> "Mesas"
                    "Pedidos" -> "Pedidos"
                    "Cozinha" -> "Cozinha"
                    "Carta" -> "Carta"
                    "Pontos" -> "Pontos"
                    "Conta" -> "Conta"
                    "Preço Justo" -> "Preço justo"
                    "Reservas" -> "Reservas"
                    "Stock" -> "Stock"
                    "Análise" -> "Análise"
                    "Conteúdo IA" -> "Conteúdo IA"
                    else -> page
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
            "Cozinha" -> renderKitchen(container)
            "Carta" -> renderMenu(container)
            "Pontos" -> renderCustomers(container)
            "Conta" -> renderBackendAccount(container)
            "Preço Justo" -> renderPriceCalculator(container)
            "Reservas" -> renderReservations(container)
            "Stock" -> renderStock(container)
            "Análise" -> renderAnalysis(container)
            "Conteúdo IA" -> renderAiAssistant(container)
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
                LinearLayout.LayoutParams(-1, dp(46)))
            addGap(container, 6)
            container.addView(secondaryButton("Atualizar do servidor") { refreshRemoteOperations() },
                LinearLayout.LayoutParams(-1, dp(40)))
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
                LinearLayout.LayoutParams(-1, dp(42)))
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
                top.addView(label(status.uppercase(Locale("pt", "PT")), 9f, true, if (closed) green else copper).apply {
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
                }, LinearLayout.LayoutParams(-1, dp(44)))
                addGap(card, 5)
                if (!closed) {
                    card.addView(secondaryButton("Cancelar pedido") {
                        confirmCancelOrder(
                            order.optString("id"),
                            order.optInt("table_number", 1),
                            order.optString("item_name", "Artigo")
                        )
                    }, LinearLayout.LayoutParams(-1, dp(40)))
                    addGap(card, 5)
                }
                card.addView(secondaryButton("Ver histórico") {
                    showOrderHistory(order.optString("id"))
                }, LinearLayout.LayoutParams(-1, dp(40)))
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
            top.addView(label(if (done) "CONCLUÍDO" else "EM ABERTO", 9f, true, if (done) green else copper).apply {
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
            }, LinearLayout.LayoutParams(-1, dp(42)))
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
                LinearLayout.LayoutParams(-1, dp(42)))
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
        }, onAdvance), LinearLayout.LayoutParams(-1, dp(46)))
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
                LinearLayout.LayoutParams(-1, dp(42)))
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
                }, LinearLayout.LayoutParams(-1, dp(40)))
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
                }, LinearLayout.LayoutParams(-1, dp(40)))
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
            }, LinearLayout.LayoutParams(-1, dp(40)))
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
            }, LinearLayout.LayoutParams(-1, dp(40)))
            container.addView(card, cardParams())
        }
    }

    private fun renderPriceCalculator(container: LinearLayout) {
        pageIntro(container, "Preço Justo", "Calcula o custo e sugere um preço.")
        val form = makeCard(16)
        val ingredientRowsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val rows = mutableListOf<IngredientFields>()
        fun addRow() {
            val card = makeCard(10, paper)
            card.addView(label("Ingrediente ${rows.size + 1}", 10f, true, forest))
            val name = inputField("Nome", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
            card.addView(name, fieldParams())
            val pair = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val qty = inputField("Quantidade", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
            val cost = inputField("Custo/unidade (€)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
            pair.addView(qty, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(4), 0) })
            pair.addView(cost, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), 0, 0, 0) })
            card.addView(pair)
            ingredientRowsContainer.addView(card, cardParams())
            rows.add(IngredientFields(card, name, qty, cost))
        }
        fun numberField(title: String, hint: String, initial: String? = null): EditText {
            form.addView(label(title, 10f, true, forestSoft))
            val field = inputField(hint, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
            if (initial != null) field.setText(initial)
            form.addView(field, fieldParams())
            return field
        }
        addRow()
        form.addView(ingredientRowsContainer, cardParams())
        form.addView(secondaryButton("+ Ingrediente") {
            if (rows.size < 30) addRow() else AlertDialog.Builder(this).setMessage("Máx. 30").setPositiveButton("OK", null).show()
        }, LinearLayout.LayoutParams(-1, dp(40)))
        form.addView(secondaryButton("Remover ingrediente") {
            if (rows.size > 1) ingredientRowsContainer.removeView(rows.removeAt(rows.lastIndex).container)
        }, LinearLayout.LayoutParams(-1, dp(40)))
        val waste = numberField("Desperdício (%)", "Percentagem", "0")
        val packaging = numberField("Embalagem (€)", "Custo por dose", "0")
        val other = numberField("Outros custos variáveis (€)", "Custo por dose", "0")
        val margin = numberField("Margem bruta pretendida (%)", "Percentagem", "60")
        val vat = numberField("IVA confirmado pelo restaurante (%)", "Taxa confirmada")
        val result = label("Preenche os campos e calcula.", 13f, false, ink).apply {
            setPadding(dp(12), dp(12), dp(12), dp(12)); background = rounded(paper, 12)
        }
        form.addView(primaryButton("Calcular preço sugerido") {
            fun parse(field: EditText, description: String): java.math.BigDecimal? {
                val raw = field.text.toString().trim().replace(',', '.')
                field.error = null
                if (raw.length > 32 || !Regex("^[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)$").matches(raw)) {
                    field.error = "Introduz $description válido."
                    return null
                }
                val parsed = raw.toBigDecimalOrNull()
                if (parsed == null) field.error = "Introduz $description válido."
                return parsed
            }
            val ingredients = mutableListOf<RestaurantPriceCalculator.Ingredient>()
            for (row in rows) {
                val name = row.name.text.toString().trim()
                if (name.isBlank()) { row.name.error = "Introduz o nome."; return@primaryButton }
                row.name.error = null
                val quantity = parse(row.quantity, "uma quantidade") ?: return@primaryButton
                val unitCost = parse(row.unitCost, "um custo") ?: return@primaryButton
                ingredients.add(RestaurantPriceCalculator.Ingredient(name, quantity, unitCost))
            }
            val wasteValue = parse(waste, "o desperdício") ?: return@primaryButton
            val packagingValue = parse(packaging, "a embalagem") ?: return@primaryButton
            val otherValue = parse(other, "outros custos") ?: return@primaryButton
            val marginValue = parse(margin, "a margem") ?: return@primaryButton
            val vatValue = parse(vat, "o IVA confirmado") ?: return@primaryButton
            val calc = try {
                RestaurantPriceCalculator.calculate(ingredients, wasteValue, packagingValue, otherValue, marginValue, vatValue)
            } catch (e: IllegalArgumentException) {
                result.text = e.message ?: "Valores inválidos."; result.setTextColor(red); return@primaryButton
            }
            result.text = "PREÇO INDICATIVO COM IVA\n" + money(calc.suggestedPriceIncludingVat.toDouble()) +
                "\nPreço sem IVA: " + money(calc.suggestedPriceExcludingVat.toDouble()) +
                "\nIVA: " + money(calc.vatAmount.toDouble()) +
                "\n\nIngredientes: " + money(calc.ingredientCost.toDouble()) +
                "\nDesperdício: " + money(calc.wasteCost.toDouble()) +
                "\nEmbalagem: " + money(calc.packagingCost.toDouble()) +
                "\nOutros variáveis: " + money(calc.otherVariableCost.toDouble()) +
                "\nCusto total: " + money(calc.totalCost.toDouble()) +
                "\nMargem bruta estimada: " + String.format(Locale("pt", "PT"), "%.2f%%", calc.estimatedGrossMarginPercent.toDouble())
            result.setTextColor(forest)
        }, LinearLayout.LayoutParams(-1, dp(46)))
        form.addView(result)
        container.addView(form, cardParams())
        val note = makeCard(14, copperPale)
        note.addView(label("Margem bruta não é lucro líquido", 13f, true, forest))
        note.addView(label("Offline; não altera nem guarda preços. Usa unidades consistentes. Custos fixos só entram se contabilizados; confirma custos e IVA.", 12f, false, ink))
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
        if (refreshToken.isBlank()) return

        val expiresAt = if (accessToken.isBlank()) 0L else SupabaseApi.tokenExpiresAtMillis(accessToken)
        if (expiresAt > System.currentTimeMillis() + 120_000L) return

        val session = SupabaseApi.refreshSession(refreshToken)
        secureSession.save(session.accessToken, session.refreshToken)
        backendPrefs.edit()
            .putString("user_id", session.userId)
            .putString("user_email", session.email)
            .apply()

        val restored = if (isCustomerApp) {
            restoreCustomerMembership(session.accessToken)
        } else {
            restoreOwnedRestaurant(session.accessToken)
        }
        if (!restored) {
            throw IllegalStateException("Não foi possível confirmar as permissões da conta. Verifica a ligação e tenta novamente.")
        }
    }

    private fun restoreOwnedRestaurant(token: String): Boolean {
        if (isCustomerApp || token.isBlank()) return false
        return try {
            val restaurant = SupabaseApi.getMyRestaurant(token)
            if (restaurant == null) {
                clearCachedTenantState()
                true
            } else {
                val restaurantId = restaurant.optString("restaurant_id").trim()
                if (restaurantId.isBlank()) {
                    throw IllegalStateException("O servidor não devolveu um restaurante válido.")
                }
                val role = restaurant.optString("user_role", "staff").ifBlank { "staff" }
                val previousRestaurantId = backendRestaurantId()
                val previousRole = backendPrefs.getString("restaurant_role", "").orEmpty()
                if (previousRestaurantId != restaurantId || previousRole != role) {
                    clearRemoteOperationalCache()
                }
                backendPrefs.edit()
                    .putString("restaurant_id", restaurantId)
                    .putString("restaurant_name", restaurant.optString("restaurant_name"))
                    .putString("join_code", restaurant.optString("join_code"))
                    .putString("staff_join_code", restaurant.optString("staff_code"))
                    .putString("restaurant_role", role)
                    .apply()
                cacheRemoteOperations(token, restaurantId)
                true
            }
        } catch (_: Exception) {
            clearCachedTenantState()
            false
        }
    }

    private fun clearRemoteOperationalCache() {
        backendPrefs.edit()
            .remove("remote_members_json")
            .remove("remote_tables_json")
            .remove("remote_menu_json")
            .remove("remote_orders_json")
            .remove("customer_menu_json")
            .remove("remote_inventory_json")
            .remove("inventory_loaded")
            .remove("remote_reservations_json")
            .remove("reservations_loaded")
            .apply()
        reservationCache = JSONArray()
        reservationsLoaded = false
    }

    private fun clearCachedTenantState() {
        backendPrefs.edit()
            .remove("restaurant_id")
            .remove("restaurant_name")
            .remove("join_code")
            .remove("staff_join_code")
            .remove("restaurant_role")
            .remove("member_id")
            .remove("member_code")
            .remove("member_points")
            .remove("member_restaurant_name")
            .apply()
        clearRemoteOperationalCache()
    }

    private fun verifyExistingSessionAndRender() {
        renderSessionVerificationScreen(isVerifying = true)
        Thread {
            var verified = false
            try {
                refreshSessionIfNeeded()
                val token = backendToken()
                if (token.isBlank()) {
                    throw IllegalStateException("Não existe uma sessão autenticada válida.")
                }
                val user = SupabaseApi.getUser(token)
                val verifiedUserId = user.optString("id").trim()
                if (verifiedUserId.isBlank()) {
                    throw IllegalStateException("O servidor não devolveu uma conta válida.")
                }

                val savedUserId = backendPrefs.getString("user_id", "").orEmpty()
                if (savedUserId.isNotBlank() && savedUserId != verifiedUserId) {
                    store.clearLocalDemoData()
                    clearCachedTenantState()
                }
                backendPrefs.edit()
                    .putString("user_id", verifiedUserId)
                    .putString("user_email", user.optString("email"))
                    .apply()

                verified = if (isCustomerApp) {
                    restoreCustomerMembership(token)
                } else {
                    restoreOwnedRestaurant(token)
                }
            } catch (_: Exception) {
                verified = false
            }
            if (!verified) clearCachedTenantState()
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    if (verified) {
                        if (isCustomerApp) renderCustomerApp() else renderApp()
                    } else {
                        renderSessionVerificationScreen(isVerifying = false)
                    }
                }
            }
        }.start()
    }

    private fun renderSessionVerificationScreen(isVerifying: Boolean) {
        appRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(paper)
        }
        applySystemBarInsets(appRoot)
        appRoot.addView(label(
            if (isVerifying) "A verificar autorização da conta" else "Não foi possível confirmar o acesso",
            23f, true, forestDeep
        ))
        addGap(appRoot, 12)
        appRoot.addView(label(
            if (isVerifying) {
                "Os dados do restaurante só aparecem depois de confirmar no servidor a tua associação e as permissões."
            } else {
                "Por segurança, os dados guardados neste dispositivo estão ocultos. Verifica a ligação e tenta novamente. Se a sessão expirou, podes terminar a sessão neste dispositivo e voltar a entrar; a conta e os dados no servidor não serão eliminados."
            },
            14f, false, muted
        ))
        if (!isVerifying) {
            addGap(appRoot, 18)
            appRoot.addView(primaryButton("Tentar novamente") {
                verifyExistingSessionAndRender()
            }, LinearLayout.LayoutParams(-1, dp(48)))
            addGap(appRoot, 8)
            appRoot.addView(secondaryButton("Terminar sessão e voltar a entrar") {
                returnToAuthenticationAfterSessionFailure()
            }, LinearLayout.LayoutParams(-1, dp(48)))
        }
        setContentView(appRoot)
    }

    /**
     * Provides a recovery path when the stored session is expired or revoked.
     * This clears credentials and local cached data only; it never deletes the server account.
     */
    private fun returnToAuthenticationAfterSessionFailure() {
        val sessionCleared = secureSession.clear()
        if (isCustomerApp) {
            store.clearCustomerLocalIdentity()
        } else {
            store.clearLocalDemoData()
            backendPrefs.edit().putBoolean("local_demo_data_isolated_v1", true).apply()
        }
        clearCachedTenantState()
        backendPrefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("user_id")
            .remove("user_email")
            .apply()

        if (isCustomerApp) {
            renderCustomerApp()
        } else {
            activePage = "Conta"
            renderApp()
        }

        if (!sessionCleared) {
            AlertDialog.Builder(this)
                .setTitle("Aviso de segurança")
                .setMessage("Não foi possível confirmar a limpeza completa das credenciais locais. A conta e os dados no servidor não foram eliminados. Reinicia a aplicação antes de voltares a utilizá-la.")
                .setPositiveButton("OK", null)
                .show()
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

    private fun restoreCustomerMembership(token: String): Boolean {
        if (!isCustomerApp || token.isBlank()) return false
        return try {
            val member = SupabaseApi.getMyLoyaltyMembership(token)
            if (member == null) {
                backendPrefs.edit()
                    .remove("member_id")
                    .remove("member_code")
                    .remove("member_points")
                    .remove("member_restaurant_name")
                    .putString("customer_menu_json", "[]")
                    .apply()
                true
            } else {
                val memberId = member.optString("member_id").trim()
                if (memberId.isBlank()) {
                    throw IllegalStateException("O servidor não devolveu um cartão de cliente válido.")
                }
                val menu = SupabaseApi.listCustomerMenu(token, memberId)
                backendPrefs.edit()
                    .putString("member_id", memberId)
                    .putString("member_code", member.optString("member_code"))
                    .putInt("member_points", member.optInt("points", 0))
                    .putString("member_restaurant_name", member.optString("restaurant_name"))
                    .putString("customer_menu_json", menu.toString())
                    .apply()
                val displayName = member.optString("display_name").trim()
                if (displayName.isNotBlank()) store.saveCustomerProfileName(displayName)
                true
            }
        } catch (_: Exception) {
            clearCachedTenantState()
            false
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

    private fun requiresSessionReverification(error: Exception): Boolean =
        SessionAuthorizationErrorClassifier.requiresReverification(error)

    private fun runBackend(
        task: () -> String,
        refreshSession: Boolean = true,
        onError: ((Exception) -> Unit)? = null,
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
                        if (requiresSessionReverification(error)) {
                            // Authorization failures take precedence over screen-specific
                            // handlers: never leave cached tenant data visible after access
                            // could not be confirmed.
                            clearCachedTenantState()
                            renderSessionVerificationScreen(isVerifying = false)
                        } else if (onError != null) {
                            onError(error)
                        } else {
                            AlertDialog.Builder(this)
                                .setTitle("Não foi possível concluir")
                                .setMessage(friendlyBackendError(error.message))
                                .setPositiveButton("OK", null)
                                .show()
                        }
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
        }, LinearLayout.LayoutParams(-1, dp(46)))
        addGap(form, 6)
        form.addView(secondaryButton("Continuar com Microsoft (Outlook)") {
            dialog.dismiss()
            startOAuth("azure")
        }, LinearLayout.LayoutParams(-1, dp(46)))
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
            }, LinearLayout.LayoutParams(-1, dp(42)))
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
                                        val restored = if (isCustomerApp) {
                                            restoreCustomerMembership(token)
                                        } else {
                                            restoreOwnedRestaurant(token)
                                        }
                                        if (!restored) {
                                            throw IllegalStateException("Não foi possível confirmar as permissões da conta. Verifica a ligação e tenta novamente.")
                                        }
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
                                val restored = if (isCustomerApp) {
                                    restoreCustomerMembership(session.accessToken)
                                } else {
                                    restoreOwnedRestaurant(session.accessToken)
                                }
                                if (!restored) {
                                    throw IllegalStateException("Não foi possível confirmar as permissões da conta. Verifica a ligação e tenta novamente.")
                                }
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
                        if (!restoreOwnedRestaurant(token)) {
                            throw IllegalStateException("Não foi possível confirmar as permissões da conta. Verifica a ligação e tenta novamente.")
                        }
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
                    }, LinearLayout.LayoutParams(-1, dp(40)))
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
                        if (!restoreOwnedRestaurant(token)) {
                            throw IllegalStateException("Não foi possível confirmar as permissões da conta. Verifica a ligação e tenta novamente.")
                        }
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
                LinearLayout.LayoutParams(-1, dp(44)))
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
                    LinearLayout.LayoutParams(-1, dp(44)))
                addGap(card, 5)
                card.addView(secondaryButton("Ver código de acesso à equipa") { showStaffJoinCode() },
                    LinearLayout.LayoutParams(-1, dp(44)))
                addGap(card, 5)
                card.addView(secondaryButton("Gerir membros e acessos da equipa") { showRestaurantTeam() },
                    LinearLayout.LayoutParams(-1, dp(44)))
                addGap(card, 5)
                card.addView(secondaryButton("Renovar código da equipa") { confirmCodeRotation(true) },
                    LinearLayout.LayoutParams(-1, dp(44)))
            } else {
                card.addView(label("Esta conta está associada à equipa deste restaurante. As alterações operacionais são guardadas no servidor.", 12f, false, ink))
            }
            addGap(card, 12)
            card.addView(primaryButton("Atualizar lista de clientes") { refreshRemoteCustomers() },
                LinearLayout.LayoutParams(-1, dp(46)))
        }
        container.addView(card, cardParams())
        addGap(container, 3)
        if (token.isNotBlank()) {
            container.addView(secondaryButton("Eliminar conta permanentemente") {
                showDeleteAccountConfirmation()
            }, LinearLayout.LayoutParams(-1, dp(44)))
            addGap(container, 7)
        }
        container.addView(secondaryButton("Terminar sessão neste dispositivo") {
            val tokenToRevoke = backendToken()
            if (tokenToRevoke.isNotBlank()) {
                Thread { runCatching { SupabaseApi.signOut(tokenToRevoke) } }.start()
            }
            val sessionCleared = secureSession.clear()
            store.clearLocalDemoData()
            backendPrefs.edit().putBoolean("local_demo_data_isolated_v1", true)
                .remove("access_token").remove("refresh_token").remove("user_id").remove("user_email")
                .remove("restaurant_id").remove("restaurant_name").remove("join_code")
                .remove("staff_join_code").remove("restaurant_role")
                .remove("member_id").remove("member_code").remove("member_points").remove("member_restaurant_name")
                .remove("remote_members_json").remove("remote_tables_json")
                .remove("remote_menu_json").remove("remote_orders_json").remove("customer_menu_json")
                .remove("remote_inventory_json").remove("inventory_loaded")
                .remove("remote_reservations_json").remove("reservations_loaded")
                .apply()
            reservationCache = JSONArray()
            reservationsLoaded = false
            renderApp()
            if (!sessionCleared) {
                AlertDialog.Builder(this)
                    .setTitle("Aviso de segurança")
                    .setMessage("Não foi possível confirmar a remoção completa das credenciais locais. A aplicação tentou terminar a sessão, mas evita partilhar este dispositivo até verificares que já não está autenticado.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }, LinearLayout.LayoutParams(-1, dp(44)))
        addGap(container, 10)
        container.addView(label("As funções de gestão e os dados de cada restaurante são controlados por permissões no servidor.", 12f, false, muted))
    }

    private fun showDeleteAccountConfirmation() {
        if (backendToken().isBlank()) {
            AlertDialog.Builder(this)
                .setTitle("Inicia sessão")
                .setMessage("Inicia sessão na conta que pretendes eliminar e tenta novamente.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val isOwner = backendPrefs.getString("restaurant_role", "").orEmpty() == "owner" &&
            backendRestaurantId().isNotBlank()

        if (isOwner) {
            AlertDialog.Builder(this)
                .setTitle("Eliminar conta e restaurante?")
                .setMessage(
                    "A eliminação de uma conta de proprietário também elimina permanentemente o restaurante, " +
                        "a carta, as mesas, os pedidos, as reservas, o stock, os códigos e os dados de fidelização " +
                        "associados. Esta ação não pode ser desfeita. Os outros membros da equipa têm de ser " +
                        "removidos primeiro; o servidor verifica esta condição antes de eliminar qualquer dado."
                )
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Continuar") { _, _ ->
                    AlertDialog.Builder(this)
                        .setTitle("Confirmação final")
                        .setMessage("Confirma que queres eliminar definitivamente a tua conta e todos os dados deste restaurante?")
                        .setNegativeButton("Manter conta", null)
                        .setPositiveButton("Eliminar definitivamente") { _, _ ->
                            performAccountDeletion(deleteRestaurantData = true)
                        }
                        .show()
                }
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Eliminar conta?")
                .setMessage(
                    "A conta será eliminada e a associação ao cartão de fidelização será removida. " +
                        "Os registos operacionais partilhados do restaurante são preservados quando possível, " +
                        "e a autoria desta conta é anonimizada no histórico. Esta ação não pode ser desfeita."
                )
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar conta") { _, _ ->
                    performAccountDeletion(deleteRestaurantData = false)
                }
                .show()
        }
    }

    private fun performAccountDeletion(deleteRestaurantData: Boolean) {
        runBackend({
            val restaurantDeleted = SupabaseApi.deleteAccount(backendToken(), deleteRestaurantData)
            if (restaurantDeleted) "DELETED_RESTAURANT" else "DELETED_ACCOUNT"
        }) { result ->
            val sessionCleared = secureSession.clear()
            runCatching { pendingRequestIds.clearAll() }
            store.clearLocalDemoData()
            backendPrefs.edit()
                .clear()
                .putBoolean("local_demo_data_isolated_v1", true)
                .apply()
            reservationCache = JSONArray()
            reservationsLoaded = false

            if (isCustomerApp) renderCustomerApp() else renderApp()

            val baseMessage = if (result == "DELETED_RESTAURANT") {
                "A conta e os dados do restaurante foram eliminados permanentemente."
            } else {
                "A conta foi eliminada. A associação de fidelização foi removida e a autoria dos registos operacionais preservados foi anonimizada."
            }
            val message = if (sessionCleared) baseMessage else {
                "$baseMessage\n\nAviso: não foi possível confirmar a limpeza completa da sessão local. A conta foi eliminada no servidor; reinicia a aplicação antes de a reutilizares."
            }
            AlertDialog.Builder(this)
                .setTitle("Conta eliminada")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun loadRemoteCustomers(): MutableList<LoyaltyCustomer> {
        val raw = backendPrefs.getString("remote_members_json", "[]") ?: "[]"
        val result = mutableListOf<LoyaltyCustomer>()
        try {
            val array = org.json.JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                result.add(LoyaltyCustomer(
                    item.optString("display_name"),
                    item.optString("member_code"),
                    item.optInt("points", 0).coerceAtLeast(0),
                    item.optString("id").takeIf { it.isNotBlank() }
                ))
            }
        } catch (_: Exception) { }
        return result
    }

    private fun refreshRemoteCustomers() {
        val restaurantId = backendRestaurantId()
        if (backendToken().isBlank() || restaurantId.isBlank()) {
            AlertDialog.Builder(this).setMessage("Inicia sessão e configura primeiro o restaurante na secção Conta.")
                .setPositiveButton("OK", null).show()
            return
        }
        runBackend({
            val members = SupabaseApi.listMembers(backendToken(), restaurantId)
            backendPrefs.edit().putString("remote_members_json", members.toString()).apply()
            members.length().toString()
        }) { count ->
            if (activePage == "Pontos") renderApp()
            AlertDialog.Builder(this).setTitle("Lista atualizada")
                .setMessage("$count cartões de cliente encontrados no servidor.")
                .setPositiveButton("OK", null).show()
        }
    }

    private fun showRestaurantJoinCode() {
        val code = backendPrefs.getString("join_code", "") ?: ""
        AlertDialog.Builder(this)
            .setTitle("Código de adesão")
            .setMessage("Código: $code\n\nO cliente deve abrir a aplicação «StudioMesa - Cliente», criar conta ou iniciar sessão e escolher «Associar ao restaurante».")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun renderCustomers(container: LinearLayout) {
        pageIntro(container, "Fidelização por pontos", "Regista clientes e gere pontos manualmente.")
        addGap(container, 9)

        val remoteMode = backendToken().isNotBlank() && backendRestaurantId().isNotBlank()
        val notice = makeCard(14, copperPale)
        notice.addView(label(if (remoteMode) "FIDELIZAÇÃO PARTILHADA" else "REGRAS A DEFINIR COM O RESTAURANTE", 10f, true, forest))
        addGap(notice, 5)
        notice.addView(label(if (remoteMode) "Os pontos são guardados no servidor e as alterações ficam registadas. A acumulação automática por compra e os prémios ainda precisam de regras comerciais." else "No modo local, os dados ficam apenas neste telemóvel. Liga uma conta e configura o restaurante na secção Conta para ativar a fidelização partilhada.", 12.5f, false, ink))
        container.addView(notice, cardParams())
        if (remoteMode) {
            container.addView(primaryButton("Atualizar clientes do servidor") { refreshRemoteCustomers() }, LinearLayout.LayoutParams(-1, dp(48)))
            addGap(container, 6)
            container.addView(secondaryButton("Ver código de adesão") { showRestaurantJoinCode() }, LinearLayout.LayoutParams(-1, dp(42)))
        } else {
            container.addView(primaryButton("+  Adicionar cliente (local)") { showAddCustomerDialog() }, LinearLayout.LayoutParams(-1, dp(48)))
        }
        addGap(container, 13)

        val customers = if (remoteMode) loadRemoteCustomers() else store.getCustomers()
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
            card.addView(label("Pontos disponíveis", 9f, true, forestSoft))
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
        if (backendToken().isNotBlank() && backendRestaurantId().isNotBlank()) {
            showRestaurantJoinCode()
            return
        }
        val nameField = inputField("Nome do cliente", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(2))
        }
        form.addView(label("Nome para identificar o cartão", 10f, true, forestSoft))
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
                        if (customer.backendId != null && backendToken().isNotBlank()) {
                            val memberId = customer.backendId
                            val restaurantId = backendRestaurantId()
                            val noteText = if (redeem) "Desconto manual" else "Atribuição manual"
                            val signature = JSONObject()
                                .put("restaurant_id", restaurantId)
                                .put("member_id", memberId)
                                .put("delta", delta)
                                .put("note", noteText)
                                .toString()
                            val requestOperation = "loyalty_adjust"
                            val requestAccountId = backendPrefs.getString("user_id", "").orEmpty()
                            val requestId = try {
                                pendingRequestIds.getOrCreate(requestOperation, requestAccountId, signature)
                            } catch (_: Exception) {
                                amountField.error = "Não foi possível guardar a referência segura. Tenta novamente."
                                return@setOnClickListener
                            }
                            runBackend({
                                SupabaseApi.adjustPoints(
                                    backendToken(), restaurantId, memberId, delta, noteText, requestId
                                )
                                val members = SupabaseApi.listMembers(backendToken(), restaurantId)
                                backendPrefs.edit().putString("remote_members_json", members.toString()).apply()
                                "OK"
                            }) {
                                dialog.dismiss()
                                renderApp()
                                pendingRequestIds.complete(requestOperation, requestAccountId, signature, requestId)
                            }
                        } else if (store.adjustCustomerPoints(customer.code, delta)) {
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

    private fun showCustomerJoinDialog() {
        if (backendToken().isBlank()) { showAuthChooser { renderCustomerApp() }; return }
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(8), dp(22), dp(4)) }
        val codeField = inputField("Código de adesão do restaurante", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
        val nameField = inputField("O teu nome", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        nameField.setText(store.getCustomerProfileName())
        form.addView(label("Código do restaurante", 10f, true, forestSoft)); form.addView(codeField, fieldParams())
        addGap(form, 4); form.addView(label("Nome para o cartão", 10f, true, forestSoft)); form.addView(nameField, fieldParams())
        val dialog = AlertDialog.Builder(this).setTitle("Associar ao restaurante")
            .setMessage("Pede o código de adesão ao restaurante.").setView(form)
            .setNegativeButton("Cancelar", null).setPositiveButton("Associar", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = codeField.text.toString().trim().uppercase(Locale.ROOT)
                val name = nameField.text.toString().trim()
                when {
                    code.length !in 6..12 -> codeField.error = "O código deve ter 6 a 12 caracteres"
                    name.isBlank() || name.length > 100 -> nameField.error = "Introduz um nome válido"
                    else -> {
                        runBackend({
                            val token = backendToken()
                            val member = SupabaseApi.joinRestaurant(token, code, name)
                            val memberId = member.optString("member_id")
                            val menu = SupabaseApi.listCustomerMenu(token, memberId)
                            backendPrefs.edit().putString("member_id", memberId)
                                .putString("member_code", member.optString("member_code"))
                                .putInt("member_points", member.optInt("points", 0))
                                .putString("member_restaurant_name", member.optString("restaurant_name"))
                                .putString("customer_menu_json", menu.toString())
                                .apply()
                            store.saveCustomerProfileName(name)
                            "OK"
                        }) {
                            dialog.dismiss()
                            renderCustomerApp()
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun refreshCustomerMembership() {
        val memberId = backendMemberId()
        if (backendToken().isBlank() || memberId.isBlank()) { showCustomerJoinDialog(); return }
        runBackend({
            val token = backendToken()
            val member = SupabaseApi.getMember(token, memberId)
            val menu = SupabaseApi.listCustomerMenu(token, memberId)
            backendPrefs.edit().putInt("member_points", member.optInt("points", 0))
                .putString("member_code", member.optString("member_code"))
                .putString("customer_menu_json", menu.toString())
                .apply()
            "OK"
        }) { renderCustomerApp() }
    }

    private fun renderCustomerApp() {
        appRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(paper)
        }
        applySystemBarInsets(appRoot)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(forestDeep, forest, forestSoft)
            ).apply { cornerRadius = dp(22).toFloat() }
        }
        header.addView(label(displayCustomerRestaurantName(), 25f, true, white))
        addGap(header, 4)
        header.addView(label("Cartão digital de cliente · " + planTitle, 10.5f, true, copper))
        addGap(header, 13)
        header.addView(label("A tua experiência, num só lugar.", 17f, true, white))
        header.addView(label(if (backendMemberId().isBlank()) "Cartão demonstrativo — associa a tua conta ao restaurante." else "Cartão digital de fidelização", 12f, false, copperPale))
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
        content.addView(label("Cartão de fidelização do restaurante.", 13.5f, false, muted))
        addGap(content, 12)
        when {
            backendToken().isBlank() -> content.addView(primaryButton("Criar conta / iniciar sessão") { showAuthChooser { renderCustomerApp() } }, LinearLayout.LayoutParams(-1, dp(46)))
            backendMemberId().isBlank() -> content.addView(primaryButton("Associar ao restaurante") { showCustomerJoinDialog() }, LinearLayout.LayoutParams(-1, dp(46)))
            else -> content.addView(primaryButton("Atualizar saldo no servidor") { refreshCustomerMembership() }, LinearLayout.LayoutParams(-1, dp(46)))
        }
        addGap(content, 15)

        val loyalty = makeCard(18, forest)
        loyalty.addView(label("O meu cartão", 10.5f, true, copper))
        addGap(loyalty, 10)
        loyalty.addView(label(backendPrefs.getInt("member_points", 0).toString(), 42f, true, white))
        loyalty.addView(label(if (backendMemberId().isBlank()) "Pontos de demonstração" else "Pontos disponíveis", 10f, true, copperPale))
        addGap(loyalty, 13)
        val code = backendPrefs.getString("member_code", "")?.takeIf { it.isNotBlank() } ?: store.getCustomerMemberCode()
        loyalty.addView(label("Código de cliente", 11f, false, copperPale))
        addGap(loyalty, 3)
        loyalty.addView(label(code, 20f, true, white))
        addGap(loyalty, 9)
        loyalty.addView(label(if (backendMemberId().isBlank()) "Este é um cartão demonstrativo. Cria sessão e associa-o ao código do restaurante para consultar os pontos reais." else "Saldo obtido do sistema de fidelização partilhado. A atribuição de pontos é controlada pelo restaurante.", 12f, false, Color.rgb(229, 235, 228)))
        content.addView(loyalty, cardParams())

        content.addView(secondaryButton("Definir o meu nome") { showCustomerProfileDialog() }, LinearLayout.LayoutParams(-1, dp(44)))
        addGap(content, 15)

        val menuCard = makeCard(16)
        val isLinkedToRestaurant = backendMemberId().isNotBlank()
        menuCard.addView(label(if (isLinkedToRestaurant) "Carta do restaurante" else "Carta de demonstração", 10f, true, forestSoft))
        addGap(menuCard, 7)
        menuCard.addView(label(if (isLinkedToRestaurant) "A nossa carta" else "Exemplos de artigos", 18f, true, forestDeep))
        addGap(menuCard, 4)
        menuCard.addView(label(
            if (isLinkedToRestaurant) "Preços publicados pelo restaurante. Os custos internos não são mostrados."
            else "Nomes e preços de exemplo; associa a tua conta a um restaurante para veres a carta real.",
            12f, false, muted
        ))
        addGap(menuCard, 10)
        if (isLinkedToRestaurant) {
            val publishedMenu = savedRemoteArray("customer_menu_json")
            if (publishedMenu.length() == 0) {
                menuCard.addView(label("O restaurante ainda não publicou artigos ativos na carta.", 13f, false, muted))
            } else {
                for (index in 0 until publishedMenu.length()) {
                    val item = publishedMenu.optJSONObject(index) ?: continue
                    val itemCard = makeCard(12, paper)
                    itemCard.addView(label(item.optString("category", "Prato principal"), 10f, true, forestSoft))
                    addGap(itemCard, 3)
                    itemCard.addView(label(item.optString("name"), 16f, true, forestDeep))
                    val description = item.optString("description").trim()
                    if (description.isNotBlank()) {
                        addGap(itemCard, 5)
                        itemCard.addView(label(description, 12.5f, false, ink))
                    }
                    addGap(itemCard, 7)
                    val priceRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    priceRow.addView(label("Preço", 11f, false, muted), LinearLayout.LayoutParams(0, -2, 1f))
                    priceRow.addView(label(money(item.optDouble("price", 0.0)), 15f, true, forest))
                    itemCard.addView(priceRow)
                    addGap(itemCard, 5)
                    val allergens = item.optString("allergens").trim()
                    itemCard.addView(label(
                        if (allergens.isBlank()) "Alergénios não indicados. Confirma com a equipa antes de encomendar."
                        else "Alergénios declarados: $allergens. Confirma com a equipa se tiveres uma alergia.",
                        11f, false, muted
                    ))
                    menuCard.addView(itemCard, cardParams())
                }
            }
        } else {
            store.getMenu().forEach { item ->
                val itemCard = makeCard(12, paper)
                itemCard.addView(label(item.category, 10f, true, forestSoft))
                addGap(itemCard, 3)
                itemCard.addView(label(item.name, 15f, true, forestDeep))
                if (item.description.isNotBlank()) {
                    addGap(itemCard, 5)
                    itemCard.addView(label(item.description, 12.5f, false, ink))
                }
                addGap(itemCard, 7)
                val priceRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                priceRow.addView(label("Preço", 11f, false, muted), LinearLayout.LayoutParams(0, -2, 1f))
                priceRow.addView(label(money(item.price), 15f, true, forest))
                itemCard.addView(priceRow)
                addGap(itemCard, 5)
                itemCard.addView(label(
                    if (item.allergens.isBlank()) "Alergénios não indicados. Confirma com a equipa antes de encomendar."
                    else "Alergénios declarados: " + item.allergens + ". Confirma com a equipa se tiveres uma alergia.",
                    11f, false, muted
                ))
                menuCard.addView(itemCard, cardParams())
            }
        }
        content.addView(menuCard, cardParams())

        val rewards = makeCard(16, copperPale)
        rewards.addView(label("Próximos prémios", 10f, true, forest))
        addGap(rewards, 6)
        rewards.addView(label("Os prémios e as regras de acumulação serão definidos com o restaurante antes do lançamento.", 13f, false, ink))
        content.addView(rewards, cardParams())

        val syncNote = makeCard(13)
        syncNote.addView(label(if (backendMemberId().isBlank()) "CARTÃO AINDA NÃO ASSOCIADO" else "CONTA LIGADA", 10f, true, if (backendMemberId().isBlank()) red else green))
        addGap(syncNote, 5)
        syncNote.addView(label(if (backendMemberId().isBlank()) "Para ver o saldo real, inicia sessão e associa a conta com o código fornecido pelo restaurante." else "Restaurante associado: " + (backendPrefs.getString("member_restaurant_name", "Restaurante") ?: "Restaurante") + ". Atualiza o saldo quando quiseres consultar as alterações mais recentes.", 12f, false, muted))
        content.addView(syncNote, cardParams())

        if (backendToken().isNotBlank()) {
            content.addView(secondaryButton("Eliminar conta permanentemente") {
                showDeleteAccountConfirmation()
            }, LinearLayout.LayoutParams(-1, dp(44)))
            addGap(content, 7)
        }

        content.addView(secondaryButton("Terminar sessão neste dispositivo") {
            val tokenToRevoke = backendToken()
            if (tokenToRevoke.isNotBlank()) {
                Thread { runCatching { SupabaseApi.signOut(tokenToRevoke) } }.start()
            }
            val sessionCleared = secureSession.clear()
            store.clearCustomerLocalIdentity()
            backendPrefs.edit()
                .remove("access_token").remove("refresh_token").remove("user_id").remove("user_email")
                .remove("member_id").remove("member_code").remove("member_points").remove("member_restaurant_name")
                .remove("customer_menu_json")
                .apply()
            renderCustomerApp()
            if (!sessionCleared) {
                AlertDialog.Builder(this)
                    .setTitle("Aviso de segurança")
                    .setMessage("Não foi possível confirmar a remoção completa das credenciais locais. A aplicação tentou terminar a sessão, mas evita partilhar este dispositivo até verificares que já não está autenticado.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }, LinearLayout.LayoutParams(-1, dp(44)))

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


    private fun remoteInventoryItems(): JSONArray =
        runCatching { JSONArray(backendPrefs.getString("remote_inventory_json", "[]") ?: "[]") }
            .getOrElse { JSONArray() }

    private fun remoteReservations(): JSONArray = reservationCache

    private fun refreshInventoryItems() {
        if (!hasRemoteRestaurant()) {
            AlertDialog.Builder(this).setTitle("Liga o restaurante")
                .setMessage("O inventário partilhado exige uma conta autenticada e um restaurante configurado.")
                .setPositiveButton("OK", null).show()
            return
        }
        runBackend({
            val items = SupabaseApi.listInventoryItems(backendToken(), backendRestaurantId())
            backendPrefs.edit().putString("remote_inventory_json", items.toString())
                .putBoolean("inventory_loaded", true).apply()
            "OK"
        }) { renderApp() }
    }

    private fun refreshReservations() {
        if (!hasRemoteRestaurant()) {
            AlertDialog.Builder(this).setTitle("Liga o restaurante")
                .setMessage("As reservas partilhadas exigem uma conta autenticada e um restaurante configurado.")
                .setPositiveButton("OK", null).show()
            return
        }
        runBackend({
            val reservations = SupabaseApi.listReservations(backendToken(), backendRestaurantId())
            reservationCache = reservations
            reservationsLoaded = true
            "OK"
        }) { renderApp() }
    }

    private fun renderReservations(container: LinearLayout) {
        pageIntro(container, "Reservas", "Agenda de reservas partilhada com a equipa.")
        addGap(container, 8)
        if (!hasRemoteRestaurant()) {
            container.addView(emptyCard("Para proteger os dados dos clientes e sincronizar as reservas, inicia sessão e configura o restaurante na secção Conta."))
            return
        }
        container.addView(primaryButton("+  Nova reserva") { showReservationDialog() },
            LinearLayout.LayoutParams(-1, dp(48)))
        addGap(container, 6)
        container.addView(secondaryButton("Atualizar reservas") { refreshReservations() },
            LinearLayout.LayoutParams(-1, dp(40)))
        addGap(container, 12)

        if (!reservationsLoaded) {
            container.addView(emptyCard("A carregar as reservas do servidor…"))
            refreshReservations()
            return
        }

        val reservations = remoteReservations()
        if (reservations.length() == 0) {
            container.addView(emptyCard("Ainda não há reservas. Usa «Nova reserva» para registar a primeira."))
            return
        }

        val displayFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "PT"))
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        for (index in 0 until reservations.length()) {
            val reservation = reservations.optJSONObject(index) ?: continue
            val id = reservation.optString("id")
            val guest = reservation.optString("guest_name", "Cliente")
            val status = reservation.optString("status", "Pendente")
            val card = makeCard(15)
            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(label(guest, 17f, true, forestDeep), LinearLayout.LayoutParams(0, -2, 1f))
            top.addView(label(status, 10f, true, if (status == "Confirmada") green else if (status == "Cancelada" || status == "Concluída" || status == "Não compareceu") muted else forestSoft).apply {
                setPadding(dp(8), dp(6), dp(8), dp(6))
                background = rounded(if (status == "Confirmada") paleGreen else copperPale, 12)
            })
            card.addView(top)
            addGap(card, 6)
            val rawDate = reservation.optString("reservation_at")
            val dateText = runCatching { displayFormat.format(isoFormat.parse(rawDate) ?: Date()) }
                .getOrElse { rawDate.replace('T', ' ').take(16) }
            val tableNo = reservation.optInt("table_number", 0)
            card.addView(label("$dateText · ${reservation.optInt("party_size", 1)} pessoa(s)", 12.5f, true, ink))
            card.addView(label(if (tableNo > 0) "Mesa $tableNo · ${reservation.optInt("duration_minutes", 90)} min" else "Mesa por atribuir · ${reservation.optInt("duration_minutes", 90)} min", 11.5f, false, muted))
            val phone = reservation.optString("guest_phone").trim()
            if (phone.isNotBlank()) card.addView(label("Contacto: $phone", 11.5f, false, muted))
            val notes = reservation.optString("notes").trim()
            if (notes.isNotBlank()) card.addView(label("Notas: $notes", 11.5f, false, muted))
            addGap(card, 10)
            when (status) {
                "Pendente" -> card.addView(primaryButton("Confirmar reserva") {
                    setReservationStatus(id, "Confirmada")
                }, LinearLayout.LayoutParams(-1, dp(42)))
                "Confirmada" -> {
                    card.addView(primaryButton("Marcar como concluída") {
                        setReservationStatus(id, "Concluída")
                    }, LinearLayout.LayoutParams(-1, dp(42)))
                    addGap(card, 5)
                    card.addView(secondaryButton("Não compareceu") {
                        setReservationStatus(id, "Não compareceu")
                    }, LinearLayout.LayoutParams(-1, dp(40)))
                }
            }
            if (status == "Pendente" || status == "Confirmada") {
                addGap(card, 5)
                card.addView(secondaryButton("Cancelar reserva") {
                    AlertDialog.Builder(this)
                        .setTitle("Cancelar reserva?")
                        .setMessage("A reserva de $guest será marcada como cancelada.")
                        .setNegativeButton("Manter", null)
                        .setPositiveButton("Cancelar reserva") { _, _ -> setReservationStatus(id, "Cancelada") }
                        .show()
                }, LinearLayout.LayoutParams(-1, dp(40)))
            }
            container.addView(card, cardParams())
        }
    }

    private fun setReservationStatus(reservationId: String, status: String) {
        runBackend({
            SupabaseApi.updateReservationStatus(backendToken(), reservationId, status)
            val latest = SupabaseApi.listReservations(backendToken(), backendRestaurantId())
            reservationCache = latest
            reservationsLoaded = true
            "OK"
        }) { renderApp() }
    }

    private fun showReservationDialog() {
        if (!hasRemoteRestaurant()) {
            AlertDialog.Builder(this).setMessage("Liga primeiro o restaurante na secção Conta.")
                .setPositiveButton("OK", null).show()
            return
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(10))
        }
        val guest = inputField("Nome do cliente", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val phone = inputField("Contacto (opcional)", InputType.TYPE_CLASS_PHONE)
        val party = inputField("Número de pessoas", InputType.TYPE_CLASS_NUMBER).apply { setText("2") }
        val date = inputField("dd/MM/aaaa HH:mm", InputType.TYPE_CLASS_DATETIME).apply {
            val defaultDate = Date(System.currentTimeMillis() + 24L * 60L * 60L * 1000L)
            setText(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "PT")).format(defaultDate))
        }
        val duration = inputField("Duração em minutos", InputType.TYPE_CLASS_NUMBER).apply { setText("90") }
        val table = inputField("Número da mesa (opcional)", InputType.TYPE_CLASS_NUMBER)
        val notes = multilineInputField("Notas úteis (opcional)")
        listOf(
            "Nome do cliente" to guest,
            "Contacto" to phone,
            "Número de pessoas" to party,
            "Data e hora (dd/MM/aaaa HH:mm)" to date,
            "Duração (minutos)" to duration,
            "Mesa (opcional)" to table,
            "Notas" to notes
        ).forEach { (caption, field) ->
            form.addView(label(caption, 11f, true, forestSoft))
            form.addView(field, if (field === notes) multilineFieldParams() else fieldParams())
            addGap(form, 5)
        }
        val scroll = ScrollView(this).apply { addView(form) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Nova reserva")
            .setView(scroll)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val guestName = guest.text.toString().trim()
                val partySize = party.text.toString().trim().toIntOrNull()
                val durationMinutes = duration.text.toString().trim().toIntOrNull()
                val tableNumber = table.text.toString().trim().takeIf { it.isNotBlank() }?.toIntOrNull()
                val parsedDate = runCatching {
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "PT")).apply { isLenient = false }
                        .parse(date.text.toString().trim())
                }.getOrNull()
                when {
                    guestName.isBlank() || guestName.length > 100 -> guest.error = "Indica um nome (máximo 100 caracteres)."
                    partySize == null || partySize !in 1..60 -> party.error = "Usa um número entre 1 e 60."
                    durationMinutes == null || durationMinutes !in 30..480 -> duration.error = "Usa 30 a 480 minutos."
                    parsedDate == null || parsedDate.time < System.currentTimeMillis() -> date.error = "Indica uma data futura válida."
                    table.text.toString().isNotBlank() && (tableNumber == null || tableNumber !in 1..100) -> table.error = "Usa uma mesa entre 1 e 100."
                    notes.text.toString().length > 1000 -> notes.error = "Máximo de 1000 caracteres."
                    else -> {
                        val reservationAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(parsedDate)
                        val normalizedPhone = phone.text.toString().trim()
                        val normalizedNotes = notes.text.toString().trim()
                        val restaurantId = backendRestaurantId()
                        val signature = JSONObject()
                            .put("restaurant_id", restaurantId)
                            .put("guest_name", guestName)
                            .put("guest_phone", normalizedPhone)
                            .put("party_size", partySize)
                            .put("reservation_at", reservationAt)
                            .put("duration_minutes", durationMinutes)
                            .put("table_number", tableNumber ?: JSONObject.NULL)
                            .put("notes", normalizedNotes)
                            .toString()
                        // If the server committed but the reply was lost, the same payload
                        // reuses its UUID; changed form data receives a different UUID.
                        val requestOperation = "reservation_create"
                        val requestAccountId = backendPrefs.getString("user_id", "").orEmpty()
                        val requestId = try {
                            pendingRequestIds.getOrCreate(requestOperation, requestAccountId, signature)
                        } catch (_: Exception) {
                            notes.error = "Não foi possível guardar a referência segura. Tenta novamente."
                            return@setOnClickListener
                        }
                        runBackend({
                            SupabaseApi.createReservation(
                                backendToken(), requestId, restaurantId, guestName,
                                normalizedPhone, partySize, reservationAt, durationMinutes,
                                tableNumber, normalizedNotes
                            )
                            val latest = SupabaseApi.listReservations(backendToken(), restaurantId)
                            reservationCache = latest
                            reservationsLoaded = true
                            "OK"
                        }) {
                            // Close the form only after the server confirmed the save.
                            // If the request fails, runBackend shows the error and the
                            // user's entered reservation details remain available to retry.
                            dialog.dismiss()
                            renderApp()
                            pendingRequestIds.complete(requestOperation, requestAccountId, signature, requestId)
                        }
                    }
                }
            }
        }
        dialog.show()
        dialog.window?.setLayout(-1, dp(590))
    }

    private fun renderStock(container: LinearLayout) {
        pageIntro(container, "Stock e inventário", "Quantidades, níveis mínimos e histórico de movimentos.")
        addGap(container, 8)
        if (!hasRemoteRestaurant()) {
            container.addView(emptyCard("O inventário partilhado exige uma conta autenticada e um restaurante configurado na secção Conta."))
            return
        }
        container.addView(primaryButton("+  Adicionar artigo de stock") { showInventoryItemDialog(null) },
            LinearLayout.LayoutParams(-1, dp(48)))
        addGap(container, 6)
        container.addView(secondaryButton("Atualizar stock") { refreshInventoryItems() },
            LinearLayout.LayoutParams(-1, dp(40)))
        addGap(container, 12)

        if (!backendPrefs.getBoolean("inventory_loaded", false)) {
            container.addView(emptyCard("A carregar o inventário do servidor…"))
            refreshInventoryItems()
            return
        }
        val items = remoteInventoryItems()
        if (items.length() == 0) {
            container.addView(emptyCard("Ainda não há artigos de stock. Adiciona ingredientes, bebidas ou consumíveis e define um limite mínimo."))
            return
        }
        var lowCount = 0
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val id = item.optString("id")
            val name = item.optString("name", "Artigo")
            val unit = item.optString("unit", "unidades")
            val quantity = item.optDouble("quantity", 0.0)
            val threshold = item.optDouble("low_stock_threshold", 0.0)
            val isLow = quantity <= threshold
            if (isLow) lowCount++
            val card = makeCard(15)
            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(label(name, 16f, true, forestDeep), LinearLayout.LayoutParams(0, -2, 1f))
            if (isLow) {
                top.addView(label(if (quantity <= 0.0) "Esgotado" else "Stock baixo", 9.5f, true, red).apply {
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    background = rounded(paleRed, 10)
                })
            }
            card.addView(top)
            addGap(card, 8)
            card.addView(label("${formatQuantity(quantity)} $unit", 22f, true, if (isLow) red else forest))
            card.addView(label("Alerta a ${formatQuantity(threshold)} $unit", 11.5f, false, muted))
            addGap(card, 10)
            card.addView(secondaryButton("Editar artigo e limite") { showInventoryItemDialog(item) },
                LinearLayout.LayoutParams(-1, dp(40)))
            addGap(card, 5)
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(secondaryButton("+ Entrada") { showStockAdjustmentDialog(id, name, unit, true) },
                LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(0, 0, dp(4), 0) })
            buttons.addView(secondaryButton("− Saída") { showStockAdjustmentDialog(id, name, unit, false) },
                LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(4), 0, 0, 0) })
            card.addView(buttons)
            addGap(card, 5)
            card.addView(secondaryButton("Ver histórico de movimentos") { showInventoryMovements(id, name) },
                LinearLayout.LayoutParams(-1, dp(40)))
            container.addView(card, cardParams())
        }
        val summary = makeCard(13, if (lowCount > 0) paleRed else paleGreen)
        summary.addView(label(if (lowCount > 0) "$lowCount artigo(s) no limite mínimo ou abaixo dele" else "Todos os artigos acima dos respetivos limites", 12.5f, true, if (lowCount > 0) red else forest))
        container.addView(summary, cardParams())
    }

    private fun formatQuantity(value: Double): String =
        String.format(Locale("pt", "PT"), "%.3f", value).trimEnd('0').trimEnd(',')

    private fun showInventoryItemDialog(item: JSONObject?) {
        if (!hasRemoteRestaurant()) {
            AlertDialog.Builder(this).setMessage("Liga primeiro o restaurante na secção Conta.")
                .setPositiveButton("OK", null).show()
            return
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(10))
        }
        val name = inputField("Nome do artigo", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES).apply {
            setText(item?.optString("name", "").orEmpty())
        }
        val unit = inputField("Unidade (kg, L, unidades…)", InputType.TYPE_CLASS_TEXT).apply {
            setText(item?.optString("unit", "unidades") ?: "unidades")
        }
        val quantity = inputField("Quantidade atual", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).apply {
            setText(formatQuantity(item?.optDouble("quantity", 0.0) ?: 0.0))
        }
        val threshold = inputField("Avisar quando for igual ou inferior a", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).apply {
            setText(formatQuantity(item?.optDouble("low_stock_threshold", 0.0) ?: 0.0))
        }
        val note = inputField("Motivo (opcional)", InputType.TYPE_CLASS_TEXT).apply { setText(if (item == null) "Stock inicial" else "Correção de inventário") }
        listOf(
            "Nome" to name,
            "Unidade" to unit,
            "Quantidade" to quantity,
            "Limite mínimo" to threshold,
            "Nota do movimento" to note
        ).forEach { (caption, field) ->
            form.addView(label(caption, 11f, true, forestSoft))
            form.addView(field, fieldParams())
            addGap(form, 5)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (item == null) "Novo artigo de stock" else "Editar artigo de stock")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val itemName = name.text.toString().trim()
                val itemUnit = unit.text.toString().trim()
                val qtyText = quantity.text.toString().trim().replace(',', '.')
                val lowText = threshold.text.toString().trim().replace(',', '.')
                val qty = qtyText.toDoubleOrNull()
                val low = lowText.toDoubleOrNull()
                when {
                    itemName.isBlank() || itemName.length > 100 -> name.error = "Indica um nome até 100 caracteres."
                    itemUnit.isBlank() || itemUnit.length > 24 -> unit.error = "Indica uma unidade até 24 caracteres."
                    qty == null || !qty.isFinite() || qty < 0 || qty > 999999999 -> quantity.error = "Quantidade inválida."
                    !StockDecimalValidator.hasAtMostThreeDecimalPlaces(qtyText) -> quantity.error = "Usa no máximo três casas decimais."
                    low == null || !low.isFinite() || low < 0 || low > 999999999 -> threshold.error = "Limite inválido."
                    !StockDecimalValidator.hasAtMostThreeDecimalPlaces(lowText) -> threshold.error = "Usa no máximo três casas decimais."
                    note.text.toString().length > 500 -> note.error = "Máximo de 500 caracteres."
                    else -> {
                        val restaurantId = backendRestaurantId()
                        val itemNote = note.text.toString().trim()
                        val requestOperation = "stock_item_create"
                        val requestAccountId = backendPrefs.getString("user_id", "").orEmpty()
                        val signature = if (item == null) {
                            JSONObject()
                                .put("restaurant_id", restaurantId)
                                .put("name", itemName)
                                .put("unit", itemUnit)
                                .put("quantity", qty)
                                .put("low_stock_threshold", low)
                                .put("note", itemNote)
                                .toString()
                        } else ""
                        val requestId = if (item == null) {
                            try {
                                pendingRequestIds.getOrCreate(requestOperation, requestAccountId, signature)
                            } catch (_: Exception) {
                                name.error = "Não foi possível guardar a referência segura. Tenta novamente."
                                return@setOnClickListener
                            }
                        } else null
                        runBackend({
                            val token = backendToken()
                            if (item == null) {
                                SupabaseApi.createInventoryItem(
                                    token, requireNotNull(requestId), restaurantId,
                                    itemName, itemUnit, qty, low, itemNote
                                )
                            } else {
                                SupabaseApi.saveInventoryItem(
                                    token, restaurantId, item.optString("id"),
                                    itemName, itemUnit, qty, low, itemNote
                                )
                            }
                            val latest = SupabaseApi.listInventoryItems(token, restaurantId)
                            backendPrefs.edit().putString("remote_inventory_json", latest.toString())
                                .putBoolean("inventory_loaded", true).apply()
                            "OK"
                        }) {
                            dialog.dismiss()
                            renderApp()
                            if (requestId != null) {
                                pendingRequestIds.complete(requestOperation, requestAccountId, signature, requestId)
                            }
                        }
                    }
                }
            }
        }
        dialog.show()
        dialog.window?.setLayout(-1, dp(520))
    }

    private fun showStockAdjustmentDialog(itemId: String, itemName: String, unit: String, isInbound: Boolean) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(10))
        }
        val amount = inputField("Quantidade", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val note = inputField("Nota (opcional)", InputType.TYPE_CLASS_TEXT).apply {
            setText(if (isInbound) "Entrada de stock" else "Consumo/quebra")
        }
        form.addView(label("Artigo: $itemName", 12f, true, forest))
        addGap(form, 8)
        form.addView(label("Quantidade a ${if (isInbound) "adicionar" else "retirar"} ($unit)", 11f, true, forestSoft))
        form.addView(amount, fieldParams())
        addGap(form, 5)
        form.addView(label("Regista a razão para manter um histórico útil.", 11f, false, muted))
        form.addView(note, fieldParams())
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (isInbound) "Entrada de stock" else "Saída de stock")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Registar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val rawValue = amount.text.toString().trim().replace(',', '.')
                val value = rawValue.toDoubleOrNull()
                if (value == null || !value.isFinite() || value <= 0 || value > 999999999) {
                    amount.error = "Indica uma quantidade positiva válida."
                } else if (!StockDecimalValidator.hasAtMostThreeDecimalPlaces(rawValue)) {
                    amount.error = "Usa no máximo três casas decimais."
                } else if (note.text.toString().length > 500) {
                    note.error = "Máximo de 500 caracteres."
                } else {
                    val delta = if (isInbound) value else -value
                    val normalizedNote = note.text.toString().trim().take(500)
                    val restaurantId = backendRestaurantId()
                    val signature = JSONObject()
                        .put("restaurant_id", restaurantId)
                        .put("item_id", itemId)
                        .put("delta", delta)
                        .put("note", normalizedNote)
                        .toString()
                    val requestOperation = "stock_adjust"
                    val requestAccountId = backendPrefs.getString("user_id", "").orEmpty()
                    val requestId = try {
                        pendingRequestIds.getOrCreate(requestOperation, requestAccountId, signature)
                    } catch (_: Exception) {
                        amount.error = "Não foi possível guardar a referência segura. Tenta novamente."
                        return@setOnClickListener
                    }
                    runBackend({
                        SupabaseApi.adjustInventoryStock(
                            backendToken(), restaurantId, itemId, delta, normalizedNote, requestId
                        )
                        val latest = SupabaseApi.listInventoryItems(backendToken(), restaurantId)
                        backendPrefs.edit().putString("remote_inventory_json", latest.toString())
                            .putBoolean("inventory_loaded", true).apply()
                        "OK"
                    }) {
                        dialog.dismiss()
                        renderApp()
                        pendingRequestIds.complete(requestOperation, requestAccountId, signature, requestId)
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showInventoryMovements(itemId: String, itemName: String) {
        runBackend({
            SupabaseApi.listInventoryMovements(backendToken(), itemId).toString()
        }) { raw ->
            val rows = JSONArray(raw)
            if (rows.length() == 0) {
                AlertDialog.Builder(this).setTitle("Histórico · $itemName")
                    .setMessage("Ainda não há movimentos registados.")
                    .setPositiveButton("OK", null).show()
                return@runBackend
            }
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "PT"))
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            val message = buildString {
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: continue
                    val delta = row.optDouble("delta", 0.0)
                    val date = runCatching { dateFormat.format(isoFormat.parse(row.optString("created_at")) ?: Date()) }
                        .getOrElse { row.optString("created_at").take(16) }
                    append(date).append(" · ")
                    append(if (delta > 0) "+" else "").append(formatQuantity(delta))
                    append(" → saldo ").append(formatQuantity(row.optDouble("quantity_after", 0.0)))
                    val noteText = row.optString("note").trim()
                    if (noteText.isNotBlank()) append(" · ").append(noteText)
                    if (index < rows.length() - 1) append('\n')
                }
            }
            AlertDialog.Builder(this).setTitle("Histórico · $itemName")
                .setMessage(message)
                .setPositiveButton("Fechar", null)
                .show()
        }
    }

    private fun renderAiAssistant(container: LinearLayout) {
        pageIntro(
            container,
            "Assistente StudioMesa",
            "Motor local especializado para criar rascunhos de carta, publicações e campanhas."
        )
        addGap(container, 8)

        val infoCard = makeCard(17, forestSoft)
        infoCard.addView(label("Motor local ativo · sem chave de API", 15f, true, forest))
        addGap(infoCard, 6)
        infoCard.addView(label(
            "O texto é processado no telemóvel e não é enviado para um fornecedor externo de IA. Esta primeira versão usa regras e modelos de texto especializados; não é um modelo de linguagem treinado. Confirma sempre os factos antes de publicar.",
            12.5f, false, ink
        ))
        container.addView(infoCard, cardParams())
        addGap(container, 8)

        renderAiAssistantForm(container)
    }

    private fun renderAiAssistantForm(container: LinearLayout) {
        val card = makeCard(17)
        card.addView(label("Criar rascunho", 17f, true, forest))
        addGap(card, 5)
        card.addView(label(
            "Escolhe o objetivo e introduz apenas informação confirmada pelo restaurante. Não introduzas dados pessoais de clientes.",
            12.5f, false, muted
        ))
        addGap(card, 9)

        val displayTypes = arrayOf(
            "Descrição de prato",
            "Publicação para redes sociais",
            "Campanha de divulgação",
            "Ideias de conteúdo"
        )
        val requestTypes = listOf("menu_description", "social_post", "campaign", "content_ideas")
        val typePicker = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                displayTypes
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }
        card.addView(label("Tipo de conteúdo", 10f, true, forestSoft))
        card.addView(typePicker, fieldParams())

        val titleField = inputField(
            "Ex.: Hambúrguer da casa",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        ).apply {
            filters = arrayOf(InputFilter.LengthFilter(120))
            isSingleLine = true
        }
        card.addView(label("Assunto", 10f, true, forestSoft))
        card.addView(titleField, fieldParams())

        val factsField = multilineInputField(
            "Ingredientes, características e outros factos confirmados…"
        ).apply {
            filters = arrayOf(InputFilter.LengthFilter(1500))
            minLines = 3
            maxLines = 6
        }
        card.addView(label("Factos confirmados", 10f, true, forestSoft))
        card.addView(factsField, multilineFieldParams())
        addGap(card, 4)

        card.addView(primaryButton("Gerar rascunho") {
            val title = titleField.text.toString().trim()
            val facts = factsField.text.toString().trim()
            when {
                title.length !in 2..120 -> titleField.error = "Usa entre 2 e 120 caracteres."
                facts.length !in 10..1500 -> factsField.error = "Escreve entre 10 e 1500 caracteres."
                else -> {
                    try {
                        val selectedType = requestTypes[
                            typePicker.selectedItemPosition.coerceIn(0, requestTypes.lastIndex)
                        ]
                        val draft = RestaurantContentEngine.generate(selectedType, title, facts)
                        showAiDraftDialog(draft)
                    } catch (error: IllegalArgumentException) {
                        AlertDialog.Builder(this)
                            .setTitle("Revê o texto introduzido")
                            .setMessage(error.message ?: "Não foi possível gerar o rascunho.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(46)))
        container.addView(card, cardParams())
    }

    private fun showAiDraftDialog(draft: String) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(4))
        }
        form.addView(label(
            "O rascunho foi criado localmente com regras e estruturas de texto. Revê-o e confirma todos os factos antes de o usar; nada é publicado automaticamente.",
            12f, false, muted
        ))
        addGap(form, 8)
        val editor = multilineInputField("Rascunho para rever").apply {
            setText(draft)
            minLines = 7
            maxLines = 12
            filters = arrayOf(InputFilter.LengthFilter(4000))
        }
        form.addView(editor, LinearLayout.LayoutParams(-1, dp(220)))

        AlertDialog.Builder(this)
            .setTitle("Rascunho StudioMesa")
            .setView(form)
            .setNegativeButton("Fechar", null)
            .setNeutralButton("Copiar texto") { _, _ ->
                val clipboard = getSystemService(android.content.ClipboardManager::class.java)
                clipboard?.setPrimaryClip(
                    android.content.ClipData.newPlainText("Rascunho StudioMesa", editor.text.toString())
                )
                android.widget.Toast.makeText(
                    this,
                    "Texto copiado. Revê-o antes de o usar.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    private fun renderAnalysis(container: LinearLayout) {
        pageIntro(container, "Análise", if (hasRemoteRestaurant())
            "Indicadores operacionais dos dados partilhados no servidor."
        else "Resumo local dos dados de demonstração.")
        addGap(container, 6)

        if (hasRemoteRestaurant()) {
            val orders = remoteOrders()
            val menu = remoteMenuItems()
            var activeCount = 0
            var completedCount = 0
            var completedValue = 0.0
            for (index in 0 until orders.length()) {
                val order = orders.optJSONObject(index) ?: continue
                when (order.optString("status")) {
                    "Aberto", "Em preparação", "Pronto" -> activeCount++
                    "Concluído" -> {
                        completedCount++
                        completedValue += order.optDouble("amount", 0.0) * order.optInt("quantity", 1).coerceIn(1, 99)
                    }
                }
            }
            val ratios = mutableListOf<Double>()
            for (index in 0 until menu.length()) {
                val item = menu.optJSONObject(index) ?: continue
                val price = item.optDouble("price", 0.0)
                if (price > 0.0) ratios.add(item.optDouble("cost", 0.0) / price * 100.0)
            }
            val avgCostRatio = ratios.average()

            val summary = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            summary.addView(statCard("PEDIDOS NO SERVIDOR", orders.length().toString(), forestSoft),
                LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), dp(10)) })
            summary.addView(statCard("EM CURSO", activeCount.toString(), copper),
                LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), 0, 0, dp(10)) })
            container.addView(summary)

            val card = makeCard(17)
            addMetric(card, "Pedidos concluídos", completedCount.toString())
            addDivider(card)
            addMetric(card, "Valor nominal de pedidos concluídos", money(completedValue))
            addDivider(card)
            addMetric(card, "Artigos ativos na carta", menu.length().toString())
            addDivider(card)
            addMetric(card, "Custo médio dos artigos / preço", if (avgCostRatio.isNaN()) "Sem dados" else String.format(Locale("pt", "PT"), "%.1f%%", avgCostRatio))
            container.addView(card, cardParams())

            addGap(container, 4)
            val note = makeCard(14, copperPale)
            note.addView(label("Indicadores operacionais", 13f, true, forest))
            addGap(note, 5)
            note.addView(label("Os dados são partilhados no servidor. O valor representa pedidos marcados como concluídos, não pagamentos confirmados nem vendas contabilísticas. Ainda não inclui IVA, custos indiretos, desperdício ou integração com caixa.", 12.5f, false, ink))
            container.addView(note, cardParams())
            return
        }

        val orders = store.getOrders()
        val activeOrders = orders.filter { it.status != "Concluído" && it.status != "Cancelado" }
        val completedOrders = orders.filter { it.status == "Concluído" }
        val menu = store.getMenu()
        val valueRecorded = completedOrders.sumOf { it.amount * it.quantity }
        val ratios = menu.filter { it.price > 0.0 }.map { it.cost / it.price * 100.0 }
        val avgCostRatio = ratios.average()
        val summary = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        summary.addView(statCard("PEDIDOS REGISTADOS", orders.size.toString(), forestSoft),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), dp(10)) })
        summary.addView(statCard("EM ABERTO", activeOrders.size.toString(), copper),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), 0, 0, dp(10)) })
        container.addView(summary)

        val card = makeCard(17)
        addMetric(card, "Valor nominal de pedidos concluídos", money(valueRecorded))
        addDivider(card)
        addMetric(card, "Pedidos concluídos", completedOrders.size.toString())
        addDivider(card)
        addMetric(card, "Artigos na carta", menu.size.toString())
        addDivider(card)
        addMetric(card, "Custo médio dos artigos / preço", if (avgCostRatio.isNaN()) "Sem dados" else String.format(Locale("pt", "PT"), "%.1f%%", avgCostRatio))
        container.addView(card, cardParams())

        addGap(container, 4)
        val note = makeCard(14, copperPale)
        note.addView(label("Indicadores de demonstração", 13f, true, forest))
        addGap(note, 5)
        note.addView(label("Os valores representam os registos guardados neste dispositivo. Não são pagamentos confirmados, não incluem IVA nem constituem contabilidade ou relatório financeiro oficial.", 12.5f, false, ink))
        container.addView(note, cardParams())
    }

    private fun showCreateOrderDialog() {
        val cloudMode = hasRemoteRestaurant()
        val items = if (cloudMode) remoteMenuEntries() else store.getMenu()
        if (items.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Carta vazia")
                .setMessage("Adiciona primeiro um artigo à carta e tenta novamente.")
                .setPositiveButton("Abrir carta") { _, _ -> activePage = "Carta"; renderApp() }
                .setNegativeButton("Fechar", null)
                .show()
            return
        }

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(12))
        }
        form.addView(label("Mesa", 10f, true, forestSoft))
        val tableOptions = mutableListOf<Pair<Int, String>>()
        if (cloudMode) {
            val tableData = remoteTables()
            for (index in 0 until tableData.length()) {
                val tableItem = tableData.optJSONObject(index) ?: continue
                val tableNumber = tableItem.optInt("table_number", index + 1)
                val area = tableItem.optString("area", "Sala principal")
                tableOptions.add(tableNumber to ("Mesa " + tableNumber.toString().padStart(2, '0') + " · " + area))
            }
        } else {
            (1..12).forEach { tableOptions.add(it to ("Mesa " + it)) }
        }
        if (tableOptions.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Sem mesas disponíveis")
                .setMessage("Adiciona uma mesa antes de criar um pedido.")
                .setPositiveButton("OK", null).show()
            return
        }
        val tableSpinner = Spinner(this)
        tableSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, tableOptions.map { it.second })
        form.addView(tableSpinner, fieldParams())
        addGap(form, 3)

        form.addView(label("Artigo", 10f, true, forestSoft))
        val itemSpinner = Spinner(this)
        itemSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items.map { it.name })
        form.addView(itemSpinner, fieldParams())
        addGap(form, 3)

        form.addView(label("Quantidade", 10f, true, forestSoft))
        val quantityField = inputField("1–99 unidades", InputType.TYPE_CLASS_NUMBER).apply { setText("1") }
        form.addView(quantityField, fieldParams())
        addGap(form, 3)

        form.addView(label("Momento do serviço", 10f, true, forestSoft))
        val courseOptions = listOf("Bebidas", "Aperitivo", "Entrada", "Principal", "Sobremesa")
        val courseSpinner = Spinner(this)
        courseSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, courseOptions)
        form.addView(courseSpinner, fieldParams())
        addGap(form, 3)

        form.addView(label("Notas para a cozinha (opcional)", 10f, true, forestSoft))
        val notesField = multilineInputField("Ex.: molho à parte, sem sal…")
        form.addView(notesField, multilineFieldParams())
        addGap(form, 3)
        form.addView(label("Os pedidos ficam com o estado «Aberto» até a equipa os encaminhar para preparação.", 11f, false, muted))

        val formScroll = ScrollView(this).apply { addView(form) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Novo pedido")
            .setView(formScroll)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Criar pedido", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val table = tableOptions.getOrNull(tableSpinner.selectedItemPosition)?.first ?: 1
                val selected = items.getOrNull(itemSpinner.selectedItemPosition)
                val quantity = quantityField.text.toString().trim().toIntOrNull()
                val serviceCourse = courseOptions.getOrNull(courseSpinner.selectedItemPosition) ?: "Principal"
                val notes = notesField.text.toString().trim()
                when {
                    selected == null -> itemSpinner.requestFocus()
                    quantity == null || quantity !in 1..99 -> quantityField.error = "Usa uma quantidade entre 1 e 99"
                    notes.length > 500 -> notesField.error = "As notas podem ter no máximo 500 caracteres"
                    else -> {
                        if (cloudMode) {
                            val restaurantId = backendRestaurantId()
                            val signature = JSONObject()
                                .put("restaurant_id", restaurantId)
                                .put("table_number", table)
                                .put("item_name", selected.name)
                                .put("amount", selected.price)
                                .put("quantity", quantity)
                                .put("service_course", serviceCourse)
                                .put("notes", notes)
                                .toString()
                            // Exact retries reuse the same key; edits get a new key.
                            val requestOperation = "order_create"
                            val requestAccountId = backendPrefs.getString("user_id", "").orEmpty()
                            val requestId = try {
                                pendingRequestIds.getOrCreate(requestOperation, requestAccountId, signature)
                            } catch (_: Exception) {
                                quantityField.error = "Não foi possível guardar a referência segura. Tenta novamente."
                                return@setOnClickListener
                            }
                            runBackend({
                                val token = backendToken()
                                SupabaseApi.createOrder(
                                    token, restaurantId, table, selected.name, selected.price,
                                    quantity, serviceCourse, notes, requestId
                                )
                                cacheRemoteOperations(token, restaurantId)
                                "OK"
                            }) {
                                dialog.dismiss()
                                activePage = "Pedidos"
                                renderApp()
                                pendingRequestIds.complete(requestOperation, requestAccountId, signature, requestId)
                            }
                        } else {
                            val now = System.currentTimeMillis()
                            store.addOrder(OrderEntry(
                                now, table, selected.name, selected.price, "Aberto", now,
                                quantity, serviceCourse, notes
                            ))
                            dialog.dismiss()
                            activePage = "Pedidos"
                            renderApp()
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showEditMenuItemDialog(itemId: String?, current: MenuEntry) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(12))
        }
        val nameField = inputField("Nome do artigo", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        val categoryField = inputField("Categoria (ex.: Entradas, Peixe, Sobremesas)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        val descriptionField = multilineInputField("Descrição do prato, ingredientes principais ou preparação")
        val priceField = inputField("Preço de venda (€)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val costField = inputField("Custo por unidade (€)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val allergensField = inputField("Alergénios declarados (se aplicável)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        nameField.setText(current.name)
        categoryField.setText(current.category)
        descriptionField.setText(current.description)
        priceField.setText(String.format(Locale("pt", "PT"), "%.2f", current.price))
        costField.setText(String.format(Locale("pt", "PT"), "%.2f", current.cost))
        allergensField.setText(current.allergens)

        form.addView(label("Nome do artigo", 10f, true, forestSoft))
        form.addView(nameField, fieldParams())
        addGap(form, 2)
        form.addView(label("Categoria", 10f, true, forestSoft))
        form.addView(categoryField, fieldParams())
        addGap(form, 2)
        form.addView(label("Descrição", 10f, true, forestSoft))
        form.addView(descriptionField, multilineFieldParams())
        addGap(form, 2)
        form.addView(label("Preço de venda (€)", 10f, true, forestSoft))
        form.addView(priceField, fieldParams())
        addGap(form, 2)
        form.addView(label("Custo por unidade (€) · interno", 10f, true, forestSoft))
        form.addView(costField, fieldParams())
        addGap(form, 2)
        form.addView(label("Alergénios declarados", 10f, true, forestSoft))
        form.addView(allergensField, fieldParams())
        addGap(form, 5)
        form.addView(label("A informação de alergénios tem de ser confirmada pelo restaurante e pela equipa de cozinha.", 11f, false, muted))

        val formScroll = ScrollView(this).apply { addView(form) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Editar artigo")
            .setView(formScroll)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar alterações", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameField.text.toString().trim()
                val category = categoryField.text.toString().trim()
                val description = descriptionField.text.toString().trim()
                val price = priceField.text.toString().trim().replace(',', '.').toDoubleOrNull()
                val cost = costField.text.toString().trim().replace(',', '.').toDoubleOrNull()
                val allergens = allergensField.text.toString().trim()
                when {
                    name.isBlank() || name.length > 120 -> nameField.error = "Usa entre 1 e 120 caracteres"
                    category.isBlank() || category.length > 60 -> categoryField.error = "Usa entre 1 e 60 caracteres"
                    description.length > 500 -> descriptionField.error = "A descrição pode ter no máximo 500 caracteres"
                    price == null || !price.isFinite() || price < 0.0 -> priceField.error = "Preço inválido"
                    cost == null || !cost.isFinite() || cost < 0.0 -> costField.error = "Custo inválido"
                    allergens.length > 300 -> allergensField.error = "Usa no máximo 300 caracteres"
                    else -> {
                        if (hasRemoteRestaurant() && !itemId.isNullOrBlank()) {
                            val restaurantId = backendRestaurantId()
                            runBackend({
                                val token = backendToken()
                                SupabaseApi.updateMenuItem(token, itemId, name, category, description, price, cost, allergens)
                                cacheRemoteOperations(token, restaurantId)
                                "OK"
                            }) {
                                dialog.dismiss()
                                renderApp()
                            }
                        } else {
                            val items = store.getMenu()
                            val index = items.indexOfFirst { it == current }
                            if (index < 0) {
                                nameField.error = "O artigo já não existe. Atualiza a carta e tenta novamente."
                            } else {
                                items[index] = MenuEntry(name, price, cost, category, description, allergens)
                                store.saveMenu(items)
                                dialog.dismiss()
                                renderApp()
                            }
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showAddMenuItemDialog() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(12))
        }
        val nameField = inputField("Nome do artigo", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        val categoryField = inputField("Categoria (ex.: Entradas, Peixe, Sobremesas)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES).apply {
            setText("Prato principal")
        }
        val descriptionField = multilineInputField("Descrição do prato, ingredientes principais ou preparação")
        val priceField = inputField("Preço de venda (€)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val costField = inputField("Custo por unidade (€)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val allergensField = inputField("Alergénios declarados (se aplicável)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        form.addView(label("Nome do artigo", 10f, true, forestSoft)); form.addView(nameField, fieldParams())
        addGap(form, 2)
        form.addView(label("Categoria", 10f, true, forestSoft)); form.addView(categoryField, fieldParams())
        addGap(form, 2)
        form.addView(label("Descrição", 10f, true, forestSoft)); form.addView(descriptionField, multilineFieldParams())
        addGap(form, 2)
        form.addView(label("Preço de venda (€)", 10f, true, forestSoft)); form.addView(priceField, fieldParams())
        addGap(form, 2)
        form.addView(label("Custo por unidade (€) · interno", 10f, true, forestSoft)); form.addView(costField, fieldParams())
        addGap(form, 2)
        form.addView(label("Alergénios declarados", 10f, true, forestSoft)); form.addView(allergensField, fieldParams())
        addGap(form, 5)
        form.addView(label("Confirma a descrição e os alergénios com a equipa de cozinha antes de publicar a carta.", 11f, false, muted))

        val formScroll = ScrollView(this).apply { addView(form) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Adicionar artigo")
            .setView(formScroll)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameField.text.toString().trim()
                val category = categoryField.text.toString().trim()
                val description = descriptionField.text.toString().trim()
                val price = priceField.text.toString().trim().replace(',', '.').toDoubleOrNull()
                val cost = costField.text.toString().trim().replace(',', '.').toDoubleOrNull()
                val allergens = allergensField.text.toString().trim()
                when {
                    name.isBlank() || name.length > 120 -> nameField.error = "Usa entre 1 e 120 caracteres"
                    category.isBlank() || category.length > 60 -> categoryField.error = "Usa entre 1 e 60 caracteres"
                    description.length > 500 -> descriptionField.error = "A descrição pode ter no máximo 500 caracteres"
                    price == null || !price.isFinite() || price < 0.0 -> priceField.error = "Preço inválido"
                    cost == null || !cost.isFinite() || cost < 0.0 -> costField.error = "Custo inválido"
                    allergens.length > 300 -> allergensField.error = "Usa no máximo 300 caracteres"
                    else -> {
                        if (hasRemoteRestaurant()) {
                            val restaurantId = backendRestaurantId()
                            val signature = JSONObject()
                                .put("restaurant_id", restaurantId)
                                .put("name", name)
                                .put("category", category)
                                .put("description", description)
                                .put("price", price)
                                .put("cost", cost)
                                .put("allergens", allergens)
                                .toString()
                            val requestOperation = "menu_add"
                            val requestAccountId = backendPrefs.getString("user_id", "").orEmpty()
                            val requestId = try {
                                pendingRequestIds.getOrCreate(requestOperation, requestAccountId, signature)
                            } catch (_: Exception) {
                                nameField.error = "Não foi possível guardar a referência segura. Tenta novamente."
                                return@setOnClickListener
                            }
                            runBackend({
                                val token = backendToken()
                                SupabaseApi.addMenuItem(token, restaurantId, requestId, name, category, description, price, cost, allergens)
                                cacheRemoteOperations(token, restaurantId)
                                "OK"
                            }) {
                                dialog.dismiss()
                                renderApp()
                                pendingRequestIds.complete(requestOperation, requestAccountId, signature, requestId)
                            }
                        } else {
                            val menu = store.getMenu()
                            menu.add(MenuEntry(name, price, cost, category, description, allergens))
                            store.saveMenu(menu)
                            dialog.dismiss()
                            renderApp()
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun pageIntro(container: LinearLayout, title: String, subtitle: String) {
        container.addView(label("Gestão de restaurante", 10.5f, true, forestSoft))
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
        parent.addView(label(title, 10.5f, true, muted))
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
            background = rounded(color, 19)
            elevation = dp(1).toFloat()
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
            background = rounded(white, 12)
            isSingleLine = true
        }

    private fun multilineInputField(hintText: String): EditText =
        EditText(this).apply {
            hint = hintText
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            textSize = 14f
            setTextColor(ink)
            setHintTextColor(muted)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(white, 12)
            minLines = 2
            maxLines = 3
            isSingleLine = false
            gravity = Gravity.TOP or Gravity.START
        }

    private fun multilineFieldParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, dp(76)).apply { setMargins(0, dp(5), 0, dp(8)) }

    private fun primaryButton(title: String, action: () -> Unit): Button =
        Button(this).apply {
            text = title
            textSize = 13f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            isAllCaps = false
            setTextColor(forestDeep)
            background = rounded(copper, 15)
            elevation = dp(1).toFloat()
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
            background = rounded(paleGreen, 14)
            elevation = 0f
            setPadding(dp(8), dp(2), dp(8), dp(2))
            setOnClickListener { action() }
        }

    private fun label(value: String, size: Float, bold: Boolean, color: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create(if (size >= 23f) "serif" else "sans-serif", Typeface.BOLD)
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
