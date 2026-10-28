package pt.studiomesa.app

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
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
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
    fun restaurantBottomNavigationKeepsThreePrimarySectionsVisible() {
        assumeFalse("Customer variant has its own navigation.", isCustomerVariant())

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withContentDescription("Abrir menu de navegação")).check(matches(isDisplayed()))
            onView(withContentDescription("Abrir secção Início")).check(matches(isDisplayed()))
            onView(withContentDescription("Abrir secção Mesas")).check(matches(isDisplayed()))
            onView(withContentDescription("Abrir secção Pedidos")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun restaurantNavigationDrawerShowsPagesAvailableForItsPlan() {
        assumeFalse("Customer variant has its own navigation.", isCustomerVariant())
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withContentDescription("Abrir menu de navegação")).perform(click())
            onView(withContentDescription("Abrir secção Cozinha")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withContentDescription("Abrir secção Assistente")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withContentDescription("Abrir secção Carta")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withContentDescription("Abrir secção Pontos")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withContentDescription("Abrir secção Conta")).perform(scrollTo()).check(matches(isDisplayed()))

            if (isCalculatorAvailable()) {
                onView(withContentDescription("Abrir secção Reservas")).perform(scrollTo()).check(matches(isDisplayed()))
                onView(withContentDescription("Abrir secção Preço Justo")).perform(scrollTo()).check(matches(isDisplayed()))
            } else {
                onView(withContentDescription("Abrir secção Reservas")).check(doesNotExist())
                onView(withContentDescription("Abrir secção Preço Justo")).check(doesNotExist())
            }

            if (packageName.contains(".premium.")) {
                onView(withContentDescription("Abrir secção Stock")).perform(scrollTo()).check(matches(isDisplayed()))
                onView(withContentDescription("Abrir secção Análise")).perform(scrollTo()).check(matches(isDisplayed()))
            } else {
                onView(withContentDescription("Abrir secção Stock")).check(doesNotExist())
                onView(withContentDescription("Abrir secção Análise")).check(doesNotExist())
            }
        }
    }

    @Test
    fun restaurantDrawerCanOpenASecondarySection() {
        assumeFalse("Customer variant has its own navigation.", isCustomerVariant())

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withContentDescription("Abrir menu de navegação")).perform(click())
            onView(withContentDescription("Abrir secção Cozinha")).perform(click())
            onView(withText("Fila de preparação por estado e tempo de espera."))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun customerNavigationSeparatesCardMenuAndAccount() {
        assumeTrue("Restaurant variant has no customer navigation.", isCustomerVariant())

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withContentDescription("Abrir secção Cartão")).check(matches(isDisplayed()))
            onView(withContentDescription("Abrir secção Carta")).perform(click())
            onView(withText("Exemplos de artigos")).check(matches(isDisplayed()))

            onView(withContentDescription("Abrir secção Conta")).perform(click())
            onView(withText("A tua conta")).check(matches(isDisplayed()))
            onView(withText("Definir o meu nome")).check(matches(isDisplayed()))
            onView(withText("Criar conta / iniciar sessão")).check(matches(isDisplayed()))

            onView(withContentDescription("Abrir secção Cartão")).perform(click())
            onView(withText("Cartão de fidelização do restaurante.")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun restaurantAssistantShowsLocalAnalysisAndAnswersPricingQuestions() {
        assumeFalse("Customer variant has its own customer navigation.", isCustomerVariant())

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withContentDescription("Abrir menu de navegação")).perform(click())
            onView(withContentDescription("Abrir secção Assistente")).perform(scrollTo(), click())
            onView(withText("Assistente StudioMesa")).check(matches(isDisplayed()))
            onView(withText(containsString("SEM API PAGA"))).check(matches(isDisplayed()))
            onView(withHint("Ex.: Como posso melhorar o preço dos pratos?"))
                .perform(replaceText("Como melhorar o preço?"))
            onView(withText("Analisar pergunta")).perform(scrollTo(), click())
            onView(withText("Análise de preços")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withText(containsString("Não alterei nenhum preço.")))
                .perform(scrollTo())
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun restaurantAssistantOffersBriefingMenuEngineeringAndMarketingDrafts() {
        assumeFalse("Customer variant has its own customer navigation.", isCustomerVariant())
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withContentDescription("Abrir menu de navegação")).perform(click())
            onView(withContentDescription("Abrir secção Assistente")).perform(scrollTo(), click())
            onView(withText("Engenharia da carta")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withText("Ideias de conteúdo")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withText("Gerar resumo da operação")).perform(scrollTo(), click())
            onView(withText("Resumo da operação")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withText(containsString("não é previsão de vendas"))).perform(scrollTo()).check(matches(isDisplayed()))
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
