package pt.studiomesa.app

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.hasErrorText
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.anyOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Smoke tests running on a real Android runtime (emulator in CI).
 * Keep these independent of production credentials: all actions use fresh local demo data.
 */
class MainActivitySmokeTest {
    private fun isCustomerVariant(): Boolean =
        InstrumentationRegistry.getInstrumentation()
            .targetContext.packageName.endsWith(".cliente")

    private fun isCalculatorAvailable(): Boolean {
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        return packageName.contains(".profissional.") || packageName.contains(".premium.")
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
                .perform(replaceText("3,20"))

            onView(withText("Calcular preço sugerido"))
                .perform(scrollTo(), click())

            onView(withText(containsString("10,67")))
                .perform(scrollTo())
                .check(matches(isDisplayed()))

            onView(withHint("Ex.: 30"))
                .perform(replaceText("40"))

            onView(withText(containsString("10,67")))
                .check(doesNotExist())

            onView(withText("Calcular preço sugerido"))
                .perform(scrollTo(), click())

            onView(withText(containsString("8,00")))
                .perform(scrollTo())
                .check(matches(isDisplayed()))

            onView(withHint("Ex.: 3,20"))
                .perform(replaceText("0"))

            onView(withText(containsString("8,00")))
                .check(doesNotExist())

            onView(withText("Calcular preço sugerido"))
                .perform(scrollTo(), click())

            onView(withText(containsString("Corrige os valores assinalados")))
                .perform(scrollTo())
                .check(matches(isDisplayed()))

            onView(withText(containsString("8,00")))
                .check(doesNotExist())

            onView(withHint("Ex.: 3,20"))
                .check(matches(hasErrorText("Introduz um custo superior a zero.")))

            onView(withHint("Ex.: 3,20"))
                .perform(replaceText("3,20"))

            onView(withHint("Ex.: 3,20"))
                .check(matches(not(hasErrorText("Introduz um custo superior a zero."))))

            onView(withText("Calcular preço sugerido"))
                .perform(scrollTo(), click())

            onView(withText(containsString("8,00")))
                .perform(scrollTo())
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun calculatorDisplaysSmallPercentagesWithoutRoundingThemToZero() {
        assumeFalse("Customer variant has no management calculator.", isCustomerVariant())
        assumeTrue("The Essential plan does not include the price calculator.", isCalculatorAvailable())

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("ABRIR CALCULADORA"))
                .perform(scrollTo(), click())

            onView(withHint("Ex.: 3,20"))
                .perform(replaceText("3,20"))

            onView(withHint("Ex.: 30"))
                .perform(replaceText("0,01"))

            onView(withText("Calcular preço sugerido"))
                .perform(scrollTo(), click())

            onView(withText(containsString("0,01%")))
                .perform(scrollTo())
                .check(matches(isDisplayed()))

            onView(withText(containsString("99,99%")))
                .perform(scrollTo())
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun calculatorRejectsPercentagesBelowSupportedPrecision() {
        assumeFalse("Customer variant has no management calculator.", isCustomerVariant())
        assumeTrue("The Essential plan does not include the price calculator.", isCalculatorAvailable())

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("ABRIR CALCULADORA"))
                .perform(scrollTo(), click())

            onView(withHint("Ex.: 3,20"))
                .perform(replaceText("3,20"))

            onView(withHint("Ex.: 30"))
                .perform(replaceText("0,009"))

            onView(withText("Calcular preço sugerido"))
                .perform(scrollTo(), click())

            onView(withHint("Ex.: 30"))
                .check(matches(hasErrorText("Usa uma percentagem entre 0,01 e 100.")))

            onView(withText(containsString("Corrige os valores assinalados")))
                .perform(scrollTo())
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun cachedTenantDataIsHiddenUntilStoredSessionIsVerified() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val backendPrefs = context.getSharedPreferences("studiomesa_backend", Context.MODE_PRIVATE)
        val secureSession = SecureSessionStore(context)
        val staleRestaurantName = "STUDIO_MESA_STALE_PRIVATE_SENTINEL"

        // Seed old account-specific cache and a deliberately invalid token.
        // The UI must show only the verification screen while the server is checked.
        secureSession.clear()
        backendPrefs.edit().clear().commit()
        secureSession.save("invalid-session-token-for-smoke-test", "")
        val staleMenu = org.json.JSONArray().put(
            org.json.JSONObject()
                .put("id", "99999999-1111-4111-8111-111111111111")
                .put("name", staleRestaurantName)
                .put("category", "Teste")
                .put("description", "")
                .put("price", 99.99)
                .put("cost", 1.00)
                .put("allergens", "")
                .put("is_active", true)
        )
        backendPrefs.edit()
            .putBoolean("local_demo_data_isolated_v1", true)
            .putString("user_id", "88888888-1111-4111-8111-111111111111")
            .putString("user_email", "stale-account@example.invalid")
            .putString("restaurant_id", "77777777-1111-4111-8111-111111111111")
            .putString("restaurant_name", staleRestaurantName)
            .putString("restaurant_role", "owner")
            .putString("remote_menu_json", staleMenu.toString())
            .putString("remote_tables_json", "[]")
            .putString("remote_orders_json", "[]")
            .commit()

        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                onView(anyOf(
                    withText(containsString("A verificar autorização da conta")),
                    withText(containsString("Não foi possível confirmar o acesso"))
                )).check(matches(isDisplayed()))

                onView(withText(containsString(staleRestaurantName)))
                    .check(doesNotExist())
            }
        } finally {
            secureSession.clear()
            backendPrefs.edit().clear().commit()
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

    @Test
    fun customerLoginFormShowsFieldsAndRejectsInvalidEmailBeforeNetworkCall() {
        assumeTrue("Restaurant variant uses the management account flow.", isCustomerVariant())

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("Criar conta / iniciar sessão"))
                .perform(scrollTo(), click())
            onView(withText("Iniciar sessão"))
                .perform(click())

            onView(withHint("Email")).check(matches(isDisplayed()))
            onView(withHint("Palavra-passe (mínimo 8 caracteres)"))
                .check(matches(isDisplayed()))

            onView(withHint("Email")).perform(replaceText("isto-nao-e-email"))
            onView(withHint("Palavra-passe (mínimo 8 caracteres)"))
                .perform(replaceText("password123"))
            onView(withText("Entrar")).perform(click())

            onView(withHint("Email"))
                .check(matches(hasErrorText("Introduz um email válido")))
        }
    }

}
