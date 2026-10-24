package pt.studiomesa.app

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
        BuildConfig.FLAVOR.contains("cliente", ignoreCase = true)

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

    private fun isPremiumVariant(): Boolean =
        !isCustomerVariant() && BuildConfig.FLAVOR.contains("premium", ignoreCase = true)

    @Test
    fun premiumRestaurantCanSeeTheSafeOrderReportExportAction() {
        assumeTrue("CSV order reporting is a Premium restaurant feature.", isPremiumVariant())

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("VER ANÁLISE"))
                .perform(scrollTo(), click())

            onView(withText("Exportar pedidos em CSV"))
                .perform(scrollTo())
                .check(matches(isDisplayed()))

            onView(withText(containsString("Não inclui notas internas")))
                .perform(scrollTo())
                .check(matches(isDisplayed()))
        }
    }

    private fun hasCalculator(): Boolean =
        !isCustomerVariant() &&
            (BuildConfig.FLAVOR.contains("profissional", ignoreCase = true) ||
                BuildConfig.FLAVOR.contains("premium", ignoreCase = true))

    @Test
    fun restaurantPriceCalculatorProducesAnIndicativePrice() {
        assumeTrue("The price calculator is not included in Essential or customer tiers.", hasCalculator())

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
            "The price calculator is not included in Essential or customer tiers.",
            hasCalculator()
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
