package pt.studiomesa.app

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Base64
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
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
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Restaurant reservation UI tests against a local HTTPS Supabase-compatible stub.
 * No production account, restaurant data, API credential or live Supabase endpoint is used.
 */
class RestaurantReservationFlowSmokeTest {
    private val userId = "b09220e2-0a0b-4a35-a0da-ae63c18d9f01"
    private val restaurantId = "b09220e2-0a0b-4a35-a0da-ae63c18d9f02"
    private val reservationId = "b09220e2-0a0b-4a35-a0da-ae63c18d9f03"
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

    private val reservationRows = mutableListOf<JSONObject>()
    private val tableRows = mutableListOf<JSONObject>()
    private val reservationListCount = AtomicInteger(0)
    private val authorizationHeaders = CopyOnWriteArrayList<String>()
    private val restaurantScopedRequests = CopyOnWriteArrayList<String>()
    private val unexpectedRequest = AtomicReference<String?>()
    private val assignmentPayload = AtomicReference<JSONObject?>()
    private val statusPayloads = CopyOnWriteArrayList<JSONObject>()

    private val initialReservationsReceived = CountDownLatch(1)
    private val reservationsAfterAssignmentReceived = CountDownLatch(1)
    private val reservationsAfterConfirmReceived = CountDownLatch(1)
    private val reservationsAfterCancelReceived = CountDownLatch(1)
    private val tablesReceived = CountDownLatch(1)
    private val assignmentReceived = CountDownLatch(1)
    private val confirmReceived = CountDownLatch(1)
    private val cancelReceived = CountDownLatch(1)

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        assumeTrue(
            "Restaurant reservation management is available only in Professional/Premium restaurant builds.",
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

        synchronized(reservationRows) {
            reservationRows.clear()
            reservationRows.add(
                JSONObject()
                    .put("id", reservationId)
                    .put("guest_name", "Sofia Reservas")
                    .put("guest_phone", "912345678")
                    .put("party_size", 4)
                    .put("reservation_at", Instant.now().plusSeconds(86_400).toString())
                    .put("duration_minutes", 90)
                    .put("table_number", JSONObject.NULL)
                    .put("status", "Pendente")
                    .put("notes", "Aniversário")
                    .put("created_at", Instant.now().toString())
            )
        }
        synchronized(tableRows) {
            tableRows.clear()
            tableRows.add(table(4, "Esplanada", 2))
            tableRows.add(table(12, "Sala principal", 4))
            tableRows.add(table(14, "Terraço", 6))
        }

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
    fun ownerCanAssignOnlyEligibleTableConfirmAndCancelReservation() {
        ActivityScenario.launch(MainActivity::class.java).use {
            installRestaurantSession("owner")
            openReservationsPage()

            assertTrue(
                "The restaurant reservation list was not requested.",
                initialReservationsReceived.await(10, TimeUnit.SECONDS)
            )
            awaitText("Sofia Reservas")
            awaitText("Atribuir mesa")
            awaitText("Confirmar reserva")

            onView(withText("Atribuir mesa")).perform(scrollTo(), click())
            assertTrue("The table list was not requested.", tablesReceived.await(10, TimeUnit.SECONDS))
            awaitText("Mesa 12 · Sala principal · 4 lugares")
            awaitText("Mesa 14 · Terraço · 6 lugares")
            onView(withText("Mesa 4 · Esplanada · 2 lugares")).check(doesNotExist())

            onView(withText("Mesa 12 · Sala principal · 4 lugares")).perform(click())
            assertTrue("The table assignment RPC was not received.", assignmentReceived.await(10, TimeUnit.SECONDS))
            assertTrue(
                "The reservation was not reloaded after table assignment.",
                reservationsAfterAssignmentReceived.await(10, TimeUnit.SECONDS)
            )
            val assigned = requireNotNull(assignmentPayload.get())
            assertEquals(reservationId, assigned.getString("p_reservation_id"))
            assertEquals(12, assigned.getInt("p_table_number"))
            awaitText("Mesa 12 · 90 min")

            onView(withText("Confirmar reserva")).perform(scrollTo(), click())
            assertTrue("The confirmation RPC was not received.", confirmReceived.await(10, TimeUnit.SECONDS))
            assertTrue(
                "The reservation was not reloaded after confirmation.",
                reservationsAfterConfirmReceived.await(10, TimeUnit.SECONDS)
            )
            awaitText("Confirmada")

            onView(withText("Cancelar reserva")).perform(scrollTo(), click())
            onView(withText("Cancelar reserva")).inRoot(isDialog()).perform(click())
            assertTrue("The cancellation RPC was not received.", cancelReceived.await(10, TimeUnit.SECONDS))
            assertTrue(
                "The reservation was not reloaded after cancellation.",
                reservationsAfterCancelReceived.await(10, TimeUnit.SECONDS)
            )
            awaitText("Cancelada")
            onView(withText("Cancelar reserva")).check(doesNotExist())

            assertEquals(
                listOf("Confirmada", "Cancelada"),
                statusPayloads.map { it.getString("p_status") }
            )
            assertTrue(
                "Every backend request must use the authenticated account token.",
                authorizationHeaders.isNotEmpty() &&
                    authorizationHeaders.all { it == "Bearer $testAccessToken" }
            )
            assertTrue(
                "Every reservation/table query must be scoped to the configured restaurant.",
                restaurantScopedRequests.isNotEmpty() &&
                    restaurantScopedRequests.all { it.contains("restaurant_id=eq.$restaurantId") }
            )
            assertNull(unexpectedRequest.get())
            assertFalse(
                "Guest details must not be persisted in ordinary SharedPreferences.",
                preferences.all.values.any {
                    it.toString().contains("Sofia Reservas") || it.toString().contains("912345678")
                }
            )
        }
    }

    @Test
    fun ownerSeesClearMessageWhenNoTableHasEnoughSeats() {
        synchronized(tableRows) {
            tableRows.clear()
            tableRows.add(table(4, "Esplanada", 2))
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            installRestaurantSession("owner")
            openReservationsPage()

            assertTrue(
                "The restaurant reservation list was not requested.",
                initialReservationsReceived.await(10, TimeUnit.SECONDS)
            )
            awaitText("Atribuir mesa")
            onView(withText("Atribuir mesa")).perform(scrollTo(), click())

            assertTrue("The table list was not requested.", tablesReceived.await(10, TimeUnit.SECONDS))
            awaitText("Não existem mesas com capacidade para 4 pessoa(s). Adiciona ou configura uma mesa.")
            onView(withText("OK")).inRoot(isDialog()).perform(click())

            assertEquals(1L, assignmentReceived.count)
            assertTrue(statusPayloads.isEmpty())
            assertNull(unexpectedRequest.get())
        }
    }

    @Test
    fun staffWithoutReservationPermissionCannotSeeReservationsNavigation() {
        ActivityScenario.launch(MainActivity::class.java).use {
            installRestaurantSession("staff")
            onView(withContentDescription("Abrir menu de navegação")).perform(click())
            onView(withContentDescription("Abrir secção Reservas")).check(doesNotExist())
            onView(withContentDescription("Abrir secção Conta")).check(matches(isDisplayed()))
        }
    }

    private fun installRestaurantSession(role: String) {
        // The Activity starts logged out; the synthetic owner/staff session is installed only
        // afterwards so startup recovery never reaches a live Supabase project.
        secureSession.save(testAccessToken, "test-refresh-token")
        preferences.edit()
            .putString("user_id", userId)
            .putString("restaurant_id", restaurantId)
            .putString("restaurant_name", "Restaurante de Teste")
            .putString("restaurant_role", role)
            .commit()
    }

    private fun openReservationsPage() {
        onView(withContentDescription("Abrir menu de navegação")).perform(click())
        onView(withContentDescription("Abrir secção Reservas")).perform(click())
    }

    private fun reservationDispatcher(): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            authorizationHeaders.add(request.getHeader("Authorization").orEmpty())
            val path = request.path.orEmpty()
            val endpoint = path.substringBefore("?")

            return when {
                request.method == "GET" && endpoint == "/rest/v1/restaurant_reservations" -> {
                    restaurantScopedRequests.add(path)
                    val rows = JSONArray()
                    synchronized(reservationRows) {
                        reservationRows.forEach { rows.put(JSONObject(it.toString())) }
                    }
                    when (reservationListCount.incrementAndGet()) {
                        1 -> initialReservationsReceived.countDown()
                        2 -> reservationsAfterAssignmentReceived.countDown()
                        3 -> reservationsAfterConfirmReceived.countDown()
                        4 -> reservationsAfterCancelReceived.countDown()
                    }
                    jsonResponse(rows.toString())
                }

                request.method == "GET" && endpoint == "/rest/v1/restaurant_tables" -> {
                    restaurantScopedRequests.add(path)
                    val rows = JSONArray()
                    synchronized(tableRows) {
                        tableRows.forEach { rows.put(JSONObject(it.toString())) }
                    }
                    tablesReceived.countDown()
                    jsonResponse(rows.toString())
                }

                endpoint == "/rest/v1/rpc/assign_restaurant_reservation_table" -> {
                    val payload = JSONObject(request.body.readUtf8())
                    assignmentPayload.set(payload)
                    synchronized(reservationRows) {
                        reservationRows.firstOrNull { it.optString("id") == payload.optString("p_reservation_id") }
                            ?.put("table_number", payload.optInt("p_table_number"))
                    }
                    assignmentReceived.countDown()
                    jsonResponse("")
                }

                endpoint == "/rest/v1/rpc/update_restaurant_reservation_status" -> {
                    val payload = JSONObject(request.body.readUtf8())
                    statusPayloads.add(payload)
                    val targetId = payload.getString("p_reservation_id")
                    synchronized(reservationRows) {
                        reservationRows.firstOrNull { it.optString("id") == targetId }
                            ?.put("status", payload.getString("p_status"))
                    }
                    when (payload.getString("p_status")) {
                        "Confirmada" -> confirmReceived.countDown()
                        "Cancelada" -> cancelReceived.countDown()
                    }
                    jsonResponse("")
                }

                else -> {
                    unexpectedRequest.set("${request.method} $path")
                    jsonResponse(
                        JSONObject().put("message", "Unexpected test endpoint: ${request.method} $path").toString(),
                        404
                    )
                }
            }
        }
    }

    private fun table(number: Int, area: String, seats: Int): JSONObject =
        JSONObject()
            .put("id", when (number) {
                4 -> "b09220e2-0a0b-4a35-a0da-ae63c18d9f14"
                12 -> "b09220e2-0a0b-4a35-a0da-ae63c18d9f15"
                else -> "b09220e2-0a0b-4a35-a0da-ae63c18d9f16"
            })
            .put("table_number", number)
            .put("is_occupied", false)
            .put("area", area)
            .put("seats", seats)

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
