# Análise semanal e previsão de movimento — StudioMesa

## O que é analisado

A análise usa os pedidos já carregados na StudioMesa. Considera apenas pedidos com estado `Concluído`, data de criação anterior ao dia atual e valor/quantidade válidos. Agrupa os registos pela data local do dispositivo e apresenta, para cada dia da semana, o total acumulado, a média por dia observado e o número de linhas de pedidos concluídos.

A média semanal é calculada apenas em dias com registos concluídos. Um dia sem linhas não é considerado automaticamente um dia com vendas zero, porque a aplicação não dispõe de um calendário fiável de abertura e encerramento.

## Previsão

O motor local:
- usa até 12 observações mais recentes do mesmo dia da semana;
- quando existem pelo menos 8 observações, compara as quatro mais recentes com as quatro anteriores e limita o ajuste de tendência a ±15%;
- classifica a confiança como baixa (1–3 amostras), média (4–7) ou alta (8 ou mais);
- devolve ausência de previsão quando não há amostras comparáveis;
- pode calcular até 30 dias, embora o painel da aplicação apresente os próximos 7 dias.

A previsão é uma estimativa do valor nominal dos pedidos concluídos com base na data de criação e no estado atual dos registos. Não é faturação recebida, receita contabilística, previsão de pagamentos nem garantia de procura.

## Afluência

O indicador de afluência usa o número de linhas de pedidos concluídos por dia observado. Um dia só é assinalado como padrão de maior afluência quando tem pelo menos quatro observações e a média de linhas concluídas é pelo menos 20% superior à média geral dos dias observados.

Este indicador não conta clientes ou pessoas. Uma linha pode incluir várias unidades e não existe atualmente integração com contadores de entradas, reservas concretizadas ou caixa.

## Feriados e eventos

O código reconhece os feriados obrigatórios do artigo 234.º do Código do Trabalho português, incluindo Sexta-Feira Santa, Domingo de Páscoa e Corpo de Deus. A terça-feira de Carnaval é apresentada como facultativa. A base legal está no Diário da República: https://diariodarepublica.pt/dr/legislacao-consolidada/lei/2009-34546475-66966857-

O feriado municipal e eventos locais — por exemplo, festas, concertos, jogos ou eventos turísticos — podem ser configurados no ecrã Análise no formato `AAAA-MM-DD | nome`. Essa configuração fica guardada localmente neste dispositivo e por restaurante; não é sincronizada automaticamente com outros dispositivos.

O efeito de um feriado específico só é tratado como medido quando existem pelo menos duas ocorrências históricas comparáveis. Caso contrário, é usado o padrão do dia da semana e a resposta explica que não há amostra suficiente para medir o efeito do feriado.

## Limites

A funcionalidade não consulta calendários municipais na Internet, meteorologia, eventos desportivos, férias escolares, ocupação hoteleira, horários de abertura, pagamentos, POS externo ou contabilidade. Também não interpreta um dia sem pedidos como encerramento ou procura nula. Estes dados só podem melhorar a previsão quando forem registados e validados pelo restaurante.

A ferramenta é local e só de leitura. Não altera pedidos, preços, stock, reservas ou faturas.
