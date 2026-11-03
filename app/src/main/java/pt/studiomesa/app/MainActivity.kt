package pt.studiomesa.app

import android.app.Activity
import android.app.Dialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.ColorStateList
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Bitmap
import android.widget.ImageView
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.integration.android.IntentIntegrator
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

    private var activityResumed = false
    private var kitchenAutoRefreshScheduled = false
    private var kitchenRefreshInFlight = false
    private var kitchenOrdersCache: JSONArray? = null
    private var kitchenQueueUpdatedAtMillis = 0L
    private var kitchenQueueRefreshFailed = false
    private var kitchenPageContainer: LinearLayout? = null
    private var inventoryAutoRefreshScheduled = false
    private var inventoryRefreshInFlight = false
    private var inventoryUpdatedAtMillis = 0L
    private var inventoryRefreshFailed = false
    private var inventoryPageContainer: LinearLayout? = null
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
    private var assistantAnswer: StudioMesaAssistantAnswer? = null
    private var assistantConversations = JSONArray()
    private var assistantMessages = JSONArray()
    private var assistantInvoices = JSONArray()
    private var assistantCurrentConversationId = ""
    private var assistantHistoryQuery = ""
    private var assistantDraft = ""
    private var assistantWebSearchEnabled = false
    private val assistantSelectedInvoiceIds = linkedSetOf<String>()
    private var assistantShowLocalTools = false
    private var assistantWorkspaceScope = ""
    private var assistantWorkspaceLoaded = false
    private var assistantWorkspaceLoading = false
    private var assistantWorkspaceError = ""
    private var assistantMessagesLoading = false
    private var assistantBusy = false
    private val requestAssistantInvoice = 4201
    private var selectedAnalysisPeriod = SalesAnalysisPeriod.ALL_TIME

    // Owner-controlled economic tools: session-only until persistent authorization is designed.
    private var economicManagerEnabled = false
    private var economicScopeKey: String? = null
    private val economicInputs = mutableMapOf<String, String>()
    private var economicReportText = ""
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
    // Customer reservation details contain names, telephone numbers and notes.
    // Keep this snapshot in memory only and scope every refresh to the active account/card.
    private var customerReservationsCache = JSONArray()
    private var customerReservationsLoaded = false
    private var customerReservationsRefreshInFlight = false
    private var customerReservationsAccountScope = ""
    private var customerReservationsMemberScope = ""
    private var customerReservationsError = ""
    // Authoritative session snapshot used for navigation only; server RPC/RLS is the security boundary.
    private var currentStaffPermissions: Set<String>? = null
    private var staffPermissionRefreshInFlight = false

    private val flavor = BuildConfig.FLAVOR.lowercase(Locale.ROOT)
    private val isCustomerApp = flavor.contains("cliente")
    private val isPremium = flavor.contains("premium")
    private val hasCalculator = flavor.contains("profissional") || isPremium

    // StudioMesa visual system: deep petrol, warm brass and soft stone surfaces.
    private val forest = Color.rgb(43, 74, 72)
    private val forestDeep = Color.rgb(24, 43, 45)
    private val forestSoft = Color.rgb(82, 119, 113)
    private val copper = Color.rgb(205, 169, 113)
    private val copperPale = Color.rgb(246, 239, 226)
    private val paper = Color.rgb(245, 246, 243)
    private val white = Color.WHITE
    private val ink = Color.rgb(35, 51, 51)
    private val muted = Color.rgb(105, 120, 117)
    private val line = Color.rgb(225, 231, 227)
    private val paleGreen = Color.rgb(232, 241, 237)
    private val paleRed = Color.rgb(250, 237, 234)
    private val red = Color.rgb(157, 65, 56)
    private val green = Color.rgb(46, 116, 91)

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
        // Close restricted screens and start authorization refresh before auto-refresh
        // can query stock or kitchen data from the previous foreground session.
        refreshStaffPermissionsSilently()
        updateKitchenAutoRefresh()
        updateInventoryAutoRefresh()
    }

    override fun onPause() {
        activityResumed = false
        stopKitchenAutoRefresh()
        stopInventoryAutoRefresh()
        super.onPause()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallback(intent)
    }

    @Deprecated("The embedded QR scanner and PDF picker currently use Activity result callbacks.")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: android.content.Intent?
    ) {
        if (requestCode == requestAssistantInvoice) {
            if (resultCode == RESULT_OK) data?.data?.let { uploadAssistantInvoiceFromUri(it) }
            return
        }
        val scan = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (scan != null) {
            val contents = scan.contents
            if (!contents.isNullOrBlank()) handleScannedInvoiceLoyaltyQr(contents)
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
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
        val isStaff = hasRemoteRestaurant() &&
            backendPrefs.getString("restaurant_role", "staff") == "staff"
        return StaffPermissionSet.filterPages(
            pages,
            currentStaffPermissions.orEmpty(),
            isStaff
        )
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
        "Prémios" -> "✦"
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
        val headerStatus = label(
            if (hasRemoteRestaurant()) "CONTA CONFIGURADA" else "MODO DE DEMONSTRAÇÃO",
            9.5f, true, forestDeep
        ).apply {
            setPadding(dp(9), dp(6), dp(9), dp(6))
            background = rounded(copper, 10)
        }
        header.addView(headerStatus, LinearLayout.LayoutParams(-2, -2))
        addGap(header, 6)
        header.addView(label("Gestão de sala, sem complicações.", 13f, false, Color.rgb(229, 235, 228)))

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
            setPadding(dp(6), dp(5), dp(6), dp(5))
            background = cardSurface(white, 19)
            elevation = dp(7).toFloat()
        }
        StaffPermissionSet.bottomNavigationPages(availablePages()).forEach { page ->
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
        appRoot.addView(nav, LinearLayout.LayoutParams(-1, dp(66)).apply {
            setMargins(dp(12), dp(3), dp(12), dp(8))
        })
        setContentView(appRoot)
        if (activePage == "Pontos" && hasRemoteRestaurant() &&
            (!LoyaltyCacheScope.belongsTo(
                backendPrefs.getString("restaurant_loyalty_cache_restaurant_id", null),
                backendRestaurantId()
            ) ||
                !backendPrefs.contains("restaurant_loyalty_rewards_json") ||
                !backendPrefs.contains("restaurant_loyalty_redemptions_json"))) {
            refreshRestaurantLoyaltyShop()
        }
        updateKitchenAutoRefresh()
        updateInventoryAutoRefresh()
    }

    private fun renderPage(container: LinearLayout) {
        if (activePage != "Cozinha") kitchenPageContainer = null
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
        container.addView(label("STUDIOMESA  /  PAINEL DE GESTÃO", 10f, true, forestSoft).apply {
            letterSpacing = 0.12f
        })
        addGap(container, 7)
        container.addView(label("A sala, sob controlo.", 29f, true, forestDeep))
        addGap(container, 5)
        container.addView(label(
            "O essencial da operação, organizado num só lugar.",
            13.5f, false, muted
        ))
        addGap(container, 15)

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
        val accessiblePages = availablePages().toSet()

        val statusCard = makeCard(14, forestDeep).apply {
            background = rounded(forestDeep, 20)
        }
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusRow.addView(statusDot(if (hasRemoteRestaurant()) Color.rgb(116, 190, 151) else copper),
            LinearLayout.LayoutParams(dp(8), dp(8)).apply { setMargins(0, 0, dp(9), 0) })
        statusRow.addView(label(
            if (hasRemoteRestaurant()) "CONTA DO RESTAURANTE CONFIGURADA" else "AMBIENTE DE DEMONSTRAÇÃO",
            9.5f, true, copperPale
        ))
        statusCard.addView(statusRow)
        addGap(statusCard, 6)
        statusCard.addView(label(
            if (hasRemoteRestaurant())
                "As áreas disponíveis respeitam as permissões atribuídas à tua conta."
            else
                "Explora a aplicação com dados de exemplo. Liga uma conta para ativar a gestão partilhada.",
            12f, false, Color.rgb(226, 235, 231)
        ))
        container.addView(statusCard, cardParams())

        addGap(container, 7)
        sectionHeading(container, "Resumo da operação", "Indicadores disponíveis nesta aplicação.")
        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        if ("Mesas" in accessiblePages) {
            stats.addView(statCard("MESAS OCUPADAS", occupied.toString() + " / " + tableTotal, forestSoft),
                LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), 0) })
        }
        if ("Pedidos" in accessiblePages) {
            stats.addView(statCard("PEDIDOS ATIVOS", activeOrders.toString(), copper),
                LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(if (stats.childCount > 0) dp(6) else 0, 0, 0, 0) })
        }
        if (stats.childCount > 0) container.addView(stats)

        addGap(container, 17)
        sectionHeading(container, "Acesso rápido", "Entra diretamente nas áreas que mais utilizas.")
        if ("Mesas" in accessiblePages) {
            actionCard(container, "01", "Mesas", "Consulta as " + tableTotal + " mesas do restaurante.", "ABRIR MESAS") {
                activePage = "Mesas"; renderApp()
            }
        }
        if ("Pedidos" in accessiblePages) {
            actionCard(container, "02", "Pedidos", "Regista pedidos e acompanha a conclusão.", "ABRIR PEDIDOS") {
                activePage = "Pedidos"; renderApp()
            }
        }
        if ("Carta" in accessiblePages) {
            actionCard(container, "03", "Carta e custos", "Artigos, preços e custos do menu.", "ABRIR CARTA") {
                activePage = "Carta"; renderApp()
            }
        }
        if ("Pontos" in accessiblePages) {
            actionCard(container, "04", "Fidelização", "Cartões, recompensas e pedidos de troca.", "GERIR FIDELIZAÇÃO") {
                activePage = "Pontos"; renderApp()
            }
        }
        if ("Reservas" in accessiblePages) {
            actionCard(container, "05", "Reservas", "Pedidos, capacidade e atribuição de mesas.", "GERIR RESERVAS") {
                activePage = "Reservas"; renderApp()
            }
        }
        if ("Preço Justo" in accessiblePages) {
            actionCard(container, "06", "Preço Justo", "Calcula preços indicativos a partir dos custos.", "ABRIR CALCULADORA") {
                activePage = "Preço Justo"; renderApp()
            }
        }
        if ("Stock" in accessiblePages) {
            actionCard(container, "07", "Stock e inventário", "Quantidades, mínimos e movimentos.", "GERIR STOCK") {
                activePage = "Stock"; renderApp()
            }
        }
        if ("Análise" in accessiblePages) {
            actionCard(container, "08", "Análise operacional", "Resumo dos pedidos, artigos e custos.", "VER ANÁLISE") {
                activePage = "Análise"; renderApp()
            }
        }
        if ("Assistente" in accessiblePages) {
            actionCard(container, "IA", "Assistente StudioMesa", "Sugestões de operação e preços.", "ABRIR ASSISTENTE") {
                activePage = "Assistente"; renderApp()
            }
        }

        addGap(container, 5)
        val note = makeCard(14, copperPale)
        note.addView(label("NOTA SOBRE OS DADOS", 9.5f, true, forest))
        addGap(note, 5)
        note.addView(label(
            if (hasRemoteRestaurant())
                "Mesas, carta e pedidos são partilhados com a equipa autenticada. Ainda não existe integração com caixa registadora, pagamentos ou impressora de cozinha."
            else
                "O modo de demonstração guarda os registos neste telemóvel. Não representa dados reais de um restaurante.",
            12f, false, ink
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
                    }) {
                        kitchenOrdersCache = null
                        kitchenQueueUpdatedAtMillis = System.currentTimeMillis()
                        kitchenQueueRefreshFailed = false
                        refreshKitchenPageContent()
                    }
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
                val role = restaurant.optString("user_role", "staff")
                if (previousRestaurantId.isNotBlank() && previousRestaurantId != restaurantId) {
                    // SharedPreferences caches are restaurant-scoped. A single account
                    // joining a different restaurant must never inherit the previous
                    // restaurant's stock, reservations, team, menu or loyalty snapshots.
                    editor.remove("remote_members_json")
                        .remove("remote_tables_json")
                        .remove("remote_menu_json")
                        .remove("remote_orders_json")
                        .remove("remote_inventory_json")
                        .remove("inventory_loaded")
                        .remove("remote_reservations_json")
                        .remove("reservations_loaded")
                        .remove("restaurant_loyalty_rewards_json")
                        .remove("restaurant_loyalty_redemptions_json")
                        .remove("restaurant_loyalty_cache_restaurant_id")
                    reservationCache = JSONArray()
                    reservationsLoaded = false
                    kitchenOrdersCache = null
                    kitchenQueueUpdatedAtMillis = 0L
                }
                editor.putString("restaurant_id", restaurantId)
                    .putString("restaurant_name", restaurant.optString("restaurant_name"))
                    .putString("join_code", restaurant.optString("join_code"))
                    .putString("staff_join_code", restaurant.optString("staff_code"))
                    .putString("restaurant_role", role)
                val cachedRestaurantId = backendPrefs.getString("restaurant_loyalty_cache_restaurant_id", null)
                if (!LoyaltyCacheScope.belongsTo(cachedRestaurantId, restaurantId)) {
                    editor.remove("restaurant_loyalty_rewards_json")
                        .remove("restaurant_loyalty_redemptions_json")
                }
                editor.putString("restaurant_loyalty_cache_restaurant_id", restaurantId)
            }
            editor.apply()
            if (restaurant != null) {
                val role = restaurant.optString("user_role", "staff")
                currentStaffPermissions = if (role == "owner") {
                    StaffPermissionSet.all.toSet()
                } else {
                    // Fail closed for navigation if the server cannot provide the effective matrix.
                    StaffPermissionSet.fromJson(SupabaseApi.getMyRestaurantPermissions(token))
                }
                cacheRemoteOperations(token, restaurant.optString("restaurant_id"))
                clearUnavailableStaffCaches()
            } else {
                currentStaffPermissions = null
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
                    .remove("restaurant_loyalty_rewards_json")
                    .remove("restaurant_loyalty_redemptions_json")
                    .remove("restaurant_loyalty_cache_restaurant_id")
                    .apply()
                reservationCache = JSONArray()
                reservationsLoaded = false
            }
        } catch (_: Exception) {
            // Authentication can remain active during a transient failure, but staff UI fails closed.
            if (backendPrefs.getString("restaurant_role", "") == "staff") {
                currentStaffPermissions = emptySet()
                backendPrefs.edit()
                    .remove("remote_members_json")
                    .remove("remote_tables_json")
                    .remove("remote_menu_json")
                    .remove("remote_orders_json")
                    .remove("remote_inventory_json")
                    .remove("inventory_loaded")
                    .remove("remote_reservations_json")
                    .remove("reservations_loaded")
                    .remove("restaurant_loyalty_rewards_json")
                    .remove("restaurant_loyalty_redemptions_json")
                    .apply()
                reservationCache = JSONArray()
                reservationsLoaded = false
            }
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

    private fun cacheRemoteOperations(
        token: String,
        restaurantId: String,
        permissionsOverride: Set<String>? = null
    ) {
        if (token.isBlank() || restaurantId.isBlank()) return
        val isOwner = backendPrefs.getString("restaurant_role", "") == "owner"
        val permissions = permissionsOverride ?: currentStaffPermissions.orEmpty()
        val editor = backendPrefs.edit()

        if (isOwner || "tables" in permissions) {
            editor.putString("remote_tables_json", SupabaseApi.listTables(token, restaurantId).toString())
        } else {
            editor.remove("remote_tables_json")
        }
        if (isOwner || "menu" in permissions) {
            editor.putString("remote_menu_json", SupabaseApi.listMenuItems(token, restaurantId).toString())
        } else {
            editor.remove("remote_menu_json")
        }
        if (isOwner || "orders" in permissions) {
            editor.putString("remote_orders_json", SupabaseApi.listOrders(token, restaurantId).toString())
        } else {
            editor.remove("remote_orders_json")
        }
        editor.apply()
    }

    private fun clearUnavailableStaffCaches() {
        if (backendPrefs.getString("restaurant_role", "") != "staff") return
        val permissions = currentStaffPermissions.orEmpty()
        val editor = backendPrefs.edit()
        if ("tables" !in permissions) editor.remove("remote_tables_json")
        if ("menu" !in permissions) editor.remove("remote_menu_json")
        if ("orders" !in permissions) editor.remove("remote_orders_json")
        if ("inventory" !in permissions) {
            editor.remove("remote_inventory_json").remove("inventory_loaded")
        }
        if ("reservations" !in permissions) {
            editor.remove("remote_reservations_json").remove("reservations_loaded")
            reservationCache = JSONArray()
            reservationsLoaded = false
        }
        if ("loyalty" !in permissions) {
            editor.remove("remote_members_json")
                .remove("restaurant_loyalty_rewards_json")
                .remove("restaurant_loyalty_redemptions_json")
        }
        editor.apply()
    }

    /**
     * Re-fetch the authoritative matrix when a staff member returns to the app.
     * Revoked areas are removed from navigation and local caches; server-side
     * RPC/RLS checks still reject direct requests immediately.
     */
    private fun refreshStaffPermissionsSilently() {
        if (isCustomerApp || staffPermissionRefreshInFlight ||
            backendPrefs.getString("restaurant_role", "") != "staff") return

        val startingUserId = backendPrefs.getString("user_id", "").orEmpty()
        val startingRestaurantId = backendRestaurantId()
        if (startingUserId.isBlank() || startingRestaurantId.isBlank() || backendToken().isBlank()) return

        // Fail closed before the network round-trip: do not leave a previously open
        // restricted page or its cached data visible while a revocation is checked.
        val hadPermissions = currentStaffPermissions?.isNotEmpty() == true
        currentStaffPermissions = emptySet()
        // Keep the old cache stored but unreachable while the screen is closed behind
        // the home fallback. After the server answers, purge only revoked areas; on
        // network/auth failure the final empty matrix purges every operational cache.
        if (activePage !in availablePages()) activePage = "Início"
        if (hadPermissions) renderApp()

        staffPermissionRefreshInFlight = true
        Thread {
            val permissions = try {
                refreshSessionIfNeeded()
                val token = backendToken()
                val currentUserId = backendPrefs.getString("user_id", "").orEmpty()
                val currentRestaurantId = backendRestaurantId()
                if (token.isBlank() || currentUserId != startingUserId) {
                    runOnUiThread { staffPermissionRefreshInFlight = false }
                    return@Thread
                }
                if (currentRestaurantId != startingRestaurantId ||
                    backendPrefs.getString("restaurant_role", "") != "staff") {
                    // Session refresh may have confirmed that the account was removed
                    // from this restaurant or promoted to owner. Restore the whole view
                    // from the new server-confirmed membership instead of leaving an old page on screen.
                    runOnUiThread {
                        staffPermissionRefreshInFlight = false
                        if (!isFinishing &&
                            backendPrefs.getString("user_id", "").orEmpty() == startingUserId) {
                            activePage = "Início"
                            renderApp()
                        }
                    }
                    return@Thread
                }

                val confirmedMembership = SupabaseApi.getMyRestaurant(token)
                val confirmedMembershipId = confirmedMembership?.optString("restaurant_id").orEmpty()
                val confirmedRole = confirmedMembership?.optString("user_role").orEmpty()
                if (confirmedMembership == null || confirmedMembershipId != startingRestaurantId ||
                    confirmedRole != "staff") {
                    // A removed member receives an empty permission list, which alone
                    // cannot distinguish removal from a deliberately empty matrix.
                    // Reconcile the actual membership first so Account cannot keep
                    // showing a restaurant that no longer recognizes this session.
                    restoreOwnedRestaurant(token)
                    runOnUiThread {
                        staffPermissionRefreshInFlight = false
                        if (!isFinishing &&
                            backendPrefs.getString("user_id", "").orEmpty() == startingUserId) {
                            activePage = "Início"
                            renderApp()
                        }
                    }
                    return@Thread
                }

                val confirmed = StaffPermissionSet.fromJson(
                    SupabaseApi.getMyRestaurantPermissions(token)
                )
                // Refresh newly granted areas as well as the permission matrix. If
                // the data fetch fails, the whole refresh fails closed rather than
                // exposing an empty or stale page as if it were current.
                cacheRemoteOperations(token, currentRestaurantId, confirmed)
                confirmed
            } catch (_: Exception) {
                // A failed authorization or data refresh must not leave an old area exposed.
                emptySet()
            }

            runOnUiThread {
                staffPermissionRefreshInFlight = false
                if (isFinishing) return@runOnUiThread
                if (backendPrefs.getString("user_id", "").orEmpty() != startingUserId ||
                    backendRestaurantId() != startingRestaurantId ||
                    backendPrefs.getString("restaurant_role", "") != "staff") return@runOnUiThread

                currentStaffPermissions = permissions
                clearUnavailableStaffCaches()
                if (activePage !in availablePages()) activePage = "Início"
                renderApp()
            }
        }.start()
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
                backendPrefs.edit()
                    .remove("member_id")
                    .remove("member_code")
                    .remove("member_points")
                    .remove("member_restaurant_name")
                    .putString("customer_menu_json", "[]")
                    .remove("customer_loyalty_rewards_json")
                    .remove("customer_loyalty_redemptions_json")
                    .remove("customer_loyalty_cache_member_id")
                    .apply()
                return
            }

            val memberId = member.optString("member_id")
            if (memberId.isBlank()) throw IllegalStateException("O servidor não devolveu um cartão de cliente válido.")
            val menu = SupabaseApi.listCustomerMenu(token, memberId)
            val cachedMemberId = backendPrefs.getString("customer_loyalty_cache_member_id", null)
            val editor = backendPrefs.edit()
            if (!LoyaltyCacheScope.belongsTo(cachedMemberId, memberId)) {
                editor.remove("customer_loyalty_rewards_json")
                    .remove("customer_loyalty_redemptions_json")
            }
            editor.putString("member_id", memberId)
                .putString("member_code", member.optString("member_code"))
                .putInt("member_points", member.optInt("points", 0))
                .putString("member_restaurant_name", member.optString("restaurant_name"))
                .putString("customer_menu_json", menu.toString())
                .putString("customer_loyalty_cache_member_id", memberId)
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
            .remove("customer_loyalty_rewards_json").remove("customer_loyalty_redemptions_json")
            .remove("customer_loyalty_cache_member_id")
            .remove("restaurant_loyalty_rewards_json").remove("restaurant_loyalty_redemptions_json")
            .remove("restaurant_loyalty_cache_restaurant_id")
            .remove("remote_inventory_json").remove("inventory_loaded")
            .remove("remote_reservations_json").remove("reservations_loaded")
            .apply()

        currentStaffPermissions = null
        reservationCache = JSONArray()
        reservationsLoaded = false
        customerReservationsCache = JSONArray()
        customerReservationsLoaded = false
        customerReservationsAccountScope = ""
        customerReservationsMemberScope = ""
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
                    card.addView(secondaryButton("Gerir permissões") {
                        showStaffPermissionsDialog(userId, email)
                    }, LinearLayout.LayoutParams(-1, dp(44)))
                    addGap(card, 6)
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

    private fun showStaffPermissionsDialog(userId: String, email: String) {
        if (backendToken().isBlank() ||
            backendPrefs.getString("restaurant_role", "").orEmpty() != "owner") {
            Toast.makeText(this, "Só o proprietário pode gerir permissões.", Toast.LENGTH_LONG).show()
            return
        }

        runBackend({
            SupabaseApi.getRestaurantStaffPermissions(backendToken(), userId).toString()
        }) { raw ->
            val current = StaffPermissionSet.fromJson(JSONArray(raw))
            val options = listOf(
                "orders" to "Pedidos e cozinha",
                "tables" to "Mesas",
                "reservations" to "Reservas",
                "menu" to "Carta",
                "inventory" to "Stock e inventário",
                "loyalty" to "Fidelização operacional"
            )
            val checks = linkedMapOf<String, android.widget.CheckBox>()
            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(8), dp(18), dp(8))
            }
            content.addView(label(
                "Escolhe as áreas operacionais a que $email pode aceder. O servidor confirma todas as alterações.",
                12f, false, muted
            ))
            addGap(content, 8)
            for ((key, title) in options) {
                val check = android.widget.CheckBox(this).apply {
                    text = title
                    textSize = 14f
                    setTextColor(ink)
                    isChecked = key in current
                    minHeight = dp(48)
                    contentDescription = "Permissão: $title"
                }
                checks[key] = check
                content.addView(check, LinearLayout.LayoutParams(-1, dp(48)))
            }
            val warning = makeCard(10, copperPale)
            warning.addView(label(
                "Gerir a equipa e códigos, alterar recompensas, consultar ferramentas económicas e eliminar o restaurante continuam a ser ações exclusivas do proprietário.",
                11.5f, false, ink
            ))
            addGap(content, 8)
            content.addView(warning, cardParams())

            val scroll = ScrollView(this).apply { addView(content) }
            val dialog = AlertDialog.Builder(this)
                .setTitle("Permissões do funcionário")
                .setView(scroll)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val selected = checks.filterValues { it.isChecked }.keys.toSet()
                    runBackend({
                        SupabaseApi.setRestaurantStaffPermissions(backendToken(), userId, selected)
                        val confirmed = StaffPermissionSet.fromJson(
                            SupabaseApi.getRestaurantStaffPermissions(backendToken(), userId)
                        )
                        if (confirmed != selected) {
                            throw IllegalStateException("O servidor não confirmou todas as permissões selecionadas.")
                        }
                        "OK"
                    }) {
                        dialog.dismiss()
                        Toast.makeText(this, "Permissões atualizadas no servidor.", Toast.LENGTH_LONG).show()
                        renderApp()
                    }
                }
            }
            dialog.show()
            dialog.window?.setLayout(-1, dp(620))
        }
    }

    private fun showStaffJoinCode() {
        val role = backendPrefs.getString("restaurant_role", "") ?: ""
        if (!StaffPermissionSet.canManageRestaurantCodes(role)) {
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
        if (!StaffPermissionSet.canManageRestaurantCodes(backendPrefs.getString("restaurant_role", ""))) {
            AlertDialog.Builder(this)
                .setTitle("Acesso reservado")
                .setMessage("Só o proprietário pode renovar códigos de adesão.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
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
            val canAccessLoyalty = role == "owner" || "loyalty" in currentStaffPermissions.orEmpty()
            if (canAccessLoyalty) {
                addGap(card, 12)
                card.addView(primaryButton("Atualizar lista de clientes") { refreshRemoteCustomers() },
                    LinearLayout.LayoutParams(-1, dp(48)))
            }
        }
        container.addView(card, cardParams())
        addGap(container, 3)
        if (token.isNotBlank()) {
            container.addView(secondaryButton("Eliminar conta permanentemente") {
                showDeleteAccountConfirmation()
            }, LinearLayout.LayoutParams(-1, dp(48)))
            addGap(container, 7)
        }
        container.addView(secondaryButton("Terminar sessão neste dispositivo") {
            clearEconomicManagerState()
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
                .remove("customer_loyalty_rewards_json").remove("customer_loyalty_redemptions_json")
                .remove("customer_loyalty_cache_member_id")
                .remove("restaurant_loyalty_rewards_json").remove("restaurant_loyalty_redemptions_json")
                .remove("restaurant_loyalty_cache_restaurant_id")
                .remove("remote_inventory_json").remove("inventory_loaded")
                .remove("remote_reservations_json").remove("reservations_loaded")
                .apply()
            currentStaffPermissions = null
            staffPermissionRefreshInFlight = false
            kitchenOrdersCache = null
            kitchenQueueUpdatedAtMillis = 0L
            kitchenQueueRefreshFailed = false
            reservationCache = JSONArray()
            reservationsLoaded = false
            customerReservationsCache = JSONArray()
            customerReservationsLoaded = false
            customerReservationsAccountScope = ""
            customerReservationsMemberScope = ""
            customerReservationsRefreshInFlight = false
            renderApp()
        }, LinearLayout.LayoutParams(-1, dp(48)))
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
        if (!StaffPermissionSet.canManageRestaurantCodes(backendPrefs.getString("restaurant_role", ""))) {
            AlertDialog.Builder(this)
                .setTitle("Acesso reservado")
                .setMessage("Só o proprietário pode consultar o código de adesão dos clientes.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val code = backendPrefs.getString("join_code", "")?.trim().orEmpty()
        if (code.isBlank()) {
            AlertDialog.Builder(this)
                .setTitle("Código indisponível")
                .setMessage("Não foi possível obter o código de adesão. Atualiza a conta do restaurante e tenta novamente.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Código de adesão")
            .setMessage("Código: $code\n\nO cliente deve abrir a aplicação «StudioMesa - Cliente», criar conta ou iniciar sessão e escolher «Associar ao restaurante».")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun renderCustomers(container: LinearLayout) {
        pageIntro(container, "Fidelização por pontos", "Gere cartões de clientes, pontos e recompensas.")
        addGap(container, 9)

        val remoteMode = backendToken().isNotBlank() && backendRestaurantId().isNotBlank()
        val notice = makeCard(14, copperPale)
        notice.addView(label(if (remoteMode) "FIDELIZAÇÃO PARTILHADA" else "REGRAS A DEFINIR COM O RESTAURANTE", 10f, true, forest))
        addGap(notice, 5)
        notice.addView(label(if (remoteMode) "Os pontos e as recompensas são partilhados com os clientes através do servidor. O proprietário gere o catálogo e a equipa confirma as entregas." else "No modo local, os dados ficam apenas neste telemóvel. Liga uma conta e configura o restaurante na secção Conta para ativar a fidelização partilhada.", 12.5f, false, ink))
        container.addView(notice, cardParams())
        if (remoteMode) {
            container.addView(primaryButton("Atualizar clientes do servidor") { refreshRemoteCustomers() }, LinearLayout.LayoutParams(-1, dp(48)))
            if (StaffPermissionSet.canManageRestaurantCodes(backendPrefs.getString("restaurant_role", ""))) {
                addGap(container, 6)
                container.addView(secondaryButton("Ver código de adesão") { showRestaurantJoinCode() }, LinearLayout.LayoutParams(-1, dp(48)))
            }
        } else {
            container.addView(primaryButton("+  Adicionar cliente (local)") { showAddCustomerDialog() }, LinearLayout.LayoutParams(-1, dp(48)))
        }
        if (remoteMode) {
            addGap(container, 13)
            renderRestaurantLoyaltyShop(container)
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
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(0, 0, dp(5), 0) })
            actions.addView(secondaryButton("− Descontar") {
                showCustomerPointsDialog(customer, true)
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(5), 0, 0, 0) })
            card.addView(actions)
            if (remoteMode && customer.backendId != null) {
                addGap(card, 8)
                card.addView(secondaryButton("Emitir QR da fatura") {
                    showIssueInvoiceLoyaltyQrDialog(customer)
                }, LinearLayout.LayoutParams(-1, dp(44)))
            }
            container.addView(card, cardParams())
        }
    }


    private fun cachedRestaurantRewards(): List<LoyaltyReward> {
        val cachedRestaurantId = backendPrefs.getString("restaurant_loyalty_cache_restaurant_id", null)
        if (!LoyaltyCacheScope.belongsTo(cachedRestaurantId, backendRestaurantId())) return emptyList()
        val raw = backendPrefs.getString("restaurant_loyalty_rewards_json", "[]") ?: "[]"
        return runCatching {
            val rows = JSONArray(raw)
            (0 until rows.length()).map { LoyaltyReward.fromJson(rows.getJSONObject(it)) }
        }.getOrElse { emptyList() }
    }

    private fun cachedRestaurantRewardRedemptions(): JSONArray {
        val cachedRestaurantId = backendPrefs.getString("restaurant_loyalty_cache_restaurant_id", null)
        if (!LoyaltyCacheScope.belongsTo(cachedRestaurantId, backendRestaurantId())) return JSONArray()
        return runCatching {
            JSONArray(backendPrefs.getString("restaurant_loyalty_redemptions_json", "[]") ?: "[]")
        }.getOrElse { JSONArray() }
    }

    private fun renderRestaurantLoyaltyShop(container: LinearLayout) {
        val isOwner = backendPrefs.getString("restaurant_role", "").orEmpty() == "owner"
        val heading = makeCard(15, paleGreen)
        heading.addView(label("LOJA DE RECOMPENSAS", 10f, true, forest))
        addGap(heading, 4)
        heading.addView(label(
            if (isOwner) "Cria prémios próprios, define quantos pontos custam e controla a disponibilidade."
            else "Consulta as recompensas do restaurante e acompanha os pedidos de entrega.",
            12f, false, ink
        ))
        container.addView(heading, cardParams())

        if (isOwner) {
            container.addView(primaryButton("+ Adicionar recompensa") {
                showLoyaltyRewardEditor(null)
            }, LinearLayout.LayoutParams(-1, dp(46)))
        }
        addGap(container, 5)
        container.addView(secondaryButton("Atualizar loja e pedidos") {
            refreshRestaurantLoyaltyShop()
        }, LinearLayout.LayoutParams(-1, dp(44)))

        val rewards = cachedRestaurantRewards()
        if (rewards.isEmpty()) {
            container.addView(emptyCard(
                if (isOwner) "Ainda não existem recompensas. Cria o primeiro prémio para os teus clientes."
                else "Ainda não há recompensas registadas neste restaurante."
            ))
        } else {
            rewards.forEach { reward ->
                val card = makeCard(13)
                card.addView(label(reward.name, 15f, true, forestDeep))
                if (reward.description.isNotBlank()) {
                    addGap(card, 4)
                    card.addView(label(reward.description, 12f, false, ink))
                }
                addGap(card, 7)
                val statusRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                statusRow.addView(
                    label(reward.pointsCost.toString() + " pontos", 13f, true, forest),
                    LinearLayout.LayoutParams(0, -2, 1f)
                )
                val remaining = reward.availableStock()
                val stockText = when {
                    !reward.isActive -> "Pausada"
                    remaining == null -> "Stock ilimitado"
                    remaining == 0 -> "Esgotada"
                    else -> remaining.toString() + " disponíveis"
                }
                statusRow.addView(label(stockText, 10.5f, true, if (reward.isActive) forestSoft else muted))
                card.addView(statusRow)
                if (isOwner) {
                    addGap(card, 8)
                    card.addView(secondaryButton("Editar recompensa") {
                        showLoyaltyRewardEditor(reward)
                    }, LinearLayout.LayoutParams(-1, dp(42)))
                }
                container.addView(card, cardParams())
            }
        }

        addGap(container, 10)
        container.addView(label("PEDIDOS DE RECOMPENSAS", 10f, true, forestSoft))
        addGap(container, 5)
        val redemptions = cachedRestaurantRewardRedemptions()
        if (redemptions.length() == 0) {
            container.addView(emptyCard("Ainda não existem pedidos de recompensa."))
        } else {
            for (index in 0 until redemptions.length()) {
                val row = redemptions.optJSONObject(index) ?: continue
                val card = makeCard(12)
                card.addView(label(row.optString("reward_name", "Recompensa"), 14f, true, forestDeep))
                addGap(card, 3)
                card.addView(label("Cliente: " + row.optString("customer_name", "Cliente"), 11.5f, false, muted))
                val fulfilled = row.optString("status") == "fulfilled"
                card.addView(label(
                    row.optInt("points_spent", 0).toString() + " pontos · " +
                        if (fulfilled) "Entregue" else "Pendente",
                    11.5f, true, if (fulfilled) green else forest
                ))
                if (!fulfilled && row.optString("redemption_id").isNotBlank()) {
                    addGap(card, 7)
                    card.addView(secondaryButton("Confirmar entrega") {
                        confirmLoyaltyRewardDelivery(row.optString("redemption_id"))
                    }, LinearLayout.LayoutParams(-1, dp(42)))
                }
                container.addView(card, cardParams())
            }
        }
    }

    private fun refreshRestaurantLoyaltyShop() {
        val restaurantId = backendRestaurantId()
        val accountId = backendPrefs.getString("user_id", "").orEmpty()
        if (backendToken().isBlank() || restaurantId.isBlank() || accountId.isBlank()) {
            AlertDialog.Builder(this)
                .setMessage("Inicia sessão e configura primeiro o restaurante.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        runBackend({
            val activeRestaurantId = backendRestaurantId()
            val activeAccountId = backendPrefs.getString("user_id", "").orEmpty()
            if (!LoyaltySessionScope.matches(accountId, activeAccountId, restaurantId, activeRestaurantId)) {
                throw IllegalStateException("A conta ou o restaurante ativo mudou. Atualiza novamente a loja.")
            }
            val token = backendToken()
            val rewards = SupabaseApi.listRestaurantLoyaltyRewards(token, activeRestaurantId)
            val redemptions = SupabaseApi.listRestaurantLoyaltyRewardRedemptions(token, activeRestaurantId)
            if (!LoyaltySessionScope.matches(
                accountId,
                backendPrefs.getString("user_id", "").orEmpty(),
                restaurantId,
                backendRestaurantId()
            )) {
                throw IllegalStateException("A conta ou o restaurante mudou durante a atualização.")
            }
            backendPrefs.edit()
                .putString("restaurant_loyalty_rewards_json", rewards.toString())
                .putString("restaurant_loyalty_redemptions_json", redemptions.toString())
                .putString("restaurant_loyalty_cache_restaurant_id", activeRestaurantId)
                .apply()
            rewards.length().toString()
        }) { count ->
            if (activePage == "Pontos") renderApp()
            Toast.makeText(this, count + " recompensas carregadas.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLoyaltyRewardEditor(existing: LoyaltyReward?) {
        if (backendPrefs.getString("restaurant_role", "").orEmpty() != "owner") {
            AlertDialog.Builder(this)
                .setMessage("Só o proprietário pode criar ou editar recompensas.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val nameField = inputField(
            "Nome do prémio",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        )
        val descriptionField = inputField(
            "Descrição",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        )
        val pointsField = inputField("Pontos necessários", InputType.TYPE_CLASS_NUMBER)
        val stockField = inputField("Stock (vazio = ilimitado)", InputType.TYPE_CLASS_NUMBER)
        existing?.let {
            nameField.setText(it.name)
            descriptionField.setText(it.description)
            pointsField.setText(it.pointsCost.toString())
            stockField.setText(it.stockLimit?.toString().orEmpty())
        }
        val rewardIdForDialog = existing?.id ?: java.util.UUID.randomUUID().toString()
        val activeCheck = android.widget.CheckBox(this).apply {
            text = "Disponível para os clientes"
            isChecked = existing?.isActive ?: true
            setTextColor(ink)
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        form.addView(label("NOME", 10f, true, forestSoft))
        form.addView(nameField, fieldParams())
        addGap(form, 4)
        form.addView(label("DESCRIÇÃO", 10f, true, forestSoft))
        form.addView(descriptionField, fieldParams())
        addGap(form, 4)
        form.addView(label("CUSTO EM PONTOS", 10f, true, forestSoft))
        form.addView(pointsField, fieldParams())
        addGap(form, 4)
        form.addView(label("LIMITE DE STOCK", 10f, true, forestSoft))
        form.addView(stockField, fieldParams())
        form.addView(activeCheck)

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Nova recompensa" else "Editar recompensa")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameField.text.toString().trim()
                val description = descriptionField.text.toString().trim()
                val points = pointsField.text.toString().trim().toIntOrNull()
                val rawStock = stockField.text.toString().trim()
                val stock = if (rawStock.isBlank()) null else rawStock.toIntOrNull()
                when {
                    name.isBlank() || name.length > 100 ->
                        nameField.error = "Introduz um nome entre 1 e 100 caracteres"
                    description.length > 500 ->
                        descriptionField.error = "A descrição pode ter no máximo 500 caracteres"
                    points == null || points !in 1..1_000_000 ->
                        pointsField.error = "Introduz entre 1 e 1 000 000 pontos"
                    rawStock.isNotBlank() && (stock == null || stock < 0) ->
                        stockField.error = "O stock deve ser zero ou um número positivo"
                    else -> {
                        val rewardId = rewardIdForDialog
                        runBackend({
                            SupabaseApi.saveLoyaltyReward(
                                backendToken(),
                                backendRestaurantId(),
                                rewardId,
                                name,
                                description,
                                points,
                                stock,
                                activeCheck.isChecked
                            )
                            "OK"
                        }) {
                            dialog.dismiss()
                            refreshRestaurantLoyaltyShop()
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun confirmLoyaltyRewardDelivery(redemptionId: String) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar entrega?")
            .setMessage("O pedido será marcado como entregue. Os pontos já foram descontados quando o cliente pediu a recompensa.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Confirmar entrega") { _, _ ->
                runBackend({
                    SupabaseApi.fulfillLoyaltyRewardRedemption(backendToken(), redemptionId)
                    "OK"
                }) {
                    refreshRestaurantLoyaltyShop()
                }
            }
            .show()
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
        if (activeCustomerPage !in setOf("Cartão", "Prémios", "Reservas", "Carta", "Conta")) activeCustomerPage = "Cartão"
        appRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(paper)
        }
        applySystemBarInsets(appRoot)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(17), dp(18), dp(17))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(forestDeep, forest, forestSoft)
            ).apply { cornerRadius = dp(24).toFloat() }
        }
        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        brandRow.addView(TextView(this).apply {
            text = "SM"
            textSize = 15f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(forestDeep)
            background = rounded(copper, 14)
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, 0, 0)
        }
        brandText.addView(label(displayCustomerRestaurantName(), 20f, true, white).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        brandText.addView(label("STUDIOMESA  /  CLIENTE", 9f, true, copperPale).apply {
            letterSpacing = 0.09f
        })
        brandRow.addView(brandText, LinearLayout.LayoutParams(0, -2, 1f))
        brandRow.addView(label(planTitle, 9f, true, forestDeep).apply {
            setPadding(dp(9), dp(7), dp(9), dp(7))
            background = rounded(copper, 10)
        })
        header.addView(brandRow)
        addGap(header, 15)
        header.addView(View(this).apply { setBackgroundColor(Color.argb(70, 255, 255, 255)) },
            LinearLayout.LayoutParams(-1, dp(1)))
        addGap(header, 11)
        header.addView(label("A tua experiência, num só lugar.", 17f, true, white))
        addGap(header, 3)
        header.addView(label(
            if (backendMemberId().isBlank()) "Cartão digital de cliente · associa-te ao restaurante para acompanhar os pontos."
            else "Cartão de fidelização e serviços do restaurante.",
            12f, false, Color.rgb(229, 235, 228)
        ))
        appRoot.addView(header, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(12), dp(8), dp(12), dp(5))
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(20))
        }
        when (activeCustomerPage) {
            "Cartão" -> renderCustomerLoyaltyPage(content)
            "Prémios" -> renderCustomerRewardsPage(content)
            "Reservas" -> renderCustomerReservationsPage(content)
            "Carta" -> renderCustomerMenuPage(content)
            "Conta" -> renderCustomerAccountPage(content)
        }
        scroll.addView(content)
        appRoot.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = cardSurface(white, 19)
            elevation = dp(7).toFloat()
        }
        listOf("Cartão", "Prémios", "Reservas", "Carta", "Conta").forEach { page ->
            val selected = activeCustomerPage == page
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(4), dp(3), dp(4))
                background = rounded(if (selected) copperPale else white, 12)
                contentDescription = "Abrir secção " + page
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    activeCustomerPage = page
                    renderCustomerApp()
                    if (page == "Prémios" && backendToken().isNotBlank() && backendMemberId().isNotBlank() &&
                        LoyaltyCacheScope.belongsTo(
                            backendPrefs.getString("customer_loyalty_cache_member_id", null),
                            backendMemberId()
                        ) &&
                        backendPrefs.contains("customer_loyalty_rewards_json") &&
                        backendPrefs.contains("customer_loyalty_redemptions_json")) {
                        refreshCustomerRewards()
                    }
                    if (page == "Reservas" && backendToken().isNotBlank() && backendMemberId().isNotBlank()) {
                        refreshCustomerReservations()
                    }
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
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        appRoot.addView(nav, LinearLayout.LayoutParams(-1, dp(66)).apply {
            setMargins(dp(12), dp(3), dp(12), dp(8))
        })
        setContentView(appRoot)
        if (activeCustomerPage == "Reservas" &&
            backendToken().isNotBlank() &&
            backendMemberId().isNotBlank() &&
            !customerReservationsLoaded &&
            customerReservationsError.isBlank()) {
            refreshCustomerReservations()
        }
        if (activeCustomerPage == "Prémios" &&
            backendToken().isNotBlank() &&
            backendMemberId().isNotBlank() &&
            (!LoyaltyCacheScope.belongsTo(
                backendPrefs.getString("customer_loyalty_cache_member_id", null),
                backendMemberId()
            ) ||
                !backendPrefs.contains("customer_loyalty_rewards_json") ||
                !backendPrefs.contains("customer_loyalty_redemptions_json"))) {
            refreshCustomerRewards()
        }
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
            else -> {
                container.addView(primaryButton("Ler QR da fatura") { startInvoiceLoyaltyQrScan() }, LinearLayout.LayoutParams(-1, dp(48)))
                addGap(container, 6)
                container.addView(secondaryButton("Atualizar saldo no servidor") { refreshCustomerMembership() }, LinearLayout.LayoutParams(-1, dp(46)))
            }
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
        addGap(loyalty, 9)
        loyalty.addView(label(if (backendMemberId().isBlank()) "Este é um cartão demonstrativo. Cria sessão e associa-o ao código do restaurante para consultar os pontos reais." else "Saldo obtido do sistema de fidelização partilhado. A atribuição de pontos é controlada pelo restaurante.", 12f, false, Color.rgb(229, 235, 228)))
        container.addView(loyalty, cardParams())
        addGap(container, 5)
        container.addView(secondaryButton("Consultar a carta") {
            activeCustomerPage = "Carta"
            renderCustomerApp()
        }, LinearLayout.LayoutParams(-1, dp(48)))
    }


    private fun customerReservationScopeMatches(): Boolean =
        LoyaltySessionScope.matches(
            customerReservationsAccountScope,
            backendPrefs.getString("user_id", "").orEmpty(),
            customerReservationsMemberScope,
            backendMemberId()
        )

    private fun formatCustomerReservationDate(raw: String): String {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.ROOT).parse(raw)
            }.getOrNull()
            if (parsed != null) {
                return SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "PT")).format(parsed)
            }
        }
        return raw.replace("T", " ").take(19)
    }

    private fun refreshCustomerReservations() {
        val token = backendToken()
        val accountId = backendPrefs.getString("user_id", "").orEmpty()
        val memberId = backendMemberId()
        if (token.isBlank() || accountId.isBlank()) {
            showAuthChooser { renderCustomerApp() }
            return
        }
        if (memberId.isBlank()) {
            showCustomerJoinDialog()
            return
        }
        if (customerReservationsRefreshInFlight) return
        customerReservationsRefreshInFlight = true
        customerReservationsError = ""

        runBackend({
            if (!LoyaltySessionScope.matches(
                    accountId,
                    backendPrefs.getString("user_id", "").orEmpty(),
                    memberId,
                    backendMemberId()
                )) {
                throw IllegalStateException("A conta ou o cartão mudou. Inicia a atualização novamente.")
            }
            val rows = SupabaseApi.listMyCustomerReservations(backendToken())
            if (!LoyaltySessionScope.matches(
                    accountId,
                    backendPrefs.getString("user_id", "").orEmpty(),
                    memberId,
                    backendMemberId()
                )) {
                throw IllegalStateException("A conta ou o cartão mudou durante a atualização das reservas.")
            }
            customerReservationsCache = rows
            customerReservationsAccountScope = accountId
            customerReservationsMemberScope = memberId
            customerReservationsLoaded = true
            customerReservationsError = ""
            "OK"
        }, onError = { error ->
            customerReservationsRefreshInFlight = false
            customerReservationsError = friendlyBackendError(error.message)
            if (activeCustomerPage == "Reservas") renderCustomerApp()
            AlertDialog.Builder(this)
                .setTitle("Não foi possível atualizar")
                .setMessage(customerReservationsError)
                .setPositiveButton("OK", null)
                .show()
        }) {
            customerReservationsRefreshInFlight = false
            if (activeCustomerPage == "Reservas" && customerReservationScopeMatches()) {
                renderCustomerApp()
            }
            Toast.makeText(this, "Reservas atualizadas.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderCustomerReservationsPage(container: LinearLayout) {
        container.addView(label("As minhas reservas", 25f, true, forestDeep))
        addGap(container, 4)
        container.addView(label(
            "Pede uma mesa e acompanha a resposta do restaurante.",
            13f, false, muted
        ))
        addGap(container, 11)

        when {
            backendToken().isBlank() -> {
                container.addView(primaryButton("Criar conta / iniciar sessão") {
                    showAuthChooser { renderCustomerApp() }
                }, LinearLayout.LayoutParams(-1, dp(48)))
                return
            }
            backendMemberId().isBlank() -> {
                container.addView(primaryButton("Associar ao restaurante") {
                    showCustomerJoinDialog()
                }, LinearLayout.LayoutParams(-1, dp(48)))
                return
            }
        }

        container.addView(primaryButton("+  Pedir uma reserva") {
            showCustomerReservationRequestDialog()
        }, LinearLayout.LayoutParams(-1, dp(48)))
        addGap(container, 6)
        container.addView(secondaryButton("Atualizar reservas") {
            refreshCustomerReservations()
        }, LinearLayout.LayoutParams(-1, dp(44)))
        addGap(container, 10)

        if (!customerReservationScopeMatches()) {
            customerReservationsCache = JSONArray()
            customerReservationsLoaded = false
        }
        if (!customerReservationsLoaded) {
            container.addView(emptyCard(
                if (customerReservationsRefreshInFlight) "A consultar as tuas reservas…"
                else customerReservationsError.ifBlank { "Ainda não consultaste as tuas reservas." }
            ))
            return
        }

        if (customerReservationsCache.length() == 0) {
            container.addView(emptyCard("Ainda não tens reservas. Quando pedires uma, aparece aqui o estado do pedido."))
            return
        }

        for (index in 0 until customerReservationsCache.length()) {
            val row = customerReservationsCache.optJSONObject(index) ?: continue
            val reservationId = row.optString("reservation_id")
            val status = row.optString("status", "Pendente")
            val card = makeCard(14)
            card.addView(label(formatCustomerReservationDate(row.optString("reservation_at")), 15f, true, forestDeep))
            addGap(card, 5)
            card.addView(label(
                row.optInt("party_size", 0).toString() +
                    if (row.optInt("party_size", 0) == 1) " pessoa" else " pessoas",
                12f, false, ink
            ))
            val tableNumber = if (row.isNull("table_number")) 0 else row.optInt("table_number", 0)
            if (tableNumber > 0) {
                card.addView(label("Mesa atribuída: $tableNumber", 11.5f, true, forestSoft))
            }
            addGap(card, 5)
            val statusColor = when (status) {
                "Confirmada" -> green
                "Cancelada", "Não compareceu" -> red
                "Concluída" -> forestSoft
                else -> copper
            }
            card.addView(label("Estado: $status", 12f, true, statusColor))
            val notes = row.optString("notes").trim()
            if (notes.isNotBlank()) {
                addGap(card, 4)
                card.addView(label("Nota: $notes", 11f, false, muted))
            }
            if (status in setOf("Pendente", "Confirmada") && reservationId.isNotBlank()) {
                addGap(card, 8)
                card.addView(secondaryButton("Cancelar pedido") {
                    AlertDialog.Builder(this)
                        .setTitle("Cancelar reserva?")
                        .setMessage("A reserva será marcada como cancelada. O restaurante deixará de a tratar como ativa.")
                        .setNegativeButton("Manter reserva", null)
                        .setPositiveButton("Cancelar reserva") { _, _ ->
                            cancelCustomerReservation(reservationId)
                        }
                        .show()
                }, LinearLayout.LayoutParams(-1, dp(42)))
            }
            container.addView(card, cardParams())
        }
    }

    private fun showCustomerReservationRequestDialog() {
        val accountId = backendPrefs.getString("user_id", "").orEmpty()
        val memberId = backendMemberId()
        val sessionToken = backendToken()
        if (accountId.isBlank() || memberId.isBlank() || sessionToken.isBlank()) {
            AlertDialog.Builder(this)
                .setMessage("Inicia sessão e associa o teu cartão ao restaurante antes de pedir uma reserva.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(4))
        }
        form.addView(label("Nome", 10f, true, forestSoft))
        val nameField = inputField("Nome para a reserva", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        nameField.setText(store.getCustomerProfileName())
        form.addView(nameField, fieldParams())
        addGap(form, 7)
        form.addView(label("Telefone (opcional)", 10f, true, forestSoft))
        val phoneField = inputField("Contacto telefónico", InputType.TYPE_CLASS_PHONE)
        form.addView(phoneField, fieldParams())
        addGap(form, 7)
        form.addView(label("Número de pessoas", 10f, true, forestSoft))
        val partySpinner = Spinner(this)
        partySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            (1..12).map { if (it == 1) "1 pessoa" else "$it pessoas" }
        )
        partySpinner.setSelection(1)
        form.addView(partySpinner, LinearLayout.LayoutParams(-1, dp(48)))
        addGap(form, 7)

        fun formatDate(): String = SimpleDateFormat("EEEE, d 'de' MMMM yyyy", Locale("pt", "PT"))
            .format(calendar.time)
        fun formatTime(): String = SimpleDateFormat("HH:mm", Locale.ROOT).format(calendar.time)

        val dateButton = secondaryButton(
            formatDate().replaceFirstChar { it.uppercase(Locale("pt", "PT")) }
        ) { }
        dateButton.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    calendar.set(Calendar.YEAR, year)
                    calendar.set(Calendar.MONTH, month)
                    calendar.set(Calendar.DAY_OF_MONTH, day)
                    dateButtonLabel(dateButton, formatDate())
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        form.addView(label("Data", 10f, true, forestSoft))
        form.addView(dateButton, LinearLayout.LayoutParams(-1, dp(44)))
        addGap(form, 6)
        val timeButton = secondaryButton("Hora: " + formatTime()) { }
        timeButton.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                    calendar.set(Calendar.MINUTE, minute)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    timeButton.text = "Hora: " + formatTime()
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        }
        form.addView(label("Hora", 10f, true, forestSoft))
        form.addView(timeButton, LinearLayout.LayoutParams(-1, dp(44)))
        addGap(form, 7)
        form.addView(label("Notas para o restaurante (opcional)", 10f, true, forestSoft))
        val notesField = inputField(
            "Ex.: cadeira de bebé, ocasião especial…",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        )
        notesField.minLines = 2
        notesField.maxLines = 3
        form.addView(notesField, fieldParams())

        val dialog = AlertDialog.Builder(this)
            .setTitle("Pedir uma reserva")
            .setView(form)
            .setNegativeButton("Fechar", null)
            .setPositiveButton("Enviar pedido", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val guestName = nameField.text.toString().trim()
                val guestPhone = phoneField.text.toString().trim()
                val notes = notesField.text.toString().trim()
                val partySize = partySpinner.selectedItemPosition + 1
                val reservationAt = SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ssXXX",
                    Locale.ROOT
                ).format(calendar.time)
                when {
                    guestName.isBlank() || guestName.length > 100 -> nameField.error = "Indica um nome válido"
                    guestPhone.length > 40 -> phoneField.error = "O telefone é demasiado longo"
                    notes.length > 1000 -> notesField.error = "As notas são demasiado longas"
                    calendar.timeInMillis <= System.currentTimeMillis() -> {
                        AlertDialog.Builder(this)
                            .setMessage("Escolhe uma data e hora futuras.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    else -> {
                        val signature = JSONObject()
                            .put("member_id", memberId)
                            .put("guest_name", guestName)
                            .put("guest_phone", guestPhone)
                            .put("party_size", partySize)
                            .put("reservation_at", reservationAt)
                            .put("duration_minutes", 90)
                            .put("notes", notes)
                            .toString()
                        val operation = "customer_reservation"
                        val requestId = try {
                            pendingRequestIds.getOrCreate(operation, accountId, signature)
                        } catch (error: Exception) {
                            AlertDialog.Builder(this)
                                .setMessage(error.message ?: "Não foi possível preparar o pedido.")
                                .setPositiveButton("OK", null)
                                .show()
                            return@setOnClickListener
                        }

                        runBackend({
                            if (!LoyaltySessionScope.matches(
                                    accountId,
                                    backendPrefs.getString("user_id", "").orEmpty(),
                                    memberId,
                                    backendMemberId()
                                )) {
                                throw IllegalStateException("A conta ou o cartão mudou. Inicia novamente o pedido.")
                            }
                            val result = SupabaseApi.createCustomerReservationRequest(
                                backendToken(), requestId, guestName, guestPhone,
                                partySize, reservationAt, 90, notes
                            )
                            if (!LoyaltySessionScope.matches(
                                    accountId,
                                    backendPrefs.getString("user_id", "").orEmpty(),
                                    memberId,
                                    backendMemberId()
                                )) {
                                throw IllegalStateException("O pedido foi processado, mas a conta mudou. Atualiza as reservas da conta correta.")
                            }
                            pendingRequestIds.complete(operation, accountId, signature, requestId)
                            customerReservationsLoaded = false
                            result.optString("status", "Pendente")
                        }) {
                            dialog.dismiss()
                            Toast.makeText(this, "Pedido de reserva enviado.", Toast.LENGTH_LONG).show()
                            activeCustomerPage = "Reservas"
                            refreshCustomerReservations()
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun dateButtonLabel(button: Button, rawDate: String) {
        button.text = rawDate.replaceFirstChar { it.uppercase(Locale("pt", "PT")) }
    }

    private fun cancelCustomerReservation(reservationId: String) {
        val accountId = backendPrefs.getString("user_id", "").orEmpty()
        val memberId = backendMemberId()
        if (accountId.isBlank() || memberId.isBlank() || backendToken().isBlank()) return
        runBackend({
            if (!LoyaltySessionScope.matches(
                    accountId,
                    backendPrefs.getString("user_id", "").orEmpty(),
                    memberId,
                    backendMemberId()
                )) {
                throw IllegalStateException("A conta ou o cartão mudou. Atualiza novamente.")
            }
            // runBackend refreshes an expiring session before this task, so read the
            // current token only after verifying that it still belongs to this account.
            val requestToken = backendToken()
            if (requestToken.isBlank()) {
                throw IllegalStateException("A sessão expirou. Inicia novamente para cancelar a reserva.")
            }
            SupabaseApi.cancelMyCustomerReservation(requestToken, reservationId)
            if (!LoyaltySessionScope.matches(
                    accountId,
                    backendPrefs.getString("user_id", "").orEmpty(),
                    memberId,
                    backendMemberId()
                )) {
                throw IllegalStateException(
                    "O cancelamento foi concluído na sessão que o iniciou, mas a conta atual mudou. Atualiza as reservas da conta atual."
                )
            }
            customerReservationsLoaded = false
            "OK"
        }) {
            Toast.makeText(this, "Reserva cancelada.", Toast.LENGTH_SHORT).show()
            refreshCustomerReservations()
        }
    }

    private fun cachedCustomerRewards(): List<LoyaltyReward> {
        val cachedMemberId = backendPrefs.getString("customer_loyalty_cache_member_id", null)
        if (!LoyaltyCacheScope.belongsTo(cachedMemberId, backendMemberId())) return emptyList()
        val raw = backendPrefs.getString("customer_loyalty_rewards_json", "[]") ?: "[]"
        return runCatching {
            val rows = JSONArray(raw)
            (0 until rows.length()).map { LoyaltyReward.fromJson(rows.getJSONObject(it)) }
        }.getOrElse { emptyList() }
    }

    private fun cachedCustomerRewardHistory(): JSONArray {
        val cachedMemberId = backendPrefs.getString("customer_loyalty_cache_member_id", null)
        if (!LoyaltyCacheScope.belongsTo(cachedMemberId, backendMemberId())) return JSONArray()
        return runCatching {
            JSONArray(backendPrefs.getString("customer_loyalty_redemptions_json", "[]") ?: "[]")
        }.getOrElse { JSONArray() }
    }

    private fun refreshCustomerRewards() {
        val token = backendToken()
        val memberId = backendMemberId()
        val accountId = backendPrefs.getString("user_id", "").orEmpty()
        if (token.isBlank() || accountId.isBlank()) {
            showAuthChooser { renderCustomerApp() }
            return
        }
        if (memberId.isBlank()) {
            showCustomerJoinDialog()
            return
        }
        runBackend({
            val activeToken = backendToken()
            if (!LoyaltySessionScope.matches(
                accountId,
                backendPrefs.getString("user_id", "").orEmpty(),
                memberId,
                backendMemberId()
            )) {
                throw IllegalStateException("A conta ou o cartão de cliente mudou. Atualiza novamente a loja.")
            }
            val member = SupabaseApi.getMember(activeToken, memberId)
            if (member.optString("id") != memberId) {
                throw IllegalStateException("O cartão de cliente devolvido não corresponde a esta conta.")
            }
            val currentPoints = member.optInt("points", -1)
            if (currentPoints < 0) {
                throw IllegalStateException("O servidor devolveu um saldo de pontos inválido.")
            }
            val rewards = SupabaseApi.listCustomerLoyaltyRewards(activeToken, memberId)
            val history = SupabaseApi.listMyLoyaltyRewardRedemptions(activeToken)
            if (!LoyaltySessionScope.matches(
                accountId,
                backendPrefs.getString("user_id", "").orEmpty(),
                memberId,
                backendMemberId()
            )) {
                throw IllegalStateException("A conta ou o cartão mudou durante a atualização da loja.")
            }
            backendPrefs.edit()
                .putInt("member_points", currentPoints)
                .putString("member_code", member.optString("member_code"))
                .putString("customer_loyalty_rewards_json", rewards.toString())
                .putString("customer_loyalty_redemptions_json", history.toString())
                .putString("customer_loyalty_cache_member_id", memberId)
                .apply()
            rewards.length().toString()
        }) { count ->
            if (activeCustomerPage == "Prémios") renderCustomerApp()
            Toast.makeText(this, "Loja e saldo atualizados.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderCustomerRewardsPage(container: LinearLayout) {
        container.addView(label("Prémios e recompensas", 25f, true, forestDeep))
        addGap(container, 4)
        container.addView(label("Troca os pontos que acumulaste por recompensas deste restaurante.", 13f, false, muted))
        addGap(container, 11)

        when {
            backendToken().isBlank() -> {
                container.addView(primaryButton("Criar conta / iniciar sessão") {
                    showAuthChooser { renderCustomerApp() }
                }, LinearLayout.LayoutParams(-1, dp(48)))
                return
            }
            backendMemberId().isBlank() -> {
                container.addView(primaryButton("Associar ao restaurante") {
                    showCustomerJoinDialog()
                }, LinearLayout.LayoutParams(-1, dp(48)))
                return
            }
        }

        val points = backendPrefs.getInt("member_points", 0).coerceAtLeast(0)
        val balanceCard = makeCard(14, paleGreen)
        balanceCard.addView(label("O TEU SALDO", 10f, true, forestSoft))
        addGap(balanceCard, 4)
        balanceCard.addView(label(points.toString() + " pontos", 22f, true, forestDeep))
        container.addView(balanceCard, cardParams())
        container.addView(secondaryButton("Atualizar loja e histórico") {
            refreshCustomerRewards()
        }, LinearLayout.LayoutParams(-1, dp(44)))
        addGap(container, 8)

        val rewards = cachedCustomerRewards()
        if (rewards.isEmpty()) {
            container.addView(emptyCard("Ainda não existem recompensas disponíveis. Volta a atualizar ou consulta mais tarde."))
        } else {
            rewards.forEach { reward ->
                val card = makeCard(14)
                card.addView(label(reward.name, 15f, true, forestDeep))
                if (reward.description.isNotBlank()) {
                    addGap(card, 4)
                    card.addView(label(reward.description, 12f, false, ink))
                }
                addGap(card, 7)
                card.addView(label(reward.pointsCost.toString() + " pontos", 13f, true, forest))
                val available = reward.availableStock()
                if (available != null) {
                    val stockText = if (available == 1) "Última unidade disponível"
                    else available.toString() + " unidades disponíveis"
                    card.addView(label(stockText, 10.5f, false, muted))
                }
                addGap(card, 7)
                if (reward.canRedeem(points)) {
                    card.addView(primaryButton("Trocar por esta recompensa") {
                        showRedeemLoyaltyRewardConfirmation(reward)
                    }, LinearLayout.LayoutParams(-1, dp(44)))
                } else {
                    val reason = when {
                        !reward.isActive -> "Indisponível neste momento."
                        available == 0 -> "Esgotado."
                        else -> "Faltam " + (reward.pointsCost - points).coerceAtLeast(0).toString() + " pontos."
                    }
                    card.addView(label(reason, 11f, true, muted))
                }
                container.addView(card, cardParams())
            }
        }

        addGap(container, 12)
        container.addView(label("OS TEUS PEDIDOS", 10f, true, forestSoft))
        addGap(container, 5)
        val history = cachedCustomerRewardHistory()
        if (history.length() == 0) {
            container.addView(emptyCard("Ainda não pediste nenhuma recompensa."))
        } else {
            for (index in 0 until history.length()) {
                val row = history.optJSONObject(index) ?: continue
                val card = makeCard(12)
                card.addView(label(row.optString("reward_name", "Recompensa"), 13.5f, true, forestDeep))
                addGap(card, 3)
                val delivered = row.optString("status") == "fulfilled"
                card.addView(label(
                    row.optInt("points_spent", 0).toString() + " pontos · " +
                        if (delivered) "Entregue" else "À espera de levantamento",
                    11f, true, if (delivered) green else forest
                ))
                container.addView(card, cardParams())
            }
        }
    }

    private fun showRedeemLoyaltyRewardConfirmation(reward: LoyaltyReward) {
        val points = backendPrefs.getInt("member_points", 0).coerceAtLeast(0)
        if (!reward.canRedeem(points)) {
            AlertDialog.Builder(this)
                .setMessage("Esta recompensa já não está disponível ou o saldo de pontos mudou. Atualiza a loja.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Trocar pontos?")
            .setMessage("Serão descontados " + reward.pointsCost + " pontos por «" + reward.name + "». O pedido ficará pendente até o restaurante confirmar a entrega.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Confirmar troca") { _, _ ->
                val accountId = backendPrefs.getString("user_id", "").orEmpty()
                val memberId = backendMemberId()
                val requestToken = backendToken()
                if (accountId.isBlank() || memberId.isBlank() || requestToken.isBlank()) {
                    AlertDialog.Builder(this)
                        .setMessage("A sessão de cliente terminou. Inicia sessão novamente.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@setPositiveButton
                }
                val signature = JSONObject()
                    .put("member_id", memberId)
                    .put("reward_id", reward.id)
                    .toString()
                val operation = "loyalty_reward_redeem"
                val requestId = try {
                    pendingRequestIds.getOrCreate(operation, accountId, signature)
                } catch (error: Exception) {
                    AlertDialog.Builder(this)
                        .setMessage(error.message ?: "Não foi possível preparar o pedido.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@setPositiveButton
                }
                runBackend({
                    if (!LoyaltySessionScope.matches(
                        accountId,
                        backendPrefs.getString("user_id", "").orEmpty(),
                        memberId,
                        backendMemberId()
                    )) {
                        throw IllegalStateException("A conta ou o cartão mudou antes do resgate. Confirma novamente.")
                    }
                    val activeToken = backendToken()
                    val result = SupabaseApi.redeemLoyaltyReward(activeToken, reward.id, requestId)
                    val remaining = result.optInt("points_remaining", -1)
                    if (remaining < 0) throw IllegalStateException("O novo saldo de pontos não é válido.")
                    val rewards = SupabaseApi.listCustomerLoyaltyRewards(activeToken, memberId)
                    val history = SupabaseApi.listMyLoyaltyRewardRedemptions(activeToken)
                    if (!LoyaltySessionScope.matches(
                        accountId,
                        backendPrefs.getString("user_id", "").orEmpty(),
                        memberId,
                        backendMemberId()
                    )) {
                        throw IllegalStateException("A conta ou o cartão mudou durante o resgate. O pedido permanece guardado para confirmação segura.")
                    }
                    backendPrefs.edit()
                        .putInt("member_points", remaining)
                        .putString("customer_loyalty_rewards_json", rewards.toString())
                        .putString("customer_loyalty_redemptions_json", history.toString())
                        .putString("customer_loyalty_cache_member_id", memberId)
                        .apply()
                    JSONObject()
                        .put("points_remaining", remaining)
                        .put("points_spent", result.optInt("points_spent", reward.pointsCost))
                        .put("reward_name", result.optString("reward_name", reward.name))
                        .toString()
                }) { response ->
                    pendingRequestIds.complete(operation, accountId, signature, requestId)
                    if (!LoyaltySessionScope.matches(
                        accountId,
                        backendPrefs.getString("user_id", "").orEmpty(),
                        memberId,
                        backendMemberId()
                    )) {
                        return@runBackend
                    }
                    val result = JSONObject(response)
                    renderCustomerApp()
                    AlertDialog.Builder(this)
                        .setTitle("Pedido registado")
                        .setMessage(
                            "A recompensa «" + result.optString("reward_name") + "» foi pedida. " +
                                "Foram descontados " + result.optInt("points_spent") + " pontos.\n\n" +
                                "Saldo atual: " + result.optInt("points_remaining") + " pontos. " +
                                "O restaurante irá confirmar a entrega."
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .show()
    }

    private fun startInvoiceLoyaltyQrScan() {
        if (backendToken().isBlank()) {
            showAuthChooser { renderCustomerApp() }
            return
        }
        if (backendMemberId().isBlank()) {
            showCustomerJoinDialog()
            return
        }
        IntentIntegrator(this)
            .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            .setPrompt("Lê o QR de fidelização apresentado na fatura")
            .setBeepEnabled(false)
            .setOrientationLocked(false)
            .initiateScan()
    }

    private fun handleScannedInvoiceLoyaltyQr(rawPayload: String) {
        val qrToken = InvoiceLoyaltyQr.parseToken(rawPayload)
        if (qrToken == null) {
            AlertDialog.Builder(this)
                .setTitle("QR não reconhecido")
                .setMessage("Este não é um QR de fidelização StudioMesa. Os QR fiscais e outros códigos não alteram os pontos.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        if (backendToken().isBlank() || backendMemberId().isBlank()) {
            showAuthChooser { renderCustomerApp() }
            return
        }
        val expectedMemberId = backendMemberId()
        val expectedAccountId = backendPrefs.getString("user_id", "").orEmpty()
        runBackend({
            if (!LoyaltySessionScope.matches(
                expectedAccountId,
                backendPrefs.getString("user_id", "").orEmpty(),
                expectedMemberId,
                backendMemberId()
            )) {
                throw IllegalStateException("A conta ou o cartão mudou antes da leitura. Lê novamente o código.")
            }
            val result = SupabaseApi.redeemLoyaltyInvoiceQr(backendToken(), qrToken)
            if (result.optString("member_id") != expectedMemberId) {
                throw IllegalStateException("O QR não corresponde ao cartão de cliente desta conta.")
            }
            if (!LoyaltySessionScope.matches(
                expectedAccountId,
                backendPrefs.getString("user_id", "").orEmpty(),
                expectedMemberId,
                backendMemberId()
            )) {
                throw IllegalStateException("A conta ou o cartão mudou durante a validação do QR.")
            }
            backendPrefs.edit()
                .putInt("member_points", result.optInt("points", 0))
                .putString("member_code", result.optString("member_code"))
                .apply()
            result.toString()
        }) { response ->
            if (!LoyaltySessionScope.matches(
                expectedAccountId,
                backendPrefs.getString("user_id", "").orEmpty(),
                expectedMemberId,
                backendMemberId()
            )) {
                return@runBackend
            }
            val result = JSONObject(response)
            val awarded = result.optInt("points_awarded", 0)
            val balance = result.optInt("points", 0)
            val reference = result.optString("invoice_reference")
            AlertDialog.Builder(this)
                .setTitle("Compra validada")
                .setMessage(
                    "Foram reconhecidos $awarded pontos para o documento $reference.\n\n" +
                    "Saldo atual: $balance pontos.\n\n" +
                    "O mesmo QR não pode voltar a creditar pontos."
                )
                .setPositiveButton("Ver cartão") { _, _ -> renderCustomerApp() }
                .show()
        }
    }

    private fun showIssueInvoiceLoyaltyQrDialog(customer: LoyaltyCustomer) {
        val memberId = customer.backendId
        val restaurantId = backendRestaurantId()
        if (memberId.isNullOrBlank() || backendToken().isBlank() || restaurantId.isBlank()) {
            AlertDialog.Builder(this)
                .setMessage("É necessária uma sessão válida e um cliente associado ao restaurante.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        val referenceField = inputField(
            "Referência/número do documento",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        )
        val amountField = inputField(
            "Total pago (€)",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        )
        form.addView(label("REFERÊNCIA DO DOCUMENTO", 10f, true, forestSoft))
        form.addView(referenceField, fieldParams())
        addGap(form, 4)
        form.addView(label("TOTAL DA COMPRA", 10f, true, forestSoft))
        form.addView(amountField, fieldParams())
        addGap(form, 6)
        form.addView(label(
            "Regra inicial: 1 ponto por cada euro completo. O servidor calcula os pontos e impede a duplicação da referência. A equipa introduz os dados; este código não valida nem substitui a fatura fiscal.",
            11.5f, false, muted
        ))

        val dialog = AlertDialog.Builder(this)
            .setTitle("QR de fidelização da fatura")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Gerar QR", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val reference = referenceField.text.toString().trim()
                val rawAmount = amountField.text.toString().trim().replace(',', '.')
                val amount = runCatching { BigDecimal(rawAmount) }.getOrNull()
                when {
                    reference.isBlank() || reference.length > 100 ->
                        referenceField.error = "Indica uma referência entre 1 e 100 caracteres"
                    amount == null || amount.scale() > 2 ||
                        amount < BigDecimal("1.00") || amount > BigDecimal("100000.99") ->
                        amountField.error = "Introduz um total entre 1 € e 100 000,99 €, com até 2 casas decimais"
                    else -> {
                        val normalizedAmount = amount.setScale(2, java.math.RoundingMode.UNNECESSARY)
                        val signature = JSONObject()
                            .put("restaurant_id", restaurantId)
                            .put("member_id", memberId)
                            .put("invoice_reference", reference)
                            .put("amount", normalizedAmount.toPlainString())
                            .toString()
                        val operation = "loyalty_invoice_qr_issue"
                        val accountId = backendPrefs.getString("user_id", "").orEmpty()
                        if (!LoyaltySessionScope.matches(
                            accountId,
                            backendPrefs.getString("user_id", "").orEmpty(),
                            restaurantId,
                            backendRestaurantId()
                        )) {
                            referenceField.error = "A conta ou o restaurante mudou. Fecha e abre novamente."
                            return@setOnClickListener
                        }
                        val requestId = try {
                            pendingRequestIds.getOrCreate(operation, accountId, signature)
                        } catch (_: Exception) {
                            referenceField.error = "Não foi possível guardar a referência segura. Tenta novamente."
                            return@setOnClickListener
                        }
                        runBackend({
                            if (!LoyaltySessionScope.matches(
                                accountId,
                                backendPrefs.getString("user_id", "").orEmpty(),
                                restaurantId,
                                backendRestaurantId()
                            )) {
                                throw IllegalStateException("A conta ou o restaurante mudou antes da emissão do QR.")
                            }
                            SupabaseApi.issueLoyaltyInvoiceQr(
                                backendToken(), memberId, normalizedAmount, reference, requestId
                            ).toString()
                        }) { response ->
                            pendingRequestIds.complete(operation, accountId, signature, requestId)
                            if (!LoyaltySessionScope.matches(
                                accountId,
                                backendPrefs.getString("user_id", "").orEmpty(),
                                restaurantId,
                                backendRestaurantId()
                            )) {
                                return@runBackend
                            }
                            dialog.dismiss()
                            showIssuedInvoiceLoyaltyQr(JSONObject(response))
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showIssuedInvoiceLoyaltyQr(result: JSONObject) {
        val token = result.optString("qr_token")
        val payload = try {
            InvoiceLoyaltyQr.payloadForToken(token)
        } catch (_: Exception) {
            AlertDialog.Builder(this)
                .setTitle("QR inválido")
                .setMessage("O servidor não devolveu um código que possa ser apresentado.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        try {
            val size = 720
            val matrix = QRCodeWriter().encode(
                payload,
                BarcodeFormat.QR_CODE,
                size,
                size,
                mapOf(
                    EncodeHintType.MARGIN to 2,
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
                )
            )
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }

            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(18), dp(8), dp(18), dp(8))
            }
            content.addView(label("CÓDIGO DE FIDELIZAÇÃO", 10f, true, forestSoft))
            addGap(content, 5)
            content.addView(label(
                "+" + result.optInt("points") + " pontos",
                24f, true, forestDeep
            ))
            addGap(content, 4)
            content.addView(label(
                "Documento " + result.optString("invoice_reference") +
                    " · " + String.format(Locale("pt", "PT"), "%.2f €", result.optDouble("amount")),
                12f, false, muted
            ))
            addGap(content, 10)
            content.addView(ImageView(this).apply {
                setImageBitmap(bitmap)
                contentDescription = "QR Code de fidelização StudioMesa"
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(dp(250), dp(250)))
            addGap(content, 8)
            content.addView(label(
                "Válido até " + result.optString("expires_at").take(10) +
                    ". O cliente deve lê-lo na aplicação StudioMesa.",
                11f, false, muted
            ))
            addGap(content, 5)
            content.addView(label(
                "Talão de pontos — não substitui nem altera o QR fiscal da fatura.",
                11f, true, red
            ))
            AlertDialog.Builder(this)
                .setTitle("QR emitido")
                .setView(content)
                .setPositiveButton("Concluído", null)
                .show()
        } catch (_: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Não foi possível criar a imagem")
                .setMessage("O código ficou registado, mas a imagem QR não pôde ser apresentada neste dispositivo.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun renderCustomerMenuPage(container: LinearLayout) {
        container.addView(label("CARTA DO RESTAURANTE", 10.5f, true, forestSoft))
        addGap(container, 4)
        container.addView(label("Descobre o menu.", 25f, true, forestDeep))
        addGap(container, 4)
        container.addView(label("Consulta os artigos, descrições e preços disponíveis.", 13.5f, false, muted))
        addGap(container, 12)
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
        container.addView(menuCard, cardParams())
    }

    private fun renderCustomerAccountPage(container: LinearLayout) {
        container.addView(label("A tua conta", 25f, true, forestDeep))
        addGap(container, 4)
        container.addView(label("Perfil, associação ao restaurante e segurança.", 13.5f, false, muted))
        addGap(container, 13)
        when {
            backendToken().isBlank() -> container.addView(
                primaryButton("Criar conta / iniciar sessão") { showAuthChooser { renderCustomerApp() } },
                LinearLayout.LayoutParams(-1, dp(48))
            )
            backendMemberId().isBlank() -> container.addView(
                primaryButton("Associar ao restaurante") { showCustomerJoinDialog() },
                LinearLayout.LayoutParams(-1, dp(48))
            )
            else -> container.addView(
                primaryButton("Atualizar saldo no servidor") { refreshCustomerMembership() },
                LinearLayout.LayoutParams(-1, dp(48))
            )
        }
        addGap(container, 8)
        container.addView(secondaryButton("Definir o meu nome") { showCustomerProfileDialog() },
            LinearLayout.LayoutParams(-1, dp(48)))
        addGap(container, 12)
        val syncNote = makeCard(13)
        syncNote.addView(label(if (backendMemberId().isBlank()) "CARTÃO AINDA NÃO ASSOCIADO" else "CONTA LIGADA", 10f, true, if (backendMemberId().isBlank()) red else green))
        addGap(syncNote, 5)
        syncNote.addView(label(if (backendMemberId().isBlank()) "Para ver o saldo real, inicia sessão e associa a conta com o código fornecido pelo restaurante." else "Restaurante associado: " + (backendPrefs.getString("member_restaurant_name", "Restaurante") ?: "Restaurante") + ". Atualiza o saldo quando quiseres consultar as alterações mais recentes.", 12f, false, muted))
        container.addView(syncNote, cardParams())
        if (backendToken().isNotBlank()) {
            container.addView(secondaryButton("Eliminar conta permanentemente") {
                showDeleteAccountConfirmation()
            }, LinearLayout.LayoutParams(-1, dp(48)))
            addGap(container, 7)
            container.addView(secondaryButton("Terminar sessão neste dispositivo") {
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
                activeCustomerPage = "Cartão"
                renderCustomerApp()
            }, LinearLayout.LayoutParams(-1, dp(48)))
        }
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
                freshItems = SupabaseApi.listInventoryItems(token, restaurantId)
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
            val items = SupabaseApi.listInventoryItems(backendToken(), backendRestaurantId())
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
            LinearLayout.LayoutParams(-1, dp(48)))
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
            if (status == "Pendente" && tableNo <= 0) {
                card.addView(secondaryButton("Atribuir mesa") {
                    showAssignReservationTableDialog(id, reservation.optInt("party_size", 1))
                }, LinearLayout.LayoutParams(-1, dp(44)))
                addGap(card, 5)
            }
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

    private fun showAssignReservationTableDialog(reservationId: String, partySize: Int) {
        if (!hasRemoteRestaurant()) return
        val accountId = backendPrefs.getString("user_id", "").orEmpty()
        val restaurantId = backendRestaurantId()
        var eligibleTables = emptyList<JSONObject>()

        runBackend({
            if (accountId.isBlank() || restaurantId.isBlank() ||
                backendPrefs.getString("user_id", "").orEmpty() != accountId ||
                backendRestaurantId() != restaurantId) {
                throw IllegalStateException("A conta ou o restaurante mudou. Atualiza as reservas.")
            }
            val rows = SupabaseApi.listTables(backendToken(), restaurantId)
            eligibleTables = (0 until rows.length())
                .mapNotNull { rows.optJSONObject(it) }
                .filter { it.optInt("seats", 0) >= partySize }
                .sortedBy { it.optInt("table_number", 0) }
            if (eligibleTables.isEmpty()) {
                throw IllegalStateException("Não existem mesas com capacidade para $partySize pessoa(s). Adiciona ou configura uma mesa.")
            }
            "OK"
        }) {
            val labels = eligibleTables.map { table ->
                val number = table.optInt("table_number")
                val area = table.optString("area").ifBlank { "Sala principal" }
                val seats = table.optInt("seats", 0)
                "Mesa $number · $area · $seats lugares"
            }.toTypedArray()

            AlertDialog.Builder(this)
                .setTitle("Atribuir mesa")
                .setItems(labels) { _, which ->
                    val tableNumber = eligibleTables.getOrNull(which)?.optInt("table_number", 0) ?: 0
                    if (tableNumber !in 1..100) return@setItems
                    runBackend({
                        if (backendPrefs.getString("user_id", "").orEmpty() != accountId ||
                            backendRestaurantId() != restaurantId) {
                            throw IllegalStateException("A conta ou o restaurante mudou. Atualiza as reservas.")
                        }
                        SupabaseApi.assignReservationTable(
                            backendToken(), reservationId, tableNumber
                        )
                        val latest = SupabaseApi.listReservations(backendToken(), restaurantId)
                        reservationCache = latest
                        reservationsLoaded = true
                        "OK"
                    }) {
                        renderApp()
                        Toast.makeText(this, "Mesa $tableNumber atribuída.", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
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
        inventoryPageContainer = container
        pageIntro(container, "Stock e inventário", "Quantidades, limites mínimos, movimentos e desperdício registado.")
        addGap(container, 8)
        if (!hasRemoteRestaurant()) {
            container.addView(emptyCard("O inventário partilhado exige uma conta autenticada e um restaurante configurado na secção Conta."))
            return
        }
        container.addView(primaryButton("+  Adicionar artigo de stock") { showInventoryItemDialog(null) },
            LinearLayout.LayoutParams(-1, dp(48)))
        addGap(container, 6)
        container.addView(secondaryButton("Atualizar stock") { refreshInventoryItems() },
            LinearLayout.LayoutParams(-1, dp(48)))
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
        if (items.length() == 0) {
            container.addView(emptyCard("Ainda não há artigos de stock. Adiciona ingredientes, bebidas ou consumíveis e define um limite mínimo."))
            return
        }
        container.addView(
            secondaryButton("Consultar desperdício registado") { showInventoryWasteHistory() },
            LinearLayout.LayoutParams(-1, dp(48))
        )
        addGap(container, 8)
        var lowCount = 0
        var stockValueWithKnownCost = BigDecimal.ZERO.setScale(2)
        var itemsWithoutUnitCost = 0
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val id = item.optString("id")
            val name = item.optString("name", "Artigo")
            val unit = item.optString("unit", "unidades")
            val quantity = item.optDouble("quantity", 0.0)
            val threshold = item.optDouble("low_stock_threshold", 0.0)
            val unitCost = if (item.isNull("unit_cost_per_unit")) null else
                runCatching { BigDecimal(item.optString("unit_cost_per_unit")) }.getOrNull()
            if (unitCost == null) {
                itemsWithoutUnitCost++
            } else {
                val itemQuantity = runCatching { BigDecimal(item.optString("quantity", "0")) }.getOrNull()
                val itemValue = itemQuantity?.let { InventoryWasteCostCalculator.estimate(it, unitCost) }
                if (itemValue != null) stockValueWithKnownCost = stockValueWithKnownCost.add(itemValue)
            }
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
            card.addView(label(
                unitCost?.let { "Custo de compra: ${formatUnitCost(it)} / $unit" }
                    ?: "Custo de compra por unidade não definido",
                11.5f, false, muted
            ))
            addGap(card, 10)
            card.addView(secondaryButton("Editar artigo e limite") { showInventoryItemDialog(item) },
                LinearLayout.LayoutParams(-1, dp(48)))
            addGap(card, 5)
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(secondaryButton("+ Entrada") { showStockAdjustmentDialog(id, name, unit, true) },
                LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(0, 0, dp(4), 0) })
            buttons.addView(secondaryButton("− Saída") { showStockAdjustmentDialog(id, name, unit, false) },
                LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(4), 0, 0, 0) })
            card.addView(buttons)
            addGap(card, 5)
            card.addView(
                secondaryButton("Registar desperdício") { showInventoryWasteDialog(id, name, unit) },
                LinearLayout.LayoutParams(-1, dp(48))
            )
            addGap(card, 5)
            card.addView(secondaryButton("Ver histórico de movimentos") { showInventoryMovements(id, name) },
                LinearLayout.LayoutParams(-1, dp(48)))
            container.addView(card, cardParams())
        }
        val summary = makeCard(13, if (lowCount > 0) paleRed else paleGreen)
        summary.addView(label(if (lowCount > 0) "$lowCount artigo(s) no limite mínimo ou abaixo dele" else "Todos os artigos acima dos respetivos limites", 12.5f, true, if (lowCount > 0) red else forest))
        container.addView(summary, cardParams())

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
        valuation.addView(label(
            "Estimativa baseada no custo unitário atual introduzido pelo restaurante; não é valorização contabilística FIFO ou custo médio.",
            10.5f, false, muted
        ))
        container.addView(valuation, cardParams())
    }

    private fun formatEuro(value: BigDecimal): String =
        NumberFormat.getCurrencyInstance(Locale("pt", "PT")).format(value)

    private fun formatUnitCost(value: BigDecimal): String =
        "€ " + DecimalFormat("0.0000", DecimalFormatSymbols.getInstance(Locale("pt", "PT"))).format(value)

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
        val unitCost = inputField("Custo de compra por unidade (€)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).apply {
            setText(item?.let { if (it.isNull("unit_cost_per_unit")) "" else it.optString("unit_cost_per_unit") } ?: "")
        }
        val note = inputField("Motivo (opcional)", InputType.TYPE_CLASS_TEXT).apply { setText(if (item == null) "Stock inicial" else "Correção de inventário") }
        listOf(
            "Nome" to name,
            "Unidade" to unit,
            "Quantidade" to quantity,
            "Limite mínimo" to threshold,
            "Custo por unidade (€ por kg, L ou unidade)" to unitCost,
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
                val parsedUnitCost = try {
                    InventoryUnitCostValidator.parseOrNull(unitCost.text.toString())
                } catch (error: IllegalArgumentException) {
                    unitCost.error = error.message ?: "Custo unitário inválido."
                    return@setOnClickListener
                }
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
        dialog.window?.setLayout(-1, dp(580))
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

    private fun showInventoryWasteDialog(itemId: String, itemName: String, unit: String) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(10))
        }
        val amount = inputField("Quantidade desperdiçada", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val reason = Spinner(this)
        val reasonAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, InventoryWasteNotes.reasons)
        reasonAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        reason.adapter = reasonAdapter
        val details = inputField("Observação opcional", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)

        form.addView(label("Artigo: $itemName", 12f, true, forest))
        addGap(form, 7)
        form.addView(label("Quantidade a retirar ($unit)", 11f, true, forestSoft))
        form.addView(amount, fieldParams())
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
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Registar perda", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val rawValue = amount.text.toString().trim().replace(',', '.')
                val value = rawValue.toDoubleOrNull()
                if (value == null || !value.isFinite() || value <= 0.0 || value > 999999999.0) {
                    amount.error = "Indica uma quantidade positiva válida."
                    return@setOnClickListener
                }
                if (!StockDecimalValidator.hasAtMostThreeDecimalPlaces(rawValue)) {
                    amount.error = "Usa no máximo três casas decimais."
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
                    val latest = SupabaseApi.listInventoryItems(token, restaurantId)
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

            val itemNames = mutableMapOf<String, Pair<String, String>>()
            val inventory = remoteInventoryItems()
            for (index in 0 until inventory.length()) {
                val item = inventory.optJSONObject(index) ?: continue
                itemNames[item.optString("id")] = Pair(
                    item.optString("name", "Artigo"),
                    item.optString("unit", "unidades")
                )
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
                    val unitCostAtMovement = if (row.isNull("unit_cost_at_movement")) null else
                        runCatching { BigDecimal(row.get("unit_cost_at_movement").toString()) }.getOrNull()
                    val estimatedCost = if (quantity == null) null else
                        runCatching { InventoryWasteCostCalculator.estimate(quantity, unitCostAtMovement) }.getOrNull()
                    movementCosts.add(estimatedCost)

                    val date = runCatching {
                        dateFormat.format(isoFormat.parse(row.optString("created_at")) ?: Date())
                    }.getOrElse { row.optString("created_at").take(16) }
                    append(item?.first ?: "Artigo de stock")
                    append(" · −").append(quantity?.let { formatQuantity(it.toDouble()) } ?: "—")
                    append(' ').append(item?.second ?: "")
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

    private fun renderAssistant(container: LinearLayout) {
        pageIntro(
            container,
            "Assistente StudioMesa",
            "Conversa, pesquisa e apoio à gestão financeira do restaurante."
        )
        addGap(container, 8)

        if (!hasRemoteRestaurant()) {
            val demo = makeCard(16, forestDeep)
            demo.addView(label("ASSISTENTE EM MODO DE DEMONSTRAÇÃO", 10f, true, copper))
            addGap(demo, 7)
            demo.addView(label(
                "Liga uma conta de proprietário e configura o serviço de IA para ativar conversas, histórico, pesquisa na Internet e arquivo privado de faturas. As ferramentas locais continuam disponíveis abaixo.",
                12.5f, false, white
            ))
            container.addView(demo, cardParams())
            renderAssistantLocalTools(container)
            return
        }

        if (backendPrefs.getString("restaurant_role", "").orEmpty() != "owner") {
            container.addView(emptyCard(
                "O Assistente e o Gestor Financeiro estão reservados ao proprietário do restaurante. Esta conta não recebe nem consulta conversas ou documentos financeiros."
            ), cardParams())
            return
        }

        val scope = backendRestaurantId() + ":" + backendPrefs.getString("user_id", "").orEmpty()
        if (assistantWorkspaceScope.isNotBlank() && assistantWorkspaceScope != scope) {
            clearAssistantWorkspace()
        }
        if (!assistantWorkspaceLoaded && !assistantWorkspaceLoading) {
            refreshAssistantWorkspace()
        }

        val intro = makeCard(15, forestDeep)
        val introRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        introRow.addView(statusDot(Color.rgb(120, 194, 153)),
            LinearLayout.LayoutParams(dp(8), dp(8)).apply { setMargins(0, 0, dp(8), 0) })
        introRow.addView(label("STUDIOMESA INTELLIGENCE", 9.5f, true, copperPale))
        intro.addView(introRow)
        addGap(intro, 6)
        intro.addView(label("O teu consultor de negócio.", 21f, true, white))
        addGap(intro, 4)
        intro.addView(label(
            "Pergunta sobre margens, compras, desperdício, clientes, tendências e estratégia. A pesquisa na Internet e a leitura de faturas são opcionais.",
            12f, false, Color.rgb(229, 235, 228)
        ))
        container.addView(intro, cardParams())

        if (assistantWorkspaceLoading) {
            container.addView(emptyCard("A carregar o histórico e o arquivo privado do restaurante…"), cardParams())
            return
        }
        if (!assistantWorkspaceLoaded) {
            container.addView(emptyCard(
                assistantWorkspaceError.ifBlank {
                    "Não foi possível carregar o espaço do assistente. Verifica a ligação e tenta atualizar."
                }
            ), cardParams())
            container.addView(secondaryButton("Tentar novamente") {
                refreshAssistantWorkspace(force = true)
            }, LinearLayout.LayoutParams(-1, dp(44)))
            return
        }

        sectionHeading(container, "Histórico de conversas", "As conversas ficam guardadas na conta deste restaurante.")
        val historySearch = inputField("Pesquisar conversas e mensagens", InputType.TYPE_CLASS_TEXT).apply {
            setText(assistantHistoryQuery)
            setSelection(text.length)
        }
        val historyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        historyRow.addView(historySearch, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            setMargins(0, 0, dp(6), 0)
        })
        historyRow.addView(secondaryButton("Pesquisar") {
            assistantHistoryQuery = historySearch.text.toString().trim().take(100)
            refreshAssistantHistory()
        }, LinearLayout.LayoutParams(dp(104), dp(44)))
        container.addView(historyRow)
        addGap(container, 7)
        container.addView(primaryButton("+ Nova conversa") {
            createAssistantConversation()
        }, LinearLayout.LayoutParams(-1, dp(46)))
        addGap(container, 7)

        if (assistantConversations.length() == 0) {
            container.addView(emptyCard(
                if (assistantHistoryQuery.isBlank())
                    "Ainda não existem conversas. Começa uma nova conversa para guardar perguntas, respostas e fontes."
                else
                    "Não encontrei conversas ou mensagens com esse termo."
            ), cardParams())
        } else {
            val visibleCount = minOf(assistantConversations.length(), 12)
            for (index in 0 until visibleCount) {
                val conversation = assistantConversations.optJSONObject(index) ?: continue
                val id = conversation.optString("conversation_id")
                val title = conversation.optString("title", "Conversa")
                val preview = conversation.optString("last_message_preview")
                val selected = id == assistantCurrentConversationId
                val item = makeCard(12, if (selected) copperPale else white)
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                text.addView(label(title, 14f, true, forestDeep))
                if (preview.isNotBlank()) {
                    addGap(text, 3)
                    text.addView(label(preview, 11f, false, muted).apply {
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                }
                row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(label(if (selected) "●" else "›", 17f, true, if (selected) forest else copper))
                item.addView(row)
                item.isClickable = true
                item.isFocusable = true
                item.contentDescription = "Abrir conversa " + title
                item.setOnClickListener {
                    if (id.isNotBlank() && id != assistantCurrentConversationId) openAssistantConversation(id)
                }
                container.addView(item, cardParams())
            }
        }

        addGap(container, 5)
        sectionHeading(container, "Conversa", "As respostas e as fontes ficam guardadas no histórico.")
        if (assistantCurrentConversationId.isBlank()) {
            container.addView(emptyCard("Escolhe uma conversa do histórico ou carrega em «Nova conversa»."), cardParams())
        } else {
            val current = (0 until assistantConversations.length())
                .mapNotNull { assistantConversations.optJSONObject(it) }
                .firstOrNull { it.optString("conversation_id") == assistantCurrentConversationId }
            val conversationHeader = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            conversationHeader.addView(label(
                current?.optString("title").orEmpty().ifBlank { "Nova conversa" },
                15f, true, forestDeep
            ), LinearLayout.LayoutParams(0, -2, 1f))
            conversationHeader.addView(secondaryButton("Eliminar") {
                confirmDeleteAssistantConversation()
            }, LinearLayout.LayoutParams(dp(92), dp(40)))
            container.addView(conversationHeader)
            addGap(container, 7)

            if (assistantMessagesLoading) {
                container.addView(emptyCard("A carregar as mensagens…"), cardParams())
            } else if (assistantMessages.length() == 0) {
                container.addView(emptyCard("Escreve a primeira pergunta. O histórico será guardado nesta conta."), cardParams())
            } else {
                renderAssistantMessages(container)
            }

            addGap(container, 8)
            val composer = makeCard(13)
            composer.addView(label("A TUA PERGUNTA", 9.5f, true, forestSoft))
            addGap(composer, 4)
            val messageField = multilineInputField(
                "Ex.: Analisa os custos e sugere três formas de aumentar a margem."
            ).apply {
                minLines = 3
                maxLines = 6
                setText(assistantDraft)
            }
            composer.addView(messageField, multilineFieldParams())

            val optionsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            optionsRow.addView(secondaryButton(
                if (assistantWebSearchEnabled) "Pesquisa web: ligada" else "Pesquisa web: desligada"
            ) {
                assistantWebSearchEnabled = !assistantWebSearchEnabled
                renderApp()
            }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(0, 0, dp(5), 0) })
            optionsRow.addView(secondaryButton(
                "Faturas (" + assistantSelectedInvoiceIds.size + ")"
            ) {
                showAssistantInvoicePickerDialog()
            }, LinearLayout.LayoutParams(0, dp(42), 1f))
            composer.addView(optionsRow)
            addGap(composer, 5)

            val invoiceActions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            invoiceActions.addView(secondaryButton("Adicionar fatura PDF") {
                chooseAssistantInvoicePdf()
            }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(0, 0, dp(5), 0) })
            invoiceActions.addView(secondaryButton("Atualizar faturas") {
                refreshAssistantInvoices()
            }, LinearLayout.LayoutParams(0, dp(42), 1f))
            composer.addView(invoiceActions)
            if (assistantSelectedInvoiceIds.isNotEmpty()) {
                addGap(composer, 5)
                composer.addView(label(
                    assistantSelectedInvoiceIds.size.toString() +
                        " fatura(s) selecionada(s). Só serão enviadas para análise quando enviares esta pergunta.",
                    10.5f, false, muted
                ))
            }
            addGap(composer, 7)
            composer.addView(primaryButton(if (assistantBusy) "A enviar…" else "Enviar mensagem") {
                assistantDraft = messageField.text.toString().trim()
                sendAssistantChatMessage()
            }, LinearLayout.LayoutParams(-1, dp(48)))
            container.addView(composer, cardParams())
        }

        addGap(container, 5)
        container.addView(secondaryButton(
            if (assistantShowLocalTools) "Ocultar gestor económico e análises locais"
            else "Abrir gestor económico e ferramentas locais"
        ) {
            assistantShowLocalTools = !assistantShowLocalTools
            renderApp()
        }, LinearLayout.LayoutParams(-1, dp(46)))
        if (assistantShowLocalTools) {
            addGap(container, 8)
            renderAssistantLocalTools(container)
        }

        container.addView(label(
            "Privacidade: o chat e os PDFs pertencem a este restaurante. A pesquisa na Internet é opcional. A análise de faturas é automática e deve ser conferida antes de decisões contabilísticas ou fiscais.",
            10.5f, false, muted
        ))
    }

    private fun clearEconomicManagerState() {
        economicManagerEnabled = false
        economicScopeKey = null
        economicInputs.clear()
        economicReportText = ""
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
        toggle.addView(label("Os valores ficam só em memória nesta sessão: não são guardados, sincronizados nem enviados para a Internet.", 11.5f, false, muted))
        addGap(toggle, 8)
        toggle.addView(secondaryButton(if (economicManagerEnabled) "Desativar e limpar valores" else "Ativar gestão económica") {
            if (economicManagerEnabled) {
                clearEconomicManagerState()
            } else {
                economicManagerEnabled = true
                economicScopeKey = scope
                economicInputs.clear()
                economicReportText = ""
                recipeReportText = ""
                reorderReportText = ""
            }
            renderApp()
        }, LinearLayout.LayoutParams(-1, dp(44)))
        container.addView(toggle, cardParams())
        if (!economicManagerEnabled) return

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

        addGap(container, 6)
        val recipe = makeCard(15)
        recipe.addView(label("2 · Custo por receita e dose", 17f, true, forestDeep))
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

        addGap(container, 6)
        val reorder = makeCard(15)
        reorder.addView(label("3 · Reposição de stock", 17f, true, forestDeep))
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

        addGap(container, 6)
        economicLogCard(
            container, "4 · Registo de desperdício por artigo", 
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

        addGap(container, 6)
        economicLogCard(
            container, "5 · Comparar preços de fornecedores",
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

        addGap(container, 6)
        economicLogCard(
            container, "6 · Orçamento versus valor real",
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

        addGap(container, 6)
        economicLogCard(
            container, "7 · Consumo teórico versus real",
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

        addGap(container, 6)
        economicLogCard(
            container, "8 · Custo de horas de equipa",
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

        addGap(container, 4)
        container.addView(label(
            "Limites: cálculos indicativos, sem contabilidade certificada, ligação bancária, leitura automática de faturas, acesso a fornecedores ou movimentos automáticos de stock. Os registos de desperdício, propostas e comparações são manuais e ficam apenas nesta sessão. Não altera preços, stock, pagamentos nem encomendas.",
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

        val periods = SalesAnalysisPeriod.values()
        container.addView(
            secondaryButton("Período da análise: ${selectedAnalysisPeriod.label}") {
                AlertDialog.Builder(this)
                    .setTitle("Seleciona o período")
                    .setItems(periods.map { it.label }.toTypedArray()) { _, selected ->
                        selectedAnalysisPeriod = periods[selected]
                        renderApp()
                    }
                    .show()
            },
            LinearLayout.LayoutParams(-1, dp(46))
        )
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
        container.addView(label("STUDIOMESA  /  GESTÃO", 9.5f, true, forestSoft).apply {
            letterSpacing = 0.1f
        })
        addGap(container, 6)
        container.addView(label(title, 28f, true, forestDeep))
        addGap(container, 4)
        container.addView(label(subtitle, 13f, false, muted))
        addGap(container, 7)
        container.addView(View(this).apply { setBackgroundColor(copper) },
            LinearLayout.LayoutParams(dp(34), dp(3)))
    }

    private fun sectionHeading(container: LinearLayout, title: String, subtitle: String) {
        container.addView(label(title, 18f, true, forestDeep))
        addGap(container, 3)
        container.addView(label(subtitle, 12f, false, muted))
        addGap(container, 9)
    }

    private fun actionCard(
        parent: LinearLayout,
        number: String,
        title: String,
        subtitle: String,
        actionText: String,
        action: () -> Unit
    ) {
        val glyph = when {
            title.contains("Mesa", ignoreCase = true) -> "▦"
            title.contains("Pedido", ignoreCase = true) -> "≡"
            title.contains("Carta", ignoreCase = true) -> "▤"
            title.contains("Fidelização", ignoreCase = true) -> "◎"
            title.contains("Reserv", ignoreCase = true) -> "◷"
            title.contains("Preço", ignoreCase = true) -> "€"
            title.contains("Stock", ignoreCase = true) -> "▣"
            title.contains("Análise", ignoreCase = true) -> "↗"
            else -> "✦"
        }
        val card = makeCard(13)
        card.background = outlinedRippleBackground(white, 19, line)
        card.isClickable = true
        card.isFocusable = true
        card.contentDescription = title + ". " + subtitle + ". " + actionText
        card.setOnClickListener { action() }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(this).apply {
            text = glyph
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(forest)
            background = rounded(if (number == "IA") copperPale else paleGreen, 14)
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            setMargins(0, 0, dp(12), 0)
        })

        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        text.addView(label(title, 15f, true, ink))
        addGap(text, 3)
        text.addView(label(subtitle, 11.5f, false, muted))
        addGap(text, 6)
        text.addView(label(actionText, 9f, true, forestSoft).apply {
            letterSpacing = 0.06f
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        })
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(label("›", 27f, false, copper).apply {
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(dp(24), dp(48)))
        card.addView(row)
        parent.addView(card, cardParams())
    }

    private fun statCard(title: String, value: String, accent: Int): LinearLayout {
        val card = makeCard(15)
        card.addView(View(this).apply { setBackgroundColor(accent) },
            LinearLayout.LayoutParams(dp(28), dp(3)))
        addGap(card, 10)
        card.addView(label(title, 9f, true, muted).apply {
            letterSpacing = 0.04f
        })
        addGap(card, 6)
        card.addView(label(value, 27f, true, forestDeep))
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
            background = cardSurface(color, 20)
            elevation = dp(2).toFloat()
        }

    private fun cardSurface(color: Int, radius: Int): GradientDrawable =
        rounded(color, radius).apply {
            setStroke(dp(1), line)
        }

    private fun outlinedRippleBackground(
        color: Int,
        radius: Int,
        strokeColor: Int
    ): Drawable {
        val base = rounded(color, radius).apply {
            setStroke(dp(1), strokeColor)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            RippleDrawable(
                ColorStateList.valueOf(Color.argb(30, 43, 74, 72)),
                base,
                rounded(Color.WHITE, radius)
            )
        } else {
            base
        }
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
            setTextColor(white)
            background = rippleBackground(forestDeep, 15)
            elevation = dp(2).toFloat()
            setPadding(dp(14), dp(4), dp(14), dp(4))
            minHeight = dp(48)
            setOnClickListener { action() }
        }

    private fun secondaryButton(title: String, action: () -> Unit): Button =
        Button(this).apply {
            text = title
            textSize = 11.5f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            isAllCaps = false
            setTextColor(forest)
            background = outlinedRippleBackground(white, 14, line)
            elevation = 0f
            setPadding(dp(10), dp(3), dp(10), dp(3))
            minHeight = dp(44)
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
