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
import android.os.Handler
import android.os.Looper
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
    private var customerMenuSearch = ""
    private var selectedCustomerMenuCategory = "Todas"
    private var managementMenuSearch = ""
    private var selectedManagementMenuCategory = "Todas"
    private var inventorySearch = ""
    private var selectedInventoryFilter = "Todos"

    private var activityResumed = false
    private var kitchenAutoRefreshScheduled = false
    private var kitchenRefreshInFlight = false
    private var reservationAutoRefreshScheduled = false
    private var reservationRefreshInFlight = false
    private var reservationPageContainer: LinearLayout? = null
    private val reservationRefreshHandler = Handler(Looper.getMainLooper())
    private val reservationRefreshRunnable = object : Runnable {
        override fun run() {
            reservationAutoRefreshScheduled = false
            if (!shouldRunReservationAutoRefresh()) return
            refreshReservationsSilently()
            scheduleNextReservationAutoRefresh()
        }
    }
    private var kitchenOrdersCache: JSONArray? = null
    private var kitchenQueueUpdatedAtMillis = 0L
    private var kitchenQueueRefreshFailed = false
    private var kitchenPageContainer: LinearLayout? = null
    private var inventoryAutoRefreshScheduled = false
    private var inventoryRefreshInFlight = false
    private var inventoryUpdatedAtMillis = 0L
    private var inventoryRefreshFailed = false
    private var inventoryPageContainer: LinearLayout? = null
    // Purchase costs are never persisted in the ordinary inventory cache.
    private val inventoryUnitCostCache = mutableMapOf<String, BigDecimal?>()
    private var inventoryUnitCostCacheScope: String? = null
    private var inventoryUnitCostCacheLoaded = false
    private val inventoryRefreshHandler = Handler(Looper.getMainLooper())
    private val inventoryRefreshRunnable = object : Runnable {
        override fun run() {
            inventoryAutoRefreshScheduled = false
            if (!shouldRunInventoryAutoRefresh()) return
            refreshInventoryItemsSilently()
            scheduleNextInventoryAutoRefresh()
        }
    }
    private val kitchenRefreshHandler = Handler(Looper.getMainLooper())
    private val kitchenRefreshRunnable = object : Runnable {
        override fun run() {
            kitchenAutoRefreshScheduled = false
            if (!shouldRunKitchenAutoRefresh()) return
            refreshKitchenQueueSilently()
            scheduleNextKitchenAutoRefresh()
        }
    }
    private var assistantQuestion = ""
    private var assistantInsightsExpanded = false
    private var assistantAnswer: StudioMesaAssistantAnswer? = null
    private data class AssistantChatTurn(
        val question: String,
        val answer: StudioMesaAssistantAnswer
    )
    private val assistantConversation = mutableListOf<AssistantChatTurn>()
    private var selectedAnalysisPeriod = SalesAnalysisPeriod.ALL_TIME
    private var selectedOrderFilter = "Todos"
    private var selectedKitchenFilter = "Todos"
    private var selectedTableFilter = "Todas"
    private var customerSearchQuery = ""

    // Owner-controlled economic tools: session-only until persistent authorization is designed.
    private var economicManagerEnabled = false
    private var economicScopeKey: String? = null
    private val economicInputs = mutableMapOf<String, String>()
    private var economicReportText = ""
    private var scenarioReportText = ""
    private var breakEvenTargetReportText = ""
    private var recipeReportText = ""
    private var reorderReportText = ""
    private var wasteReportText = ""
    private var supplierReportText = ""
    private var budgetReportText = ""
    private var usageReportText = ""
    private var labourReportText = ""
    private val backendPrefs by lazy { getSharedPreferences("studiomesa_backend", MODE_PRIVATE) }
    private val secureSession by lazy { SecureSessionStore(this) }
    private val pendingRequestIds by lazy { PendingRequestIds(this) }
    // Reservation contacts are kept in memory only, not persisted to SharedPreferences.
    private var reservationCache = JSONArray()
    private var reservationsLoaded = false
    private var selectedReservationFilter = "Todas"
    private var reservationSearchQuery = ""
    private var customerReservationsCache = JSONArray()
    private var customerReservationsLoaded = false
    private var customerReservationsLoading = false
    private var customerReservationsLoadError = false
    private var selectedCustomerReservationFilter = "Todas"
    private var customerReservationSearchQuery = ""

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

    override fun onResume() {
        super.onResume()
        activityResumed = true
        updateKitchenAutoRefresh()
        updateInventoryAutoRefresh()
        updateReservationAutoRefresh()
    }

    override fun onPause() {
        activityResumed = false
        stopKitchenAutoRefresh()
        stopInventoryAutoRefresh()
        stopReservationAutoRefresh()
        super.onPause()
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
        if (!hasRemoteRestaurant() || backendPrefs.getString("restaurant_role", "").orEmpty() == "owner") {
            pages.add("Gestão económica")
        }
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
        if (isCustomerApp) {
            showCustomerNavigationDrawer()
            return
        }
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
            "GESTÃO DO RESTAURANTE" to listOf("Carta", "Pontos", "Reservas", "Preço Justo", "Gestão económica", "Stock", "Análise"),
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

    private fun showCustomerNavigationDrawer() {
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
        titleStack.addView(label(displayCustomerRestaurantName(), 14f, true, copperPale).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        titleStack.addView(label("ÁREA DE CLIENTE · " + planTitle, 10.5f, false, copperPale))
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

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(12))
        }
        list.addView(label("A MINHA EXPERIÊNCIA", 10f, true, forestSoft).apply {
            setPadding(dp(20), dp(13), dp(14), dp(7))
            letterSpacing = 0.08f
        }, LinearLayout.LayoutParams(-1, -2))
        val customerPages = if (hasCalculator) listOf("Cartão", "Carta", "Reservas", "Conta") else listOf("Cartão", "Carta", "Conta")
        customerPages.forEach { page ->
            val selected = activeCustomerPage == page
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
                    activeCustomerPage = page
                    renderCustomerApp()
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
        drawerRoot.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        drawerRoot.addView(View(this).apply { setBackgroundColor(line) },
            LinearLayout.LayoutParams(-1, dp(1)))
        drawerRoot.addView(label("StudioMesa · Cartão de cliente", 10.5f, false, muted).apply {
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
        "Gestão económica" -> "∑"
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

        if (activePage == "Assistente") {
            appRoot.addView(renderAssistantComposer(), LinearLayout.LayoutParams(-1, -2))
            if (assistantConversation.isNotEmpty()) {
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }
        } else {
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
                nav.addView(
                    bottomNavigationItem(page, activePage == page) {
                        activePage = page
                        renderApp()
                    },
                    LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                        setMargins(dp(3), 0, dp(3), 0)
                    }
                )
            }
            appRoot.addView(nav, LinearLayout.LayoutParams(-1, dp(60)))
        }
        setContentView(appRoot)
        updateKitchenAutoRefresh()
        updateInventoryAutoRefresh()
        updateReservationAutoRefresh()
    }

    private fun renderPage(container: LinearLayout) {
        if (activePage != "Cozinha") kitchenPageContainer = null
        if (activePage != "Reservas") reservationPageContainer = null
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
            "Gestão económica" -> renderEconomicDashboard(container)
            else -> renderHome(container)
        }
    }

    private fun renderHome(container: LinearLayout) {
        val remote = hasRemoteRestaurant()
        container.addView(label("Painel de operação", 10f, true, forestSoft))
        addGap(container, 5)
        container.addView(label("A sala, num relance.", 27f, true, forestDeep))
        addGap(container, 5)
        container.addView(label(
            if (remote)
                "Resumo da operação partilhada. Os indicadores usam os últimos dados recebidos do servidor."
            else
                "Explora o painel com dados de exemplo. Liga um restaurante na secção Conta para partilhar a operação com a equipa.",
            14f, false, muted
        ))
        if (remote) {
            val lastSync = backendPrefs.getLong("remote_operations_updated_at", 0L)
            val syncLabel = if (lastSync > 0L) {
                "Última sincronização concluída às " +
                    SimpleDateFormat("HH:mm", Locale("pt", "PT")).format(Date(lastSync))
            } else {
                "Ainda não existe uma sincronização concluída neste dispositivo."
            }
            container.addView(label(syncLabel, 10.5f, true, forestSoft))
        }
        addGap(container, 18)

        val tableTotal: Int
        val occupied = if (remote) {
            val tables = remoteTables()
            tableTotal = tables.length()
            (0 until tables.length()).count { tables.optJSONObject(it)?.optBoolean("is_occupied", false) == true }
        } else {
            tableTotal = 12
            store.occupiedTableCount()
        }
        val orderStatuses = if (remote) {
            val orders = remoteOrders()
            (0 until orders.length()).mapNotNull { orders.optJSONObject(it)?.optString("status") }
        } else {
            store.getOrders().map { it.status }
        }
        val activeOrders = orderStatuses.count { it !in setOf("Concluído", "Cancelado") }
        val preparingOrders = orderStatuses.count { it == "Em preparação" }
        val availableTables = (tableTotal - occupied).coerceAtLeast(0)

        val firstStats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        firstStats.addView(
            statCard("MESAS OCUPADAS", "$occupied / $tableTotal", if (occupied > 0) copper else forestSoft),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), 0) }
        )
        firstStats.addView(
            statCard("PEDIDOS ATIVOS", activeOrders.toString(), if (activeOrders > 0) copper else forestSoft),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), 0, 0, 0) }
        )
        container.addView(firstStats)

        addGap(container, 8)
        val secondStats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        secondStats.addView(
            statCard("MESAS LIVRES", availableTables.toString(), if (availableTables > 0) green else muted),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), 0) }
        )
        secondStats.addView(
            statCard("EM PREPARAÇÃO", preparingOrders.toString(), if (preparingOrders > 0) copper else forestSoft),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), 0, 0, 0) }
        )
        container.addView(secondStats)

        addGap(container, 18)
        sectionHeading(container, "Estado do serviço", "O que merece atenção neste momento.")
        val serviceCard = makeCard(15, if (activeOrders > 0) copperPale else white)
        serviceCard.addView(label(
            when {
                activeOrders > 0 && preparingOrders > 0 ->
                    "$activeOrders pedido(s) continuam ativos; $preparingOrders estão em preparação."
                activeOrders > 0 ->
                    "$activeOrders pedido(s) continuam ativos. Confirma os estados antes de fechar o serviço."
                occupied > 0 ->
                    "Existem $occupied mesa(s) ocupadas e não há pedidos ativos nos dados disponíveis."
                else ->
                    "Não existem mesas ocupadas nem pedidos ativos nos dados disponíveis."
            },
            13f, true, forestDeep
        ))
        addGap(serviceCard, 10)
        val serviceActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        serviceActions.addView(
            secondaryButton("Ver pedidos") {
                activePage = "Pedidos"
                renderApp()
            },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(0, 0, dp(5), 0) }
        )
        serviceActions.addView(
            secondaryButton("Abrir cozinha") {
                activePage = "Cozinha"
                renderApp()
            },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(5), 0, 0, 0) }
        )
        serviceCard.addView(serviceActions)
        container.addView(serviceCard, cardParams())

        if (remote) {
            container.addView(
                secondaryButton("Atualizar dados do painel") { refreshRemoteOperations() },
                LinearLayout.LayoutParams(-1, dp(46)).apply { setMargins(0, 0, 0, dp(14)) }
            )
        }

        sectionHeading(container, "Acesso rápido", "As principais áreas do restaurante.")

        val quickActions = mutableListOf<Triple<String, String, String>>()
        quickActions += Triple("Mesas", "Mesas do restaurante", "$tableTotal mesas disponíveis para consultar e gerir.")
        quickActions += Triple("Pedidos", "Pedidos e serviço", "Regista pedidos e acompanha cada estado.")
        quickActions += Triple("Carta", "Carta e custos", if (remote) "Gere artigos, preços e custos internos." else "Artigos de exemplo, preços e custos.")
        quickActions += Triple("Pontos", "Fidelização", "Consulta clientes e gere os respetivos pontos.")
        if (hasCalculator) {
            quickActions += Triple("Reservas", "Reservas", "Organiza pedidos e confirmações.")
            quickActions += Triple("Preço Justo", "Preço Justo", "Calcula preços indicativos a partir dos custos.")
        }
        if (isPremium) {
            quickActions += Triple("Stock", "Stock e inventário", "Controla quantidades, alertas e movimentos.")
            quickActions += Triple("Análise", "Análise operacional", "Consulta pedidos, artigos e indicadores.")
        }
        if ("Gestão económica" in availablePages()) {
            quickActions += Triple("Gestão económica", "Gestão económica", "Simula cenários e calcula metas financeiras.")
        }
        quickActions += Triple("Assistente", "Assistente StudioMesa", "Analisa a operação e sugere pontos a rever.")

        for (index in quickActions.indices step 2) {
            val first = quickActions[index]
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }
            row.addView(
                homeActionTile(first.second, first.third, first.first),
                LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), dp(10)) }
            )
            if (index + 1 < quickActions.size) {
                val second = quickActions[index + 1]
                row.addView(
                    homeActionTile(second.second, second.third, second.first),
                    LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), 0, 0, dp(10)) }
                )
            } else {
                row.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))
            }
            container.addView(row, LinearLayout.LayoutParams(-1, -2))
        }

        addGap(container, 6)
        val note = makeCard(14, copperPale)
        note.addView(label(if (remote) "OPERAÇÃO PARTILHADA" else "MODO DE DEMONSTRAÇÃO", 10f, true, forest))
        addGap(note, 4)
        note.addView(label(
            if (remote)
                "Mesas, carta e pedidos são guardados no servidor e partilhados com a equipa autenticada. Os indicadores deste painel não são atualizados em tempo real. Não existe integração com caixa registadora, pagamentos ou impressora de cozinha."
            else
                "O modo de demonstração guarda mesas, carta e pedidos apenas neste telemóvel. Liga uma conta e configura o restaurante para usar os dados partilhados no servidor.",
            12.5f, false, ink
        ))
        container.addView(note, cardParams())
    }

    private fun addTableFilters(container: LinearLayout) {
        container.addView(label("FILTRAR MESAS", 9.5f, true, forestSoft))
        addGap(container, 5)
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listOf("Todas", "Livres", "Ocupadas").forEach { filter ->
            row.addView(
                filterChip(filter, selectedTableFilter == filter) {
                    selectedTableFilter = filter
                    renderApp()
                },
                LinearLayout.LayoutParams(-2, dp(42)).apply {
                    setMargins(0, 0, dp(6), 0)
                }
            )
        }
        scroll.addView(row)
        container.addView(scroll, LinearLayout.LayoutParams(-1, dp(46)))
        addGap(container, 5)
    }

    private fun tableMatchesSelectedFilter(occupied: Boolean): Boolean = when (selectedTableFilter) {
        "Livres" -> !occupied
        "Ocupadas" -> occupied
        else -> true
    }

    private fun renderTables(container: LinearLayout) {
        pageIntro(container, "Mesas", if (hasRemoteRestaurant())
            "Estado, zona e capacidade partilhados pela equipa."
        else "Toca numa mesa para alternar entre livre e ocupada.")
        addGap(container, 8)

        if (hasRemoteRestaurant()) {
            addActionPair(
                container,
                "+  Adicionar mesa",
                { showAddTableDialog() },
                "Atualizar",
                { refreshRemoteOperations() }
            )
            addGap(container, 10)

            val tableData = remoteTables()
            val validTableIndices = (0 until tableData.length()).filter {
                tableData.optJSONObject(it) != null
            }
            if (validTableIndices.isEmpty()) {
                container.addView(emptyCard("Ainda não existem mesas sincronizadas. Atualiza os dados ou volta a iniciar sessão."))
                return
            }

            val occupiedTables = validTableIndices.count {
                tableData.optJSONObject(it)?.optBoolean("is_occupied", false) == true
            }
            val tableOverview = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            tableOverview.addView(statCard("MESAS LIVRES", (validTableIndices.size - occupiedTables).toString(), green),
                LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), dp(10)) })
            tableOverview.addView(statCard("MESAS OCUPADAS", occupiedTables.toString(), if (occupiedTables > 0) copper else forestSoft),
                LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), 0, 0, dp(10)) })
            container.addView(tableOverview)
            addTableFilters(container)
            val visibleTableIndices = validTableIndices.filter { index ->
                val occupied = tableData.optJSONObject(index)?.optBoolean("is_occupied", false) == true
                tableMatchesSelectedFilter(occupied)
            }
            container.addView(label("${visibleTableIndices.size} mesa(s) neste filtro", 11f, true, forestSoft))
            addGap(container, 6)
            if (visibleTableIndices.isEmpty()) {
                container.addView(emptyCard("Não há mesas neste estado. Escolhe outro filtro para consultar a sala."))
                return
            }

            for (position in visibleTableIndices.indices step 2) {
                val firstIndex = visibleTableIndices[position]
                val first = tableData.optJSONObject(firstIndex) ?: continue
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                row.addView(remoteTableCard(
                    first.optString("id"),
                    first.optInt("table_number", firstIndex + 1),
                    first.optBoolean("is_occupied", false),
                    first.optString("area", "Sala principal"),
                    first.optInt("seats", 2)
                ), LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins(0, 0, dp(6), dp(10))
                })
                if (position + 1 < visibleTableIndices.size) {
                    val secondIndex = visibleTableIndices[position + 1]
                    val second = tableData.optJSONObject(secondIndex)
                    if (second != null) {
                        row.addView(remoteTableCard(
                            second.optString("id"),
                            second.optInt("table_number", secondIndex + 1),
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

        addGap(container, 8)
        val localOccupiedTables = store.occupiedTableCount()
        val localTableOverview = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        localTableOverview.addView(statCard("MESAS LIVRES", (12 - localOccupiedTables).toString(), green),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), dp(10)) })
        localTableOverview.addView(statCard("MESAS OCUPADAS", localOccupiedTables.toString(), if (localOccupiedTables > 0) copper else forestSoft),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), 0, 0, dp(10)) })
        container.addView(localTableOverview)
        addTableFilters(container)
        val visibleTables = (1..12).filter { table ->
            tableMatchesSelectedFilter(store.isTableOccupied(table))
        }
        container.addView(label("${visibleTables.size} mesa(s) neste filtro", 11f, true, forestSoft))
        addGap(container, 6)
        if (visibleTables.isEmpty()) {
            container.addView(emptyCard("Não há mesas neste estado. Escolhe outro filtro para consultar a sala."))
            return
        }
        for (position in visibleTables.indices step 2) {
            val table = visibleTables[position]
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(tableCard(table), LinearLayout.LayoutParams(0, -2, 1f).apply {
                setMargins(0, 0, dp(6), dp(10))
            })
            if (position + 1 < visibleTables.size) {
                row.addView(tableCard(visibleTables[position + 1]), LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins(dp(6), 0, 0, dp(10))
                })
            } else {
                row.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))
            }
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
            addActionPair(container, "+  Novo pedido", { showCreateOrderDialog() }, "Atualizar", { refreshRemoteOperations() })
        } else {
            addActionPair(container, "+  Novo pedido", { showCreateOrderDialog() })
        }
        addGap(container, 15)

        if (hasRemoteRestaurant()) {
            val orders = remoteOrders()
            val remoteStatuses = (0 until orders.length()).mapNotNull {
                orders.optJSONObject(it)?.optString("status", "Aberto")
            }
            addOrderStatusOverview(container, remoteStatuses)
            if (orders.length() == 0) {
                container.addView(emptyCard("Ainda não há pedidos registados no servidor. Cria o primeiro pedido para testar o fluxo."))
                return
            }
            addOrderFilters(container)
            val filteredOrders = (0 until orders.length())
                .mapNotNull { orders.optJSONObject(it) }
                .filter { orderMatchesSelectedFilter(it.optString("status", "Aberto")) }
            container.addView(label("${filteredOrders.size} pedido(s) neste filtro", 11f, true, forestSoft))
            addGap(container, 6)
            if (filteredOrders.isEmpty()) {
                container.addView(emptyCard("Não existem pedidos neste estado. Escolhe outro filtro para consultar os restantes."))
                return
            }
            for (order in filteredOrders) {
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
                top.addView(statusBadge(
                    status.uppercase(Locale("pt", "PT")),
                    when (status) {
                        "Concluído" -> green
                        "Cancelado" -> muted
                        else -> forestSoft
                    },
                    when (status) {
                        "Concluído" -> paleGreen
                        "Cancelado" -> paper
                        else -> copperPale
                    },
                    9f
                ))
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
                addGap(card, 6)
                if (!closed) {
                    val actions = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    actions.addView(secondaryButton("Cancelar") {
                        confirmCancelOrder(
                            order.optString("id"),
                            order.optInt("table_number", 1),
                            order.optString("item_name", "Artigo")
                        )
                    }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                        setMargins(0, 0, dp(4), 0)
                    })
                    actions.addView(secondaryButton("Ver histórico") {
                        showOrderHistory(order.optString("id"))
                    }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                        setMargins(dp(4), 0, 0, 0)
                    })
                    card.addView(actions)
                } else {
                    card.addView(secondaryButton("Ver histórico") {
                        showOrderHistory(order.optString("id"))
                    }, LinearLayout.LayoutParams(-1, dp(44)))
                }
                container.addView(card, cardParams())
            }
            return
        }

        val orders = store.getOrders()
        addOrderStatusOverview(container, orders.map { it.status })
        if (orders.isEmpty()) {
            container.addView(emptyCard("Ainda não há pedidos registados. Cria um pedido de demonstração para experimentar o fluxo."))
            return
        }
        addOrderFilters(container)
        val filteredOrders = orders.filter { orderMatchesSelectedFilter(it.status) }
        container.addView(label("${filteredOrders.size} pedido(s) neste filtro", 11f, true, forestSoft))
        addGap(container, 6)
        if (filteredOrders.isEmpty()) {
            container.addView(emptyCard("Não existem pedidos neste estado. Escolhe outro filtro para consultar os restantes."))
            return
        }

        filteredOrders.forEach { order ->
            val done = order.status == "Concluído"
            val card = makeCard(16)
            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(label("Mesa " + order.table.toString().padStart(2, '0'), 10f, true, forestSoft), LinearLayout.LayoutParams(0, -2, 1f))
            top.addView(statusBadge(
                if (done) "CONCLUÍDO" else "EM ABERTO",
                if (done) green else forestSoft,
                if (done) paleGreen else copperPale,
                9f
            ))
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

    private fun addOrderFilters(container: LinearLayout) {
        container.addView(label("FILTRAR PEDIDOS", 9.5f, true, forestSoft))
        addGap(container, 5)
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listOf("Todos", "Ativos", "Concluídos", "Cancelados").forEach { filter ->
            row.addView(
                filterChip(filter, selectedOrderFilter == filter) {
                    selectedOrderFilter = filter
                    renderApp()
                },
                LinearLayout.LayoutParams(-2, dp(42)).apply {
                    setMargins(0, 0, dp(6), 0)
                }
            )
        }
        scroll.addView(row)
        container.addView(scroll, LinearLayout.LayoutParams(-1, dp(46)))
        addGap(container, 5)
    }

    private fun orderMatchesSelectedFilter(status: String): Boolean = when (selectedOrderFilter) {
        "Ativos" -> status !in setOf("Concluído", "Cancelado")
        "Concluídos" -> status == "Concluído"
        "Cancelados" -> status == "Cancelado"
        else -> true
    }

    private fun addKitchenFilters(container: LinearLayout) {
        container.addView(label("FILTRAR FILA", 9.5f, true, forestSoft))
        addGap(container, 5)
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listOf("Todos", "Novos", "Em preparação", "Prontos").forEach { filter ->
            row.addView(
                filterChip(filter, selectedKitchenFilter == filter) {
                    selectedKitchenFilter = filter
                    renderApp()
                },
                LinearLayout.LayoutParams(-2, dp(42)).apply {
                    setMargins(0, 0, dp(6), 0)
                }
            )
        }
        scroll.addView(row)
        container.addView(scroll, LinearLayout.LayoutParams(-1, dp(46)))
        addGap(container, 5)
    }

    private fun kitchenMatchesSelectedFilter(status: String): Boolean = when (selectedKitchenFilter) {
        "Novos" -> status == "Aberto"
        "Em preparação" -> status == "Em preparação"
        "Prontos" -> status == "Pronto"
        else -> true
    }

    private fun renderKitchen(container: LinearLayout) {
        kitchenPageContainer = container
        pageIntro(container, "Cozinha", "Fila de preparação por estado e tempo de espera.")
        addGap(container, 8)
        if (hasRemoteRestaurant()) {
            container.addView(secondaryButton("Atualizar fila agora") { refreshKitchenQueueSilently() },
                LinearLayout.LayoutParams(-1, dp(48)))
            addGap(container, 6)
            container.addView(label(kitchenQueueStatusMessage(), 11f, false,
                if (kitchenQueueRefreshFailed) red else muted))
        } else {
            container.addView(label("Modo de demonstração · os pedidos não são partilhados com outros dispositivos.", 11f, false, muted))
        }
        addGap(container, 12)

        if (hasRemoteRestaurant()) {
            val orders = kitchenOrdersCache ?: remoteOrders()
            val allStatuses = (0 until orders.length()).mapNotNull {
                orders.optJSONObject(it)?.optString("status", "Aberto")
            }
            addOrderStatusOverview(container, allStatuses)
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
                container.addView(emptyCard("A fila está vazia. Os novos pedidos de dispositivos ligados aparecerão automaticamente até 15 segundos depois de serem criados."))
                return
            }
            addKitchenFilters(container)
            val filteredActive = active.filter { kitchenMatchesSelectedFilter(it.optString("status", "Aberto")) }
            container.addView(label("${filteredActive.size} pedido(s) nesta fila", 11f, true, forestSoft))
            addGap(container, 6)
            if (filteredActive.isEmpty()) {
                container.addView(emptyCard("Não há pedidos neste estado. Escolhe outro filtro para consultar a fila."))
                return
            }
            for (order in filteredActive) {
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
                    }) {
                        kitchenOrdersCache = null
                        kitchenQueueUpdatedAtMillis = System.currentTimeMillis()
                        kitchenQueueRefreshFailed = false
                        refreshKitchenPageContent()
                    }
                }
            }
        } else {
            val allLocalOrders = store.getOrders()
            addOrderStatusOverview(container, allLocalOrders.map { it.status })
            val active = allLocalOrders
                .filter { it.status !in setOf("Concluído", "Cancelado") }
                .sortedWith(compareBy(
                    { order -> when (order.status) {
                        "Aberto" -> 0
                        "Em preparação" -> 1
                        "Pronto" -> 2
                        else -> 3
                    } },
                    { order -> order.createdAt }
                ))
            if (active.isEmpty()) {
                container.addView(emptyCard("A fila está vazia. Cria um pedido para testar o fluxo da cozinha."))
                return
            }
            addKitchenFilters(container)
            val filteredActive = active.filter { kitchenMatchesSelectedFilter(it.status) }
            container.addView(label("${filteredActive.size} pedido(s) nesta fila", 11f, true, forestSoft))
            addGap(container, 6)
            if (filteredActive.isEmpty()) {
                container.addView(emptyCard("Não há pedidos neste estado. Escolhe outro filtro para consultar a fila."))
                return
            }
            filteredActive.forEach { order ->
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

    private fun refreshKitchenPageContent() {
        if (activePage != "Cozinha") return
        val container = kitchenPageContainer ?: return
        val scroll = container.parent as? ScrollView
        val previousScrollY = scroll?.scrollY ?: 0
        container.removeAllViews()
        renderKitchen(container)
        scroll?.post {
            val maxScroll = (container.height - (scroll?.height ?: 0)).coerceAtLeast(0)
            scroll?.scrollTo(0, previousScrollY.coerceAtMost(maxScroll))
        }
    }

    private fun kitchenQueueStatusMessage(): String {
        if (kitchenRefreshInFlight) return "A atualizar a fila do servidor…"
        if (kitchenQueueRefreshFailed) {
            return if (kitchenQueueUpdatedAtMillis > 0L) {
                "Falha na atualização automática. A mostrar os dados recebidos às " +
                    SimpleDateFormat("HH:mm:ss", Locale("pt", "PT")).format(Date(kitchenQueueUpdatedAtMillis)) + "."
            } else {
                "Não foi possível atualizar a fila. Tenta novamente manualmente."
            }
        }
        return if (kitchenQueueUpdatedAtMillis > 0L) {
            "Atualizada às " +
                SimpleDateFormat("HH:mm:ss", Locale("pt", "PT")).format(Date(kitchenQueueUpdatedAtMillis)) +
                " · atualização automática a cada 15 segundos."
        } else {
            "Atualização automática a cada 15 segundos enquanto esta secção estiver aberta."
        }
    }

    private fun shouldRunKitchenAutoRefresh(): Boolean =
        KitchenQueueRefreshPolicy.shouldScheduleAutoRefresh(
            activityResumed = activityResumed,
            isCustomerApp = isCustomerApp,
            activePage = activePage,
            hasRemoteRestaurant = hasRemoteRestaurant()
        )

    private fun updateKitchenAutoRefresh() {
        if (!shouldRunKitchenAutoRefresh()) {
            stopKitchenAutoRefresh()
            return
        }
        if (!kitchenAutoRefreshScheduled) {
            refreshKitchenQueueSilently()
            scheduleNextKitchenAutoRefresh()
        }
    }

    private fun scheduleNextKitchenAutoRefresh() {
        if (!shouldRunKitchenAutoRefresh()) {
            stopKitchenAutoRefresh()
            return
        }
        if (!kitchenAutoRefreshScheduled) {
            kitchenAutoRefreshScheduled = true
            kitchenRefreshHandler.postDelayed(
                kitchenRefreshRunnable,
                KitchenQueueRefreshPolicy.REFRESH_INTERVAL_MILLIS
            )
        }
    }

    private fun stopKitchenAutoRefresh() {
        kitchenRefreshHandler.removeCallbacks(kitchenRefreshRunnable)
        kitchenAutoRefreshScheduled = false
    }

    private fun refreshKitchenQueueSilently() {
        val restaurantId = backendRestaurantId()
        if (restaurantId.isBlank() ||
            !KitchenQueueRefreshPolicy.mayStartRefresh(
                activityResumed = activityResumed,
                isCustomerApp = isCustomerApp,
                activePage = activePage,
                hasRemoteRestaurant = hasRemoteRestaurant(),
                refreshInFlight = kitchenRefreshInFlight
            )
        ) return

        kitchenRefreshInFlight = true
        Thread({
            var loadedOrders: JSONArray? = null
            try {
                refreshSessionIfNeeded()
                val token = backendToken()
                if (token.isBlank() || backendRestaurantId() != restaurantId) {
                    throw IllegalStateException("A sessão ou o restaurante já não estão disponíveis.")
                }
                loadedOrders = SupabaseApi.listActiveKitchenOrders(token, restaurantId)
            } catch (_: Exception) {
                // Keep the last verified queue visible and show a non-blocking refresh warning.
            }

            runOnUiThread {
                kitchenRefreshInFlight = false
                if (isFinishing || backendRestaurantId() != restaurantId || !hasRemoteRestaurant()) return@runOnUiThread

                val freshOrders = loadedOrders
                if (freshOrders != null) {
                    kitchenOrdersCache = freshOrders
                    kitchenQueueUpdatedAtMillis = System.currentTimeMillis()
                    kitchenQueueRefreshFailed = false
                } else {
                    kitchenQueueRefreshFailed = true
                }

                if (activityResumed && activePage == "Cozinha") {
                    refreshKitchenPageContent()
                }
            }
        }, "StudioMesa-kitchen-refresh").start()
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
        top.addView(statusBadge(
            status.uppercase(Locale("pt", "PT")),
            statusColor,
            statusBackground,
            10f
        ))
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

    private fun addManagementMenuFilters(container: LinearLayout, rawCategories: List<String>) {
        val categories = listOf("Todas") + rawCategories
            .map { it.trim().ifBlank { "Outros" } }
            .filter { it != "Todas" }
            .distinct()
            .sorted()
        if (selectedManagementMenuCategory !in categories) {
            selectedManagementMenuCategory = "Todas"
        }

        val filterCard = makeCard(13)
        filterCard.addView(label("PESQUISAR E FILTRAR", 9.5f, true, forestSoft))
        addGap(filterCard, 6)
        val searchField = inputField(
            "Nome, categoria, descrição ou alergénios",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        ).apply {
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                    managementMenuSearch = text.toString().trim()
                    renderApp()
                    true
                } else {
                    false
                }
            }
        }
        searchField.setText(managementMenuSearch)
        filterCard.addView(searchField, fieldParams())
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(primaryButton("Pesquisar") {
            managementMenuSearch = searchField.text.toString().trim()
            renderApp()
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            setMargins(0, 0, dp(5), 0)
        })
        actions.addView(secondaryButton("Limpar") {
            managementMenuSearch = ""
            selectedManagementMenuCategory = "Todas"
            renderApp()
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            setMargins(dp(5), 0, 0, 0)
        })
        filterCard.addView(actions)
        addGap(filterCard, 7)

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        categories.forEach { category ->
            row.addView(
                filterChip(category, selectedManagementMenuCategory == category) {
                    selectedManagementMenuCategory = category
                    renderApp()
                },
                LinearLayout.LayoutParams(-2, dp(42)).apply {
                    setMargins(0, 0, dp(6), 0)
                }
            )
        }
        scroll.addView(row)
        filterCard.addView(scroll, LinearLayout.LayoutParams(-1, dp(46)))
        container.addView(filterCard, cardParams())
        addGap(container, 5)
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
            addActionPair(
                container,
                "+  Adicionar artigo",
                { showAddMenuItemDialog() },
                "Atualizar",
                { refreshRemoteOperations() }
            )
            addGap(container, 15)
            val items = remoteMenuItems()
            val validRemoteCosts = (0 until items.length()).count { index ->
                val item = items.optJSONObject(index) ?: return@count false
                val price = item.optDouble("price", Double.NaN)
                val cost = item.optDouble("cost", Double.NaN)
                price.isFinite() && price > 0.0 && cost.isFinite() && cost >= 0.0
            }
            addMenuCostOverview(container, items.length(), validRemoteCosts)
            if (items.length() == 0) {
                container.addView(emptyCard("A carta do servidor está vazia. Adiciona os primeiros artigos e preços aprovados pelo restaurante."))
                return
            }
            val menuEntries = (0 until items.length()).mapNotNull { items.optJSONObject(it) }
            val categories = menuEntries.map { it.optString("category", "Prato principal").trim().ifBlank { "Outros" } }
            addManagementMenuFilters(container, categories)
            val query = normalizeCustomerMenuSearch(managementMenuSearch)
            val filteredItems = menuEntries.filter { item ->
                val category = item.optString("category", "Prato principal").trim().ifBlank { "Outros" }
                (selectedManagementMenuCategory == "Todas" || category == selectedManagementMenuCategory) &&
                    (query.isBlank() || normalizeCustomerMenuSearch(
                        "${category} ${item.optString("name")} ${item.optString("description")} ${item.optString("allergens")}"
                    ).contains(query))
            }
            container.addView(label("${filteredItems.size} de ${menuEntries.size} artigo(s)", 11f, true, forestSoft))
            addGap(container, 6)
            if (filteredItems.isEmpty()) {
                container.addView(emptyCard("Não existem artigos que correspondam à pesquisa e categoria selecionadas."))
                return
            }
            for (item in filteredItems) {
                val card = makeCard(15)
                card.addView(label(item.optString("name"), 17f, true, forestDeep))
                addGap(card, 3)
                card.addView(label(item.optString("category", "Prato principal").trim().ifBlank { "Outros" }, 10.5f, true, forestSoft))
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
                if (isPremium && item.optString("id").isNotBlank()) {
                    addGap(card, 5)
                    card.addView(secondaryButton("Configurar receita e ingredientes") {
                        showRecipeDialog(item.optString("id"), item.optString("name"))
                    }, LinearLayout.LayoutParams(-1, dp(48)))
                }
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
        val validLocalCosts = items.count {
            it.price.isFinite() && it.price > 0.0 && it.cost.isFinite() && it.cost >= 0.0
        }
        addMenuCostOverview(container, items.size, validLocalCosts)
        if (items.isEmpty()) {
            container.addView(emptyCard("A carta está vazia. Adiciona o primeiro artigo de demonstração."))
            return
        }
        val categories = items.map { it.category.trim().ifBlank { "Outros" } }
        addManagementMenuFilters(container, categories)
        val query = normalizeCustomerMenuSearch(managementMenuSearch)
        val filteredItems = items.filter { item ->
            val category = item.category.trim().ifBlank { "Outros" }
            (selectedManagementMenuCategory == "Todas" || category == selectedManagementMenuCategory) &&
                (query.isBlank() || normalizeCustomerMenuSearch(
                    "${category} ${item.name} ${item.description} ${item.allergens}"
                ).contains(query))
        }
        container.addView(label("${filteredItems.size} de ${items.size} artigo(s)", 11f, true, forestSoft))
        addGap(container, 6)
        if (filteredItems.isEmpty()) {
            container.addView(emptyCard("Não existem artigos que correspondam à pesquisa e categoria selecionadas."))
            return
        }
        filteredItems.forEach { item ->
            val card = makeCard(15)
            card.addView(label(item.name, 17f, true, forestDeep))
            addGap(card, 3)
            card.addView(label(item.category.trim().ifBlank { "Outros" }, 10.5f, true, forestSoft))
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

    @Synchronized
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
            val previousRestaurantId = backendRestaurantId()
            val restaurant = SupabaseApi.getMyRestaurant(token)
            val editor = backendPrefs.edit()
                .remove("restaurant_id")
                .remove("restaurant_name")
                .remove("join_code")
                .remove("staff_join_code")
                .remove("restaurant_role")
            if (restaurant != null) {
                val restaurantId = restaurant.optString("restaurant_id")
                if (previousRestaurantId.isNotBlank() && previousRestaurantId != restaurantId) {
                    editor.remove("remote_members_json")
                        .remove("remote_tables_json")
                        .remove("remote_menu_json")
                        .remove("remote_orders_json")
                        .remove("remote_inventory_json")
                        .remove("inventory_loaded")
                        .remove("remote_operations_updated_at")
                }
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
                    .remove("remote_operations_updated_at")
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
                    item.optString("allergens", ""),
                    item.optString("id").trim().takeIf { it.isNotBlank() }
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
            .putLong("remote_operations_updated_at", System.currentTimeMillis())
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
            kitchenOrdersCache = null
            kitchenQueueUpdatedAtMillis = System.currentTimeMillis()
            kitchenQueueRefreshFailed = false
            renderApp()
        }
    }

    private fun restoreCustomerMembership(token: String) {
        if (!isCustomerApp || token.isBlank()) return
        try {
            val member = SupabaseApi.getMyLoyaltyMembership(token)
            if (member == null) {
                customerReservationsCache = JSONArray()
                selectedCustomerReservationFilter = "Todas"
                customerReservationsLoaded = false
                customerReservationsLoading = false
                customerReservationsLoadError = false
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
            customerReservationsCache = JSONArray()
            selectedCustomerReservationFilter = "Todas"
            customerReservationsLoaded = false
            customerReservationsLoading = false
            customerReservationsLoadError = false
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
        clearEconomicManagerState()
        customerReservationsCache = JSONArray()
        selectedCustomerReservationFilter = "Todas"
        customerReservationsLoaded = false
        customerReservationsLoading = false
        customerReservationsLoadError = false
        kitchenOrdersCache = null
        kitchenQueueUpdatedAtMillis = 0L
        kitchenQueueRefreshFailed = false
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
                        if (onError != null) {
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

    private fun clearAccountScopedUiState() {
        clearEconomicManagerState()
        assistantConversation.clear()
        assistantQuestion = ""
        assistantAnswer = null
        assistantInsightsExpanded = false

        selectedOrderFilter = "Todos"
        selectedKitchenFilter = "Todos"
        selectedTableFilter = "Todas"
        selectedReservationFilter = "Todas"
        reservationSearchQuery = ""
        managementMenuSearch = ""
        selectedManagementMenuCategory = "Todas"
        inventorySearch = ""
        selectedInventoryFilter = "Todos"

        customerSearchQuery = ""
        selectedCustomerReservationFilter = "Todas"
        customerReservationSearchQuery = ""
        customerMenuSearch = ""
        selectedCustomerMenuCategory = "Todas"
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

        if (token.isNotBlank() && backendRestaurantId().isNotBlank()) {
            addGap(container, 3)
            val setupCard = makeCard(14, paleGreen)
            setupCard.addView(label("PREPARAR A OPERAÇÃO", 9.5f, true, forestSoft))
            addGap(setupCard, 4)
            setupCard.addView(label(
                "Atalhos para rever a configuração essencial do restaurante.",
                11.5f, false, ink
            ))
            addGap(setupCard, 8)
            val setupScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            val setupRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            listOf("Mesas", "Carta", "Pedidos").forEach { page ->
                setupRow.addView(
                    navigationChip("Abrir $page") {
                        activePage = page
                        renderApp()
                    },
                    LinearLayout.LayoutParams(-2, dp(42)).apply {
                        setMargins(0, 0, dp(7), 0)
                    }
                )
            }
            setupScroll.addView(setupRow)
            setupCard.addView(setupScroll, LinearLayout.LayoutParams(-1, dp(46)))
            container.addView(setupCard, cardParams())
        }

        if (token.isNotBlank()) {
            addGap(container, 3)
            val securityCard = makeCard(14)
            securityCard.addView(label("Segurança da conta", 14f, true, forestDeep))
            addGap(securityCard, 4)
            securityCard.addView(label(
                "Termina a sessão neste dispositivo ou elimina a conta permanentemente. A eliminação é irreversível.",
                11.5f, false, muted
            ))
            addGap(securityCard, 8)
            securityCard.addView(secondaryButton("Terminar sessão neste dispositivo") {
                clearAccountScopedUiState()
                val tokenToRevoke = backendToken()
                if (tokenToRevoke.isNotBlank()) {
                    Thread { runCatching { SupabaseApi.signOut(tokenToRevoke) } }.start()
                }
                secureSession.clear()
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
            }, LinearLayout.LayoutParams(-1, dp(44)))
            addGap(securityCard, 6)
            securityCard.addView(dangerButton("Eliminar conta permanentemente") {
                showDeleteAccountConfirmation()
            }, LinearLayout.LayoutParams(-1, dp(44)))
            container.addView(securityCard, cardParams())
        }

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
            secureSession.clear()
            runCatching { pendingRequestIds.clearAll() }
            store.clearLocalDemoData()
            backendPrefs.edit()
                .clear()
                .putBoolean("local_demo_data_isolated_v1", true)
                .apply()
            reservationCache = JSONArray()
            reservationsLoaded = false

            if (isCustomerApp) renderCustomerApp() else renderApp()

            val message = if (result == "DELETED_RESTAURANT") {
                "A conta e os dados do restaurante foram eliminados permanentemente."
            } else {
                "A conta foi eliminada. A associação de fidelização foi removida e a autoria dos registos operacionais preservados foi anonimizada."
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
            container.addView(secondaryButton("Ver código de adesão") { showRestaurantJoinCode() }, LinearLayout.LayoutParams(-1, dp(48)))
        } else {
            container.addView(primaryButton("+  Adicionar cliente (local)") { showAddCustomerDialog() }, LinearLayout.LayoutParams(-1, dp(48)))
        }
        addGap(container, 13)

        val customers = if (remoteMode) loadRemoteCustomers() else store.getCustomers()
        if (customers.isEmpty()) {
            container.addView(emptyCard("Ainda não há cartões de fidelização. Adiciona um cliente para começar a testar os pontos."))
            return
        }

        val searchCard = makeCard(13)
        searchCard.addView(label("PESQUISAR CLIENTE", 9.5f, true, forestSoft))
        addGap(searchCard, 6)
        val customerSearchField = inputField(
            "Nome ou código do cartão",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        ).apply {
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                    customerSearchQuery = text.toString().trim()
                    renderApp()
                    true
                } else {
                    false
                }
            }
        }
        customerSearchField.setText(customerSearchQuery)
        searchCard.addView(customerSearchField, fieldParams())
        val searchActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        searchActions.addView(primaryButton("Pesquisar") {
            customerSearchQuery = customerSearchField.text.toString().trim()
            renderApp()
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            setMargins(0, 0, dp(5), 0)
        })
        searchActions.addView(secondaryButton("Limpar") {
            customerSearchQuery = ""
            renderApp()
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            setMargins(dp(5), 0, 0, 0)
        })
        searchCard.addView(searchActions)
        container.addView(searchCard, cardParams())

        val query = normalizeCustomerMenuSearch(customerSearchQuery)
        val filteredCustomers = customers.filter { customer ->
            query.isBlank() || normalizeCustomerMenuSearch(
                "${customer.name} ${customer.code}"
            ).contains(query)
        }
        container.addView(label(
            "${filteredCustomers.size} de ${customers.size} cliente(s)",
            11f, true, forestSoft
        ))
        addGap(container, 6)
        if (filteredCustomers.isEmpty()) {
            container.addView(emptyCard("Não encontrámos clientes com esse nome ou código. Confirma a pesquisa ou limpa o filtro."))
            return
        }

        filteredCustomers.forEach { customer ->
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
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(0, 0, dp(5), 0) })
            actions.addView(secondaryButton("− Descontar") {
                showCustomerPointsDialog(customer, true)
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(5), 0, 0, 0) })
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
                            customerReservationsCache = JSONArray()
                            selectedCustomerReservationFilter = "Todas"
                            customerReservationsLoaded = false
                            customerReservationsLoading = false
                            customerReservationsLoadError = false
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

    private fun refreshCustomerReservations() {
        val memberId = backendMemberId()
        if (backendToken().isBlank() || memberId.isBlank()) return
        if (customerReservationsLoading) return
        customerReservationsLoading = true
        customerReservationsLoadError = false
        runBackend(
            task = {
                SupabaseApi.listCustomerReservations(backendToken(), memberId).toString()
            },
            onError = { error ->
                customerReservationsLoading = false
                customerReservationsLoadError = true
                if (activeCustomerPage == "Reservas") renderCustomerApp()
                AlertDialog.Builder(this)
                    .setTitle("Não foi possível atualizar as reservas")
                    .setMessage(friendlyBackendError(error.message))
                    .setPositiveButton("OK", null)
                    .show()
            },
            onSuccess = { raw ->
                customerReservationsCache = JSONArray(raw)
                customerReservationsLoaded = true
                customerReservationsLoading = false
                customerReservationsLoadError = false
                if (activeCustomerPage == "Reservas") renderCustomerApp()
            }
        )
    }

    private fun renderCustomerReservationsPage(container: LinearLayout) {
        container.addView(label("A MINHA CONTA", 10f, true, forestSoft))
        addGap(container, 4)
        container.addView(label("As minhas reservas.", 25f, true, forestDeep))
        addGap(container, 4)
        container.addView(label(
            "Pede uma mesa ao restaurante e acompanha a confirmação nesta página.",
            13.5f, false, muted
        ))
        addGap(container, 12)

        when {
            backendToken().isBlank() -> {
                container.addView(emptyCard("Inicia sessão para pedir uma reserva e acompanhar o estado."), cardParams())
                container.addView(primaryButton("Criar conta / iniciar sessão") {
                    showAuthChooser { renderCustomerApp() }
                }, LinearLayout.LayoutParams(-1, dp(48)))
                return
            }
            backendMemberId().isBlank() -> {
                container.addView(emptyCard("Associa primeiro a tua conta ao restaurante com o código de adesão fornecido pela equipa."), cardParams())
                container.addView(primaryButton("Associar ao restaurante") {
                    showCustomerJoinDialog()
                }, LinearLayout.LayoutParams(-1, dp(48)))
                return
            }
        }

        val requestCard = makeCard(14, copperPale)
        requestCard.addView(label("PEDIDO DE RESERVA", 9.5f, true, forestSoft))
        addGap(requestCard, 4)
        requestCard.addView(label(
            "O pedido fica pendente até o restaurante o confirmar. Enviar o pedido não garante uma mesa.",
            12f, false, ink
        ))
        addGap(requestCard, 8)
        requestCard.addView(primaryButton("Pedir uma reserva") {
            showCustomerReservationDialog()
        }, LinearLayout.LayoutParams(-1, dp(46)))
        container.addView(requestCard, cardParams())

        container.addView(secondaryButton("Atualizar as minhas reservas") {
            refreshCustomerReservations()
        }, LinearLayout.LayoutParams(-1, dp(44)).apply { setMargins(0, 0, 0, dp(10)) })

        if (!customerReservationsLoaded) {
            container.addView(
                emptyCard(
                    if (customerReservationsLoading) "A carregar os teus pedidos de reserva…"
                    else if (customerReservationsLoadError) "Não foi possível carregar os pedidos. Tenta atualizar novamente."
                    else "A preparar a lista de reservas."
                ),
                cardParams()
            )
            if (!customerReservationsLoading && !customerReservationsLoadError) refreshCustomerReservations()
            return
        }

        val allCustomerReservations = (0 until customerReservationsCache.length())
            .mapNotNull { customerReservationsCache.optJSONObject(it) }
        if (allCustomerReservations.isEmpty()) {
            container.addView(emptyCard("Ainda não tens pedidos de reserva. Quando enviares um, o estado aparece aqui."), cardParams())
            return
        }

        val activeCount = allCustomerReservations.count {
            it.optString("status") in setOf("Pendente", "Confirmada")
        }
        val historyCount = allCustomerReservations.size - activeCount
        val customerFilters = listOf("Todas", "Ativas", "Histórico")
        container.addView(label(
            "$activeCount reserva(s) ativa(s) · $historyCount no histórico",
            11f, true, forestSoft
        ))
        addGap(container, 7)
        val customerFilterScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val customerFilterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        customerFilters.forEach { filter ->
            customerFilterRow.addView(
                filterChip(filter, selectedCustomerReservationFilter == filter) {
                    selectedCustomerReservationFilter = filter
                    renderCustomerApp()
                },
                LinearLayout.LayoutParams(-2, dp(42)).apply {
                    setMargins(0, 0, dp(6), 0)
                }
            )
        }
        customerFilterScroll.addView(customerFilterRow)
        container.addView(customerFilterScroll, LinearLayout.LayoutParams(-1, dp(46)))
        addGap(container, 6)

        val customerReservationSearchCard = makeCard(13)
        customerReservationSearchCard.addView(label("PESQUISAR RESERVA", 9.5f, true, forestSoft))
        addGap(customerReservationSearchCard, 5)
        val customerReservationSearchField = inputField(
            "Data (dd/mm/aaaa) ou número de pessoas",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        ).apply {
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                    customerReservationSearchQuery = text.toString().trim()
                    renderCustomerApp()
                    true
                } else {
                    false
                }
            }
        }
        customerReservationSearchField.setText(customerReservationSearchQuery)
        customerReservationSearchCard.addView(customerReservationSearchField, fieldParams())
        val customerReservationSearchActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        customerReservationSearchActions.addView(primaryButton("Pesquisar") {
            customerReservationSearchQuery = customerReservationSearchField.text.toString().trim()
            renderCustomerApp()
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            setMargins(0, 0, dp(5), 0)
        })
        customerReservationSearchActions.addView(secondaryButton("Limpar") {
            customerReservationSearchQuery = ""
            renderCustomerApp()
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            setMargins(dp(5), 0, 0, 0)
        })
        customerReservationSearchCard.addView(customerReservationSearchActions)
        container.addView(customerReservationSearchCard, cardParams())

        val filteredCustomerReservations = when (selectedCustomerReservationFilter) {
            "Ativas" -> allCustomerReservations.filter { it.optString("status") in setOf("Pendente", "Confirmada") }
            "Histórico" -> allCustomerReservations.filter { it.optString("status") !in setOf("Pendente", "Confirmada") }
            else -> allCustomerReservations
        }
        if (filteredCustomerReservations.isEmpty()) {
            container.addView(
                emptyCard(
                    if (selectedCustomerReservationFilter == "Ativas")
                        "Não tens reservas ativas."
                    else
                        "Ainda não há reservas no histórico."
                ),
                cardParams()
            )
            return
        }

        val displayFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "PT"))
        val searchDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "PT"))
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        val customerReservationQuery = normalizeCustomerMenuSearch(customerReservationSearchQuery)
        val visibleCustomerReservations = filteredCustomerReservations.filter { reservation ->
            val rawDate = reservation.optString("reservation_at")
            val timestamp = parseOrderTimestamp(rawDate)
            val localDate = if (timestamp > 0L) searchDateFormat.format(Date(timestamp)) else ""
            customerReservationQuery.isBlank() || normalizeCustomerMenuSearch(
                "$rawDate $localDate ${reservation.optInt("party_size", 1)}"
            ).contains(customerReservationQuery)
        }
        container.addView(label(
            "${visibleCustomerReservations.size} de ${filteredCustomerReservations.size} reserva(s)",
            11f, true, forestSoft
        ))
        addGap(container, 6)
        if (visibleCustomerReservations.isEmpty()) {
            container.addView(emptyCard("Não encontrámos reservas com esses dados. Altera a pesquisa ou limpa o filtro."))
            return
        }
        visibleCustomerReservations.forEach { reservation ->
            val reservationId = reservation.optString("id")
            val status = reservation.optString("status", "Pendente")
            val rawDate = reservation.optString("reservation_at")
            val parsedDate = runCatching { isoFormat.parse(rawDate) }.getOrNull()
            val dateText = parsedDate?.let { displayFormat.format(it) }
                ?: rawDate.replace('T', ' ').take(16)
            val card = makeCard(15)
            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(label(dateText, 14f, true, forestDeep), LinearLayout.LayoutParams(0, -2, 1f))
            val statusColor = when (status) {
                "Confirmada" -> green
                "Cancelada", "Concluída", "Não compareceu" -> muted
                else -> forestSoft
            }
            val statusBackground = when (status) {
                "Confirmada" -> paleGreen
                "Cancelada", "Concluída", "Não compareceu" -> paper
                else -> copperPale
            }
            top.addView(statusBadge(status, statusColor, statusBackground))
            card.addView(top)
            addGap(card, 7)
            card.addView(label(
                "${reservation.optInt("party_size", 1)} pessoa(s) · Duração prevista: ${reservation.optInt("duration_minutes", 90)} minutos",
                12f, false, muted
            ))
            val phone = reservation.optString("guest_phone").trim()
            if (phone.isNotBlank()) card.addView(label("Contacto: $phone", 11.5f, false, muted))
            val notes = reservation.optString("notes").trim()
            if (notes.isNotBlank()) card.addView(label("Notas: $notes", 11.5f, false, muted))
            addGap(card, 5)
            card.addView(label(
                when (status) {
                    "Confirmada" -> "O restaurante confirmou o teu pedido."
                    "Cancelada" -> "Este pedido foi cancelado."
                    "Concluída" -> "Esta reserva foi concluída."
                    "Não compareceu" -> "O restaurante registou a falta."
                    else -> "À espera de confirmação do restaurante."
                },
                11.5f, false, muted
            ))
            if (status in setOf("Pendente", "Confirmada") &&
                parsedDate != null && parsedDate.time > System.currentTimeMillis()
            ) {
                addGap(card, 8)
                card.addView(secondaryButton("Cancelar pedido") {
                    confirmCancelCustomerReservation(reservationId, dateText)
                }, LinearLayout.LayoutParams(-1, dp(44)))
            }
            container.addView(card, cardParams())
        }
    }

    private fun showCustomerReservationDialog() {
        if (backendToken().isBlank() || backendMemberId().isBlank()) {
            showCustomerJoinDialog()
            return
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(10))
        }
        val party = inputField("Número de pessoas", InputType.TYPE_CLASS_NUMBER).apply { setText("2") }
        val phone = inputField("Contacto telefónico (opcional)", InputType.TYPE_CLASS_PHONE)
        val selectedReservationDate = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dateFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "PT"))
        val dateButton = TextView(this).apply {
            text = "Selecionar data e hora  ▾"
            textSize = 14f
            setTextColor(ink)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(11), dp(13), dp(11))
            background = rounded(paper, 12)
            contentDescription = "Selecionar data e hora da reserva"
            isClickable = true
            isFocusable = true
        }
        fun refreshSelectedDateLabel() {
            dateButton.text = dateFormat.format(selectedReservationDate.time) + "  ▾"
        }
        refreshSelectedDateLabel()
        dateButton.setOnClickListener {
            val maximumDate = Calendar.getInstance().apply { add(Calendar.YEAR, 1) }.timeInMillis
            val datePicker = android.app.DatePickerDialog(
                this,
                { _, year, month, day ->
                    selectedReservationDate.set(Calendar.YEAR, year)
                    selectedReservationDate.set(Calendar.MONTH, month)
                    selectedReservationDate.set(Calendar.DAY_OF_MONTH, day)
                    android.app.TimePickerDialog(
                        this,
                        { _, hour, minute ->
                            selectedReservationDate.set(Calendar.HOUR_OF_DAY, hour)
                            selectedReservationDate.set(Calendar.MINUTE, minute)
                            selectedReservationDate.set(Calendar.SECOND, 0)
                            selectedReservationDate.set(Calendar.MILLISECOND, 0)
                            dateButton.error = null
                            refreshSelectedDateLabel()
                        },
                        selectedReservationDate.get(Calendar.HOUR_OF_DAY),
                        selectedReservationDate.get(Calendar.MINUTE),
                        true
                    ).show()
                },
                selectedReservationDate.get(Calendar.YEAR),
                selectedReservationDate.get(Calendar.MONTH),
                selectedReservationDate.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.datePicker.minDate = System.currentTimeMillis()
            datePicker.datePicker.maxDate = maximumDate
            datePicker.show()
        }
        val notes = multilineInputField("Notas ou pedidos especiais (opcional)")
        form.addView(label(
            "A reserva usará o nome associado ao teu cartão de cliente.",
            11f, true, forestSoft
        ))
        addGap(form, 5)
        listOf(
            "Número de pessoas" to party,
            "Contacto telefónico" to phone
        ).forEach { (caption, field) ->
            form.addView(label(caption, 11f, true, forestSoft))
            form.addView(field, fieldParams())
            addGap(form, 4)
        }
        form.addView(label("Data e hora", 11f, true, forestSoft))
        form.addView(dateButton, fieldParams())
        addGap(form, 4)
        form.addView(label("Notas ou pedidos especiais", 11f, true, forestSoft))
        form.addView(notes, multilineFieldParams())
        addGap(form, 4)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Pedir uma reserva")
            .setMessage("O restaurante receberá o pedido como pendente. A confirmação é feita pela equipa.")
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Enviar pedido", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val partySize = party.text.toString().trim().toIntOrNull()
                val parsedDate = selectedReservationDate.time
                val normalizedPhone = phone.text.toString().trim()
                val normalizedNotes = notes.text.toString().trim()
                when {
                    partySize == null || partySize !in 1..60 ->
                        party.error = "Usa um número entre 1 e 60."
                    parsedDate.time <= System.currentTimeMillis() ->
                        dateButton.error = "Indica uma hora futura."
                    parsedDate.time > Calendar.getInstance().apply { add(Calendar.YEAR, 1) }.timeInMillis ->
                        dateButton.error = "A reserva não pode estar a mais de um ano de distância."
                    normalizedPhone.length > 40 ->
                        phone.error = "Máximo de 40 caracteres."
                    normalizedNotes.length > 1000 ->
                        notes.error = "Máximo de 1000 caracteres."
                    else -> {
                        val reservationAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(parsedDate)
                        val memberId = backendMemberId()
                        val requestAccountId = backendPrefs.getString("user_id", "").orEmpty()
                        val signature = JSONObject()
                            .put("member_id", memberId)
                            .put("party_size", partySize)
                            .put("reservation_at", reservationAt)
                            .put("guest_phone", normalizedPhone)
                            .put("notes", normalizedNotes)
                            .put("duration_minutes", 90)
                            .toString()
                        val requestOperation = "customer_reservation_create"
                        val requestId = try {
                            pendingRequestIds.getOrCreate(requestOperation, requestAccountId, signature)
                        } catch (_: Exception) {
                            notes.error = "Não foi possível guardar a referência segura. Tenta novamente."
                            return@setOnClickListener
                        }
                        runBackend(
                            task = {
                                val token = backendToken()
                                val createdId = SupabaseApi.createCustomerReservationRequest(
                                    token, requestId, memberId, partySize, reservationAt, normalizedPhone, normalizedNotes
                                )
                                val latest = SupabaseApi.listCustomerReservations(token, memberId)
                                JSONObject()
                                    .put("reservation_id", createdId)
                                    .put("reservations", latest)
                                    .toString()
                            },
                            onError = { error ->
                                AlertDialog.Builder(this)
                                    .setTitle("Pedido não confirmado")
                                    .setMessage(
                                        friendlyBackendError(error.message) +
                                            "\n\nOs dados continuam no formulário. Podes repetir o envio com segurança."
                                    )
                                    .setPositiveButton("OK", null)
                                    .show()
                            },
                            onSuccess = { raw ->
                                val result = JSONObject(raw)
                                customerReservationsCache = result.optJSONArray("reservations") ?: JSONArray()
                                customerReservationsLoaded = true
                                customerReservationsLoading = false
                                customerReservationsLoadError = false
                                dialog.dismiss()
                                pendingRequestIds.complete(requestOperation, requestAccountId, signature, requestId)
                                Toast.makeText(this, "Pedido enviado. Aguarda a confirmação do restaurante.", Toast.LENGTH_LONG).show()
                                renderCustomerApp()
                            }
                        )
                    }
                }
            }
        }
        dialog.show()
        dialog.window?.setLayout(-1, dp(620))
    }

    private fun confirmCancelCustomerReservation(reservationId: String, dateText: String) {
        if (reservationId.isBlank()) return
        AlertDialog.Builder(this)
            .setTitle("Cancelar pedido de reserva?")
            .setMessage("Queres cancelar o pedido de $dateText? O restaurante deixará de o tratar como ativo.")
            .setNegativeButton("Manter pedido", null)
            .setPositiveButton("Cancelar pedido") { _, _ ->
                runBackend(
                    task = {
                        SupabaseApi.cancelMyCustomerReservation(backendToken(), reservationId)
                        SupabaseApi.listCustomerReservations(backendToken(), backendMemberId()).toString()
                    },
                    onError = { error ->
                        AlertDialog.Builder(this)
                            .setTitle("Não foi possível cancelar")
                            .setMessage(friendlyBackendError(error.message))
                            .setPositiveButton("OK", null)
                            .show()
                    },
                    onSuccess = { raw ->
                        customerReservationsCache = JSONArray(raw)
                        customerReservationsLoaded = true
                        customerReservationsLoading = false
                        customerReservationsLoadError = false
                        Toast.makeText(this, "Pedido de reserva cancelado.", Toast.LENGTH_SHORT).show()
                        renderCustomerApp()
                    }
                )
            }
            .show()
    }

    private fun renderCustomerApp() {
        val allowedCustomerPages = if (hasCalculator) setOf("Cartão", "Carta", "Reservas", "Conta") else setOf("Cartão", "Carta", "Conta")
        if (activeCustomerPage !in allowedCustomerPages) activeCustomerPage = "Cartão"
        appRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(paper)
        }
        applySystemBarInsets(appRoot)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(15), dp(18), dp(15))
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
            textSize = 14f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(forestDeep)
            background = rounded(copper, 14)
        }
        brandRow.addView(mark, LinearLayout.LayoutParams(dp(42), dp(42)))
        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(4), 0)
        }
        brandText.addView(label(displayCustomerRestaurantName(), 20f, true, white).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        brandText.addView(label("StudioMesa · Área de cliente", 10f, true, copperPale))
        brandRow.addView(brandText, LinearLayout.LayoutParams(0, -2, 1f))
        brandRow.addView(label("CLIENTE", 9f, true, forestDeep).apply {
            gravity = Gravity.CENTER
            setPadding(dp(9), dp(8), dp(9), dp(8))
            background = rounded(copper, 18)
        })
        header.addView(brandRow)
        addGap(header, 10)
        header.addView(View(this).apply { setBackgroundColor(Color.argb(65, 255, 255, 255)) },
            LinearLayout.LayoutParams(-1, dp(1)))
        addGap(header, 9)
        header.addView(label("A tua experiência, num só lugar.", 16f, true, white))
        header.addView(label(
            if (backendMemberId().isBlank()) "Consulta a carta e acompanha os teus pontos."
            else "O teu cartão de fidelização.",
            12f, false, copperPale
        ))
        appRoot.addView(header, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(12), dp(8), dp(12), dp(4))
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(18))
        }
        when (activeCustomerPage) {
            "Cartão" -> renderCustomerLoyaltyPage(content)
            "Carta" -> renderCustomerMenuPage(content)
            "Reservas" -> renderCustomerReservationsPage(content)
            "Conta" -> renderCustomerAccountPage(content)
        }
        scroll.addView(content)
        appRoot.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setBackgroundColor(white)
            elevation = dp(6).toFloat()
        }
        val customerPages = if (hasCalculator) listOf("Cartão", "Carta", "Reservas", "Conta") else listOf("Cartão", "Carta", "Conta")
        customerPages.forEach { page ->
            nav.addView(
                bottomNavigationItem(page, activeCustomerPage == page) {
                    activeCustomerPage = page
                    renderCustomerApp()
                },
                LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
            )
        }
        appRoot.addView(nav, LinearLayout.LayoutParams(-1, dp(60)))
        setContentView(appRoot)
    }

    private fun renderCustomerLoyaltyPage(container: LinearLayout) {
        val savedName = store.getCustomerProfileName()
        container.addView(label(if (savedName.isBlank()) "Olá!" else "Olá, " + savedName + "!", 25f, true, forestDeep))
        addGap(container, 5)
        container.addView(label("Cartão de fidelização do restaurante.", 13.5f, false, muted))
        addGap(container, 12)
        when {
            backendToken().isBlank() -> container.addView(primaryButton("Criar conta / iniciar sessão") { showAuthChooser { renderCustomerApp() } }, LinearLayout.LayoutParams(-1, dp(48)))
            backendMemberId().isBlank() -> container.addView(primaryButton("Associar ao restaurante") { showCustomerJoinDialog() }, LinearLayout.LayoutParams(-1, dp(48)))
            else -> container.addView(primaryButton("Atualizar saldo no servidor") { refreshCustomerMembership() }, LinearLayout.LayoutParams(-1, dp(48)))
        }
        addGap(container, 15)

        val loyalty = makeCard(18, forest)
        loyalty.addView(label("O meu cartão", 10.5f, true, copperPale))
        addGap(loyalty, 10)
        loyalty.addView(label(backendPrefs.getInt("member_points", 0).toString(), 42f, true, white))
        loyalty.addView(label(if (backendMemberId().isBlank()) "Pontos de demonstração" else "Pontos disponíveis", 10f, true, copperPale))
        addGap(loyalty, 13)
        val code = backendPrefs.getString("member_code", "")?.takeIf { it.isNotBlank() } ?: store.getCustomerMemberCode()
        loyalty.addView(label("Código de cliente", 11f, false, copperPale))
        addGap(loyalty, 3)
        loyalty.addView(label(code, 20f, true, white))
        addGap(loyalty, 8)
        loyalty.addView(primaryButton("Copiar código do cartão") {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Código de cliente StudioMesa", code))
            Toast.makeText(this, "Código de cliente copiado.", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(-1, dp(42)))
        addGap(loyalty, 9)
        loyalty.addView(label(if (backendMemberId().isBlank()) "Este é um cartão demonstrativo. Cria sessão e associa-o ao código do restaurante para consultar os pontos reais." else "Saldo obtido do sistema de fidelização partilhado. A atribuição de pontos é controlada pelo restaurante.", 12f, false, Color.rgb(229, 235, 228)))
        container.addView(loyalty, cardParams())
        addGap(container, 5)
        container.addView(secondaryButton("Consultar a carta") {
            activeCustomerPage = "Carta"
            renderCustomerApp()
        }, LinearLayout.LayoutParams(-1, dp(48)))
    }

    private fun renderCustomerMenuPage(container: LinearLayout) {
        data class CustomerMenuDisplayItem(
            val category: String,
            val name: String,
            val description: String,
            val allergens: String,
            val price: Double
        )

        container.addView(label("CARTA DO RESTAURANTE", 10.5f, true, forestSoft))
        addGap(container, 4)
        container.addView(label("Descobre o menu.", 25f, true, forestDeep))
        addGap(container, 4)
        container.addView(label("Pesquisa pratos, categorias ou informação sobre alergénios.", 13.5f, false, muted))
        addGap(container, 12)

        val isLinkedToRestaurant = backendMemberId().isNotBlank()
        val menuItems = mutableListOf<CustomerMenuDisplayItem>()
        if (isLinkedToRestaurant) {
            val publishedMenu = savedRemoteArray("customer_menu_json")
            for (index in 0 until publishedMenu.length()) {
                val item = publishedMenu.optJSONObject(index) ?: continue
                menuItems.add(
                    CustomerMenuDisplayItem(
                        category = item.optString("category", "Prato principal").trim().ifBlank { "Outros" },
                        name = item.optString("name"),
                        description = item.optString("description").trim(),
                        allergens = item.optString("allergens").trim(),
                        price = item.optDouble("price", 0.0)
                    )
                )
            }
        } else {
            store.getMenu().forEach { item ->
                menuItems.add(
                    CustomerMenuDisplayItem(
                        category = item.category.trim().ifBlank { "Outros" },
                        name = item.name,
                        description = item.description,
                        allergens = item.allergens,
                        price = item.price
                    )
                )
            }
        }

        val query = normalizeCustomerMenuSearch(customerMenuSearch)
        val categories = listOf("Todas") + menuItems.map { it.category }
            .filter { it.isNotBlank() && it != "Todas" }.distinct().sorted()
        if (selectedCustomerMenuCategory !in categories) selectedCustomerMenuCategory = "Todas"
        val filteredItems = menuItems.filter { item ->
            (selectedCustomerMenuCategory == "Todas" || item.category == selectedCustomerMenuCategory) &&
                (query.isBlank() || normalizeCustomerMenuSearch(
                    "${item.category} ${item.name} ${item.description} ${item.allergens}"
                ).contains(query))
        }

        val menuCard = makeCard(16)
        menuCard.addView(label(
            if (isLinkedToRestaurant) "Carta do restaurante" else "Carta de demonstração",
            10f, true, forestSoft
        ))
        addGap(menuCard, 7)
        menuCard.addView(label(if (isLinkedToRestaurant) "A nossa carta" else "Exemplos de artigos", 18f, true, forestDeep))
        addGap(menuCard, 4)
        menuCard.addView(label(
            if (isLinkedToRestaurant) "Preços publicados pelo restaurante. Os custos internos não são mostrados."
            else "Nomes e preços de exemplo; associa a tua conta a um restaurante para veres a carta real.",
            12f, false, muted
        ))
        addGap(menuCard, 10)

        val searchPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val searchField = inputField("Pesquisar nome, categoria ou alergénios", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES).apply {
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                    customerMenuSearch = text.toString().trim()
                    renderCustomerApp()
                    true
                } else {
                    false
                }
            }
        }
        searchField.setText(customerMenuSearch)
        searchPanel.addView(searchField, fieldParams())
        val searchActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        searchActions.addView(
            primaryButton("Pesquisar") {
                customerMenuSearch = searchField.text.toString().trim()
                renderCustomerApp()
            },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(0, 0, dp(5), 0) }
        )
        searchActions.addView(
            secondaryButton("Limpar") {
                customerMenuSearch = ""
                renderCustomerApp()
            },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(5), 0, 0, 0) }
        )
        searchPanel.addView(searchActions)
        menuCard.addView(searchPanel, cardParams())

        val categoryScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val categoryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        categories.forEach { category ->
            categoryRow.addView(
                filterChip(category, selectedCustomerMenuCategory == category) {
                    selectedCustomerMenuCategory = category
                    renderCustomerApp()
                },
                LinearLayout.LayoutParams(-2, dp(42)).apply {
                    setMargins(0, 0, dp(6), 0)
                }
            )
        }
        categoryScroll.addView(categoryRow)
        menuCard.addView(categoryScroll, LinearLayout.LayoutParams(-1, dp(46)))

        menuCard.addView(label(
            if (query.isBlank()) "${menuItems.size} artigo(s) disponíveis"
            else "${filteredItems.size} resultado(s) para «${customerMenuSearch.trim()}»",
            11f, true, forestSoft
        ))
        addGap(menuCard, 9)

        when {
            menuItems.isEmpty() && isLinkedToRestaurant ->
                menuCard.addView(emptyCard("O restaurante ainda não publicou artigos ativos na carta."), cardParams())
            filteredItems.isEmpty() ->
                menuCard.addView(emptyCard("Não encontrámos artigos com esse termo. Experimenta outro nome ou categoria."), cardParams())
            else -> filteredItems.forEach { item ->
                val itemCard = makeCard(12, paper)
                itemCard.addView(label(item.category, 10f, true, forestSoft))
                addGap(itemCard, 3)
                itemCard.addView(label(item.name, 16f, true, forestDeep))
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
                    else "Alergénios declarados: ${item.allergens}. Confirma com a equipa se tiveres uma alergia.",
                    11f, false, muted
                ))
                menuCard.addView(itemCard, cardParams())
            }
        }
        container.addView(menuCard, cardParams())
    }

    private fun normalizeCustomerMenuSearch(value: String): String =
        java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .trim()

    private fun renderCustomerAccountPage(container: LinearLayout) {
        val token = backendToken()
        val isSignedIn = token.isNotBlank()
        val isLinked = isSignedIn && backendMemberId().isNotBlank()
        val email = backendPrefs.getString("user_email", "").orEmpty()
        val restaurantName = backendPrefs.getString("member_restaurant_name", "").orEmpty()
        val savedName = store.getCustomerProfileName()

        container.addView(label("A tua conta", 25f, true, forestDeep))
        addGap(container, 4)
        container.addView(label("Perfil, associação ao restaurante e segurança.", 13.5f, false, muted))
        addGap(container, 13)

        val accountCard = makeCard(15)
        accountCard.addView(label(
            when {
                isLinked -> "CARTÃO ASSOCIADO"
                isSignedIn -> "SESSÃO INICIADA"
                else -> "SEM SESSÃO"
            },
            10f, true, if (isLinked) green else forestSoft
        ))
        addGap(accountCard, 7)
        accountCard.addView(label(
            when {
                isLinked -> "A tua conta está ligada ao programa de fidelização."
                isSignedIn -> "A conta está autenticada, mas ainda não está associada a um restaurante."
                else -> "Estás a usar a área de cliente sem uma conta autenticada."
            },
            13f, true, forestDeep
        ))
        if (email.isNotBlank()) {
            addGap(accountCard, 8)
            accountCard.addView(label("EMAIL DA CONTA", 9.5f, true, muted))
            addGap(accountCard, 2)
            accountCard.addView(label(email, 12.5f, false, ink))
        }
        if (isLinked && restaurantName.isNotBlank()) {
            addGap(accountCard, 8)
            accountCard.addView(label("RESTAURANTE ASSOCIADO", 9.5f, true, muted))
            addGap(accountCard, 2)
            accountCard.addView(label(restaurantName, 14f, true, forest))
        }
        addGap(accountCard, 9)
        accountCard.addView(label(
            when {
                isLinked -> "O saldo real e as reservas são consultados através do restaurante associado."
                isSignedIn -> "Pede à equipa o código de adesão para associares o cartão."
                else -> "Inicia sessão para associares o teu cartão e acompanhares os pontos e reservas."
            },
            11.5f, false, muted
        ))
        container.addView(accountCard, cardParams())

        addGap(container, 3)
        when {
            !isSignedIn -> container.addView(
                primaryButton("Criar conta / iniciar sessão") { showAuthChooser { renderCustomerApp() } },
                LinearLayout.LayoutParams(-1, dp(48))
            )
            !isLinked -> container.addView(
                primaryButton("Associar ao restaurante") { showCustomerJoinDialog() },
                LinearLayout.LayoutParams(-1, dp(48))
            )
            else -> container.addView(
                primaryButton("Atualizar saldo no servidor") { refreshCustomerMembership() },
                LinearLayout.LayoutParams(-1, dp(48))
            )
        }

        addGap(container, 8)
        val profileCard = makeCard(14)
        profileCard.addView(label("Nome de apresentação", 14f, true, forestDeep))
        addGap(profileCard, 4)
        profileCard.addView(label(
            if (savedName.isBlank()) "Ainda não definiste um nome neste dispositivo."
            else "Nome guardado neste dispositivo: $savedName",
            11.5f, false, muted
        ))
        addGap(profileCard, 8)
        profileCard.addView(secondaryButton("Definir nome neste dispositivo") { showCustomerProfileDialog() },
            LinearLayout.LayoutParams(-1, dp(44)))
        profileCard.addView(label(
            "Este nome é apenas local: não altera o email da conta nem é enviado ao restaurante.",
            10.5f, false, muted
        ))
        container.addView(profileCard, cardParams())

        if (isSignedIn) {
            addGap(container, 3)
            val securityCard = makeCard(14)
            securityCard.addView(label("Segurança da conta", 14f, true, forestDeep))
            addGap(securityCard, 4)
            securityCard.addView(label(
                "Podes terminar a sessão neste dispositivo ou pedir a eliminação permanente da conta. A eliminação é irreversível.",
                11.5f, false, muted
            ))
            addGap(securityCard, 8)
            securityCard.addView(secondaryButton("Terminar sessão neste dispositivo") {
                clearAccountScopedUiState()
                val tokenToRevoke = backendToken()
                if (tokenToRevoke.isNotBlank()) {
                    Thread { runCatching { SupabaseApi.signOut(tokenToRevoke) } }.start()
                }
                secureSession.clear()
                store.clearCustomerLocalIdentity()
                backendPrefs.edit()
                    .remove("access_token").remove("refresh_token").remove("user_id").remove("user_email")
                    .remove("member_id").remove("member_code").remove("member_points").remove("member_restaurant_name")
                    .remove("customer_menu_json")
                    .apply()
                customerReservationsCache = JSONArray()
                selectedCustomerReservationFilter = "Todas"
                customerReservationsLoaded = false
                customerReservationsLoading = false
                customerReservationsLoadError = false
                activeCustomerPage = "Cartão"
                renderCustomerApp()
            }, LinearLayout.LayoutParams(-1, dp(44)))
            addGap(securityCard, 6)
            securityCard.addView(dangerButton("Eliminar conta permanentemente") {
                showDeleteAccountConfirmation()
            }, LinearLayout.LayoutParams(-1, dp(44)))
            container.addView(securityCard, cardParams())
        }
    }

    private fun showCustomerProfileDialog() {
        val field = inputField("O teu nome", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        field.setText(store.getCustomerProfileName())
        val dialog = AlertDialog.Builder(this)
            .setTitle("Nome neste dispositivo")
            .setMessage("Este nome é guardado apenas nesta instalação. Não altera os dados da tua conta nem é partilhado com o restaurante.")
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


    private fun currentInventoryCostScope(restaurantId: String = backendRestaurantId()): String =
        restaurantId + ":" + backendPrefs.getString("user_id", "").orEmpty()

    private fun isCurrentRestaurantOwner(): Boolean =
        hasRemoteRestaurant() &&
            backendPrefs.getString("restaurant_role", "").orEmpty() == "owner"

    private fun clearInventoryUnitCostCache() {
        inventoryUnitCostCache.clear()
        inventoryUnitCostCacheScope = null
        inventoryUnitCostCacheLoaded = false
    }

    private fun inventoryItemsWithoutPurchaseCosts(items: JSONArray): JSONArray {
        val safeItems = JSONArray()
        val safeFields = listOf(
            "id", "name", "unit", "quantity", "low_stock_threshold", "updated_at"
        )
        for (index in 0 until items.length()) {
            val source = items.optJSONObject(index) ?: continue
            val safe = JSONObject()
            safeFields.forEach { field ->
                if (source.has(field)) safe.put(field, source.opt(field))
            }
            safeItems.put(safe)
        }
        return safeItems
    }

    private fun inventoryItemsWithOwnerCosts(items: JSONArray): JSONArray {
        val safeItems = inventoryItemsWithoutPurchaseCosts(items)
        if (!isCurrentRestaurantOwner() ||
            inventoryUnitCostCacheScope != currentInventoryCostScope()
        ) return safeItems

        for (index in 0 until safeItems.length()) {
            val item = safeItems.optJSONObject(index) ?: continue
            val itemId = item.optString("id")
            if (inventoryUnitCostCache.containsKey(itemId)) {
                item.put(
                    "unit_cost_per_unit",
                    inventoryUnitCostCache[itemId] ?: JSONObject.NULL
                )
            }
        }
        return safeItems
    }

    private fun loadInventoryItemsForCurrentRole(
        token: String,
        restaurantId: String
    ): JSONArray {
        if (!isCurrentRestaurantOwner()) {
            clearInventoryUnitCostCache()
            return SupabaseApi.listInventoryItems(token, restaurantId)
        }

        val rows = SupabaseApi.listInventoryItemsWithCosts(token, restaurantId)
        val newCosts = mutableMapOf<String, BigDecimal?>()
        for (index in 0 until rows.length()) {
            val item = rows.optJSONObject(index) ?: continue
            val itemId = item.optString("id").trim()
            if (itemId.isBlank()) continue
            val cost = if (item.isNull("unit_cost_per_unit")) {
                null
            } else {
                try {
                    BigDecimal(item.get("unit_cost_per_unit").toString())
                } catch (_: NumberFormatException) {
                    throw IllegalStateException(
                        "O servidor devolveu um custo de compra inválido para um artigo de stock."
                    )
                }
            }
            newCosts[itemId] = cost
        }
        inventoryUnitCostCache.clear()
        inventoryUnitCostCache.putAll(newCosts)
        inventoryUnitCostCacheScope = currentInventoryCostScope(restaurantId)
        inventoryUnitCostCacheLoaded = true
        return inventoryItemsWithoutPurchaseCosts(rows)
    }

    private fun remoteInventoryItems(): JSONArray {
        if (!isCurrentRestaurantOwner()) clearInventoryUnitCostCache()
        val stored = backendPrefs.getString("remote_inventory_json", "[]") ?: "[]"
        val parsed = runCatching { JSONArray(stored) }.getOrElse { JSONArray() }
        val safeItems = inventoryItemsWithOwnerCosts(parsed)
        val safeJson = inventoryItemsWithoutPurchaseCosts(parsed).toString()
        if (safeJson != stored) {
            backendPrefs.edit().putString("remote_inventory_json", safeJson).apply()
        }
        return safeItems
    }

    private fun remoteReservations(): JSONArray = reservationCache

    private fun inventoryRefreshStatusMessage(): String {
        if (inventoryRefreshInFlight) return "A atualizar o stock do servidor…"
        if (inventoryRefreshFailed) {
            return if (inventoryUpdatedAtMillis > 0L) {
                "Falha na atualização automática. A mostrar os dados recebidos às " +
                    SimpleDateFormat("HH:mm:ss", Locale("pt", "PT")).format(Date(inventoryUpdatedAtMillis)) + "."
            } else {
                "Não foi possível atualizar o stock. Tenta novamente manualmente."
            }
        }
        return if (inventoryUpdatedAtMillis > 0L) {
            "Atualizado às " +
                SimpleDateFormat("HH:mm:ss", Locale("pt", "PT")).format(Date(inventoryUpdatedAtMillis)) +
                " · atualização automática a cada 30 segundos."
        } else {
            "Atualização automática a cada 30 segundos enquanto esta secção estiver aberta."
        }
    }

    private fun shouldRunInventoryAutoRefresh(): Boolean =
        InventoryRefreshPolicy.shouldScheduleAutoRefresh(
            activityResumed = activityResumed,
            isCustomerApp = isCustomerApp,
            isPremium = isPremium,
            activePage = activePage,
            hasRemoteRestaurant = hasRemoteRestaurant()
        )

    private fun updateInventoryAutoRefresh() {
        if (!shouldRunInventoryAutoRefresh()) {
            stopInventoryAutoRefresh()
            return
        }
        scheduleNextInventoryAutoRefresh()
    }

    private fun scheduleNextInventoryAutoRefresh() {
        if (!shouldRunInventoryAutoRefresh()) {
            stopInventoryAutoRefresh()
            return
        }
        if (!inventoryAutoRefreshScheduled) {
            inventoryAutoRefreshScheduled = true
            inventoryRefreshHandler.postDelayed(
                inventoryRefreshRunnable,
                InventoryRefreshPolicy.REFRESH_INTERVAL_MILLIS
            )
        }
    }

    private fun stopInventoryAutoRefresh() {
        inventoryRefreshHandler.removeCallbacks(inventoryRefreshRunnable)
        inventoryAutoRefreshScheduled = false
    }

    private fun refreshInventoryItemsSilently() {
        val restaurantId = backendRestaurantId()
        if (restaurantId.isBlank() ||
            !InventoryRefreshPolicy.mayStartRefresh(
                activityResumed = activityResumed,
                isCustomerApp = isCustomerApp,
                isPremium = isPremium,
                activePage = activePage,
                hasRemoteRestaurant = hasRemoteRestaurant(),
                refreshInFlight = inventoryRefreshInFlight
            )
        ) return

        inventoryRefreshInFlight = true
        Thread({
            var freshItems: JSONArray? = null
            try {
                refreshSessionIfNeeded()
                val token = backendToken()
                if (token.isBlank() || backendRestaurantId() != restaurantId) {
                    throw IllegalStateException("A sessão ou o restaurante já não estão disponíveis.")
                }
                freshItems = loadInventoryItemsForCurrentRole(token, restaurantId)
            } catch (_: Exception) {
                // Retain the last verified stock snapshot and show a non-blocking warning.
            }

            runOnUiThread {
                inventoryRefreshInFlight = false
                if (isFinishing || backendRestaurantId() != restaurantId || !hasRemoteRestaurant()) return@runOnUiThread

                val loadedItems = freshItems
                if (loadedItems != null) {
                    backendPrefs.edit()
                        .putString("remote_inventory_json", loadedItems.toString())
                        .putBoolean("inventory_loaded", true)
                        .apply()
                    inventoryUpdatedAtMillis = System.currentTimeMillis()
                    inventoryRefreshFailed = false
                } else {
                    inventoryRefreshFailed = true
                }

                if (activityResumed && activePage == "Stock") {
                    refreshInventoryPageContent()
                }
            }
        }, "StudioMesa-stock-refresh").start()
    }

    private fun refreshInventoryPageContent() {
        if (activePage != "Stock") return
        val container = inventoryPageContainer ?: return
        val scroll = container.parent as? ScrollView
        val previousScrollY = scroll?.scrollY ?: 0
        container.removeAllViews()
        renderStock(container)
        scroll?.post {
            val maxScroll = (container.height - (scroll?.height ?: 0)).coerceAtLeast(0)
            scroll?.scrollTo(0, previousScrollY.coerceAtMost(maxScroll))
        }
    }

    private fun refreshInventoryItems() {
        if (!hasRemoteRestaurant()) {
            AlertDialog.Builder(this).setTitle("Liga o restaurante")
                .setMessage("O inventário partilhado exige uma conta autenticada e um restaurante configurado.")
                .setPositiveButton("OK", null).show()
            return
        }
        if (inventoryRefreshInFlight) return
        inventoryRefreshInFlight = true
        runBackend({
            val items = loadInventoryItemsForCurrentRole(backendToken(), backendRestaurantId())
            backendPrefs.edit().putString("remote_inventory_json", items.toString())
                .putBoolean("inventory_loaded", true).apply()
            "OK"
        }, onError = {
            inventoryRefreshInFlight = false
            inventoryRefreshFailed = true
            if (activePage == "Stock") renderApp()
        }) {
            inventoryRefreshInFlight = false
            inventoryUpdatedAtMillis = System.currentTimeMillis()
            inventoryRefreshFailed = false
            renderApp()
        }
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

    private fun shouldRunReservationAutoRefresh(): Boolean =
        ReservationRefreshPolicy.shouldScheduleAutoRefresh(
            activityResumed = activityResumed,
            isCustomerApp = isCustomerApp,
            activePage = activePage,
            hasRemoteRestaurant = hasRemoteRestaurant()
        )

    private fun updateReservationAutoRefresh() {
        if (!shouldRunReservationAutoRefresh()) {
            stopReservationAutoRefresh()
            return
        }
        scheduleNextReservationAutoRefresh()
    }

    private fun scheduleNextReservationAutoRefresh() {
        if (!shouldRunReservationAutoRefresh()) {
            stopReservationAutoRefresh()
            return
        }
        if (!reservationAutoRefreshScheduled) {
            reservationAutoRefreshScheduled = true
            reservationRefreshHandler.postDelayed(
                reservationRefreshRunnable,
                ReservationRefreshPolicy.REFRESH_INTERVAL_MILLIS
            )
        }
    }

    private fun stopReservationAutoRefresh() {
        reservationRefreshHandler.removeCallbacks(reservationRefreshRunnable)
        reservationAutoRefreshScheduled = false
    }

    private fun refreshReservationsSilently() {
        val restaurantId = backendRestaurantId()
        if (restaurantId.isBlank() ||
            !ReservationRefreshPolicy.mayStartRefresh(
                activityResumed = activityResumed,
                isCustomerApp = isCustomerApp,
                activePage = activePage,
                hasRemoteRestaurant = hasRemoteRestaurant(),
                refreshInFlight = reservationRefreshInFlight
            )
        ) return

        reservationRefreshInFlight = true
        Thread({
            var freshReservations: JSONArray? = null
            try {
                refreshSessionIfNeeded()
                val token = backendToken()
                if (token.isBlank() || backendRestaurantId() != restaurantId) {
                    throw IllegalStateException("A sessão ou o restaurante já não estão disponíveis.")
                }
                freshReservations = SupabaseApi.listReservations(token, restaurantId)
            } catch (_: Exception) {
                // Keep the last verified agenda visible during temporary network failures.
            }

            runOnUiThread {
                reservationRefreshInFlight = false
                if (isFinishing || backendRestaurantId() != restaurantId || !hasRemoteRestaurant()) return@runOnUiThread
                val loaded = freshReservations
                if (loaded != null) {
                    reservationCache = loaded
                    reservationsLoaded = true
                    if (activityResumed && activePage == "Reservas") {
                        refreshReservationPageContent()
                    }
                }
            }
        }, "StudioMesa-reservations-refresh").start()
    }

    private fun refreshReservationPageContent() {
        if (activePage != "Reservas") return
        val container = reservationPageContainer ?: return
        val scroll = container.parent as? ScrollView
        val previousScrollY = scroll?.scrollY ?: 0
        container.removeAllViews()
        renderReservations(container)
        scroll?.post {
            val maxScroll = (container.height - (scroll?.height ?: 0)).coerceAtLeast(0)
            scroll?.scrollTo(0, previousScrollY.coerceAtMost(maxScroll))
        }
    }

    private fun renderReservations(container: LinearLayout) {
        reservationPageContainer = container
        pageIntro(container, "Reservas", "Agenda de reservas partilhada com a equipa.")
        addGap(container, 8)
        if (!hasRemoteRestaurant()) {
            container.addView(emptyCard("Para proteger os dados dos clientes e sincronizar as reservas, inicia sessão e configura o restaurante na secção Conta."))
            return
        }
        addActionPair(
            container,
            "+  Nova reserva",
            { showReservationDialog() },
            "Atualizar",
            { refreshReservations() }
        )
        addGap(container, 12)
        container.addView(label(
            "Atualização automática a cada 30 segundos enquanto esta agenda está aberta.",
            10.5f, false, muted
        ))
        addGap(container, 6)

        if (!reservationsLoaded) {
            container.addView(emptyCard("A carregar as reservas do servidor…"))
            refreshReservations()
            return
        }

        val reservations = remoteReservations()
        val allReservations = (0 until reservations.length()).mapNotNull { reservations.optJSONObject(it) }
        if (allReservations.isEmpty()) {
            container.addView(emptyCard("Ainda não há reservas. Usa «Nova reserva» para registar a primeira."))
            return
        }

        val now = System.currentTimeMillis()
        val todayStart = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val tomorrowStart = Calendar.getInstance().apply {
            timeInMillis = todayStart
            add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
        val pendingCount = allReservations.count { it.optString("status") == "Pendente" }
        val confirmedCount = allReservations.count { it.optString("status") == "Confirmada" }
        val todayCount = allReservations.count {
            val timestamp = parseOrderTimestamp(it.optString("reservation_at"))
            timestamp in todayStart until tomorrowStart
        }

        val overview = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        overview.addView(statCard("PENDENTES", pendingCount.toString(), if (pendingCount > 0) copper else forestSoft),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(4), 0) })
        overview.addView(statCard("CONFIRMADAS", confirmedCount.toString(), if (confirmedCount > 0) green else forestSoft),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        overview.addView(statCard("HOJE", todayCount.toString(), if (todayCount > 0) copper else forestSoft),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), 0, 0, 0) })
        container.addView(overview, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, 0, 0, dp(10))
        })

        val filters = listOf("Todas", "Pendentes", "Confirmadas", "Hoje")
        val filterScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        filters.forEach { filter ->
            filterRow.addView(
                filterChip(filter, selectedReservationFilter == filter) {
                    selectedReservationFilter = filter
                    renderApp()
                },
                LinearLayout.LayoutParams(-2, dp(42)).apply {
                    setMargins(0, 0, dp(6), 0)
                }
            )
        }
        filterScroll.addView(filterRow)
        container.addView(filterScroll, LinearLayout.LayoutParams(-1, dp(46)))
        addGap(container, 6)

        val reservationSearchCard = makeCard(13)
        reservationSearchCard.addView(label("PESQUISAR RESERVA", 9.5f, true, forestSoft))
        addGap(reservationSearchCard, 5)
        val reservationSearchField = inputField(
            "Nome, data (dd/mm/aaaa) ou número de mesa",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        ).apply {
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                    reservationSearchQuery = text.toString().trim()
                    renderApp()
                    true
                } else {
                    false
                }
            }
        }
        reservationSearchField.setText(reservationSearchQuery)
        reservationSearchCard.addView(reservationSearchField, fieldParams())
        val searchActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        searchActions.addView(primaryButton("Pesquisar") {
            reservationSearchQuery = reservationSearchField.text.toString().trim()
            renderApp()
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            setMargins(0, 0, dp(5), 0)
        })
        searchActions.addView(secondaryButton("Limpar") {
            reservationSearchQuery = ""
            renderApp()
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            setMargins(dp(5), 0, 0, 0)
        })
        reservationSearchCard.addView(searchActions)
        container.addView(reservationSearchCard, cardParams())

        val filteredReservations = when (selectedReservationFilter) {
            "Pendentes" -> allReservations.filter { it.optString("status") == "Pendente" }
            "Confirmadas" -> allReservations.filter { it.optString("status") == "Confirmada" }
            "Hoje" -> allReservations.filter {
                val timestamp = parseOrderTimestamp(it.optString("reservation_at"))
                timestamp in todayStart until tomorrowStart
            }
            else -> allReservations
        }
        if (filteredReservations.isEmpty()) {
            container.addView(
                emptyCard(
                    when (selectedReservationFilter) {
                        "Pendentes" -> "Não há reservas pendentes neste momento."
                        "Confirmadas" -> "Não há reservas confirmadas neste momento."
                        "Hoje" -> "Não há reservas para hoje."
                        else -> "Não existem reservas para apresentar."
                    }
                ),
                cardParams()
            )
            return
        }

        val reservationQuery = normalizeCustomerMenuSearch(reservationSearchQuery)
        val reservationSearchDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "PT"))
        val visibleReservations = filteredReservations.filter { reservation ->
            val rawReservationDate = reservation.optString("reservation_at")
            val reservationTimestamp = parseOrderTimestamp(rawReservationDate)
            val displaySearchDate = if (reservationTimestamp > 0L) {
                reservationSearchDateFormat.format(Date(reservationTimestamp))
            } else ""
            reservationQuery.isBlank() || normalizeCustomerMenuSearch(
                "${reservation.optString("guest_name")} $rawReservationDate $displaySearchDate ${reservation.optInt("table_number")} ${reservation.optInt("party_size")}"
            ).contains(reservationQuery)
        }
        container.addView(label(
            "${visibleReservations.size} de ${filteredReservations.size} reserva(s)",
            11f, true, forestSoft
        ))
        addGap(container, 6)
        if (visibleReservations.isEmpty()) {
            container.addView(emptyCard("Não encontrámos reservas com esses dados. Altera a pesquisa ou limpa o filtro."))
            return
        }

        val displayFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "PT"))
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        visibleReservations.forEach { reservation ->
            val id = reservation.optString("id")
            val guest = reservation.optString("guest_name", "Cliente")
            val status = reservation.optString("status", "Pendente")
            val card = makeCard(15)
            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(label(guest, 17f, true, forestDeep), LinearLayout.LayoutParams(0, -2, 1f))
            top.addView(statusBadge(
                status,
                if (status == "Confirmada") green else if (status == "Cancelada" || status == "Concluída" || status == "Não compareceu") muted else forestSoft,
                when (status) {
                    "Confirmada" -> paleGreen
                    "Cancelada", "Concluída", "Não compareceu" -> paper
                    else -> copperPale
                }
            ))
            card.addView(top)
            addGap(card, 6)
            val rawDate = reservation.optString("reservation_at")
            val parsedReservationDate = runCatching { isoFormat.parse(rawDate) }.getOrNull()
            val dateText = parsedReservationDate?.let { displayFormat.format(it) }
                ?: rawDate.replace('T', ' ').take(16)
            val tableNo = reservation.optInt("table_number", 0)
            card.addView(label("$dateText · ${reservation.optInt("party_size", 1)} pessoa(s)", 12.5f, true, ink))
            card.addView(label(if (tableNo > 0) "Mesa $tableNo · ${reservation.optInt("duration_minutes", 90)} min" else "Mesa por atribuir · ${reservation.optInt("duration_minutes", 90)} min", 11.5f, false, muted))
            if (reservation.optString("loyalty_member_id").isNotBlank()) {
                card.addView(label("Pedido enviado pela aplicação de cliente", 10.5f, true, forestSoft))
            }
            val phone = reservation.optString("guest_phone").trim()
            if (phone.isNotBlank()) card.addView(label("Contacto: $phone", 11.5f, false, muted))
            val notes = reservation.optString("notes").trim()
            if (notes.isNotBlank()) card.addView(label("Notas: $notes", 11.5f, false, muted))
            if (status in setOf("Pendente", "Confirmada") &&
                parsedReservationDate != null && parsedReservationDate.time > System.currentTimeMillis()
            ) {
                addGap(card, 6)
                card.addView(secondaryButton(if (tableNo > 0) "Alterar mesa atribuída" else "Atribuir mesa") {
                    showAssignReservationTableDialog(id, guest, reservation.optInt("party_size", 1), dateText)
                }, LinearLayout.LayoutParams(-1, dp(44)))
            }
            addGap(card, 10)
            when (status) {
                "Pendente" -> card.addView(primaryButton("Confirmar reserva") {
                    setReservationStatus(id, "Confirmada")
                }, LinearLayout.LayoutParams(-1, dp(48)))
                "Confirmada" -> {
                    card.addView(primaryButton("Marcar como concluída") {
                        setReservationStatus(id, "Concluída")
                    }, LinearLayout.LayoutParams(-1, dp(48)))
                    addGap(card, 5)
                    card.addView(secondaryButton("Não compareceu") {
                        setReservationStatus(id, "Não compareceu")
                    }, LinearLayout.LayoutParams(-1, dp(48)))
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
                }, LinearLayout.LayoutParams(-1, dp(48)))
            }
            container.addView(card, cardParams())
        }
    }

    private fun showAssignReservationTableDialog(
        reservationId: String,
        guestName: String,
        partySize: Int,
        dateText: String
    ) {
        if (!hasRemoteRestaurant()) return
        val tableField = inputField("Número da mesa (1–100)", InputType.TYPE_CLASS_NUMBER)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Atribuir mesa")
            .setMessage(
                "$guestName · $partySize pessoa(s)\n$dateText\n\nO servidor confirma que a mesa pertence ao restaurante, tem capacidade suficiente e não está ocupada por outra reserva nesse horário."
            )
            .setView(tableField)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar mesa", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val tableNumber = tableField.text.toString().trim().toIntOrNull()
                if (tableNumber == null || tableNumber !in 1..100) {
                    tableField.error = "Indica uma mesa entre 1 e 100."
                    return@setOnClickListener
                }
                runBackend(
                    task = {
                        SupabaseApi.assignReservationTable(backendToken(), reservationId, tableNumber)
                        reservationCache = SupabaseApi.listReservations(backendToken(), backendRestaurantId())
                        reservationsLoaded = true
                        "OK"
                    },
                    onSuccess = {
                        dialog.dismiss()
                        Toast.makeText(this, "Mesa atribuída à reserva.", Toast.LENGTH_SHORT).show()
                        renderApp()
                    }
                )
            }
        }
        dialog.show()
        dialog.window?.setLayout(-1, dp(310))
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
        val selectedReservationDate = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dateFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "PT"))
        val dateButton = TextView(this).apply {
            textSize = 14f
            setTextColor(ink)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(11), dp(13), dp(11))
            background = rounded(paper, 12)
            contentDescription = "Selecionar data e hora da reserva"
            isClickable = true
            isFocusable = true
        }
        fun refreshSelectedReservationDateLabel() {
            dateButton.text = dateFormat.format(selectedReservationDate.time) + "  ▾"
        }
        refreshSelectedReservationDateLabel()
        dateButton.setOnClickListener {
            val maximumDate = Calendar.getInstance().apply { add(Calendar.YEAR, 1) }.timeInMillis
            val datePicker = android.app.DatePickerDialog(
                this,
                { _, year, month, day ->
                    selectedReservationDate.set(Calendar.YEAR, year)
                    selectedReservationDate.set(Calendar.MONTH, month)
                    selectedReservationDate.set(Calendar.DAY_OF_MONTH, day)
                    android.app.TimePickerDialog(
                        this,
                        { _, hour, minute ->
                            selectedReservationDate.set(Calendar.HOUR_OF_DAY, hour)
                            selectedReservationDate.set(Calendar.MINUTE, minute)
                            selectedReservationDate.set(Calendar.SECOND, 0)
                            selectedReservationDate.set(Calendar.MILLISECOND, 0)
                            dateButton.error = null
                            refreshSelectedReservationDateLabel()
                        },
                        selectedReservationDate.get(Calendar.HOUR_OF_DAY),
                        selectedReservationDate.get(Calendar.MINUTE),
                        true
                    ).show()
                },
                selectedReservationDate.get(Calendar.YEAR),
                selectedReservationDate.get(Calendar.MONTH),
                selectedReservationDate.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.datePicker.minDate = System.currentTimeMillis()
            datePicker.datePicker.maxDate = maximumDate
            datePicker.show()
        }

        val duration = inputField("Duração em minutos", InputType.TYPE_CLASS_NUMBER).apply { setText("90") }
        val table = inputField("Número da mesa (opcional)", InputType.TYPE_CLASS_NUMBER)
        val notes = multilineInputField("Notas úteis (opcional)")
        listOf(
            "Nome do cliente" to guest,
            "Contacto (opcional)" to phone,
            "Número de pessoas" to party
        ).forEach { (caption, field) ->
            form.addView(label(caption, 11f, true, forestSoft))
            form.addView(field, fieldParams())
            addGap(form, 5)
        }
        form.addView(label("Data e hora", 11f, true, forestSoft))
        form.addView(dateButton, fieldParams())
        addGap(form, 5)
        listOf(
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
                val parsedDate = selectedReservationDate.time
                val maximumDate = Calendar.getInstance().apply { add(Calendar.YEAR, 1) }.timeInMillis
                when {
                    guestName.isBlank() || guestName.length > 100 -> guest.error = "Indica um nome (máximo 100 caracteres)."
                    partySize == null || partySize !in 1..60 -> party.error = "Usa um número entre 1 e 60."
                    durationMinutes == null || durationMinutes !in 30..480 -> duration.error = "Usa 30 a 480 minutos."
                    parsedDate.time <= System.currentTimeMillis() -> dateButton.error = "Indica uma data e hora futuras."
                    parsedDate.time > maximumDate -> dateButton.error = "A reserva não pode estar a mais de um ano de distância."
                    table.text.toString().isNotBlank() && (tableNumber == null || tableNumber !in 1..100) -> table.error = "Usa uma mesa entre 1 e 100."
                    phone.text.toString().trim().length > 40 -> phone.error = "O contacto pode ter no máximo 40 caracteres."
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

    private fun addInventoryFilters(container: LinearLayout) {
        val filters = if (isCurrentRestaurantOwner()) {
            listOf("Todos", "Esgotados", "Stock baixo", "Sem custo")
        } else {
            listOf("Todos", "Esgotados", "Stock baixo")
        }
        if (selectedInventoryFilter !in filters) selectedInventoryFilter = "Todos"
        val filterCard = makeCard(13)
        filterCard.addView(label("PESQUISAR E FILTRAR STOCK", 9.5f, true, forestSoft))
        addGap(filterCard, 6)
        val searchField = inputField(
            "Nome do artigo ou unidade",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        ).apply {
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                    inventorySearch = text.toString().trim()
                    renderApp()
                    true
                } else {
                    false
                }
            }
        }
        searchField.setText(inventorySearch)
        filterCard.addView(searchField, fieldParams())
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(primaryButton("Pesquisar") {
            inventorySearch = searchField.text.toString().trim()
            renderApp()
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            setMargins(0, 0, dp(5), 0)
        })
        actions.addView(secondaryButton("Limpar") {
            inventorySearch = ""
            selectedInventoryFilter = "Todos"
            renderApp()
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            setMargins(dp(5), 0, 0, 0)
        })
        filterCard.addView(actions)
        addGap(filterCard, 7)
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        filters.forEach { filter ->
            row.addView(
                filterChip(filter, selectedInventoryFilter == filter) {
                    selectedInventoryFilter = filter
                    renderApp()
                },
                LinearLayout.LayoutParams(-2, dp(42)).apply {
                    setMargins(0, 0, dp(6), 0)
                }
            )
        }
        scroll.addView(row)
        filterCard.addView(scroll, LinearLayout.LayoutParams(-1, dp(46)))
        container.addView(filterCard, cardParams())
        addGap(container, 5)
    }

    private fun renderStock(container: LinearLayout) {
        inventoryPageContainer = container
        val isOwner = isCurrentRestaurantOwner()
        pageIntro(container, "Stock e inventário", "Quantidades, limites mínimos, movimentos e desperdício registado.")
        addGap(container, 8)
        if (!hasRemoteRestaurant()) {
            container.addView(emptyCard("O inventário partilhado exige uma conta autenticada e um restaurante configurado na secção Conta."))
            return
        }
        addActionPair(
            container,
            "+  Artigo de stock",
            { showInventoryItemDialog(null) },
            "Atualizar",
            { refreshInventoryItems() }
        )
        addGap(container, 6)
        container.addView(label(
            inventoryRefreshStatusMessage(),
            11f, false, if (inventoryRefreshFailed) red else muted
        ))
        addGap(container, 12)

        if (!backendPrefs.getBoolean("inventory_loaded", false)) {
            val message = when {
                inventoryRefreshFailed -> "Não foi possível carregar o inventário. Usa «Atualizar stock» para tentar novamente."
                inventoryRefreshInFlight -> "A carregar o inventário do servidor…"
                else -> "A preparar a ligação ao inventário…"
            }
            container.addView(emptyCard(message))
            if (!inventoryRefreshInFlight && !inventoryRefreshFailed) refreshInventoryItems()
            return
        }

        val items = remoteInventoryItems()
        if (isOwner &&
            (!inventoryUnitCostCacheLoaded ||
                inventoryUnitCostCacheScope != currentInventoryCostScope()) &&
            !inventoryRefreshInFlight
        ) {
            // Load owner-only costs after a cold app start. Until they arrive,
            // show operational stock but do not imply that missing data is zero.
            refreshInventoryItems()
        }
        if (items.length() == 0) {
            container.addView(emptyCard("Ainda não há artigos de stock. Adiciona ingredientes, bebidas ou consumíveis e define um limite mínimo."))
            return
        }

        if (isOwner) {
            container.addView(
                secondaryButton("Consultar desperdício registado") { showInventoryWasteHistory() },
                LinearLayout.LayoutParams(-1, dp(48))
            )
            addGap(container, 8)
        }

        val stockItems = (0 until items.length()).mapNotNull { items.optJSONObject(it) }
        var lowCount = 0
        var stockValueWithKnownCost = BigDecimal.ZERO.setScale(2)
        var itemsWithoutUnitCost = 0
        stockItems.forEach { item ->
            val quantity = item.optDouble("quantity", 0.0)
            val threshold = item.optDouble("low_stock_threshold", 0.0)
            if (quantity <= threshold) lowCount++
            if (isOwner) {
                val unitCost = if (item.isNull("unit_cost_per_unit")) null else
                    runCatching { BigDecimal(item.optString("unit_cost_per_unit")) }.getOrNull()
                if (unitCost == null) {
                    itemsWithoutUnitCost++
                } else {
                    val itemQuantity = runCatching { BigDecimal(item.optString("quantity", "0")) }.getOrNull()
                    val itemValue = itemQuantity?.let {
                        InventoryWasteCostCalculator.estimate(it, unitCost)
                    }
                    if (itemValue != null) {
                        stockValueWithKnownCost = stockValueWithKnownCost.add(itemValue)
                    }
                }
            }
        }

        val overview = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val overviewCardWidth = if (isOwner) 1f else 1f
        overview.addView(
            statCard("ARTIGOS", stockItems.size.toString(), forestSoft),
            LinearLayout.LayoutParams(0, -2, overviewCardWidth).apply {
                setMargins(0, 0, dp(4), dp(8))
            }
        )
        overview.addView(
            statCard("STOCK BAIXO", lowCount.toString(), if (lowCount > 0) red else green),
            LinearLayout.LayoutParams(0, -2, overviewCardWidth).apply {
                setMargins(dp(2), 0, if (isOwner) dp(2) else 0, dp(8))
            }
        )
        if (isOwner) {
            overview.addView(
                statCard(
                    "SEM CUSTO",
                    itemsWithoutUnitCost.toString(),
                    if (itemsWithoutUnitCost > 0) copper else forestSoft
                ),
                LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins(dp(4), 0, 0, dp(8))
                }
            )
        }
        container.addView(overview)

        val summary = makeCard(13, if (lowCount > 0) paleRed else paleGreen)
        summary.addView(label(
            if (lowCount > 0) "$lowCount artigo(s) no limite mínimo ou abaixo dele"
            else "Todos os artigos acima dos respetivos limites",
            12.5f, true, if (lowCount > 0) red else forest
        ))
        container.addView(summary, cardParams())

        if (isOwner) {
            val valuation = makeCard(13)
            valuation.addView(label(
                "Valor estimado do stock com custo registado: ${formatEuro(stockValueWithKnownCost)}",
                13f, true, forest
            ))
            if (itemsWithoutUnitCost > 0) {
                valuation.addView(label(
                    "$itemsWithoutUnitCost artigo(s) sem custo unitário; ficam fora deste total.",
                    11f, false, muted
                ))
            }
            if (!inventoryUnitCostCacheLoaded ||
                inventoryUnitCostCacheScope != currentInventoryCostScope()
            ) {
                valuation.addView(label(
                    "A atualizar os custos de compra do servidor…",
                    10.5f, false, muted
                ))
            } else {
                valuation.addView(label(
                    "Estimativa baseada no custo unitário atual introduzido pelo restaurante; não é valorização contabilística FIFO ou custo médio.",
                    10.5f, false, muted
                ))
            }
            container.addView(valuation, cardParams())
        }

        addInventoryFilters(container)
        val query = normalizeCustomerMenuSearch(inventorySearch)
        val filteredStockItems = stockItems.filter { item ->
            val quantity = item.optDouble("quantity", 0.0)
            val threshold = item.optDouble("low_stock_threshold", 0.0)
            val unitCost = if (isOwner && !item.isNull("unit_cost_per_unit")) {
                runCatching { BigDecimal(item.optString("unit_cost_per_unit")) }.getOrNull()
            } else null
            val matchesStatus = when (selectedInventoryFilter) {
                "Esgotados" -> quantity <= 0.0
                "Stock baixo" -> quantity > 0.0 && quantity <= threshold
                "Sem custo" -> isOwner && unitCost == null
                else -> true
            }
            val matchesSearch = query.isBlank() || normalizeCustomerMenuSearch(
                "${item.optString("name")} ${item.optString("unit")}"
            ).contains(query)
            matchesStatus && matchesSearch
        }
        container.addView(label(
            "${filteredStockItems.size} de ${stockItems.size} artigo(s)",
            11f, true, forestSoft
        ))
        addGap(container, 6)
        if (filteredStockItems.isEmpty()) {
            container.addView(emptyCard("Não há artigos de stock que correspondam à pesquisa e ao filtro selecionado."))
            return
        }

        for (item in filteredStockItems) {
            val id = item.optString("id")
            val name = item.optString("name", "Artigo")
            val unit = item.optString("unit", "unidades")
            val quantity = item.optDouble("quantity", 0.0)
            val threshold = item.optDouble("low_stock_threshold", 0.0)
            val unitCost = if (isOwner && !item.isNull("unit_cost_per_unit")) {
                runCatching { BigDecimal(item.optString("unit_cost_per_unit")) }.getOrNull()
            } else null
            val isLow = quantity <= threshold
            val card = makeCard(15)
            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(label(name, 16f, true, forestDeep), LinearLayout.LayoutParams(0, -2, 1f))
            if (isLow) {
                top.addView(statusBadge(
                    if (quantity <= 0.0) "Esgotado" else "Stock baixo",
                    red,
                    paleRed
                ))
            }
            card.addView(top)
            addGap(card, 8)
            card.addView(label(
                "${formatQuantity(quantity)} $unit",
                22f, true, if (isLow) red else forest
            ))
            card.addView(label("Alerta a ${formatQuantity(threshold)} $unit", 11.5f, false, muted))
            if (isOwner) {
                card.addView(label(
                    unitCost?.let { "Custo de compra: ${formatUnitCost(it)} / $unit" }
                        ?: "Custo de compra por unidade não definido",
                    11.5f, false, muted
                ))
            }
            addGap(card, 10)
            card.addView(
                secondaryButton("Editar artigo e limite") { showInventoryItemDialog(item) },
                LinearLayout.LayoutParams(-1, dp(48))
            )
            addGap(card, 5)
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(
                secondaryButton("+ Entrada") { showStockAdjustmentDialog(id, name, unit, true) },
                LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(0, 0, dp(4), 0) }
            )
            buttons.addView(
                secondaryButton("− Saída") { showStockAdjustmentDialog(id, name, unit, false) },
                LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(4), 0, 0, 0) }
            )
            card.addView(buttons)
            addGap(card, 5)
            card.addView(
                secondaryButton("Registar desperdício") { showInventoryWasteDialog(id, name, unit) },
                LinearLayout.LayoutParams(-1, dp(48))
            )
            addGap(card, 5)
            card.addView(
                secondaryButton("Ver histórico de movimentos") { showInventoryMovements(id, name) },
                LinearLayout.LayoutParams(-1, dp(48))
            )
            container.addView(card, cardParams())
        }
    }

    private fun formatEuro(value: BigDecimal): String =
        NumberFormat.getCurrencyInstance(Locale("pt", "PT")).format(value)

    private fun formatUnitCost(value: BigDecimal): String =
        "€ " + DecimalFormat("0.0000", DecimalFormatSymbols.getInstance(Locale("pt", "PT"))).format(value)

    private fun formatQuantity(value: Double): String =
        String.format(Locale("pt", "PT"), "%.3f", value).trimEnd('0').trimEnd(',')

    private fun scrollableForm(content: View): ScrollView =
        ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(content)
        }

    private fun inventoryUnitSpinner(stockUnit: String): Spinner {
        val normalizedStockUnit = stockUnit.trim().ifBlank { "unidades" }
        val options = InventoryUnitConverter.compatibleUnits(normalizedStockUnit)
        return Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                options
            )
        }
    }

    private fun parseInventoryQuantityToStockUnit(
        rawValue: String,
        enteredUnit: String,
        stockUnit: String
    ): Double =
        InventoryQuantityParser.parseToStockUnit(
            rawValue,
            enteredUnit,
            stockUnit
        ).toDouble()

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
        val isOwner = isCurrentRestaurantOwner()
        val name = inputField("Nome do artigo", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES).apply {
            setText(item?.optString("name", "").orEmpty())
        }
        val unit = inputField("Unidade (kg, L, unidades…)", InputType.TYPE_CLASS_TEXT).apply {
            setText(item?.optString("unit", "unidades") ?: "unidades")
            if (item != null && !isOwner) {
                isEnabled = false
                contentDescription = "Unidade base do artigo. Só o proprietário pode alterá-la."
            }
        }
        val quantity = inputField("Quantidade atual", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).apply {
            setText(formatQuantity(item?.optDouble("quantity", 0.0) ?: 0.0))
        }
        val threshold = inputField("Avisar quando for igual ou inferior a", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).apply {
            setText(formatQuantity(item?.optDouble("low_stock_threshold", 0.0) ?: 0.0))
        }
        val unitCost = inputField("Custo de compra por unidade (€)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).apply {
            setText(item?.let { if (it.isNull("unit_cost_per_unit")) "" else it.optString("unit_cost_per_unit") } ?: "")
        }
        val note = inputField("Motivo (opcional)", InputType.TYPE_CLASS_TEXT).apply { setText(if (item == null) "Stock inicial" else "Correção de inventário") }
        listOf(
            "Nome" to name,
            "Unidade base" to unit,
            "Quantidade" to quantity,
            "Limite mínimo" to threshold
        ).forEach { (caption, field) ->
            form.addView(label(caption, 11f, true, forestSoft))
            form.addView(field, fieldParams())
            addGap(form, 5)
        }
        if (isOwner) {
            form.addView(label("Custo de compra por unidade de stock (€)", 11f, true, forestSoft))
            form.addView(unitCost, fieldParams())
            addGap(form, 4)
            form.addView(label(
                "Indica o custo usando a unidade definida para este artigo (por exemplo, €/kg quando a unidade é kg). Este valor só é visível ao proprietário.",
                10.5f, false, muted
            ))
            addGap(form, 5)
        }
        form.addView(label("Nota do movimento", 11f, true, forestSoft))
        form.addView(note, fieldParams())
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
                val parsedUnitCost = if (isOwner) {
                    try {
                        InventoryUnitCostValidator.parseOrNull(unitCost.text.toString())
                    } catch (error: IllegalArgumentException) {
                        unitCost.error = error.message ?: "Custo unitário inválido."
                        return@setOnClickListener
                    }
                } else null
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
                                .put("unit_cost_per_unit", parsedUnitCost ?: JSONObject.NULL)
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
                                SupabaseApi.createInventoryItemWithCost(
                                    token, requireNotNull(requestId), restaurantId,
                                    itemName, itemUnit, qty, low, itemNote, parsedUnitCost
                                )
                            } else {
                                SupabaseApi.saveInventoryItemWithCost(
                                    token, restaurantId, item.optString("id"),
                                    itemName, itemUnit, qty, low, itemNote, parsedUnitCost
                                )
                            }
                            val latest = loadInventoryItemsForCurrentRole(token, restaurantId)
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
        dialog.window?.setLayout(-1, dp(580))
    }

    private fun showStockAdjustmentDialog(
        itemId: String,
        itemName: String,
        unit: String,
        isInbound: Boolean
    ) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(10))
        }
        val enteredUnitSpinner = inventoryUnitSpinner(unit)
        val amount = inputField(
            "Quantidade",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        )
        val note = inputField("Nota (opcional)", InputType.TYPE_CLASS_TEXT).apply {
            setText(if (isInbound) "Entrada de stock" else "Consumo/quebra")
        }

        form.addView(label("Artigo: $itemName", 12f, true, forest))
        addGap(form, 8)
        form.addView(label("Unidade da quantidade introduzida", 11f, true, forestSoft))
        form.addView(enteredUnitSpinner, LinearLayout.LayoutParams(-1, dp(48)))
        addGap(form, 5)
        form.addView(label(
            "Quantidade a ${if (isInbound) "adicionar" else "retirar"}",
            11f, true, forestSoft
        ))
        form.addView(amount, fieldParams())
        addGap(form, 5)
        form.addView(label(
            "O valor é convertido para $unit antes de atualizar o stock.",
            10.5f, false, muted
        ))
        form.addView(label("Regista a razão para manter um histórico útil.", 11f, false, muted))
        form.addView(note, fieldParams())

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (isInbound) "Entrada de stock" else "Saída de stock")
            .setView(scrollableForm(form))
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Registar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = try {
                    parseInventoryQuantityToStockUnit(
                        amount.text.toString(),
                        enteredUnitSpinner.selectedItem?.toString().orEmpty(),
                        unit
                    )
                } catch (error: IllegalArgumentException) {
                    amount.error = error.message ?: "Quantidade ou unidade inválida."
                    return@setOnClickListener
                }
                if (!value.isFinite() || value <= 0.0 || value > 999999999.0) {
                    amount.error = "A quantidade convertida está fora dos limites."
                    return@setOnClickListener
                }
                if (note.text.toString().length > 500) {
                    note.error = "Máximo de 500 caracteres."
                    return@setOnClickListener
                }

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
                    val latest = loadInventoryItemsForCurrentRole(backendToken(), restaurantId)
                    backendPrefs.edit()
                        .putString("remote_inventory_json", latest.toString())
                        .putBoolean("inventory_loaded", true)
                        .apply()
                    "OK"
                }) {
                    dialog.dismiss()
                    renderApp()
                    pendingRequestIds.complete(requestOperation, requestAccountId, signature, requestId)
                }
            }
        }
        dialog.show()
    }

    private fun showInventoryWasteDialog(itemId: String, itemName: String, unit: String) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(10))
        }
        val enteredUnitSpinner = inventoryUnitSpinner(unit)
        val amount = inputField("Quantidade desperdiçada", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val reason = Spinner(this)
        val reasonAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, InventoryWasteNotes.reasons)
        reasonAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        reason.adapter = reasonAdapter
        val details = inputField("Observação opcional", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)

        form.addView(label("Artigo: $itemName", 12f, true, forest))
        addGap(form, 7)
        form.addView(label("Unidade da quantidade introduzida", 11f, true, forestSoft))
        form.addView(enteredUnitSpinner, LinearLayout.LayoutParams(-1, dp(48)))
        addGap(form, 5)
        form.addView(label("Quantidade desperdiçada", 11f, true, forestSoft))
        form.addView(amount, fieldParams())
        addGap(form, 5)
        form.addView(label("O valor é convertido para $unit antes de registar a perda.", 10.5f, false, muted))
        addGap(form, 5)
        form.addView(label("Motivo do desperdício", 11f, true, forestSoft))
        form.addView(reason, LinearLayout.LayoutParams(-1, dp(48)))
        addGap(form, 5)
        form.addView(label("Detalhe (máximo 350 caracteres)", 11f, true, forestSoft))
        form.addView(details, fieldParams())
        addGap(form, 5)
        form.addView(label(
            "O registo reduz o stock no servidor e fica no histórico. O servidor impede que a quantidade fique negativa.",
            10.5f, false, muted
        ))

        val dialog = AlertDialog.Builder(this)
            .setTitle("Registar desperdício")
            .setView(scrollableForm(form))
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Registar perda", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val rawValue = amount.text.toString()
                val value = try {
                    parseInventoryQuantityToStockUnit(
                        rawValue,
                        enteredUnitSpinner.selectedItem?.toString().orEmpty(),
                        unit
                    )
                } catch (error: IllegalArgumentException) {
                    amount.error = error.message ?: "Quantidade ou unidade inválida."
                    return@setOnClickListener
                }
                if (!value.isFinite() || value <= 0.0 || value > 999999999.0) {
                    amount.error = "A quantidade convertida está fora dos limites."
                    return@setOnClickListener
                }
                val movementNote = try {
                    InventoryWasteNotes.build(
                        reason.selectedItem?.toString().orEmpty(),
                        details.text.toString()
                    )
                } catch (error: IllegalArgumentException) {
                    details.error = error.message ?: "Revê o motivo e a observação."
                    return@setOnClickListener
                }

                val restaurantId = backendRestaurantId()
                val delta = -value
                val signature = JSONObject()
                    .put("restaurant_id", restaurantId)
                    .put("item_id", itemId)
                    .put("delta", delta)
                    .put("note", movementNote)
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
                    val token = backendToken()
                    SupabaseApi.adjustInventoryStock(
                        token, restaurantId, itemId, delta, movementNote, requestId
                    )
                    val latest = loadInventoryItemsForCurrentRole(token, restaurantId)
                    backendPrefs.edit()
                        .putString("remote_inventory_json", latest.toString())
                        .putBoolean("inventory_loaded", true)
                        .apply()
                    "OK"
                }) {
                    dialog.dismiss()
                    renderApp()
                    pendingRequestIds.complete(requestOperation, requestAccountId, signature, requestId)
                }
            }
        }
        dialog.show()
        dialog.window?.setLayout(-1, dp(560))
    }

    private fun showInventoryWasteHistory() {
        if (!isCurrentRestaurantOwner()) {
            AlertDialog.Builder(this)
                .setMessage("O histórico de custos do desperdício está disponível apenas ao proprietário.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        runBackend({
            SupabaseApi.listInventoryWasteMovements(
                backendToken(), backendRestaurantId(), 100
            ).toString()
        }) { raw ->
            val rows = JSONArray(raw)
            if (rows.length() == 0) {
                AlertDialog.Builder(this)
                    .setTitle("Desperdício registado")
                    .setMessage("Ainda não existem perdas registadas no inventário.")
                    .setPositiveButton("Fechar", null)
                    .show()
                return@runBackend
            }

            val itemNames = mutableMapOf<String, String>()
            val inventory = remoteInventoryItems()
            for (index in 0 until inventory.length()) {
                val item = inventory.optJSONObject(index) ?: continue
                itemNames[item.optString("id")] = item.optString("name", "Artigo")
            }
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "PT"))
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            val movementCosts = mutableListOf<BigDecimal?>()
            val message = buildString {
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: continue
                    val itemId = row.optString("item_id")
                    val item = itemNames[itemId]
                    val quantity = runCatching {
                        BigDecimal(row.get("delta").toString()).abs()
                    }.getOrNull()
                    val movementUnit = row.optString("unit_at_movement").trim()
                        .takeIf { it.isNotBlank() } ?: "unidade não registada"
                    val unitCostAtMovement = if (row.isNull("unit_cost_at_movement")) null else
                        runCatching { BigDecimal(row.get("unit_cost_at_movement").toString()) }.getOrNull()
                    val estimatedCost = if (quantity == null) null else
                        runCatching { InventoryWasteCostCalculator.estimate(quantity, unitCostAtMovement) }.getOrNull()
                    movementCosts.add(estimatedCost)

                    val date = runCatching {
                        dateFormat.format(isoFormat.parse(row.optString("created_at")) ?: Date())
                    }.getOrElse { row.optString("created_at").take(16) }
                    append(item ?: "Artigo de stock")
                    append(" · −").append(quantity?.let { formatQuantity(it.toDouble()) } ?: "—")
                    append(' ').append(movementUnit)
                    append(" · ").append(date).append('\n')
                    append(row.optString("note").removePrefix(InventoryWasteNotes.PREFIX))
                    append('\n')
                    append(
                        estimatedCost?.let { "Custo estimado na data: ${formatEuro(it)}" }
                            ?: "Custo não registado na data desta perda"
                    )
                    if (index < rows.length() - 1) append("\n\n")
                }
                val costSummary = InventoryWasteCostCalculator.summarize(movementCosts)
                append("\n\nTotal estimado das perdas com custo registado: ${formatEuro(costSummary.knownTotal)}")
                if (costSummary.movementsWithoutCost > 0) {
                    append("\n")
                    append(costSummary.movementsWithoutCost)
                    append(" perda(s) sem custo conhecido; não foram estimadas.")
                }
                append("\n\nConsulta limitada às 100 perdas mais recentes.")
            }
            AlertDialog.Builder(this)
                .setTitle("Desperdício registado")
                .setMessage(message)
                .setPositiveButton("Fechar", null)
                .show()
        }
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
                    val movementUnit = row.optString("unit_at_movement").trim()
                        .takeIf { it.isNotBlank() } ?: "unidade não registada"
                    val date = runCatching { dateFormat.format(isoFormat.parse(row.optString("created_at")) ?: Date()) }
                        .getOrElse { row.optString("created_at").take(16) }
                    append(date).append(" · ")
                    append(if (delta > 0) "+" else "").append(formatQuantity(delta))
                    append(' ').append(movementUnit)
                    append(" → saldo ").append(formatQuantity(row.optDouble("quantity_after", 0.0)))
                    append(' ').append(movementUnit)
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

    private fun assistantSnapshot(): StudioMesaAssistantSnapshot {
        val remote = hasRemoteRestaurant()
        val menu = (if (remote) remoteMenuEntries() else store.getMenu()).map { item ->
            AssistantMenuItem(
                name = item.name,
                sellingPrice = item.price.takeIf { it.isFinite() && it > 0.0 }?.let { BigDecimal.valueOf(it) } ?: BigDecimal.ZERO,
                unitCost = item.cost.takeIf { it.isFinite() && it > 0.0 }?.let { BigDecimal.valueOf(it) } ?: BigDecimal.ZERO,
                category = item.category,
                description = item.description
            )
        }
        val statuses: List<String> = if (remote) {
            val orders = remoteOrders()
            (0 until orders.length()).mapNotNull { orders.optJSONObject(it)?.optString("status", "Aberto") }
        } else {
            store.getOrders().map { it.status }
        }
        val active = statuses.count { it !in setOf("Concluído", "Cancelado") }
        val preparing = statuses.count { it == "Em preparação" }
        val stockSnapshotFresh = AssistantStockSnapshotPolicy.isFresh(
            System.currentTimeMillis(), inventoryUpdatedAtMillis
        )
        val stockLoaded = isPremium && remote &&
            backendPrefs.getBoolean("inventory_loaded", false) &&
            !inventoryRefreshFailed && stockSnapshotFresh
        val stockItems = if (stockLoaded) {
            val items = remoteInventoryItems()
            (0 until items.length()).mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString("name").trim()
                if (name.isBlank()) return@mapNotNull null
                val quantity = item.optDouble("quantity", 0.0)
                val minimum = item.optDouble("low_stock_threshold", 0.0)
                if (!quantity.isFinite() || !minimum.isFinite()) return@mapNotNull null
                val unit = item.optString("unit", "").trim()
                val unitCost = if (item.isNull("unit_cost_per_unit")) null else
                    runCatching { BigDecimal(item.get("unit_cost_per_unit").toString()) }.getOrNull()
                AssistantStockItem(
                    name,
                    BigDecimal(item.optString("quantity", "0")),
                    BigDecimal(item.optString("low_stock_threshold", "0")),
                    unit,
                    unitCost
                )
            }
        } else emptyList()
        val reservationsAreLoaded = hasCalculator && remote && reservationsLoaded
        val pendingReservations = if (reservationsAreLoaded) {
            (0 until remoteReservations().length()).count { index ->
                remoteReservations().optJSONObject(index)?.optString("status") == "Pendente"
            }
        } else 0
        val salesLines = if (remote) {
            val orders = remoteOrders()
            (0 until orders.length()).mapNotNull { index ->
                val order = orders.optJSONObject(index) ?: return@mapNotNull null
                AssistantSaleLine(
                    itemName = order.optString("item_name").trim(),
                    quantity = order.optInt("quantity", 1),
                    status = order.optString("status", "Aberto")
                ).takeIf { it.itemName.isNotBlank() }
            }
        } else {
            store.getOrders().map { AssistantSaleLine(it.itemName, it.quantity, it.status) }
        }
        return StudioMesaAssistantSnapshot(
            isDemo = !remote,
            restaurantConfigured = remote,
            menuItems = menu,
            activeOrderCount = active,
            preparationOrderCount = preparing,
            stockFeatureAvailable = isPremium,
            stockLoaded = stockLoaded,
            stockItems = stockItems,
            reservationsFeatureAvailable = hasCalculator,
            reservationsLoaded = reservationsAreLoaded,
            pendingReservations = pendingReservations,
            salesLines = salesLines
        )
    }

    private fun assistantActionTarget(page: String?): String? {
        if (page.isNullOrBlank()) return null
        val target = if (page == "Preço Justo" && page !in availablePages()) "Carta" else page
        return target.takeIf { it in availablePages() }
    }

    private fun renderRestaurantPriceAdvisor(container: LinearLayout) {
        val usesRemoteMenu = hasRemoteRestaurant()
        val isOwner = backendPrefs.getString("restaurant_role", "").orEmpty() == "owner"

        val card = makeCard(17)
        card.addView(label("Consultor de preços", 17f, true, forest))
        addGap(card, 5)
        card.addView(label(
            "Compara os custos unitários e os preços registados. O objetivo é configurável; 30% é apenas um ponto de partida, não uma regra universal.",
            12.5f, false, muted
        ))
        addGap(card, 9)

        val targetField = inputField(
            "Ex.: 30",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        ).apply {
            setText("30")
            filters = arrayOf(InputFilter.LengthFilter(6))
        }
        card.addView(label("Objetivo de custo direto sobre o preço (%)", 10f, true, forestSoft))
        card.addView(targetField, fieldParams())
        addGap(card, 7)

        card.addView(primaryButton("Analisar preços da carta") {
            val items = if (usesRemoteMenu) remoteMenuEntries() else store.getMenu()
            if (items.isEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("Sem artigos para analisar")
                    .setMessage(
                        if (usesRemoteMenu) {
                            "A carta carregada do servidor está vazia. Abre «Carta» e atualiza os dados antes de analisar."
                        } else {
                            "A carta de demonstração está vazia. Adiciona artigos com custos e preços para os analisar."
                        }
                    )
                    .setPositiveButton("OK", null)
                    .show()
                return@primaryButton
            }

            val report = try {
                RestaurantAdvisorEngine.analyzeMenu(items, targetField.text.toString())
            } catch (error: IllegalArgumentException) {
                targetField.error = error.message ?: "Verifica a percentagem."
                return@primaryButton
            }

            if (usesRemoteMenu && isOwner) {
                // Read the permission from the server at analysis time. The apply RPC
                // checks it again and never trusts this client-side result.
                runBackend(
                    task = { SupabaseApi.getRestaurantPriceAdviserEnabled(backendToken()).toString() },
                    onError = {
                        showRestaurantMenuAdviceDialog(
                            report, usesRemoteMenu, allowApplying = false, permissionCheckFailed = true
                        )
                    },
                    onSuccess = { response ->
                        showRestaurantMenuAdviceDialog(
                            report, usesRemoteMenu,
                            allowApplying = response.trim().equals("true", ignoreCase = true)
                        )
                    }
                )
            } else {
                showRestaurantMenuAdviceDialog(report, usesRemoteMenu, allowApplying = false)
            }
        }, LinearLayout.LayoutParams(-1, dp(46)))

        container.addView(card, cardParams())
        addGap(container, 9)

        when {
            !usesRemoteMenu -> {
                val info = makeCard(13, copperPale)
                info.addView(label("Aplicação de preços desativada na demonstração", 13.5f, true, forest))
                addGap(info, 4)
                info.addView(label(
                    "Os dados locais são exemplos. A aplicação de preços só pode ser ativada num restaurante ligado ao servidor; esta análise nunca altera os dados de demonstração.",
                    12f, false, ink
                ))
                container.addView(info, cardParams())
            }
            !isOwner -> {
                val info = makeCard(13, copperPale)
                info.addView(label("Apenas o proprietário pode autorizar alterações", 13.5f, true, forest))
                addGap(info, 4)
                info.addView(label(
                    "Podes consultar as recomendações em modo de leitura. A ativação, a aplicação dos preços e a consulta do histórico ficam reservadas ao proprietário.",
                    12f, false, ink
                ))
                container.addView(info, cardParams())
            }
            else -> renderPriceAdviserOwnerControls(container)
        }
    }

    private fun renderPriceAdviserOwnerControls(container: LinearLayout) {
        val card = makeCard(15, paleGreen)
        card.addView(label("Autorização do proprietário", 15f, true, forest))
        addGap(card, 5)
        card.addView(label(
            "A autorização vem desligada por defeito. Quando a ativares, cada preço continua a exigir confirmação individual. O servidor volta a verificar o proprietário, o preço atual, o custo atual e a recomendação antes de alterar a carta.",
            12.5f, false, ink
        ))
        addGap(card, 7)

        val status = label("Estado ainda não consultado no servidor.", 12f, true, muted)
        card.addView(status)
        addGap(card, 5)

        var settingLoaded = false
        var enabled = false
        var settingButton: Button? = null
        val permissionButton = secondaryButton("Consultar autorização no servidor") {
            if (!settingLoaded) {
                runBackend(
                    task = { SupabaseApi.getRestaurantPriceAdviserEnabled(backendToken()).toString() },
                    onError = { error ->
                        status.text = "Não foi possível confirmar a autorização no servidor."
                        AlertDialog.Builder(this)
                            .setTitle("Autorização não confirmada")
                            .setMessage(friendlyBackendError(error.message))
                            .setPositiveButton("OK", null)
                            .show()
                    },
                    onSuccess = { response ->
                        enabled = response.trim().equals("true", ignoreCase = true)
                        settingLoaded = true
                        status.text = if (enabled) {
                            "Ativa no servidor. Cada alteração ainda exige confirmação."
                        } else {
                            "Desligada no servidor. Nenhum preço pode ser aplicado."
                        }
                        settingButton?.text = if (enabled) {
                            "Desativar aplicação após confirmação"
                        } else {
                            "Ativar aplicação após confirmação"
                        }
                    }
                )
            } else {
                val nextValue = !enabled
                AlertDialog.Builder(this)
                    .setTitle(if (nextValue) "Ativar autorização de preços?" else "Desativar autorização de preços?")
                    .setMessage(
                        if (nextValue) {
                            "Permite ao proprietário aplicar individualmente os preços sugeridos pelo consultor. Cada artigo exige uma segunda confirmação, e o servidor regista o preço anterior, o novo preço e os dados usados no cálculo. Não existe aplicação em lote."
                        } else {
                            "Impede novas aplicações de preços pelo consultor. O histórico das alterações anteriores mantém-se disponível."
                        }
                    )
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton(if (nextValue) "Ativar" else "Desativar") { _, _ ->
                        runBackend(
                            task = {
                                val changed = SupabaseApi.setRestaurantPriceAdviserEnabled(
                                    backendToken(), nextValue
                                )
                                if (changed != nextValue) {
                                    throw IllegalStateException("O servidor não confirmou a alteração da autorização.")
                                }
                                SupabaseApi.getRestaurantPriceAdviserEnabled(backendToken()).toString()
                            },
                            onError = { error ->
                                AlertDialog.Builder(this)
                                    .setTitle("Autorização não alterada")
                                    .setMessage(friendlyBackendError(error.message))
                                    .setPositiveButton("OK", null)
                                    .show()
                            },
                            onSuccess = { response ->
                                enabled = response.trim().equals("true", ignoreCase = true)
                                settingLoaded = true
                                status.text = if (enabled) {
                                    "Ativa no servidor. Cada alteração ainda exige confirmação."
                                } else {
                                    "Desligada no servidor. Nenhum preço pode ser aplicado."
                                }
                                settingButton?.text = if (enabled) {
                                    "Desativar aplicação após confirmação"
                                } else {
                                    "Ativar aplicação após confirmação"
                                }
                            }
                        )
                    }
                    .show()
            }
        }
        settingButton = permissionButton
        card.addView(permissionButton, LinearLayout.LayoutParams(-1, dp(44)))
        addGap(card, 5)

        card.addView(secondaryButton("Ver histórico de alterações") {
            runBackend(
                task = { SupabaseApi.listRestaurantPriceAdviserAudit(backendToken(), 20).toString() },
                onError = { error ->
                    AlertDialog.Builder(this)
                        .setTitle("Histórico indisponível")
                        .setMessage(friendlyBackendError(error.message))
                        .setPositiveButton("OK", null)
                        .show()
                },
                onSuccess = { response ->
                    showPriceAdviserAuditDialog(JSONArray(response))
                }
            )
        }, LinearLayout.LayoutParams(-1, dp(44)))

        container.addView(card, cardParams())
        addGap(container, 9)
    }

    private fun showRestaurantMenuAdviceDialog(
        report: RestaurantMenuAdviceReport,
        usesRemoteMenu: Boolean,
        allowApplying: Boolean,
        permissionCheckFailed: Boolean = false
    ) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        content.addView(label(
            "Objetivo definido: ${formatAdvicePercent(report.targetCostPercent)}% do preço final.",
            14f, true, forest
        ))
        addGap(content, 5)
        content.addView(label(
            if (usesRemoteMenu) {
                "Fonte: carta do servidor atualmente carregada em cache. O servidor rejeitará qualquer alteração se o preço ou custo atual já tiver mudado."
            } else {
                "Fonte: dados locais de demonstração; não representam necessariamente um restaurante real e não podem ser alterados por esta função."
            },
            12f, false, muted
        ))
        addGap(content, 5)
        content.addView(label(
            "A percentagem é calculada sobre o custo unitário registado. O cálculo não inclui IVA, salários, renda, energia, comissões, desperdício ou outros encargos, e não garante rentabilidade.",
            12f, false, muted
        ))
        addGap(content, 5)
        content.addView(label(
            "Resumo: ${report.itemsNeedingReview} artigos precisam de revisão; ${report.itemsWithIncompleteData} têm dados incompletos.",
            12.5f, true, forest
        ))
        addGap(content, 5)

        val ownerOfRemote = usesRemoteMenu &&
            backendPrefs.getString("restaurant_role", "").orEmpty() == "owner"
        val permissionNote = when {
            !usesRemoteMenu -> "Modo de demonstração: não existe ligação a um restaurante real."
            !ownerOfRemote -> "Só o proprietário pode autorizar ou aplicar preços."
            permissionCheckFailed -> "Não foi possível confirmar a autorização no servidor; os botões de alteração estão indisponíveis."
            allowApplying -> "Autorização ativa no servidor. Cada preço é aplicado individualmente, só após nova confirmação."
            else -> "A autorização está desligada no servidor. Ativa-a na secção do proprietário para poderes aplicar recomendações."
        }
        content.addView(label(permissionNote, 12f, true, forest))
        addGap(content, 5)

        var reportDialog: AlertDialog? = null
        report.items.forEachIndexed { index, advice ->
            val itemCard = makeCard(12)
            itemCard.addView(label("${index + 1}. ${advice.itemName}", 14f, true, forest))
            addGap(itemCard, 4)
            val statusText = when (advice.status) {
                MenuPriceAdviceStatus.INCOMPLETE_DATA -> "Dados incompletos"
                MenuPriceAdviceStatus.URGENT_REVIEW -> "Revisão urgente"
                MenuPriceAdviceStatus.REVIEW -> "Rever custo ou preço"
                MenuPriceAdviceStatus.WITHIN_TARGET -> "Dentro do objetivo configurado"
            }
            val itemDetails = StringBuilder()
            itemDetails.append("Estado: ").append(statusText).append('\n')
            advice.currentPrice?.let {
                itemDetails.append("Preço atual: ").append(money(it)).append('\n')
            }
            advice.unitCost?.let {
                itemDetails.append("Custo unitário: ").append(money(it)).append('\n')
            }
            advice.directCostPercent?.let {
                itemDetails.append("Custo direto / preço: ").append(formatAdvicePercent(it)).append("%\n")
            }
            advice.indicativePriceAtTarget?.let {
                itemDetails.append("Preço teórico para este objetivo: ").append(money(it)).append('\n')
            }
            itemDetails.append(advice.explanation)
            itemCard.addView(label(itemDetails.toString(), 12f, false, ink))

            val oldPrice = advice.currentPrice
            val suggestedPrice = advice.indicativePriceAtTarget
            val canApply = ownerOfRemote &&
                allowApplying &&
                advice.itemId?.isNotBlank() == true &&
                advice.status in setOf(MenuPriceAdviceStatus.REVIEW, MenuPriceAdviceStatus.URGENT_REVIEW) &&
                oldPrice != null &&
                suggestedPrice != null &&
                suggestedPrice > oldPrice

            if (canApply) {
                addGap(itemCard, 6)
                itemCard.addView(primaryButton(
                    "Rever e aplicar ${money(suggestedPrice!!)}"
                ) {
                    confirmApplyRecommendedPrice(advice, report.targetCostPercent) {
                        reportDialog?.dismiss()
                    }
                }, LinearLayout.LayoutParams(-1, dp(44)))
            } else if (ownerOfRemote && allowApplying &&
                advice.status in setOf(MenuPriceAdviceStatus.REVIEW, MenuPriceAdviceStatus.URGENT_REVIEW) &&
                advice.itemId.isNullOrBlank()
            ) {
                addGap(itemCard, 5)
                itemCard.addView(label(
                    "Atualiza a carta no servidor para carregar o identificador deste artigo antes de aplicar uma alteração.",
                    11.5f, false, muted
                ))
            }
            content.addView(itemCard, cardParams())
            addGap(content, 4)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            addView(content)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Análise de preços da carta")
            .setView(scroll)
            .setPositiveButton("Fechar", null)
            .create()
        reportDialog = dialog
        dialog.show()
    }

    private fun confirmApplyRecommendedPrice(
        advice: MenuPriceAdvice,
        targetCostPercent: java.math.BigDecimal,
        onStartApplying: () -> Unit
    ) {
        val itemId = advice.itemId?.takeIf { it.isNotBlank() }
        val previousPrice = advice.currentPrice
        val newPrice = advice.indicativePriceAtTarget
        if (!hasRemoteRestaurant() ||
            backendPrefs.getString("restaurant_role", "").orEmpty() != "owner" ||
            itemId == null ||
            previousPrice == null ||
            newPrice == null ||
            advice.unitCost == null ||
            newPrice <= previousPrice
        ) {
            AlertDialog.Builder(this)
                .setTitle("Alteração indisponível")
                .setMessage("Atualiza a carta e confirma a autorização do proprietário antes de aplicar um preço.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Confirmar alteração de preço?")
            .setMessage(
                "Artigo: ${advice.itemName}\n" +
                    "Preço atual: ${money(previousPrice)}\n" +
                    "Custo unitário registado: ${money(advice.unitCost)}\n" +
                    "Novo preço: ${money(newPrice)}\n" +
                    "Objetivo de custo direto: ${formatAdvicePercent(targetCostPercent)}%\n\n" +
                    "Ao confirmar, este preço será alterado na carta do restaurante e a operação ficará registada no histórico. O servidor volta a calcular a recomendação usando o custo e o preço atuais. IVA e custos indiretos não estão incluídos."
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Confirmar e aplicar") { _, _ ->
                onStartApplying()
                val actionId = java.util.UUID.randomUUID().toString()
                runBackend(
                    task = {
                        val token = backendToken()
                        val restaurantId = backendRestaurantId()
                        val applied = SupabaseApi.applyRestaurantAdvisedMenuPrice(
                            token, actionId, itemId, previousPrice, newPrice, targetCostPercent
                        )
                        // The server price and audit row have committed. Cache refresh is
                        // best-effort and must not relabel a successful mutation as failure.
                        val refreshedMenu = runCatching {
                            SupabaseApi.listMenuItems(token, restaurantId)
                        }.getOrNull()
                        val cacheUpdated = refreshedMenu?.let { menu ->
                            runCatching {
                                backendPrefs.edit()
                                    .putString("remote_menu_json", menu.toString())
                                    .commit()
                            }.getOrDefault(false)
                        } ?: false
                        JSONObject()
                            .put("result", applied)
                            .put("cache_updated", cacheUpdated)
                            .toString()
                    },
                    onError = {
                        AlertDialog.Builder(this)
                            .setTitle("Resultado não confirmado")
                            .setMessage(
                                "O servidor não confirmou a resposta. Antes de repetir, consulta o histórico e atualiza a carta: uma resposta de rede perdida pode ter ocorrido depois de a alteração ficar registada."
                            )
                            .setPositiveButton("OK", null)
                            .show()
                    },
                    onSuccess = { raw ->
                        val outcome = JSONObject(raw)
                        val result = outcome.getJSONObject("result")
                        val cacheUpdated = outcome.optBoolean("cache_updated", false)
                        if (cacheUpdated) renderApp()
                        val replay = result.optBoolean("was_replay", false)
                        val statusLine = if (replay) {
                            "A operação já constava no histórico; não foi aplicado um segundo ajuste."
                        } else {
                            "O servidor aplicou e registou a alteração."
                        }
                        val cacheLine = if (cacheUpdated) {
                            "A cópia local da carta foi atualizada."
                        } else {
                            "Não foi possível atualizar a cópia local da carta. Abre «Carta» e atualiza os dados antes de voltar a analisar."
                        }
                        AlertDialog.Builder(this)
                            .setTitle("Preço registado")
                            .setMessage(
                                "${advice.itemName}\n" +
                                    "Preço anterior: ${money(previousPrice)}\n" +
                                    "Novo preço: ${money(newPrice)}\n\n" +
                                    "$statusLine\n$cacheLine\nConsulta o histórico para rever a operação."
                            )
                            .setPositiveButton("OK", null)
                            .show()
                    }
                )
            }
            .show()
    }

    private fun showPriceAdviserAuditDialog(rows: JSONArray) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        if (rows.length() == 0) {
            content.addView(label(
                "Ainda não existem alterações de preço registadas para este restaurante.",
                13f, false, muted
            ))
        } else {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val card = makeCard(12)
                card.addView(label(row.optString("item_name", "Artigo"), 14f, true, forest))
                addGap(card, 3)
                val details = StringBuilder()
                    .append("Preço anterior: ").append(money(row.optDouble("previous_price", 0.0))).append('\n')
                    .append("Novo preço: ").append(money(row.optDouble("applied_price", 0.0))).append('\n')
                    .append("Custo unitário registado: ").append(money(row.optDouble("unit_cost", 0.0))).append('\n')
                    .append("Objetivo de custo direto: ").append(row.optString("target_cost_percent")).append("%\n")
                    .append("Data: ").append(row.optString("acted_at", "Data indisponível"))
                card.addView(label(details.toString(), 12f, false, ink))
                content.addView(card, cardParams())
                addGap(content, 4)
            }
        }
        val scroll = ScrollView(this).apply { addView(content) }
        AlertDialog.Builder(this)
            .setTitle("Histórico de alterações de preço")
            .setView(scroll)
            .setPositiveButton("Fechar", null)
            .show()
    }

    private fun formatAdvicePercent(value: java.math.BigDecimal): String =
        value.stripTrailingZeros().toPlainString().replace('.', ',')

    private fun sendAssistantMessage(rawQuestion: String) {
        val question = rawQuestion.trim().take(400)
        if (question.isBlank()) {
            Toast.makeText(this, "Escreve uma pergunta primeiro.", Toast.LENGTH_SHORT).show()
            return
        }
        val answer = StudioMesaAdvisor.answerQuestion(question, assistantSnapshot())
        assistantConversation.add(AssistantChatTurn(question, answer))
        while (assistantConversation.size > 8) assistantConversation.removeAt(0)
        assistantQuestion = ""
        assistantAnswer = answer
        assistantInsightsExpanded = false
        renderApp()
    }

    private fun sendAssistantBriefing() {
        val answer = StudioMesaAdvisor.dailyBriefing(assistantSnapshot())
        assistantConversation.add(AssistantChatTurn("Faz um resumo da operação.", answer))
        while (assistantConversation.size > 8) assistantConversation.removeAt(0)
        assistantQuestion = ""
        assistantAnswer = answer
        assistantInsightsExpanded = false
        renderApp()
    }

    private fun assistantPromptButton(title: String, question: String, briefing: Boolean = false): TextView =
        label(title, 11.5f, true, forest).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(9), dp(8), dp(9))
            background = rippleBackground(paleGreen, 13)
            contentDescription = title
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (briefing) sendAssistantBriefing() else sendAssistantMessage(question)
            }
        }

    private fun renderEconomicDashboard(container: LinearLayout) {
        pageIntro(
            container,
            "Gestão económica",
            "Ferramentas privadas para o proprietário analisar custos, preços e equilíbrio financeiro."
        )
        addGap(container, 8)
        renderEconomicManager(container)
        if (isPremium && (!hasRemoteRestaurant() || backendPrefs.getString("restaurant_role", "").orEmpty() == "owner")) {
            addGap(container, 9)
            renderRestaurantPriceAdvisor(container)
        }
    }


    private fun renderAssistantComposer(): LinearLayout {
        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(white)
            elevation = dp(7).toFloat()
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val questionField = EditText(this).apply {
            hint = "Escreve a tua pergunta…"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            textSize = 14f
            setTextColor(ink)
            setHintTextColor(muted)
            setPadding(dp(13), dp(10), dp(13), dp(10))
            background = rounded(paper, 15)
            minLines = 1
            maxLines = 3
            filters = arrayOf(InputFilter.LengthFilter(400))
            contentDescription = "Campo de pergunta do Assistente StudioMesa, máximo 400 caracteres"
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
            setText(assistantQuestion)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                    sendAssistantMessage(text.toString())
                    true
                } else {
                    false
                }
            }
        }
        row.addView(questionField, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
            setMargins(0, 0, dp(8), 0)
        })
        val sendButton = TextView(this).apply {
            text = "↑"
            textSize = 25f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(forestDeep)
            background = rippleBackground(copper, 15)
            contentDescription = "Enviar pergunta ao assistente"
            isClickable = true
            isFocusable = true
            setOnClickListener { sendAssistantMessage(questionField.text.toString()) }
        }
        row.addView(sendButton, LinearLayout.LayoutParams(dp(52), dp(52)))
        composer.addView(row)
        addGap(composer, 4)
        composer.addView(label(
            "Máximo 400 caracteres. Assistente local: usa regras e dados disponíveis; não é um modelo generativo e não altera registos.",
            10f, false, muted
        ))
        return composer
    }

    private fun renderAssistant(container: LinearLayout) {
        val snapshot = assistantSnapshot()
        pageIntro(container, "Assistente StudioMesa", "O apoio à gestão do restaurante, numa conversa.")
        addGap(container, 4)

        val assistantIdentity = makeCard(12, forest)
        val identityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        identityRow.addView(label("✦", 24f, true, copper), LinearLayout.LayoutParams(dp(38), dp(38)))
        val identityText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        identityText.addView(label("Assistente StudioMesa", 15f, true, white))
        identityText.addView(label("ANÁLISE LOCAL · SEM API PAGA", 9.5f, true, copperPale))
        identityRow.addView(identityText, LinearLayout.LayoutParams(0, -2, 1f))
        assistantIdentity.addView(identityRow)
        assistantIdentity.addView(label(
            "Respostas baseadas nos dados disponíveis. Confirma sempre os valores antes de decidir.",
            11f, false, copperPale
        ))
        container.addView(assistantIdentity, cardParams())

        if (assistantConversation.isNotEmpty()) {
            container.addView(secondaryButton("Nova conversa") {
                assistantConversation.clear()
                assistantQuestion = ""
                assistantAnswer = null
                assistantInsightsExpanded = false
                renderApp()
            }, LinearLayout.LayoutParams(-1, dp(40)).apply {
                setMargins(0, 0, 0, dp(8))
            })
        }

        val chatPanel = makeCard(13, Color.rgb(242, 244, 239))
        if (assistantConversation.isEmpty()) {
            val welcome = makeCard(14, white)
            welcome.addView(label("STUDIOMESA", 9f, true, forestSoft))
            addGap(welcome, 5)
            welcome.addView(label(
                "Olá! Posso ajudar-te a analisar preços, margens, pedidos, reservas, stock e organização do serviço. Começa por escolher uma sugestão ou escreve a tua pergunta.",
                13f, false, ink
            ))
            chatPanel.addView(welcome, cardParams())
        } else {
            val bubbleWidth = maxOf(dp(180), minOf(dp(310), resources.displayMetrics.widthPixels - dp(76)))
            assistantConversation.forEach { turn ->
                val userRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END
                }
                val userBubble = makeCard(12, paleGreen)
                userBubble.addView(label("TU", 9f, true, forestSoft))
                addGap(userBubble, 4)
                userBubble.addView(label(turn.question, 13f, false, ink))
                userRow.addView(userBubble, LinearLayout.LayoutParams(bubbleWidth, -2))
                chatPanel.addView(userRow, LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(0, 0, 0, dp(8))
                })

                val assistantRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.START
                }
                val assistantBubble = makeCard(13, white)
                assistantBubble.addView(label("✦  ASSISTENTE STUDIOMESA", 9f, true, forestSoft))
                addGap(assistantBubble, 5)
                assistantBubble.addView(label(turn.answer.title, 15f, true, forestDeep))
                addGap(assistantBubble, 5)
                assistantBubble.addView(label(turn.answer.response, 12.5f, false, ink))
                assistantActionTarget(turn.answer.actionPage)?.let { target ->
                    addGap(assistantBubble, 8)
                    assistantBubble.addView(secondaryButton("Abrir " + target) {
                        activePage = target
                        renderApp()
                    }, LinearLayout.LayoutParams(-1, dp(42)))
                }
                assistantRow.addView(assistantBubble, LinearLayout.LayoutParams(bubbleWidth, -2))
                chatPanel.addView(assistantRow, LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(0, 0, 0, dp(10))
                })
            }
        }
        container.addView(chatPanel, cardParams())

        if (assistantConversation.isEmpty()) {
            sectionHeading(container, "Sugestões rápidas", "Toca numa opção para iniciar a conversa.")
            val quickRowOne = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            quickRowOne.addView(assistantPromptButton("Gerar resumo da operação", "", true),
                LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(0, 0, dp(5), 0) })
            quickRowOne.addView(assistantPromptButton("Rever margens", "Quais artigos têm melhor margem e quais têm a margem mais baixa?"),
                LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(5), 0, 0, 0) })
            container.addView(quickRowOne, LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, 0, 0, dp(6))
            })
            val quickRowTwo = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            quickRowTwo.addView(assistantPromptButton("Stock baixo", "Que artigos de stock estão baixos ou esgotados?"),
                LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(0, 0, dp(5), 0) })
            quickRowTwo.addView(assistantPromptButton("Reservas pendentes", "Há reservas pendentes por confirmar?"),
                LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(5), 0, 0, 0) })
            container.addView(quickRowTwo, LinearLayout.LayoutParams(-1, -2))
        }

        addGap(container, 8)
        container.addView(secondaryButton(
            if (assistantInsightsExpanded) "Ocultar análises complementares" else "Ver análises complementares"
        ) {
            assistantInsightsExpanded = !assistantInsightsExpanded
            renderApp()
        }, LinearLayout.LayoutParams(-1, dp(44)))

        if (assistantInsightsExpanded) {
            addGap(container, 8)
            if ("Gestão económica" in availablePages()) {
                val economicLink = makeCard(13, copperPale)
                economicLink.addView(label("GESTÃO ECONÓMICA", 9.5f, true, forestSoft))
                addGap(economicLink, 4)
                economicLink.addView(label(
                    "Abre a área do proprietário para simular cenários, calcular metas e consultar ferramentas financeiras.",
                    12f, false, ink
                ))
                addGap(economicLink, 8)
                economicLink.addView(secondaryButton("Abrir gestão económica") {
                    activePage = "Gestão económica"
                    renderApp()
                }, LinearLayout.LayoutParams(-1, dp(44)))
                container.addView(economicLink, cardParams())
            }

            addGap(container, 5)
            sectionHeading(container, "Sugestões para rever", "Prioridades estimadas. Confirma os dados e decide antes de alterar qualquer coisa.")
            StudioMesaAdvisor.recommendations(snapshot).forEach { recommendation ->
                val (priorityText, textColor, backColor) = when (recommendation.priority) {
                    AssistantPriority.URGENT -> Triple("PRIORIDADE ALTA", red, paleRed)
                    AssistantPriority.ATTENTION -> Triple("A REVER", forestDeep, copperPale)
                    AssistantPriority.TIP -> Triple("SUGESTÃO", forestSoft, paleGreen)
                }
                val card = makeCard(15)
                card.addView(label(priorityText, 9f, true, textColor).apply {
                    setPadding(dp(8), dp(5), dp(8), dp(5))
                    background = rounded(backColor, 10)
                }, LinearLayout.LayoutParams(-2, -2))
                addGap(card, 8)
                card.addView(label(recommendation.title, 16f, true, forestDeep))
                addGap(card, 5)
                card.addView(label(recommendation.detail, 12.5f, false, muted))
                assistantActionTarget(recommendation.actionPage)?.let { target ->
                    addGap(card, 10)
                    card.addView(secondaryButton("Abrir " + target) {
                        activePage = target
                        renderApp()
                    }, LinearLayout.LayoutParams(-1, dp(44)))
                }
                container.addView(card, cardParams())
            }
            sectionHeading(container, "Engenharia da carta", "Combina unidades de pedidos concluídos com a contribuição estimada por artigo.")
            val engineering = StudioMesaAdvisor.menuEngineering(snapshot)
            if (!engineering.hasEnoughSales) {
                container.addView(emptyCard(
                    "Ainda não há pelo menos ${engineering.requiredUnits} unidades concluídas que correspondam aos artigos atuais (${engineering.completedUnits} encontradas). A análise não inventa popularidade."
                ), cardParams())
            } else {
                if (!engineering.hasMarginData) {
                    container.addView(emptyCard("Existem vendas, mas faltam custos válidos nos artigos vendidos para comparar a contribuição. Confirma os custos na Carta."), cardParams())
                }
                engineering.items.forEach { item ->
                    val title = when (item.classification) {
                        MenuEngineeringClass.STAR -> "ESTRELA"
                        MenuEngineeringClass.WORKHORSE -> "POPULAR · REVER MARGEM"
                        MenuEngineeringClass.PUZZLE -> "MARGEM ALTA · REVER PROCURA"
                        MenuEngineeringClass.DOG -> "REVER ARTIGO"
                        MenuEngineeringClass.MISSING_COST -> "CUSTO EM FALTA"
                    }
                    val backColor = when (item.classification) {
                        MenuEngineeringClass.STAR -> paleGreen
                        MenuEngineeringClass.WORKHORSE, MenuEngineeringClass.PUZZLE -> copperPale
                        MenuEngineeringClass.DOG, MenuEngineeringClass.MISSING_COST -> paleRed
                    }
                    val card = makeCard(14)
                    card.addView(label(title, 9f, true, forestDeep).apply {
                        setPadding(dp(8), dp(5), dp(8), dp(5))
                        background = rounded(backColor, 10)
                    }, LinearLayout.LayoutParams(-2, -2))
                    addGap(card, 7)
                    card.addView(label(item.itemName, 16f, true, forestDeep))
                    addGap(card, 4)
                    card.addView(label(
                        "${item.unitsSold} unidade(s) concluídas · ${item.popularitySharePercent}% das unidades correspondentes · contribuição atual: ${item.contributionPerUnit?.let { money(it) } ?: "custo em falta"} por unidade.",
                        11.5f, true, muted
                    ))
                    addGap(card, 5)
                    card.addView(label(item.recommendation, 12.5f, false, ink))
                    container.addView(card, cardParams())
                }
                container.addView(label(
                    "Triagem com pedidos concluídos e preço/custo atuais; não é lucro contabilístico nem custo histórico e não inclui IVA, mão de obra, desperdício ou encargos.",
                    10.5f, false, muted
                ))
            }

            sectionHeading(container, "Ideias de conteúdo", "Rascunhos para revisão; só usam nomes, descrições e preços registados na carta.")
            val drafts = StudioMesaAdvisor.marketingDrafts(snapshot)
            if (drafts.isEmpty()) {
                container.addView(emptyCard("Adiciona artigos com nome e preço válidos para criar rascunhos."), cardParams())
            } else {
                drafts.forEach { draft ->
                    val card = makeCard(14)
                    card.addView(label(draft.itemName, 16f, true, forestDeep))
                    addGap(card, 6)
                    card.addView(label(draft.caption, 13f, false, ink))
                    addGap(card, 5)
                    card.addView(label(draft.basis, 10.5f, false, muted))
                    addGap(card, 8)
                    card.addView(secondaryButton("Copiar rascunho") {
                        copyAssistantText(draft.caption)
                    }, LinearLayout.LayoutParams(-1, dp(44)))
                    container.addView(card, cardParams())
                }
            }

            addGap(container, 4)
            container.addView(label(
                if (snapshot.isDemo) "Fonte atual: dados de demonstração locais. Liga um restaurante na secção Conta para analisar dados partilhados."
                else "Fonte atual: dados guardados em cache pela aplicação. Confirma a última atualização antes de decidir; esta análise não verifica a conectividade em tempo real.",
                11f, false, muted
            ))
        }

    }


    private fun clearEconomicManagerState() {
        economicManagerEnabled = false
        economicScopeKey = null
        economicInputs.clear()
        economicReportText = ""
        scenarioReportText = ""
        breakEvenTargetReportText = ""
        recipeReportText = ""
        reorderReportText = ""
        wasteReportText = ""
        supplierReportText = ""
        budgetReportText = ""
        usageReportText = ""
        labourReportText = ""
    }

    private fun renderEconomicManager(container: LinearLayout) {
        val scope = if (hasRemoteRestaurant()) {
            "restaurant:" + backendRestaurantId() + ":" + backendPrefs.getString("user_id", "").orEmpty()
        } else "demo"
        if (economicScopeKey != null && economicScopeKey != scope) clearEconomicManagerState()

        if (hasRemoteRestaurant() && backendPrefs.getString("restaurant_role", "").orEmpty() != "owner") {
            clearEconomicManagerState()
            sectionHeading(container, "Gestor económico · opcional", "Área reservada ao proprietário do restaurante.")
            container.addView(emptyCard("Esta conta não tem acesso ao gestor económico. Nenhum valor financeiro será recolhido ou guardado."), cardParams())
            return
        }

        sectionHeading(container, "Gestor económico · opcional",
            "Ativa apenas se o proprietário quiser analisar custos, tesouraria, receitas e compras.")
        val toggle = makeCard(14)
        toggle.addView(label(if (economicManagerEnabled) "Gestor ativo nesta sessão" else "Gestor desativado", 15f, true, forestDeep))
        addGap(toggle, 5)
        toggle.addView(label(
            "Por defeito, os valores ficam apenas na memória desta sessão. Só são guardados neste dispositivo quando pedires uma cópia cifrada.",
            11.5f, false, muted
        ))
        addGap(toggle, 8)
        val toggleAction: Button = if (economicManagerEnabled) {
            dangerButton("Desativar e limpar valores da sessão") {
                clearEconomicManagerState()
                renderApp()
            }
        } else {
            primaryButton("Ativar gestão económica") {
                economicManagerEnabled = true
                economicScopeKey = scope
                economicInputs.clear()
                economicReportText = ""
                scenarioReportText = ""
                breakEvenTargetReportText = ""
                recipeReportText = ""
                reorderReportText = ""
                renderApp()
            }
        }
        toggle.addView(toggleAction, LinearLayout.LayoutParams(-1, dp(44)))
        container.addView(toggle, cardParams())
        if (!economicManagerEnabled) return

        if (hasRemoteRestaurant()) {
            val encryptedStore = SecureEconomicStore(this)
            val savedCopyExists = encryptedStore.hasSaved(scope)
            val savedCard = makeCard(14)
            savedCard.addView(label("Cópia privada neste dispositivo", 15f, true, forestDeep))
            addGap(savedCard, 4)
            savedCard.addView(label(
                if (savedCopyExists) "Existe uma cópia cifrada para esta conta e este restaurante."
                else "Ainda não existe uma cópia guardada para esta conta e este restaurante.",
                11.5f, false, muted
            ))
            addGap(savedCard, 8)
            savedCard.addView(primaryButton("Guardar valores atuais cifrados") {
                try {
                    encryptedStore.save(scope, economicInputs)
                    Toast.makeText(this, "Cópia económica cifrada guardada neste dispositivo.", Toast.LENGTH_LONG).show()
                } catch (error: Exception) {
                    Toast.makeText(this, error.message ?: "Não foi possível guardar a cópia económica.", Toast.LENGTH_LONG).show()
                }
                renderApp()
            }, LinearLayout.LayoutParams(-1, dp(44)))
            addGap(savedCard, 5)
            savedCard.addView(secondaryButton("Carregar cópia guardada") {
                try {
                    val restoredInputs = encryptedStore.load(scope)
                    if (restoredInputs.isEmpty()) {
                        Toast.makeText(this, "Não foram encontrados valores guardados para esta conta.", Toast.LENGTH_LONG).show()
                    } else {
                        economicInputs.clear()
                        economicInputs.putAll(restoredInputs)
                        economicReportText = ""
                        scenarioReportText = ""
                        breakEvenTargetReportText = ""
                        recipeReportText = ""
                        reorderReportText = ""
                        wasteReportText = ""
                        supplierReportText = ""
                        budgetReportText = ""
                        usageReportText = ""
                        labourReportText = ""
                        Toast.makeText(this, "Valores económicos carregados.", Toast.LENGTH_SHORT).show()
                    }
                } catch (error: Exception) {
                    Toast.makeText(this, error.message ?: "Não foi possível abrir a cópia económica.", Toast.LENGTH_LONG).show()
                }
                renderApp()
            }, LinearLayout.LayoutParams(-1, dp(44)))
            if (savedCopyExists) {
                addGap(savedCard, 5)
                savedCard.addView(secondaryButton("Apagar cópia cifrada") {
                    AlertDialog.Builder(this)
                        .setTitle("Apagar cópia económica?")
                        .setMessage("Apenas a cópia cifrada deste restaurante neste dispositivo será eliminada. Os dados do servidor não são alterados.")
                        .setNegativeButton("Manter", null)
                        .setPositiveButton("Apagar") { _, _ ->
                            val deleted = runCatching { encryptedStore.delete(scope) }.getOrDefault(false)
                            Toast.makeText(
                                this,
                                if (deleted) "Cópia cifrada eliminada." else "Não foi possível eliminar a cópia.",
                                Toast.LENGTH_LONG
                            ).show()
                            renderApp()
                        }
                        .show()
                }, LinearLayout.LayoutParams(-1, dp(44)))
            }
            addGap(savedCard, 6)
            savedCard.addView(label(
                "A cópia usa a chave do Android Keystore, fica no armazenamento privado excluído das cópias automáticas e não é sincronizada. Só pode ser aberta nesta instalação com a mesma conta de proprietário e restaurante.",
                10.5f, false, muted
            ))
            container.addView(savedCard, cardParams())
        } else {
            container.addView(
                emptyCard("Na demonstração, os valores continuam apenas nesta sessão. A cópia cifrada só fica disponível depois de ligares uma conta de proprietário a um restaurante."),
                cardParams()
            )
        }

        val toolIndex = container.childCount
        val toolTargets = linkedMapOf<String, View>()

        addGap(container, 6)
        val monthly = makeCard(15)
        monthly.addView(label("1 · Indicadores económicos mensais", 17f, true, forestDeep))
        addGap(monthly, 4)
        monthly.addView(label("Usa valores do mesmo período. Introduz vendas sem IVA e custos reais; o resultado é uma estimativa, não contabilidade oficial.", 11.5f, false, muted))
        addGap(monthly, 7)
        economicNumberField(monthly, "sales", "Vendas mensais sem IVA (€)", "Ex.: 18000")
        economicNumberField(monthly, "food", "Custo alimentar consumido, incluindo desperdício (€)", "Ex.: 5400")
        economicNumberField(monthly, "payroll", "Custo total de pessoal com encargos (€)", "Ex.: 6200")
        economicNumberField(monthly, "fixed", "Outros custos fixos (€)", "Renda, seguros, software…")
        economicNumberField(monthly, "variable", "Outros custos variáveis (€)", "Sem comida e pessoal")
        economicNumberField(monthly, "foodTarget", "Objetivo de custo alimentar (%)", "Exemplo inicial: 30", "30")
        economicNumberField(monthly, "waste", "Desperdício registado (€), opcional", "Indicador separado")
        addGap(monthly, 5)
        monthly.addView(label("Tesouraria a 30 dias · opcional", 14f, true, forestDeep))
        economicNumberField(monthly, "cash", "Saldo de caixa/banco disponível (€)", "Ex.: 2500")
        economicNumberField(monthly, "receipts", "Recebimentos previstos (€)", "Próximos 30 dias")
        economicNumberField(monthly, "due", "Pagamentos a vencer (€)", "Próximos 30 dias")
        addGap(monthly, 5)
        monthly.addView(label("Vendas semanais históricas · opcional", 14f, true, forestDeep))
        monthly.addView(label("Quatro ou mais semanas comparáveis, da mais antiga para a mais recente, separadas por ponto e vírgula.", 11f, false, muted))
        economicTextField(monthly, "weeks", "Ex.: 4200; 4600; 4400; 5100")
        addGap(monthly, 8)
        monthly.addView(primaryButton("Calcular indicadores e alertas") {
            economicReportText = try {
                val sales = economicRequiredAmount("sales", "vendas mensais")
                val food = economicRequiredAmount("food", "custo alimentar")
                val payroll = economicRequiredAmount("payroll", "custo de pessoal")
                val fixed = economicRequiredAmount("fixed", "custos fixos")
                val variable = economicRequiredAmount("variable", "outros custos variáveis")
                val target = economicRequiredAmount("foodTarget", "objetivo de custo alimentar")
                val waste = economicInputs["waste"].orEmpty().trim().takeIf { it.isNotEmpty() }
                    ?.let { RestaurantEconomicManager.parseNonNegative(it, "desperdício") }
                val cashFields = listOf("cash", "receipts", "due").map { economicInputs[it].orEmpty().trim() }
                if (cashFields.any { it.isNotEmpty() } && cashFields.any { it.isEmpty() }) {
                    throw IllegalArgumentException("Para a tesouraria, preenche os três campos: saldo, recebimentos e pagamentos.")
                }
                val analysis = RestaurantEconomicManager.analyze(EconomicManagerInput(
                    monthlyNetSales = sales,
                    foodCostConsumedIncludingWaste = food,
                    monthlyPayroll = payroll,
                    otherFixedCosts = fixed,
                    otherVariableCosts = variable,
                    targetFoodCostPercent = target,
                    recordedWasteCost = waste,
                    cashAvailableNow = cashFields[0].takeIf { it.isNotEmpty() }?.let { RestaurantEconomicManager.parseNonNegative(it, "saldo") },
                    expectedReceiptsNext30Days = cashFields[1].takeIf { it.isNotEmpty() }?.let { RestaurantEconomicManager.parseNonNegative(it, "recebimentos") },
                    paymentsDueNext30Days = cashFields[2].takeIf { it.isNotEmpty() }?.let { RestaurantEconomicManager.parseNonNegative(it, "pagamentos") },
                    weeklyNetSalesHistory = RestaurantEconomicManager.parseWeeklySales(economicInputs["weeks"].orEmpty())
                ))
                buildString {
                    append("Custo alimentar / vendas: ").append(economicPercent(analysis.foodCostPercent)).append('\n')
                    append("Pessoal / vendas: ").append(economicPercent(analysis.payrollPercent)).append('\n')
                    append("Custo alimentar + pessoal: ").append(economicPercent(analysis.primeCostPercent)).append('\n')
                    append("Resultado operacional estimado: ").append(money(analysis.estimatedOperatingResult)).append('\n')
                    analysis.estimatedBreakEvenSales?.let { append("Ponto de equilíbrio mensal estimado: ").append(money(it)).append('\n') }
                    if (analysis.estimatedBreakEvenSales != null) {
                        val difference = sales.subtract(analysis.estimatedBreakEvenSales)
                        append(if (difference >= BigDecimal.ZERO) "Vendas acima do ponto de equilíbrio: " else "Vendas abaixo do ponto de equilíbrio: ")
                            .append(money(difference.abs())).append('\n')
                    }
                    analysis.recordedWasteAsPercentOfSales?.let { append("Desperdício / vendas: ").append(economicPercent(it)).append('\n') }
                    analysis.recordedWasteAsPercentOfFoodCost?.let { append("Desperdício / custo alimentar: ").append(economicPercent(it)).append('\n') }
                    analysis.projectedCashIn30Days?.let { append("Tesouraria estimada a 30 dias: ").append(money(it)).append('\n') }
                    analysis.forecastNextWeekSales?.let { append("Referência semanal (média das últimas 4 semanas): ").append(money(it)).append('\n') }
                    analysis.warnings.forEach { append("• ").append(it).append('\n') }
                }.trim()
            } catch (e: IllegalArgumentException) {
                "Não foi possível calcular: ${e.message ?: "confirma os valores introduzidos"}"
            }
            renderApp()
        }, LinearLayout.LayoutParams(-1, dp(46)))
        if (economicReportText.isNotBlank()) {
            addGap(monthly, 8)
            monthly.addView(label(economicReportText, 12f, false, ink))
        }
        container.addView(monthly, cardParams())
        toolTargets["Indicadores mensais"] = monthly

        addGap(container, 6)
        val scenario = makeCard(15)
        scenario.addView(label("2 · Simulador de cenários", 17f, true, forestDeep))
        addGap(scenario, 4)
        scenario.addView(label(
            "Testa hipóteses sem alterar os registos: define como podem variar as vendas e os custos em relação ao mês-base acima.",
            11.5f, false, muted
        ))
        addGap(scenario, 7)
        economicNumberField(scenario, "scenarioSalesChange", "Variação das vendas (%)", "Ex.: -10 para uma quebra de 10%", "0")
        economicNumberField(scenario, "scenarioFoodChange", "Variação do custo alimentar (%)", "Ex.: 5 para um aumento de 5%", "0")
        economicNumberField(scenario, "scenarioPayrollChange", "Variação do custo de pessoal (%)", "Ex.: 2 para um aumento de 2%", "0")
        economicNumberField(scenario, "scenarioVariableChange", "Variação de outros custos variáveis (%)", "Ex.: -3 para uma redução de 3%", "0")
        addGap(scenario, 4)
        scenario.addView(primaryButton("Simular impacto no resultado") {
            scenarioReportText = try {
                val baselineInput = EconomicManagerInput(
                    monthlyNetSales = economicRequiredAmount("sales", "vendas mensais"),
                    foodCostConsumedIncludingWaste = economicRequiredAmount("food", "custo alimentar"),
                    monthlyPayroll = economicRequiredAmount("payroll", "custo de pessoal"),
                    otherFixedCosts = economicRequiredAmount("fixed", "custos fixos"),
                    otherVariableCosts = economicRequiredAmount("variable", "outros custos variáveis"),
                    targetFoodCostPercent = economicRequiredAmount("foodTarget", "objetivo de custo alimentar")
                )
                val result = RestaurantEconomicManager.simulateScenario(
                    baselineInput = baselineInput,
                    salesChangePercent = RestaurantEconomicManager.parseScenarioPercent(
                        economicInputs["scenarioSalesChange"].orEmpty(), "variação das vendas"
                    ),
                    foodCostChangePercent = RestaurantEconomicManager.parseScenarioPercent(
                        economicInputs["scenarioFoodChange"].orEmpty(), "variação do custo alimentar"
                    ),
                    payrollChangePercent = RestaurantEconomicManager.parseScenarioPercent(
                        economicInputs["scenarioPayrollChange"].orEmpty(), "variação do custo de pessoal"
                    ),
                    otherVariableCostChangePercent = RestaurantEconomicManager.parseScenarioPercent(
                        economicInputs["scenarioVariableChange"].orEmpty(), "variação dos outros custos variáveis"
                    )
                )
                val difference = result.operatingResultChange
                buildString {
                    append("Resultado operacional base: ").append(money(result.baseline.estimatedOperatingResult)).append('\n')
                    append("Resultado no cenário: ").append(money(result.scenario.estimatedOperatingResult)).append('\n')
                    append(
                        when {
                            difference > BigDecimal.ZERO -> "Melhoria estimada: "
                            difference < BigDecimal.ZERO -> "Redução estimada: "
                            else -> "Variação estimada: "
                        }
                    ).append(money(difference.abs())).append('\n')
                    append("Vendas no cenário: ").append(money(baselineInput.monthlyNetSales.add(result.salesChange))).append('\n')
                    result.scenario.estimatedBreakEvenSales?.let {
                        append("Ponto de equilíbrio no cenário: ").append(money(it)).append('\n')
                    }
                    result.scenario.warnings.filterNot {
                        it.startsWith("Estimativa simplificada:")
                    }.forEach { append("Atenção: ").append(it).append('\n') }
                    append("A simulação altera apenas as rubricas percentuais indicadas e mantém os custos fixos. Não é uma previsão.")
                }.trim()
            } catch (e: IllegalArgumentException) {
                "Não foi possível simular: ${e.message ?: "confirma os valores do mês-base e as percentagens"}"
            }
            renderApp()
        }, LinearLayout.LayoutParams(-1, dp(46)))
        if (scenarioReportText.isNotBlank()) {
            addGap(scenario, 8)
            scenario.addView(label(scenarioReportText, 12f, false, ink))
        }
        container.addView(scenario, cardParams())
        toolTargets["Simulador de cenários"] = scenario

        addGap(container, 6)
        val breakEvenTarget = makeCard(15)
        breakEvenTarget.addView(label("3 · Meta diária para atingir o equilíbrio", 17f, true, forestDeep))
        addGap(breakEvenTarget, 4)
        breakEvenTarget.addView(label(
            "Transforma o ponto de equilíbrio mensal estimado numa referência diária e num número aproximado de clientes. Usa vendas e ticket médio sem IVA.",
            11.5f, false, muted
        ))
        addGap(breakEvenTarget, 7)
        economicNumberField(breakEvenTarget, "breakEvenDays", "Dias de abertura no mês", "Ex.: 26", "26")
        economicNumberField(breakEvenTarget, "breakEvenTicket", "Ticket médio líquido por cliente (€)", "Ex.: 22,50")
        breakEvenTarget.addView(primaryButton("Calcular meta diária") {
            breakEvenTargetReportText = try {
                val daysValue = economicRequiredAmount("breakEvenDays", "dias de abertura")
                val days = try {
                    daysValue.intValueExact()
                } catch (_: ArithmeticException) {
                    throw IllegalArgumentException("Os dias de abertura têm de ser um número inteiro.")
                }
                require(days in 1..31) { "Indica entre 1 e 31 dias de abertura." }
                val averageTicket = economicRequiredAmount("breakEvenTicket", "ticket médio líquido")
                require(averageTicket > BigDecimal.ZERO) { "O ticket médio tem de ser superior a zero." }
                val baseline = RestaurantEconomicManager.analyze(EconomicManagerInput(
                    monthlyNetSales = economicRequiredAmount("sales", "vendas mensais"),
                    foodCostConsumedIncludingWaste = economicRequiredAmount("food", "custo alimentar"),
                    monthlyPayroll = economicRequiredAmount("payroll", "custo de pessoal"),
                    otherFixedCosts = economicRequiredAmount("fixed", "custos fixos"),
                    otherVariableCosts = economicRequiredAmount("variable", "outros custos variáveis"),
                    targetFoodCostPercent = economicRequiredAmount("foodTarget", "objetivo de custo alimentar")
                ))
                val breakEven = baseline.estimatedBreakEvenSales
                    ?: throw IllegalArgumentException("Com esta estrutura de custos não é possível calcular um ponto de equilíbrio positivo.")
                val dailySalesTarget = breakEven.divide(
                    BigDecimal.valueOf(days.toLong()), 2, java.math.RoundingMode.CEILING
                )
                val customersPerDay = dailySalesTarget.divide(
                    averageTicket, 0, java.math.RoundingMode.CEILING
                )
                buildString {
                    append("Ponto de equilíbrio mensal: ").append(money(breakEven)).append('\n')
                    append("Meta de vendas por dia aberto: ").append(money(dailySalesTarget)).append('\n')
                    append("Clientes estimados por dia: ").append(customersPerDay.toPlainString()).append('\n')
                    append("Ticket médio líquido utilizado: ").append(money(averageTicket)).append('\n')
                    append("É uma média matemática. Não considera dias da semana, sazonalidade, capacidade, reservas ou variação do consumo.")
                }.trim()
            } catch (e: IllegalArgumentException) {
                "Não foi possível calcular a meta: ${e.message ?: "confirma os valores introduzidos"}"
            }
            renderApp()
        }, LinearLayout.LayoutParams(-1, dp(46)))
        if (breakEvenTargetReportText.isNotBlank()) {
            addGap(breakEvenTarget, 8)
            breakEvenTarget.addView(label(breakEvenTargetReportText, 12f, false, ink))
        }
        container.addView(breakEvenTarget, cardParams())
        toolTargets["Meta diária para equilíbrio"] = breakEvenTarget

        addGap(container, 6)
        val recipe = makeCard(15)
        recipe.addView(label("4 · Custo por receita e dose", 17f, true, forestDeep))
        addGap(recipe, 4)
        recipe.addView(label("Cada linha: ingrediente | quantidade por dose | preço por unidade | desperdício %. As unidades têm de ser compatíveis, por exemplo kg e €/kg.", 11.5f, false, muted))
        economicTextField(recipe, "recipe", "Farinha | 0,2 | 2,50 | 5\nMolho | 0,1 | 4,00 | 0")
        addGap(recipe, 4)
        recipe.addView(primaryButton("Calcular custo da receita") {
            recipeReportText = try {
                val target = economicRequiredAmount("foodTarget", "objetivo de custo alimentar")
                val result = RestaurantEconomicManager.costRecipe(economicInputs["recipe"].orEmpty(), target)
                buildString {
                    append("Custo estimado por dose: ").append(money(result.totalCostPerPortion)).append('\n')
                    append("Preço indicativo a ").append(economicPercent(result.targetFoodCostPercent)).append(": ")
                        .append(money(result.suggestedPriceAtTarget)).append('\n')
                    result.ingredients.forEach { append("• ").append(it.name).append(": ").append(preciseMoney(it.costPerPortion)).append('\n') }
                    append("Confirma rendimentos, unidades, IVA e outros custos antes de definir um preço.")
                }
            } catch (e: IllegalArgumentException) {
                "Não foi possível calcular a receita: ${e.message ?: "confirma os ingredientes"}"
            }
            renderApp()
        }, LinearLayout.LayoutParams(-1, dp(46)))
        if (recipeReportText.isNotBlank()) {
            addGap(recipe, 8)
            recipe.addView(label(recipeReportText, 12f, false, ink))
        }
        container.addView(recipe, cardParams())
        toolTargets["Custo por receita e dose"] = recipe

        addGap(container, 6)
        val reorder = makeCard(15)
        reorder.addView(label("5 · Reposição de stock", 17f, true, forestDeep))
        addGap(reorder, 4)
        reorder.addView(label("Formato: stock atual | consumo médio/dia | prazo do fornecedor em dias | dias de segurança. Só calcula uma quantidade; não faz encomendas.", 11.5f, false, muted))
        economicTextField(reorder, "reorder", "Ex.: 5 | 3 | 4 | 2")
        addGap(reorder, 4)
        reorder.addView(primaryButton("Calcular reposição sugerida") {
            reorderReportText = try {
                val result = RestaurantEconomicManager.recommendReorder(economicInputs["reorder"].orEmpty())
                if (result.suggestedOrderQuantity.signum() == 0) {
                    "O stock introduzido já cobre o prazo e a margem de segurança. Confirma a contagem física."
                } else {
                    "Stock alvo: ${result.targetStockForLeadAndSafetyDays.stripTrailingZeros().toPlainString()} unidades.\n" +
                        "Reposição indicativa: ${result.suggestedOrderQuantity.stripTrailingZeros().toPlainString()} unidades para ${result.totalCoverageDays} dias. Confirma a unidade, validade, consumo e prazo real."
                }
            } catch (e: IllegalArgumentException) {
                "Não foi possível calcular a reposição: ${e.message ?: "confirma os quatro valores"}"
            }
            renderApp()
        }, LinearLayout.LayoutParams(-1, dp(46)))
        if (reorderReportText.isNotBlank()) {
            addGap(reorder, 8)
            reorder.addView(label(reorderReportText, 12f, false, ink))
        }
        container.addView(reorder, cardParams())
        toolTargets["Reposição de stock"] = reorder

        addGap(container, 6)
        economicLogCard(
            container, "6 · Registo de desperdício por artigo", 
            "Formato: artigo | quantidade desperdiçada | custo por unidade | motivo. Regista apenas quantidades e custos compatíveis.",
            "wasteLog", "Frango | 2 | 6,50 | Validade\nSopa | 1 | 4,00 | Produção",
            "Calcular custo do desperdício", wasteReportText,
            compute = { raw ->
                val report = RestaurantEconomicManager.analyzeWasteLog(raw)
                buildString {
                    append("Custo total registado: ").append(money(report.totalCost)).append('\n')
                    report.costByReason.forEach { append("• ").append(it.first).append(": ").append(money(it.second)).append('\n') }
                    report.entries.forEach { append("• ").append(it.itemName).append(" — ")
                        .append(it.quantity.stripTrailingZeros().toPlainString()).append(" × ")
                        .append(money(it.unitCost)).append(" = ").append(money(it.totalCost)).append('\n') }
                    append("Este cálculo não movimenta stock nem desconta o desperdício outra vez.")
                }
            },
            save = { wasteReportText = it }
        )
        toolTargets["Registo de desperdício"] = container.getChildAt(container.childCount - 1)

        addGap(container, 6)
        economicLogCard(
            container, "7 · Comparar preços de fornecedores",
            "Formato: artigo | fornecedor | unidade | quantidade na embalagem | preço da embalagem. Compara apenas a mesma unidade (por exemplo, €/kg).",
            "supplierQuotes", "Tomate | Fornecedor A | kg | 10 | 16,00\nTomate | Fornecedor B | kg | 5 | 7,50",
            "Comparar custo por unidade", supplierReportText,
            compute = { raw ->
                val groups = RestaurantEconomicManager.compareSupplierQuotes(raw)
                buildString {
                    groups.forEach { group ->
                        append(group.itemName).append(" (por ").append(group.unit).append("):\n")
                        group.options.forEachIndexed { index, quote ->
                            append(if (index == 0) "• Mais barato: " else "• ")
                                .append(quote.supplierName).append(" — ")
                                .append(money(quote.pricePerUnit)).append(" / ").append(group.unit)
                                .append(" (embalagem ").append(quote.packQuantity.stripTrailingZeros().toPlainString())
                                .append(" por ").append(money(quote.packPrice)).append(")\n")
                        }
                    }
                    append("Confirma qualidade, transporte, IVA, validade, mínimos de encomenda e condições antes de escolher.")
                }
            },
            save = { supplierReportText = it }
        )
        toolTargets["Comparar fornecedores"] = container.getChildAt(container.childCount - 1)

        addGap(container, 6)
        economicLogCard(
            container, "8 · Orçamento versus valor real",
            "Formato: categoria | orçamento do período | valor real. Usa o mesmo período. Uma diferença positiva não é automaticamente boa nem má.",
            "budgetComparison", "Vendas | 15000 | 14200\nPessoal | 5200 | 5300\nRenda | 1800 | 1800",
            "Comparar orçamento e realizado", budgetReportText,
            compute = { raw ->
                val rows = RestaurantEconomicManager.compareBudget(raw)
                buildString {
                    rows.forEach { row ->
                        append("• ").append(row.category).append(": orçamento ").append(money(row.budget))
                            .append("; real ").append(money(row.actual))
                            .append("; diferença ").append(if (row.variance.signum() > 0) "+" else "").append(money(row.variance))
                        row.variancePercent?.let { append(" (").append(economicPercent(it)).append(")") }
                        append('\n')
                    }
                    append("Para custos, ficar acima do orçamento pode ser desfavorável; para vendas, o sentido é normalmente o contrário. Revê cada categoria.")
                }
            },
            save = { budgetReportText = it }
        )
        toolTargets["Orçamento vs. realizado"] = container.getChildAt(container.childCount - 1)

        addGap(container, 6)
        economicLogCard(
            container, "9 · Consumo teórico versus real",
            "Formato: artigo | consumo teórico | consumo real | custo por unidade. Quantidades e unidade de custo têm de coincidir e referir-se ao mesmo período.",
            "usageVariance", "Frango | 20 | 23 | 6,50\nQueijo | 5 | 4 | 8,00",
            "Analisar diferenças de consumo", usageReportText,
            compute = { raw ->
                val rows = RestaurantEconomicManager.compareTheoreticalAndActualUsage(raw)
                buildString {
                    rows.forEach { row ->
                        append("• ").append(row.itemName).append(": previsto ")
                            .append(row.expectedUse.stripTrailingZeros().toPlainString())
                            .append("; real ").append(row.actualUse.stripTrailingZeros().toPlainString())
                            .append("; diferença ").append(if (row.varianceQuantity.signum() > 0) "+" else "")
                            .append(row.varianceQuantity.stripTrailingZeros().toPlainString())
                            .append("; impacto estimado ").append(if (row.varianceCost.signum() > 0) "+" else "")
                            .append(money(row.varianceCost)).append('\n')
                    }
                    append("Diferenças podem resultar de desperdício, porções, contagens, ofertas, erros de receita ou dados incompletos; investiga antes de concluir.")
                }
            },
            save = { usageReportText = it }
        )
        toolTargets["Consumo teórico vs. real"] = container.getChildAt(container.childCount - 1)

        addGap(container, 6)
        economicLogCard(
            container, "10 · Custo de horas de equipa",
            "Formato: horas previstas | custo total por hora com encargos | vendas esperadas | objetivo de custo de pessoal %. É um cenário de custos, não um horário nem recomendação laboral.",
            "labourPlan", "120 | 12,50 | 8000 | 25",
            "Calcular custo de pessoal", labourReportText,
            compute = { raw ->
                val result = RestaurantEconomicManager.analyzeLabourPlan(raw)
                buildString {
                    append("Custo estimado das horas: ").append(money(result.estimatedPayrollCost)).append('\n')
                    append("Peso nas vendas esperadas: ").append(economicPercent(result.estimatedPayrollPercent)).append('\n')
                    append("Horas equivalentes ao objetivo de ").append(economicPercent(result.targetPayrollPercent)).append(": ")
                        .append(result.hoursAtTarget.stripTrailingZeros().toPlainString()).append('\n')
                    if (result.hoursAboveTarget > BigDecimal.ZERO) {
                        append("Diferença acima desse cenário: ").append(result.hoursAboveTarget.stripTrailingZeros().toPlainString())
                            .append(" horas. Revê turnos, procura e regras laborais; não reduzas pessoal automaticamente.")
                    } else {
                        append("As horas introduzidas não ultrapassam o custo definido pelo objetivo.")
                    }
                }
            },
            save = { labourReportText = it }
        )
        toolTargets["Custo de horas de equipa"] = container.getChildAt(container.childCount - 1)

        val toolNames = toolTargets.keys.toList()
        val quickAccess = makeCard(13, paleGreen)
        quickAccess.addView(label("NAVEGAÇÃO RÁPIDA", 9.5f, true, forestSoft))
        addGap(quickAccess, 4)
        quickAccess.addView(label(
            "Desliza os atalhos e abre diretamente a ferramenta pretendida.",
            11.5f, false, ink
        ))
        addGap(quickAccess, 8)
        val toolChipScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val toolChipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        toolNames.forEach { toolName ->
            toolChipRow.addView(
                navigationChip(toolName) {
                    val target = toolTargets[toolName] ?: return@navigationChip
                    target.post {
                        val scroll = container.parent as? ScrollView
                        scroll?.smoothScrollTo(0, (target.top - dp(12)).coerceAtLeast(0))
                    }
                },
                LinearLayout.LayoutParams(-2, dp(42)).apply {
                    setMargins(0, 0, dp(7), 0)
                }
            )
        }
        toolChipScroll.addView(toolChipRow)
        quickAccess.addView(toolChipScroll, LinearLayout.LayoutParams(-1, dp(46)))
        container.addView(quickAccess, toolIndex)

        addGap(container, 4)
        container.addView(label(
            "Limites: cálculos indicativos, sem contabilidade certificada, ligação bancária, leitura automática de faturas, acesso a fornecedores ou movimentos automáticos de stock. Os registos de desperdício, propostas e comparações são manuais; por defeito ficam na sessão, mas o proprietário pode guardar uma cópia cifrada local por opção. Não são sincronizados nem alteram preços, stock, pagamentos ou encomendas.",
            10.5f, false, muted
        ))
    }

    private fun economicNumberField(
        parent: LinearLayout, key: String, title: String, hint: String, defaultValue: String = ""
    ) {
        parent.addView(label(title, 11.5f, true, forestDeep))
        val field = inputField(hint, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED)
        field.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                economicInputs[key] = s?.toString().orEmpty()
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        field.setText(economicInputs[key] ?: defaultValue)
        parent.addView(field, LinearLayout.LayoutParams(-1, dp(45)).apply { setMargins(0, dp(3), 0, dp(7)) })
    }

    private fun economicTextField(parent: LinearLayout, key: String, hint: String) {
        val field = multilineInputField(hint)
        field.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                economicInputs[key] = s?.toString().orEmpty()
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        field.setText(economicInputs[key].orEmpty())
        parent.addView(field, multilineFieldParams())
    }


    private fun economicLogCard(
        parent: LinearLayout,
        title: String,
        description: String,
        inputKey: String,
        hint: String,
        buttonTitle: String,
        reportText: String,
        compute: (String) -> String,
        save: (String) -> Unit
    ) {
        val card = makeCard(15)
        card.addView(label(title, 17f, true, forestDeep))
        addGap(card, 4)
        card.addView(label(description, 11.5f, false, muted))
        economicTextField(card, inputKey, hint)
        addGap(card, 3)
        card.addView(primaryButton(buttonTitle) {
            val result = try {
                compute(economicInputs[inputKey].orEmpty())
            } catch (e: IllegalArgumentException) {
                "Não foi possível analisar: ${e.message ?: "confirma os valores e unidades"}"
            }
            save(result)
            renderApp()
        }, LinearLayout.LayoutParams(-1, dp(44)))
        if (reportText.isNotBlank()) {
            addGap(card, 8)
            card.addView(label(reportText, 12f, false, ink))
        }
        parent.addView(card, cardParams())
    }

    private fun economicRequiredAmount(key: String, fieldName: String): BigDecimal =
        RestaurantEconomicManager.parseNonNegative(economicInputs[key].orEmpty(), fieldName)

    private fun economicPercent(value: BigDecimal): String =
        value.setScale(1, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',') + "%"

    private fun copyAssistantText(value: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Rascunho StudioMesa", value))
        Toast.makeText(this, "Rascunho copiado. Revê antes de publicar.", Toast.LENGTH_SHORT).show()
    }

    private fun renderAnalysis(container: LinearLayout) {
        val remoteMode = hasRemoteRestaurant()
        pageIntro(
            container,
            "Análise",
            if (remoteMode) "Indicadores operacionais dos dados partilhados no servidor."
            else "Resumo local dos dados de demonstração."
        )
        addGap(container, 6)
        if (remoteMode) {
            container.addView(
                secondaryButton("Atualizar análise do servidor") { refreshRemoteOperations() },
                LinearLayout.LayoutParams(-1, dp(44))
            )
            addGap(container, 6)
        }

        val periods = SalesAnalysisPeriod.values().toList()
        val periodScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val periodRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        periods.forEach { period ->
            periodRow.addView(
                filterChip(period.label, selectedAnalysisPeriod == period) {
                    selectedAnalysisPeriod = period
                    renderApp()
                },
                LinearLayout.LayoutParams(-2, dp(42)).apply {
                    setMargins(0, 0, dp(6), 0)
                }
            )
        }
        periodScroll.addView(periodRow)
        container.addView(periodScroll, LinearLayout.LayoutParams(-1, dp(46)))
        addGap(container, 6)
        container.addView(label(
            "O período é calculado pela data de criação do pedido e pelo fuso horário local deste dispositivo. O valor é nominal, não uma confirmação de pagamento.",
            11.5f, false, muted
        ))
        addGap(container, 8)

        val orders = if (remoteMode) {
            val remote = remoteOrders()
            (0 until remote.length()).mapNotNull { index ->
                val order = remote.optJSONObject(index) ?: return@mapNotNull null
                SalesAnalysisOrder(
                    createdAtMillis = parseOrderTimestamp(order.optString("created_at")),
                    status = order.optString("status", "Aberto"),
                    unitAmount = order.optDouble("amount", Double.NaN),
                    quantity = order.optInt("quantity", 1)
                )
            }
        } else {
            store.getOrders().map { order ->
                SalesAnalysisOrder(
                    createdAtMillis = order.createdAt,
                    status = order.status,
                    unitAmount = order.amount,
                    quantity = order.quantity
                )
            }
        }
        val summary = SalesAnalysisCalculator.summarize(orders, selectedAnalysisPeriod)

        val menuSize: Int
        val ratios: List<Double>
        if (remoteMode) {
            val remoteMenu = remoteMenuItems()
            menuSize = remoteMenu.length()
            ratios = (0 until remoteMenu.length()).mapNotNull { index ->
                val item = remoteMenu.optJSONObject(index) ?: return@mapNotNull null
                val price = item.optDouble("price", Double.NaN)
                val cost = item.optDouble("cost", Double.NaN)
                if (price.isFinite() && cost.isFinite() && price > 0.0 && cost >= 0.0) {
                    cost / price * 100.0
                } else {
                    null
                }
            }
        } else {
            val localMenu = store.getMenu()
            menuSize = localMenu.size
            ratios = localMenu.mapNotNull { item ->
                if (item.price.isFinite() && item.cost.isFinite() && item.price > 0.0 && item.cost >= 0.0) {
                    item.cost / item.price * 100.0
                } else {
                    null
                }
            }
        }
        val avgCostRatio = ratios.average()

        val summaryCards = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        summaryCards.addView(
            statCard(
                if (selectedAnalysisPeriod == SalesAnalysisPeriod.ALL_TIME) {
                    if (remoteMode) "PEDIDOS CARREGADOS" else "PEDIDOS REGISTADOS"
                } else "PEDIDOS NO PERÍODO",
                summary.ordersInPeriod.toString(),
                forestSoft
            ),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), dp(10)) }
        )
        summaryCards.addView(
            statCard("EM CURSO", summary.activeOrderCount.toString(), copper),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), 0, 0, dp(10)) }
        )
        container.addView(summaryCards)

        val metrics = makeCard(17)
        addMetric(metrics, "Pedidos criados no período e concluídos", summary.completedOrderCount.toString())
        addDivider(metrics)
        addMetric(metrics, "Valor nominal dos pedidos criados no período e concluídos", money(summary.nominalCompletedValue))
        addDivider(metrics)
        addMetric(metrics, "Artigos ativos na carta", menuSize.toString())
        addDivider(metrics)
        addMetric(
            metrics,
            "Média simples do custo atual / preço atual",
            if (avgCostRatio.isNaN()) "Sem dados" else String.format(Locale("pt", "PT"), "%.1f%%", avgCostRatio)
        )
        container.addView(metrics, cardParams())

        if (summary.ordersWithInvalidTimestampsExcluded > 0 && selectedAnalysisPeriod != SalesAnalysisPeriod.ALL_TIME) {
            addGap(container, 4)
            container.addView(emptyCard(
                "${summary.ordersWithInvalidTimestampsExcluded} pedido(s) com data ausente ou inválida ficam de fora deste período. Seleciona «Todo o histórico carregado» para incluir todos os registos disponíveis na aplicação."
            ), cardParams())
        }
        if (summary.completedOrdersWithInvalidAmount > 0) {
            addGap(container, 4)
            container.addView(emptyCard(
                "${summary.completedOrdersWithInvalidAmount} pedido(s) concluído(s) têm valor ou quantidade inválidos; esses valores não foram somados."
            ), cardParams())
        }

        addGap(container, 4)
        val note = makeCard(14, copperPale)
        note.addView(label(if (remoteMode) "Indicadores operacionais do servidor" else "Indicadores de demonstração", 13f, true, forest))
        addGap(note, 5)
        note.addView(label(
            if (remoteMode)
                "A soma usa apenas os pedidos que a aplicação recebeu e guardou em cache; confirma a última atualização. Se existirem mais pedidos do que os devolvidos pelo servidor, o resumo pode não cobrir todo o histórico. Não representa pagamentos confirmados nem vendas contabilísticas e não inclui IVA, custos indiretos, desperdício ou integração com caixa. O rácio da carta usa custos e preços atuais."
            else
                "Os valores usam os registos de demonstração guardados neste dispositivo. Não representam pagamentos confirmados nem contabilidade oficial. O rácio da carta usa custos e preços atuais.",
            12.5f, false, ink
        ))
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
        val pricePreview = label("", 11.5f, true, forest)
        fun refreshPricePreview() {
            val selectedItem = items.getOrNull(itemSpinner.selectedItemPosition)
            val selectedQuantity = quantityField.text.toString().trim().toIntOrNull()
                ?.takeIf { it in 1..99 }
            pricePreview.text = when {
                selectedItem == null -> "Seleciona um artigo para consultar o preço."
                selectedQuantity == null -> "Preço unitário: ${money(selectedItem.price)} · Introduz uma quantidade válida para calcular o subtotal."
                else -> "Preço unitário: ${money(selectedItem.price)} · Subtotal: ${money(selectedItem.price * selectedQuantity)}"
            }
        }
        itemSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                refreshPricePreview()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                refreshPricePreview()
            }
        }
        quantityField.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refreshPricePreview()
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        form.addView(pricePreview)
        addGap(form, 5)
        refreshPricePreview()
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
                    cloudMode && selected.id.isNullOrBlank() -> {
                        AlertDialog.Builder(this)
                            .setTitle("Artigo indisponível")
                            .setMessage("O artigo selecionado não tem um identificador válido. Atualiza a carta antes de criar o pedido.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    quantity == null || quantity !in 1..99 -> quantityField.error = "Usa uma quantidade entre 1 e 99"
                    notes.length > 500 -> notesField.error = "As notas podem ter no máximo 500 caracteres"
                    else -> {
                        if (cloudMode) {
                            val menuItemId = selected.id?.trim().orEmpty()
                            if (menuItemId.isBlank()) {
                                quantityField.error = "Atualiza a carta do servidor antes de criar este pedido."
                                return@setOnClickListener
                            }
                            val restaurantId = backendRestaurantId()
                            val signature = JSONObject()
                                .put("restaurant_id", restaurantId)
                                .put("menu_item_id", menuItemId)
                                .put("table_number", table)
                                .put("item_name", selected.name)
                                .put("amount", selected.price)
                                .put("menu_item_id", selected.id ?: JSONObject.NULL)
                                .put("unit_cost_snapshot", selected.cost.takeIf { it.isFinite() && it > 0.0 } ?: JSONObject.NULL)
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
                                    quantity, serviceCourse, notes, requestId,
                                    menuItemId = selected.id,
                                    unitCostSnapshot = selected.cost.takeIf { it.isFinite() && it > 0.0 }?.let { BigDecimal.valueOf(it) }
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

    private fun showRecipeDialog(menuItemId: String, menuItemName: String) {
        if (!isPremium || !hasRemoteRestaurant() || menuItemId.isBlank()) return
        runBackend({
            val token = backendToken()
            val restaurantId = backendRestaurantId()
            val recipe = SupabaseApi.getRestaurantRecipe(token, restaurantId, menuItemId)
            val stock = loadInventoryItemsForCurrentRole(token, restaurantId)
            val recipeId = recipe?.optString("id").orEmpty()
            val ingredients = if (recipeId.isNotBlank()) {
                SupabaseApi.listRecipeIngredients(token, recipeId)
            } else JSONArray()
            JSONObject()
                .put("recipe", recipe ?: JSONObject.NULL)
                .put("ingredients", ingredients)
                .put("stock", inventoryItemsWithOwnerCosts(stock))
                .toString()
        }) { raw ->
            showRecipeDialogContent(menuItemId, menuItemName, JSONObject(raw))
        }
    }

    private fun openRecipeInEconomicManager(analysis: InventoryRecipeCostAnalysis) {
        if (!analysis.isComplete || (analysis.preciseTotalCostPerPortion?.signum() ?: 0) <= 0) {
            Toast.makeText(
                this,
                "Confirma todos os custos de compra antes de transferir a receita.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val scope = if (hasRemoteRestaurant()) {
            "restaurant:" + backendRestaurantId() + ":" +
                backendPrefs.getString("user_id", "").orEmpty()
        } else "demo"
        if (economicScopeKey != null && economicScopeKey != scope) {
            clearEconomicManagerState()
        }

        val managerLines = analysis.ingredients.joinToString("\n") { ingredient ->
            val safeName = ingredient.name
                .replace("|", "/")
                .replace(Regex("[\\r\\n]+"), " ")
                .trim()
            val unitCost = ingredient.unitCostPerStockUnit
                ?: throw IllegalStateException("Falta o custo de compra de «${ingredient.name}».")
            "$safeName | ${ingredient.quantityPerPortion.toPlainString()} | " +
                "${unitCost.toPlainString()} | " +
                ingredient.wastePercent.toPlainString()
        }
        economicManagerEnabled = true
        economicScopeKey = scope
        economicInputs["recipe"] = managerLines
        if (economicInputs["foodTarget"].isNullOrBlank()) {
            economicInputs["foodTarget"] = "30"
        }
        recipeReportText = ""
        activePage = "Gestão económica"
        renderApp()
    }

    private fun showRecipeDialogContent(
        menuItemId: String,
        menuItemName: String,
        data: JSONObject
    ) {
        val recipe = data.optJSONObject("recipe")
        val recipeId = recipe?.optString("id").orEmpty()
        val active = recipe?.optBoolean("is_active", false) ?: false
        val requiresReview = recipe?.optBoolean("requires_review", false) ?: false
        val inventory = data.optJSONArray("stock") ?: JSONArray()
        val ingredients = data.optJSONArray("ingredients") ?: JSONArray()
        val inventoryById = mutableMapOf<String, JSONObject>()
        for (index in 0 until inventory.length()) {
            val item = inventory.optJSONObject(index) ?: continue
            inventoryById[item.optString("id")] = item
        }

        // Purchase-cost data is private to the restaurant owner.
        val ownerCanSeeRecipeCost = !hasRemoteRestaurant() ||
            backendPrefs.getString("restaurant_role", "").orEmpty() == "owner"
        val recipeCostInputs = if (ownerCanSeeRecipeCost) {
            runCatching {
                (0 until ingredients.length()).map { index ->
                    val line = ingredients.optJSONObject(index)
                        ?: throw IllegalArgumentException("A ficha técnica contém uma linha inválida.")
                    val stockItemId = line.optString("inventory_item_id")
                    val stockItem = inventoryById[stockItemId]
                    val ingredientName = stockItem?.optString("name")
                        ?.takeIf { it.isNotBlank() }
                        ?: "Ingrediente sem artigo de stock"
                    val stockUnit = stockItem?.optString("unit")
                        ?.takeIf { it.isNotBlank() }
                        ?: "unidades"
                    val quantity = try {
                        BigDecimal(line.optString("quantity_per_portion"))
                    } catch (_: NumberFormatException) {
                        throw IllegalArgumentException("Quantidade inválida em «$ingredientName».")
                    }
                    val waste = try {
                        BigDecimal(line.optString("waste_percent", "0"))
                    } catch (_: NumberFormatException) {
                        throw IllegalArgumentException("Desperdício inválido em «$ingredientName».")
                    }
                    val unitCost = if (stockItem == null || stockItem.isNull("unit_cost_per_unit")) {
                        null
                    } else {
                        try {
                            BigDecimal(stockItem.get("unit_cost_per_unit").toString())
                        } catch (_: NumberFormatException) {
                            throw IllegalArgumentException("O custo de compra de «$ingredientName» não é válido.")
                        }
                    }
                    InventoryRecipeIngredientInput(
                        name = ingredientName,
                        stockUnit = stockUnit,
                        quantityPerPortion = quantity,
                        unitCostPerStockUnit = unitCost,
                        wastePercent = waste
                    )
                }
            }.getOrElse {
                null
            }
        } else null
        val recipeCostAnalysis = recipeCostInputs?.let { inputs ->
            runCatching { InventoryRecipeCostCalculator.calculate(inputs) }.getOrNull()
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(10))
        }
        val statusCard = makeCard(12, if (active) paleGreen else copperPale)
        val statusTitle = when {
            active -> "Consumo automático ativo"
            requiresReview -> "Revisão do proprietário necessária"
            else -> "Receita ainda não aprovada"
        }
        val statusDescription = when {
            active -> "Quando um pedido ligado a este prato passa de «Aberto» para «Em preparação», o servidor desconta os ingredientes desta receita."
            requiresReview -> "A receita ou uma unidade de stock foi alterada. Os pedidos deste prato ficam bloqueados até o proprietário rever as quantidades e aprovar novamente."
            else -> "Configura os ingredientes e pede ao proprietário para ativar o consumo automático. A receita começa sempre desligada."
        }
        statusCard.addView(label(
            statusTitle,
            14f, true, if (active) forest else forestDeep
        ))
        addGap(statusCard, 4)
        statusCard.addView(label(statusDescription, 11.5f, false, ink))
        content.addView(statusCard)
        addGap(content, 10)

        if (ownerCanSeeRecipeCost && ingredients.length() > 0) {
            val costCard = makeCard(
                13,
                if (recipeCostAnalysis != null && recipeCostAnalysis.isComplete) paleGreen else copperPale
            )
            costCard.addView(label("Custo da receita", 14f, true, forestDeep))
            addGap(costCard, 4)
            when {
                recipeCostAnalysis == null -> {
                    costCard.addView(label(
                        "Não foi possível calcular o custo. Confirma se todos os ingredientes e quantidades da ficha técnica estão válidos.",
                        11.5f, false, muted
                    ))
                }
                recipeCostAnalysis != null && recipeCostAnalysis.isComplete -> {
                    val analysis = recipeCostAnalysis
                    costCard.addView(label(
                        "Custo estimado por dose: ${formatEuro(analysis.totalCostPerPortion!!)}",
                        18f, true, forest
                    ))
                    addGap(costCard, 3)
                    analysis.ingredients.forEach { ingredient ->
                        val line = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                        }
                        line.addView(
                            label(
                                "${ingredient.name} · ${formatQuantity(ingredient.quantityPerPortion.toDouble())} ${ingredient.stockUnit}",
                                11.5f, false, ink
                            ),
                            LinearLayout.LayoutParams(0, -2, 1f)
                        )
                        line.addView(label(
                            formatEuro(ingredient.costPerPortion!!),
                            11.5f, true, forestDeep
                        ))
                        costCard.addView(line)
                    }
                    val target = economicInputs["foodTarget"].orEmpty().ifBlank { "30" }
                    val targetValue = runCatching {
                        RestaurantEconomicManager.parseNonNegative(target, "objetivo de custo alimentar")
                    }.getOrNull()
                    if (targetValue != null && targetValue.signum() > 0 && targetValue <= BigDecimal("100")) {
                        val suggested = runCatching {
                            analysis.suggestedPriceAtTarget(targetValue)
                        }.getOrNull()
                        if (suggested != null) {
                            addGap(costCard, 4)
                            costCard.addView(label(
                                "Preço teórico à meta de ${targetValue.stripTrailingZeros().toPlainString().replace('.', ',')}%: ${formatEuro(suggested)}",
                                12f, true, forestDeep
                            ))
                        }
                    }
                    addGap(costCard, 5)
                    costCard.addView(label(
                        "Estimativa com os custos de compra atuais do inventário e o desperdício definido na ficha. Não inclui IVA, mão de obra, energia, renda ou outros custos indiretos.",
                        10.5f, false, muted
                    ))
                    addGap(costCard, 7)
                    if ((analysis.preciseTotalCostPerPortion?.signum() ?: 0) > 0) {
                        costCard.addView(
                            secondaryButton("Usar no Gestor económico") {
                                openRecipeInEconomicManager(analysis)
                            },
                            LinearLayout.LayoutParams(-1, dp(44))
                        )
                    } else {
                        costCard.addView(label(
                            "O custo total registado é zero. Confirma se os custos de compra foram preenchidos antes de usar esta receita para sugerir um preço.",
                            10.5f, false, muted
                        ))
                    }
                }
                else -> {
                    costCard.addView(label(
                        "Custo parcial conhecido: ${formatEuro(recipeCostAnalysis.knownCostSubtotal)}",
                        14f, true, forestDeep
                    ))
                    addGap(costCard, 3)
                    costCard.addView(label(
                        "Falta indicar o custo de compra de: ${recipeCostAnalysis.missingCostIngredientNames.joinToString(", ")}.",
                        11.5f, false, red
                    ))
                    costCard.addView(label(
                        "O custo total e o preço indicativo ficam indisponíveis até todos os ingredientes terem um custo conhecido.",
                        10.5f, false, muted
                    ))
                }
            }
            content.addView(costCard, cardParams())
            addGap(content, 8)
        }

        if (ingredients.length() == 0) {
            content.addView(emptyCard("Ainda não há ingredientes associados. Adiciona cada ingrediente com a quantidade necessária por dose."))
        } else {
            content.addView(label("Ingredientes por dose", 13.5f, true, forestDeep))
            addGap(content, 5)
            for (index in 0 until ingredients.length()) {
                val line = ingredients.optJSONObject(index) ?: continue
                val inventoryItemId = line.optString("inventory_item_id")
                val stockItem = inventoryById[inventoryItemId]
                val ingredientName = stockItem?.optString("name", "Ingrediente") ?: "Ingrediente"
                val unit = stockItem?.optString("unit", "unidades") ?: "unidades"
                val amount = line.optString("quantity_per_portion")
                val waste = line.optString("waste_percent", "0")
                val card = makeCard(12)
                card.addView(label(ingredientName, 14f, true, forestDeep))
                addGap(card, 3)
                card.addView(label(
                    "$amount $unit por dose · desperdício de $waste%",
                    11.5f, false, muted
                ))
                addGap(card, 7)
                card.addView(secondaryButton("Remover ingrediente") {
                    AlertDialog.Builder(this)
                        .setTitle("Remover ingrediente?")
                        .setMessage("A remoção desativa a receita e exige nova aprovação do proprietário.")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Remover") { _, _ ->
                            runBackend({
                                SupabaseApi.removeRecipeIngredient(
                                    backendToken(), recipeId, inventoryItemId
                                )
                                "OK"
                            }) {
                                showRecipeDialog(menuItemId, menuItemName)
                            }
                        }
                        .show()
                }, LinearLayout.LayoutParams(-1, dp(44)))
                content.addView(card, cardParams())
                addGap(content, 5)
            }
        }

        addGap(content, 7)
        content.addView(primaryButton("+  Adicionar ingrediente") {
            if (inventory.length() == 0) {
                AlertDialog.Builder(this)
                    .setTitle("Inventário vazio")
                    .setMessage("Adiciona primeiro os ingredientes na secção Stock.")
                    .setPositiveButton("OK", null)
                    .show()
            } else {
                showAddRecipeIngredientDialog(menuItemId, menuItemName, inventory)
            }
        }, LinearLayout.LayoutParams(-1, dp(48)))

        val isOwner = backendPrefs.getString("restaurant_role", "").orEmpty() == "owner"
        if (recipeId.isNotBlank() && isOwner) {
            addGap(content, 6)
            content.addView(secondaryButton(
                when {
                    active -> "Desativar consumo automático"
                    requiresReview -> "Rever e aprovar receita"
                    else -> "Aprovar e ativar consumo automático"
                }
            ) {
                val confirmation = when {
                    active -> "Os novos pedidos deixam de descontar ingredientes automaticamente. Os movimentos já registados não são revertidos."
                    requiresReview -> "Revê o artigo, cada ingrediente, a unidade e as quantidades por dose. Só aproves depois de confirmares que a ficha técnica está correta. A partir daí, o servidor volta a descontar o stock ao iniciar a preparação."
                    else -> "Confirma que a receita e as quantidades por dose foram verificadas. O stock passa a ser descontado quando os pedidos começam a ser preparados."
                }
                AlertDialog.Builder(this)
                    .setTitle(
                        when {
                            active -> "Desativar receita?"
                            requiresReview -> "Confirmar revisão?"
                            else -> "Ativar receita?"
                        }
                    )
                    .setMessage(confirmation)
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton(if (active) "Desativar" else "Aprovar e ativar") { _, _ ->
                        runBackend({
                            SupabaseApi.setRecipeActive(
                                backendToken(), backendRestaurantId(), menuItemId, !active
                            )
                            "OK"
                        }) {
                            showRecipeDialog(menuItemId, menuItemName)
                        }
                    }
                    .show()
            }, LinearLayout.LayoutParams(-1, dp(48)))
        } else if (recipeId.isNotBlank()) {
            addGap(content, 5)
            content.addView(label(
                if (requiresReview) {
                    "A receita foi alterada e aguarda revisão do proprietário. Os pedidos deste prato não podem iniciar preparação."
                } else {
                    "A ativação e desativação do consumo automático são exclusivas do proprietário."
                },
                10.5f, false, muted
            ))
        }

        addGap(content, 7)
        content.addView(label(
            "As quantidades são guardadas na unidade original do inventário. Na introdução são aceites conversões entre kg/g e L/ml, sem misturar massa com volume nem arredondar valores sem precisão suficiente. Alterar quantidades, mudar a unidade ou eliminar um ingrediente exige nova revisão e aprovação do proprietário; enquanto a revisão estiver pendente, os pedidos deste prato não avançam para preparação.",
            10.5f, false, muted
        ))

        val scroll = ScrollView(this).apply { addView(content) }
        AlertDialog.Builder(this)
            .setTitle("Receita · $menuItemName")
            .setView(scroll)
            .setPositiveButton("Fechar", null)
            .show()
    }

    private fun showAddRecipeIngredientDialog(
        menuItemId: String,
        menuItemName: String,
        inventory: JSONArray
    ) {
        val stockItems = (0 until inventory.length()).mapNotNull { index ->
            inventory.optJSONObject(index)?.let { item ->
                val id = item.optString("id").trim()
                if (id.isBlank()) null else item
            }
        }
        if (stockItems.isEmpty()) {
            AlertDialog.Builder(this)
                .setMessage("Não há artigos de inventário válidos para associar.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(10))
        }
        val ingredientSpinner = Spinner(this)
        ingredientSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            stockItems.map { item ->
                "${item.optString("name")} · ${item.optString("unit", "unidades")}"
            }
        )
        val enteredUnitSpinner = Spinner(this)
        var unitOptionsStockItemId = ""
        fun updateIngredientUnitOptions(position: Int) {
            val selectedItem = stockItems.getOrNull(position) ?: return
            val stockItemId = selectedItem.optString("id")
            if (stockItemId == unitOptionsStockItemId) return

            val stockUnit = selectedItem.optString("unit", "unidades")
                .trim()
                .ifBlank { "unidades" }
            val options = InventoryUnitConverter.compatibleUnits(stockUnit)
            enteredUnitSpinner.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                options
            )
            enteredUnitSpinner.setSelection(0, false)
            unitOptionsStockItemId = stockItemId
        }
        updateIngredientUnitOptions(0)
        ingredientSpinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    updateIngredientUnitOptions(position)
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }

        val quantity = inputField(
            "Quantidade por dose (ex.: 150)",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        )
        val waste = inputField(
            "Desperdício de preparação (%)",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        ).apply { setText("0") }

        form.addView(label("Ingrediente do inventário", 11f, true, forestSoft))
        form.addView(ingredientSpinner, fieldParams())
        addGap(form, 5)
        form.addView(label("Unidade da quantidade introduzida", 11f, true, forestSoft))
        form.addView(enteredUnitSpinner, fieldParams())
        addGap(form, 5)
        form.addView(label("Quantidade necessária por dose", 11f, true, forestSoft))
        form.addView(quantity, fieldParams())
        addGap(form, 5)
        form.addView(label("Desperdício esperado na preparação", 11f, true, forestSoft))
        form.addView(waste, fieldParams())
        addGap(form, 7)
        form.addView(label(
            "A quantidade é convertida automaticamente para a unidade do inventário (por exemplo, 150 g = 0,150 kg). Só são aceites unidades compatíveis e conversões sem perda de precisão. O desperdício aumenta a quantidade retirada.",
            10.5f, false, muted
        ))

        val dialog = AlertDialog.Builder(this)
            .setTitle("Adicionar ingrediente · $menuItemName")
            .setView(scrollableForm(form))
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar ingrediente", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selected = stockItems.getOrNull(ingredientSpinner.selectedItemPosition)
                if (selected == null) {
                    ingredientSpinner.requestFocus()
                    return@setOnClickListener
                }
                val enteredQuantity = try {
                    RestaurantRecipeInputValidator.parseQuantityPerPortion(
                        quantity.text.toString()
                    )
                } catch (error: IllegalArgumentException) {
                    quantity.error = error.message ?: "Quantidade inválida."
                    return@setOnClickListener
                }
                val stockUnit = selected.optString("unit", "unidades")
                    .trim()
                    .ifBlank { "unidades" }
                val enteredUnit = enteredUnitSpinner.selectedItem?.toString().orEmpty()
                val quantityValue = try {
                    InventoryUnitConverter.convertToStockUnit(
                        enteredQuantity,
                        enteredUnit,
                        stockUnit
                    )
                } catch (error: IllegalArgumentException) {
                    quantity.error = error.message ?: "Não foi possível converter a unidade."
                    return@setOnClickListener
                }
                val wasteValue = try {
                    RestaurantRecipeInputValidator.parseWastePercent(waste.text.toString())
                } catch (error: IllegalArgumentException) {
                    waste.error = error.message ?: "Percentagem inválida."
                    return@setOnClickListener
                }
                runBackend({
                    SupabaseApi.saveRecipeIngredient(
                        backendToken(),
                        backendRestaurantId(),
                        menuItemId,
                        selected.optString("id"),
                        quantityValue,
                        wasteValue
                    )
                    "OK"
                }) {
                    dialog.dismiss()
                    showRecipeDialog(menuItemId, menuItemName)
                }
            }
        }
        dialog.show()
        dialog.window?.setLayout(-1, dp(560))
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

    private fun navigationChip(title: String, action: () -> Unit): TextView =
        label(title, 11f, true, forestDeep).apply {
            gravity = Gravity.CENTER
            minHeight = dp(38)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rippleBackground(white, 18)
            contentDescription = "Ir para $title"
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun addMenuCostOverview(container: LinearLayout, itemCount: Int, validCostCount: Int) {
        val needsReview = (itemCount - validCostCount).coerceAtLeast(0)
        val overview = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        overview.addView(statCard("ARTIGOS", itemCount.toString(), forestSoft),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(4), dp(8)) })
        overview.addView(statCard("CUSTOS VÁLIDOS", validCostCount.toString(), green),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(2), 0, dp(2), dp(8)) })
        overview.addView(statCard("A REVER", needsReview.toString(), if (needsReview > 0) copper else forestSoft),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), 0, 0, dp(8)) })
        container.addView(overview)
        container.addView(label(
            "Custo válido = preço positivo e custo não negativo registados. Os restantes artigos precisam de revisão antes de usar análises de margem.",
            10.5f, false, muted
        ))
        addGap(container, 8)
    }

    private fun addOrderStatusOverview(container: LinearLayout, statuses: List<String>) {
        val newCount = statuses.count { it == "Aberto" }
        val preparingCount = statuses.count { it == "Em preparação" }
        val readyCount = statuses.count { it == "Pronto" }
        val activeCount = statuses.count { it !in setOf("Concluído", "Cancelado") }
        val finishedCount = statuses.count { it in setOf("Concluído", "Cancelado") }

        val overview = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        overview.addView(statCard("NOVOS", newCount.toString(), if (newCount > 0) copper else forestSoft),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(4), dp(8)) })
        overview.addView(statCard("EM PREPARAÇÃO", preparingCount.toString(), if (preparingCount > 0) copper else forestSoft),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(2), 0, dp(2), dp(8)) })
        overview.addView(statCard("PRONTOS", readyCount.toString(), if (readyCount > 0) green else forestSoft),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), 0, 0, dp(8)) })
        container.addView(overview)
        container.addView(label(
            "${statuses.size} pedido(s) registado(s) · $activeCount ativo(s) · $finishedCount concluído(s) ou cancelado(s)",
            10.5f, false, muted
        ))
        addGap(container, 8)
    }

    private fun filterChip(
        title: String,
        selected: Boolean,
        action: () -> Unit
    ): TextView = label(
        title,
        11.5f,
        true,
        if (selected) forestDeep else muted
    ).apply {
        gravity = Gravity.CENTER
        minHeight = dp(38)
        setPadding(dp(13), dp(8), dp(13), dp(8))
        background = rippleBackground(if (selected) copperPale else white, 18)
        contentDescription = if (selected) "Filtro $title selecionado" else "Aplicar filtro $title"
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun statusBadge(
        text: String,
        foreground: Int,
        backgroundColor: Int,
        textSize: Float = 9.5f
    ): TextView = label(text, textSize, true, foreground).apply {
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        letterSpacing = 0.02f
        setPadding(dp(8), dp(6), dp(8), dp(6))
        background = rounded(backgroundColor, 11)
    }

    private fun bottomNavigationItem(
        page: String,
        selected: Boolean,
        action: () -> Unit
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(3), dp(3), dp(3), dp(3))
        background = rippleBackground(if (selected) copperPale else white, 12)
        contentDescription = if (selected) "$page, selecionado" else "Abrir secção $page"
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }

        addView(View(this@MainActivity).apply {
            background = rounded(if (selected) copper else white, 4)
        }, LinearLayout.LayoutParams(dp(20), dp(3)))

        addView(TextView(this@MainActivity).apply {
            text = pageGlyph(page)
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(if (selected) forestDeep else muted)
        }, LinearLayout.LayoutParams(-2, dp(21)))

        addView(label(page, 10.5f, selected, if (selected) forestDeep else muted).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
    }

    private fun pageIntro(container: LinearLayout, title: String, subtitle: String) {
        container.addView(label("STUDIOMESA  /  RESTAURANTE", 9.5f, true, forestSoft).apply {
            letterSpacing = 0.08f
        })
        addGap(container, 6)
        container.addView(label(title, 25f, true, forestDeep).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        addGap(container, 4)
        container.addView(label(subtitle, 13f, false, muted))
    }

    private fun sectionHeading(container: LinearLayout, title: String, subtitle: String) {
        container.addView(label(title, 17f, true, forestDeep))
        addGap(container, 3)
        container.addView(label(subtitle, 12f, false, muted))
        addGap(container, 9)
    }

    private fun addActionPair(
        container: LinearLayout,
        primaryTitle: String,
        primaryAction: () -> Unit,
        secondaryTitle: String? = null,
        secondaryAction: (() -> Unit)? = null
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (secondaryTitle != null && secondaryAction != null) {
            row.addView(
                secondaryButton(secondaryTitle, secondaryAction),
                LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                    setMargins(0, 0, dp(5), 0)
                }
            )
        }
        val primaryWidth = if (secondaryTitle != null && secondaryAction != null) 0 else -1
        val primaryWeight = if (primaryWidth == 0) 1f else 0f
        row.addView(
            primaryButton(primaryTitle, primaryAction),
            LinearLayout.LayoutParams(primaryWidth, dp(48), primaryWeight).apply {
                if (primaryWeight > 0f) setMargins(dp(5), 0, 0, 0)
            }
        )
        container.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    private fun homeActionTile(title: String, subtitle: String, destination: String): LinearLayout {
        val tile = makeCard(13, white).apply {
            background = rippleBackground(white, 18)
            elevation = dp(1).toFloat()
            minimumHeight = dp(116)
            isClickable = true
            isFocusable = true
            contentDescription = "Abrir $title"
            setOnClickListener {
                activePage = destination
                renderApp()
            }
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(this).apply {
            text = pageGlyph(destination)
            textSize = 18f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(forestDeep)
            background = rounded(paleGreen, 12)
        }, LinearLayout.LayoutParams(dp(38), dp(38)))

        titleRow.addView(label(title, 13.5f, true, forestDeep).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(9), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        tile.addView(titleRow)

        addGap(tile, 9)
        tile.addView(label(subtitle, 11.5f, false, muted).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        addGap(tile, 8)
        tile.addView(label("ABRIR  ↗", 9f, true, forestSoft))
        return tile
    }

    private fun actionCard(parent: LinearLayout, number: String, title: String, subtitle: String, actionText: String, action: () -> Unit) {
        val card = makeCard(14)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(label(number, 11f, true, forestSoft), LinearLayout.LayoutParams(dp(28), -2))
        val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        text.addView(label(title, 16f, true, ink))
        addGap(text, 3)
        text.addView(label(subtitle, 12.5f, false, muted))
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(row)
        addGap(card, 12)
        card.addView(secondaryButton(actionText, action), LinearLayout.LayoutParams(-1, dp(48)))
        parent.addView(card, cardParams())
    }

    private fun statCard(title: String, value: String, accent: Int): LinearLayout {
        val card = makeCard(14)
        card.addView(label(title, 9f, true, muted).apply {
            maxLines = 2
        })
        addGap(card, 6)
        card.addView(label(value, 23f, true, forestDeep).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        addGap(card, 8)
        card.addView(View(this).apply { setBackgroundColor(accent) },
            LinearLayout.LayoutParams(dp(30), dp(3)))
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
            background = rippleBackground(copper, 15)
            elevation = dp(1).toFloat()
            setPadding(dp(12), dp(3), dp(12), dp(3))
            setOnClickListener { action() }
        }

    private fun dangerButton(title: String, action: () -> Unit): Button =
        Button(this).apply {
            text = title
            textSize = 11.5f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            isAllCaps = false
            setTextColor(red)
            background = rippleBackground(paleRed, 14)
            elevation = 0f
            setPadding(dp(8), dp(2), dp(8), dp(2))
            setOnClickListener { action() }
        }

    private fun secondaryButton(title: String, action: () -> Unit): Button =
        Button(this).apply {
            text = title
            textSize = 11.5f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            isAllCaps = false
            setTextColor(forest)
            background = rippleBackground(paleGreen, 14)
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

    private fun rippleBackground(
        color: Int,
        radius: Int,
        rippleColor: Int = Color.argb(42, 43, 59, 50)
    ): Drawable {
        val base = rounded(color, radius)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            RippleDrawable(
                ColorStateList.valueOf(rippleColor),
                base,
                rounded(Color.WHITE, radius)
            )
        } else {
            base
        }
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

    private fun money(value: BigDecimal): String =
        NumberFormat.getCurrencyInstance(Locale("pt", "PT")).format(value)

    // Ingredient-level recipe costs can be below one cent. Preserve enough precision
    // to reconcile the rounded recipe total with the individual ingredient lines.
    private fun preciseMoney(value: BigDecimal): String {
        val format = NumberFormat.getCurrencyInstance(Locale("pt", "PT"))
        format.minimumFractionDigits = 2
        format.maximumFractionDigits = 8
        format.setRoundingMode(java.math.RoundingMode.HALF_UP)
        return format.format(value)
    }

    // Keep one decimal for ordinary values but retain a second decimal when it is material (e.g. 0.01%).
    private fun formatPercentage(value: BigDecimal): String =
        DecimalFormat("0.0#", DecimalFormatSymbols.getInstance(Locale("pt", "PT"))).format(value) + "%"

    private fun money(value: Double): String =
        NumberFormat.getCurrencyInstance(Locale("pt", "PT")).format(value)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
