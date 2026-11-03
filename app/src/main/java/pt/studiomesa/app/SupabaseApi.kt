package pt.studiomesa.app

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

/**
 * Minimal Supabase REST/Auth client. Only the public publishable key is embedded here.
 * All data-changing RPCs enforce authorization in PostgreSQL; never embed service_role keys.
 */
object SupabaseApi {
    private const val BASE_URL = "https://svwvkfsyevuyabelrtjk.supabase.co"
    private const val API_KEY = "sb_publishable_ioJIukpwd0_Fyajd41QL_Q_M6WKE0UH"

    private data class InstrumentationTestEndpoint(
        val baseUrl: String,
        val sslSocketFactory: SSLSocketFactory
    )

    @Volatile
    private var instrumentationTestEndpoint: InstrumentationTestEndpoint? = null

    /**
     * Allows Android instrumentation tests to point the real HTTP client at a local HTTPS
     * mock server. The hook is debug-only and rejects non-loopback or non-HTTPS endpoints.
     */
    internal fun configureInstrumentationTestEndpoint(
        baseUrl: String?,
        sslSocketFactory: SSLSocketFactory? = null
    ) {
        check(BuildConfig.DEBUG) { "Instrumentation endpoints are only available in debug builds." }
        if (baseUrl == null) {
            instrumentationTestEndpoint = null
            return
        }

        val uri = java.net.URI(baseUrl)
        require(
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.host in setOf("localhost", "127.0.0.1", "::1") &&
                uri.rawUserInfo == null &&
                uri.rawQuery == null &&
                uri.rawFragment == null &&
                (uri.path.isNullOrEmpty() || uri.path == "/")
        ) {
            "The instrumentation endpoint must be an HTTPS loopback URL."
        }
        instrumentationTestEndpoint = InstrumentationTestEndpoint(
            baseUrl.removeSuffix("/"),
            requireNotNull(sslSocketFactory) {
                "A test-only TLS socket factory is required."
            }
        )
    }

    data class Session(val accessToken: String, val userId: String, val email: String, val refreshToken: String)

    private fun call(
        method: String,
        path: String,
        body: JSONObject? = null,
        accessToken: String? = null,
        returnRepresentation: Boolean = false
    ): String {
        val testEndpoint = if (BuildConfig.DEBUG) instrumentationTestEndpoint else null
        val requestBaseUrl = testEndpoint?.baseUrl ?: BASE_URL
        val connection = (URL(requestBaseUrl + path).openConnection() as HttpURLConnection).apply {
            if (testEndpoint != null) {
                val secureConnection = this as? HttpsURLConnection
                    ?: throw IllegalStateException("The instrumentation endpoint must use HTTPS.")
                secureConnection.sslSocketFactory = testEndpoint.sslSocketFactory
            }
            requestMethod = method
            connectTimeout = 12000
            readTimeout = 12000
            // Supabase REST/Auth endpoints should return API responses, not redirects.
            // Fail closed instead of following a redirect with session headers.
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("apikey", API_KEY)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            if (returnRepresentation) setRequestProperty("Prefer", "return=representation")
            // Publishable keys are not JWTs: send them only via apikey.
            // Authorization must carry a real user access token, and only after sign-in.
            if (!accessToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer " + accessToken)
            }
        }
        try {
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = ResponseBodyReader.read(stream)
            if (code !in 200..299) {
                // Only show recognized message fields. Never surface an arbitrary HTML or
                // proxy response body to the user, and always cap the displayed message.
                val message = try {
                    val error = JSONObject(text)
                    sequenceOf("msg", "message", "error_description", "error")
                        .map { key -> error.optString(key, "") }
                        .firstOrNull { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                } catch (_: Exception) {
                    null
                }
                throw BackendHttpException(code, path, ApiErrorMessage.sanitize(message, code))
            }
            return text
        } finally {
            connection.disconnect()
        }
    }

    /**
     * PostgREST can return HTTP success with an empty result when RLS hides the target row.
     * Requesting representation and requiring a returned row prevents the UI from reporting
     * that a write succeeded when the target was deleted or access was revoked.
     */
    private fun mutateRows(
        method: String,
        path: String,
        body: JSONObject,
        token: String,
        emptyMessage: String
    ) {
        val response = call(method, path, body, token, returnRepresentation = true)
        if (response.isBlank()) throw IllegalStateException(emptyMessage)
        val rows = try {
            JSONArray(response)
        } catch (_: Exception) {
            throw IllegalStateException(emptyMessage)
        }
        if (rows.length() == 0) throw IllegalStateException(emptyMessage)
    }

    private fun jsonCall(method: String, path: String, body: JSONObject? = null, token: String? = null): JSONObject {
        val response = call(method, path, body, token)
        return if (response.isBlank()) JSONObject() else JSONObject(response)
    }

    fun signIn(email: String, password: String): Session {
        val result = jsonCall("POST", "/auth/v1/token?grant_type=password",
            JSONObject().put("email", email.trim()).put("password", password))
        val token = result.optString("access_token")
        val user = result.optJSONObject("user") ?: throw IllegalStateException("A resposta de autenticação não contém utilizador.")
        if (token.isBlank()) throw IllegalStateException("Não foi recebido um token de sessão.")
        return Session(
            token,
            user.optString("id"),
            user.optString("email", email.trim()),
            result.optString("refresh_token")
        )
    }

    fun refreshSession(refreshToken: String): Session {
        if (refreshToken.isBlank()) {
            throw IllegalStateException("A sessão expirou. Inicia sessão novamente.")
        }
        val result = jsonCall(
            "POST",
            "/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", refreshToken)
        )
        val token = result.optString("access_token")
        val user = result.optJSONObject("user")
            ?: throw IllegalStateException("O servidor não devolveu uma conta para renovar a sessão.")
        val nextRefreshToken = result.optString("refresh_token", refreshToken)
        if (token.isBlank() || nextRefreshToken.isBlank()) {
            throw IllegalStateException("Não foi possível renovar a sessão. Inicia sessão novamente.")
        }
        return Session(
            token,
            user.optString("id"),
            user.optString("email", ""),
            nextRefreshToken
        )
    }

    fun tokenExpiresAtMillis(token: String): Long {
        return try {
            val payload = token.split(".").getOrNull(1)
                ?: return 0L
            val bytes = android.util.Base64.decode(
                payload,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
            )
            org.json.JSONObject(String(bytes, Charsets.UTF_8)).optLong("exp", 0L) * 1000L
        } catch (_: Exception) {
            0L
        }
    }

    fun signUp(email: String, password: String, codeChallenge: String): JSONObject {
        require(codeChallenge.matches(Regex("^[A-Za-z0-9_-]{43}$"))) {
            "O desafio de autenticação não é válido."
        }
        val redirect = URLEncoder.encode("studiomesa://auth-callback", "UTF-8")
        val result = jsonCall(
            "POST",
            "/auth/v1/signup?redirect_to=$redirect",
            JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .put("code_challenge", codeChallenge)
                .put("code_challenge_method", "s256")
        )
        // GoTrue signup commonly returns the user at the JSON root, not in "user".
        // Normalize the response so the UI can recognize that the account was created.
        if (result.optJSONObject("user") == null && result.optString("id").isNotBlank()) {
            result.put("user", JSONObject()
                .put("id", result.optString("id"))
                .put("email", result.optString("email", email.trim())))
        }
        return result
    }

    fun requestPasswordRecovery(email: String, codeChallenge: String) {
        require(codeChallenge.matches(Regex("^[A-Za-z0-9_-]{43}$"))) {
            "O desafio de autenticação não é válido."
        }
        val redirect = URLEncoder.encode("studiomesa://auth-callback", "UTF-8")
        call(
            "POST",
            "/auth/v1/recover?redirect_to=$redirect",
            JSONObject()
                .put("email", email.trim())
                .put("code_challenge", codeChallenge)
                .put("code_challenge_method", "s256")
        )
    }

    fun exchangeCodeForSession(authCode: String, codeVerifier: String): Session {
        require(authCode.isNotBlank()) { "A ligação de autenticação não contém um código válido." }
        require(codeVerifier.matches(Regex("^[A-Za-z0-9._~-]{43,128}$"))) {
            "A verificação de autenticação não é válida."
        }
        val result = jsonCall(
            "POST",
            "/auth/v1/token?grant_type=pkce",
            JSONObject()
                .put("auth_code", authCode)
                .put("code_verifier", codeVerifier)
        )
        val user = result.optJSONObject("user") ?: JSONObject()
        val accessToken = result.optString("access_token")
        val refreshToken = result.optString("refresh_token")
        if (accessToken.isBlank() || refreshToken.isBlank()) {
            throw IllegalStateException("O servidor não devolveu uma sessão válida.")
        }
        return Session(
            accessToken,
            user.optString("id"),
            user.optString("email", ""),
            refreshToken
        )
    }

    fun updatePassword(accessToken: String, newPassword: String) {
        call(
            "PUT",
            "/auth/v1/user",
            JSONObject().put("password", newPassword),
            accessToken
        )
    }

    fun getUser(accessToken: String): JSONObject =
        jsonCall("GET", "/auth/v1/user", token = accessToken)

    fun signOut(accessToken: String) {
        if (accessToken.isBlank()) return
        call("POST", "/auth/v1/logout?scope=local", accessToken = accessToken)
    }

    /**
     * Requests deletion of the currently authenticated user's account.
     * The Edge Function verifies the caller and uses server-only Supabase admin
     * credentials; no privileged key is embedded in the Android client.
     */
    fun deleteAccount(accessToken: String, deleteRestaurantData: Boolean): Boolean {
        require(accessToken.isNotBlank()) { "Inicia sessão antes de eliminar a conta." }
        val response = call(
            "POST",
            "/functions/v1/delete-account",
            JSONObject().put("delete_restaurant_data", deleteRestaurantData),
            accessToken
        )
        val result = try {
            JSONObject(response)
        } catch (_: Exception) {
            throw IllegalStateException("O servidor não devolveu uma confirmação válida da eliminação.")
        }
        if (!result.optBoolean("deleted", false)) {
            throw IllegalStateException("O servidor não confirmou a eliminação da conta.")
        }
        return result.optBoolean("restaurant_data_deleted", false)
    }

    fun isOAuthProviderEnabled(provider: String): Boolean {
        require(provider == "google" || provider == "azure") { "Fornecedor de início de sessão inválido." }
        val settings = jsonCall("GET", "/auth/v1/settings")
        return settings.optJSONObject("external")?.optBoolean(provider, false) ?: false
    }

    fun oauthUrl(provider: String, codeChallenge: String): String {
        require(provider == "google" || provider == "azure") { "Fornecedor de início de sessão inválido." }
        require(codeChallenge.matches(Regex("^[A-Za-z0-9_-]{43}$"))) {
            "O desafio de autenticação não é válido."
        }
        val redirect = URLEncoder.encode("studiomesa://auth-callback", "UTF-8")
        val scopes = if (provider == "azure") "&scopes=email" else ""
        return "$BASE_URL/auth/v1/authorize?provider=$provider&redirect_to=$redirect&code_challenge=$codeChallenge&code_challenge_method=s256&apikey=$API_KEY$scopes"
    }

    fun createRestaurant(token: String, name: String, code: String): String {
        val response = call("POST", "/rest/v1/rpc/create_restaurant",
            JSONObject().put("p_name", name.trim()).put("p_join_code", code.trim().uppercase()), token)
        val restaurantId = response.trim().trim('"')
        require(runCatching { java.util.UUID.fromString(restaurantId).toString() == restaurantId }.getOrDefault(false)) {
            "O servidor não confirmou a criação do restaurante com um identificador válido."
        }
        return restaurantId
    }

    fun joinRestaurant(token: String, code: String, displayName: String): JSONObject {
        val response = call("POST", "/rest/v1/rpc/join_restaurant",
            JSONObject().put("p_join_code", code.trim().uppercase()).put("p_display_name", displayName.trim()), token)
        val array = JSONArray(response)
        if (array.length() == 0) throw IllegalStateException("Não foi devolvido um cartão de cliente.")
        return array.getJSONObject(0)
    }

    fun listTables(token: String, restaurantId: String): JSONArray {
        val encoded = URLEncoder.encode(restaurantId, "UTF-8")
        return JSONArray(call(
            "GET",
            "/rest/v1/restaurant_tables?select=id,table_number,is_occupied,area,seats&restaurant_id=eq.$encoded&order=area.asc,table_number.asc",
            null,
            token
        ))
    }

    fun addTable(token: String, restaurantId: String, tableNumber: Int, area: String, seats: Int) {
        mutateRows(
            "POST",
            "/rest/v1/restaurant_tables",
            JSONObject()
                .put("restaurant_id", restaurantId)
                .put("table_number", tableNumber)
                .put("area", area.trim())
                .put("seats", seats)
                .put("is_occupied", false),
            token,
            "O servidor não confirmou a criação da mesa."
        )
    }

    fun updateTableDetails(token: String, tableId: String, tableNumber: Int, area: String, seats: Int) {
        val encoded = URLEncoder.encode(tableId, "UTF-8")
        mutateRows(
            "PATCH",
            "/rest/v1/restaurant_tables?id=eq.$encoded",
            JSONObject()
                .put("table_number", tableNumber)
                .put("area", area.trim())
                .put("seats", seats),
            token,
            "A mesa já não existe ou a tua conta deixou de ter acesso a este restaurante."
        )
    }

    fun updateTable(token: String, tableId: String, occupied: Boolean) {
        val encoded = URLEncoder.encode(tableId, "UTF-8")
        mutateRows(
            "PATCH",
            "/rest/v1/restaurant_tables?id=eq.$encoded",
            JSONObject().put("is_occupied", occupied),
            token,
            "O estado da mesa não foi alterado. Atualiza os dados e verifica o acesso."
        )
    }

    fun listMenuItems(token: String, restaurantId: String): JSONArray {
        val encoded = URLEncoder.encode(restaurantId, "UTF-8")
        return JSONArray(call(
            "GET",
            "/rest/v1/menu_items?select=id,name,category,description,price,cost,allergens,is_active&restaurant_id=eq.$encoded&is_active=eq.true&order=category.asc,name.asc",
            null,
            token
        ))
    }

    /**
     * Uses the client-generated row ID as the retry key. If a POST committed but its
     * response was lost, a retry can verify the same row instead of creating a duplicate.
     */
    fun addMenuItem(
        token: String,
        restaurantId: String,
        itemId: String,
        name: String,
        category: String,
        description: String,
        price: Double,
        cost: Double,
        allergens: String
    ) {
        require(runCatching { java.util.UUID.fromString(itemId).toString() == itemId }.getOrDefault(false)) {
            "O identificador do artigo não é válido."
        }
        val normalizedName = name.trim()
        val normalizedCategory = category.trim()
        val normalizedDescription = description.trim()
        val normalizedAllergens = allergens.trim()
        val body = JSONObject()
            .put("id", itemId)
            .put("restaurant_id", restaurantId)
            .put("name", normalizedName)
            .put("category", normalizedCategory)
            .put("description", normalizedDescription)
            .put("price", price)
            .put("cost", cost)
            .put("allergens", normalizedAllergens)
            .put("is_active", true)

        try {
            mutateRows(
                "POST",
                "/rest/v1/menu_items",
                body,
                token,
                "O servidor não confirmou a criação do artigo da carta."
            )
        } catch (writeError: Exception) {
            val existingMatches = runCatching {
                val encodedId = URLEncoder.encode(itemId, "UTF-8")
                val rows = JSONArray(call(
                    "GET",
                    "/rest/v1/menu_items?select=id,restaurant_id,name,category,description,price,cost,allergens,is_active&id=eq.$encodedId",
                    null,
                    token
                ))
                val row = rows.optJSONObject(0)
                row != null &&
                    row.optString("id") == itemId &&
                    row.optString("restaurant_id") == restaurantId &&
                    row.optString("name") == normalizedName &&
                    row.optString("category") == normalizedCategory &&
                    row.optString("description") == normalizedDescription &&
                    kotlin.math.abs(row.optDouble("price", Double.NaN) - price) < 0.000001 &&
                    kotlin.math.abs(row.optDouble("cost", Double.NaN) - cost) < 0.000001 &&
                    row.optString("allergens") == normalizedAllergens &&
                    row.optBoolean("is_active", false)
            }.getOrDefault(false)
            if (!existingMatches) throw writeError
        }
    }

    fun updateMenuItem(
        token: String,
        itemId: String,
        name: String,
        category: String,
        description: String,
        price: Double,
        cost: Double,
        allergens: String
    ) {
        val encoded = URLEncoder.encode(itemId, "UTF-8")
        mutateRows(
            "PATCH",
            "/rest/v1/menu_items?id=eq.$encoded",
            JSONObject()
                .put("name", name.trim())
                .put("category", category.trim())
                .put("description", description.trim())
                .put("price", price)
                .put("cost", cost)
                .put("allergens", allergens.trim()),
            token,
            "O artigo já não existe ou a tua conta deixou de ter acesso a este restaurante."
        )
    }

    fun getRestaurantPriceAdviserEnabled(token: String): Boolean {
        val response = call(
            "POST",
            "/rest/v1/rpc/get_restaurant_price_adviser_enabled",
            JSONObject(),
            token
        )
        return response.trim().trim('"').equals("true", ignoreCase = true)
    }

    fun setRestaurantPriceAdviserEnabled(token: String, enabled: Boolean): Boolean {
        val response = call(
            "POST",
            "/rest/v1/rpc/set_restaurant_price_adviser_enabled",
            JSONObject().put("p_enabled", enabled),
            token
        )
        return response.trim().trim('"').equals("true", ignoreCase = true)
    }

    fun applyRestaurantAdvisedMenuPrice(
        token: String,
        actionId: String,
        itemId: String,
        expectedPrice: BigDecimal,
        newPrice: BigDecimal,
        targetCostPercent: BigDecimal
    ): JSONObject {
        require(runCatching { java.util.UUID.fromString(actionId).toString() == actionId }.getOrDefault(false)) {
            "O identificador da ação não é válido."
        }
        require(runCatching { java.util.UUID.fromString(itemId).toString() == itemId }.getOrDefault(false)) {
            "O identificador do artigo não é válido."
        }
        val response = call(
            "POST",
            "/rest/v1/rpc/apply_restaurant_advised_menu_price",
            JSONObject()
                .put("p_action_id", actionId)
                .put("p_item_id", itemId)
                .put("p_expected_price", expectedPrice)
                .put("p_new_price", newPrice)
                .put("p_target_cost_percent", targetCostPercent),
            token
        )
        val rows = try {
            JSONArray(response)
        } catch (_: Exception) {
            throw IllegalStateException("O servidor não confirmou a alteração do preço.")
        }
        if (rows.length() != 1) {
            throw IllegalStateException("O servidor não confirmou a alteração do preço.")
        }
        val result = rows.optJSONObject(0)
            ?: throw IllegalStateException("A resposta do servidor não contém um resultado válido.")
        val appliedPriceMatches = runCatching {
            BigDecimal(result.optString("applied_price")).compareTo(newPrice) == 0
        }.getOrDefault(false)
        if (result.optString("action_id") != actionId ||
            result.optString("menu_item_id") != itemId ||
            !appliedPriceMatches
        ) {
            throw IllegalStateException(
                "Não foi possível confirmar o preço aplicado. Consulta o histórico e atualiza a carta antes de repetir."
            )
        }
        return result
    }

    fun listRestaurantPriceAdviserAudit(token: String, limit: Int = 20): JSONArray {
        require(limit in 1..100) { "O limite do histórico deve estar entre 1 e 100." }
        return JSONArray(call(
            "POST",
            "/rest/v1/rpc/get_restaurant_price_adviser_audit",
            JSONObject().put("p_limit", limit),
            token
        ))
    }

    fun deactivateMenuItem(token: String, itemId: String) {
        val encoded = URLEncoder.encode(itemId, "UTF-8")
        mutateRows(
            "PATCH",
            "/rest/v1/menu_items?id=eq.$encoded",
            JSONObject().put("is_active", false),
            token,
            "O artigo não foi arquivado. Atualiza a carta e verifica o acesso."
        )
    }

    /**
     * Loads only orders that are still active for the kitchen screen.
     * Closed history is deliberately excluded from the 15-second refresh cycle.
     */
    fun listActiveKitchenOrders(token: String, restaurantId: String): JSONArray {
        val loadedRows = OrderHistoryPagination.collect { offset, limit ->
            val page = JSONArray(call(
                "GET",
                KitchenQueueQuery.path(restaurantId, offset, limit),
                null,
                token
            ))
            (0 until page.length()).map { index ->
                page.optJSONObject(index)
                    ?: throw IllegalStateException("O servidor devolveu um pedido de cozinha inválido.")
            }
        }

        val result = JSONArray()
        val seenIds = HashSet<String>()
        loadedRows.forEach { order ->
            val id = order.optString("id").trim()
            if (id.isBlank()) {
                throw IllegalStateException("O servidor devolveu um pedido sem identificador.")
            }
            if (seenIds.add(id)) result.put(order)
        }
        return result
    }

    fun listOrders(token: String, restaurantId: String): JSONArray {
        val encoded = URLEncoder.encode(restaurantId, "UTF-8")
        val loadedRows = OrderHistoryPagination.collect { offset, limit ->
            val page = JSONArray(call(
                "GET",
                "/rest/v1/restaurant_orders?select=id,table_number,item_name,amount,quantity,service_course,notes,status,created_at&restaurant_id=eq.$encoded&order=created_at.desc,id.desc&limit=$limit&offset=$offset",
                null,
                token
            ))
            (0 until page.length()).map { index ->
                page.optJSONObject(index)
                    ?: throw IllegalStateException("O servidor devolveu um pedido com formato inválido.")
            }
        }

        // Inserts can happen while offset pages are fetched. Keep the first copy of
        // each ID so a shifted page boundary never duplicates a ticket in the cache.
        val result = JSONArray()
        val seenIds = HashSet<String>()
        loadedRows.forEach { order ->
            val id = order.optString("id").trim()
            if (id.isBlank()) {
                throw IllegalStateException("O servidor devolveu um pedido sem identificador.")
            }
            if (seenIds.add(id)) result.put(order)
        }
        return result
    }

    fun createOrder(
        token: String,
        restaurantId: String,
        tableNumber: Int,
        itemName: String,
        amount: Double,
        quantity: Int,
        serviceCourse: String,
        notes: String,
        requestId: String
    ) {
        require(runCatching { java.util.UUID.fromString(requestId).toString() == requestId }.getOrDefault(false)) {
            "O identificador da tentativa de pedido não é válido."
        }
        val body = JSONObject()
            .put("id", requestId)
            .put("restaurant_id", restaurantId)
            .put("table_number", tableNumber)
            .put("item_name", itemName)
            .put("amount", amount)
            .put("quantity", quantity)
            .put("service_course", serviceCourse)
            .put("notes", notes.trim())
            .put("status", "Aberto")

        try {
            mutateRows(
                "POST",
                "/rest/v1/restaurant_orders",
                body,
                token,
                "O servidor não confirmou a criação do pedido."
            )
        } catch (writeError: Exception) {
            // The request may have committed while its HTTP response was lost.
            // Reuse the client-generated primary key and verify the saved row before
            // reporting success, rather than inserting a duplicate on retry.
            val existingMatches = runCatching {
                val encodedId = URLEncoder.encode(requestId, "UTF-8")
                val rows = JSONArray(call(
                    "GET",
                    "/rest/v1/restaurant_orders?select=id,restaurant_id,table_number,item_name,amount,quantity,service_course,notes,created_by&id=eq.$encodedId",
                    null,
                    token
                ))
                val row = rows.optJSONObject(0)
                val userId = getUser(token).optString("id")
                row != null &&
                    userId.isNotBlank() &&
                    row.optString("id") == requestId &&
                    row.optString("restaurant_id") == restaurantId &&
                    row.optString("created_by") == userId &&
                    row.optInt("table_number") == tableNumber &&
                    row.optString("item_name") == itemName &&
                    kotlin.math.abs(row.optDouble("amount", Double.NaN) - amount) < 0.000001 &&
                    row.optInt("quantity") == quantity &&
                    row.optString("service_course") == serviceCourse &&
                    row.optString("notes") == notes.trim()
            }.getOrDefault(false)
            if (!existingMatches) throw writeError
        }
    }

    fun listOrderEvents(token: String, orderId: String): JSONArray {
        val encoded = URLEncoder.encode(orderId, "UTF-8")
        return JSONArray(call(
            "GET",
            "/rest/v1/restaurant_order_events?select=event_type,old_status,new_status,created_at,actor_user_id&order_id=eq.$encoded&order=created_at.asc",
            null,
            token
        ))
    }

    fun updateOrder(token: String, orderId: String, status: String) {
        val encoded = URLEncoder.encode(orderId, "UTF-8")
        mutateRows(
            "PATCH",
            "/rest/v1/restaurant_orders?id=eq.$encoded",
            JSONObject().put("status", status),
            token,
            "O pedido não foi alterado. Atualiza a fila e verifica o acesso."
        )
    }

    fun getMyRestaurant(token: String): JSONObject? {
        val response = call("POST", "/rest/v1/rpc/get_my_restaurant_details", JSONObject(), token)
        val restaurants = JSONArray(response)
        return if (restaurants.length() == 0) null else restaurants.getJSONObject(0)
    }

    fun rotateStaffJoinCode(token: String): String {
        val response = call("POST", "/rest/v1/rpc/rotate_restaurant_staff_code", JSONObject(), token)
        return response.trim().trim('"')
    }

    fun rotateCustomerJoinCode(token: String): String {
        val response = call("POST", "/rest/v1/rpc/rotate_restaurant_customer_code", JSONObject(), token)
        return response.trim().trim('"')
    }

    fun joinRestaurantStaff(token: String, staffCode: String): JSONObject {
        val response = call(
            "POST",
            "/rest/v1/rpc/join_restaurant_staff",
            JSONObject().put("p_staff_code", staffCode.trim().uppercase()),
            token
        )
        val result = JSONArray(response)
        if (result.length() == 0) throw IllegalStateException("Não foi possível associar a conta à equipa.")
        return result.getJSONObject(0)
    }

    fun getMyLoyaltyMembership(token: String): JSONObject? {
        val response = call("POST", "/rest/v1/rpc/get_my_loyalty_membership", JSONObject(), token)
        val memberships = JSONArray(response)
        return if (memberships.length() == 0) null else memberships.getJSONObject(0)
    }

    fun listRestaurantLoyaltyRewards(token: String, restaurantId: String): JSONArray {
        require(runCatching { java.util.UUID.fromString(restaurantId) }.isSuccess) {
            "O restaurante não está identificado."
        }
        return JSONArray(call(
            "POST",
            "/rest/v1/rpc/get_restaurant_loyalty_rewards",
            JSONObject().put("p_restaurant_id", restaurantId),
            token
        ))
    }

    fun listCustomerLoyaltyRewards(token: String, memberId: String): JSONArray {
        require(runCatching { java.util.UUID.fromString(memberId) }.isSuccess) {
            "O cartão de cliente não é válido."
        }
        return JSONArray(call(
            "POST",
            "/rest/v1/rpc/get_customer_loyalty_rewards",
            JSONObject().put("p_member_id", memberId),
            token
        ))
    }

    fun saveLoyaltyReward(
        token: String,
        restaurantId: String,
        rewardId: String,
        name: String,
        description: String,
        pointsCost: Int,
        stockLimit: Int?,
        isActive: Boolean
    ): String {
        require(runCatching { java.util.UUID.fromString(restaurantId) }.isSuccess &&
            runCatching { java.util.UUID.fromString(rewardId) }.isSuccess) {
            "O restaurante ou a recompensa não estão identificados."
        }
        val normalizedName = name.trim()
        val normalizedDescription = description.trim()
        require(normalizedName.length in 1..100 &&
            normalizedDescription.length <= 500 &&
            pointsCost in 1..1_000_000 &&
            (stockLimit == null || stockLimit >= 0)) {
            "Confirma o nome, a descrição, os pontos e o stock da recompensa."
        }
        val body = JSONObject()
            .put("p_restaurant_id", restaurantId)
            .put("p_reward_id", rewardId)
            .put("p_name", normalizedName)
            .put("p_description", normalizedDescription)
            .put("p_points_cost", pointsCost)
            .put("p_stock_limit", stockLimit ?: JSONObject.NULL)
            .put("p_is_active", isActive)
        val savedId = call("POST", "/rest/v1/rpc/save_loyalty_reward", body, token)
            .trim().trim('"')
        require(savedId == rewardId) {
            "O servidor não confirmou a gravação desta recompensa."
        }
        return savedId
    }

    fun redeemLoyaltyReward(token: String, rewardId: String, requestId: String): JSONObject {
        require(runCatching { java.util.UUID.fromString(rewardId) }.isSuccess &&
            runCatching { java.util.UUID.fromString(requestId).toString() == requestId }.getOrDefault(false)) {
            "A recompensa ou o identificador do pedido não são válidos."
        }
        val rows = JSONArray(call(
            "POST",
            "/rest/v1/rpc/redeem_loyalty_reward",
            JSONObject()
                .put("p_reward_id", rewardId)
                .put("p_client_request_id", requestId),
            token
        ))
        if (rows.length() == 0) {
            throw IllegalStateException("O servidor não confirmou a troca dos pontos.")
        }
        val result = rows.getJSONObject(0)
        require(result.optString("redemption_id").isNotBlank() &&
            result.optString("reward_name").isNotBlank() &&
            result.optInt("points_spent", 0) in 1..1_000_000 &&
            result.optInt("points_remaining", -1) >= 0 &&
            result.optString("status") in setOf("pending", "fulfilled")) {
            "O servidor devolveu um resultado de resgate inválido."
        }
        return result
    }

    fun listMyLoyaltyRewardRedemptions(token: String): JSONArray =
        JSONArray(call(
            "POST",
            "/rest/v1/rpc/get_my_loyalty_reward_redemptions",
            JSONObject(),
            token
        ))

    fun listRestaurantLoyaltyRewardRedemptions(token: String, restaurantId: String): JSONArray {
        require(runCatching { java.util.UUID.fromString(restaurantId) }.isSuccess) {
            "O restaurante não está identificado."
        }
        return JSONArray(call(
            "POST",
            "/rest/v1/rpc/get_restaurant_loyalty_reward_redemptions",
            JSONObject().put("p_restaurant_id", restaurantId),
            token
        ))
    }

    fun fulfillLoyaltyRewardRedemption(token: String, redemptionId: String) {
        require(runCatching { java.util.UUID.fromString(redemptionId) }.isSuccess) {
            "O pedido de recompensa não é válido."
        }
        call(
            "POST",
            "/rest/v1/rpc/fulfill_loyalty_reward_redemption",
            JSONObject().put("p_redemption_id", redemptionId),
            token
        )
    }

    fun listCustomerMenu(token: String, memberId: String): JSONArray {
        val response = call(
            "POST",
            "/rest/v1/rpc/get_member_restaurant_menu",
            JSONObject().put("p_member_id", memberId),
            token
        )
        return JSONArray(response)
    }

    /**
     * Creates a loyalty-only QR associated with a receipt reference and total.
     * The server calculates the points and checks the restaurant staff role.
     */
    fun issueLoyaltyInvoiceQr(
        token: String,
        memberId: String,
        amount: BigDecimal,
        invoiceReference: String,
        requestId: String
    ): JSONObject {
        require(runCatching { java.util.UUID.fromString(requestId).toString() == requestId }.getOrDefault(false)) {
            "O identificador da emissão não é válido."
        }
        require(runCatching { java.util.UUID.fromString(memberId) }.isSuccess) {
            "O cartão de cliente não é válido."
        }
        require(amount.scale() <= 2 && amount >= BigDecimal("1.00") && amount <= BigDecimal("100000.99")) {
            "O total deve ser válido e ter pelo menos 1 euro."
        }
        val body = JSONObject()
            .put("p_member_id", memberId)
            .put("p_amount", amount.setScale(2, java.math.RoundingMode.UNNECESSARY))
            .put("p_invoice_reference", invoiceReference.trim())
            .put("p_client_request_id", requestId)
        val rows = JSONArray(call("POST", "/rest/v1/rpc/issue_loyalty_invoice_qr", body, token))
        if (rows.length() == 0) throw IllegalStateException("O servidor não devolveu o QR da fatura.")
        val result = rows.getJSONObject(0)
        val qrToken = result.optString("qr_token")
        require(runCatching { java.util.UUID.fromString(qrToken) }.isSuccess &&
            result.optInt("points", 0) in 1..100000 &&
            result.optString("invoice_reference") == invoiceReference.trim()) {
            "O servidor devolveu dados de QR inválidos."
        }
        return result
    }

    /**
     * Redeems a one-time loyalty QR for the currently authenticated customer.
     * Repeated scans by the same account return the original result without new points.
     */
    fun redeemLoyaltyInvoiceQr(token: String, qrToken: String): JSONObject {
        require(runCatching { java.util.UUID.fromString(qrToken).toString() == qrToken }.getOrDefault(false)) {
            "O QR de fidelização não é válido."
        }
        val rows = JSONArray(call(
            "POST",
            "/rest/v1/rpc/redeem_loyalty_invoice_qr",
            JSONObject().put("p_qr_token", qrToken),
            token
        ))
        if (rows.length() == 0) throw IllegalStateException("O servidor não confirmou a atribuição dos pontos.")
        val result = rows.getJSONObject(0)
        require(result.optString("member_id").isNotBlank() &&
            result.optInt("points_awarded", 0) in 1..100000 &&
            result.optInt("points", -1) >= 0) {
            "O servidor devolveu um saldo de fidelização inválido."
        }
        return result
    }

    fun listMembers(token: String, restaurantId: String): JSONArray {
        val encoded = URLEncoder.encode(restaurantId, "UTF-8")
        return JSONArray(call("GET",
            "/rest/v1/loyalty_members?select=id,member_code,display_name,points&restaurant_id=eq.$encoded&order=created_at.desc",
            null, token))
    }

    fun adjustPoints(
        token: String,
        restaurantId: String,
        memberId: String,
        delta: Int,
        note: String?,
        requestId: String
    ): JSONObject {
        require(runCatching { java.util.UUID.fromString(requestId).toString() == requestId }.getOrDefault(false)) {
            "O identificador da tentativa de pontos não é válido."
        }
        val normalizedNote = note?.trim()?.takeIf { it.isNotEmpty() }
        val body = JSONObject()
            .put("p_client_request_id", requestId)
            .put("p_member_id", memberId)
            .put("p_delta", delta)
        if (normalizedNote != null) body.put("p_note", normalizedNote)

        try {
            val response = call("POST", "/rest/v1/rpc/adjust_loyalty_points_idempotent", body, token)
            val array = JSONArray(response)
            if (array.length() == 0) throw IllegalStateException("Não foi devolvido o novo saldo.")
            val result = array.getJSONObject(0)
            if (result.optString("member_id") != memberId || result.optInt("points", -1) < 0) {
                throw IllegalStateException("O servidor devolveu um saldo de pontos inválido.")
            }
            return result
        } catch (writeError: Exception) {
            // A transaction may have committed even if the HTTP response was lost.
            // Verify the exact request before interpreting the operation as successful.
            val existing = runCatching {
                val userId = getUser(token).optString("id")
                if (userId.isBlank()) return@runCatching null
                val encodedRestaurant = URLEncoder.encode(restaurantId, "UTF-8")
                val encodedUser = URLEncoder.encode(userId, "UTF-8")
                val encodedRequest = URLEncoder.encode(requestId, "UTF-8")
                val rows = JSONArray(call(
                    "GET",
                    "/rest/v1/loyalty_transactions?select=id,restaurant_id,member_id,created_by,points_delta,points_after,note,client_request_id&restaurant_id=eq.$encodedRestaurant&created_by=eq.$encodedUser&client_request_id=eq.$encodedRequest",
                    null,
                    token
                ))
                val row = rows.optJSONObject(0) ?: return@runCatching null
                if (
                    row.optString("restaurant_id") == restaurantId &&
                    row.optString("created_by") == userId &&
                    row.optString("client_request_id") == requestId &&
                    row.optString("member_id") == memberId &&
                    row.optInt("points_delta", Int.MIN_VALUE) == delta &&
                    row.optString("note") == (normalizedNote ?: "")
                ) row else null
            }.getOrNull() ?: throw writeError
            if (existing.optString("id").isBlank()) throw writeError

            val currentMember = runCatching { getMember(token, memberId) }.getOrElse { throw writeError }
            if (currentMember.optString("restaurant_id") != restaurantId) throw writeError
            return JSONObject()
                .put("member_id", memberId)
                .put("member_code", currentMember.optString("member_code"))
                .put("points", currentMember.optInt("points", -1))
                .also {
                    if (it.optInt("points", -1) < 0) throw writeError
                }
        }
    }

    fun listMemberTransactions(token: String, memberId: String): JSONArray {
        val encoded = URLEncoder.encode(memberId, "UTF-8")
        return JSONArray(call(
            "GET",
            "/rest/v1/loyalty_transactions?select=points_delta,points_after,note,created_at&member_id=eq.$encoded&order=created_at.desc&limit=20",
            null,
            token
        ))
    }

    fun getMember(token: String, memberId: String): JSONObject {
        val encoded = URLEncoder.encode(memberId, "UTF-8")
        val array = JSONArray(call("GET",
            "/rest/v1/loyalty_members?select=id,member_code,display_name,points,restaurant_id&id=eq.$encoded",
            null, token))
        if (array.length() == 0) throw IllegalStateException("Cartão de cliente não encontrado.")
        return array.getJSONObject(0)
    }
    fun listInventoryItems(token: String, restaurantId: String): JSONArray {
        val encoded = URLEncoder.encode(restaurantId, "UTF-8")
        return JSONArray(call(
            "GET",
            "/rest/v1/restaurant_inventory_items?select=id,name,unit,quantity,low_stock_threshold,unit_cost_per_unit,updated_at&restaurant_id=eq.$encoded&order=name.asc",
            null,
            token
        ))
    }

    fun saveInventoryItem(
        token: String,
        restaurantId: String,
        itemId: String?,
        name: String,
        unit: String,
        quantity: Double,
        lowStockThreshold: Double,
        note: String
    ): String {
        val body = JSONObject()
            .put("p_restaurant_id", restaurantId)
            .put("p_item_id", itemId ?: JSONObject.NULL)
            .put("p_name", name.trim())
            .put("p_unit", unit.trim())
            .put("p_quantity", quantity)
            .put("p_low_stock_threshold", lowStockThreshold)
            .put("p_note", note.trim())
        return call("POST", "/rest/v1/rpc/save_inventory_item", body, token).trim().trim('"')
    }

    fun createInventoryItem(
        token: String,
        requestId: String,
        restaurantId: String,
        name: String,
        unit: String,
        quantity: Double,
        lowStockThreshold: Double,
        note: String
    ): String {
        require(runCatching { java.util.UUID.fromString(requestId).toString() == requestId }.getOrDefault(false)) {
            "O identificador da tentativa de criação de stock não é válido."
        }
        val body = JSONObject()
            .put("p_client_request_id", requestId)
            .put("p_restaurant_id", restaurantId)
            .put("p_name", name.trim())
            .put("p_unit", unit.trim())
            .put("p_quantity", quantity)
            .put("p_low_stock_threshold", lowStockThreshold)
            .put("p_note", note.trim())
        val itemId = call(
            "POST",
            "/rest/v1/rpc/create_inventory_item_idempotent",
            body,
            token
        ).trim().trim('"')
        require(runCatching { java.util.UUID.fromString(itemId).toString() == itemId }.getOrDefault(false)) {
            "O servidor não confirmou a criação do artigo de stock."
        }
        return itemId
    }

    fun saveInventoryItemWithCost(
        token: String,
        restaurantId: String,
        itemId: String?,
        name: String,
        unit: String,
        quantity: Double,
        lowStockThreshold: Double,
        note: String,
        unitCostPerUnit: java.math.BigDecimal?
    ): String {
        val body = JSONObject()
            .put("p_restaurant_id", restaurantId)
            .put("p_item_id", itemId ?: JSONObject.NULL)
            .put("p_name", name.trim())
            .put("p_unit", unit.trim())
            .put("p_quantity", quantity)
            .put("p_low_stock_threshold", lowStockThreshold)
            .put("p_note", note.trim())
            .put("p_unit_cost_per_unit", unitCostPerUnit ?: JSONObject.NULL)
        return call("POST", "/rest/v1/rpc/save_inventory_item_with_cost", body, token).trim().trim('"')
    }

    fun createInventoryItemWithCost(
        token: String,
        requestId: String,
        restaurantId: String,
        name: String,
        unit: String,
        quantity: Double,
        lowStockThreshold: Double,
        note: String,
        unitCostPerUnit: java.math.BigDecimal?
    ): String {
        require(runCatching { java.util.UUID.fromString(requestId).toString() == requestId }.getOrDefault(false)) {
            "O identificador da tentativa de criação de stock não é válido."
        }
        val body = JSONObject()
            .put("p_client_request_id", requestId)
            .put("p_restaurant_id", restaurantId)
            .put("p_name", name.trim())
            .put("p_unit", unit.trim())
            .put("p_quantity", quantity)
            .put("p_low_stock_threshold", lowStockThreshold)
            .put("p_note", note.trim())
            .put("p_unit_cost_per_unit", unitCostPerUnit ?: JSONObject.NULL)
        val itemId = call(
            "POST",
            "/rest/v1/rpc/create_inventory_item_idempotent_with_cost",
            body,
            token
        ).trim().trim('"')
        require(runCatching { java.util.UUID.fromString(itemId).toString() == itemId }.getOrDefault(false)) {
            "O servidor não confirmou a criação do artigo de stock."
        }
        return itemId
    }

    fun adjustInventoryStock(
        token: String,
        restaurantId: String,
        itemId: String,
        delta: Double,
        note: String,
        requestId: String
    ): Double {
        require(runCatching { java.util.UUID.fromString(requestId).toString() == requestId }.getOrDefault(false)) {
            "O identificador da tentativa de stock não é válido."
        }
        val normalizedNote = note.trim().take(500)
        val body = JSONObject()
            .put("p_client_request_id", requestId)
            .put("p_item_id", itemId)
            .put("p_delta", delta)
            .put("p_note", normalizedNote)
        try {
            val response = call("POST", "/rest/v1/rpc/adjust_inventory_stock_idempotent", body, token)
            val rows = JSONArray(response)
            if (rows.length() == 0) throw IllegalStateException("O servidor não devolveu a quantidade atualizada.")
            val row = rows.getJSONObject(0)
            val resultItem = row.optString("item_id")
            val quantity = row.optDouble("quantity", Double.NaN)
            if (resultItem != itemId || !quantity.isFinite() || quantity < 0.0) {
                throw IllegalStateException("O servidor devolveu uma quantidade de stock inválida.")
            }
            return quantity
        } catch (writeError: Exception) {
            // The database may have committed the movement before the response was lost.
            // Only accept a retry when the original movement exactly matches this request.
            val movementMatches = runCatching {
                val userId = getUser(token).optString("id")
                if (userId.isBlank()) return@runCatching false
                val encodedRestaurant = URLEncoder.encode(restaurantId, "UTF-8")
                val encodedUser = URLEncoder.encode(userId, "UTF-8")
                val encodedRequest = URLEncoder.encode(requestId, "UTF-8")
                val rows = JSONArray(call(
                    "GET",
                    "/rest/v1/restaurant_inventory_movements?select=id,restaurant_id,item_id,delta,quantity_after,note,created_by,client_request_id&restaurant_id=eq.$encodedRestaurant&created_by=eq.$encodedUser&client_request_id=eq.$encodedRequest",
                    null,
                    token
                ))
                val row = rows.optJSONObject(0) ?: return@runCatching false
                row.optString("restaurant_id") == restaurantId &&
                    row.optString("item_id") == itemId &&
                    row.optString("created_by") == userId &&
                    row.optString("client_request_id") == requestId &&
                    kotlin.math.abs(row.optDouble("delta", Double.NaN) - delta) < 0.000001 &&
                    row.optString("note") == normalizedNote
            }.getOrDefault(false)
            if (!movementMatches) throw writeError

            // Return the current quantity after refreshing: later valid movements may
            // have followed the original idempotent movement.
            val latest = runCatching { listInventoryItems(token, restaurantId) }.getOrElse { throw writeError }
            for (i in 0 until latest.length()) {
                val item = latest.optJSONObject(i) ?: continue
                if (item.optString("id") == itemId) {
                    val currentQuantity = item.optDouble("quantity", Double.NaN)
                    if (currentQuantity.isFinite() && currentQuantity >= 0.0) return currentQuantity
                    throw writeError
                }
            }
            throw writeError
        }
    }

    fun listInventoryMovements(token: String, itemId: String): JSONArray {
        val encoded = URLEncoder.encode(itemId, "UTF-8")
        return JSONArray(call(
            "GET",
            "/rest/v1/restaurant_inventory_movements?select=delta,quantity_after,note,created_at&item_id=eq.$encoded&order=created_at.desc&limit=30",
            null,
            token
        ))
    }

    fun listInventoryWasteMovements(
        token: String,
        restaurantId: String,
        limit: Int = 100
    ): JSONArray {
        require(limit in 1..200) { "O limite de perdas deve estar entre 1 e 200." }
        val encodedRestaurant = URLEncoder.encode(restaurantId, "UTF-8")
        val encodedWastePrefix = URLEncoder.encode(InventoryWasteNotes.PREFIX + "%", "UTF-8")
        return JSONArray(call(
            "GET",
            "/rest/v1/restaurant_inventory_movements?select=item_id,delta,note,created_at,unit_cost_at_movement&restaurant_id=eq.$encodedRestaurant&delta=lt.0&note=like.$encodedWastePrefix&order=created_at.desc&limit=$limit",
            null,
            token
        ))
    }

    fun listReservations(token: String, restaurantId: String): JSONArray {
        val encoded = URLEncoder.encode(restaurantId, "UTF-8")
        return JSONArray(call(
            "GET",
            "/rest/v1/restaurant_reservations?select=id,guest_name,guest_phone,party_size,reservation_at,duration_minutes,table_number,status,notes,created_at&restaurant_id=eq.$encoded&order=reservation_at.asc&limit=200",
            null,
            token
        ))
    }

    fun createReservation(
        token: String,
        requestId: String,
        restaurantId: String,
        guestName: String,
        guestPhone: String,
        partySize: Int,
        reservationAt: String,
        durationMinutes: Int,
        tableNumber: Int?,
        notes: String
    ): String {
        require(runCatching { java.util.UUID.fromString(requestId).toString() == requestId }.getOrDefault(false)) {
            "O identificador da tentativa de reserva não é válido."
        }
        val body = JSONObject()
            .put("p_client_request_id", requestId)
            .put("p_restaurant_id", restaurantId)
            .put("p_guest_name", guestName.trim())
            .put("p_guest_phone", guestPhone.trim())
            .put("p_party_size", partySize)
            .put("p_reservation_at", reservationAt)
            .put("p_duration_minutes", durationMinutes)
            .put("p_table_number", tableNumber ?: JSONObject.NULL)
            .put("p_notes", notes.trim())
        val reservationId = call(
            "POST",
            "/rest/v1/rpc/create_restaurant_reservation_idempotent",
            body,
            token
        ).trim().trim('"')
        require(runCatching { java.util.UUID.fromString(reservationId).toString() == reservationId }.getOrDefault(false)) {
            "O servidor não confirmou a criação da reserva. Atualiza a lista antes de voltar a tentar."
        }
        return reservationId
    }

    fun updateReservationStatus(token: String, reservationId: String, status: String) {
        require(status in setOf("Pendente", "Confirmada", "Cancelada", "Concluída", "Não compareceu")) {
            "Estado de reserva inválido."
        }
        call(
            "POST",
            "/rest/v1/rpc/update_restaurant_reservation_status",
            JSONObject().put("p_reservation_id", reservationId).put("p_status", status),
            token
        )
    }

    fun assignReservationTable(token: String, reservationId: String, tableNumber: Int) {
        require(token.isNotBlank()) { "Inicia sessão para atribuir uma mesa." }
        require(runCatching { java.util.UUID.fromString(reservationId).toString() == reservationId }.getOrDefault(false)) {
            "O identificador da reserva não é válido."
        }
        require(tableNumber in 1..100) { "Seleciona uma mesa entre 1 e 100." }
        call(
            "POST",
            "/rest/v1/rpc/assign_restaurant_reservation_table",
            JSONObject()
                .put("p_reservation_id", reservationId)
                .put("p_table_number", tableNumber),
            token
        )
    }

    fun createCustomerReservationRequest(
        token: String,
        requestId: String,
        guestName: String,
        guestPhone: String,
        partySize: Int,
        reservationAt: String,
        durationMinutes: Int,
        notes: String
    ): JSONObject {
        require(token.isNotBlank()) { "Inicia sessão para pedir uma reserva." }
        require(runCatching { java.util.UUID.fromString(requestId).toString() == requestId }.getOrDefault(false)) {
            "O identificador da tentativa de reserva não é válido."
        }
        require(partySize in 1..60) { "O número de pessoas deve estar entre 1 e 60." }
        require(durationMinutes in 30..480) { "A duração deve estar entre 30 e 480 minutos." }
        val response = call(
            "POST",
            "/rest/v1/rpc/create_customer_reservation_request",
            JSONObject()
                .put("p_client_request_id", requestId)
                .put("p_guest_name", guestName.trim())
                .put("p_guest_phone", guestPhone.trim())
                .put("p_party_size", partySize)
                .put("p_reservation_at", reservationAt)
                .put("p_duration_minutes", durationMinutes)
                .put("p_notes", notes.trim()),
            token
        )
        val rows = try { JSONArray(response) } catch (_: Exception) {
            throw IllegalStateException("O servidor não confirmou o pedido de reserva.")
        }
        if (rows.length() != 1) throw IllegalStateException("O servidor não confirmou o pedido de reserva.")
        val row = rows.getJSONObject(0)
        val id = row.optString("reservation_id")
        require(runCatching { java.util.UUID.fromString(id).toString() == id }.getOrDefault(false)) {
            "O servidor não devolveu um identificador válido para a reserva."
        }
        return row
    }

    fun listMyCustomerReservations(token: String): JSONArray {
        require(token.isNotBlank()) { "Inicia sessão para consultar as tuas reservas." }
        val response = call(
            "POST",
            "/rest/v1/rpc/get_my_restaurant_reservations",
            JSONObject(),
            token
        )
        return JSONArray(response)
    }

    fun cancelMyCustomerReservation(token: String, reservationId: String) {
        require(token.isNotBlank()) { "Inicia sessão para cancelar uma reserva." }
        require(runCatching { java.util.UUID.fromString(reservationId).toString() == reservationId }.getOrDefault(false)) {
            "O identificador da reserva não é válido."
        }
        call(
            "POST",
            "/rest/v1/rpc/cancel_my_restaurant_reservation",
            JSONObject().put("p_reservation_id", reservationId),
            token
        )
    }


    fun getMyRestaurantPermissions(token: String): JSONArray {
        require(token.isNotBlank()) { "Inicia sessão para consultar as permissões." }
        return JSONArray(call(
            "POST",
            "/rest/v1/rpc/get_my_restaurant_permissions",
            JSONObject(),
            token
        ))
    }

    fun getRestaurantStaffPermissions(token: String, userId: String): JSONArray {
        require(token.isNotBlank()) { "Inicia sessão para consultar as permissões da equipa." }
        require(runCatching { java.util.UUID.fromString(userId).toString() == userId }.getOrDefault(false)) {
            "Seleciona um funcionário válido."
        }
        return JSONArray(call(
            "POST",
            "/rest/v1/rpc/get_restaurant_staff_permissions",
            JSONObject().put("p_user_id", userId),
            token
        ))
    }

    fun setRestaurantStaffPermissions(
        token: String,
        userId: String,
        permissions: Set<String>
    ): JSONArray {
        require(token.isNotBlank()) { "Inicia sessão para alterar as permissões da equipa." }
        require(runCatching { java.util.UUID.fromString(userId).toString() == userId }.getOrDefault(false)) {
            "Seleciona um funcionário válido."
        }
        val payload = StaffPermissionSet.toJsonArray(permissions)
        return JSONArray(call(
            "POST",
            "/rest/v1/rpc/set_restaurant_staff_permissions",
            JSONObject().put("p_user_id", userId).put("p_permissions", payload),
            token
        ))
    }

    fun listRestaurantTeam(token: String): JSONArray {
        return JSONArray(call(
            "POST",
            "/rest/v1/rpc/list_restaurant_team",
            JSONObject(),
            token
        ))
    }

    fun removeRestaurantStaff(token: String, userId: String) {
        require(userId.isNotBlank()) { "Seleciona um membro válido da equipa." }
        call(
            "POST",
            "/rest/v1/rpc/remove_restaurant_staff",
            JSONObject().put("p_user_id", userId),
            token
        )
    }


    data class AssistantInvoiceUpload(val invoiceId: String, val storagePath: String)

    fun listAssistantConversations(token: String, query: String = ""): JSONArray {
        require(token.isNotBlank()) { "Inicia sessão para consultar o histórico do assistente." }
        require(query.length <= 100) { "A pesquisa do histórico é demasiado longa." }
        return JSONArray(call(
            "POST",
            "/rest/v1/rpc/studiomesa_assistant_list_conversations",
            JSONObject().put("p_query", query),
            token
        ))
    }

    fun createAssistantConversation(token: String, title: String = "Nova conversa"): JSONObject {
        require(token.isNotBlank()) { "Inicia sessão para criar uma conversa." }
        val rows = JSONArray(call(
            "POST",
            "/rest/v1/rpc/studiomesa_assistant_create_conversation",
            JSONObject().put("p_title", title.take(120)),
            token
        ))
        return rows.optJSONObject(0) ?: throw IllegalStateException(
            "O servidor não devolveu a nova conversa."
        )
    }

    fun listAssistantMessages(token: String, conversationId: String): JSONArray {
        require(token.isNotBlank()) { "Inicia sessão para consultar a conversa." }
        require(isCanonicalUuid(conversationId)) { "A conversa selecionada não é válida." }
        return JSONArray(call(
            "POST",
            "/rest/v1/rpc/studiomesa_assistant_list_messages",
            JSONObject().put("p_conversation_id", conversationId),
            token
        ))
    }

    fun deleteAssistantConversation(token: String, conversationId: String) {
        require(token.isNotBlank()) { "Inicia sessão para eliminar a conversa." }
        require(isCanonicalUuid(conversationId)) { "A conversa selecionada não é válida." }
        call(
            "POST",
            "/rest/v1/rpc/studiomesa_assistant_delete_conversation",
            JSONObject().put("p_conversation_id", conversationId),
            token
        )
    }

    fun sendAssistantMessage(
        token: String,
        conversationId: String,
        message: String,
        webSearch: Boolean,
        invoiceIds: List<String>
    ): JSONObject {
        require(token.isNotBlank()) { "Inicia sessão para utilizar o assistente." }
        require(isCanonicalUuid(conversationId)) { "A conversa selecionada não é válida." }
        require(message.trim().length in 1..5000) { "A pergunta deve ter entre 1 e 5000 caracteres." }
        require(invoiceIds.size <= 3 && invoiceIds.all(::isCanonicalUuid) && invoiceIds.distinct().size == invoiceIds.size) {
            "Seleciona no máximo três faturas válidas."
        }
        val body = JSONObject()
            .put("conversation_id", conversationId)
            .put("message", message.trim())
            .put("web_search", webSearch)
            .put("invoice_ids", JSONArray(invoiceIds))
        return JSONObject(call(
            "POST",
            "/functions/v1/studiomesa-assistant",
            body,
            token
        ))
    }

    fun listAssistantInvoices(token: String): JSONArray {
        require(token.isNotBlank()) { "Inicia sessão para consultar o arquivo de faturas." }
        return JSONArray(call(
            "POST",
            "/rest/v1/rpc/studiomesa_assistant_list_invoices",
            JSONObject(),
            token
        ))
    }

    fun registerAssistantInvoice(
        token: String,
        invoiceId: String,
        fileName: String,
        fileSizeBytes: Int
    ): AssistantInvoiceUpload {
        require(token.isNotBlank()) { "Inicia sessão para guardar a fatura." }
        require(isCanonicalUuid(invoiceId)) { "O identificador da fatura não é válido." }
        require(fileSizeBytes in 1..(10 * 1024 * 1024)) { "O PDF excede o limite de 10 MB." }
        val rows = JSONArray(call(
            "POST",
            "/rest/v1/rpc/studiomesa_assistant_register_invoice",
            JSONObject()
                .put("p_invoice_id", invoiceId)
                .put("p_file_name", fileName.take(180))
                .put("p_file_size_bytes", fileSizeBytes),
            token
        ))
        val row = rows.optJSONObject(0)
            ?: throw IllegalStateException("O servidor não preparou o arquivo da fatura.")
        val returnedId = row.optString("invoice_id")
        val storagePath = row.optString("storage_path")
        require(returnedId == invoiceId && storagePath.matches(
            Regex("^[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}\\\\.pdf$")
        )) {
            "O servidor devolveu um destino de arquivo inválido."
        }
        return AssistantInvoiceUpload(returnedId, storagePath)
    }

    fun uploadAssistantInvoicePdf(token: String, storagePath: String, bytes: ByteArray) {
        require(token.isNotBlank()) { "Inicia sessão para carregar a fatura." }
        require(bytes.isNotEmpty() && bytes.size <= 10 * 1024 * 1024) {
            "O PDF deve ter até 10 MB."
        }
        require(storagePath.matches(
            Regex("^[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}\\\\.pdf$")
        )) {
            "O caminho privado da fatura não é válido."
        }
        val endpoint = if (BuildConfig.DEBUG) instrumentationTestEndpoint else null
        val baseUrl = endpoint?.baseUrl ?: BASE_URL
        val connection = (URL(
            baseUrl + "/storage/v1/object/restaurant-financial-invoices/" + storagePath
        ).openConnection() as HttpURLConnection).apply {
            if (endpoint != null) {
                val secure = this as? HttpsURLConnection
                    ?: throw IllegalStateException("O arquivo de teste tem de usar HTTPS.")
                secure.sslSocketFactory = endpoint.sslSocketFactory
            }
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = false
            useCaches = false
            doOutput = true
            setRequestProperty("apikey", API_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/pdf")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("x-upsert", "false")
        }
        try {
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = ResponseBodyReader.read(stream)
            if (code !in 200..299) {
                val message = try {
                    val error = JSONObject(response)
                    sequenceOf("message", "error", "msg")
                        .map { error.optString(it, "") }
                        .firstOrNull { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                } catch (_: Exception) {
                    null
                }
                throw BackendHttpException(
                    code,
                    "/storage/v1/object/restaurant-financial-invoices",
                    ApiErrorMessage.sanitize(message, code)
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    fun completeAssistantInvoiceUpload(token: String, invoiceId: String) {
        require(token.isNotBlank()) { "Inicia sessão para confirmar a fatura." }
        require(isCanonicalUuid(invoiceId)) { "O identificador da fatura não é válido." }
        call(
            "POST",
            "/rest/v1/rpc/studiomesa_assistant_complete_invoice_upload",
            JSONObject().put("p_invoice_id", invoiceId),
            token
        )
    }

    private fun isCanonicalUuid(value: String): Boolean =
        runCatching { java.util.UUID.fromString(value).toString() == value }.getOrDefault(false)

}
