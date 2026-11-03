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
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
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
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Authenticated customer reservation flow against a local HTTPS Supabase-compatible stub.
 * No production account, restaurant data, API credential or live Supabase endpoint is used.
 */
class CustomerReservationFlowSmokeTest {
    private val userId = "5a5f2dc8-30fd-4fbd-9c13-e327e7d4e6f1"
    private val memberId = "b4f7c320-6b52-4b43-8d51-f6b6e8b7d101"
    private val reservationId = "c9c0a19a-7da8-4c94-a12f-04a7c75c138d"
    private val testAccessToken: String by lazy { createFutureJwt() }

    private var server: MockWebServer? = null
    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private lateinit var pendingPreferences: SharedPreferences
    private lateinit var secureSession: SecureSessionStore

    private var previousPreferences: Map<String, Any?> = emptyMap()
    private var previousPendingPreferences: Map<String, Any?> = emptyMap()
    private var previousAccessToken = ""
    private var previousRefreshToken = ""
    private var previousPendingVerifier = ""
    private var previousPendingFlow = ""

    private val reservationRows = mutableListOf<JSONObject>()
    private val listRequestCount = AtomicInteger(0)
    private val createPayload = AtomicReference<JSONObject?>()
    private val lastAuthorization = AtomicReference<String?>()
    private val initialListReceived = CountDownLatch(1)
    private val listAfterCreateReceived = CountDownLatch(1)
    private val listAfterCancelReceived = CountDownLatch(1)
    private val createReceived = CountDownLatch(1)
    private val cancelReceived = CountDownLatch(1)

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        assumeTrue("This flow runs only in the customer application.", context.packageName.endsWith(".cliente"))

        preferences = context.getSharedPreferences("studiomesa_backend", Context.MODE_PRIVATE)
        pendingPreferences = context.getSharedPreferences("studiomesa_pending_requests", Context.MODE_PRIVATE)
        secureSession = SecureSessionStore(context)

        previousPendingPreferences = pendingPreferences.all
        previousAccessToken = secureSession.accessToken()
        previousRefreshToken = secureSession.refreshToken()
        previousPendingVerifier = secureSession.pendingCodeVerifier()
        previousPendingFlow = secureSession.pendingCodeFlowType()

        val keys = setOf("user_id", "member_id", "member_restaurant_name", "local_demo_data_isolated_v1")
        previousPreferences = preferences.all.filterKeys { it in keys }
        pendingPreferences.edit().clear().commit()

        // Start the Activity logged out. The fake account is installed after onCreate so that
        // startup recovery does not make unrelated membership or menu requests.
        secureSession.clear()
        preferences.edit()
            .remove("user_id")
            .remove("member_id")
            .remove("member_restaurant_name")
            .putBoolean("local_demo_data_isolated_v1", true)
            .commit()

        val serverCertificate = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1")
            .build()
        val serverTls = HandshakeCertificates.Builder()
            .heldCertificate(serverCertificate)
            .build()
        val clientTls = HandshakeCertificates.Builder()
            .addTrustedCertificate(serverCertificate.certificate)
            .build()

        val mockServer = MockWebServer()
        mockServer.useHttps(serverTls.sslSocketFactory(), false)
        mockServer.dispatcher = reservationDispatcher()
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
            val pendingEditor = pendingPreferences.edit().clear()
            previousPendingPreferences.forEach { (key, value) ->
                if (value is String) pendingEditor.putString(key, value)
            }
            pendingEditor.commit()

            val editor = preferences.edit()
            setOf("user_id", "member_id", "member_restaurant_name", "local_demo_data_isolated_v1")
                .forEach { editor.remove(it) }
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
    fun authenticatedCustomerCanCreateListAndCancelReservation() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Install a synthetic, unexpired session only after the logged-out Activity launched.
            secureSession.save(testAccessToken, "test-refresh-token")
            preferences.edit()
                .putString("user_id", userId)
                .putString("member_id", memberId)
                .putString("member_restaurant_name", "Restaurante de teste")
                .commit()

            onView(withContentDescription("Abrir secção Reservas")).perform(click())
            assertTrue("The initial reservation list request was not received.", initialListReceived.await(10, TimeUnit.SECONDS))
            awaitText("Ainda não tens reservas.")

            onView(withText("+  Pedir uma reserva")).perform(scrollTo(), click())
            onView(withHint("Nome para a reserva")).perform(replaceText("Miguel Teste"))
            onView(withHint("Contacto telefónico")).perform(replaceText("912345678"))

            onView(withText("Enviar pedido")).perform(click())
            assertTrue("The reservation creation RPC was not received.", createReceived.await(10, TimeUnit.SECONDS))
            assertTrue("The updated reservation list was not received.", listAfterCreateReceived.await(10, TimeUnit.SECONDS))

            val payload = requireNotNull(createPayload.get())
            assertFalse("The client must not choose its own loyalty member.", payload.has("p_member_id"))
            assertTrue(
                "The idempotency key must be a UUID.",
                runCatching { UUID.fromString(payload.getString("p_client_request_id")) }.isSuccess
            )
            assertTrue(
                "The requested reservation date must be in the future.",
                OffsetDateTime.parse(payload.getString("p_reservation_at")).toInstant().isAfter(Instant.now())
            )
            assertEquals("Miguel Teste", payload.getString("p_guest_name"))
            assertEquals("912345678", payload.getString("p_guest_phone"))
            assertEquals(2, payload.getInt("p_party_size"))
            assertEquals(90, payload.getInt("p_duration_minutes"))
            assertEquals("", payload.getString("p_notes"))
            assertEquals("Bearer $testAccessToken", lastAuthorization.get())
            assertFalse(
                "Reservation names must not be persisted in ordinary preferences.",
                preferences.all.values.any { it.toString().contains("Miguel Teste") }
            )
            assertFalse(
                "Reservation phone numbers must not be persisted in ordinary preferences.",
                preferences.all.values.any { it.toString().contains("912345678") }
            )
            assertFalse(
                "Pending idempotency preferences must not store raw customer details.",
                pendingPreferences.all.values.any {
                    it.toString().contains("Miguel Teste") || it.toString().contains("912345678")
                }
            )

            awaitText("Estado: Pendente")
            onView(withText("2 pessoas")).check(matches(isDisplayed()))

            onView(withText("Cancelar pedido")).perform(scrollTo(), click())
            onView(withText("Cancelar reserva")).perform(click())
            assertTrue("The reservation cancellation RPC was not received.", cancelReceived.await(10, TimeUnit.SECONDS))
            assertTrue("The cancelled reservation was not reloaded.", listAfterCancelReceived.await(10, TimeUnit.SECONDS))

            awaitText("Estado: Cancelada")
            onView(withText("Cancelar pedido")).check(doesNotExist())
        }
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

    private fun reservationDispatcher(): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            lastAuthorization.set(request.getHeader("Authorization"))
            return when (request.path?.substringBefore("?")) {
                "/rest/v1/rpc/get_my_restaurant_reservations" -> {
                    val rows = JSONArray()
                    synchronized(reservationRows) {
                        reservationRows.forEach { rows.put(JSONObject(it.toString())) }
                    }
                    when (listRequestCount.incrementAndGet()) {
                        1 -> initialListReceived.countDown()
                        2 -> listAfterCreateReceived.countDown()
                        3 -> listAfterCancelReceived.countDown()
                    }
                    jsonResponse(rows.toString())
                }

                "/rest/v1/rpc/create_customer_reservation_request" -> {
                    val payload = JSONObject(request.body.readUtf8())
                    createPayload.set(payload)
                    val row = JSONObject()
                        .put("reservation_id", reservationId)
                        .put("reservation_at", payload.getString("p_reservation_at"))
                        .put("party_size", payload.getInt("p_party_size"))
                        .put("status", "Pendente")
                        .put("notes", payload.optString("p_notes"))
                        .put("table_number", JSONObject.NULL)
                    synchronized(reservationRows) { reservationRows.add(row) }
                    createReceived.countDown()
                    // The create RPC returns only the reservation id and status.
                    jsonResponse(
                        JSONArray().put(
                            JSONObject()
                                .put("reservation_id", reservationId)
                                .put("status", "Pendente")
                        ).toString()
                    )
                }

                "/rest/v1/rpc/cancel_my_restaurant_reservation" -> {
                    val payload = JSONObject(request.body.readUtf8())
                    val targetId = payload.getString("p_reservation_id")
                    synchronized(reservationRows) {
                        reservationRows.firstOrNull { it.optString("reservation_id") == targetId }
                            ?.put("status", "Cancelada")
                    }
                    cancelReceived.countDown()
                    jsonResponse("")
                }

                else -> jsonResponse(
                    JSONObject().put("message", "Unexpected test endpoint: ${request.path}").toString(),
                    404
                )
            }
        }
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
