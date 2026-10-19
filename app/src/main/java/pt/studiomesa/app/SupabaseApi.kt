package pt.studiomesa.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal Supabase REST/Auth client. Only the public publishable key is embedded here.
 * All data-changing RPCs enforce authorization in PostgreSQL; never embed service_role keys.
 */
object SupabaseApi {
    private const val BASE_URL = "https://svwvkfsyevuyabelrtjk.supabase.co"
    private const val API_KEY = "sb_publishable_ioJIukpwd0_Fyajd41QL_Q_M6WKE0UH"

    data class Session(val accessToken: String, val userId: String, val email: String, val refreshToken: String)

    private fun call(
        method: String,
        path: String,
        body: JSONObject? = null,
        accessToken: String? = null
    ): String {
        val connection = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("apikey", API_KEY)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            // Publishable keys are not JWTs: send them only via apikey.
            // Authorization must carry a real user access token, and only after sign-in.
            if (!accessToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer " + accessToken)
            }
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = try {
                    JSONObject(text).optString("msg").ifBlank {
                        JSONObject(text).optString("message").ifBlank {
                            JSONObject(text).optString("error_description").ifBlank { text }
                        }
                    }
                } catch (_: Exception) { text }
                throw IllegalStateException(message.ifBlank { "Erro de ligação ($code)." })
            }
            return text
        } finally {
            connection.disconnect()
        }
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

    fun signUp(email: String, password: String): JSONObject {
        val redirect = URLEncoder.encode("studiomesa://auth-callback", "UTF-8")
        val result = jsonCall("POST", "/auth/v1/signup?redirect_to=$redirect",
            JSONObject().put("email", email.trim()).put("password", password))
        // GoTrue signup commonly returns the user at the JSON root, not in "user".
        // Normalize the response so the UI can recognize that the account was created.
        if (result.optJSONObject("user") == null && result.optString("id").isNotBlank()) {
            result.put("user", JSONObject()
                .put("id", result.optString("id"))
                .put("email", result.optString("email", email.trim())))
        }
        return result
    }

    fun getUser(accessToken: String): JSONObject =
        jsonCall("GET", "/auth/v1/user", token = accessToken)

    fun isOAuthProviderEnabled(provider: String): Boolean {
        require(provider == "google" || provider == "azure") { "Fornecedor de início de sessão inválido." }
        val settings = jsonCall("GET", "/auth/v1/settings")
        return settings.optJSONObject("external")?.optBoolean(provider, false) ?: false
    }

    fun oauthUrl(provider: String): String {
        require(provider == "google" || provider == "azure") { "Fornecedor de início de sessão inválido." }
        val redirect = URLEncoder.encode("studiomesa://auth-callback", "UTF-8")
        val scopes = if (provider == "azure") "&scopes=email" else ""
        return "$BASE_URL/auth/v1/authorize?provider=$provider&redirect_to=$redirect&apikey=$API_KEY$scopes"
    }

    fun createRestaurant(token: String, name: String, code: String): String {
        val response = call("POST", "/rest/v1/rpc/create_restaurant",
            JSONObject().put("p_name", name.trim()).put("p_join_code", code.trim().uppercase()), token)
        return response.trim().trim('"')
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
            "/rest/v1/restaurant_tables?select=id,table_number,is_occupied&restaurant_id=eq.$encoded&order=table_number.asc",
            null,
            token
        ))
    }

    fun updateTable(token: String, tableId: String, occupied: Boolean) {
        val encoded = URLEncoder.encode(tableId, "UTF-8")
        call(
            "PATCH",
            "/rest/v1/restaurant_tables?id=eq.$encoded",
            JSONObject().put("is_occupied", occupied),
            token
        )
    }

    fun listMenuItems(token: String, restaurantId: String): JSONArray {
        val encoded = URLEncoder.encode(restaurantId, "UTF-8")
        return JSONArray(call(
            "GET",
            "/rest/v1/menu_items?select=id,name,price,cost,is_active&restaurant_id=eq.$encoded&is_active=eq.true&order=name.asc",
            null,
            token
        ))
    }

    fun addMenuItem(token: String, restaurantId: String, name: String, price: Double, cost: Double) {
        call(
            "POST",
            "/rest/v1/menu_items",
            JSONObject()
                .put("restaurant_id", restaurantId)
                .put("name", name.trim())
                .put("price", price)
                .put("cost", cost)
                .put("is_active", true),
            token
        )
    }

    fun deactivateMenuItem(token: String, itemId: String) {
        val encoded = URLEncoder.encode(itemId, "UTF-8")
        call(
            "PATCH",
            "/rest/v1/menu_items?id=eq.$encoded",
            JSONObject().put("is_active", false),
            token
        )
    }

    fun listOrders(token: String, restaurantId: String): JSONArray {
        val encoded = URLEncoder.encode(restaurantId, "UTF-8")
        return JSONArray(call(
            "GET",
            "/rest/v1/restaurant_orders?select=id,table_number,item_name,amount,status,created_at&restaurant_id=eq.$encoded&order=created_at.desc",
            null,
            token
        ))
    }

    fun createOrder(token: String, restaurantId: String, tableNumber: Int, itemName: String, amount: Double) {
        call(
            "POST",
            "/rest/v1/restaurant_orders",
            JSONObject()
                .put("restaurant_id", restaurantId)
                .put("table_number", tableNumber)
                .put("item_name", itemName)
                .put("amount", amount)
                .put("status", "Aberto"),
            token
        )
    }

    fun updateOrder(token: String, orderId: String, status: String) {
        val encoded = URLEncoder.encode(orderId, "UTF-8")
        call(
            "PATCH",
            "/rest/v1/restaurant_orders?id=eq.$encoded",
            JSONObject().put("status", status),
            token
        )
    }

    fun getMyRestaurant(token: String): JSONObject? {
        val response = call("POST", "/rest/v1/rpc/get_my_restaurant_details", JSONObject(), token)
        val restaurants = JSONArray(response)
        return if (restaurants.length() == 0) null else restaurants.getJSONObject(0)
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

    fun listCustomerMenu(token: String, memberId: String): JSONArray {
        val response = call(
            "POST",
            "/rest/v1/rpc/get_member_restaurant_menu",
            JSONObject().put("p_member_id", memberId),
            token
        )
        return JSONArray(response)
    }

    fun listMembers(token: String, restaurantId: String): JSONArray {
        val encoded = URLEncoder.encode(restaurantId, "UTF-8")
        return JSONArray(call("GET",
            "/rest/v1/loyalty_members?select=id,member_code,display_name,points&restaurant_id=eq.$encoded&order=created_at.desc",
            null, token))
    }

    fun adjustPoints(token: String, memberId: String, delta: Int, note: String? = null): JSONObject {
        val body = JSONObject().put("p_member_id", memberId).put("p_delta", delta)
        if (!note.isNullOrBlank()) body.put("p_note", note)
        val response = call("POST", "/rest/v1/rpc/adjust_loyalty_points", body, token)
        val array = JSONArray(response)
        if (array.length() == 0) throw IllegalStateException("Não foi devolvido o novo saldo.")
        return array.getJSONObject(0)
    }

    fun getMember(token: String, memberId: String): JSONObject {
        val encoded = URLEncoder.encode(memberId, "UTF-8")
        val array = JSONArray(call("GET",
            "/rest/v1/loyalty_members?select=id,member_code,display_name,points,restaurant_id&id=eq.$encoded",
            null, token))
        if (array.length() == 0) throw IllegalStateException("Cartão de cliente não encontrado.")
        return array.getJSONObject(0)
    }
}
