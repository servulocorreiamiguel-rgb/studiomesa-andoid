# StudioMesa — estado de continuação

**Data:** 1 de novembro de 2026  
**Branch de desenvolvimento:** `feat/order-menu-snapshots-2026-10-30`  
**Pull request:** [#72 — receitas, gestão económica e reservas de cliente](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/pull/72)

## Estado da branch

- A PR #72 continua aberta, em rascunho, e não foi integrada em `main`.
- A branch reúne as alterações de receitas/consumo de stock, identidade estável dos artigos e snapshots económicos dos pedidos, reservas iniciadas pela aplicação de cliente e melhorias de gestão.
- Não foram aplicadas migrações novas ao Supabase alojado nesta fase.
- Não foram usados dados reais de restaurantes.

## Trabalho feito nesta fase

1. Corrigida a expectativa do teste `scenarioSimulatorComparesBaselineWithExplicitPercentageChanges`: o ponto de equilíbrio esperado é **7 570,09 €**, não 7 570,59 €. No cenário: vendas de 9 000 €, custo alimentar de 3 150 €, custos variáveis de 500 € e custos tratados como fixos de 4 500 €. A taxa de contribuição é 5 350 / 9 000; o ponto de equilíbrio é 4 500 / (5 350 / 9 000).
2. O cabeçalho da aplicação de gestão passou a distinguir:
   - sincronização em curso;
   - última sincronização concluída, com hora;
   - falha ao atualizar, indicando que a aplicação está a usar os últimos dados recebidos;
   - falha antes de qualquer sincronização confirmada.
3. A atualização manual das mesas, carta e pedidos mostra o estado no próprio ecrã, em vez de depender exclusivamente de uma janela modal.
4. As mensagens de estado foram extraídas para `RemoteOperationsSyncStatus.kt` e foram acrescentados cinco testes unitários para os diferentes estados.

## Validação — não confundir com aprovação final

A execução CI #943, anterior a estas correções, confirmou:
- compilação das variantes Android: passou;
- auditorias estáticas e verificações de segurança incluídas antes dos testes: passaram;
- reprodução das migrações da branch numa base PostgreSQL isolada e testes de autorização/idempotência: passaram;
- testes unitários: falhou um teste do simulador económico por causa do valor esperado incorreto;
- Lint, compilação comercial de CI e testes de interface emuladores Android 8/16: foram ignorados por a tarefa de testes unitários ter falhado.

**Não foi executada uma nova CI depois das alterações desta fase.** A correção do teste e os novos testes de estado de sincronização ainda não estão validados por uma execução observável. Não afirmar que a branch está aprovada.

## Bloqueios que continuam a impedir a venda como produto pronto para operação real

- A branch candidata tem 47 migrações; o projeto Supabase alojado tinha 41 migrações aplicadas na última verificação documentada. As seis migrações novas não foram aplicadas em produção.
- Antes de qualquer migração live são obrigatórios backup manual cifrado, ensaio de restauro, plano de reversão, aplicação controlada e verificações posteriores.
- Faltam os testes finais ponta a ponta com contas descartáveis e dois dispositivos físicos, incluindo autenticação, reservas, pedidos, stock, concorrência, rede interrompida/restaurada e isolamento entre restaurantes.
- SMTP próprio, confirmação/recuperação por email e URLs de retorno de autenticação ainda precisam de configuração e validação.
- Faltam política de privacidade, declaração Google Play Data safety, retenção/exportação/eliminação de dados, cópias de segurança e procedimento de restauro validados.
- Falta a chave definitiva de assinatura, identificadores finais da aplicação e AAB/APK comercial assinado.
- Não anunciar integrações POS, pagamentos, impressoras ou faturação; não estão implementadas.

## Próximos passos

1. Continuar a revisão de funcionalidades e experiência, sem ampliar o âmbito com funcionalidades que não aumentem o valor para o primeiro restaurante.
2. Rever feedback de gravação/falha/repetição nos fluxos de pedidos, reservas, carta e stock.
3. Fechar os fluxos de autenticação e a experiência inicial do restaurante.
4. Quando o desenvolvimento estiver fechado, executar uma única ronda final completa de testes, corrigir todas as falhas e repetir os testes afetados.
5. Só depois rever a PR, planear com segurança as migrações e preparar uma demonstração comercial. A PR deve continuar em rascunho até essa validação.

**Objetivo comercial:** conseguir o primeiro restaurante interessado e pagante, sem prometer funcionalidades ou prontidão operacional que ainda não tenham sido demonstradas.

## Continuação — revisão de 1 de novembro de 2026

Depois do registo inicial deste estado, foram acrescentadas as seguintes alterações à mesma branch:

- `RemoteOperationsSyncStatus.kt`: mensagens distintas para sincronização em curso, sucesso anterior, falha com cache anterior e falha antes de existir uma sincronização confirmada.
- `RemoteOperationsSyncStatusTest.kt`: cinco testes unitários escritos para esses estados.
- O cabeçalho da gestão usa agora esse componente e a atualização manual das mesas/carta/pedidos apresenta o estado de sincronização no próprio ecrã.
- `CustomerReservationRefreshStatus.kt`: aviso no ecrã de reservas do cliente enquanto atualiza ou quando a última atualização falhou.
- `CustomerReservationRefreshStatusTest.kt`: três testes unitários escritos. Quando a rede falha, a lista de reservas já recebida continua visível e a interface explica que está a usar os últimos dados disponíveis.
- O teste do simulador económico foi corrigido para esperar **7 570,09 €** no ponto de equilíbrio do cenário documentado.

**Verificação de repositório:** os dois componentes auxiliares e os respetivos ficheiros de testes foram encontrados na branch; a chamada de interface para o estado das reservas também foi confirmada. A PR #72 permanece aberta e em rascunho.

**Importante:** estas verificações foram de leitura do código e dos ficheiros no GitHub. Não foi executada CI, compilação, Lint ou testes unitários depois das alterações. A bateria final continua pendente por decisão de desenvolvimento faseado.

### Próximo trabalho de produto

Continuar a revisão dos fluxos de pedidos e inventário, sobretudo:
- confirmar que o pedido novo usa sempre os dados autoritativos da carta;
- garantir que falhas de rede e repetições não criam pedidos ou movimentos de stock duplicados;
- tornar explícito quando uma ação foi guardada no servidor;
- manter os estados vazios, de carregamento e de erro claros sem apagar dados úteis em cache.

Não aplicar as migrações pendentes ao Supabase de produção nesta fase.

### Continuação — pedidos e repetição segura

- A criação de pedidos na aplicação de gestão usa uma referência persistente por conta e conteúdo do pedido. O backend verifica a tentativa repetida pelo mesmo identificador e pelos dados/snapshots guardados, para evitar criar um segundo pedido quando a primeira resposta HTTP se perde.
- A mensagem de erro da criação foi melhorada: explica que o servidor pode ter recebido o pedido antes de a ligação falhar e pede ao funcionário que mantenha os mesmos dados ao repetir. O formulário permanece aberto; a referência da tentativa não é marcada como concluída enquanto a operação e a atualização da cache não terminarem.
- Esta alteração é apenas uma melhoria de orientação e não substitui o teste de perda de rede depois do servidor confirmar a gravação.
- Não foi executada CI, compilação, Lint ou testes unitários após esta alteração.

### Continuação — custo estimado por dose nas fichas técnicas

- Criado `RestaurantRecipeCostCalculator.kt`: calcula o custo conhecido por dose a partir dos custos unitários atuais do inventário.
- O cálculo usa a quantidade de stock arredondada para cima a três casas decimais, a mesma regra que a função de consumo usa por dose, incluindo a percentagem de desperdício.
- A ficha técnica mostra agora o custo de cada ingrediente e o custo estimado da dose. Quando falta um custo de compra, mostra apenas o custo conhecido e declara que o total está incompleto; um ingrediente sem custo nunca é tratado como custo zero. Um custo explicitamente igual a zero continua a ser um custo conhecido.
- O ecrã esclarece que a estimativa não inclui mão de obra, energia, embalagem nem outros custos indiretos e que usa os custos unitários atuais, não uma valorização contabilística.
- Criado `RestaurantRecipeCostCalculatorTest.kt` com seis testes escritos para desperdício/arredondamento, custos em falta, custo zero válido, entradas duplicadas ou inválidas e métricas face ao preço de venda.

**Validação pendente:** os ficheiros foram revistos no GitHub após a gravação, mas não foi possível executar Gradle/Android Lint nem GitHub Actions nesta sessão. O workflow Android está configurado para correr em pushes para `main` ou por `workflow_dispatch`; não foi disponibilizada uma ação de disparo manual nesta ligação. A tentativa de clonar o repositório para uma execução local também falhou por indisponibilidade de ligação ao GitHub. Não declarar estes testes como aprovados até à execução efetiva.

**Nota de produto:** este é um custo estimado de ingredientes por dose, não o custo total de produção nem uma margem líquida. Não atualizar automaticamente o preço de venda.

- A ficha técnica também recebe o preço de venda atual do artigo da carta e apresenta o peso conhecido/completo dos ingredientes no preço. Quando todos os custos estão definidos, mostra o valor que resta depois dos ingredientes para pagar os restantes custos; identifica expressamente que esse valor não é lucro líquido. Com custos em falta, só mostra uma percentagem parcial e avisa que o valor real pode ser superior.
- O resumo económico mantém o preço atual do artigo ao voltar a carregar a ficha depois de adicionar/remover ingredientes ou aprovar/desativar a receita.
- O conjunto `RestaurantRecipeCostCalculatorTest.kt` passou a ter seis testes escritos, incluindo a percentagem do preço de venda, valor após ingredientes e rejeição de preço zero para o cálculo percentual.

A validação de compilação e execução dos seis testes continua pendente; os resultados acima descrevem o código e os testes adicionados, não testes executados.

**Estado do PR após esta etapa:** PR #72 continua aberta em rascunho. A branch contém 147 commits a mais e está 1 commit atrás de `main`; não foi feito rebase nem integração nesta etapa. A consulta ao GitHub não devolveu execuções de workflow nem status checks para o último commit. O código e os testes foram confirmados nos ficheiros da branch, mas a validação executável continua pendente.

### Continuação — comparação económica dos artigos da carta

- Criado `MenuCostComparisonAnalyzer.kt`: compara cada artigo ativo pelo custo unitário que o restaurante registou na carta relativamente ao preço de venda atual.
- O ranking apresenta primeiro os artigos com maior relação custo/preço e compara-os com o objetivo percentual que o proprietário definiu no gestor económico (30% é apenas o valor inicial editável).
- Para cada artigo apresenta preço, custo registado, percentagem e diferença após esse custo. Se o custo for superior ao preço, o caso fica visível como valor negativo; não é ocultado.
- Artigos sem custo, sem preço positivo ou com valores inválidos ficam fora do ranking e são contados separadamente. O custo zero explicitamente registado é aceite como valor conhecido.
- O painel apresenta os oito artigos com maior relação custo/preço, permite atualizar os dados do restaurante e tem um atalho para abrir a carta e rever os artigos.
- A interface esclarece que o custo usado é o valor atualmente registado na carta/cache, não uma valorização contabilística nem necessariamente a composição completa da ficha técnica. A diferença preço-custo não é apresentada como lucro líquido e não é alterado qualquer preço automaticamente.
- Criado `MenuCostComparisonAnalyzerTest.kt` com oito testes escritos: ordenação, comparação com o objetivo, igualdade exata com o objetivo, custo superior ao preço, custo zero válido, dados em falta, custos inválidos e limites/nomes de entrada.

**Validação pendente:** os oito testes foram escritos e os ficheiros foram confirmados no GitHub, mas ainda não foram executados. Também não foi feita compilação Android nem Lint nesta etapa.

**Estado da branch após a comparação:** a PR #72 continua aberta e em rascunho. Na última comparação, a branch estava 155 commits à frente e 1 atrás de `main`. Não foi feito rebase, merge ou alteração da base de dados de produção. A consulta ao GitHub para o último commit não devolveu execuções de workflow nem status checks; não declarar a compilação ou os testes como aprovados.
