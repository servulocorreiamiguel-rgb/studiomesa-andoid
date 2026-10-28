# Pesquisa de funcionalidades em falta no assistente StudioMesa — 28/10/2026

## Resumo executivo
Os sistemas de restauração que anunciam assistência por IA centram-se em vendas/receita, menu e inventário, marketing, relatórios e briefings operacionais. Também apontam para engenharia de menu, previsão da procura, desperdício, receitas/custos e planeamento de equipa. Estas áreas precisam de dados operacionais ligados e fiáveis.

## Prioridades implementadas nesta revisão
1. **Resumo da operação:** carta, pedidos ativos, custos em falta e, quando carregados, stock e reservas.
2. **Engenharia da carta:** classificação Estrela, Popular com margem baixa, Margem alta com pouca procura e Rever artigo, usando pedidos concluídos e custos/preços atuais.
3. **Rascunhos de marketing:** texto baseado no nome, descrição e preço registados, com botão para copiar; não inventa ingredientes ou promoções.

## Evidência e fontes
- A Toast publicou uma análise de uso de Toast IQ no Q1 de 2026, em mais de 125 000 localizações dos EUA: 47% perguntaram sobre vendas/receita, 34% sobre menu/inventário, 32% sobre hóspedes/marketing e 26% sobre otimização do menu. É dado do próprio fornecedor e dos seus clientes nos EUA, não uma estimativa automática para Portugal. https://pos.toasttab.com/news/toast-iq-restaurant-trends-report-ai-assistant
- A Lightspeed destaca identificar artigos populares e rentáveis, encontrar artigos com fraco desempenho, análises e criação de menus. https://www.lightspeedhq.com/POS/AI/
- Toast IQ Inventory documenta ligação entre vendas e stock, receitas/ingredientes, reconciliação física, pedidos de compra e registo de desperdício, exigindo dados de inventário/receitas. https://support.toasttab.com/en/article/Toast-IQ-Inventory-Overview
- A National Restaurant Association identifica previsão de procura, inventário, preparação e análise de feedback como aplicações possíveis, dependentes de histórico e processos operacionais. https://restaurant.org/education-and-resources/resource-library/ai-on-the-menu-using-ai-for-restaurant-operations/
- O relatório global da EHL (dezembro de 2025) cita previsão de procura, otimização de horários, engenharia de menu e automatização de back-office; alerta para problemas quando ferramentas não partilham dados. https://research.ehl.edu/hubfs/EHL-RESEARCH/Serving-the-Future-the-2025-Global-Food-Service-Outlook.pdf

## Limitações
O assistente continua local e determinístico, não um LLM generativo. A engenharia usa cinco unidades concluídas como limite técnico de triagem, não suficiente para decisões definitivas. Usa preço/custo atuais, não valores históricos à data da venda. Alterar nomes pode quebrar correspondências com vendas antigas. A aplicação ainda não recolhe registo de desperdício, receitas ligadas a ingredientes, faturas de fornecedores, avaliações, horários de equipa nem histórico temporal validado para previsão. Previsão de vendas, meteorologia, escalas automáticas, análise de sentimento, reconhecimento de imagens e compras automáticas não foram simulados nem apresentados como disponíveis.

## Próximo caminho técnico
Estabilizar a ligação entre itens do pedido e artigos da carta por identificador estável e guardar snapshots de preço/custo à data da venda. Depois, acrescentar receitas e movimentos de desperdício com autorização e auditoria; só então será defensável criar previsão de procura e recomendações de compra.
