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
            "Quanto me custou o Bitoque?",
            "Quanto me custa o Bitoque?",
            "Que quantidade devo encomendar ao fornecedor?",
            "Como analiso os custos fixos e variáveis?",
            "Como faço uma previsão financeira?",
            "Como calculo o custo do prato?",
            "Quanto lucro tive este mês?",
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

    @Test fun routesAdversarialNaturalLanguageVariantsWithoutGuessingTheWrongDomain() {
        val cases = listOf(
            "Quanto custa cada receita?" to RestaurantAssistantIntent.ECONOMIC_MANAGER,
            "Quais pratos estão a dar prejuízo?" to RestaurantAssistantIntent.MENU_ENGINEERING,
            "Quais pratos são menos rentáveis?" to RestaurantAssistantIntent.MENU_ENGINEERING,
            "Quantas mesas estão reservadas hoje?" to RestaurantAssistantIntent.RESERVATIONS,
            "Acabou o arroz, quanto devo encomendar?" to RestaurantAssistantIntent.STOCK,
            "Quanto resta de frango?" to RestaurantAssistantIntent.STOCK,
            "Cria uma promoção para Instagram" to RestaurantAssistantIntent.MARKETING,
            "A app fecha quando tento guardar" to RestaurantAssistantIntent.TROUBLESHOOTING,
            "Não aparece a resposta da IA" to RestaurantAssistantIntent.TROUBLESHOOTING,
            "Qual foi o prato mais vendido ontem?" to RestaurantAssistantIntent.MENU_ENGINEERING,
            "Faz um resumo das reservas e da cozinha" to RestaurantAssistantIntent.DAILY_BRIEFING,
            "Quais os artigos com melhor margem?" to RestaurantAssistantIntent.MENU_ENGINEERING
        )
        cases.forEach { (question, expected) ->
            assertEquals("$question -> ${RestaurantAssistantIntentClassifier.classify(question)}",
                expected, RestaurantAssistantIntentClassifier.classify(question).intent)
        }
    }


    @Test fun handlesCommonCashCostAndMarketingPhrases() {
        val cases = listOf(
            "Quanto resta em caixa?" to RestaurantAssistantIntent.ECONOMIC_MANAGER,
            "Quanto gastei em salários?" to RestaurantAssistantIntent.ECONOMIC_MANAGER,
            "O custo dos pratos está alto?" to RestaurantAssistantIntent.PRICING,
            "O que devo publicar amanhã?" to RestaurantAssistantIntent.MARKETING,
            "Como poupar nas compras?" to RestaurantAssistantIntent.ECONOMIC_MANAGER
        )
        cases.forEach { (question, expected) ->
            assertEquals("$question -> ${RestaurantAssistantIntentClassifier.classify(question)}",
                expected, RestaurantAssistantIntentClassifier.classify(question).intent)
        }
    }


    @Test fun routesNaturalLanguageReorderQuestionsToEconomicManager() {
        listOf(
            "Quanto devo encomendar?",
            "Quanto encomendar de arroz?",
            "Qual é o ponto de encomenda do frango?"
        ).forEach { question ->
            assertEquals("$question -> ${RestaurantAssistantIntentClassifier.classify(question)}",
                RestaurantAssistantIntent.ECONOMIC_MANAGER,
                RestaurantAssistantIntentClassifier.classify(question).intent)
        }
    }


    @Test fun handlesNaturalStockAvailabilityPhrasing() {
        val cases = listOf(
            "Quanto tenho de leite?" to RestaurantAssistantIntent.STOCK,
            "O arroz acabou" to RestaurantAssistantIntent.STOCK,
            "Há arroz suficiente?" to RestaurantAssistantIntent.STOCK,
            "Quanto resta de frango?" to RestaurantAssistantIntent.STOCK
        )
        cases.forEach { (question, expected) ->
            assertEquals("$question -> ${RestaurantAssistantIntentClassifier.classify(question)}",
                expected, RestaurantAssistantIntentClassifier.classify(question).intent)
        }
    }

    @Test fun identifiesDatedSalesAcrossDaysWeeksMonthsAndYears() {
        val datedQuestions = listOf(
            "Quanto vendi no domingo passado?",
            "Quanto vendi ontem?",
            "Qual foi a faturação nos últimos 30 dias?",
            "Qual foi a faturação nos últimos seis meses?",
            "Quanto vendemos em março?",
            "Quais pratos venderam mais na semana passada?"
        )
        datedQuestions.forEach { question ->
            assertTrue("$question should be recognized as a dated sales question",
                RestaurantAssistantIntentClassifier.requestsDatedSales(question))
        }
        assertTrue(RestaurantAssistantIntentClassifier.requestsDatedSales("Quanto foi a receita em 2025?"))
        assertTrue(RestaurantAssistantIntentClassifier.requestsDatedSales("Quanto vendi em 29/10/2026?"))
        assertTrue(RestaurantAssistantIntentClassifier.requestsDatedSales("Qual foi a faturação em 29.10.2026?"))
        assertTrue(RestaurantAssistantIntentClassifier.requestsDatedSales("Quanto vendi em 29-10?"))
        assertTrue(RestaurantAssistantIntentClassifier.requestsDatedMenuPerformance("Quais pratos venderam mais em 29/10/2026?"))
        assertEquals(
            RestaurantAssistantIntent.ECONOMIC_MANAGER,
            RestaurantAssistantIntentClassifier.classify("Quanto vendi em 29/10/2026?").intent
        )
        assertEquals(
            RestaurantAssistantIntent.MENU_ENGINEERING,
            RestaurantAssistantIntentClassifier.classify("Quais pratos venderam mais em 29/10/2026?").intent
        )
        assertTrue(!RestaurantAssistantIntentClassifier.requestsDatedSales("Como posso aumentar as vendas?"))
        assertTrue(!RestaurantAssistantIntentClassifier.requestsDatedSales("Como posso aumentar as vendas no próximo domingo?"))
        assertTrue(!RestaurantAssistantIntentClassifier.requestsDatedMenuPerformance("Como posso reduzir custos esta semana?"))
        assertTrue(!RestaurantAssistantIntentClassifier.requestsDatedSales("Previsão de vendas para amanhã"))
        assertTrue(!RestaurantAssistantIntentClassifier.requestsDatedSales("Quanto vou vender na próxima semana?"))
        assertTrue(!RestaurantAssistantIntentClassifier.requestsDatedMenuPerformance("Previsão de vendas da próxima semana"))
        assertEquals(
            RestaurantAssistantIntent.ECONOMIC_MANAGER,
            RestaurantAssistantIntentClassifier.classify("Previsão de vendas para amanhã").intent
        )
        assertEquals(
            RestaurantAssistantIntent.ECONOMIC_MANAGER,
            RestaurantAssistantIntentClassifier.classify("Quanto vou vender na próxima semana?").intent
        )
        assertEquals(
            RestaurantAssistantIntent.MENU_ENGINEERING,
            RestaurantAssistantIntentClassifier.classify("Como posso aumentar as vendas no próximo domingo?").intent
        )
    }

    @Test fun routesDatedSpecificDishSalesToMenuEngineeringWithoutGuessingThePeriod() {
        val cases = listOf(
            "Quanto vendi no domingo passado?" to RestaurantAssistantIntent.ECONOMIC_MANAGER,
            "Qual foi a faturação nos últimos 30 dias?" to RestaurantAssistantIntent.ECONOMIC_MANAGER,
            "Quais pratos venderam mais no domingo passado?" to RestaurantAssistantIntent.MENU_ENGINEERING,
            "Quais foram os artigos mais vendidos no último mês?" to RestaurantAssistantIntent.MENU_ENGINEERING,
            "Que artigos venderam mais em março?" to RestaurantAssistantIntent.MENU_ENGINEERING,
            "Quais pratos foram mais rentáveis no mês passado?" to RestaurantAssistantIntent.MENU_ENGINEERING,
            "Quais pratos foram mais rentáveis do mês de setembro?" to RestaurantAssistantIntent.MENU_ENGINEERING,
            "Quais artigos tiveram a margem mais alta na semana passada?" to RestaurantAssistantIntent.MENU_ENGINEERING,
            "Qual foi a margem média em março?" to RestaurantAssistantIntent.ECONOMIC_MANAGER,
            "Qual era o preço do Bitoque no mês passado?" to RestaurantAssistantIntent.ECONOMIC_MANAGER,
            "Quanto gastei em salários este mês?" to RestaurantAssistantIntent.ECONOMIC_MANAGER,
            "Quanto gastei em setembro?" to RestaurantAssistantIntent.ECONOMIC_MANAGER,
            "Quanto paguei em março?" to RestaurantAssistantIntent.ECONOMIC_MANAGER,
            "Qual foi o lucro em março?" to RestaurantAssistantIntent.ECONOMIC_MANAGER
        )
        cases.forEach { (question, expected) ->
            assertEquals("$question -> ${RestaurantAssistantIntentClassifier.classify(question)}",
                expected, RestaurantAssistantIntentClassifier.classify(question).intent)
        }
    }

    @Test fun routesDatedSalesPromotionsEnergyAndDishProfitQuestions() {
        val cases = listOf(
            "Quanto vendemos hoje?" to RestaurantAssistantIntent.ECONOMIC_MANAGER,
            "Quais foram as vendas de ontem?" to RestaurantAssistantIntent.ECONOMIC_MANAGER,
            "Tens ideias de promoção?" to RestaurantAssistantIntent.MARKETING,
            "O que comprar esta semana?" to RestaurantAssistantIntent.ECONOMIC_MANAGER,
            "Como reduzo a conta de luz?" to RestaurantAssistantIntent.ECONOMIC_MANAGER,
            "Quais pratos deram prejuízo?" to RestaurantAssistantIntent.MENU_ENGINEERING,
            "O bitoque é rentável?" to RestaurantAssistantIntent.PRICING,
            "Como analiso os custos mensais?" to RestaurantAssistantIntent.ECONOMIC_MANAGER,
            "Faz um resumo do dinheiro" to RestaurantAssistantIntent.ECONOMIC_MANAGER
        )
        cases.forEach { (question, expected) ->
            assertEquals("$question -> ${RestaurantAssistantIntentClassifier.classify(question)}",
                expected, RestaurantAssistantIntentClassifier.classify(question).intent)
        }
    }

    @Test fun compoundHistoricalMetricAndAdviceKeepsTheHistoricalPeriodGuard() {
        val historicProfitWithAdvice = "Qual foi o lucro em março e como posso reduzir custos?"
        assertTrue(RestaurantAssistantIntentClassifier.requestsDatedMenuPerformance(historicProfitWithAdvice))
        assertTrue(!RestaurantAssistantIntentClassifier.requestsDatedSales(historicProfitWithAdvice))

        val historicSalesWithAdvice = "Quanto vendi em março e como posso aumentar as vendas?"
        assertTrue(RestaurantAssistantIntentClassifier.requestsDatedSales(historicSalesWithAdvice))
        assertTrue(RestaurantAssistantIntentClassifier.requestsDatedMenuPerformance(historicSalesWithAdvice))

        val historicAnalysisWithAdvice = "Analisa as vendas em março e sugere como melhorar."
        assertTrue(RestaurantAssistantIntentClassifier.requestsDatedSales(historicAnalysisWithAdvice))
        assertTrue(RestaurantAssistantIntentClassifier.requestsDatedMenuPerformance(historicAnalysisWithAdvice))

        // Purely forward-looking advice must not be mistaken for an actual historical metric.
        assertTrue(!RestaurantAssistantIntentClassifier.requestsDatedSales(
            "Como posso aumentar as vendas no próximo domingo?"
        ))
        assertTrue(!RestaurantAssistantIntentClassifier.requestsDatedMenuPerformance(
            "Como posso reduzir custos esta semana?"
        ))
    }

}
