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

### Continuação agrupada — integração das fichas técnicas e fiabilidade do comparador

- A leitura económica passa a obter a carta ativa, as fichas técnicas, as linhas de ingredientes e o inventário do mesmo restaurante. A carta e as fichas técnicas são paginadas até 500 registos; os ingredientes até 3000; o inventário até 2000. Se algum limite for excedido, a análise falha explicitamente em vez de comparar um conjunto truncado.
- As consultas usam ordenação estável com identificadores como desempate. As consultas de inventário e ingredientes usam paginação por blocos de 500.
- Cada artigo da carta é associado à ficha pelo identificador estável do artigo, nunca pelo nome. Os custos unitários atuais do inventário são multiplicados pelas quantidades por dose e desperdício da receita, seguindo o arredondamento de consumo já usado no projeto.
- Só uma ficha ativa, aprovada, completa e com todos os ingredientes presentes e unidades iguais às guardadas na ficha pode substituir o custo manual da carta. Uma ficha em revisão ou inativa mantém o custo manual; uma ficha ativa mas incompleta ou com unidade incompatível fica fora do ranking em vez de subestimar o custo.
- O comparador distingue fichas que exigem revisão de fichas simplesmente inativas. Cada linha do ranking identifica a fonte do custo e, na versão Premium ligada a um restaurante, permite abrir diretamente a ficha técnica do prato.
- A análise guarda a versão do último conjunto de dados operacionais recebido. Alterações locais à carta, inventário, movimentos de stock ou receitas avançam essa versão. Uma comparação anterior é então identificada como desatualizada e não é usada como se fosse atual; mantém-se visível o aviso até o proprietário carregar uma nova análise. Alterações feitas noutro dispositivo só são refletidas após atualização manual.
- A análise de carta, receitas e inventário exige perfil de proprietário e conta/restaurante correspondentes. Não altera preços, stock, fichas ou encomendas.
- Criado `MenuRecipeCostSnapshotVersion.kt` com três testes escritos para versão igual, versão alterada e versão em falta.
- `MenuCostComparisonAnalyzerTest.kt` passou a ter 12 testes escritos, incluindo prioridade da ficha aprovada, manutenção do custo manual quando a ficha está inativa ou precisa de revisão, rejeição de fichas incompletas e distinção entre estados de receita.
- Melhorias de inventário/receitas e paginação foram revistas nos ficheiros do GitHub. Não foi executada CI, Gradle, Lint nem a bateria de testes nesta etapa; os testes estão escritos, não aprovados.

**Limites desta integração:** ainda não há ligação automática a faturas de fornecedores ou custo histórico por lote. A análise usa o custo unitário atual registado no inventário; se esse valor não estiver atualizado, a estimativa também não estará.

### Fecho da ronda — revisão dos estados e experiência do proprietário

- A lista de problemas já não mistura fichas inativas por escolha do proprietário com fichas que precisam de revisão. O relatório distingue: ficha aprovada; ficha inativa; ficha bloqueada para revisão; ficha aprovada mas incompleta.
- As fichas que exigem revisão ou têm custos/unidades incompletos aparecem numa lista de pratos identificados. No Premium remoto, o proprietário pode abrir a ficha técnica diretamente a partir dessa lista ou do ranking.
- A comparação da carta foi colocada depois dos indicadores mensais, para que o objetivo percentual que o proprietário introduz esteja disponível antes de calcular o ranking. Foi adicionada à navegação rápida do gestor económico.
- Uma atualização falhada depois de uma alteração é mostrada como falha com dados desatualizados; o ranking volta a usar apenas o custo manual da carta até carregar uma nova análise. A análise anterior não é apresentada como atual.
- Respostas de uma atualização iniciada numa conta/restaurante anterior deixam de alterar o estado de uma sessão posterior.
- O cálculo da ficha técnica rejeita custos por ingrediente ou totais de receita acima de **999 999 999,99 €**; foram acrescentados dois casos de teste para estes limites. `RestaurantRecipeCostCalculatorTest.kt` tem agora sete testes escritos.
- `MenuCostComparisonAnalyzerTest.kt` tem 12 testes escritos e `MenuRecipeCostSnapshotVersionTest.kt` tem três testes escritos. Nenhum destes testes foi executado nesta sessão.

**Validação final desta ronda:** os ficheiros, chamadas de interface e contagens de testes foram relidos na branch. Continua sem existir resultado de CI para o commit mais recente; não foi executado Gradle, Android Lint, compilação APK/AAB ou teste em dispositivo. O comparador usa custos unitários atuais do inventário e dados carregados manualmente, sem histórico por lote ou leitura de faturas.


### Continuação — proteção contra cache operacional desatualizada

- O assistente passa a verificar a idade da última sincronização real da carta, mesas e pedidos. O limite de referência é cinco minutos; uma hora ausente, futura ou demasiado antiga é considerada não recente.
- Quando os dados operacionais não estão confirmados como recentes, o assistente mostra um alerta nas recomendações, acrescenta um aviso apenas às respostas que dependem da carta e dos pedidos e identifica a limitação no resumo diário. As perguntas de stock mantêm o controlo de atualidade próprio. O atalho encaminha para **Início**, onde existe a navegação da operação.
- Foi separado o carimbo de hora da sincronização real (\`remote_operations_synced_at\`) da versão usada para invalidar análises (\`remote_operations_updated_at\`). Esta distinção é necessária porque a invalidação de uma análise económica pode avançar a versão sem voltar a descarregar a carta e os pedidos.
- A sincronização geral grava os dois carimbos; a mudança/perda de associação ao restaurante limpa ambos. O assistente usa exclusivamente o carimbo de sincronização real.
- Foram acrescentados três testes unitários escritos: validade temporal do carimbo, aviso antes de responder com cache antiga e identificação da cache antiga no resumo diário.

**Validação desta continuação:** os ficheiros foram escritos e relidos no GitHub. Os três novos testes e os testes existentes continuam por executar; não houve Gradle, Lint, CI, build APK/AAB nem teste em dispositivo. A PR #72 continua em rascunho e não houve alterações ao Supabase alojado.


### Continuação — paginação completa da agenda de reservas

- A lista do restaurante deixava de consultar após 200 reservas e a lista do cliente após 100. Isso podia ocultar reservas mais recentes quando existia histórico suficiente para preencher a resposta inicial.
- Ambas as consultas usam agora `OrderHistoryPagination`, com páginas de 500 e ordenação determinística por `reservation_at` e `id`. O carregamento continua até à primeira página incompleta e faz uma verificação adicional quando chega exatamente ao limite.
- O limite atual é de 10 000 reservas por lista. Se existirem mais registos, a operação falha explicitamente e não substitui a cache por uma lista parcial. A mensagem identifica corretamente o histórico como reservas, não pedidos.
- A paginação mantém os filtros e contagens atuais da agenda, porque continua a devolver o histórico completo suportado antes de a interface calcular os grupos.
- Foram escritos dois testes adicionais para a mensagem de excesso de reservas e validação do rótulo do histórico. Os testes anteriores de paginação já cobrem páginas múltiplas, página final curta, limite exato, excesso e limites inválidos.

**Validação desta continuação:** as alterações foram gravadas e relidas no GitHub. Os testes de paginação estão escritos, mas não foram executados nesta sessão. Não houve CI, compilação Android, Lint, teste em dispositivos nem alterações ao Supabase alojado.


### Continuação — isolamento das respostas de atualização de reservas

- A agenda da equipa deixou de alterar a cache dentro da tarefa de rede. A resposta é convertida e aplicada à interface apenas no callback de sucesso, na thread principal.
- Cada atualização captura o identificador do restaurante e da conta. Antes de iniciar a consulta e novamente antes de aplicar a resposta, confirma-se que ambos continuam a corresponder à sessão atual.
- Na aplicação de cliente, a mesma proteção confirma o identificador do cartão de fidelização e a conta autenticada. Erros ou respostas de uma sessão anterior não alteram o estado de carregamento nem substituem a lista da nova conta.
- Ao mudar de restaurante, a cache de reservas da equipa, o estado de carregamento e o filtro selecionado são reiniciados. A perda da associação ao restaurante também limpa esse estado.
- Criado `ReservationRefreshScope.kt` e `ReservationRefreshScopeTest.kt`, com dois testes que cobrem conta/restaurante diferentes, cartões diferentes e identificadores ausentes.

**Validação desta continuação:** os ficheiros foram gravados e relidos no GitHub. Os dois testes de isolamento e os dois testes de paginação acrescentados nesta fase ainda não foram executados. Não houve CI, Gradle, Lint, compilação APK/AAB, teste físico ou alteração ao Supabase alojado.


### Continuação — estado de sincronização e concorrência na agenda

- A agenda da equipa mostra a hora da última lista integral confirmada. A própria interface explica que a atualização é manual e não uma subscrição em tempo real; para ver alterações de outros dispositivos é necessário atualizar.
- Se a atualização falhar e já existir uma lista confirmada, a agenda conserva-a e identifica-a como a última lista disponível. Se nunca carregou dados, mostra um estado de erro recuperável e não repete automaticamente o pedido em ciclo.
- Pedidos simultâneos de atualização para a mesma conta/restaurante são ignorados. Cada pedido recebe também uma geração; uma resposta atrasada de uma geração anterior não pode limpar o bloqueio nem substituir a lista de uma atualização posterior da mesma sessão.
- Criar uma reserva, atribuir mesa e alterar o estado passam a devolver a lista atualizada à thread da interface, em vez de alterarem a cache dentro da tarefa de rede. A cache e a hora da última consulta só mudam depois de a resposta ser confirmada e de a conta/restaurante continuarem iguais.
- As falhas e respostas tardias das operações também são filtradas pelo âmbito da conta e do restaurante. Os dados da sessão atual não são substituídos por resultados de uma sessão anterior.

**Validação desta continuação:** as alterações foram gravadas e relidas no GitHub. Os testes de paginação e isolamento continuam escritos, não executados. Não houve CI, Gradle, Lint, compilação APK/AAB, teste físico nem alteração ao Supabase alojado.


- A saída de sessão e a eliminação da conta invalidam a geração de atualização de reservas em curso, limpam a hora da última consulta e repõem filtros/estados das duas variantes. Assim, voltar a iniciar sessão com a mesma conta não fica bloqueado por uma consulta antiga que ainda estivesse pendente.
- `ReservationRefreshScope.ownsRefreshRequest` centraliza a validação da geração e tem um teste adicional para garantir que uma resposta antiga não é dona do bloqueio depois de começar outra atualização na mesma conta/restaurante.


### Continuação — proteção da cache geral de operações

- `cacheRemoteOperations` passa a guardar a conta que iniciou as consultas e só grava mesas, carta, pedidos e carimbos de sincronização se o token atual, a conta e o restaurante continuarem a corresponder à sessão inicial.
- A sincronização manual tem uma geração própria. Só a geração que iniciou a operação pode alterar o estado de sucesso/erro ou a fila da interface; respostas antigas são ignoradas.
- A mudança de restaurante, a saída de sessão e a eliminação da conta invalidam a geração e libertam o estado de sincronização. Uma atualização antiga não pode bloquear a atualização depois de voltar a iniciar sessão.
- As chamadas de atualização feitas depois de criar/alterar mesas, pedidos ou artigos também beneficiam da validação final antes de substituir a cache partilhada.

**Validação desta continuação:** revisão por leitura dos ficheiros e verificações de consistência no GitHub. Os testes continuam escritos e não executados; sem CI, compilação, Lint ou testes físicos. Não foi alterada a base de dados Supabase alojada.
