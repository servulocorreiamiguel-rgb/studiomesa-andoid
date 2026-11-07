package pt.studiomesa.app

import android.content.Context
import com.google.ai.edge.litertlm.OpenApiTool
import org.json.JSONArray
import org.json.JSONObject

/** Public web search. Only a short public query is sent; provider credentials stay server-side. */
internal class StudioMesaWebSearchTool(context: Context) : OpenApiTool {
    private val sessions = SecureSessionStore(context.applicationContext)

    override fun getToolDescriptionJsonString(): String {
        val query = JSONObject()
            .put("type", "string")
            .put("description", "Pergunta curta sobre informação pública. Nunca incluas dados pessoais, dados do restaurante, clientes, equipa, vendas, faturas, fornecedores, credenciais ou identificadores privados.")
        return JSONObject()
            .put("name", "search_public_web")
            .put("description", "Pesquisa na Web pública para informação atual, notícias, legislação, afirmações controversas ou pedidos de verificação factual. Usa uma consulta curta e genérica. Os resultados são pistas, não prova: compara fontes independentes, prefere fontes oficiais e documentos primários, repara nas datas e inclui as ligações devolvidas. Nunca envies dados privados do StudioMesa.")
            .put("parameters", JSONObject()
                .put("type", "object")
                .put("properties", JSONObject().put("query", query))
                .put("required", JSONArray().put("query")))
            .toString()
    }

    override fun execute(paramsJsonString: String): String = runCatching {
        val query = JSONObject(paramsJsonString).optString("query").trim()
        if (query.length !in MIN_QUERY_CHARS..MAX_QUERY_CHARS ||
            EMAIL_PATTERN.containsMatchIn(query) ||
            LONG_NUMBER_PATTERN.containsMatchIn(query) ||
            PRIVATE_ID_PATTERN.containsMatchIn(query)
        ) {
            return@runCatching errorResult("A pesquisa só aceita uma pergunta pública curta. Remove dados pessoais ou identificadores.")
        }

        val token = sessions.accessToken()
        if (token.isBlank()) {
            return@runCatching JSONObject()
                .put("error", "A pesquisa Web completa exige que inicies sessão na StudioMesa. A pesquisa enciclopédica continua disponível.")
                .put("code", "WEB_SEARCH_REQUIRES_LOGIN")
                .toString()
        }

        SupabaseApi.searchPublicWeb(token, query).put("query", query).toString()
    }.getOrElse {
        errorResult(it.message?.take(MAX_ERROR_CHARS)
            ?: "A pesquisa Web não está disponível. Tenta novamente mais tarde.")
    }

    private fun errorResult(message: String): String = JSONObject().put("error", message).toString()

    private companion object {
        const val MIN_QUERY_CHARS = 3
        const val MAX_QUERY_CHARS = 200
        const val MAX_ERROR_CHARS = 300
        val EMAIL_PATTERN = Regex("""[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}""", RegexOption.IGNORE_CASE)
        val LONG_NUMBER_PATTERN = Regex("""(?<!\d)(?:\d[ -]?){9,}(?!\d)""")
        val PRIVATE_ID_PATTERN = Regex("""(?i)\b(?:NIF|NIPC|IBAN)\s*[:#]?\s*[A-Z0-9 -]{8,}""")
    }
}
