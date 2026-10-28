package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestaurantAssistantIntentClassifierTest {
    @Test fun routesSpecificEconomicQuestionsToEconomicManager() {
        val questions = listOf(
            "Faz um resumo da tesouraria e do custo alimentar",
            "Como comparo fornecedores?",
            "Como calculo o custo dos ingredientes por dose?",
            "Como calculo o custo de produção deste prato?",
            "Como preparo a ficha técnica do prato?",
            "Que quantidade devo encomendar ao fornecedor?",
            "Como analiso os custos fixos e variáveis?",
            "Como faço uma previsão financeira?",
            "Onde estou a perder dinheiro?",
            "Qual é o meu lucro mensal?",
            "Como reduzo despesas?",
            "Como analiso o cash flow?"
        )
        questions.forEach { question ->
            val match = RestaurantAssistantIntentClassifier.classify(question)
            assertEquals("$question -> $match", RestaurantAssistantIntent.ECONOMIC_MANAGER, match.intent)
            assertTrue("$question should include a matched phrase", match.matchedPhrases.isNotEmpty())
        }
    }

    @Test fun keepsGeneralDailyBriefingSeparateFromEconomicSummary() {
        assertEquals(
            RestaurantAssistantIntent.DAILY_BRIEFING,
            RestaurantAssistantIntentClassifier.classify("Faz um resumo da operação").intent
        )
        assertEquals(
            RestaurantAssistantIntent.ECONOMIC_MANAGER,
            RestaurantAssistantIntentClassifier.classify("Faz um resumo da tesouraria").intent
        )
    }

    @Test fun distinguishesSalesGrowthFromSpecificPriceQuestions() {
        listOf(
            "Como posso aumentar as vendas?",
            "Qual é a rentabilidade do menu?",
            "Quais pratos dão mais lucro?",
            "Como aumento a faturação?"
        ).forEach { question ->
            assertEquals(question, RestaurantAssistantIntent.MENU_ENGINEERING, RestaurantAssistantIntentClassifier.classify(question).intent)
        }
        assertEquals(
            RestaurantAssistantIntent.PRICING,
            RestaurantAssistantIntentClassifier.classify("Qual deve ser o preço de venda deste prato?").intent
        )
    }

    @Test fun recognizesInventoryWarningsAndSaveFailures() {
        assertEquals(
            RestaurantAssistantIntent.STOCK,
            RestaurantAssistantIntentClassifier.classify("Que ingredientes estão a acabar?").intent
        )
        assertEquals(
            RestaurantAssistantIntent.TROUBLESHOOTING,
            RestaurantAssistantIntentClassifier.classify("Não consigo guardar uma reserva").intent
        )
        assertEquals(
            RestaurantAssistantIntent.DAILY_BRIEFING,
            RestaurantAssistantIntentClassifier.classify("Faz resumo da operação e do stock").intent
        )
    }

    @Test fun recognizesOtherRestaurantWorkflows() {
        val samples = listOf(
            "Escreve um post para Instagram" to RestaurantAssistantIntent.MARKETING,
            "Há stock baixo de arroz?" to RestaurantAssistantIntent.STOCK,
            "Tenho reservas pendentes?" to RestaurantAssistantIntent.RESERVATIONS,
            "Como está a fila da cozinha?" to RestaurantAssistantIntent.ORDERS,
            "A aplicação tem erro de sincronização" to RestaurantAssistantIntent.TROUBLESHOOTING,
            "Como altero permissões da equipa?" to RestaurantAssistantIntent.ACCESS
        )
        samples.forEach { (question, expected) ->
            assertEquals(question, expected, RestaurantAssistantIntentClassifier.classify(question).intent)
        }
    }

    @Test fun accentAndPunctuationNormalizationDoesNotChangeIntent() {
        val withAccents = RestaurantAssistantIntentClassifier.classify("Como calculo o custo por porção?")
        val withoutAccents = RestaurantAssistantIntentClassifier.classify("Como calculo o custo por porcao")
        assertEquals(withoutAccents.intent, withAccents.intent)
        assertEquals(RestaurantAssistantIntent.ECONOMIC_MANAGER, withAccents.intent)
    }

    @Test fun emptyOrUnknownQuestionsUseGeneralFallback() {
        assertEquals(
            RestaurantAssistantIntent.GENERAL,
            RestaurantAssistantIntentClassifier.classify("   ").intent
        )
        assertEquals(
            RestaurantAssistantIntent.GENERAL,
            RestaurantAssistantIntentClassifier.classify("Quero conversar sobre outra coisa").intent
        )
    }

    @Test fun matchScoreIsOnlyAHeuristicAndStaysWithinBounds() {
        val match = RestaurantAssistantIntentClassifier.classify("Gestão económica: compara fornecedores e tesouraria")
        assertTrue(match.confidence in 0..100)
        assertTrue(match.matchedPhrases.isNotEmpty())
    }
}
