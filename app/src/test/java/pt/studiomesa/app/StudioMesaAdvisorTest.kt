package pt.studiomesa.app

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class StudioMesaAdvisorTest {
    private fun item(name: String, price: String, cost: String) = AssistantMenuItem(name, BigDecimal(price), BigDecimal(cost))
    private fun snapshot(
        menu: List<AssistantMenuItem> = listOf(item("Sopa", "5.00", "2.00")),
        demo: Boolean = false, configured: Boolean = true, activeOrders: Int = 0,
        stockAvailable: Boolean = false, stockLoaded: Boolean = false,
        stockItems: List<AssistantStockItem> = emptyList(),
        reservationsAvailable: Boolean = false, reservationsLoaded: Boolean = false, pendingReservations: Int = 0,
        salesLines: List<AssistantSaleLine> = emptyList()
    ) = StudioMesaAssistantSnapshot(
        isDemo = demo, restaurantConfigured = configured, menuItems = menu,
        activeOrderCount = activeOrders, preparationOrderCount = activeOrders.coerceAtMost(1),
        stockFeatureAvailable = stockAvailable, stockLoaded = stockLoaded, stockItems = stockItems,
        reservationsFeatureAvailable = reservationsAvailable, reservationsLoaded = reservationsLoaded,
        pendingReservations = pendingReservations, salesLines = salesLines
    )

    @Test fun suggestedSellingPriceUsesChosenCostShareAndRoundsToCents() {
        assertEquals(BigDecimal("10.67"), StudioMesaAdvisor.recommendedSellingPrice(BigDecimal("3.20"), BigDecimal("30")))
    }
    @Test fun refusesToSuggestPriceForInvalidCostOrTarget() {
        assertNull(StudioMesaAdvisor.recommendedSellingPrice(BigDecimal.ZERO))
        assertNull(StudioMesaAdvisor.recommendedSellingPrice(BigDecimal("3.20"), BigDecimal.ZERO))
        assertNull(StudioMesaAdvisor.recommendedSellingPrice(BigDecimal("3.20"), BigDecimal("101")))
    }
    @Test fun flagsMenuItemsAboveReferenceCostShare() {
        val insight = StudioMesaAdvisor.recommendations(snapshot(menu = listOf(item("Prato", "10.00", "4.00")))).first { it.id.startsWith("price-") }
        assertTrue(insight.detail.contains("40,0%"))
        assertTrue(insight.detail.contains("13,33"))
        assertEquals(AssistantPriority.URGENT, insight.priority)
    }
    @Test fun missingCostNeverProducesAnInventedSuggestedPrice() {
        val data = snapshot(menu = listOf(item("Prato", "10.00", "0")))
        assertTrue(StudioMesaAdvisor.recommendations(data).any { it.id == "missing-costs" })
        assertTrue(StudioMesaAdvisor.answerQuestion("Que preço devo cobrar?", data).response.contains("Não vou inventar"))
    }
    @Test fun flagsStockOnlyAfterTheStockListWasLoaded() {
        val stock = AssistantStockItem("Arroz", BigDecimal("1"), BigDecimal("3"))
        assertFalse(StudioMesaAdvisor.recommendations(snapshot(stockAvailable = true, stockItems = listOf(stock))).any { it.title.contains("Arroz") })
        assertTrue(StudioMesaAdvisor.recommendations(snapshot(stockAvailable = true, stockLoaded = true, stockItems = listOf(stock))).any { it.title.contains("Arroz") })
    }
    @Test fun questionIntentFindsPricingAndOrderGuidance() {
        val data = snapshot(menu = listOf(item("Prato", "10.00", "4.00")), activeOrders = 4)
        assertEquals("Análise de preços", StudioMesaAdvisor.answerQuestion("Como melhoro o preço?", data).title)
        assertEquals("Análise de pedidos", StudioMesaAdvisor.answerQuestion("Como está a fila da cozinha?", data).title)
    }

    @Test fun salesGrowthQuestionsAreNotMisroutedToPriceAnalysis() {
        val data = snapshot(
            menu = listOf(item("Sopa", "5.00", "2.00")),
            salesLines = listOf(AssistantSaleLine("Sopa", 6, "Concluído"))
        )
        val answer = StudioMesaAdvisor.answerQuestion("Como posso aumentar as vendas?", data)
        assertEquals("Engenharia da carta", answer.title)
        assertTrue(answer.response.contains("Triagem indicativa"))
    }

    @Test fun economicManagementQuestionsPointToTheOptInManager() {
        val answer = StudioMesaAdvisor.answerQuestion("Como calculo o ponto de equilíbrio e a tesouraria?", snapshot())
        assertEquals("Gestor económico (opcional)", answer.title)
        assertTrue(answer.response.contains("não consultam bancos ou fornecedores"))
    }
    @Test fun newEconomicQuestionsRouteToTheOptInManager() {
        listOf(
            "Como comparo fornecedores?",
            "Como controlo desperdício por motivo?",
            "Como comparo orçamento com o consumo real?",
            "Como calculo o custo de pessoal?",
            "Faz um resumo da tesouraria e do custo alimentar",
            "Que quantidade devo encomendar ao fornecedor?",
            "Como analiso os custos fixos e variáveis?",
            "Como faço uma previsão financeira?"
        ).forEach { question ->
            assertEquals("Gestor económico (opcional)", StudioMesaAdvisor.answerQuestion(question, snapshot()).title)
        }
        listOf(
            "Como calculo o custo dos ingredientes por dose?",
            "Como preparo a ficha técnica do prato?"
        ).forEach { question ->
            assertEquals("Custo por receita e ingredientes", StudioMesaAdvisor.answerQuestion(question, snapshot()).title)
        }
        assertEquals("Resumo da operação", StudioMesaAdvisor.answerQuestion("Faz um resumo da operação", snapshot()).title)
    }

    @Test fun specificPriceQuestionsStillUsePriceAnalysis() {
        val answer = StudioMesaAdvisor.answerQuestion(
            "Qual deve ser o preço de venda deste prato?",
            snapshot(menu = listOf(item("Prato", "10.00", "4.00")))
        )
        assertEquals("Análise de preços", answer.title)
    }

    @Test fun pricingQuestionAnalysesTheNamedMenuItemOnly() {
        val data = snapshot(menu = listOf(
            item("Sopa", "5.00", "2.00"),
            item("Hambúrguer", "12.00", "5.00"),
            item("Salada", "8.00", "2.00")
        ))
        val answer = StudioMesaAdvisor.answerQuestion("Qual deve ser o preço de venda da Sopa?", data)
        assertEquals("Análise de preços", answer.title)
        assertTrue(answer.response.contains("Sopa"))
        assertFalse(answer.response.contains("Hambúrguer"))
        assertFalse(answer.response.contains("Salada"))
        assertTrue(answer.response.contains("o artigo selecionado"))
    }

    @Test fun pricingQuestionPrefersTheFullDishNameOverAShorterPrefix() {
        val data = snapshot(menu = listOf(
            item("Hambúrguer", "10.00", "4.00"),
            item("Hambúrguer Clássico", "14.00", "5.00"),
            item("Sopa", "5.00", "2.00")
        ))
        val answer = StudioMesaAdvisor.answerQuestion(
            "Qual é o preço do Hambúrguer Clássico?",
            data
        )
        assertEquals("Análise de preços", answer.title)
        assertTrue(answer.response.contains("Hambúrguer Clássico"))
        assertFalse(answer.response.contains("Hambúrguer: custo"))
        assertFalse(answer.response.contains("Sopa"))
    }

    @Test fun pricingQuestionClarifiesWhenDishNameIsAmbiguous() {
        val data = snapshot(menu = listOf(
            item("Hambúrguer Clássico", "14.00", "5.00"),
            item("Hambúrguer Vegetariano", "13.00", "5.00"),
            item("Sopa", "5.00", "2.00")
        ))
        val answer = StudioMesaAdvisor.answerQuestion("Quanto devo cobrar pelo hambúrguer?", data)
        assertEquals("Qual artigo da carta?", answer.title)
        assertTrue(answer.response.contains("Hambúrguer Clássico"))
        assertTrue(answer.response.contains("Hambúrguer Vegetariano"))
        assertTrue(answer.response.contains("Não alterei nenhum valor"))
    }
    @Test fun economicQuestionShowsRegisteredCostForTheNamedDish() {
        val data = snapshot(menu = listOf(
            item("Bitoque", "14.00", "4.50"),
            item("Sopa", "5.00", "2.00")
        ))
        val answer = StudioMesaAdvisor.answerQuestion("Como calculo o custo do prato Bitoque?", data)
        assertEquals("Custo registado do artigo", answer.title)
        assertTrue(answer.response.contains("Bitoque"))
        assertTrue(answer.response.contains("4,50"))
        assertTrue(answer.response.contains("contribuição unitária estimada"))
        assertFalse(answer.response.contains("Sopa"))
        assertTrue(answer.response.contains("não é uma ficha técnica de ingredientes"))
    }

    @Test fun economicQuestionDoesNotInventMissingDishCost() {
        val data = snapshot(menu = listOf(item("Bitoque", "14.00", "0")))
        val answer = StudioMesaAdvisor.answerQuestion("Como calculo o custo do prato Bitoque?", data)
        assertEquals("Custo em falta: Bitoque", answer.title)
        assertTrue(answer.response.contains("Não vou inventar um valor"))
    }

    @Test fun ingredientCostQuestionExplainsNeedForRecipeDetails() {
        val answer = StudioMesaAdvisor.answerQuestion(
            "Como calculo o custo dos ingredientes por dose?",
            snapshot(menu = listOf(item("Bitoque", "14.00", "4.50")))
        )
        assertEquals("Custo por receita e ingredientes", answer.title)
        assertTrue(answer.response.contains("quantidade por dose"))
        assertTrue(answer.response.contains("Não vou inventar ingredientes"))
    }

    @Test fun monthlyProfitQuestionDoesNotInventHistoricalProfit() {
        val answer = StudioMesaAdvisor.answerQuestion("Quanto lucro tive este mês?", snapshot())
        assertEquals("Estimativa de resultado mensal", answer.title)
        assertTrue(answer.response.contains("Não consigo confirmar o lucro de um mês"))
        assertTrue(answer.response.contains("não vou inventar um lucro"))
    }

    @Test fun contributionRankingIsNotMislabelledAsNetProfit() {
        val data = snapshot(menu = listOf(
            item("Sopa", "5.00", "1.00"),
            item("Bitoque", "14.00", "4.50"),
            item("Salada", "8.00", "2.00")
        ))
        val answer = StudioMesaAdvisor.answerQuestion("Quais pratos dão mais lucro?", data)
        assertEquals("Contribuição estimada por artigo", answer.title)
        assertTrue(answer.response.contains("Bitoque"))
        assertTrue(answer.response.contains("não é lucro líquido"))
        assertTrue(answer.response.indexOf("Bitoque") < answer.response.indexOf("Salada"))
        assertTrue(answer.response.indexOf("Salada") < answer.response.indexOf("Sopa"))
    }

    @Test fun pricingQuestionClarifiesWhenTwoNamedDishesAreRequested() {
        val data = snapshot(menu = listOf(
            item("Sopa", "5.00", "2.00"),
            item("Hambúrguer", "12.00", "5.00")
        ))
        val answer = StudioMesaAdvisor.answerQuestion(
            "Qual deve ser o preço de venda da Sopa e do Hambúrguer?",
            data
        )
        assertEquals("Qual artigo da carta?", answer.title)
        assertTrue(answer.response.contains("Sopa"))
        assertTrue(answer.response.contains("Hambúrguer"))
    }

    @Test fun genericWordPratoDoesNotCountAsASelectedDishName() {
        val data = snapshot(menu = listOf(item("Prato", "10.00", "4.00")))
        val answer = StudioMesaAdvisor.answerQuestion("Qual deve ser o preço de venda deste prato?", data)
        assertEquals("Análise de preços", answer.title)
        assertTrue(answer.response.contains("1 artigo(s)"))
        assertFalse(answer.response.contains("o artigo selecionado"))
    }

    @Test fun unknownQuestionsReceiveAnHonestClarificationInsteadOfUnrelatedAdvice() {
        val answer = StudioMesaAdvisor.answerQuestion(
            "Compara o custo da energia e a concorrência na minha zona",
            snapshot()
        )
        assertEquals("Preciso de uma pergunta mais específica", answer.title)
        assertTrue(answer.response.contains("não consegui identificar com segurança"))
        assertNull(answer.actionPage)

        val blank = StudioMesaAdvisor.answerQuestion("   ", snapshot())
        assertTrue(blank.response.contains("Escreve uma pergunta concreta"))
    }

    @Test fun problemResponseDoesNotPretendToTestConnectivity() {
        assertTrue(StudioMesaAdvisor.answerQuestion("A app está com erro de ligação?", snapshot()).response.contains("Não consigo testar a ligação à Internet"))
    }
    @Test fun labelsDemoModeClearly() {
        val demo = StudioMesaAdvisor.recommendations(snapshot(demo = true, configured = false)).first { it.id == "demo-mode" }
        assertTrue(demo.detail.contains("demonstração"))
    }
    @Test fun onlyReportsPendingReservationsWhenListWasLoaded() {
        val notLoaded = StudioMesaAdvisor.recommendations(snapshot(reservationsAvailable = true, pendingReservations = 3))
        assertFalse(notLoaded.any { it.id == "pending-reservations" })
        val loaded = StudioMesaAdvisor.recommendations(snapshot(reservationsAvailable = true, reservationsLoaded = true, pendingReservations = 3))
        assertTrue(loaded.any { it.id == "pending-reservations" })
    }

    @Test fun menuEngineeringClassifiesFourGroupsFromCompletedSales() {
        val menu = listOf(item("Estrela", "20.00", "5.00"), item("Popular", "10.00", "9.00"), item("Puzzle", "20.00", "2.00"), item("Rever", "5.00", "4.50"))
        val sales = listOf(AssistantSaleLine("Estrela", 6, "Concluído"), AssistantSaleLine("Popular", 14, "Concluído"), AssistantSaleLine("Puzzle", 2, "Cancelado"), AssistantSaleLine("Rever", 1, "Aberto"))
        val report = StudioMesaAdvisor.menuEngineering(snapshot(menu = menu, salesLines = sales))
        assertTrue(report.hasEnoughSales)
        assertTrue(report.hasMarginData)
        assertEquals(20, report.completedUnits)
        assertEquals(MenuEngineeringClass.STAR, report.items.first { it.itemName == "Estrela" }.classification)
        assertEquals(MenuEngineeringClass.WORKHORSE, report.items.first { it.itemName == "Popular" }.classification)
        assertEquals(MenuEngineeringClass.PUZZLE, report.items.first { it.itemName == "Puzzle" }.classification)
        assertEquals(MenuEngineeringClass.DOG, report.items.first { it.itemName == "Rever" }.classification)
    }

    @Test fun menuEngineeringIgnoresOpenAndCancelledOrdersAndRequiresMinimumSample() {
        val data = snapshot(menu = listOf(item("Prato", "10.00", "4.00")), salesLines = listOf(AssistantSaleLine("Prato", 3, "Aberto"), AssistantSaleLine("Prato", 8, "Cancelado")))
        val report = StudioMesaAdvisor.menuEngineering(data)
        assertFalse(report.hasEnoughSales)
        assertEquals(0, report.completedUnits)
        assertTrue(StudioMesaAdvisor.answerQuestion("Analisa a popularidade do menu", data).response.contains("Não vou inventar popularidade"))
    }

    @Test fun dailyBriefingSummarizesDataAndDoesNotClaimToForecast() {
        val answer = StudioMesaAdvisor.dailyBriefing(snapshot(menu = listOf(item("Prato", "10.00", "0")), demo = true, configured = false, activeOrders = 4))
        assertEquals("Resumo da operação", answer.title)
        assertTrue(answer.response.contains("Pedidos ativos: 4"))
        assertTrue(answer.response.contains("não é previsão de vendas"))
    }

    @Test fun marketingDraftUsesOnlyStoredNameDescriptionAndPrice() {
        val menuItem = AssistantMenuItem("Bolo da casa", BigDecimal("3.50"), BigDecimal("1.00"), description = "Bolo de chocolate")
        val drafts = StudioMesaAdvisor.marketingDrafts(snapshot(menu = listOf(menuItem)))
        assertEquals(1, drafts.size)
        assertTrue(drafts[0].caption.contains("Bolo da casa"))
        assertTrue(drafts[0].caption.contains("Bolo de chocolate"))
        assertTrue(drafts[0].caption.contains("3,50"))
        assertFalse(drafts[0].caption.contains("desconto"))
        assertTrue(StudioMesaAdvisor.answerQuestion("Escreve um post para Instagram", snapshot(menu = listOf(menuItem))).response.contains("Revê e aprova"))
    }
}
