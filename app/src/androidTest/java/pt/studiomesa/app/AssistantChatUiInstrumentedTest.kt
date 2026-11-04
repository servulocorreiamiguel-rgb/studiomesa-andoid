package pt.studiomesa.app

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.containsString
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AssistantChatUiInstrumentedTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun managerAssistantOpensChatSendsMessageAndFindsItInHistory() {
        // The customer app intentionally has no restaurant-management assistant.
        assumeFalse(BuildConfig.FLAVOR.lowercase().contains("cliente"))

        val uniqueMarker = UUID.randomUUID().toString().take(8)
        val question = "Teste de histórico margem $uniqueMarker"
        val scope = "demo:${BuildConfig.FLAVOR}"

        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                onView(withContentDescription("Abrir menu de navegação")).perform(click())
                onView(withContentDescription("Abrir secção Assistente")).perform(scrollTo(), click())

                // Generative AI is an explicit owner-controlled opt-in.
                onView(withText("Ativar conversa generativa"))
                    .check(matches(isDisplayed()))

                onView(withText("Histórico")).check(matches(isDisplayed())).perform(scrollTo(), click())
                onView(withText("Histórico de conversas")).check(matches(isDisplayed()))
                onView(withText("Fechar")).perform(click())

                onView(withText("Memória")).check(matches(isDisplayed())).perform(scrollTo(), click())
                onView(withText("Memória do assistente")).check(matches(isDisplayed()))
                onView(withText("Cancelar")).perform(click())

                onView(withHint("Escreve uma mensagem…"))
                    .perform(scrollTo(), replaceText(question), closeSoftKeyboard())
                onView(withText("Enviar mensagem  ↑")).perform(scrollTo(), click())

                onView(withText("TU")).check(matches(isDisplayed()))
                onView(withText("STUDIOMESA")).check(matches(isDisplayed()))

                onView(withText("Histórico")).perform(scrollTo(), click())
                onView(withHint("Pesquisar conversas…"))
                    .perform(replaceText(uniqueMarker))
                onView(withText(question)).check(matches(isDisplayed()))
            }
        } finally {
            val store = AssistantChatStore(context)
            runCatching {
                store.listConversations(scope, uniqueMarker).forEach {
                    store.deleteConversation(scope, it.id)
                }
            }
        }
    }
    @Test
    fun invoiceCanBeRegisteredAndUsedByTheFinancialChat() {
        assumeFalse(BuildConfig.FLAVOR.lowercase().contains("cliente"))
        val marker = UUID.randomUUID().toString().take(8)
        val supplier = "Fornecedor $marker"
        val number = "FT $marker"
        val scope = "demo:${BuildConfig.FLAVOR}"
        val store = AssistantChatStore(context)

        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                onView(withContentDescription("Abrir menu de navegação")).perform(click())
                onView(withContentDescription("Abrir secção Assistente")).perform(scrollTo(), click())
                onView(withText("＋ Ferramentas financeiras")).perform(scrollTo(), click())
                onView(withText("＋ Registar fatura ou nota de crédito")).perform(scrollTo(), click())

                onView(withHint("Ex.: Distribuidor alimentar"))
                    .perform(scrollTo(), replaceText(supplier))
                onView(withHint("Ex.: FT A/2026/104"))
                    .perform(scrollTo(), replaceText(number))
                onView(withHint("Ex.: 100,00")).perform(scrollTo(), replaceText("100,00"))
                onView(withHint("Ex.: 23,00")).perform(scrollTo(), replaceText("23,00"))
                onView(withHint("Ex.: 123,00")).perform(scrollTo(), replaceText("123,00"))
                onView(withText("Guardar")).perform(scrollTo(), click())

                onView(withText("Documento financeiro")).check(matches(isDisplayed()))
                onView(withText(containsString(supplier))).check(matches(isDisplayed()))
                onView(withText("Fechar")).perform(click())

                val question = "Quanto gastei em compras do $supplier?"
                onView(withHint("Escreve uma mensagem…"))
                    .perform(scrollTo(), replaceText(question), closeSoftKeyboard())
                onView(withText("Enviar mensagem  ↑")).perform(scrollTo(), click())
                onView(withText(containsString("Compras líquidas registadas")))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()))
            }
        } finally {
            runCatching {
                store.listInvoices(scope).filter { it.number == number }.forEach { invoice ->
                    store.deleteInvoice(scope, invoice.id)?.let { AssistantInvoiceFileStore(context).delete(it) }
                }
                store.listConversations(scope, marker).forEach { conversation ->
                    store.deleteConversation(scope, conversation.id)
                }
            }
        }
    }

}
