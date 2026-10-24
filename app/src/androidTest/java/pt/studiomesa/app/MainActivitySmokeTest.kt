package pt.studiomesa.app

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
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
    private fun isCustomerVariant(): Boolean =
        InstrumentationRegistry.getInstrumentation()
            .targetContext.packageName.endsWith(".cliente")

    private fun hasCalculator(): Boolean {
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
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
        assumeTrue("Calculator is only included in Profissional and Premium management variants.", hasCalculator() && !isCustomerVariant())

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
    fun calculatorShowsValidationForExcessivelyLongCostInput() {
        assumeTrue(
            "Calculator is only included in Profissional and Premium management variants.",
            hasCalculator() && !isCustomerVariant()
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
