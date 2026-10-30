# Gestor económico opcional do StudioMesa — 28/10/2026

## Objetivo
Dar ao proprietário uma área opcional de apoio à decisão, com cálculos determinísticos, sem fazer passar estimativas por contabilidade oficial ou por IA generativa. Os cálculos usam apenas os valores introduzidos manualmente nesta sessão.

## Funções acrescentadas
- Indicadores mensais: custo alimentar / vendas, pessoal / vendas, custo alimentar + pessoal, resultado operacional estimado e ponto de equilíbrio.
- Alertas: custo alimentar acima do objetivo definido pelo proprietário, peso elevado do custo alimentar + pessoal (um sinal de investigação, não um limite universal) e resultado estimado negativo.
- Tesouraria manual a 30 dias: saldo atual + recebimentos previstos − pagamentos a vencer. Não existe integração bancária.
- Indicador de desperdício em euros e como percentagem das vendas/custo alimentar. Não é subtraído novamente ao resultado quando o custo alimentar já inclui o desperdício.
- Estimativa de vendas da semana seguinte através da média simples das quatro últimas semanas comparáveis. Não modela sazonalidade, feriados, meteorologia, eventos ou tendências; exige pelo menos quatro valores.
- Custo da receita por dose: linhas de ingrediente, quantidade por dose, custo por unidade de compra e percentagem de desperdício; calcula custo e preço indicativo para o objetivo de custo alimentar escolhido.
- Quantidade de reposição: stock atual, consumo médio diário, prazo do fornecedor e dias de segurança. Apenas recomenda quantidade; não emite encomendas nem altera stock.
- A IA local lê os custos unitários atuais dos artigos de inventário, estima o valor das existências com custo conhecido, identifica artigos sem custo e calcula o custo indicativo para repor até ao limite mínimo configurado. Não assume lead time, quantidade de embalagem, portes ou desconto; não emite encomendas.
- Comparação manual de preços entre fornecedores, normalizada por unidade de compra, apenas quando artigo e unidade coincidem.
- Comparação orçamento-versus-realizado por categoria, mostrando a diferença absoluta e percentual sem presumir que uma variação é favorável.
- Consumo teórico-versus-real por ingrediente, com estimativa do impacto em euros; requer unidades e período comparáveis.
- Cenário de custo de horas de equipa face às vendas e a um objetivo de custo de pessoal. Não cria horários nem recomenda cortes de pessoal.
- A secção é voluntária, começa desativada e pode ser limpa/desativada pelo utilizador.

## Fórmulas e hipóteses
- Custo alimentar (%) = custo alimentar consumido / vendas do período × 100.
- Custo de pessoal (%) = custo de pessoal / vendas do período × 100.
- Custo principal (%) = (custo alimentar + pessoal) / vendas × 100.
- Resultado operacional estimado = vendas − custo alimentar − pessoal − outros custos fixos − outros custos variáveis.
- Ponto de equilíbrio: custos tratados como fixos (pessoal + outros fixos) divididos pela margem de contribuição aproximada depois do custo alimentar e outros custos variáveis proporcionais às vendas. É uma simplificação: parte do pessoal pode variar com a atividade.
- Custo de receita: quantidade usada × preço unitário de compra ajustado pelo desperdício; quantidade e preço unitário têm de usar unidades compatíveis.
- Reposição: máximo de zero e (consumo médio diário × (prazo do fornecedor + dias de segurança) − stock atual).

## Privacidade e segurança
Os campos do gestor económico e os relatórios não são persistidos em SharedPreferences nem enviados para o backend; existem apenas em memória durante a sessão da Activity. Reiniciar ou fechar a aplicação perde estes dados. Antes de tornar os valores permanentes ou sincronizados, é necessário desenhar uma política de acesso do proprietário, armazenamento seguro, auditoria e testes de autorização entre restaurantes/dispositivos.

## Limites atuais
- Os dados são introduzidos manualmente; não são importados da caixa, banco, fornecedores ou contabilidade.
- As vendas e custos devem pertencer ao mesmo período e as vendas devem ser introduzidas sem IVA para que os rácios tenham interpretação consistente.
- O resultado não inclui automaticamente impostos, amortizações, juros, levantamentos do proprietário, dívidas, ativos ou lançamentos contabilísticos.
- A referência de custo alimentar é configurável pelo proprietário; não existe uma percentagem universal adequada a todos os conceitos de restauração.
- A estimativa de procura é uma média simples; não é um modelo preditivo treinado.
- Não são executadas ações automáticas: sem alteração de preços, movimentos de stock, encomendas, pagamentos ou horários.
- O inventário já permite associar um custo de compra unitário manual a cada artigo e guardar esse custo como fotografia em novos movimentos. O histórico de desperdício calcula perdas com custo conhecido por artigo e data. Movimentos anteriores à migração sem fotografia de custo permanecem desconhecidos; não são reconstituídos com o preço atual. A valorização atual do stock usa o custo unitário atual e não é um método FIFO ou custo médio.
- O custo por receita usa quantidades e custos fornecidos manualmente; ainda não está ligado aos ingredientes do stock nem a movimentos automáticos por venda.

## Trabalho seguinte recomendado
1. Criar tabelas privadas de receitas e linhas de receita ligadas aos artigos de inventário, com identificação do restaurante e políticas RLS testadas.
2. Ligar vendas a preço/custo histórico por identificador estável, evitando reconstruir resultados antigos com preços atuais.
3. Registar faturas e alterações de preços de fornecedores com validação humana.
4. Adicionar histórico datado de vendas e calendário para previsão por dia da semana/sazonalidade e comparação entre previsão e realizado.
5. Criar livro de caixa manual auditável e comparação real entre valores previstos e executados; depois avaliar integrações externas seguras. Não afirmar que existe acesso bancário até existir integração.
6. Ligar os artigos de stock às receitas e às vendas para calcular consumo teórico e movimentos automáticos após confirmação do pedido; acrescentar receção de compras/faturas, histórico de preços por fornecedor e controlos de validade. O registo persistente de desperdício por artigo, motivo e data já existe, mas ainda falta ligá-lo ao consumo teórico/real e aos custos por período.
7. Construir previsão com vendas datadas por dia/turno, sazonalidade, feriados/eventos e comparação forecast-versus-real; a média simples de quatro semanas é apenas uma referência preliminar.
8. Integrar horas trabalhadas reais, escalas e custo de pessoal por turno apenas após desenho de permissões e testes laborais/operacionais.
9. Validar o módulo com restaurantes reais e contabilista certificado antes de o usar como aconselhamento fiscal/contabilístico.

## Pesquisa de referência
- National Restaurant Association, “AI on the menu: Using AI for restaurant operations” (inventário, produção, previsão e análise de feedback): https://restaurant.org/education-and-resources/resource-library/ai-on-the-menu-using-ai-for-restaurant-operations/
- National Restaurant Association, “Restaurateurs use tech to manage inventory, save money” (receitas, desperdício, variâncias, reposição, custos teóricos/atuais): https://www.restaurant.org/education-and-resources/resource-library/restaurateurs-use-tech-to-manage-inventory%2C-save-money/
- National Restaurant Association, “Working to reduce food waste” (ligar registo de desperdício, planeamento de produção e previsão): https://www.restaurant.org/education-and-resources/resource-library/working-to-reduce-food-waste/
- Toast, “How to calculate food cost percentage” (custo alimentar, COGS e margem de contribuição): https://pos.toasttab.com/blog/on-the-line/how-to-calculate-food-cost-percentage
- Supabase, “Securing your API” (grants e Row Level Security): https://supabase.com/docs/guides/api/securing-your-api
