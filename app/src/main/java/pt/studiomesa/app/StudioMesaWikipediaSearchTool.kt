package pt.studiomesa.app

import com.google.ai.edge.litertlm.OpenApiTool
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * Read-only public web lookup for general facts. Sends only the model-selected
 * short query to Wikipedia; it never receives the restaurant data snapshot.
 */
internal class StudioMesaWikipediaSearchTool : OpenApiTool {
    override fun getToolDescriptionJsonString(): String {
        val query = JSONObject()
            .put("type", "string")
            .put("description", "Consulta curta e genérica sobre um facto público. Nunca incluas dados do restaurante, clientes, equipa, vendas, faturas, preços internos, contactos ou identificadores.")
        return JSONObject()
            .put("name", "search_public_wikipedia")
            .put(
                "description",
                "Pesquisa factos públicos na Wikipédia em português e, se não encontrar resultados, em inglês. Usa apenas quando o utilizador pede informação pública que possa precisar de consulta externa. Envia apenas uma consulta genérica, nunca dados internos do StudioMesa ou dados pessoais. Não serve para notícias de última hora, preços atuais, aconselhamento profissional ou confirmação de factos críticos. Devolve títulos, excertos e ligações para que a resposta possa citar as fontes."
            )
            .put("parameters", JSONObject()
                .put("type", "object")
                .put("properties", JSONObject().put("query", query))
                .put("required", JSONArray().put("query")))
            .toString()
    }

    override fun execute(paramsJsonString: String): String = runCatching {
        val query = JSONObject(paramsJsonString).optString("query").trim()
        if (query.length !in MIN_QUERY_CHARS..MAX_QUERY_CHARS || query.count(Char::isDigit) >= 9 ||
            query.contains('@') || query.contains(Regex("(?i)\b(email|telefone|telemovel|telemóvel|nif|nipc)\b"))
        ) {
            return@runCatching JSONObject()
                .put("error", "A pesquisa só aceita perguntas curtas sobre informação pública. Remove dados pessoais ou do restaurante.")
                .toString()
        }

        val portuguese = searchLanguage("pt", query)
        val language = if (portuguese.length() > 0) "pt" else "en"
        val pages = if (language == "pt") portuguese else searchLanguage("en", query)
        if (pages.length() == 0) {
            return@runCatching JSONObject()
                .put("query", query)
                .put("results", JSONArray())
                .put("source", "Wikipedia")
                .put("message", "Não foram encontrados resultados na Wikipédia em português ou inglês.")
                .toString()
        }
        JSONObject()
            .put("query", query)
            .put("language", language)
            .put("source", "Wikipedia")
            .put("results", pages)
            .put("note", "Fonte enciclopédica; confirma informação crítica ou muito recente noutras fontes.")
            .toString()
    }.getOrElse {
        JSONObject()
            .put("error", "A pesquisa pública não está disponível neste momento. Verifica a ligação e tenta novamente.")
            .toString()
    }

    private fun searchLanguage(language: String, query: String): JSONArray {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val address = "https://$language.wikipedia.org/w/api.php" +
            "?action=query&generator=search&gsrsearch=$encoded&gsrnamespace=0&gsrlimit=$MAX_RESULTS" +
            "&prop=extracts%7Cinfo&exintro=1&explaintext=1&exsentences=3&inprop=url&format=json&formatversion=2"
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty(
                "User-Agent",
                "StudioMesa-LocalAssistant/1.0 (https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid)"
            )
            setRequestProperty(
                "Api-User-Agent",
                "StudioMesa-LocalAssistant/1.0 (https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid)"
            )
        }
        try {
            val status = connection.responseCode
            if (status == 429 || status == 503) {
                throw IllegalStateException("A fonte pública está a limitar pedidos.")
            }
            if (status !in 200..299) throw IllegalStateException("Pesquisa HTTP $status")
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_RESPONSE_BYTES) throw IllegalStateException("Resposta demasiado grande")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val response = JSONObject(String(bytes, Charsets.UTF_8))
            val sourcePages = response.optJSONObject("query")?.optJSONArray("pages") ?: JSONArray()
            val results = JSONArray()
            for (index in 0 until minOf(sourcePages.length(), MAX_RESULTS)) {
                val page = sourcePages.optJSONObject(index) ?: continue
                val title = page.optString("title").trim()
                val extract = page.optString("extract").trim()
                val url = page.optString("fullurl").trim()
                if (title.isBlank() || extract.isBlank() || !url.startsWith("https://$language.wikipedia.org/")) continue
                results.put(
                    JSONObject()
                        .put("title", title.take(MAX_TITLE_CHARS))
                        .put("extract", extract.take(MAX_EXTRACT_CHARS))
                        .put("url", url)
                )
            }
            return results
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MIN_QUERY_CHARS = 3
        const val MAX_QUERY_CHARS = 120
        const val MAX_RESULTS = 3
        const val MAX_TITLE_CHARS = 160
        const val MAX_EXTRACT_CHARS = 900
        const val MAX_RESPONSE_BYTES = 64 * 1024
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 12_000
    }
}
