package pt.studiomesa.app

import androidx.activity.ComponentActivity
import android.content.Intent
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.ColorStateList
import android.app.AlertDialog
import android.graphics.Bitmap
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
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.FileProvider
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.integration.android.IntentIntegrator

class MainActivity : ComponentActivity() {
    companion object {
        private const val REQUEST_EXPORT_ORDERS_CSV = 7812
        private const val REQUEST_IMPORT_SUPPLIER_INVOICE = 7813
    }

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
    private val assistantChatStore by lazy { AssistantChatStore(this) }
    private val supplierInvoiceStore by lazy { SupplierInvoiceStore(this) }
    private val assistantCloudSyncCoordinator by lazy { AssistantCloudSyncCoordinator() }
    private var assistantCloudSyncInFlight = false
    private var assistantCloudLastSyncAtMillis = 0L
    private var invoiceImportInFlight = false
    private var pendingInvoiceImport: PendingInvoiceImport? = null

    private data class PendingInvoiceImport(
        val uri: android.net.Uri,
        val document: ProcessedInvoiceDocument,
        val draft: SupplierInvoiceDraft
    )
    private var activeAssistantConversationId: String? = null
    private var assistantChatScopeKey: String? = null
    private var assistantComposerDraft = ""
    // Preserve the chat reading position when progress updates rebuild the screen.
    private var assistantChatScrollY = 0
    private var assistantChatAutoFollow = true
    private var assistantChatError: String? = null
    private var assistantSending = false
    private var assistantStreamingTextView: TextView? = null
    private var assistantModelDownloading = false
    private var assistantModelProgressBytes = 0L
    private var assistantModelError: String? = null
    // Update download progress in place; rebuilding the chat for every percent causes flicker.
    private var assistantModelStatusTextView: TextView? = null
    private var assistantModelProgressTextView: TextView? = null
    private val localAssistantModelManagerDelegate = lazy { LocalAssistantModelManager(this) }
    private val localAssistantModelManager by localAssistantModelManagerDelegate
    private var assistantDeepThinkingEnabled = false

    private data class AssistantChatMessage(
        val fromUser: Boolean,
        val text: String,
        val actionPage: String? = null
    )

    private val assistantChatMessages = mutableListOf(
        AssistantChatMessage(
            fromUser = false,
            text = "Olá! Sou o Assistente StudioMesa. Pergunta sobre preços, carta, pedidos, stock, reservas ou gestão do restaurante."
        )
    )
    private var assistantToolsVisible = false
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
    // Reservation contacts, reward history and staff permissions are memory-only.
    private var currentStaffPermissions: Set<String>? = null
    private var customerReservationsCache = JSONArray()
    private var customerRedemptionHistory = JSONArray()
    // Reservation contacts are kept in memory only, not persisted to SharedPreferences.
    private var reservationCache = JSONArray()
    private var reservationsLoaded = false
    // CSV remains in memory only until the user chooses a destination.
    private var pendingCsvExport: String? = null

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
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    activePage == "Assistente económico" && assistantToolsVisible -> {
                        assistantToolsVisible = false
                        renderApp()
                    }
                    activePage == "IA" || activePage == "Assistente económico" -> {
                        activePage = "Início"
                        assistantToolsVisible = false
                        renderApp()
                    }
                    else -> finish()
                }
            }
        })
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
    }

    override fun onPause() {
        activityResumed = false
        stopKitchenAutoRefresh()
        stopInventoryAutoRefresh()
        super.onPause()
    }

    override fun onStop() {
        if (localAssistantModelManagerDelegate.isInitialized()) {
            localAssistantModelManager.releaseEngine()
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (localAssistantModelManagerDelegate.isInitialized()) {
            localAssistantModelManager.close()
        }
        super.onDestroy()
    }

    @Deprecated("Uses Android's document picker result contract for compatibility with Activity.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (scanResult != null) {
            val contents = scanResult.contents
            if (contents.isNullOrBlank()) {
                Toast.makeText(this, "Leitura do QR cancelada.", Toast.LENGTH_SHORT).show()
            } else {
                handleLoyaltyQrPayload(contents)
            }
            return
        }

        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMPORT_SUPPLIER_INVOICE) {
            val uri = data?.data
            if (resultCode == RESULT_OK && uri != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                processSupplierInvoiceDocument(uri)
            }
            return
        }
        if (requestCode != REQUEST_EXPORT_ORDERS_CSV) return
        val csv = pendingCsvExport
        pendingCsvExport = null
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        if (csv == null) {
            AlertDialog.Builder(this)
                .setTitle("Exportação interrompida")
                .setMessage("O relatório deixou de estar disponível. Abre Análise e inicia a exportação novamente.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        try {
            val output = contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Não foi possível abrir o ficheiro escolhido.")
            output.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
            AlertDialog.Builder(this)
                .setTitle("Relatório exportado")
                .setMessage("O CSV foi guardado no destino escolhido. Contém dados operacionais, não comprovativos de pagamento ou contabilidade oficial.")
                .setPositiveButton("OK", null)
                .show()
        } catch (_: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Não foi possível exportar")
                .setMessage("Não foi possível guardar o ficheiro. Escolhe outro destino com espaço disponível e tenta novamente.")
                .setPositiveButton("OK", null)
                .show()
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
        val pages = mutableListOf("Início", "Mesas", "Pedidos", "Cozinha", "Carta", "Pontos", "IA", "Assistente económico", "Conta")
        if (hasCalculator) {
            pages.add("Reservas")
            pages.add("Preço Justo")
        }
        if (isPremium) {
            pages.add("Stock")
            pages.add("Análise")
        }

        if (hasRemoteRestaurant() && backendPrefs.getString("restaurant_role", "").orEmpty() != "owner") {
            pages.remove("Assistente económico")
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
            "ASSISTÊNCIA" to listOf("IA", "Assistente económico"),
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
                            assistantToolsVisible = false
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
        drawerRoot.addView(secondaryButton("Privacidade e dados") {
            dialog.dismiss()
            showPrivacyInformation()
        }, LinearLayout.LayoutParams(-1, dp(46)).apply {
            setMargins(dp(12), dp(8), dp(12), dp(2))
        })
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
        "IA" -> "✦"
        "Assistente económico" -> "€"
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
    private fun applySystemBarInsets(root: View, includeIme: Boolean = false) {
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
            // In edge-to-edge mode, the IME does not resize the content automatically.
            // Reserve the keyboard height on the chat screen so its composer stays above it.
            val bottomInset = if (includeIme) {
                maxOf(safeInsets.bottom, insets.getInsets(WindowInsets.Type.ime()).bottom)
            } else {
                safeInsets.bottom
            }
            view.setPadding(
                baseLeft + safeInsets.left,
                baseTop + safeInsets.top,
                baseRight + safeInsets.right,
                baseBottom + bottomInset
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
        if (activePage == "IA" || activePage == "Assistente económico") {
            val economicAssistant = activePage == "Assistente económico"
            if (economicAssistant && assistantToolsVisible) {
                renderAssistantToolsScreen()
            } else {
                renderAssistantChatScreen(isEconomicAssistant = economicAssistant)
            }
            return
        }

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
        updateKitchenAutoRefresh()
        updateInventoryAutoRefresh()
    }

    private fun renderAssistantChatScreen(
        isEconomicAssistant: Boolean = activePage == "Assistente económico"
    ) {
        val scope = assistantConversationScope(isEconomicAssistant)
        val ownerInvoiceAccess = hasRemoteRestaurant() &&
            backendToken().isNotBlank() &&
            backendPrefs.getString("restaurant_role", "") == "owner"
        if (isEconomicAssistant || ownerInvoiceAccess) {
            refreshSupplierInvoicesFromCloud(showFeedback = false)
        }
        if (assistantChatScopeKey != scope) {
            assistantChatScopeKey = scope
            activeAssistantConversationId = null
            assistantComposerDraft = ""
            assistantChatScrollY = 0
            assistantChatAutoFollow = true
            assistantChatError = null
        }

        appRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(paper)
        }
        applySystemBarInsets(appRoot, includeIme = true)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setBackgroundColor(white)
            elevation = dp(2).toFloat()
        }
        val backButton = TextView(this).apply {
            text = "‹"
            textSize = 31f
            gravity = Gravity.CENTER
            setTextColor(forestDeep)
            contentDescription = "Voltar ao painel principal"
            background = rippleBackground(paper, 14)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                activePage = "Início"
                renderApp()
            }
        }
        toolbar.addView(backButton, LinearLayout.LayoutParams(dp(42), dp(46)))

        val titleStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), 0, dp(2), 0)
        }
        titleStack.addView(label(
            if (isEconomicAssistant) "Assistente económico" else "IA StudioMesa",
            15f, true, forestDeep
        ))
        titleStack.addView(label(
            (if (isEconomicAssistant) "Faturas e gestão financeira" else "Conversa geral") +
                " · " + displayRestaurantName(),
            9.5f, false, muted
        ).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        toolbar.addView(titleStack, LinearLayout.LayoutParams(0, -2, 1f))

        val historyButton = TextView(this).apply {
            text = "☷"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(forestDeep)
            contentDescription = "Histórico de conversas"
            background = rippleBackground(paper, 12)
            isClickable = true
            isFocusable = true
            setOnClickListener { showAssistantHistoryDialog() }
        }
        toolbar.addView(historyButton, LinearLayout.LayoutParams(dp(36), dp(44)))

        val memoryButton = TextView(this).apply {
            text = "✎"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(forestDeep)
            contentDescription = "Memória do assistente"
            background = rippleBackground(paper, 12)
            isClickable = true
            isFocusable = true
            setOnClickListener { showAssistantMemoryDialog() }
        }
        toolbar.addView(memoryButton, LinearLayout.LayoutParams(dp(34), dp(44)))

        val syncButton = TextView(this).apply {
            text = "⟳"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(forestDeep)
            contentDescription = "Sincronizar histórico e memória"
            background = rippleBackground(paper, 12)
            isClickable = true
            isFocusable = true
            setOnClickListener { requestAssistantCloudSync() }
        }
        toolbar.addView(syncButton, LinearLayout.LayoutParams(dp(34), dp(44)))

        if (isEconomicAssistant) {
            val documentsButton = TextView(this).apply {
                text = "▤"
                textSize = 19f
                gravity = Gravity.CENTER
                setTextColor(forestDeep)
                contentDescription = "Abrir documentos e faturas"
                background = rippleBackground(paper, 12)
                isClickable = true
                isFocusable = true
                setOnClickListener { showSupplierInvoiceArchiveDialog() }
            }
            toolbar.addView(documentsButton, LinearLayout.LayoutParams(dp(34), dp(44)))

            val toolsButton = TextView(this).apply {
                text = "€"
                textSize = 19f
                gravity = Gravity.CENTER
                setTextColor(forestDeep)
                contentDescription = "Abrir ferramentas económicas"
                background = rippleBackground(paper, 12)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    assistantToolsVisible = true
                    renderApp()
                }
            }
            toolbar.addView(toolsButton, LinearLayout.LayoutParams(dp(36), dp(44)))
        }

        val newChatButton = TextView(this).apply {
            text = "＋"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(forestDeep)
            contentDescription = "Nova conversa"
            background = rippleBackground(paper, 12)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                activeAssistantConversationId = null
                assistantComposerDraft = ""
                assistantChatScrollY = 0
                assistantChatAutoFollow = true
                assistantChatError = null
                assistantDeepThinkingEnabled = false
                renderAssistantChatScreen()
            }
        }
        toolbar.addView(newChatButton, LinearLayout.LayoutParams(dp(36), dp(44)))
        appRoot.addView(toolbar, LinearLayout.LayoutParams(-1, -2))

        val conversationScroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            setOnScrollChangeListener { _, _, scrollY, _, _ ->
                assistantChatScrollY = scrollY
                val contentHeight = getChildAt(0)?.height ?: 0
                // Follow new messages only while the reader is already at the bottom.
                assistantChatAutoFollow = contentHeight <= height ||
                    scrollY + height >= contentHeight - dp(12)
            }
        }
        val conversation = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(16))
        }
        renderLocalAssistantModelCard(conversation)

        val turns = try {
            activeAssistantConversationId?.let { assistantChatStore.readTurns(scope, it) }.orEmpty()
        } catch (error: Exception) {
            assistantChatError = error.message ?: "Não foi possível abrir o histórico cifrado."
            emptyList()
        }

        if (turns.isEmpty()) {
            val welcome = makeCard(16, white)
            welcome.addView(label(
                if (isEconomicAssistant) "Vamos analisar as finanças do restaurante." else "Nova conversa. Em que posso ajudar?",
                17f, true, forestDeep
            ))
            addGap(welcome, 6)
            welcome.addView(label("EXPERIMENTA PERGUNTAR", 9f, true, forestSoft))
            addGap(welcome, 5)
            addGap(welcome, 6)
            welcome.addView(label(
                if (isEconomicAssistant)
                    "Posso analisar custos, tesouraria, fornecedores e metadados das faturas importadas. A leitura é limitada aos campos guardados no arquivo; não prova pagamentos nem substitui a contabilidade."
                else if (ownerInvoiceAccess)
                    "Com o modelo instalado, podes conversar sobre qualquer tema e pedir à IA para consultar o Assistente económico sobre as faturas do restaurante. Esta ligação só lê metadados do arquivo; não altera documentos nem comprova pagamentos."
                else
                    "Com o modelo instalado, podes conversar sobre assuntos gerais, aprender, escrever, traduzir, programar e resolver problemas. Os dados financeiros privados continuam reservados ao proprietário.",
                12f, false, muted
            ))
            conversation.addView(welcome, cardParams())
            (if (isEconomicAssistant) listOf(
                "Resume as faturas pendentes e confirmadas.",
                "Agrupa as despesas por fornecedor.",
                "Que dados preciso para analisar a tesouraria?"
            ) else if (ownerInvoiceAccess) listOf(
                "Consulta as faturas pendentes através do Assistente económico.",
                "Quanto foi gasto por fornecedor?",
                "Resume as despesas registadas."
            ) else listOf(
                "Explica-me um conceito de programação.",
                "Ajuda-me a escrever um texto profissional.",
                "Como posso aprender um tema novo?"
            )).forEach { prompt ->
                conversation.addView(secondaryButton(prompt) {
                    sendAssistantMessage(prompt)
                }, LinearLayout.LayoutParams(-1, dp(42)).apply {
                    setMargins(0, dp(3), 0, dp(3))
                })
            }
        } else {
            val title = runCatching {
                assistantChatStore.listConversations(scope).firstOrNull { it.id == activeAssistantConversationId }?.title
            }.getOrNull()
            if (!title.isNullOrBlank()) {
                conversation.addView(label(title, 13.5f, true, forestDeep))
                addGap(conversation, 7)
            }
            turns.forEach { turn -> renderAssistantBubble(conversation, turn) }
        }

        if (assistantSending) {
            val thinkingCard = makeCard(12)
            thinkingCard.addView(label(
                if (isEconomicAssistant) "STUDIOMESA · ASSISTENTE ECONÓMICO" else "STUDIOMESA · IA LOCAL",
                9f, true, forestSoft
            ))
            addGap(thinkingCard, 5)
            assistantStreamingTextView = label("A analisar no dispositivo…", 13f, false, ink)
            thinkingCard.addView(assistantStreamingTextView)
            conversation.addView(thinkingCard, cardParams())
        } else {
            assistantStreamingTextView = null
        }

        assistantChatError?.let { error ->
            val errorCard = makeCard(11, paleRed)
            errorCard.addView(label(error, 11.5f, true, red))
            conversation.addView(errorCard, cardParams())
        }

        conversationScroll.addView(conversation)
        appRoot.addView(conversationScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val composerArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(7), dp(10), dp(7))
            setBackgroundColor(white)
            elevation = dp(5).toFloat()
        }
        val messageField = multilineInputField("Escreve uma mensagem...")
        messageField.minLines = 1
        messageField.maxLines = 4
        messageField.setPadding(dp(13), dp(10), dp(13), dp(10))
        messageField.setText(assistantComposerDraft)
        messageField.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                assistantComposerDraft = s?.toString().orEmpty()
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        val composerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        composerRow.addView(messageField, LinearLayout.LayoutParams(0, -2, 1f).apply {
            setMargins(0, 0, dp(7), 0)
        })
        composerRow.addView(primaryButton("Enviar") {
            assistantComposerDraft = messageField.text.toString()
            sendAssistantMessage(assistantComposerDraft)
        }, LinearLayout.LayoutParams(dp(78), dp(48)))
        composerArea.addView(composerRow, LinearLayout.LayoutParams(-1, -2))

        val optionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, 0)
        }
        val modelInstalled = localAssistantModelManager.isModelInstalled()
        optionsRow.addView(secondaryButton(
            if (assistantDeepThinkingEnabled) "✦ Pensar mais · ativo" else "✦ Pensar mais"
        ) {
            if (!localAssistantModelManager.isModelInstalled()) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Pensar mais")
                    .setMessage("O modo de análise aprofundada requer o modelo generativo local. A instalação é única, ocupa cerca de 736 MB e não tem custo por mensagem. Recomenda-se Wi-Fi.")
                    .setNegativeButton("Agora não", null)
                    .setPositiveButton("Instalar modelo") { _, _ -> startLocalAssistantModelDownload() }
                    .show()
            } else {
                assistantDeepThinkingEnabled = !assistantDeepThinkingEnabled
                renderAssistantChatScreen()
            }
        }, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
            setMargins(0, 0, dp(6), 0)
        })
        optionsRow.addView(label(
            if (assistantSending) "A analisar…" else if (modelInstalled) "IA offline" else "IA local por instalar",
            9.5f, true, if (modelInstalled) forestSoft else muted
        ).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(100), -2))
        composerArea.addView(optionsRow, LinearLayout.LayoutParams(-1, -2))
        composerArea.addView(label(
            if (assistantDeepThinkingEnabled && modelInstalled)
                "Modo aprofundado: verifica dados e compara opções; não expõe raciocínio interno."
            else if (modelInstalled)
                "As mensagens são processadas no dispositivo. Sem API ou custo por mensagem."
            else
                "Sem modelo instalado, continuam disponíveis as respostas locais baseadas em regras.",
            9f, false, muted
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(3), dp(2), 0)
        })
        appRoot.addView(composerArea, LinearLayout.LayoutParams(-1, -2))

        setContentView(appRoot)
        val savedScrollY = assistantChatScrollY
        val shouldFollowLatestMessage = assistantChatAutoFollow
        conversationScroll.post {
            if (shouldFollowLatestMessage) {
                conversationScroll.fullScroll(View.FOCUS_DOWN)
            } else {
                conversationScroll.scrollTo(0, savedScrollY.coerceAtLeast(0))
            }
        }
        updateKitchenAutoRefresh()
        updateInventoryAutoRefresh()
    }


    private fun showSupplierInvoiceArchiveDialog() {
        val scope = assistantChatScope()
        val records = runCatching { supplierInvoiceStore.list(scope) }.getOrDefault(emptyList())
        val total = records.fold(BigDecimal.ZERO) { sum, item ->
            sum.add(item.totalAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO)
        }
        val text = buildString {
            append("Documentos: ").append(records.size)
            append("\nPendentes de revisão: ").append(records.count { it.reviewStatus == "pending_review" })
            append("\nTotal arquivado: ").append(formatInvoiceEuro(total))
            if (records.isEmpty()) {
                append("\n\nAinda não existem faturas importadas.")
            } else {
                append("\n\n")
                records.take(30).forEach { item ->
                    val status = when (item.reviewStatus) {
                        "approved" -> "Confirmada"
                        "rejected" -> "Rejeitada"
                        else -> "Pendente"
                    }
                    append("• ").append(item.supplierName).append(" · ")
                        .append(item.invoiceNumber).append(" · ")
                        .append(item.issueDate).append(" · ")
                        .append(formatInvoiceEuro(item.totalAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO))
                        .append(" · ").append(status).append('\n')
                }
                if (records.size > 30) append("\nA mostrar os primeiros 30 documentos.")
            }
            append("\n\nO OCR corre no telemóvel. Só o proprietário pode sincronizar documentos para o arquivo privado do restaurante.")
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Documentos e faturas")
            .setMessage(text)
            .setNegativeButton("Fechar", null)
            .setNeutralButton("Importar PDF / imagem", null)
            .setPositiveButton("Sincronizar pendentes", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialog.dismiss()
                openSupplierInvoicePicker()
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialog.dismiss()
                syncPendingSupplierInvoiceUploads()
            }
        }
        dialog.show()
    }

    private fun openSupplierInvoicePicker() {
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        runCatching {
            startActivityForResult(picker, REQUEST_IMPORT_SUPPLIER_INVOICE)
        }.onFailure {
            AlertDialog.Builder(this)
                .setTitle("Não foi possível abrir os documentos")
                .setMessage("O seletor de ficheiros não está disponível neste dispositivo.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun processSupplierInvoiceDocument(uri: android.net.Uri) {
        if (invoiceImportInFlight) return
        invoiceImportInFlight = true
        val progress = AlertDialog.Builder(this)
            .setTitle("A ler documento no telemóvel")
            .setMessage("A extrair texto localmente. Nenhum documento está a ser enviado para uma API de IA.")
            .setCancelable(false)
            .create()
        progress.show()
        Thread {
            val result = runCatching {
                val document = InvoiceDocumentProcessor(this).process(uri)
                val source = if (document.mimeType == "application/pdf") "pdf_ocr" else "image_ocr"
                PendingInvoiceImport(uri, document, SupplierInvoiceTextParser.parse(document.extractedText, source))
            }
            runOnUiThread {
                invoiceImportInFlight = false
                progress.dismiss()
                result.fold(
                    onSuccess = { pending ->
                        pendingInvoiceImport = pending
                        showSupplierInvoiceReviewDialog(pending)
                    },
                    onFailure = { error ->
                        AlertDialog.Builder(this)
                            .setTitle("Não foi possível ler a fatura")
                            .setMessage(error.message ?: "A leitura local falhou. Experimenta uma imagem mais nítida ou introduz os valores manualmente.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                )
            }
        }.start()
    }

    private fun showSupplierInvoiceReviewDialog(pending: PendingInvoiceImport) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        form.addView(label(
            "Confirma todos os campos na fatura original. O OCR pode trocar algarismos, datas ou separadores decimais.",
            11.5f, false, muted
        ))
        addGap(form, 7)
        if (pending.draft.warnings.isNotEmpty()) {
            val warnings = makeCard(10, copperPale)
            warnings.addView(label(pending.draft.warnings.joinToString("\n• ", prefix = "• "), 10.5f, true, forestDeep))
            form.addView(warnings, cardParams())
            addGap(form, 7)
        }
        fun field(title: String, hint: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText {
            form.addView(label(title, 10f, true, forestSoft))
            val edit = inputField(hint, type).apply { setText(value) }
            form.addView(edit, fieldParams())
            addGap(form, 4)
            return edit
        }
        val supplier = field("Fornecedor", "Nome do fornecedor", pending.draft.supplierName)
        val vat = field("NIF / VAT", "NIF ou número VAT", pending.draft.supplierVatNumber)
        val invoice = field("Número da fatura", "Número da fatura", pending.draft.invoiceNumber)
        val issue = field("Data de emissão (AAAA-MM-DD)", "AAAA-MM-DD", pending.draft.issueDate)
        val due = field("Data de vencimento (opcional)", "AAAA-MM-DD", pending.draft.dueDate)
        val amountType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        val net = field("Base tributável (€)", "0,00", pending.draft.netAmount?.toPlainString().orEmpty(), amountType)
        val vatAmount = field("IVA (€)", "0,00", pending.draft.vatAmount?.toPlainString().orEmpty(), amountType)
        val total = field("Total a pagar (€)", "0,00", pending.draft.totalAmount?.toPlainString().orEmpty(), amountType)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Rever fatura")
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton("Guardar pendente", null)
            .setNeutralButton("Cancelar", null)
            .setPositiveButton("Confirmar fatura", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                saveReviewedSupplierInvoice(pending, "pending_review", supplier, vat, invoice, issue, due, net, vatAmount, total, dialog)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                saveReviewedSupplierInvoice(pending, "approved", supplier, vat, invoice, issue, due, net, vatAmount, total, dialog)
            }
        }
        dialog.show()
        dialog.window?.setLayout(-1, dp(660))
    }

    private fun saveReviewedSupplierInvoice(
        pending: PendingInvoiceImport,
        status: String,
        supplierField: EditText,
        vatField: EditText,
        invoiceField: EditText,
        issueField: EditText,
        dueField: EditText,
        netField: EditText,
        vatFieldAmount: EditText,
        totalField: EditText,
        dialog: AlertDialog
    ) {
        val supplier = supplierField.text.toString().trim().take(100)
        val vatNumber = vatField.text.toString().trim().take(30)
        val invoiceNumber = invoiceField.text.toString().trim().take(40)
        val issueDate = parseInvoiceDate(issueField.text.toString())
        val dueDate = parseInvoiceDate(dueField.text.toString())
        val netAmount = netField.text.toString().trim().takeIf { it.isNotBlank() }?.let(SupplierInvoiceTextParser::parseAmount)
        val vatAmount = vatFieldAmount.text.toString().trim().takeIf { it.isNotBlank() }?.let(SupplierInvoiceTextParser::parseAmount)
        val totalAmount = SupplierInvoiceTextParser.parseAmount(totalField.text.toString())
        if (supplier.isBlank()) { supplierField.error = "Indica o fornecedor."; return }
        if (invoiceNumber.isBlank()) { invoiceField.error = "Indica o número da fatura."; return }
        if (issueDate == null) { issueField.error = "Usa uma data válida no formato AAAA-MM-DD."; return }
        if (dueField.text.isNotBlank() && dueDate == null) { dueField.error = "Usa uma data válida ou deixa vazio."; return }
        if (totalAmount == null || totalAmount <= BigDecimal.ZERO) { totalField.error = "Indica um total superior a zero."; return }
        if (vatNumber.length == 9 && vatNumber.all(Char::isDigit) && !SupplierInvoiceTextParser.isValidPortugueseVatNumber(vatNumber)) {
            vatField.error = "O NIF português não passou o dígito de controlo. Confirma-o."
            return
        }
        val warning = if (netAmount != null && vatAmount != null) {
            SupplierInvoiceDraft(netAmount = netAmount, vatAmount = vatAmount, totalAmount = totalAmount).arithmeticWarning()
        } else null
        if (warning != null && status == "approved") {
            AlertDialog.Builder(this)
                .setTitle("Valores não coincidem")
                .setMessage("$warning Confirma os valores no original antes de aprovar.")
                .setNegativeButton("Voltar a rever", null)
                .setPositiveButton("Confirmar mesmo assim") { _, _ ->
                    persistSupplierInvoice(pending, status, supplier, vatNumber, invoiceNumber, issueDate, dueDate.orEmpty(), netAmount, vatAmount, totalAmount, warning, dialog)
                }
                .show()
            return
        }
        persistSupplierInvoice(pending, status, supplier, vatNumber, invoiceNumber, issueDate, dueDate.orEmpty(), netAmount, vatAmount, totalAmount, warning, dialog)
    }

    private fun parseInvoiceDate(value: String): String? {
        val clean = value.trim()
        if (clean.isBlank()) return null
        return runCatching { java.time.LocalDate.parse(clean).toString() }.getOrElse {
            listOf("d/M/uuuu", "d-M-uuuu", "d.M.uuuu").firstNotNullOfOrNull { pattern ->
                runCatching {
                    java.time.LocalDate.parse(clean, java.time.format.DateTimeFormatter.ofPattern(pattern, Locale.ROOT)).toString()
                }.getOrNull()
            }
        }
    }

    private fun persistSupplierInvoice(
        pending: PendingInvoiceImport,
        status: String,
        supplier: String,
        vatNumber: String,
        invoiceNumber: String,
        issueDate: String,
        dueDate: String,
        netAmount: BigDecimal?,
        vatAmount: BigDecimal?,
        totalAmount: BigDecimal,
        warning: String?,
        dialog: AlertDialog
    ) {
        val scope = assistantChatScope()
        val duplicate = supplierInvoiceStore.findDuplicate(scope, supplier, vatNumber, invoiceNumber, issueDate)
        if (duplicate != null) {
            AlertDialog.Builder(this)
                .setTitle("Esta fatura já foi importada")
                .setMessage("Já existe uma fatura com o mesmo fornecedor, número e data de emissão. Não criei um segundo registo.")
                .setNegativeButton("Fechar", null)
                .setPositiveButton("Ver fatura existente") { _, _ ->
                    dialog.dismiss()
                    showSupplierInvoiceDetails(duplicate)
                }
                .show()
            return
        }
        val now = System.currentTimeMillis()
        val id = java.util.UUID.randomUUID().toString()
        val warnings = pending.draft.warnings + listOfNotNull(warning)
        val record = SupplierInvoiceRecord(
            id = id,
            supplierName = supplier,
            supplierVatNumber = vatNumber,
            invoiceNumber = invoiceNumber,
            issueDate = issueDate,
            dueDate = dueDate,
            netAmount = netAmount?.toPlainString(),
            vatAmount = vatAmount?.toPlainString(),
            totalAmount = totalAmount.toPlainString(),
            currency = "EUR",
            mimeType = pending.document.mimeType,
            byteSize = pending.document.byteSize,
            documentSha256 = pending.document.sha256,
            documentUri = pending.uri.toString(),
            documentPath = null,
            sourceType = pending.draft.source,
            reviewStatus = status,
            warnings = warnings,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        try {
            supplierInvoiceStore.save(scope, record)
        } catch (error: Exception) {
            Toast.makeText(this, error.message ?: "Não foi possível guardar a fatura localmente.", Toast.LENGTH_LONG).show()
            return
        }
        dialog.dismiss()
        pendingInvoiceImport = null
        showSupplierInvoiceArchiveDialog()

        val ownerConnected = hasRemoteRestaurant() && backendToken().isNotBlank() &&
            backendPrefs.getString("restaurant_role", "") == "owner"
        if (!ownerConnected) {
            Toast.makeText(
                this,
                if (status == "approved") "Fatura confirmada apenas neste dispositivo. Liga a conta do proprietário para arquivar no servidor."
                else "Fatura guardada neste dispositivo como pendente de revisão.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        runBackend({
            val bytes = InvoiceDocumentProcessor(this).readVerifiedDocumentBytes(pending.uri, pending.document.sha256)
            val objectPath = SupabaseApi.uploadSupplierInvoiceDocument(
                backendToken(), backendRestaurantId(), id, pending.document.mimeType, pending.document.sha256, bytes
            )
            try {
                val row = buildSupplierInvoiceMetadataRow(
                    record, objectPath, backendRestaurantId(), backendPrefs.getString("user_id", "").orEmpty()
                )
                SupabaseApi.insertSupplierInvoice(backendToken(), row)
            } catch (error: Exception) {
                runCatching { SupabaseApi.deleteSupplierInvoiceDocument(backendToken(), objectPath) }
                throw error
            }
            objectPath
        }, onError = { error ->
            Toast.makeText(
                this,
                "A cópia local está guardada, mas a sincronização falhou: ${friendlyBackendError(error.message)}",
                Toast.LENGTH_LONG
            ).show()
        }) { objectPath ->
            supplierInvoiceStore.save(scope, record.copy(documentPath = objectPath, documentUri = null, updatedAtMillis = System.currentTimeMillis()))
            Toast.makeText(this, "Fatura enviada para o arquivo privado do restaurante.", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildSupplierInvoiceMetadataRow(
        record: SupplierInvoiceRecord,
        objectPath: String,
        restaurantId: String,
        userId: String
    ): JSONObject {
        require(restaurantId.isNotBlank() && userId.isNotBlank())
        val row = JSONObject()
            .put("id", record.id)
            .put("restaurant_id", restaurantId)
            .put("supplier_name", record.supplierName)
            .put("supplier_vat_number", record.supplierVatNumber)
            .put("invoice_number", record.invoiceNumber)
            .put("issue_date", record.issueDate)
            .put("due_date", if (record.dueDate.isBlank()) JSONObject.NULL else record.dueDate)
            .put("currency", record.currency)
            .put("net_amount", record.netAmount ?: JSONObject.NULL)
            .put("vat_amount", record.vatAmount ?: JSONObject.NULL)
            .put("total_amount", record.totalAmount)
            .put("review_status", record.reviewStatus)
            .put("source_type", record.sourceType)
            .put("mime_type", record.mimeType)
            .put("byte_size", record.byteSize)
            .put("document_sha256", record.documentSha256)
            .put("document_path", objectPath)
            .put("warnings", JSONArray(record.warnings))
            .put("created_by", userId)
        if (record.reviewStatus != "pending_review") {
            row.put("reviewed_by", userId)
                .put("reviewed_at", java.time.Instant.ofEpochMilli(record.updatedAtMillis).toString())
        }
        return row
    }

    private fun syncPendingSupplierInvoiceUploads() {
        if (!hasRemoteRestaurant() || backendToken().isBlank() ||
            backendPrefs.getString("restaurant_role", "") != "owner") {
            Toast.makeText(this, "Só o proprietário pode sincronizar faturas.", Toast.LENGTH_LONG).show()
            return
        }
        val scope = assistantChatScope()
        val pending = runCatching { supplierInvoiceStore.list(scope) }
            .getOrDefault(emptyList())
            .filter { it.documentPath.isNullOrBlank() && !it.documentUri.isNullOrBlank() }
        runBackend({
            var uploaded = 0
            val failures = mutableListOf<String>()
            pending.forEach { invoice ->
                try {
                    val uri = android.net.Uri.parse(invoice.documentUri)
                    val bytes = InvoiceDocumentProcessor(this).readVerifiedDocumentBytes(uri, invoice.documentSha256)
                    val objectPath = SupabaseApi.uploadSupplierInvoiceDocument(
                        backendToken(), backendRestaurantId(), invoice.id,
                        invoice.mimeType, invoice.documentSha256, bytes
                    )
                    try {
                        val row = buildSupplierInvoiceMetadataRow(
                            invoice, objectPath, backendRestaurantId(),
                            backendPrefs.getString("user_id", "").orEmpty()
                        )
                        SupabaseApi.insertSupplierInvoice(backendToken(), row)
                    } catch (error: Exception) {
                        runCatching { SupabaseApi.deleteSupplierInvoiceDocument(backendToken(), objectPath) }
                        throw error
                    }
                    supplierInvoiceStore.save(
                        scope,
                        invoice.copy(
                            documentPath = objectPath,
                            documentUri = null,
                            updatedAtMillis = System.currentTimeMillis()
                        )
                    )
                    uploaded++
                } catch (error: Exception) {
                    failures += "${invoice.invoiceNumber}: ${error.message ?: "falha de envio"}"
                }
            }
            "Enviadas: $uploaded. Com problemas: ${failures.size}." +
                if (failures.isEmpty()) "" else "\\n" + failures.take(3).joinToString("\\n")
        }, onError = { error ->
            Toast.makeText(this, friendlyBackendError(error.message), Toast.LENGTH_LONG).show()
        }) { summary ->
            Toast.makeText(this, summary, Toast.LENGTH_LONG).show()
            refreshSupplierInvoicesFromCloud(showFeedback = true)
        }
    }

    private fun showSupplierInvoiceDetails(invoice: SupplierInvoiceRecord) {
        val message = buildString {
            append("Fornecedor: ").append(invoice.supplierName).append('\n')
            append("NIF / VAT: ").append(invoice.supplierVatNumber.ifBlank { "Não registado" }).append('\n')
            append("Fatura: ").append(invoice.invoiceNumber).append('\n')
            append("Emissão: ").append(invoice.issueDate).append('\n')
            if (invoice.dueDate.isNotBlank()) append("Vencimento: ").append(invoice.dueDate).append('\n')
            append("Base: ").append(invoice.netAmount ?: "não identificada").append(" €\n")
            append("IVA: ").append(invoice.vatAmount ?: "não identificado").append(" €\n")
            append("Total: ").append(invoice.totalAmount).append(" €\n")
            append("Estado: ").append(invoice.reviewStatus)
            if (invoice.warnings.isNotEmpty()) append("\n\nAvisos:\n• ").append(invoice.warnings.joinToString("\n• "))
            append("\n\nNão é um lançamento contabilístico nem prova de pagamento.")
        }
        val hasAttachment = !invoice.documentUri.isNullOrBlank() || !invoice.documentPath.isNullOrBlank()
        val builder = AlertDialog.Builder(this)
            .setTitle("Fatura · ${invoice.invoiceNumber}")
            .setMessage(message)
            .setNegativeButton("Fechar", null)
        if (invoice.reviewStatus == "pending_review") {
            builder.setPositiveButton("Confirmar fatura") { _, _ ->
                updateSupplierInvoiceReview(invoice, "approved")
            }
        } else {
            builder.setPositiveButton("Eliminar do arquivo") { _, _ ->
                deleteSupplierInvoice(invoice)
            }
        }
        if (hasAttachment) builder.setNeutralButton("Abrir documento original", null)
        val dialog = builder.create()
        dialog.setOnShowListener {
            if (hasAttachment) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    dialog.dismiss()
                    openSupplierInvoiceAttachment(invoice)
                }
            }
        }
        dialog.show()
    }

    private fun openSupplierInvoiceAttachment(invoice: SupplierInvoiceRecord) {
        val localUri = invoice.documentUri
        val remotePath = invoice.documentPath
        if (localUri.isNullOrBlank() && remotePath.isNullOrBlank()) {
            Toast.makeText(this, "O documento original não está disponível.", Toast.LENGTH_LONG).show()
            return
        }
        val remote = localUri.isNullOrBlank()
        if (remote && (!hasRemoteRestaurant() || backendToken().isBlank() ||
                backendPrefs.getString("restaurant_role", "") != "owner")) {
            Toast.makeText(this, "Só o proprietário pode abrir o documento privado do restaurante.", Toast.LENGTH_LONG).show()
            return
        }
        runBackend({
            val bytes = if (!localUri.isNullOrBlank()) {
                val input = contentResolver.openInputStream(android.net.Uri.parse(localUri))
                    ?: throw java.io.FileNotFoundException("O documento local já não está disponível.")
                input.use { stream ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(64 * 1024)
                    var total = 0
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= InvoiceDocumentProcessor.MAX_DOCUMENT_BYTES) {
                            "O documento excede 25 MB."
                        }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            } else {
                SupabaseApi.downloadSupplierInvoiceDocument(backendToken(), requireNotNull(remotePath))
            }
            val actualHash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(bytes).joinToString("") { "%02x".format(it) }
            require(actualHash.equals(invoice.documentSha256, ignoreCase = true)) {
                "A verificação de integridade do documento falhou. Não abri o ficheiro."
            }
            val extension = when (invoice.mimeType) {
                "application/pdf" -> "pdf"
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> throw IllegalArgumentException("Formato do documento não suportado.")
            }
            require(invoice.id.matches(Regex("^[0-9a-fA-F-]{36}$"))) {
                "O identificador do documento não é válido."
            }
            val directory = java.io.File(cacheDir, "invoices").apply {
                if (!exists() && !mkdirs()) {
                    throw IllegalStateException("Não foi possível preparar a cache privada.")
                }
                val expiry = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
                listFiles()?.filter { it.isFile && it.lastModified() < expiry }?.forEach { it.delete() }
            }
            val file = java.io.File(directory, "${invoice.id}.$extension")
            file.writeBytes(bytes)
            require(file.length() == invoice.byteSize || invoice.byteSize <= 0L) {
                "O tamanho do documento não corresponde ao registo guardado."
            }
            file.absolutePath
        }, refreshSession = remote, onError = { error ->
            Toast.makeText(this, friendlyBackendError(error.message), Toast.LENGTH_LONG).show()
        }) { filePath ->
            runCatching {
                val file = java.io.File(filePath)
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                val view = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, invoice.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(view, "Abrir fatura"))
            }.onFailure {
                Toast.makeText(
                    this,
                    "Não existe uma aplicação compatível para abrir este documento.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updateSupplierInvoiceReview(invoice: SupplierInvoiceRecord, status: String) {
        val oldRecord = invoice
        val updated = invoice.copy(reviewStatus = status, updatedAtMillis = System.currentTimeMillis())
        supplierInvoiceStore.save(assistantChatScope(), updated)
        val ownerConnected = hasRemoteRestaurant() && backendToken().isNotBlank() &&
            backendPrefs.getString("restaurant_role", "") == "owner"
        if (ownerConnected && !invoice.documentPath.isNullOrBlank()) {
            runBackend({
                SupabaseApi.updateSupplierInvoiceReview(backendToken(), backendRestaurantId(), invoice.id, status)
                "OK"
            }, onError = { error ->
                supplierInvoiceStore.save(assistantChatScope(), oldRecord)
                Toast.makeText(this, friendlyBackendError(error.message), Toast.LENGTH_LONG).show()
            }) {
                Toast.makeText(this, "Revisão guardada no servidor.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Revisão atualizada apenas neste dispositivo.", Toast.LENGTH_LONG).show()
        }
    }

    private fun deleteSupplierInvoice(invoice: SupplierInvoiceRecord) {
        val ownerConnected = hasRemoteRestaurant() && backendToken().isNotBlank() &&
            backendPrefs.getString("restaurant_role", "") == "owner"
        if (ownerConnected && !invoice.documentPath.isNullOrBlank()) {
            runBackend({
                SupabaseApi.deleteSupplierInvoiceDocument(backendToken(), invoice.documentPath)
                SupabaseApi.deleteSupplierInvoiceMetadata(backendToken(), backendRestaurantId(), invoice.id)
                "OK"
            }, onError = { error ->
                Toast.makeText(this, friendlyBackendError(error.message), Toast.LENGTH_LONG).show()
            }) {
                supplierInvoiceStore.delete(assistantChatScope(), invoice.id)
                showSupplierInvoiceArchiveDialog()
            }
        } else {
            supplierInvoiceStore.delete(assistantChatScope(), invoice.id)
            showSupplierInvoiceArchiveDialog()
        }
    }

    private var supplierInvoiceRefreshInFlight = false
    private var lastSupplierInvoiceRefreshAtMillis = 0L

    private fun refreshSupplierInvoicesFromCloud(showFeedback: Boolean) {
        if (!hasRemoteRestaurant() || backendToken().isBlank() ||
            backendPrefs.getString("restaurant_role", "") != "owner") {
            if (showFeedback) Toast.makeText(this, "Só o proprietário pode atualizar o arquivo partilhado.", Toast.LENGTH_LONG).show()
            return
        }
        if (supplierInvoiceRefreshInFlight) return
        val now = System.currentTimeMillis()
        if (!showFeedback && now - lastSupplierInvoiceRefreshAtMillis < 60_000L) return
        supplierInvoiceRefreshInFlight = true
        lastSupplierInvoiceRefreshAtMillis = now
        runBackend({
            SupabaseApi.listSupplierInvoices(backendToken(), backendRestaurantId()).toString()
        }, onError = { error ->
            supplierInvoiceRefreshInFlight = false
            if (showFeedback) Toast.makeText(this, "O arquivo remoto não está disponível: ${friendlyBackendError(error.message)}", Toast.LENGTH_LONG).show()
        }) { raw ->
            supplierInvoiceRefreshInFlight = false
            val rows = JSONArray(raw)
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val id = row.optString("id")
                if (id.isBlank()) continue
                val warnings = row.optJSONArray("warnings") ?: JSONArray()
                val existing = supplierInvoiceStore.get(assistantChatScope(), id)
                supplierInvoiceStore.save(
                    assistantChatScope(),
                    SupplierInvoiceRecord(
                        id = id,
                        supplierName = row.optString("supplier_name"),
                        supplierVatNumber = row.optString("supplier_vat_number"),
                        invoiceNumber = row.optString("invoice_number"),
                        issueDate = row.optString("issue_date"),
                        dueDate = row.optString("due_date"),
                        netAmount = if (row.isNull("net_amount")) null else row.optString("net_amount"),
                        vatAmount = if (row.isNull("vat_amount")) null else row.optString("vat_amount"),
                        totalAmount = row.optString("total_amount"),
                        currency = row.optString("currency", "EUR"),
                        mimeType = row.optString("mime_type"),
                        byteSize = row.optLong("byte_size"),
                        documentSha256 = row.optString("document_sha256"),
                        documentUri = existing?.documentUri,
                        documentPath = row.optString("document_path").takeIf { it.isNotBlank() },
                        sourceType = row.optString("source_type"),
                        reviewStatus = row.optString("review_status", "pending_review"),
                        warnings = (0 until warnings.length()).map { warnings.optString(it) },
                        createdAtMillis = parseServerTimestamp(row.optString("created_at")),
                        updatedAtMillis = parseServerTimestamp(row.optString("updated_at"))
                    )
                )
            }
            if (showFeedback) Toast.makeText(this, "${rows.length()} fatura(s) no arquivo privado.", Toast.LENGTH_SHORT).show()
            if (activePage in setOf("Assistente económico", "IA") &&
                !assistantSending && !isFinishing && !isDestroyed) {
                renderApp()
            }
        }
    }

    private fun parseServerTimestamp(value: String): Long =
        runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrDefault(0L)

    private fun formatInvoiceEuro(value: BigDecimal): String =
        NumberFormat.getCurrencyInstance(Locale("pt", "PT")).format(value)

    private fun assistantCloudSyncPreferenceKey(): String {
        val ownerId = backendPrefs.getString("user_id", "").orEmpty()
        val restaurantId = backendRestaurantId()
        return "assistant_cloud_sync_enabled_${ownerId}_${restaurantId}"
    }

    private fun assistantCloudSyncEnabled(): Boolean =
        backendPrefs.getBoolean(assistantCloudSyncPreferenceKey(), false)

    private fun setAssistantCloudSyncEnabled(enabled: Boolean) {
        backendPrefs.edit().putBoolean(assistantCloudSyncPreferenceKey(), enabled).apply()
    }

    /**
     * Cloud sync is off by default. The owner must explicitly enable it after
     * seeing that conversation text and memory are stored in Supabase without E2E encryption.
     */
    private fun requestAssistantCloudSync() {
        val ownerConnected = backendToken().isNotBlank() &&
            backendPrefs.getString("user_id", "").orEmpty().isNotBlank() &&
            backendRestaurantId().isNotBlank() &&
            backendPrefs.getString("restaurant_role", "") == "owner"
        if (!ownerConnected) {
            Toast.makeText(
                this,
                "A sincronização está disponível apenas para o proprietário com sessão iniciada.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val enabled = assistantCloudSyncEnabled()
        val builder = AlertDialog.Builder(this)
        if (enabled) {
            builder.setTitle("Sincronização cloud ativa")
                .setMessage(
                    "O histórico e a Memória desta conta estão a ser sincronizados com o Supabase " +
                        "(região EU West 1). A cópia no servidor é protegida por autenticação e RLS, " +
                        "mas não é cifrada ponta a ponta. Se desativares a sincronização, as novas " +
                        "alterações deixam de ser enviadas; os dados já enviados não são apagados automaticamente."
                )
                .setPositiveButton("Sincronizar agora") { _, _ ->
                    syncAssistantCloud(silent = false, force = true)
                }
                .setNeutralButton("Desativar") { _, _ ->
                    setAssistantCloudSyncEnabled(false)
                    Toast.makeText(
                        this,
                        "Novas sincronizações desativadas. A cópia já enviada continua no Supabase até ser eliminada.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                .setNegativeButton("Fechar", null)
        } else {
            builder.setTitle("Ativar sincronização cloud?")
                .setMessage(
                    "A sincronização está desligada por defeito. Se ativares, o histórico de conversas " +
                        "e a Memória desta conta serão enviados para o Supabase (região EU West 1) para " +
                        "poderem ser consultados noutros dispositivos. O acesso é controlado pela sessão " +
                        "do proprietário e por regras RLS, mas a cópia no servidor não é cifrada ponta a ponta. " +
                        "As mensagens novas e alterações da Memória serão sincronizadas automaticamente " +
                        "enquanto a opção estiver ativa. Podes desativá-la mais tarde; os dados já enviados " +
                        "não são apagados automaticamente."
                )
                .setPositiveButton("Aceitar e ativar") { _, _ ->
                    setAssistantCloudSyncEnabled(true)
                    syncAssistantCloud(silent = false, force = true)
                }
                .setNegativeButton("Manter apenas neste dispositivo", null)
        }
        builder.show()
    }

    private fun syncAssistantCloud(silent: Boolean, force: Boolean = false) {
        if (!assistantCloudSyncEnabled()) {
            if (!silent) requestAssistantCloudSync()
            return
        }
        val token = backendToken()
        val userId = backendPrefs.getString("user_id", "").orEmpty()
        val restaurantId = backendRestaurantId()
        val ownerConnected = token.isNotBlank() && userId.isNotBlank() && restaurantId.isNotBlank() &&
            backendPrefs.getString("restaurant_role", "") == "owner"
        if (!ownerConnected) {
            if (!silent) Toast.makeText(this, "A sincronização está disponível apenas para o proprietário com sessão iniciada.", Toast.LENGTH_LONG).show()
            return
        }
        if (assistantCloudSyncInFlight) return
        if (silent && !force && System.currentTimeMillis() - assistantCloudLastSyncAtMillis < 60_000L) return
        val conversationScope = assistantConversationScope()
        assistantCloudSyncInFlight = true
        assistantCloudLastSyncAtMillis = System.currentTimeMillis()
        Thread {
            val result = runCatching {
                assistantCloudSyncCoordinator.sync(token, userId, restaurantId, conversationScope, assistantChatStore)
            }
            runOnUiThread {
                assistantCloudSyncInFlight = false
                result.fold(
                    onSuccess = { sync ->
                        if (!silent) Toast.makeText(
                            this,
                            "Histórico sincronizado: ${sync.conversationsUploaded} conversas enviadas e ${sync.messagesDownloaded} mensagens recebidas.",
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    onFailure = { error ->
                        if (!silent) Toast.makeText(
                            this,
                            "Não foi possível sincronizar. A cópia local continua guardada: ${friendlyBackendError(error.message)}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }.start()
    }


    private fun assistantChatScope(): String {
        val userId = backendPrefs.getString("user_id", "").orEmpty().trim()
        val restaurantId = backendRestaurantId().trim()
        return if (userId.isNotBlank()) {
            "account:$userId:restaurant:${restaurantId.ifBlank { "none" }}"
        } else {
            "demo:${BuildConfig.FLAVOR}"
        }
    }

    private fun assistantConversationScope(economicAssistant: Boolean = activePage == "Assistente económico"): String =
        assistantChatScope() + if (economicAssistant) ":economic" else ""

    private fun renderLocalAssistantModelCard(container: LinearLayout) {
        assistantModelStatusTextView = null
        assistantModelProgressTextView = null
        val modelInstalled = localAssistantModelManager.isModelInstalled()
        val card = makeCard(13, if (modelInstalled) paleGreen else copperPale)
        card.addView(label("MODELO DE IA NO DISPOSITIVO", 9.5f, true, forestDeep))
        addGap(card, 5)
        val status = when {
            assistantModelDownloading || localAssistantModelManager.isDownloading() ->
                "A transferir o modelo: ${((assistantModelProgressBytes * 100) / localAssistantModelManager.modelSizeBytes()).coerceIn(0, 100)}%."
            modelInstalled ->
                "IA generativa instalada. As mensagens e os dados selecionados são processados no próprio telemóvel, sem API e sem cobrança por mensagem."
            else ->
                "O motor generativo local ainda não foi instalado. O assistente de regras continua disponível; transfere o modelo uma única vez para ativar a conversa generativa offline."
        }
        val statusView = label(status, 12f, false, ink)
        assistantModelStatusTextView = statusView
        card.addView(statusView)
        addGap(card, 8)

        if (assistantModelDownloading || localAssistantModelManager.isDownloading()) {
            val progressView = label(
                "Transferido: ${formatModelBytes(assistantModelProgressBytes)} de aproximadamente 736 MB",
                10.5f, true, forest
            )
            assistantModelProgressTextView = progressView
            card.addView(progressView)
        } else if (!modelInstalled) {
            card.addView(secondaryButton("Transferir IA local · 736 MB") {
                AlertDialog.Builder(this)
                    .setTitle("Instalar IA local")
                    .setMessage(
                        "A transferência inicial é de aproximadamente 736 MB e requer Internet. O ficheiro fica guardado na área privada da aplicação. Depois de instalado, o modelo funciona sem Internet e não envia mensagens, memória ou dados do restaurante para servidores. A transferência pode consumir dados móveis; recomenda-se Wi-Fi."
                    )
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Transferir") { _, _ -> startLocalAssistantModelDownload() }
                    .show()
            }, LinearLayout.LayoutParams(-1, dp(44)))
        } else {
            card.addView(label("Modelo LFM2.5 1.2B · quantização INT4 · execução CPU local", 10.5f, true, forest))
            addGap(card, 6)
            card.addView(secondaryButton("Ver modelo e licenças") { showLocalAssistantLicencesDialog() }, LinearLayout.LayoutParams(-1, dp(40)))
        }

        assistantModelError?.let {
            addGap(card, 6)
            card.addView(label(it, 11f, true, red))
        }
        container.addView(card, cardParams())
        if (!modelInstalled) {
            container.addView(secondaryButton("Ver origem e licença do modelo") {
                showLocalAssistantLicencesDialog()
            }, LinearLayout.LayoutParams(-1, dp(40)))
            addGap(container, 6)
        }
    }

    private fun showLocalAssistantLicencesDialog() {
        AlertDialog.Builder(this)
            .setTitle("Modelo e licenças")
            .setMessage(
                "Modelo: LFM2.5-1.2B-Instruct, quantizado INT4 e convertido para LiteRT-LM pela comunidade litert-community.\n\nO modelo é disponibilizado sob a LFM Open License v1.0, com condições de utilização comercial e atribuição. Confirma os termos completos antes de comercializar a StudioMesa.\n\nO runtime LiteRT-LM é distribuído sob Apache 2.0.\n\nO modelo não foi criado pela StudioMesa nem pela Google."
            )
            .setNegativeButton("Fechar", null)
            .setPositiveButton("Abrir licença") { _, _ ->
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct/blob/main/LICENSE")))
                }.onFailure {
                    Toast.makeText(this, "Não foi possível abrir a licença.", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun formatModelBytes(bytes: Long): String {
        val megabytes = bytes.toDouble() / 1_000_000.0
        return String.format(Locale("pt", "PT"), "%.0f MB", megabytes)
    }

    private fun startLocalAssistantModelDownload() {
        if (assistantModelDownloading || localAssistantModelManager.isDownloading()) return
        assistantModelDownloading = true
        assistantModelProgressBytes = 0L
        assistantModelError = null
        renderApp()
        var lastShownPercent = -1
        localAssistantModelManager.downloadModel(
            onProgress = { downloaded, total ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    assistantModelProgressBytes = downloaded
                    val percent = if (total > 0L) ((downloaded * 100) / total).toInt() else 0
                    if (percent != lastShownPercent) {
                        lastShownPercent = percent
                        assistantModelStatusTextView?.text = "A transferir o modelo: $percent%."
                        assistantModelProgressTextView?.text =
                            "Transferido: ${formatModelBytes(downloaded)} de ${formatModelBytes(total)}"
                    }
                }
            },
            onComplete = { result ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    assistantModelDownloading = false
                    result.fold(
                        onSuccess = {
                            assistantModelProgressBytes = localAssistantModelManager.modelSizeBytes()
                            assistantModelError = null
                            Toast.makeText(this, "IA local instalada e verificada.", Toast.LENGTH_LONG).show()
                        },
                        onFailure = { error ->
                            assistantModelError = error.message ?: "Não foi possível instalar o modelo local."
                        }
                    )
                    renderApp()
                }
            }
        )
    }

    private fun buildLocalAssistantSystemInstruction(
        scope: String,
        economicAssistant: Boolean = activePage == "Assistente económico"
    ): String {
        val snapshot = assistantSnapshot()
        val ownerInvoiceAccess = hasRemoteRestaurant() && backendToken().isNotBlank() &&
            backendPrefs.getString("restaurant_role", "") == "owner"
        val restaurantName = backendPrefs.getString("restaurant_name", "").orEmpty()
            .ifBlank { "Restaurante sem nome configurado" }
        val memory = runCatching { assistantChatStore.readMemory(scope) }.getOrDefault("")
        val promptMemory = AssistantAutomaticMemoryPolicy.contextSnippet(memory)

        return buildString {
            appendLine(AssistantGeneralConversationPolicy.SYSTEM_INSTRUCTION)
            if (economicAssistant) {
                appendLine("FUNÇÃO: Assistente económico. Analisa receitas, despesas, custos, tesouraria, compras e fornecedores com os dados disponíveis.")
                appendLine("Consulta get_supplier_invoice_summary apenas para metadados; não lê PDFs/OCR nem prova pagamentos. Nunca alteres ou pagues faturas.")
                appendLine("Usa calculate_restaurant_economics para calcular indicadores mensais, custos por dose, desperdício, orçamentos, consumo, pessoal e reposição. Usa apenas valores explícitos; pergunta pelo que falta. Mostra as entradas usadas e pede confirmação: a origem não é verificada pela ferramenta e os valores não vêm automaticamente do POS ou da contabilidade.")
            } else {
                appendLine("FUNÇÃO: assistente geral. Responde também a assuntos gerais; não transformes todas as perguntas em perguntas de restauração.")
                if (ownerInvoiceAccess) {
                    appendLine("O proprietário pode usar consult_economic_assistant para consultar metadados de faturas. Não abras documentos nem executes operações.")
                } else {
                    appendLine("Esta conta não tem acesso a faturas ou metadados financeiros privados.")
                }
            }
            appendLine("Plano: reservas/preços ${if (hasCalculator) "disponíveis" else "não incluídos"}; stock/análise ${if (isPremium) "disponíveis" else "dependem do Premium"}.")
            appendLine("Para valores atuais, consulta get_restaurant_data ou calculate_menu_price_reference. Usa apenas os resultados das ferramentas; se faltarem dados, pergunta.")
            appendLine("Restaurante: $restaurantName. Modo: ${if (snapshot.isDemo) "demonstração" else "conta autenticada"}.")
            appendLine("Estado: ${snapshot.menuItems.size} artigos; ${snapshot.activeOrderCount} pedidos ativos; stock ${if (snapshot.stockLoaded) "carregado" else "não carregado"}; reservas ${if (snapshot.reservationsLoaded) "carregadas" else "não carregadas"}.")
            if (economicAssistant) {
                appendLine("Totais de faturas são somas de documentos arquivados; não provam pagamento, despesa contabilística ou dedutibilidade.")
            }
            if (promptMemory.isNotBlank()) {
                appendLine("MEMÓRIA RELEVANTE:")
                appendLine(promptMemory)
            }
        }.trim()
    }
    private fun renderAssistantBubble(
        parent: LinearLayout,
        turn: AssistantChatStore.Turn
    ) {
        val isUser = turn.role == "user"
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (isUser) Gravity.END else Gravity.START
            setPadding(0, dp(2), 0, dp(7))
        }
        val bubble = makeCard(13, if (isUser) forest else white)
        bubble.addView(label(
            if (isUser) "TU" else "STUDIOMESA",
            9f,
            true,
            if (isUser) copper else forestSoft
        ))
        addGap(bubble, 5)
        val messageText = label(
            turn.content,
            13f,
            false,
            if (isUser) white else ink
        )
        if (!isUser) {
            messageText.autoLinkMask = Linkify.WEB_URLS
            messageText.movementMethod = LinkMovementMethod.getInstance()
            messageText.linksClickable = true
        }
        bubble.addView(messageText)
        addGap(bubble, 5)
        val time = SimpleDateFormat("HH:mm", Locale("pt", "PT")).format(Date(turn.createdAtMillis))
        bubble.addView(label(time, 9f, false, if (isUser) copperPale else muted).apply {
            gravity = Gravity.END
        })
        val width = (resources.displayMetrics.widthPixels * 0.84f).toInt()
        row.addView(bubble, LinearLayout.LayoutParams(width, -2))
        parent.addView(row, LinearLayout.LayoutParams(-1, -2))
    }



    private fun sendAssistantMessage(rawMessage: String) {
        if (assistantSending) return
        val question = rawMessage.trim().take(4000)
        if (question.isBlank()) {
            Toast.makeText(this, "Escreve uma mensagem primeiro.", Toast.LENGTH_SHORT).show()
            return
        }
        if (localAssistantModelManager.isModelInstalled() &&
            question.length > LocalAssistantModelManager.MAX_USER_MESSAGE_CHARS) {
            Toast.makeText(
                this,
                "Divide a pergunta em mensagens mais curtas (máximo " +
                    LocalAssistantModelManager.MAX_USER_MESSAGE_CHARS + " caracteres com o modelo local).",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val economicAssistant = activePage == "Assistente económico"
        val scope = assistantConversationScope(economicAssistant)
        val turnUsesDeepThinking = assistantDeepThinkingEnabled && localAssistantModelManager.isModelInstalled()
        try {
            val conversationId = assistantChatStore.appendTurn(
                scope,
                activeAssistantConversationId,
                "user",
                question
            )
            activeAssistantConversationId = conversationId
            assistantComposerDraft = ""
            assistantChatAutoFollow = true
            assistantChatError = null

            if (localAssistantModelManager.isModelInstalled()) {
                assistantSending = true
                renderApp()
                val previousTurns = assistantChatStore.readTurns(scope, conversationId)
                    .dropLast(1)
                    .takeLast(LocalAssistantModelManager.MAX_HISTORY_MESSAGES)
                    .map { turn -> LocalAssistantModelManager.Turn(turn.role, turn.content) }

                localAssistantModelManager.generate(
                    conversationId = conversationId,
                    systemInstruction = buildLocalAssistantSystemInstruction(scope, economicAssistant),
                    snapshot = assistantSnapshot(),
                    history = previousTurns,
                    userMessage = question,
                    deepThinking = turnUsesDeepThinking,
                    economicMode = economicAssistant,
                    economicBridgeEnabled = !economicAssistant && hasRemoteRestaurant() &&
                        backendToken().isNotBlank() &&
                        backendPrefs.getString("restaurant_role", "") == "owner",
                    onToken = { token ->
                        runOnUiThread {
                            assistantStreamingTextView?.append(token)
                        }
                    },
                    onComplete = { result ->
                        runOnUiThread {
                            assistantSending = false
                            assistantStreamingTextView = null
                            result.fold(
                                onSuccess = { reply ->
                                    runCatching {
                                        assistantChatStore.appendTurn(scope, conversationId, "assistant", reply)
                                        syncAssistantCloud(silent = true)
                                    }.onFailure { error ->
                                        assistantChatError = error.message ?: "A resposta chegou, mas não foi possível guardar o histórico."
                                    }
                                },
                                onFailure = { error ->
                                    assistantChatError = error.message ?: "O modelo local não conseguiu responder."
                                }
                            )
                            renderApp()
                        }
                    }
                )
                return
            }

            val reply = answerAssistantChatMessage(question, scope, conversationId, economicAssistant)
            assistantChatStore.appendTurn(scope, conversationId, "assistant", reply)
            syncAssistantCloud(silent = true)
        } catch (error: Exception) {
            assistantSending = false
            assistantStreamingTextView = null
            assistantChatError = error.message ?: "Não foi possível guardar ou responder à mensagem."
        }
        renderApp()
    }



    private fun answerAssistantChatMessage(
        question: String,
        scope: String,
        conversationId: String,
        economicAssistant: Boolean = activePage == "Assistente económico"
    ): String {
        val normalized = java.text.Normalizer.normalize(
            question.lowercase(Locale.ROOT),
            java.text.Normalizer.Form.NFD
        ).replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val memory = assistantChatStore.readMemory(scope)
        AssistantCapabilitiesPolicy.answer(
            question,
            AssistantCapabilitiesPolicy.Features(
                hasReservationsAndPricing = hasCalculator,
                isPremium = isPremium,
                localModelInstalled = localAssistantModelManager.isModelInstalled(),
                canAccessInvoices = economicAssistant || (
                    hasRemoteRestaurant() && backendToken().isNotBlank() &&
                        backendPrefs.getString("restaurant_role", "") == "owner"
                )
            )
        )?.let { return it }

        val asksAboutMemory = listOf(
            "o que sabes", "o que te lembras", "do que te lembras",
            "o que guardaste", "qual e a minha memoria", "mostra a memoria"
        ).any { normalized.contains(it) } || normalized == "memoria"
        if (asksAboutMemory) {
            return if (memory.isBlank()) {
                "Ainda não guardaste notas na Memória do assistente. Usa o botão «Memória» para registar informação útil sobre o restaurante."
            } else {
                "Estas são as notas guardadas na Memória deste dispositivo:\n\n$memory"
            }
        }

        val asksAboutInvoices = listOf(
            "fatura", "faturas", "factura", "facturas", "saft", "documento contabilistico",
            "fatura de fornecedor", "despesa de fornecedor"
        ).any { normalized.contains(it) }
        if (asksAboutInvoices && !economicAssistant) {
            return "Para consultar faturas, fornecedores e indicadores financeiros do restaurante, abre a secção «Assistente económico»."
        }
        if (asksAboutInvoices && economicAssistant) {
            val invoices = runCatching { supplierInvoiceStore.list(assistantChatScope()) }.getOrDefault(emptyList())
            if (invoices.isEmpty()) {
                return "O arquivo ainda não tem faturas importadas. Abre Documentos e faturas para importar um PDF ou uma fotografia. A leitura é feita localmente e os campos ficam pendentes até os confirmares."
            }
            val approved = invoices.filter { it.reviewStatus == "approved" }
            val pending = invoices.filter { it.reviewStatus == "pending_review" }
            val approvedTotal = approved.fold(BigDecimal.ZERO) { sum, item ->
                sum.add(item.totalAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO)
            }
            val pendingTotal = pending.fold(BigDecimal.ZERO) { sum, item ->
                sum.add(item.totalAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO)
            }
            return buildString {
                appendLine("Resumo do arquivo de fornecedores")
                appendLine("Documentos guardados: ${invoices.size}.")
                appendLine("Confirmados pelo proprietário: ${approved.size} · total ${formatInvoiceEuro(approvedTotal)}.")
                appendLine("Pendentes de revisão: ${pending.size} · total ${formatInvoiceEuro(pendingTotal)}.")
                appendLine("Estes totais representam documentos importados, não pagamentos efetuados, custos reconhecidos ou valores fiscalmente dedutíveis.")
                val latest = invoices.take(5)
                if (latest.isNotEmpty()) {
                    appendLine()
                    appendLine("Documentos recentes:")
                    latest.forEach { item ->
                        appendLine("- ${item.supplierName}: ${item.invoiceNumber}, ${item.issueDate}, ${formatInvoiceEuro(item.totalAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO)} (${item.reviewStatus}).")
                    }
                }
            }.trim()
        }


        val previousUserQuestion = runCatching {
            assistantChatStore.readTurns(scope, conversationId)
                .dropLast(1)
                .lastOrNull { it.role == "user" }?.content
        }.getOrNull()
        val isShortFollowUp = normalized.length <= 35 && listOf(
            "e como", "e porque", "porquê", "explica melhor", "mais detalhes",
            "e depois", "e isso", "como assim"
        ).any { normalized.startsWith(it) }
        val questionForLocalEngine = if (isShortFollowUp && !previousUserQuestion.isNullOrBlank()) {
            "$previousUserQuestion. Seguimento: $question"
        } else {
            question
        }

        val answer = StudioMesaAdvisor.answerQuestion(questionForLocalEngine, assistantSnapshot())
        val contextNote = if (memory.isNotBlank() && listOf(
            "restaurante", "negocio", "empresa", "equipa", "objetivo", "preferencia"
        ).any { normalized.contains(it) }) {
            "\n\nNota da Memória: $memory"
        } else {
            ""
        }
        return "${answer.title}\n\n${answer.response}$contextNote"
    }



    private fun showAssistantHistoryDialog() {
        val scope = assistantConversationScope()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(4))
        }
        val search = inputField(
            "Pesquisar conversas…",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        )
        panel.addView(search, fieldParams())
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            addView(results)
        }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, dp(380)))

        val dialog = AlertDialog.Builder(this)
            .setTitle("Histórico de conversas")
            .setView(panel)
            .setNegativeButton("Fechar", null)
            .create()

        fun refreshResults(query: String) {
            results.removeAllViews()
            val conversations = runCatching {
                assistantChatStore.listConversations(scope, query)
            }.getOrElse {
                results.addView(emptyCard("Não foi possível abrir o histórico cifrado. Os dados não foram apagados."))
                return
            }
            if (conversations.isEmpty()) {
                results.addView(emptyCard(
                    if (query.isBlank()) "Ainda não existem conversas guardadas."
                    else "Não encontrei conversas com esse texto."
                ))
                return
            }
            conversations.forEach { conversation ->
                val card = makeCard(12)
                card.addView(label(conversation.title, 14f, true, forestDeep))
                addGap(card, 4)
                val date = SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale("pt", "PT"))
                    .format(Date(conversation.updatedAtMillis))
                card.addView(label(
                    "$date · ${conversation.messageCount} mensagens",
                    10.5f, false, muted
                ))
                card.setOnClickListener {
                    activeAssistantConversationId = conversation.id
                    assistantChatAutoFollow = true
                    assistantChatScrollY = 0
                    assistantChatError = null
                    dialog.dismiss()
                    renderApp()
                }
                card.setOnLongClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("Eliminar conversa?")
                        .setMessage("Esta conversa será removida deste dispositivo e, quando a sincronização estiver disponível, dos restantes dispositivos associados ao restaurante.")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Eliminar") { _, _ ->
                            runCatching {
                                assistantChatStore.deleteConversation(scope, conversation.id)
                                syncAssistantCloud(silent = true, force = true)
                            }.onFailure {
                                Toast.makeText(this, "Não foi possível eliminar a conversa.", Toast.LENGTH_SHORT).show()
                            }
                            if (activeAssistantConversationId == conversation.id) {
                                activeAssistantConversationId = null
                            }
                            refreshResults(search.text.toString())
                        }
                        .show()
                    true
                }
                results.addView(card, cardParams())
            }
        }

        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refreshResults(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        refreshResults("")
        dialog.show()
    }



    private fun showAssistantMemoryDialog() {
        val scope = assistantConversationScope()
        val memory = runCatching { assistantChatStore.readMemory(scope) }.getOrDefault("")
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        form.addView(label(
            "O assistente guarda automaticamente preferências, objetivos e contexto estável que exprimes claramente. Também podes rever ou editar estas notas. Evita palavras-passe, contactos, dados bancários e informação pessoal desnecessária.",
            12f, false, muted
        ))
        addGap(form, 7)
        val memoryField = multilineInputField("Ex.: tipo de restaurante, objetivos, regras da casa e preferências…").apply {
            minLines = 6
            maxLines = 9
            filters = filters + InputFilter.LengthFilter(2500)
            setText(memory)
        }
        form.addView(memoryField, LinearLayout.LayoutParams(-1, dp(160)))

        AlertDialog.Builder(this)
            .setTitle("Memória do assistente")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Apagar memória", null)
            .setPositiveButton("Guardar") { _, _ ->
                runCatching {
                    assistantChatStore.saveMemory(scope, memoryField.text.toString())
                }.onSuccess {
                    Toast.makeText(this, "Memória guardada neste dispositivo.", Toast.LENGTH_SHORT).show()
                    syncAssistantCloud(silent = true)
                }.onFailure {
                    Toast.makeText(this, "Não foi possível guardar a memória.", Toast.LENGTH_SHORT).show()
                }
            }
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Apagar memória?")
                            .setMessage("As notas guardadas serão eliminadas deste dispositivo.")
                            .setNegativeButton("Cancelar", null)
                            .setPositiveButton("Apagar") { _, _ ->
                                runCatching { assistantChatStore.saveMemory(scope, "") }
                                memoryField.setText("")
                                dismiss()
                                Toast.makeText(this@MainActivity, "Memória apagada.", Toast.LENGTH_SHORT).show()
                                syncAssistantCloud(silent = true)
                            }
                            .show()
                    }
                }
                show()
            }
    }



    private fun renderAssistantToolsScreen() {
        appRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(paper)
        }
        applySystemBarInsets(appRoot)

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(7), dp(12), dp(7))
            setBackgroundColor(white)
            elevation = dp(2).toFloat()
        }
        val backButton = TextView(this).apply {
            text = "‹"
            textSize = 31f
            gravity = Gravity.CENTER
            setTextColor(forestDeep)
            contentDescription = "Voltar à conversa"
            background = rippleBackground(paper, 14)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                assistantToolsVisible = false
                renderApp()
            }
        }
        toolbar.addView(backButton, LinearLayout.LayoutParams(dp(46), dp(48)))
        val titleStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, 0, 0)
        }
        titleStack.addView(label("Assistente económico", 16f, true, forestDeep))
        titleStack.addView(label("Indicadores e documentos", 10f, false, muted))
        toolbar.addView(titleStack, LinearLayout.LayoutParams(0, -2, 1f))
        appRoot.addView(toolbar, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        scroll.addView(content)
        appRoot.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        sectionHeading(content, "Ferramentas económicas", "Analisa indicadores, custos e faturas importadas. Os cálculos são indicativos e as operações continuam sob controlo do proprietário.")
        renderEconomicInvoiceSummary(content)
        addGap(content, 8)
        renderAssistant(content, showPageIntro = false)

        setContentView(appRoot)
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
            "Assistente económico" -> {
                renderEconomicInvoiceSummary(container)
                renderEconomicManager(container)
            }
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
        if ("IA" in availablePages()) {
            actionCard(container, "IA", "IA StudioMesa", "Conversa geral para aprender, escrever, programar e resolver problemas.", "ABRIR IA") {
                assistantToolsVisible = false
                activePage = "IA"; renderApp()
            }
        }
        if (!hasRemoteRestaurant() || backendPrefs.getString("restaurant_role", "").orEmpty() == "owner") {
            actionCard(container, "€", "Assistente económico", "Analisa custos, tesouraria e faturas importadas do restaurante.", "ABRIR ASSISTENTE ECONÓMICO") {
                assistantToolsVisible = false
                activePage = "Assistente económico"; renderApp()
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
                val role = restaurant.optString("user_role", "staff")
                currentStaffPermissions = if (role == "owner") {
                    StaffPermissionSet.all.toSet()
                } else {
                    StaffPermissionSet.fromJson(SupabaseApi.getMyRestaurantPermissions(token))
                }
                cacheRemoteOperations(token, restaurant.optString("restaurant_id"))
                if ("inventory" !in currentStaffPermissions.orEmpty()) {
                    backendPrefs.edit().remove("remote_inventory_json").remove("inventory_loaded").apply()
                }
                if ("reservations" !in currentStaffPermissions.orEmpty()) {
                    backendPrefs.edit().remove("remote_reservations_json").remove("reservations_loaded").apply()
                    reservationCache = JSONArray()
                    reservationsLoaded = false
                }
                if ("loyalty" !in currentStaffPermissions.orEmpty()) {
                    backendPrefs.edit().remove("remote_members_json").remove("restaurant_rewards_json").apply()
                }
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
                    .apply()
                reservationCache = JSONArray()
                reservationsLoaded = false
            }
        } catch (_: Exception) {
            // Keep authentication successful if restoring cloud data temporarily fails,
            // but fail closed for staff navigation until permissions can be reloaded.
            if (backendPrefs.getString("restaurant_role", "") == "staff") {
                currentStaffPermissions = emptySet()
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
            val isStaff = backendPrefs.getString("restaurant_role", "").orEmpty() == "staff"
            if (isStaff) {
                currentStaffPermissions = StaffPermissionSet.fromJson(
                    SupabaseApi.getMyRestaurantPermissions(backendToken())
                )
            } else {
                currentStaffPermissions = StaffPermissionSet.all.toSet()
            }
            cacheRemoteOperations(backendToken(), restaurantId)
            if (isStaff) {
                if ("inventory" !in currentStaffPermissions.orEmpty()) {
                    backendPrefs.edit().remove("remote_inventory_json").remove("inventory_loaded").apply()
                }
                if ("reservations" !in currentStaffPermissions.orEmpty()) {
                    backendPrefs.edit().remove("remote_reservations_json").remove("reservations_loaded").apply()
                    reservationCache = JSONArray()
                    reservationsLoaded = false
                }
                if ("loyalty" !in currentStaffPermissions.orEmpty()) {
                    backendPrefs.edit().remove("remote_members_json").remove("restaurant_rewards_json").apply()
                }
            }
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
                    .putString("customer_rewards_json", "[]")
                    .apply()
                customerReservationsCache = JSONArray()
                customerRedemptionHistory = JSONArray()
                return
            }

            val memberId = member.optString("member_id")
            val menu = if (memberId.isNotBlank()) SupabaseApi.listCustomerMenu(token, memberId) else org.json.JSONArray()
            val rewards = if (memberId.isNotBlank()) SupabaseApi.listCustomerRewards(token, memberId) else JSONArray()
            val redemptions = if (memberId.isNotBlank()) SupabaseApi.listMyLoyaltyRewardRedemptions(token) else JSONArray()
            val reservations = if (memberId.isNotBlank()) SupabaseApi.listMyRestaurantReservations(token) else JSONArray()
            customerRedemptionHistory = redemptions
            customerReservationsCache = reservations
            backendPrefs.edit()
                .putString("member_id", memberId)
                .putString("member_code", member.optString("member_code"))
                .putInt("member_points", member.optInt("points", 0))
                .putString("member_restaurant_name", member.optString("restaurant_name"))
                .putString("customer_menu_json", menu.toString())
                .putString("customer_rewards_json", rewards.toString())
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
            .remove("customer_rewards_json")
            .remove("remote_inventory_json").remove("inventory_loaded")
            .remove("remote_reservations_json").remove("reservations_loaded")
            .apply()

        currentStaffPermissions = null
        reservationCache = JSONArray()
        customerReservationsCache = JSONArray()
        customerRedemptionHistory = JSONArray()
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
                "menu" to "Carta e preços",
                "inventory" to "Stock e inventário",
                "loyalty" to "Fidelização operacional"
            )
            val checks = linkedMapOf<String, android.widget.CheckBox>()
            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(8), dp(18), dp(8))
            }
            content.addView(label(
                "Escolhe as áreas a que $email pode aceder. As alterações são verificadas no servidor.",
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
                "A gestão da equipa, códigos de acesso, loja de recompensas, consultor económico e definições do restaurante continuam reservados ao proprietário.",
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
                        SupabaseApi.setRestaurantStaffPermissions(
                            backendToken(), userId, selected
                        )
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
                .remove("customer_rewards_json")
                .remove("customer_redemptions_json")
                .remove("remote_inventory_json").remove("inventory_loaded")
                .remove("remote_reservations_json").remove("reservations_loaded")
                .apply()
            currentStaffPermissions = null
            reservationCache = JSONArray()
            reservationsLoaded = false
            renderApp()
        }, LinearLayout.LayoutParams(-1, dp(48)))
        addGap(container, 10)
        container.addView(label("As funções de gestão e os dados de cada restaurante são controlados por permissões no servidor.", 12f, false, muted))
        addGap(container, 8)
        container.addView(secondaryButton("Privacidade e dados") {
            showPrivacyInformation()
        }, LinearLayout.LayoutParams(-1, dp(48)))
    }

    private fun showPrivacyInformation() {
        val notice = """
            A SUA PRIVACIDADE É UMA PRIORIDADE

            OS SEUS DADOS NÃO ESTÃO À VENDA.
            O StudioMesa não vende dados pessoais nem utiliza informações privadas dos restaurantes para publicidade própria.
            Os seus dados são tratados apenas para as finalidades explicadas nesta informação de privacidade e pelos prestadores necessários ao funcionamento do serviço.
            O seu negócio é seu. Os seus dados merecem respeito e proteção.

            DADOS DA CONTA E DO RESTAURANTE
            Para disponibilizar as funcionalidades, dados como conta, carta, mesas, pedidos, reservas, stock e fidelização podem ser guardados no Supabase, na região EU West 1. O acesso é limitado por autenticação, permissões e regras de segurança no servidor. Os dados de clientes introduzidos pelo restaurante também podem ser tratados para executar essas funcionalidades.

            ASSISTENTE DE INTELIGÊNCIA ARTIFICIAL
            O modelo LFM2.5-1.2B-Instruct é desenvolvido e publicado por terceiros, descarregado para o armazenamento privado da aplicação e executado localmente no dispositivo. As perguntas e os dados usados na inferência local não são enviados ao fornecedor do modelo. A função de IA na nuvem do StudioMesa está desativada e não recebe mensagens.

            HISTÓRICO E MEMÓRIA
            O histórico e a Memória são cifrados localmente com uma chave do Android Keystore. A sincronização cloud está desligada por defeito. Só é ativada quando o proprietário a aceita no controlo de sincronização. Nesse caso, conversas e Memória são enviadas para o Supabase para utilização noutros dispositivos. A cópia cloud é protegida por autenticação e RLS, mas não é cifrada ponta a ponta. Desativar a sincronização impede novos envios, mas não elimina automaticamente cópias já enviadas.

            DOCUMENTOS E FATURAS
            A leitura de PDFs/imagens e o OCR são processados localmente. O texto bruto extraído por OCR não é guardado pelo arquivo. Quando o proprietário escolhe arquivar uma fatura no servidor, o documento original e os respetivos metadados são enviados para um bucket privado do Supabase. O acesso ao arquivo está limitado ao proprietário do restaurante. O documento não é enviado ao fornecedor do modelo de IA.

            PESQUISA PÚBLICA
            Quando utilizas a pesquisa pública na Web, a consulta é enviada ao serviço de pesquisa configurado para obter resultados. A função de pesquisa rejeita consultas que pareçam conter emails, números de telefone, NIF ou IBAN, mas este filtro não deteta todos os dados confidenciais. Nunca incluas nomes de clientes, contactos, faturas, valores privados ou informação interna do restaurante numa pesquisa pública.

            FORNECEDORES E SEGURANÇA
            O Supabase é utilizado para autenticação, base de dados e arquivo cloud. O alojamento público do modelo é utilizado apenas para descarregar o ficheiro do modelo, não para receber perguntas. Nenhuma medida elimina totalmente o risco de incidente; o StudioMesa aplica controlos de acesso, cifragem local e regras de segurança no servidor.

            ELIMINAÇÃO
            Podes iniciar a eliminação em Conta → Eliminar conta permanentemente. Existe também uma página externa:
            https://svwvkfsyevuyabelrtjk.supabase.co/functions/v1/account-deletion-web

            A eliminação de uma conta de proprietário pode apagar permanentemente o restaurante e os dados associados, depois de confirmação e validação pelo servidor. A conta de um funcionário/cliente pode ser eliminada sem apagar os registos operacionais partilhados; a autoria pode ser anonimizada quando suportado.

            ESTADO LEGAL
            ${if (BuildConfig.DEBUG) "Esta instalação é uma versão de demonstração. Não introduzas dados pessoais ou documentos reais." else "A política de privacidade pública final, a identidade e contacto do responsável pelo tratamento, os prazos de conservação e a declaração Data safety têm de estar aprovados e publicados antes da utilização comercial."}
        """.trimIndent()

        val styledNotice = android.text.SpannableString(notice).apply {
            val privacyHeading = "A SUA PRIVACIDADE É UMA PRIORIDADE"
            val noSaleHeading = "OS SEUS DADOS NÃO ESTÃO À VENDA."
            val privacyStart = notice.indexOf(privacyHeading)
            val noSaleStart = notice.indexOf(noSaleHeading)
            if (privacyStart >= 0) {
                setSpan(
                    android.text.style.StyleSpan(Typeface.BOLD),
                    privacyStart,
                    privacyStart + privacyHeading.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    android.text.style.RelativeSizeSpan(1.25f),
                    privacyStart,
                    privacyStart + privacyHeading.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            if (noSaleStart >= 0) {
                setSpan(
                    android.text.style.StyleSpan(Typeface.BOLD),
                    noSaleStart,
                    noSaleStart + noSaleHeading.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    android.text.style.RelativeSizeSpan(1.15f),
                    noSaleStart,
                    noSaleStart + noSaleHeading.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        val textView = TextView(this).apply {
            text = styledNotice
            textSize = 13.5f
            setTextColor(ink)
            setPadding(dp(18), dp(14), dp(18), dp(14))
            autoLinkMask = android.text.util.Linkify.WEB_URLS
            linksClickable = true
            setMovementMethod(android.text.method.LinkMovementMethod.getInstance())
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            addView(textView)
        }
        AlertDialog.Builder(this)
            .setTitle("Privacidade e dados")
            .setView(scroll)
            .setPositiveButton("Fechar", null)
            .setNeutralButton("Abrir Conta") { _, _ ->
                if (isCustomerApp) {
                    activeCustomerPage = "Conta"
                    renderCustomerApp()
                } else {
                    activePage = "Conta"
                    renderApp()
                }
            }
            .show()
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
            currentStaffPermissions = null
            reservationCache = JSONArray()
            customerReservationsCache = JSONArray()
            customerRedemptionHistory = JSONArray()
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
            val token = backendToken()
            val members = SupabaseApi.listMembers(token, restaurantId)
            val rewards = SupabaseApi.listRestaurantRewards(token, restaurantId)
            val redemptions = SupabaseApi.listRestaurantRewardRedemptions(token, restaurantId)
            backendPrefs.edit()
                .putString("remote_members_json", members.toString())
                .putString("restaurant_rewards_json", rewards.toString())
                .putString("remote_reward_redemptions_json", redemptions.toString())
                .apply()
            members.length().toString()
        }) { count ->
            if (activePage == "Pontos") renderApp()
            AlertDialog.Builder(this).setTitle("Lista atualizada")
                .setMessage(count + " cartões de cliente encontrados no servidor.")
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
        pageIntro(container, "Fidelização por pontos", "Cartões, loja de recompensas e atribuição segura através de QR.")
        addGap(container, 9)
        val remoteMode = backendToken().isNotBlank() && backendRestaurantId().isNotBlank()
        val isOwner = backendPrefs.getString("restaurant_role", "staff") == "owner"
        val notice = makeCard(14, copperPale)
        notice.addView(label(if (remoteMode) "FIDELIZAÇÃO PARTILHADA E PROTEGIDA" else "MODO DE DEMONSTRAÇÃO", 10f, true, forest))
        addGap(notice, 5)
        notice.addView(label(
            if (remoteMode) "O cliente apresenta um QR temporário. Cada QR só credita pontos uma vez e o servidor valida o funcionário e o restaurante."
            else "No modo local, os dados ficam apenas neste telemóvel. Liga uma conta e configura o restaurante para ativar a fidelização partilhada.",
            12.5f, false, ink
        ))
        container.addView(notice, cardParams())
        if (remoteMode) {
            container.addView(primaryButton("Ler QR e atribuir pontos") { startLoyaltyQrScan() }, LinearLayout.LayoutParams(-1, dp(48)))
            addGap(container, 6)
            container.addView(secondaryButton("Atualizar cartões e loja") { refreshRemoteCustomers() }, LinearLayout.LayoutParams(-1, dp(48)))
            addGap(container, 6)
            container.addView(secondaryButton("Ver código de adesão") { showRestaurantJoinCode() }, LinearLayout.LayoutParams(-1, dp(48)))
        } else {
            container.addView(primaryButton("+  Adicionar cliente (local)") { showAddCustomerDialog() }, LinearLayout.LayoutParams(-1, dp(48)))
        }

        if (remoteMode) {
            addGap(container, 14)
            container.addView(label("LOJA DE PONTOS", 10f, true, forestSoft))
            addGap(container, 5)
            container.addView(label("Recompensas do restaurante", 20f, true, forestDeep))
            if (isOwner) {
                addGap(container, 7)
                container.addView(primaryButton("+ Criar recompensa") { showLoyaltyRewardEditor(null) }, LinearLayout.LayoutParams(-1, dp(44)))
            }
            val rewards = try { JSONArray(backendPrefs.getString("restaurant_rewards_json", "[]") ?: "[]") }
                catch (_: Exception) { JSONArray() }
            if (rewards.length() == 0) {
                addGap(container, 6)
                container.addView(emptyCard("Ainda não existem recompensas. O proprietário pode criar prémios e definir quantos pontos custam."))
            } else {
                for (i in 0 until rewards.length()) {
                    val reward = rewards.optJSONObject(i) ?: continue
                    val card = makeCard(13)
                    val isActive = reward.optBoolean("is_active", false)
                    card.addView(label(reward.optString("name"), 15f, true, forestDeep))
                    addGap(card, 3)
                    card.addView(label(reward.optInt("points_cost").toString() + " pontos", 11f, true, forestSoft))
                    val description = reward.optString("description").trim()
                    if (description.isNotBlank()) {
                        addGap(card, 3)
                        card.addView(label(description, 12f, false, muted))
                    }
                    val limit = if (reward.isNull("stock_limit")) -1 else reward.optInt("stock_limit", -1)
                    val redeemed = reward.optInt("redeemed_count", 0)
                    addGap(card, 5)
                    card.addView(label(
                        if (!isActive) "Inativa"
                        else if (limit < 0) redeemed.toString() + " resgates · sem limite de stock"
                        else redeemed.toString() + " / " + limit + " resgates",
                        10f, true, if (isActive) green else red
                    ))
                    if (isOwner) {
                        addGap(card, 8)
                        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                        actions.addView(secondaryButton("Editar") { showLoyaltyRewardEditor(reward) },
                            LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(0, 0, dp(4), 0) })
                        if (isActive) {
                            actions.addView(secondaryButton("Desativar") { setLoyaltyRewardActive(reward, false) },
                                LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(4), 0, 0, 0) })
                        }
                        card.addView(actions)
                    }
                    container.addView(card, cardParams())
                }
            }

            addGap(container, 14)
            container.addView(label("RESGATES POR ENTREGAR", 10f, true, forestSoft))
            addGap(container, 5)
            val redemptions = try { JSONArray(backendPrefs.getString("remote_reward_redemptions_json", "[]") ?: "[]") }
                catch (_: Exception) { JSONArray() }
            var pendingCount = 0
            for (i in 0 until redemptions.length()) {
                val item = redemptions.optJSONObject(i) ?: continue
                if (item.optString("status") != "pending") continue
                pendingCount++
                val card = makeCard(12)
                card.addView(label(item.optString("reward_name"), 14f, true, forestDeep))
                addGap(card, 3)
                card.addView(label(item.optString("customer_name") + " · " + item.optInt("points_spent") + " pontos", 11f, false, muted))
                addGap(card, 8)
                card.addView(primaryButton("Confirmar entrega") {
                    runBackend({
                        SupabaseApi.fulfillLoyaltyRewardRedemption(backendToken(), item.optString("redemption_id"))
                        val updated = SupabaseApi.listRestaurantRewardRedemptions(backendToken(), backendRestaurantId())
                        backendPrefs.edit().putString("remote_reward_redemptions_json", updated.toString()).apply()
                        "OK"
                    }) { renderApp() }
                }, LinearLayout.LayoutParams(-1, dp(42)))
                container.addView(card, cardParams())
            }
            if (pendingCount == 0) container.addView(label("Não existem recompensas pendentes de entrega.", 12f, false, muted))
        }

        addGap(container, 14)
        container.addView(label("CARTÕES DE CLIENTE", 10f, true, forestSoft))
        addGap(container, 5)
        val customers = if (remoteMode) loadRemoteCustomers() else store.getCustomers()
        if (customers.isEmpty()) {
            container.addView(emptyCard("Ainda não há cartões de fidelização associados a este restaurante."))
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
            if (!remoteMode) {
                addGap(card, 12)
                val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                actions.addView(secondaryButton("+ Atribuir") { showCustomerPointsDialog(customer, false) },
                    LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(0, 0, dp(5), 0) })
                actions.addView(secondaryButton("− Descontar") { showCustomerPointsDialog(customer, true) },
                    LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(5), 0, 0, 0) })
                card.addView(actions)
            }
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

    private fun startLoyaltyQrScan() {
        if (backendToken().isBlank() || backendRestaurantId().isBlank()) {
            Toast.makeText(this, "Inicia sessão no restaurante antes de ler QR.", Toast.LENGTH_LONG).show()
            return
        }
        IntentIntegrator(this)
            .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            .setPrompt("Aponta a câmara ao QR temporário do cliente")
            .setBeepEnabled(false)
            .setOrientationLocked(false)
            .initiateScan()
    }

    private fun handleLoyaltyQrPayload(payload: String) {
        val prefix = "STUDIOMESA-LOYALTY:"
        if (!payload.startsWith(prefix, ignoreCase = true)) {
            Toast.makeText(this, "Este QR não é um código de fidelização StudioMesa.", Toast.LENGTH_LONG).show()
            return
        }
        val qrToken = payload.substring(prefix.length).trim()
        if (runCatching { java.util.UUID.fromString(qrToken) }.isFailure) {
            Toast.makeText(this, "O QR não é válido.", Toast.LENGTH_LONG).show()
            return
        }
        val pointsField = inputField("Pontos a atribuir nesta compra", InputType.TYPE_CLASS_NUMBER)
        val noteField = inputField("Referência da compra (opcional)", InputType.TYPE_CLASS_TEXT)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        form.addView(label("PONTOS", 10f, true, forestSoft))
        form.addView(pointsField, fieldParams())
        addGap(form, 5)
        form.addView(label("REFERÊNCIA", 10f, true, forestSoft))
        form.addView(noteField, fieldParams())
        val dialog = AlertDialog.Builder(this)
            .setTitle("Atribuir pontos por QR")
            .setMessage("O QR é de utilização única e expira em dois minutos.")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Confirmar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val points = pointsField.text.toString().trim().toIntOrNull()
                val note = noteField.text.toString().trim()
                when {
                    points == null || points !in 1..100000 -> pointsField.error = "Indica entre 1 e 100000 pontos."
                    note.length > 200 -> noteField.error = "A referência não pode exceder 200 caracteres."
                    else -> {
                        val signature = JSONObject()
                            .put("qr_token", qrToken)
                            .put("points", points)
                            .put("note", note)
                            .toString()
                        val operation = "loyalty_qr_award"
                        val accountId = backendPrefs.getString("user_id", "").orEmpty()
                        val requestId = try {
                            pendingRequestIds.getOrCreate(operation, accountId, signature)
                        } catch (_: Exception) {
                            pointsField.error = "Não foi possível guardar a referência segura. Tenta novamente."
                            return@setOnClickListener
                        }
                        runBackend({
                            val result = SupabaseApi.awardLoyaltyPointsFromQr(
                                backendToken(), qrToken, points, note, requestId
                            )
                            val members = SupabaseApi.listMembers(backendToken(), backendRestaurantId())
                            backendPrefs.edit().putString("remote_members_json", members.toString()).apply()
                            result.optInt("points", -1).toString()
                        }) { newBalance ->
                            dialog.dismiss()
                            pendingRequestIds.complete(operation, accountId, signature, requestId)
                            renderApp()
                            AlertDialog.Builder(this).setTitle("Pontos atribuídos")
                                .setMessage("O novo saldo do cliente é " + newBalance + " pontos.")
                                .setPositiveButton("OK", null).show()
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showLoyaltyRewardEditor(existing: JSONObject?) {
        if (backendToken().isBlank() || backendRestaurantId().isBlank() ||
            backendPrefs.getString("restaurant_role", "") != "owner") {
            AlertDialog.Builder(this).setMessage("Só o proprietário pode gerir a loja de pontos.")
                .setPositiveButton("OK", null).show()
            return
        }
        val name = inputField("Nome da recompensa", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        val description = inputField("Descrição (opcional)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        val cost = inputField("Pontos necessários", InputType.TYPE_CLASS_NUMBER)
        val stock = inputField("Quantidade disponível (vazio = sem limite)", InputType.TYPE_CLASS_NUMBER)
        if (existing != null) {
            name.setText(existing.optString("name"))
            description.setText(existing.optString("description"))
            cost.setText(existing.optInt("points_cost").toString())
            if (!existing.isNull("stock_limit")) stock.setText(existing.optInt("stock_limit").toString())
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        form.addView(label("PRÉMIO", 10f, true, forestSoft))
        form.addView(name, fieldParams())
        addGap(form, 5)
        form.addView(label("DESCRIÇÃO", 10f, true, forestSoft))
        form.addView(description, fieldParams())
        addGap(form, 5)
        form.addView(label("CUSTO EM PONTOS", 10f, true, forestSoft))
        form.addView(cost, fieldParams())
        addGap(form, 5)
        form.addView(label("STOCK", 10f, true, forestSoft))
        form.addView(stock, fieldParams())
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Nova recompensa" else "Editar recompensa")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val rewardName = name.text.toString().trim()
                val rewardDescription = description.text.toString().trim()
                val points = cost.text.toString().trim().toIntOrNull()
                val limitText = stock.text.toString().trim()
                val stockLimit = if (limitText.isBlank()) null else limitText.toIntOrNull()
                when {
                    rewardName.isBlank() || rewardName.length > 100 -> name.error = "Indica um nome até 100 caracteres."
                    rewardDescription.length > 500 -> description.error = "A descrição não pode exceder 500 caracteres."
                    points == null || points !in 1..1000000 -> cost.error = "Indica um custo entre 1 e 1000000 pontos."
                    limitText.isNotBlank() && (stockLimit == null || stockLimit < 0) -> stock.error = "Indica uma quantidade válida ou deixa vazio."
                    else -> {
                        val rewardId = existing?.optString("id")?.takeIf { it.isNotBlank() }
                            ?: java.util.UUID.randomUUID().toString()
                        runBackend({
                            SupabaseApi.saveLoyaltyReward(
                                backendToken(), backendRestaurantId(), rewardId, rewardName,
                                rewardDescription, points, stockLimit, true
                            )
                            val rewards = SupabaseApi.listRestaurantRewards(backendToken(), backendRestaurantId())
                            backendPrefs.edit().putString("restaurant_rewards_json", rewards.toString()).apply()
                            "OK"
                        }) {
                            dialog.dismiss()
                            renderApp()
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun setLoyaltyRewardActive(reward: JSONObject, active: Boolean) {
        val rewardId = reward.optString("id")
        val stock = if (reward.isNull("stock_limit")) null else reward.optInt("stock_limit")
        runBackend({
            SupabaseApi.saveLoyaltyReward(
                backendToken(), backendRestaurantId(), rewardId,
                reward.optString("name"), reward.optString("description"),
                reward.optInt("points_cost"), stock, active
            )
            val rewards = SupabaseApi.listRestaurantRewards(backendToken(), backendRestaurantId())
            backendPrefs.edit().putString("restaurant_rewards_json", rewards.toString()).apply()
            "OK"
        }) { renderApp() }
    }

    private fun showCustomerPointsDialog(customer: LoyaltyCustomer, redeem: Boolean) {
        if (backendToken().isNotBlank() && customer.backendId != null) {
            Toast.makeText(this, "Na conta online, os pontos só podem ser atribuídos através do QR temporário.", Toast.LENGTH_LONG).show()
            return
        }
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
                            val rewards = SupabaseApi.listCustomerRewards(token, memberId)
                            backendPrefs.edit().putString("member_id", memberId)
                                .putString("member_code", member.optString("member_code"))
                                .putInt("member_points", member.optInt("points", 0))
                                .putString("member_restaurant_name", member.optString("restaurant_name"))
                                .putString("customer_menu_json", menu.toString())
                                .putString("customer_rewards_json", rewards.toString())
                                .apply()
                            customerRedemptionHistory = SupabaseApi.listMyLoyaltyRewardRedemptions(token)
                            customerReservationsCache = SupabaseApi.listMyRestaurantReservations(token)
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
            val rewards = SupabaseApi.listCustomerRewards(token, memberId)
            val redemptions = SupabaseApi.listMyLoyaltyRewardRedemptions(token)
            val reservations = SupabaseApi.listMyRestaurantReservations(token)
            customerRedemptionHistory = redemptions
            customerReservationsCache = reservations
            backendPrefs.edit().putInt("member_points", member.optInt("points", 0))
                .putString("member_code", member.optString("member_code"))
                .putString("customer_menu_json", menu.toString())
                .putString("customer_rewards_json", rewards.toString())
                .apply()
            "OK"
        }) { renderCustomerApp() }
    }

    private fun renderCustomerApp() {
        if (activeCustomerPage !in setOf("Cartão", "Carta", "Reservas", "Conta")) activeCustomerPage = "Cartão"
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
        header.addView(label(displayCustomerRestaurantName(), 23f, true, white).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        addGap(header, 4)
        header.addView(label("Cartão digital de cliente · " + planTitle, 10.5f, true, copperPale))
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
        listOf("Cartão", "Carta", "Reservas", "Conta").forEach { page ->
            val selected = activeCustomerPage == page
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(3), dp(3), dp(3))
                background = rippleBackground(if (selected) paleGreen else white, 12)
                contentDescription = "Abrir secção " + page
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    activeCustomerPage = page
                    renderCustomerApp()
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

    private fun renderCustomerLoyaltyPage(container: LinearLayout) {
        val savedName = store.getCustomerProfileName()
        container.addView(label(if (savedName.isBlank()) "Olá!" else "Olá, " + savedName + "!", 25f, true, forestDeep))
        addGap(container, 5)
        container.addView(label("Cartão de fidelização do restaurante.", 13.5f, false, muted))
        addGap(container, 12)
        when {
            backendToken().isBlank() -> container.addView(primaryButton("Criar conta / iniciar sessão") { showAuthChooser { renderCustomerApp() } }, LinearLayout.LayoutParams(-1, dp(48)))
            backendMemberId().isBlank() -> container.addView(primaryButton("Associar ao restaurante") { showCustomerJoinDialog() }, LinearLayout.LayoutParams(-1, dp(48)))
            else -> container.addView(primaryButton("Atualizar saldo e recompensas") { refreshCustomerMembership() }, LinearLayout.LayoutParams(-1, dp(48)))
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
        loyalty.addView(label(
            if (backendMemberId().isBlank()) "Este é um cartão demonstrativo. Cria sessão e associa-o ao código do restaurante para consultar os pontos reais."
            else "O saldo pertence à tua conta. O QR temporário só pode ser utilizado uma vez para creditar pontos.",
            12f, false, Color.rgb(229, 235, 228)
        ))
        container.addView(loyalty, cardParams())
        if (backendToken().isNotBlank() && backendMemberId().isNotBlank()) {
            addGap(container, 8)
            container.addView(primaryButton("Mostrar QR para acumular pontos") { showCustomerLoyaltyQr() },
                LinearLayout.LayoutParams(-1, dp(48)))
            addGap(container, 14)
            renderCustomerRewards(container)
            renderCustomerRedemptionHistory(container)
        }
        addGap(container, 5)
        container.addView(secondaryButton("Consultar a carta") {
            activeCustomerPage = "Carta"
            renderCustomerApp()
        }, LinearLayout.LayoutParams(-1, dp(48)))
    }

    private fun renderCustomerRewards(container: LinearLayout) {
        container.addView(label("LOJA DE PONTOS", 10f, true, forestSoft))
        addGap(container, 4)
        container.addView(label("Troca pontos por recompensas.", 21f, true, forestDeep))
        addGap(container, 8)
        val rewards = try { JSONArray(backendPrefs.getString("customer_rewards_json", "[]") ?: "[]") }
            catch (_: Exception) { JSONArray() }
        if (rewards.length() == 0) {
            container.addView(emptyCard("Este restaurante ainda não publicou recompensas disponíveis."))
            return
        }
        for (i in 0 until rewards.length()) {
            val reward = rewards.optJSONObject(i) ?: continue
            val rewardId = reward.optString("id")
            val cost = reward.optInt("points_cost", 0)
            val stockLimit = if (reward.isNull("stock_limit")) -1 else reward.optInt("stock_limit", -1)
            val redeemed = reward.optInt("redeemed_count", 0)
            val remaining = if (stockLimit < 0) -1 else (stockLimit - redeemed).coerceAtLeast(0)
            val card = makeCard(14)
            card.addView(label(reward.optString("name"), 16f, true, forestDeep))
            val description = reward.optString("description").trim()
            if (description.isNotBlank()) {
                addGap(card, 4)
                card.addView(label(description, 12f, false, muted))
            }
            addGap(card, 7)
            val stockText = if (remaining < 0) "Disponível" else "Restam " + remaining
            card.addView(label(cost.toString() + " pontos · " + stockText, 11f, true, forestSoft))
            val currentPoints = backendPrefs.getInt("member_points", 0)
            addGap(card, 8)
            if (remaining != 0 && cost > 0 && currentPoints >= cost) {
                card.addView(primaryButton("Resgatar por " + cost + " pontos") {
                    confirmLoyaltyRewardRedemption(rewardId, reward.optString("name"), cost)
                }, LinearLayout.LayoutParams(-1, dp(44)))
            } else {
                val message = when {
                    remaining == 0 -> "Esgotado"
                    cost > currentPoints -> "Faltam " + (cost - currentPoints) + " pontos"
                    else -> "Indisponível"
                }
                card.addView(label(message, 11f, true, muted))
            }
            container.addView(card, cardParams())
        }
    }

    private fun renderCustomerRedemptionHistory(container: LinearLayout) {
        container.addView(label("OS MEUS RESGATES", 10f, true, forestSoft))
        addGap(container, 5)
        val redemptions = customerRedemptionHistory
        if (redemptions.length() == 0) {
            container.addView(label("Ainda não resgataste nenhuma recompensa.", 12f, false, muted))
            return
        }
        for (i in 0 until redemptions.length()) {
            val item = redemptions.optJSONObject(i) ?: continue
            val fulfilled = item.optString("status") == "fulfilled"
            val card = makeCard(12)
            card.addView(label(item.optString("reward_name"), 15f, true, forestDeep))
            addGap(card, 4)
            card.addView(label(
                item.optInt("points_spent").toString() + " pontos · " +
                    if (fulfilled) "Entregue" else "A aguardar entrega",
                11f, true, if (fulfilled) green else forestSoft
            ))
            val redeemedAt = item.optString("redeemed_at").replace("T", " ").take(16)
            if (redeemedAt.isNotBlank()) {
                addGap(card, 3)
                card.addView(label("Pedido em " + redeemedAt, 10f, false, muted))
            }
            container.addView(card, cardParams())
        }
    }

    private fun showCustomerLoyaltyQr() {
        if (backendToken().isBlank() || backendMemberId().isBlank()) {
            Toast.makeText(this, "Associa primeiro o teu cartão ao restaurante.", Toast.LENGTH_LONG).show()
            return
        }
        runBackend({
            SupabaseApi.createLoyaltyQrToken(backendToken()).optString("qr_token")
        }) { qrToken ->
            try {
                val hints = hashMapOf<EncodeHintType, Any>(EncodeHintType.MARGIN to 1)
                val matrix = MultiFormatWriter().encode(
                    "STUDIOMESA-LOYALTY:" + qrToken,
                    BarcodeFormat.QR_CODE, dp(260), dp(260), hints
                )
                val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
                for (y in 0 until matrix.height) {
                    for (x in 0 until matrix.width) {
                        bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                    }
                }
                val content = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(16), dp(10), dp(16), dp(12))
                }
                content.addView(ImageView(this).apply {
                    setImageBitmap(bitmap)
                    contentDescription = "QR temporário de fidelização"
                    adjustViewBounds = true
                }, LinearLayout.LayoutParams(dp(260), dp(260)))
                addGap(content, 8)
                content.addView(label("Mostra este código ao funcionário antes de terminar a compra.", 13f, true, forestDeep))
                addGap(content, 5)
                content.addView(label("Válido durante dois minutos e apenas para uma atribuição.", 11f, false, muted))
                AlertDialog.Builder(this).setTitle("O teu QR de pontos").setView(content)
                    .setPositiveButton("Fechar", null).show()
            } catch (_: Exception) {
                Toast.makeText(this, "Não foi possível gerar a imagem QR. Tenta novamente.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmLoyaltyRewardRedemption(rewardId: String, rewardName: String, pointsCost: Int) {
        AlertDialog.Builder(this)
            .setTitle("Resgatar recompensa?")
            .setMessage("Vais trocar " + pointsCost + " pontos por «" + rewardName + "». O desconto é confirmado pelo servidor.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Resgatar") { _, _ ->
                val signature = JSONObject().put("reward_id", rewardId).toString()
                val operation = "loyalty_reward_redeem"
                val accountId = backendPrefs.getString("user_id", "").orEmpty()
                val requestId = try {
                    pendingRequestIds.getOrCreate(operation, accountId, signature)
                } catch (_: Exception) {
                    Toast.makeText(this, "Não foi possível guardar a referência segura do resgate.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                runBackend({
                    val result = SupabaseApi.redeemLoyaltyReward(backendToken(), rewardId, requestId)
                    val member = SupabaseApi.getMyLoyaltyMembership(backendToken())
                    val rewards = SupabaseApi.listCustomerRewards(backendToken(), backendMemberId())
                    val redemptions = SupabaseApi.listMyLoyaltyRewardRedemptions(backendToken())
                    customerRedemptionHistory = redemptions
                    backendPrefs.edit()
                        .putInt("member_points", member?.optInt("points", result.optInt("points_remaining", 0)) ?: 0)
                        .putString("customer_rewards_json", rewards.toString())
                        .apply()
                    result.optInt("points_remaining", -1).toString()
                }) { remaining ->
                    pendingRequestIds.complete(operation, accountId, signature, requestId)
                    renderCustomerApp()
                    AlertDialog.Builder(this).setTitle("Recompensa reservada")
                        .setMessage("O restaurante já recebeu o resgate. Mostra o teu cartão à equipa para receberes «" + rewardName + "». Saldo: " + remaining + " pontos.")
                        .setPositiveButton("OK", null).show()
                }
            }.show()
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

    private fun refreshCustomerReservations() {
        if (backendToken().isBlank() || backendMemberId().isBlank()) {
            showCustomerJoinDialog()
            return
        }
        runBackend({
            customerReservationsCache = SupabaseApi.listMyRestaurantReservations(backendToken())
            "OK"
        }) { renderCustomerApp() }
    }

    private fun renderCustomerReservationsPage(container: LinearLayout) {
        pageIntro(container, "As minhas reservas", "Pedidos de reserva e respetiva confirmação pelo restaurante.")
        addGap(container, 10)
        when {
            backendToken().isBlank() -> {
                container.addView(emptyCard("Inicia sessão para pedir e consultar reservas."))
                addGap(container, 8)
                container.addView(primaryButton("Criar conta / iniciar sessão") {
                    showAuthChooser { renderCustomerApp() }
                }, LinearLayout.LayoutParams(-1, dp(48)))
                return
            }
            backendMemberId().isBlank() -> {
                container.addView(emptyCard("Associa a tua conta ao restaurante para poderes pedir uma reserva."))
                addGap(container, 8)
                container.addView(primaryButton("Associar ao restaurante") { showCustomerJoinDialog() },
                    LinearLayout.LayoutParams(-1, dp(48)))
                return
            }
        }
        container.addView(primaryButton("+ Pedir uma reserva") { showCustomerReservationDialog() },
            LinearLayout.LayoutParams(-1, dp(48)))
        addGap(container, 6)
        container.addView(secondaryButton("Atualizar pedidos") { refreshCustomerReservations() },
            LinearLayout.LayoutParams(-1, dp(46)))
        addGap(container, 12)

        if (customerReservationsCache.length() == 0) {
            container.addView(emptyCard("Ainda não tens pedidos de reserva."))
            return
        }
        val displayFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "PT"))
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        for (i in 0 until customerReservationsCache.length()) {
            val item = customerReservationsCache.optJSONObject(i) ?: continue
            val reservationId = item.optString("reservation_id")
            val status = item.optString("status", "Pendente")
            val dateText = runCatching {
                displayFormat.format(isoFormat.parse(item.optString("reservation_at")) ?: Date())
            }.getOrElse { item.optString("reservation_at").replace('T', ' ').take(16) }
            val tableNumber = item.optInt("table_number", 0)
            val card = makeCard(15)
            card.addView(label(dateText, 16f, true, forestDeep))
            addGap(card, 4)
            card.addView(label(
                item.optInt("party_size", 1).toString() + " pessoa(s) · " +
                    if (tableNumber > 0) "Mesa " + tableNumber else "Mesa por atribuir",
                12f, false, muted
            ))
            addGap(card, 7)
            card.addView(label(
                status, 11f, true,
                when (status) {
                    "Confirmada" -> green
                    "Cancelada", "Concluída", "Não compareceu" -> muted
                    else -> forestSoft
                }
            ))
            if (status == "Pendente") {
                addGap(card, 4)
                card.addView(label("Pedido enviado. Aguarda a confirmação do restaurante.", 11f, false, muted))
            }
            val notes = item.optString("notes").trim()
            if (notes.isNotBlank()) {
                addGap(card, 4)
                card.addView(label("Notas: " + notes, 11f, false, muted))
            }
            if (status == "Pendente" || status == "Confirmada") {
                addGap(card, 8)
                card.addView(secondaryButton("Cancelar pedido") {
                    AlertDialog.Builder(this)
                        .setTitle("Cancelar reserva?")
                        .setMessage("Queres cancelar este pedido de reserva?")
                        .setNegativeButton("Manter", null)
                        .setPositiveButton("Cancelar") { _, _ ->
                            runBackend({
                                SupabaseApi.cancelMyRestaurantReservation(backendToken(), reservationId)
                                customerReservationsCache = SupabaseApi.listMyRestaurantReservations(backendToken())
                                "OK"
                            }) { renderCustomerApp() }
                        }
                        .show()
                }, LinearLayout.LayoutParams(-1, dp(44)))
            }
            container.addView(card, cardParams())
        }
    }

    private fun showCustomerReservationDialog() {
        if (backendToken().isBlank() || backendMemberId().isBlank()) {
            AlertDialog.Builder(this)
                .setMessage("Inicia sessão e associa primeiro a tua conta ao restaurante.")
                .setPositiveButton("OK", null).show()
            return
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(6))
        }
        val guest = inputField("Nome", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
            .apply { setText(store.getCustomerProfileName()) }
        val phone = inputField("Contacto telefónico (opcional)", InputType.TYPE_CLASS_PHONE)
        val party = inputField("Número de pessoas", InputType.TYPE_CLASS_NUMBER).apply { setText("2") }
        val date = inputField("dd/MM/aaaa HH:mm", InputType.TYPE_CLASS_DATETIME).apply {
            val calendar = java.util.Calendar.getInstance()
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 19)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            setText(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "PT")).format(calendar.time))
        }
        val notes = multilineInputField("Notas (opcional)")
        listOf(
            "Nome para a reserva" to guest,
            "Telefone" to phone,
            "Pessoas" to party,
            "Data e hora" to date,
            "Notas" to notes
        ).forEach { (caption, field) ->
            form.addView(label(caption, 10f, true, forestSoft))
            form.addView(field, if (field === notes) multilineFieldParams() else fieldParams())
            addGap(form, 5)
        }
        val scroll = ScrollView(this).apply { addView(form) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Pedir uma reserva")
            .setMessage("O pedido fica pendente até o restaurante confirmar a mesa.")
            .setView(scroll)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Enviar pedido", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = guest.text.toString().trim()
                val phoneText = phone.text.toString().trim()
                val partySize = party.text.toString().trim().toIntOrNull()
                val parsedDate = runCatching {
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "PT")).apply { isLenient = false }
                        .parse(date.text.toString().trim())
                }.getOrNull()
                val notesText = notes.text.toString().trim()
                when {
                    name.isBlank() || name.length > 100 -> guest.error = "Indica um nome até 100 caracteres."
                    phoneText.length > 40 -> phone.error = "O contacto não pode exceder 40 caracteres."
                    partySize == null || partySize !in 1..60 -> party.error = "Indica entre 1 e 60 pessoas."
                    parsedDate == null || parsedDate.time <= System.currentTimeMillis() -> date.error = "Indica uma data futura válida."
                    parsedDate.time > System.currentTimeMillis() + 365L * 24L * 60L * 60L * 1000L -> date.error = "Escolhe uma data dentro do próximo ano."
                    notesText.length > 1000 -> notes.error = "Máximo de 1000 caracteres."
                    else -> {
                        val reservationAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(parsedDate)
                        val signature = JSONObject()
                            .put("guest_name", name)
                            .put("guest_phone", phoneText)
                            .put("party_size", partySize)
                            .put("reservation_at", reservationAt)
                            .put("duration_minutes", 90)
                            .put("notes", notesText)
                            .toString()
                        val operation = "customer_reservation_request"
                        val accountId = backendPrefs.getString("user_id", "").orEmpty()
                        val requestId = try {
                            pendingRequestIds.getOrCreate(operation, accountId, signature)
                        } catch (_: Exception) {
                            notes.error = "Não foi possível guardar a referência segura. Tenta novamente."
                            return@setOnClickListener
                        }
                        runBackend({
                            SupabaseApi.createCustomerReservationRequest(
                                backendToken(), requestId, name, phoneText, partySize,
                                reservationAt, 90, notesText
                            )
                            customerReservationsCache = SupabaseApi.listMyRestaurantReservations(backendToken())
                            "OK"
                        }) {
                            dialog.dismiss()
                            pendingRequestIds.complete(operation, accountId, signature, requestId)
                            activeCustomerPage = "Reservas"
                            renderCustomerApp()
                            AlertDialog.Builder(this)
                                .setTitle("Pedido enviado")
                                .setMessage("O pedido ficou pendente. O restaurante irá confirmar a disponibilidade e a mesa.")
                                .setPositiveButton("OK", null).show()
                        }
                    }
                }
            }
        }
        dialog.show()
        dialog.window?.setLayout(-1, dp(600))
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
                    .remove("customer_rewards_json")
                    .apply()
                customerRedemptionHistory = JSONArray()
                customerReservationsCache = JSONArray()
                activeCustomerPage = "Cartão"
                renderCustomerApp()
            }, LinearLayout.LayoutParams(-1, dp(48)))
        }
        addGap(container, 8)
        container.addView(secondaryButton("Privacidade e dados") {
            showPrivacyInformation()
        }, LinearLayout.LayoutParams(-1, dp(48)))
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
            val token = backendToken()
            val restaurantId = backendRestaurantId()
            val reservations = SupabaseApi.listReservations(token, restaurantId)
            val tables = SupabaseApi.listTables(token, restaurantId)
            reservationCache = reservations
            backendPrefs.edit().putString("remote_tables_json", tables.toString()).apply()
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
            when (status) {
                "Pendente" -> {
                    if (tableNo <= 0) {
                        card.addView(primaryButton("Atribuir mesa") {
                            showReservationTableAssignmentDialog(id, reservation.optInt("party_size", 1))
                        }, LinearLayout.LayoutParams(-1, dp(48)))
                        addGap(card, 5)
                        card.addView(label("Atribui uma mesa antes de confirmares este pedido.", 11f, false, muted))
                    } else {
                        card.addView(primaryButton("Confirmar reserva") {
                            setReservationStatus(id, "Confirmada")
                        }, LinearLayout.LayoutParams(-1, dp(48)))
                    }
                }
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

    private fun showReservationTableAssignmentDialog(reservationId: String, partySize: Int) {
        val tables = remoteTables()
        val eligible = mutableListOf<JSONObject>()
        for (i in 0 until tables.length()) {
            val table = tables.optJSONObject(i) ?: continue
            if (table.optInt("seats", 0) >= partySize) eligible.add(table)
        }
        if (eligible.isEmpty()) {
            AlertDialog.Builder(this)
                .setMessage("Não há mesas configuradas com lugares suficientes. Adiciona ou ajusta uma mesa primeiro.")
                .setPositiveButton("OK", null).show()
            return
        }
        val labels = eligible.map { table ->
            "Mesa " + table.optInt("table_number") + " · " +
                table.optString("area", "Sala principal") + " · " +
                table.optInt("seats") + " lugares"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Atribuir mesa ao pedido")
            .setItems(labels) { _, which ->
                val tableNumber = eligible[which].optInt("table_number")
                runBackend({
                    SupabaseApi.assignRestaurantReservationTable(backendToken(), reservationId, tableNumber)
                    val latest = SupabaseApi.listReservations(backendToken(), backendRestaurantId())
                    reservationCache = latest
                    reservationsLoaded = true
                    "OK"
                }) { renderApp() }
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
            salesLines = salesLines,
            supplierInvoices = runCatching {
                supplierInvoiceStore.list(assistantChatScope())
            }.getOrDefault(emptyList())
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

    private fun renderAssistant(container: LinearLayout, showPageIntro: Boolean = true) {
        val snapshot = assistantSnapshot()
        if (showPageIntro) {
            pageIntro(container, "Assistente StudioMesa", "Apoio à operação com os dados que já estão disponíveis.")
            addGap(container, 8)
        }

        assistantAnswer?.let { answer ->
            val answerCard = makeCard(16, copperPale)
            answerCard.addView(label(answer.title, 17f, true, forestDeep))
            addGap(answerCard, 7)
            answerCard.addView(label(answer.response, 12.5f, false, ink))
            assistantActionTarget(answer.actionPage)?.let { target ->
                addGap(answerCard, 10)
                answerCard.addView(secondaryButton("Abrir " + target) {
                    activePage = target
                    renderApp()
                }, LinearLayout.LayoutParams(-1, dp(44)))
            }
            container.addView(answerCard, cardParams())
            addGap(container, 6)
        }

        val privacyCard = makeCard(15, forest)
        privacyCard.addView(label("ASSISTENTE LOCAL · SEM API PAGA", 10f, true, copper))
        addGap(privacyCard, 5)
        privacyCard.addView(label(
            "Os indicadores e cálculos desta área são locais e não usam API paga. A conversa com o modelo generativo está no ecrã principal do Assistente económico; estes controlos apenas analisam e não alteram registos.",
            12.5f, false, white
        ))
        container.addView(privacyCard, cardParams())

        addGap(container, 9)
        renderEconomicManager(container)

        if (isPremium) {
            addGap(container, 9)
            renderRestaurantPriceAdvisor(container)
        }

        addGap(container, 5)
        sectionHeading(container, "Pergunta ao assistente", "Experimenta perguntar sobre custos, preços, pedidos, stock, reservas ou um problema de sincronização.")
        val questionField = multilineInputField("Ex.: Como posso melhorar o preço dos pratos?")
        questionField.setText(assistantQuestion)
        container.addView(questionField, multilineFieldParams())
        container.addView(primaryButton("Analisar pergunta") {
            assistantQuestion = questionField.text.toString().trim().take(400)
            assistantAnswer = StudioMesaAdvisor.answerQuestion(assistantQuestion, snapshot)
            renderApp()
        }, LinearLayout.LayoutParams(-1, dp(48)))
        addGap(container, 5)
        container.addView(secondaryButton("Gerar resumo da operação") {
            // Keep the user's question intact; the briefing is a separate action.
            assistantAnswer = StudioMesaAdvisor.dailyBriefing(assistantSnapshot())
            renderApp()
        }, LinearLayout.LayoutParams(-1, dp(46)))
        addGap(container, 12)

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


    private fun renderEconomicInvoiceSummary(container: LinearLayout) {
        val invoices = runCatching { supplierInvoiceStore.list(assistantChatScope()) }.getOrDefault(emptyList())
        val approved = invoices.filter { it.reviewStatus == "approved" }
        val pending = invoices.filter { it.reviewStatus == "pending_review" }
        val rejected = invoices.filter { it.reviewStatus == "rejected" }
        fun total(records: List<SupplierInvoiceRecord>): BigDecimal =
            records.fold(BigDecimal.ZERO) { sum, invoice ->
                sum.add(invoice.totalAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO)
            }

        sectionHeading(container, "Faturas de fornecedores", "A IA económica pode consultar estes metadados depois de importares e reveres os documentos.")
        val card = makeCard(14)
        card.addView(label("${invoices.size} documentos arquivados", 16f, true, forestDeep))
        addGap(card, 5)
        card.addView(label(
            "Confirmados: ${approved.size} · ${formatInvoiceEuro(total(approved))}\n" +
                "Pendentes de revisão: ${pending.size} · ${formatInvoiceEuro(total(pending))}\n" +
                "Rejeitados: ${rejected.size} · ${formatInvoiceEuro(total(rejected))}",
            12f, false, ink
        ))
        addGap(card, 6)
        card.addView(label(
            "A IA lê fornecedor, número, datas, valores base/IVA/total e estado. Não lê o PDF completo nem as linhas de artigos. Uma fatura confirmada não prova pagamento nem validação contabilística.",
            10.5f, false, muted
        ))
        addGap(card, 8)
        card.addView(secondaryButton("Abrir arquivo de faturas") {
            showSupplierInvoiceArchiveDialog()
        }, LinearLayout.LayoutParams(-1, dp(44)))
        container.addView(card, cardParams())
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
        addGap(container, 8)
        container.addView(
            primaryButton("Exportar histórico completo em CSV") { exportOrdersCsv() },
            LinearLayout.LayoutParams(-1, dp(48))
        )
        addGap(container, 6)
        container.addView(label(
            "Inclui o histórico de pedidos disponível no servidor ou neste dispositivo. Não inclui dados pessoais de clientes e não substitui um relatório contabilístico.",
            11.5f, false, muted
        ))
    }

    private fun exportOrdersCsv() {
        if (hasRemoteRestaurant()) {
            runBackend({
                val token = backendToken()
                val restaurantId = backendRestaurantId()
                require(token.isNotBlank() && restaurantId.isNotBlank()) {
                    "Inicia sessão e seleciona um restaurante antes de exportar."
                }
                val orders = SupabaseApi.listOrders(token, restaurantId)
                val records = (0 until orders.length()).mapNotNull { index ->
                    val order = orders.optJSONObject(index) ?: return@mapNotNull null
                    val amount = order.optDouble("amount", Double.NaN)
                    require(amount.isFinite() && amount >= 0.0) {
                        "O relatório contém um preço inválido. Corrige o pedido antes de exportar."
                    }
                    OrderReportRecord(
                        orderId = order.optString("id"),
                        createdAt = reportDate(parseOrderTimestamp(order.optString("created_at"))),
                        tableNumber = order.optInt("table_number", 0),
                        itemName = order.optString("item_name", "Artigo"),
                        quantity = order.optInt("quantity", 0),
                        unitPrice = BigDecimal.valueOf(amount),
                        status = order.optString("status", "Desconhecido")
                    )
                }
                if (records.isEmpty()) "" else ReportCsvExporter.toCsv(records)
            }, onError = { error ->
                AlertDialog.Builder(this)
                    .setTitle("Relatório não preparado")
                    .setMessage(friendlyBackendError(error.message))
                    .setPositiveButton("OK", null)
                    .show()
            }) { csv ->
                if (csv.isBlank()) showNoOrdersForExport() else launchCsvDocumentPicker(csv)
            }
            return
        }

        try {
            val records = store.getOrders().map { order ->
                require(order.amount.isFinite() && order.amount >= 0.0) {
                    "O relatório contém um preço inválido. Corrige o pedido antes de exportar."
                }
                OrderReportRecord(
                    orderId = order.id.toString(),
                    createdAt = reportDate(order.createdAt),
                    tableNumber = order.table,
                    itemName = order.itemName,
                    quantity = order.quantity,
                    unitPrice = BigDecimal.valueOf(order.amount),
                    status = order.status
                )
            }
            if (records.isEmpty()) {
                showNoOrdersForExport()
                return
            }
            launchCsvDocumentPicker(ReportCsvExporter.toCsv(records))
        } catch (error: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Relatório não preparado")
                .setMessage(error.message ?: "Verifica os dados dos pedidos e tenta novamente.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun showNoOrdersForExport() {
        AlertDialog.Builder(this)
            .setTitle("Sem pedidos para exportar")
            .setMessage("Cria ou sincroniza pelo menos um pedido antes de gerar o relatório.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun launchCsvDocumentPicker(csv: String) {
        try {
            pendingCsvExport = csv
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val picker = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/csv"
                putExtra(Intent.EXTRA_TITLE, "studiomesa-pedidos-$date.csv")
            }
            startActivityForResult(picker, REQUEST_EXPORT_ORDERS_CSV)
        } catch (error: Exception) {
            pendingCsvExport = null
            AlertDialog.Builder(this)
                .setTitle("Relatório não preparado")
                .setMessage(error.message ?: "Não foi possível abrir o seletor de ficheiros. Tenta novamente.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun reportDate(epochMillis: Long): String {
        if (epochMillis <= 0L) return ""
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(epochMillis))
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
                                    quantity, serviceCourse, notes, requestId, menuItemId
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
            val stock = SupabaseApi.listInventoryItems(token, restaurantId)
            val recipeId = recipe?.optString("id").orEmpty()
            val ingredients = if (recipeId.isNotBlank()) {
                SupabaseApi.listRecipeIngredients(token, recipeId)
            } else JSONArray()
            JSONObject()
                .put("recipe", recipe ?: JSONObject.NULL)
                .put("ingredients", ingredients)
                .put("stock", stock)
                .toString()
        }) { raw ->
            showRecipeDialogContent(menuItemId, menuItemName, JSONObject(raw))
        }
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
            "Usa a mesma unidade do inventário (kg com kg, L com L ou unidades com unidades). Não há conversões automáticas. Alterar quantidades, mudar a unidade ou eliminar um ingrediente exige nova revisão e aprovação do proprietário; enquanto a revisão estiver pendente, os pedidos deste prato não avançam para preparação.",
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
        val quantity = inputField(
            "Quantidade por dose (ex.: 0,150)",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        )
        val waste = inputField(
            "Desperdício de preparação (%)",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        ).apply { setText("0") }

        form.addView(label("Ingrediente do inventário", 11f, true, forestSoft))
        form.addView(ingredientSpinner, fieldParams())
        addGap(form, 5)
        form.addView(label("Quantidade necessária por dose", 11f, true, forestSoft))
        form.addView(quantity, fieldParams())
        addGap(form, 5)
        form.addView(label("Desperdício esperado na preparação", 11f, true, forestSoft))
        form.addView(waste, fieldParams())
        addGap(form, 7)
        form.addView(label(
            "O valor é multiplicado pela quantidade de pratos do pedido. A percentagem de desperdício aumenta a quantidade retirada do stock.",
            10.5f, false, muted
        ))

        val dialog = AlertDialog.Builder(this)
            .setTitle("Adicionar ingrediente · $menuItemName")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar ingrediente", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selected = stockItems.getOrNull(ingredientSpinner.selectedItemPosition)
                val quantityValue = try {
                    RestaurantRecipeInputValidator.parseQuantityPerPortion(
                        quantity.text.toString()
                    )
                } catch (error: IllegalArgumentException) {
                    quantity.error = error.message ?: "Quantidade inválida."
                    return@setOnClickListener
                }
                val wasteValue = try {
                    RestaurantRecipeInputValidator.parseWastePercent(waste.text.toString())
                } catch (error: IllegalArgumentException) {
                    waste.error = error.message ?: "Percentagem inválida."
                    return@setOnClickListener
                }
                if (selected == null) {
                    ingredientSpinner.requestFocus()
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
            background = rippleBackground(copper, 15)
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
