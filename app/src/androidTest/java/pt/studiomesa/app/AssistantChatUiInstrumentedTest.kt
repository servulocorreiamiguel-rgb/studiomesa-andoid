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
                onView(withContentDescription("Abrir secção Assistente")).perform(click())

                onView(withText("Histórico")).perform(scrollTo()).check(matches(isDisplayed())).perform(click())
                onView(withText("Histórico de conversas")).check(matches(isDisplayed()))
                onView(withText("Fechar")).perform(click())

                onView(withText("Memória")).perform(scrollTo()).check(matches(isDisplayed())).perform(click())
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
                onView(withText(containsString(uniqueMarker))).check(matches(isDisplayed()))
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
}
