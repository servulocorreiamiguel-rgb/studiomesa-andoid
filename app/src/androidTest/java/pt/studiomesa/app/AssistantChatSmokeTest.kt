package pt.studiomesa.app

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Base64
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Exercises the owner-facing assistant chat through the actual Activity and API client,
 * using only a local HTTPS Supabase-compatible stub. No provider key, real restaurant,
 * hosted Supabase endpoint or invoice document is used.
 */
class AssistantChatSmokeTest {
    private val userId = "a180a7f0-0f0e-4e9f-9e33-a5d9d6a00001"
    private val restaurantId = "a180a7f0-0f0e-4e9f-9e33-a5d9d6a00002"
    private val conversationId = "a180a7f0-0f0e-4e9f-9e33-a5d9d6a00003"
    private val userMessageId = "a180a7f0-0f0e-4e9f-9e33-a5d9d6a00004"
    private val assistantMessageId = "a180a7f0-0f0e-4e9f-9e33-a5d9d6a00005"
    private val testAccessToken: String by lazy { createFutureJwt() }

    private val backendKeys = setOf(
        "user_id",
        "user_email",
        "restaurant_id",
        "restaurant_name",
        "join_code",
        "staff_join_code",
        "restaurant_role",
        "local_demo_data_isolated_v1"
    )

    private var server: MockWebServer? = null
    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private lateinit var secureSession: SecureSessionStore

    private var previousPreferences: Map<String, Any?> = emptyMap()
    private var previousAccessToken = ""
    private var previousRefreshToken = ""
    private var previousPendingVerifier = ""
    private var previousPendingFlow = ""

    private val createConversationReceived = CountDownLatch(1)
    private val assistantRequestReceived = CountDownLatch(1)
    private val messagesAfterAnswerReceived = CountDownLatch(1)
    private val historySearchReceived = CountDownLatch(1)
    private val assistantRequest = AtomicReference<JSONObject?>()
    private val authorizationHeaders = mutableListOf<String>()
    private val messages = mutableListOf<JSONObject>()
    private val unexpectedRequest = AtomicReference<String?>()
    private var conversationTitle = "Nova conversa"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        assumeTrue(
            "The assistant workspace is tested only in Professional/Premium restaurant builds.",
            context.packageName.endsWith(".profissional.restaurante") ||
                context.packageName.endsWith(".premium.restaurante")
        )

        preferences = context.getSharedPreferences("studiomesa_backend", Context.MODE_PRIVATE)
        secureSession = SecureSessionStore(context)
        previousPreferences = preferences.all.filterKeys { it in backendKeys }
        previousAccessToken = secureSession.accessToken()
        previousRefreshToken = secureSession.refreshToken()
        previousPendingVerifier = secureSession.pendingCodeVerifier()
        previousPendingFlow = secureSession.pendingCodeFlowType()

        secureSession.clear()
        preferences.edit().apply {
            backendKeys.forEach { remove(it) }
            putBoolean("local_demo_data_isolated_v1", true)
        }.commit()

        val certificate = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1")
            .build()
        val serverTls = HandshakeCertificates.Builder()
            .heldCertificate(certificate)
            .build()
        val clientTls = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()

        val mockServer = MockWebServer()
        mockServer.useHttps(serverTls.sslSocketFactory(), false)
        mockServer.dispatcher = assistantDispatcher()
        mockServer.start()
        server = mockServer

        SupabaseApi.configureInstrumentationTestEndpoint(
            mockServer.url("/").toString().removeSuffix("/"),
            clientTls.sslSocketFactory()
        )
    }

    @After
    fun tearDown() {
        runCatching { SupabaseApi.configureInstrumentationTestEndpoint(null) }
        runCatching { server?.shutdown() }
        if (::preferences.isInitialized && ::secureSession.isInitialized) {
            secureSession.clear()
            val editor = preferences.edit()
            backendKeys.forEach { editor.remove(it) }
            previousPreferences.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                }
            }
            editor.commit()
            if (previousAccessToken.isNotBlank() || previousRefreshToken.isNotBlank()) {
                secureSession.save(previousAccessToken, previousRefreshToken)
            }
            if (previousPendingVerifier.isNotBlank() &&
                previousPendingFlow in setOf("oauth", "recovery", "signup")) {
                secureSession.savePendingCodeVerifier(previousPendingVerifier, previousPendingFlow)
            }
        }
    }

    @Test
    fun ownerCanCreateChatSearchHistoryAndOpenWebSources() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Install the test owner session only after the logged-out Activity is created.
            secureSession.save(testAccessToken, "test-refresh-token")
            preferences.edit()
                .putString("user_id", userId)
                .putString("restaurant_id", restaurantId)
                .putString("restaurant_name", "Restaurante de Teste")
                .putString("restaurant_role", "owner")
                .commit()

            onView(withContentDescription("Abrir menu de navegação")).perform(click())
            onView(withContentDescription("Abrir secção Assistente")).perform(click())

            awaitText("O teu consultor de negócio.")
            awaitText("+ Nova conversa")
            assertTrue(
                "The assistant workspace did not load its conversation and invoice lists.",
                historySearchReceived.await(10, TimeUnit.SECONDS)
            )

            onView(withText("+ Nova conversa")).perform(scrollTo(), click())
            assertTrue(
                "The assistant conversation was not created.",
                createConversationReceived.await(10, TimeUnit.SECONDS)
            )

            onView(withHint("Ex.: Analisa os custos e sugere três formas de aumentar a margem."))
                .perform(scrollTo(), replaceText("Como reduzo o desperdício alimentar sem perder qualidade?"))
            onView(withText("Pesquisa web: desligada")).perform(scrollTo(), click())
            awaitText("Pesquisa web: ligada")
            onView(withText("Enviar mensagem")).perform(scrollTo(), click())

            assertTrue(
                "The assistant Edge Function request was not received.",
                assistantRequestReceived.await(10, TimeUnit.SECONDS)
            )
            assertTrue(
                "The assistant messages were not reloaded after the answer.",
                messagesAfterAnswerReceived.await(10, TimeUnit.SECONDS)
            )

            awaitText("Organiza a preparação por lotes e regista o desperdício por ingrediente.")
            awaitText("Fonte: Fonte de teste")

            val request = requireNotNull(assistantRequest.get())
            assertEquals(conversationId, request.getString("conversation_id"))
            assertEquals("Como reduzo o desperdício alimentar sem perder qualidade?", request.getString("message"))
            assertTrue("The web-search option must be sent to the server.", request.getBoolean("web_search"))
            assertEquals(0, request.getJSONArray("invoice_ids").length())

            onView(withHint("Pesquisar conversas e mensagens"))
                .perform(scrollTo(), replaceText("desperdício"))
            onView(withText("Pesquisar")).perform(scrollTo(), click())
            assertTrue("The history search RPC was not received.", historySearchReceived.await(10, TimeUnit.SECONDS))
            awaitText("Como reduzo o desperdício alimentar sem perder qualidade?")

            assertTrue(
                "Every request must use the authenticated owner access token.",
                authorizationHeaders.isNotEmpty() &&
                    authorizationHeaders.all { it == "Bearer $testAccessToken" }
            )
            assertNull(unexpectedRequest.get())
        }
    }

    private fun assistantDispatcher(): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            synchronized(authorizationHeaders) {
                authorizationHeaders.add(request.getHeader("Authorization").orEmpty())
            }
            val path = request.path?.substringBefore("?")
            return when (path) {
                "/rest/v1/rpc/studiomesa_assistant_list_conversations" -> {
                    val payload = JSONObject(request.body.readUtf8())
                    if (payload.optString("p_query") == "desperdício") {
                        historySearchReceived.countDown()
                    }
                    jsonResponse(conversationRows())
                }

                "/rest/v1/rpc/studiomesa_assistant_list_invoices" ->
                    jsonResponse("[]")

                "/rest/v1/rpc/studiomesa_assistant_create_conversation" -> {
                    createConversationReceived.countDown()
                    jsonResponse(
                        JSONArray().put(
                            JSONObject()
                                .put("conversation_id", conversationId)
                                .put("title", "Nova conversa")
                                .put("created_at", "2026-11-03T12:00:00Z")
                        ).toString()
                    )
                }

                "/functions/v1/studiomesa-assistant" -> {
                    val payload = JSONObject(request.body.readUtf8())
                    assistantRequest.set(payload)
                    conversationTitle = payload.getString("message").take(72)
                    synchronized(messages) {
                        messages.clear()
                        messages.add(
                            JSONObject()
                                .put("message_id", userMessageId)
                                .put("role", "user")
                                .put("content", payload.getString("message"))
                                .put("citations", JSONArray())
                                .put("created_at", "2026-11-03T12:01:00Z")
                        )
                        messages.add(
                            JSONObject()
                                .put("message_id", assistantMessageId)
                                .put("role", "assistant")
                                .put("content", "Organiza a preparação por lotes e regista o desperdício por ingrediente.")
                                .put("citations", JSONArray().put(
                                    JSONObject()
                                        .put("title", "Fonte de teste")
                                        .put("url", "https://example.com/restaurant-waste")
                                ))
                                .put("created_at", "2026-11-03T12:01:02Z")
                        )
                    }
                    assistantRequestReceived.countDown()
                    jsonResponse(
                        JSONObject()
                            .put("message_id", assistantMessageId)
                            .put("response", "Organiza a preparação por lotes e regista o desperdício por ingrediente.")
                            .put("citations", JSONArray().put(
                                JSONObject()
                                    .put("title", "Fonte de teste")
                                    .put("url", "https://example.com/restaurant-waste")
                            ))
                            .put("invoice_count", 0)
                            .put("web_search_used", true)
                            .toString()
                    )
                }

                "/rest/v1/rpc/studiomesa_assistant_list_messages" -> {
                    messagesAfterAnswerReceived.countDown()
                    val rows = JSONArray()
                    synchronized(messages) {
                        messages.forEach { rows.put(JSONObject(it.toString())) }
                    }
                    jsonResponse(rows.toString())
                }

                else -> {
                    unexpectedRequest.set(request.method + " " + path)
                    jsonResponse(JSONObject().put("message", "Unexpected endpoint").toString(), 404)
                }
            }
        }
    }

    private fun conversationRows(): String {
        if (conversationTitle == "Nova conversa") return "[]"
        return JSONArray().put(
            JSONObject()
                .put("conversation_id", conversationId)
                .put("title", conversationTitle)
                .put("created_at", "2026-11-03T12:00:00Z")
                .put("updated_at", "2026-11-03T12:01:02Z")
                .put("last_message_preview", "Organiza a preparação por lotes e regista o desperdício.")
        ).toString()
    }

    private fun awaitText(text: String) {
        val deadline = SystemClock.uptimeMillis() + 10_000L
        var lastError: Throwable? = null
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                onView(withText(text)).check(matches(isDisplayed()))
                return
            } catch (error: Throwable) {
                lastError = error
                SystemClock.sleep(50)
            }
        }
        throw AssertionError("Timed out waiting for visible text: $text", lastError)
    }

    private fun jsonResponse(body: String, status: Int = 200): MockResponse =
        MockResponse()
            .setResponseCode(status)
            .setHeader("Content-Type", "application/json; charset=utf-8")
            .setBody(body)

    private fun createFutureJwt(): String {
        val payload = JSONObject()
            .put("exp", System.currentTimeMillis() / 1000L + 3600L)
            .toString()
        val encoded = Base64.encodeToString(
            payload.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return "e30.$encoded.test-signature"
    }
}
