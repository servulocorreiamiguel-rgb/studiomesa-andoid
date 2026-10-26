package pt.studiomesa.app

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasErrorText
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.containsString
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Smoke tests running on a real Android runtime (emulator in CI).
 * Keep these independent of production credentials: all actions use fresh local demo data.
 */
class MainActivitySmokeTest {
    private fun collectViewText(view: View): String = when (view) {
        is TextView -> view.text?.toString().orEmpty()
        is ViewGroup -> (0 until view.childCount).joinToString(" ") {
            collectViewText(view.getChildAt(it))
        }
        else -> ""
    }

    @Test
    fun existingSessionDoesNotRenderCachedTenantDataBeforeServerAuthorization() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("studiomesa_backend", Context.MODE_PRIVATE)
        val secureSession = SecureSessionStore(context)
        secureSession.clear()
        prefs.edit().clear()
            .putString("user_id", "fixture-user")
            .putString("restaurant_id", "old-restaurant-id")
            .putString("restaurant_name", "PRIVATE_RESTAURANT_SENTINEL")
            .putString("restaurant_role", "owner")
            .putString("member_id", "old-member-id")
            .putString("member_restaurant_name", "PRIVATE_RESTAURANT_SENTINEL")
            .putString("remote_menu_json", """[{"name":"PRIVATE_RESTAURANT_SENTINEL"}]""")
            .putString("remote_orders_json", """[{"restaurant":"PRIVATE_RESTAURANT_SENTINEL"}]""")
            .putString("customer_menu_json", """[{"name":"PRIVATE_RESTAURANT_SENTINEL"}]""")
            .putBoolean("local_demo_data_isolated_v1", true)
            .commit()
        secureSession.save("not-a-valid-session-token", "")

        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val displayed = collectViewText(
                        activity.findViewById(android.R.id.content)
                    )
                    org.junit.Assert.assertFalse(
                        "Cached tenant data was displayed before server authorization.",
                        displayed.contains("PRIVATE_RESTAURANT_SENTINEL")
                    )
                    org.junit.Assert.assertTrue(
                        "The app should show a restricted verification state.",
                        displayed.contains("A verificar autorização da conta") ||
                            displayed.contains("Não foi possível confirmar o acesso")
                    )
                }
            }
        } finally {
            secureSession.clear()
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun failedSessionVerificationOffersSignInRecoveryWithoutDeletingServerData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("studiomesa_backend", Context.MODE_PRIVATE)
        val secureSession = SecureSessionStore(context)
        secureSession.clear()
        prefs.edit().clear().commit()

        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    // Render the failure state directly so this UI test never depends on
                    // network availability or the hosted Supabase project.
                    val method = MainActivity::class.java.getDeclaredMethod(
                        "renderSessionVerificationScreen",
                        Boolean::class.javaPrimitiveType
                    )
                    method.isAccessible = true
                    method.invoke(activity, false)
                }

                onView(withText("Terminar sessão e voltar a entrar"))
                    .check(matches(isDisplayed()))
                onView(withText(containsString("a conta e os dados no servidor não serão eliminados.")))
                    .check(matches(isDisplayed()))

                onView(withText("Terminar sessão e voltar a entrar"))
                    .perform(click())

                onView(withText("Criar conta / iniciar sessão"))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()))
            }
        } finally {
            secureSession.clear()
            prefs.edit().clear().commit()
        }
    }

    private fun isCustomerVariant(): Boolean =
        InstrumentationRegistry.getInstrumentation()
            .targetContext.packageName.contains(".cliente")

    private fun isCalculatorAvailable(): Boolean {
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        // Gradle combines flavor suffixes in dimension order; the plan suffix may be
        // the final segment, so do not require a trailing dot after the plan name.
        return packageName.contains(".profissional") || packageName.contains(".premium")
    }

    @Test
    fun clearingSecureSessionAlsoRemovesLegacyPlaintextTokens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val legacyPrefs = context.getSharedPreferences("studiomesa_backend", Context.MODE_PRIVATE)
        val store = SecureSessionStore(context)
        store.clear()
        store.save("secure-access-token", "secure-refresh-token")

        org.junit.Assert.assertTrue(
            "Could not seed legacy test preferences.",
            legacyPrefs.edit()
                .putString("access_token", "legacy-access-token")
                .putString("refresh_token", "legacy-refresh-token")
                .commit()
        )

        store.clear()

        org.junit.Assert.assertNull(legacyPrefs.getString("access_token", null))
        org.junit.Assert.assertNull(legacyPrefs.getString("refresh_token", null))
        org.junit.Assert.assertEquals("", store.accessToken())
        org.junit.Assert.assertEquals("", store.refreshToken())
    }

    @Test
    fun migratingLegacySessionTokensRemovesPlaintextCopies() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val legacyPrefs = context.getSharedPreferences("studiomesa_backend", Context.MODE_PRIVATE)
        val store = SecureSessionStore(context)
        store.clear()

        org.junit.Assert.assertTrue(
            "Could not seed legacy test preferences.",
            legacyPrefs.edit()
                .putString("access_token", "migration-access-token")
                .putString("refresh_token", "migration-refresh-token")
                .commit()
        )

        org.junit.Assert.assertEquals("migration-access-token", store.accessToken())
        org.junit.Assert.assertEquals("migration-refresh-token", store.refreshToken())
        org.junit.Assert.assertNull(legacyPrefs.getString("access_token", null))
        org.junit.Assert.assertNull(legacyPrefs.getString("refresh_token", null))

        store.clear()
    }

    @Test
    fun appLaunchesIntoTheExpectedDemoExperience() {
        ActivityScenario.launch(MainActivity::class.java).use {
            if (isCustomerVariant()) {
                onView(withText(containsString("Cartão digital de cliente")))
                    .check(matches(isDisplayed()))
                onView(withText("Cartão de fidelização do restaurante."))
                    .check(matches(isDisplayed()))
            } else {
                onView(withText("Painel de operação"))
                    .check(matches(isDisplayed()))
                onView(withText("Demonstração · Dados de exemplo"))
                    .check(matches(isDisplayed()))
            }
        }
    }

    @Test
    fun restaurantCanCreateAndSeeALocalDemoOrder() {
        assumeFalse("Customer variant has no restaurant order-management screen.", isCustomerVariant())

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("ABRIR PEDIDOS"))
                .perform(scrollTo(), click())

            onView(withText("+  Novo pedido"))
                .perform(scrollTo(), click())

            onView(withText("Criar pedido"))
                .perform(click())

            onView(withText("Entrada da casa · exemplo"))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun restaurantPriceCalculatorProducesAnIndicativePrice() {
        assumeFalse("Customer variant has no management calculator.", isCustomerVariant())
        assumeTrue("The Essential plan does not include the price calculator.", isCalculatorAvailable())

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("ABRIR CALCULADORA"))
                .perform(scrollTo(), click())

            onView(withHint("Ex.: 3,20"))
                .perform(replaceText("3,20"))

            onView(withText("Calcular preço sugerido"))
                .perform(scrollTo(), click())

            onView(withText(containsString("PREÇO INDICATIVO")))
                .perform(scrollTo())
                .check(matches(isDisplayed()))
            onView(withText(containsString("10,67")))
                .perform(scrollTo())
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun calculatorDoesNotKeepShowingAnOldPriceAfterInputChanges() {
        assumeFalse("Customer variant has no management calculator.", isCustomerVariant())
        assumeTrue("The Essential plan does not include the price calculator.", isCalculatorAvailable())

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("ABRIR CALCULADORA"))
                .perform(scrollTo(), click())

            onView(withHint("Ex.: 3,20"))
                .perform(scrollTo(), replaceText("3,20"))

            onView(withText("Calcular preço sugerido"))
                .perform(scrollTo(), click())

            onView(withText(containsString("10,67")))
                .perform(scrollTo())
                .check(matches(isDisplayed()))

            onView(withHint("Ex.: 3,20"))
                .perform(scrollTo(), replaceText("0"))

            onView(withText(containsString("10,67")))
                .check(doesNotExist())

            onView(withText("Calcular preço sugerido"))
                .perform(scrollTo(), click())

            onView(withText(containsString("Corrige os valores assinalados")))
                .perform(scrollTo())
                .check(matches(isDisplayed()))

            onView(withText(containsString("10,67")))
                .check(doesNotExist())
        }
    }

    @Test
    fun calculatorShowsValidationForExcessivelyLongCostInput() {
        assumeTrue(
            "The calculator is only available in management variants.",
            isCalculatorAvailable() && !isCustomerVariant()
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("ABRIR CALCULADORA"))
                .perform(scrollTo(), click())

            onView(withHint("Ex.: 3,20"))
                .perform(replaceText("9".repeat(65)))

            onView(withText("Calcular preço sugerido"))
                .perform(scrollTo(), click())

            onView(withHint("Ex.: 3,20"))
                .check(matches(hasErrorText("Introduz um custo numérico válido.")))
        }
    }

    @Test
    fun planSpecificManagementFeaturesMatchSelectedVariant() {
        assumeFalse("Customer variants do not expose restaurant management screens.", isCustomerVariant())
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName

        ActivityScenario.launch(MainActivity::class.java).use {
            when {
                packageName.contains(".essencial.") -> {
                    onView(withText("ABRIR CALCULADORA")).check(doesNotExist())
                    onView(withText("GERIR STOCK")).check(doesNotExist())
                }
                packageName.contains(".profissional.") -> {
                    onView(withText("ABRIR CALCULADORA"))
                        .perform(scrollTo())
                        .check(matches(isDisplayed()))
                    onView(withText("GERIR STOCK")).check(doesNotExist())
                }
                packageName.contains(".premium.") -> {
                    onView(withText("ABRIR CALCULADORA"))
                        .perform(scrollTo())
                        .check(matches(isDisplayed()))
                    onView(withText("GERIR STOCK"))
                        .perform(scrollTo())
                        .check(matches(isDisplayed()))
                }
                else -> throw AssertionError("Unexpected restaurant application package: $packageName")
            }
        }
    }

    @Test
    fun aiAssistantNavigationIsRestrictedToPremiumRestaurantManagement() {
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val expected = packageName.contains(".premium.") && !isCustomerVariant()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val method = MainActivity::class.java.getDeclaredMethod("availablePages")
                method.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val pages = method.invoke(activity) as List<String>
                org.junit.Assert.assertEquals(
                    "AI assistant navigation must only exist for Premium restaurant management builds.",
                    expected,
                    pages.contains("Conteúdo IA")
                )
            }
        }
    }

    @Test
    fun premiumRestaurantCanAnalyzeMenuPricesWithoutChangingTheMenu() {
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        assumeTrue(
            "The local price adviser is only available in Premium restaurant management.",
            packageName.contains(".premium.") && !isCustomerVariant()
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("studiomesa_backend", Context.MODE_PRIVATE)
        val secureSession = SecureSessionStore(context)
        secureSession.clear()
        prefs.edit().clear().commit()

        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val page = MainActivity::class.java.getDeclaredField("activePage")
                    page.isAccessible = true
                    page.set(activity, "Conteúdo IA")
                    val render = MainActivity::class.java.getDeclaredMethod("renderApp")
                    render.isAccessible = true
                    render.invoke(activity)
                }

                onView(withText("Consultor de preços"))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()))
                onView(withText("Aplicação de preços desativada na demonstração"))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()))
                onView(withText("Autorização do proprietário"))
                    .check(doesNotExist())
                onView(withHint("Ex.: 30"))
                    .perform(scrollTo(), replaceText("30"))
                onView(withText("Analisar preços da carta"))
                    .perform(scrollTo(), click())

                onView(withText("Análise de preços da carta"))
                    .check(matches(isDisplayed()))
                onView(withText(containsString("dados locais de demonstração")))
                    .check(matches(isDisplayed()))
                onView(withText(containsString("Rever e aplicar")))
                    .check(doesNotExist())
                onView(withText("Fechar"))
                    .perform(click())
            }
        } finally {
            secureSession.clear()
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun premiumRestaurantCanGenerateAnOfflineAiDraftWithoutExternalServices() {
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        assumeTrue(
            "The local content assistant is only available in Premium restaurant management.",
            packageName.contains(".premium.") && !isCustomerVariant()
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("studiomesa_backend", Context.MODE_PRIVATE)
        val secureSession = SecureSessionStore(context)
        secureSession.clear()
        prefs.edit().clear().commit()

        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    // Open the real UI route while keeping the test independent of login,
                    // restaurant data and all network services.
                    val page = MainActivity::class.java.getDeclaredField("activePage")
                    page.isAccessible = true
                    page.set(activity, "Conteúdo IA")
                    val render = MainActivity::class.java.getDeclaredMethod("renderApp")
                    render.isAccessible = true
                    render.invoke(activity)
                }

                onView(withText("Assistente StudioMesa"))
                    .check(matches(isDisplayed()))
                onView(withText(containsString("não é um modelo de linguagem treinado")))
                    .check(matches(isDisplayed()))

                onView(withHint("Ex.: Hambúrguer da casa"))
                    .perform(scrollTo(), replaceText("Hambúrguer da casa"))
                onView(withHint("Ingredientes, características e outros factos confirmados…"))
                    .perform(scrollTo(), replaceText("Pão brioche, carne de vaca e alface."))

                onView(withText("Gerar rascunho"))
                    .perform(scrollTo(), click())

                onView(withText("Rascunho StudioMesa"))
                    .check(matches(isDisplayed()))
                onView(withText("Copiar texto"))
                    .check(matches(isDisplayed()))
            }
        } finally {
            secureSession.clear()
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun customerCanOpenAccountChooserWithoutNetworkCredentials() {
        assumeTrue("Restaurant variant uses the management account flow.", isCustomerVariant())

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("Criar conta / iniciar sessão"))
                .perform(scrollTo(), click())
            onView(withText("Conta StudioMesa"))
                .check(matches(isDisplayed()))
            onView(withText("Iniciar sessão"))
                .check(matches(isDisplayed()))
        }
    }
}
