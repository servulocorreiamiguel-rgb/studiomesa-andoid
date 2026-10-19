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

    data class Session(val accessToken: String, val userId: String, val email: String)

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
        return Session(token, user.optString("id"), user.optString("email", email.trim()))
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
