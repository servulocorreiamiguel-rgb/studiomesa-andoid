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
            "Como comparo fornecedores?" to "Comparar fornecedores",
            "Como controlo desperdício por motivo?" to "Analisar desperdício",
            "Como comparo orçamento com o consumo real?" to "Consumo teórico vs. real",
            "Como calculo o custo de pessoal?" to "Cenário de custo de pessoal",
            "Faz um resumo da tesouraria e do custo alimentar" to "Gestor económico (opcional)",
            "Que quantidade devo encomendar ao fornecedor?" to "Quantidade indicativa de reposição",
            "Como analiso os custos fixos e variáveis?" to "Gestor económico (opcional)",
            "Como faço uma previsão financeira?" to "Referência simples de vendas"
        ).forEach { (question, expectedTitle) ->
            assertEquals("$question -> ${StudioMesaAdvisor.answerQuestion(question, snapshot()).title}",
                expectedTitle, StudioMesaAdvisor.answerQuestion(question, snapshot()).title)
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

    @Test fun naturalPastTenseDishCostQuestionReturnsTheRegisteredCost() {
        val data = snapshot(menu = listOf(
            item("Bitoque", "14.00", "4.50"),
            item("Sopa", "5.00", "2.00")
        ))

        val answer = StudioMesaAdvisor.answerQuestion("Quanto me custou o Bitoque?", data)

        assertEquals("Custo registado do artigo", answer.title)
        assertTrue(answer.response.contains("Bitoque"))
        assertTrue(answer.response.contains("4,50"))
        assertFalse(answer.response.contains("Sopa"))
    }

    @Test fun ambiguousNaturalDishCostQuestionAsksWhichDishInsteadOfChoosing() {
        val data = snapshot(menu = listOf(
            item("Hambúrguer Clássico", "14.00", "5.00"),
            item("Hambúrguer Vegetariano", "13.00", "5.00")
        ))

        val answer = StudioMesaAdvisor.answerQuestion("Quanto me custou o hambúrguer?", data)

        assertEquals("Qual artigo da carta?", answer.title)
        assertTrue(answer.response.contains("Hambúrguer Clássico"))
        assertTrue(answer.response.contains("Hambúrguer Vegetariano"))
    }

    @Test fun unknownNaturalDishCostQuestionDoesNotListUnrelatedCostsAsItsAnswer() {
        val data = snapshot(menu = listOf(item("Bitoque", "14.00", "4.50")))

        val answer = StudioMesaAdvisor.answerQuestion("Quanto me custou o custo de entrega?", data)

        assertEquals("Custo do artigo não identificado", answer.title)
        assertTrue(answer.response.contains("nome exato de um artigo"))
        assertFalse(answer.response.contains("Bitoque:"))
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
            "Que nome artístico devo dar ao robô mascote?",
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

    @Test fun lowContributionQuestionRanksWeakestItemsWithoutClaimingNetLoss() {
        val data = snapshot(menu = listOf(
            item("Sopa", "5.00", "2.00"),
            item("Bitoque", "14.00", "16.00"),
            item("Salada", "8.00", "2.00")
        ))
        val answer = StudioMesaAdvisor.answerQuestion("Quais pratos estão a dar prejuízo?", data)
        assertEquals("Contribuição estimada por artigo", answer.title)
        assertTrue(answer.response.contains("Bitoque"))
        assertFalse(answer.response.contains("Sopa"))
        assertFalse(answer.response.contains("Salada"))
        assertTrue(answer.response.contains("não prova prejuízo líquido"))
        assertTrue(answer.response.contains("custo interno registado ultrapassa o preço"))
    }

    @Test fun marginQuestionsRankByContributionMarginPercentageNotEuroContribution() {
        val data = snapshot(menu = listOf(
            item("Sopa", "5.00", "1.00"),
            item("Bitoque", "14.00", "7.00"),
            item("Salada", "8.00", "2.00")
        ))
        val best = StudioMesaAdvisor.answerQuestion("Quais os artigos com melhor margem?", data)
        assertEquals("Margem estimada por artigo", best.title)
        assertTrue(best.response.indexOf("Sopa") < best.response.indexOf("Salada"))
        assertTrue(best.response.indexOf("Salada") < best.response.indexOf("Bitoque"))
        assertTrue(best.response.contains("80,0%"))
        val weakest = StudioMesaAdvisor.answerQuestion("Quais pratos são menos rentáveis?", data)
        assertEquals("Margem estimada por artigo", weakest.title)
        assertTrue(weakest.response.indexOf("Bitoque") < weakest.response.indexOf("Salada"))
    }

    @Test fun doesNotUseUnfilteredSalesToAnswerQuestionAboutYesterday() {
        val data = snapshot(
            menu = listOf(item("Bitoque", "14.00", "4.00")),
            salesLines = listOf(AssistantSaleLine("Bitoque", 10, "Concluído"))
        )
        val answer = StudioMesaAdvisor.answerQuestion("Qual foi o prato mais vendido ontem?", data)
        assertEquals("Período de desempenho não confirmado", answer.title)
        assertTrue(answer.response.contains("não inclui datas por pedido"))
        assertEquals("Análise", answer.actionPage)
        assertFalse(answer.response.contains("10 unidades"))
    }

    @Test fun doesNotInferReservedTablesOrDailyAvailabilityFromPendingCount() {
        val data = snapshot(reservationsAvailable = true, reservationsLoaded = true, pendingReservations = 3)
        val answer = StudioMesaAdvisor.answerQuestion("Quantas mesas estão reservadas hoje?", data)
        assertEquals("Disponibilidade de reservas não confirmada", answer.title)
        assertTrue(answer.response.contains("não um mapa confirmado de mesas"))
        assertFalse(answer.response.contains("Há 3 reserva"))
    }

    @Test fun naturalLanguageRecipeCostQuestionExplainsRequiredInputs() {
        val answer = StudioMesaAdvisor.answerQuestion("Quanto custa cada receita?", snapshot())
        assertEquals("Custo por receita e ingredientes", answer.title)
        assertTrue(answer.response.contains("introduz cada ingrediente"))
        assertTrue(answer.response.contains("Não vou inventar ingredientes"))
    }

    @Test fun crashQuestionGetsSpecificSafeTroubleshootingInsteadOfGenericNetworkAdvice() {
        val answer = StudioMesaAdvisor.answerQuestion("A app fecha quando tento guardar", snapshot())
        assertEquals("Diagnóstico inicial", answer.title)
        assertTrue(answer.response.contains("registos de falha"))
        assertFalse(answer.response.contains("verifica a conta e o restaurante"))
    }


    @Test fun stockQuestionAnswersOnlyTheNamedItemWhenAvailable() {
        val data = snapshot(
            stockAvailable = true,
            stockLoaded = true,
            stockItems = listOf(
                AssistantStockItem("Arroz", BigDecimal("2"), BigDecimal("5")),
                AssistantStockItem("Frango", BigDecimal("8"), BigDecimal("3"))
            )
        )
        val answer = StudioMesaAdvisor.answerQuestion("Quanto tenho de arroz em stock?", data)
        assertEquals("Stock de Arroz", answer.title)
        assertTrue(answer.response.contains("2 disponíveis"))
        assertTrue(answer.response.contains("limite mínimo 5"))
        assertFalse(answer.response.contains("Frango"))
    }

    @Test fun stockQuestionDoesNotConfuseUnknownNamedItemWithNoLowStock() {
        val data = snapshot(
            stockAvailable = true,
            stockLoaded = true,
            stockItems = listOf(AssistantStockItem("Frango", BigDecimal("8"), BigDecimal("3")))
        )
        val answer = StudioMesaAdvisor.answerQuestion("Quanto tenho de arroz em stock?", data)
        assertEquals("Artigo de stock não encontrado", answer.title)
        assertTrue(answer.response.contains("não prova que não exista fisicamente"))
        assertFalse(answer.response.contains("Não encontrei artigos abaixo do limite mínimo"))
    }

    @Test fun stockQuestionAsksForClarificationWhenSeveralItemsAreNamed() {
        val data = snapshot(
            stockAvailable = true,
            stockLoaded = true,
            stockItems = listOf(
                AssistantStockItem("Arroz", BigDecimal("2"), BigDecimal("5")),
                AssistantStockItem("Frango", BigDecimal("8"), BigDecimal("3"))
            )
        )
        val answer = StudioMesaAdvisor.answerQuestion("Compara arroz e frango em stock", data)
        assertEquals("Qual artigo de stock?", answer.title)
        assertTrue(answer.response.contains("Arroz"))
        assertTrue(answer.response.contains("Frango"))
    }


    @Test fun marketingQuestionForNamedDishDoesNotReturnOtherDishDrafts() {
        val data = snapshot(menu = listOf(
            AssistantMenuItem("Bolo da casa", BigDecimal("3.50"), BigDecimal("1.00"), description = "Bolo de chocolate"),
            AssistantMenuItem("Bitoque", BigDecimal("14.00"), BigDecimal("4.50")),
            AssistantMenuItem("Sopa", BigDecimal("5.00"), BigDecimal("1.50"))
        ))
        val answer = StudioMesaAdvisor.answerQuestion("Escreve um post para o Bitoque", data)
        assertEquals("Rascunhos para rever", answer.title)
        assertTrue(answer.response.contains("Bitoque"))
        assertFalse(answer.response.contains("Bolo da casa"))
        assertFalse(answer.response.contains("Sopa"))
        assertTrue(answer.response.contains("Revê e aprova"))
    }

    @Test fun marketingQuestionAsksForClarificationWhenSeveralDishesAreNamed() {
        val data = snapshot(menu = listOf(
            item("Hambúrguer Clássico", "14.00", "5.00"),
            item("Hambúrguer Vegetariano", "13.00", "5.00"),
            item("Sopa", "5.00", "1.50")
        ))
        val answer = StudioMesaAdvisor.answerQuestion("Faz uma publicação para o hambúrguer", data)
        assertEquals("Qual artigo da carta?", answer.title)
        assertTrue(answer.response.contains("Hambúrguer Clássico"))
        assertTrue(answer.response.contains("Hambúrguer Vegetariano"))
    }


    @Test fun genericLowStockQuestionStillListsLowItemsInsteadOfInventingAnItemName() {
        val data = snapshot(
            stockAvailable = true,
            stockLoaded = true,
            stockItems = listOf(
                AssistantStockItem("Arroz", BigDecimal("2"), BigDecimal("5")),
                AssistantStockItem("Frango", BigDecimal("8"), BigDecimal("3"))
            )
        )
        val answer = StudioMesaAdvisor.answerQuestion("Quais artigos estão baixos em stock?", data)
        assertEquals("Análise de stock", answer.title)
        assertTrue(answer.response.contains("Arroz"))
        assertFalse(answer.response.contains("Artigo de stock não encontrado"))
    }


    @Test fun economicQuestionsExplainTheCorrectToolAndRequiredInputFormat() {
        val cases = listOf(
            "Como comparo fornecedores?" to ("Comparar fornecedores" to "artigo | fornecedor | unidade"),
            "Como registo desperdício por motivo?" to ("Analisar desperdício" to "quantidade | custo por unidade | motivo"),
            "Como comparo consumo teórico com real?" to ("Consumo teórico vs. real" to "consumo teórico | consumo real"),
            "Como comparo orçamento e realizado?" to ("Orçamento vs. realizado" to "orçamento | valor real"),
            "Como calculo custo de pessoal?" to ("Cenário de custo de pessoal" to "custo total por hora"),
            "Quanto devo encomendar?" to ("Quantidade indicativa de reposição" to "consumo médio por dia"),
            "Como calculo tesouraria a 30 dias?" to ("Previsão simples de tesouraria" to "recebimentos previstos"),
            "Como calculo o ponto de equilíbrio?" to ("Ponto de equilíbrio" to "custo alimentar consumido"),
            "Como faço uma previsão financeira?" to ("Referência simples de vendas" to "últimas quatro semanas")
        )
        cases.forEach { (question, expected) ->
            val answer = StudioMesaAdvisor.answerQuestion(question, snapshot())
            assertEquals("$question -> ${answer.title}", expected.first, answer.title)
            assertTrue("$question -> ${answer.response}", answer.response.contains(expected.second))
        }
    }


    @Test fun reservationStatusQuestionsDoNotReusePendingCountAsConfirmedOrCancelled() {
        val data = snapshot(reservationsAvailable = true, reservationsLoaded = true, pendingReservations = 4)
        val answer = StudioMesaAdvisor.answerQuestion("Quantas reservas canceladas temos?", data)
        assertEquals("Disponibilidade de reservas não confirmada", answer.title)
        assertTrue(answer.response.contains("não um mapa confirmado"))
        assertFalse(answer.response.contains("Há 4 reserva"))
    }

    @Test fun orderDelayQuestionDoesNotInventDelayedOrderDetails() {
        val data = snapshot(activeOrders = 5)
        val answer = StudioMesaAdvisor.answerQuestion("Quais pedidos estão atrasados?", data)
        assertEquals("Detalhes dos pedidos não confirmados", answer.title)
        assertTrue(answer.response.contains("não inclui a lista de cada pedido"))
        assertFalse(answer.response.contains("Há 5 pedido"))
    }


    @Test fun specificDishLossQuestionChecksItsRegisteredContributionSafely() {
        val data = snapshot(menu = listOf(
            item("Bitoque", "10.00", "12.00"),
            item("Sopa", "5.00", "1.00")
        ))
        val answer = StudioMesaAdvisor.answerQuestion("O Bitoque dá prejuízo?", data)
        assertEquals("Custo registado do artigo", answer.title)
        assertTrue(answer.response.contains("Bitoque"))
        assertTrue(answer.response.contains("contribuição unitária estimada: -2,00"))
        assertTrue(answer.response.contains("Isto não prova o prejuízo líquido"))
        assertFalse(answer.response.contains("Sopa"))
    }

    @Test fun futureSalesQuestionsUseClearlyLabeledFourWeekReferenceInsteadOfHistoricalPeriodError() {
        val questions = listOf(
            "Previsão de vendas para amanhã",
            "Previsão de vendas da próxima semana",
            "Quanto vou vender na próxima semana?"
        )
        questions.forEach { question ->
            val answer = StudioMesaAdvisor.answerQuestion(question, snapshot())
            assertEquals("$question -> ${answer.title}", "Referência simples de vendas", answer.title)
            assertTrue(answer.response.contains("últimas quatro semanas"))
            assertFalse(answer.response.contains("Período de vendas não confirmado"))
        }
    }

    @Test fun currentWeekSalesQuestionIsNotAnsweredWithHistoricalAverage() {
        val answer = StudioMesaAdvisor.answerQuestion("Como está a faturação esta semana?", snapshot())
        assertEquals("Período de vendas não confirmado", answer.title)
        assertTrue(answer.response.contains("Não vou apresentar totais acumulados"))
        assertFalse(answer.response.contains("média das últimas quatro semanas"))
    }

    @Test fun dateGuardExplainsThatAnalysisFiltersTotalsButNotHistoricalDishMargins() {
        val answer = StudioMesaAdvisor.answerQuestion("Qual foi o prato mais vendido ontem?", snapshot())
        assertEquals("Período de desempenho não confirmado", answer.title)
        assertTrue(answer.response.contains("Na versão Premium, a página Análise permite filtrar contagens"))
        assertTrue(answer.response.contains("não calcula rentabilidade histórica por artigo"))
        assertEquals("Análise", answer.actionPage)
        assertFalse(answer.response.contains("ainda não permite filtrar estes dados por período"))
    }

    @Test fun datedMenuSalesNeverReuseAllTimeSalesAsIfTheyBelongedToRequestedPeriod() {
        val data = snapshot(
            menu = listOf(item("Sopa", "5.00", "2.00"), item("Bitoque", "14.00", "4.50")),
            salesLines = listOf(
                AssistantSaleLine("Sopa", 20, "Concluído"),
                AssistantSaleLine("Bitoque", 8, "Concluído")
            )
        )
        val questions = listOf(
            "Quais pratos venderam mais no domingo passado?",
            "Quais artigos foram mais vendidos nos últimos 30 dias?",
            "Quanto vendi em março?",
            "Quanto vendi em 29/10/2026?",
            "Qual foi o lucro em 29/10/2026?",
            "Qual era o preço do Bitoque em 29/10/2026?",
            "Quais pratos venderam mais em 29/10/2026?",
            "Quais pratos foram mais rentáveis no mês passado?",
            "Quais pratos foram mais rentáveis do mês de setembro?",
            "Quais artigos tiveram a margem mais alta na semana passada?",
            "Qual era a margem do Bitoque no mês passado?",
            "Qual era o preço do Bitoque no mês passado?",
            "Quanto gastei em salários este mês?",
            "Quanto gastei em setembro?",
            "Quanto paguei em março?",
            "Qual foi o lucro em março?"
        )
        questions.forEach { question ->
            val answer = StudioMesaAdvisor.answerQuestion(question, data)
            assertTrue("$question -> ${answer.title}", answer.title.contains("Período") && answer.title.contains("não confirmado"))
            assertTrue(
                answer.response.contains("Na versão Premium") ||
                    answer.response.contains("Não vou apresentar totais acumulados")
            )
            assertFalse(answer.response.contains("seleciona o período pretendido"))
            assertFalse(answer.response.contains("Triagem indicativa"))
            assertFalse(answer.response.contains("20 unidades"))
        }
    }


    @Test fun dishDoseCostQuestionGuidesToRecipeCalculationInsteadOfInventingIngredients() {
        val answer = StudioMesaAdvisor.answerQuestion("Quanto custa uma dose de lasanha?", snapshot())
        assertEquals("Custo por receita e ingredientes", answer.title)
        assertTrue(answer.response.contains("quantidade por dose"))
        assertTrue(answer.response.contains("Não vou inventar ingredientes"))
    }

    @Test fun unknownStockItemWithExhaustedOrSufficientWordingIsNotConfusedWithGlobalStock() {
        val data = snapshot(
            stockAvailable = true,
            stockLoaded = true,
            stockItems = listOf(AssistantStockItem("Frango", BigDecimal("8"), BigDecimal("3")))
        )
        val exhausted = StudioMesaAdvisor.answerQuestion("O arroz acabou", data)
        assertEquals("Artigo de stock não encontrado", exhausted.title)
        assertTrue(exhausted.response.contains("arroz"))
        val sufficient = StudioMesaAdvisor.answerQuestion("Há arroz suficiente?", data)
        assertEquals("Artigo de stock não encontrado", sufficient.title)
        assertTrue(sufficient.response.contains("arroz"))
        assertFalse(sufficient.response.contains("Não encontrei artigos abaixo do limite mínimo"))
    }


    @Test fun lossQuestionDoesNotLabelPositiveContributionsAsLosses() {
        val data = snapshot(menu = listOf(
            item("Sopa", "5.00", "2.00"),
            item("Bitoque", "14.00", "4.50"),
            item("Salada", "8.00", "2.00")
        ))
        val answer = StudioMesaAdvisor.answerQuestion("Quais pratos estão a dar prejuízo?", data)
        assertEquals("Não há contribuição unitária negativa registada", answer.title)
        assertTrue(answer.response.contains("não encontrei artigos cujo custo interno unitário ultrapasse o preço"))
        assertTrue(answer.response.contains("Isto não prova que sejam lucrativos"))
        assertFalse(answer.response.contains("Sopa: contribuição"))
    }

    @Test fun compoundHistoricalProfitAndAdviceDoesNotReuseCurrentTotals() {
        val answer = StudioMesaAdvisor.answerQuestion(
            "Qual foi o lucro em março e como posso reduzir custos?",
            snapshot()
        )
        assertEquals("Período de desempenho não confirmado", answer.title)
        assertTrue(answer.response.contains("não incluem séries temporais fiáveis"))
        assertFalse(answer.response.contains("Estimativa de resultado mensal"))
    }

    @Test fun datedSalesAnalysisWithFollowUpAdviceDoesNotUseAllTimeSalesTotals() {
        val answer = StudioMesaAdvisor.answerQuestion(
            "Analisa as vendas em março e sugere como melhorar.",
            snapshot()
        )
        assertEquals("Período de vendas não confirmado", answer.title)
        assertTrue(answer.response.contains("Não vou apresentar totais acumulados"))
        assertEquals("Análise", answer.actionPage)
        assertFalse(answer.response.contains("Triagem indicativa"))
    }

}
