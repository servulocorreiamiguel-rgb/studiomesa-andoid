# Plano de testes — pedidos, quantidades, fases e notas

**Versão-alvo:** StudioMesa 1.5.0 (APK de demonstração/debug)  
**Funcionalidade:** quantidade por artigo, fase de serviço e nota de cozinha por pedido.  
**Estado:** plano preparado; os testes de execução ainda não foram feitos num telemóvel real.

## Ambiente e pré-condições

- Instalar a mesma variante de demonstração em dois dispositivos Android compatíveis.
- Usar uma conta de teste de proprietário e outra de funcionário do mesmo restaurante; criar também uma conta de outro restaurante e uma conta de cliente.
- Confirmar que só existem dados de teste. Não usar nomes, contactos ou pedidos de clientes reais.
- Guardar o commit, a variante instalada e os resultados de cada execução.
- O workflow compila as cinco variantes debug, executa os testes unitários das cinco variantes de distribuição (parser de autenticação e validação da precisão decimal de stock) e Android Lint. Estes controlos não substituem os testes funcionais abaixo.

## Casos de teste

| ID | Categoria | Ação / dados | Resultado esperado |
|---|---|---|---|
| ORD-01 | Normal | Criar um pedido com quantidade 1, fase Principal e nota vazia | Pedido guardado e mostrado uma única vez |
| ORD-02 | Normal | Criar um pedido com quantidade 2 e nota curta | Quantidade, nota e total da linha corretos |
| ORD-03 | Normal | Usar cada fase permitida: Bebidas, Aperitivo, Entrada, Principal e Sobremesa | Fase preservada após fechar e reabrir a aplicação |
| ORD-04 | Normal | Alterar o estado do pedido até Concluído | Novo estado refletido no ecrã e no histórico |
| ORD-05 | Normal | Consultar histórico de um pedido | Eventos e horários correspondem às alterações efetuadas |
| ORD-06 | Limite | Quantidade 1 | Aceite |
| ORD-07 | Limite | Quantidade 99 | Aceite |
| ORD-08 | Inválido | Quantidade 0, negativa ou 100 | Rejeitada pelo formulário/servidor; nenhum pedido parcial |
| ORD-09 | Inválido | Fase de serviço não reconhecida | Rejeitada; não guardar um valor alternativo silenciosamente |
| ORD-10 | Limite | Nota com 500 caracteres | Aceite e preservada integralmente |
| ORD-11 | Inválido | Nota com 501 caracteres | Rejeitada com mensagem clara |
| ORD-12 | Inválido | Artigo em branco, mesa inválida ou valor monetário negativo | Rejeitado sem alterar os dados existentes |
| ORD-13 | Permissões | Utilizador sem sessão tenta criar ou alterar pedidos remotos | Acesso recusado; os dados mantêm-se inalterados |
| ORD-14 | Permissões | Funcionário autorizado cria e atualiza um pedido | Operação aceite dentro do restaurante ao qual pertence |
| ORD-15 | Permissões | Conta de outro restaurante tenta consultar ou modificar o pedido | Nenhum dado do primeiro restaurante devolvido ou alterado |
| ORD-16 | Permissões | Cliente abre a aplicação de cliente e consulta a carta | Só vê campos publicados; nunca recebe custos internos |
| ORD-17 | Rede | Desligar a internet antes de guardar | Erro claro; a interface não declara sucesso |
| ORD-18 | Rede | Cortar a ligação durante o envio e voltar a tentar | Verificar se foi criado zero ou um pedido; sem duplicados silenciosos |
| ORD-19 | Rede | Expirar a sessão antes de atualizar o pedido | Renovação controlada ou pedido de novo login; sem perda silenciosa |
| ORD-20 | Sincronização | Dois dispositivos atualizam mesas/pedidos quase ao mesmo tempo | Estado final coerente e sem sobrescritas inesperadas |
| ORD-21 | Regressão | Abrir um pedido antigo criado antes dos novos campos | Valores predefinidos coerentes; a aplicação não falha |
| ORD-22 | Regressão | Reiniciar a aplicação e consultar o pedido | Dados persistem conforme a origem local/remota |

## Casos de regressão da calculadora de preços

Os casos PC-01 a PC-04 são cobertos por `PriceCalculatorTest`; os respetivos resultados só devem ser considerados aprovados depois de a execução CI do commit candidato passar.

| PC-01 | Calculadora de preço | Custo 3,20 € e custo pretendido 30% | Preço 10,67 €, lucro bruto estimado 7,47 €, custo/preço 30,0% e margem bruta 70,0% |
| PC-02 | Calculadora de preço | Custo 1,00 € e custo pretendido 30% | Preço arredondado a 3,33 € antes do cálculo de lucro; lucro bruto 2,33 € |
| PC-03 | Calculadora de preço | Custo 12,99 € e custo pretendido 100% | Preço 12,99 €, lucro bruto 0,00 € e margem 0,0% |
| PC-04 | Validação da calculadora | Custo zero, custo com mais de duas casas decimais, percentagem <= 0 ou > 100, texto inválido/NaN/infinito | Entrada rejeitada com mensagem no campo correto; sem crash nem resultado não numérico |

## Eliminação de conta e proteção de dados

Os casos ACCT-01 a ACCT-06 são **pendentes de teste ponta a ponta com contas descartáveis**. Nunca os executar com uma conta real que contenha dados necessários.

| ID | Categoria | Ação / dados | Resultado esperado |
|---|---|---|---|
| ACCT-01 | Autorização / sem sessão | Enviar pedido de eliminação sem token de sessão válido | Endpoint rejeita o pedido; nenhuma conta ou dado é alterado |
| ACCT-02 | Conta pessoal | Eliminar uma conta de teste que não seja proprietária | A conta deixa de autenticar; associação de fidelização é removida; as referências de autoria dos registos operacionais preservados ficam nulas |
| ACCT-03 | Proprietário / segurança | Pedir eliminação do proprietário sem confirmação explícita dos dados do restaurante | Pedido recusado; restaurante e todos os dados permanecem |
| ACCT-04 | Proprietário / equipa | Pedir eliminação do proprietário quando ainda há membros adicionais | Pedido recusado; nenhum dado do restaurante é apagado |
| ACCT-05 | Proprietário / confirmação | Remover os restantes membros da conta de teste, confirmar duas vezes e eliminar proprietário | Conta, restaurante e dados operacionais associados são eliminados; não sobra fila de eliminação pendente |
| ACCT-06 | Erro / expiração | Forçar falha antes de o Auth Admin apagar o utilizador ou deixar a fila ultrapassar 15 minutos | A fila é cancelada ou expira e não elimina o restaurante por si só |
| ACCT-07 | Eliminação web / email | Pedir ligação na página externa, abrir a ligação no mesmo navegador, escolher eliminação de conta e confirmar | O utilizador autenticado consegue enviar um pedido; email inválido/sem conta não revela se a conta existe; a conta é eliminada apenas após confirmação e verificação do servidor |
| ACCT-08 | Redirecionamento web | Tentar magic link antes e depois de permitir o URL da página em Supabase Auth Redirect URLs | Antes da configuração, a ligação falha de forma segura; depois de autorizado, regressa à página externa e estabelece uma sessão válida |
| ACCT-09 | Concorrência / proprietário | Entre a confirmação inicial e a eliminação autenticada, associar um segundo membro ao restaurante de teste | O gatilho volta a verificar os membros e reverte a eliminação do Auth; não elimina o restaurante nem a nova equipa |
| ACCT-10 | Segurança / enumeração de emails | Com duas contas descartáveis — uma existente e outra inexistente — comparar a resposta HTTP e o tempo do endpoint Auth chamado por `signInWithOtp` com `shouldCreateUser=false` | A página apresenta a mesma mensagem genérica em sucesso/erro, mas o teste deve também verificar a API diretamente. Não considerar a proteção suficiente se status/corpo/tempo revelarem a existência; este comportamento foi reportado na [issue pública Supabase Auth #1547](https://github.com/supabase/auth/issues/1547). Bloqueio aberto até validar mitigação suportada. |

## Execução automatizada disponível

O workflow `.github/workflows/android.yml` compila as cinco variantes debug, executa os testes unitários das cinco variantes de distribuição, Android Lint, a configuração de release com uma chave descartável de CI, a reprodução de migrações/testes de autorização PostgreSQL e testes instrumentados de interface em Android 8 (API 26) e Android 16 (API 36). A suite inclui testes de `PriceCalculator`, `RestaurantEconomicManager`, `AuthCallbackParser` e `StockDecimalValidator`, além de regressões de interface para a calculadora, formulário de login do cliente, gestor económico e callback PKCE. Estes testes não validam credenciais reais, OAuth/SMTP alojado, recuperação de conta por email, rede intermitente real, restauração de backups ou todos os fluxos de negócio ponta a ponta. Confirmar a execução CI do commit exato candidato à distribuição antes de considerar a versão validada.

Comando de compilação usado pelo workflow:

```bash
gradle --no-daemon assembleEssencialRestauranteDebug assembleProfissionalRestauranteDebug assembleProfissionalClienteDebug assemblePremiumRestauranteDebug assemblePremiumClienteDebug
```

## Registo de resultados desta revisão

| Verificação | Resultado real |
|---|---|
| Revisão das restrições SQL de quantidade/fase/nota | Inspecionada na estrutura da base de dados; não equivale a teste funcional |
| Testes manuais em Android | Não executados nesta sessão |
| Testes unitários de lógica | Existem testes para calculadora de preços, gestor económico, parser PKCE e precisão de stock, executados nas cinco variantes. As alterações recentes de precisão económica no PR #58 ainda aguardam CI completo. |
| Testes instrumentados/funcionais em Android | O workflow executa smoke tests de interface nos emuladores Android 8 e Android 16 para as cinco variantes; inclui formulário de login, calculadora, gestor económico e callback de autenticação. A execução integrada do commit atual tem de terminar com sucesso antes de distribuir. Não valida credenciais reais, OAuth/SMTP por email, rede real ou operações completas ponta a ponta. |
| Compilação CI, testes unitários do parser e Android Lint | Verificar sempre a execução GitHub Actions do commit candidato a distribuição; uma execução aprovada não substitui testes de negócio em Android. |
| Testes de rede e concorrência | Não executados |
| Teste de isolamento e papéis | Teste transacional live: duas contas viram apenas os dados do próprio restaurante nas cinco tabelas centrais; as RPCs de criação de stock/reserva para o restaurante alheio foram recusadas. Uma conta `staff` foi recusada ao consultar equipa, renovar códigos e remover membro. Dados temporários revertidos e ausência de resíduos confirmada. O E2E com Android/sessões reais e restantes RPCs continua pendente. |

Não marcar um caso como aprovado sem guardar a evidência do teste. Uma compilação aprovada não permite concluir que a app está segura ou pronta para operar um restaurante.


## Testes adicionais — autenticação PKCE

| ID | Categoria | Ação / dados | Resultado esperado |
|---|---|---|---|
| AUTH-01 | Normal | Iniciar OAuth com fornecedor configurado | URL contém challenge PKCE e o navegador abre |
| AUTH-02 | Normal | Regressar com um código válido e o verificador guardado | Código trocado uma vez; sessão cifrada no Android Keystore |
| AUTH-03 | Segurança | Abrir um callback com `access_token`/ `refresh_token` no URL e sem código | Callback rejeitado; tokens no URL não são aceites |
| AUTH-04 | Segurança | Abrir callback com código, mas sem verificador guardado | Acesso recusado; nenhuma sessão guardada |
| AUTH-05 | Segurança | Tentar reutilizar o mesmo código | Segundo uso rejeitado pelo servidor |
| AUTH-06 | Limite | Código vazio, muito longo ou parâmetros malformados | Erro controlado, sem falha da aplicação |
| AUTH-07 | Recuperação | Pedir recuperação e abrir a ligação no mesmo dispositivo | Código trocado com o verificador de recuperação; diálogo para definir nova palavra-passe |
| AUTH-08 | Recuperação | Abrir a ligação de recuperação noutro dispositivo | Não deve iniciar sessão; mensagem clara a pedir nova ligação no dispositivo certo |
| AUTH-09 | Inválido | Palavra-passe nova abaixo de 8 caracteres ou confirmação diferente | Formulário rejeita e não envia atualização |
| AUTH-10 | Sessão | Código PKCE válido, mas sessão anterior expirada | A troca PKCE decorre sem tentar renovar primeiro a sessão antiga |
| AUTH-11 | Rede | Perder rede ao trocar o código | Erro claro; não mostrar sessão iniciada sem confirmar resposta do servidor |
| AUTH-12 | Rede | Servidor troca o código mas a resposta é perdida | Repetir o código pode falhar; pedir novo início de sessão/ligação sem guardar uma sessão incompleta |
| AUTH-13 | Regressão | Login normal por email/palavra-passe | Continua a iniciar sessão e a guardar os tokens cifrados |
| AUTH-14 | Registo | Confirmar email com PKCE no mesmo dispositivo | Código trocado com o verificador de registo; o fluxo não aceita tokens bearer da ligação |
| AUTH-15 | Logout | Terminar sessão com rede disponível | Tokens locais são limpos e o servidor recebe o pedido de revogação da sessão atual |
| AUTH-16 | Logout / rede | Terminar sessão sem rede | A sessão local é limpa de imediato; a revogação no servidor não pode ser confirmada e deve ser tratada como best effort |
| AUTH-17 | Logout / regressão | Fechar sessão e voltar ao ecrã de conta | Não ficam dados da conta anterior ou verificador PKCE pendente acessíveis na interface |

Estes casos foram acrescentados ao plano após a revisão do fluxo de autenticação. **Não foram executados num dispositivo real**; dependem de OAuth/email configurados e contas de teste. A compilação do APK só valida sintaxe/compilação, não prova os resultados de autenticação.

**Bloqueio adicional verificado em 29/10/2026:** a página externa de eliminação usa `signInWithOtp` com `shouldCreateUser=false`. A alteração da mensagem da interface evita respostas visuais diferentes, mas não impede uma chamada direta ao endpoint Auth. A issue pública [supabase/auth#1547](https://github.com/supabase/auth/issues/1547) descreve diferenças de resposta e temporização para emails existentes/inexistentes. O caso ACCT-10 exige teste com contas descartáveis e uma mitigação avaliada antes da utilização comercial; ainda não está aprovado.


## Regressão do perfil local do cliente

| ID | Categoria | Ação / dados | Resultado esperado |
|---|---|---|---|
| PROFILE-01 | Privacidade / logout | Guardar um nome de perfil local, terminar sessão e voltar ao ecrã de cliente | O nome da conta anterior não aparece no modo sem sessão |
| PROFILE-02 | Privacidade / troca de conta | Iniciar sessão com uma conta sem cartão de fidelização depois de uma conta com perfil | O nome local anterior não reaparece; só é mostrado um nome carregado para a nova conta |

Estes casos estão cobertos pela limpeza local no código, mas continuam pendentes de validação manual num dispositivo Android real.

## Regressão da cache após autenticação

| ID | Categoria | Ação / dados | Resultado esperado |
|---|---|---|---|
| CACHE-01 | Privacidade / sessão | Encerrar sessão, mudar a adesão/permissões da mesma conta no servidor e iniciar sessão novamente | Os dados de restaurante guardados no dispositivo são removidos antes do carregamento; só os dados atuais devolvidos pelo servidor voltam a aparecer |
| CACHE-02 | Privacidade / troca de conta | Iniciar sessão com outra conta no mesmo dispositivo | Não são apresentados cartões de cliente, reservas, stock, mesas, carta ou pedidos da conta anterior |

Estes casos estão protegidos no código por limpeza da cache ao estabelecer uma nova sessão, mas continuam pendentes de execução manual num Android real.

## Casos adicionais da revisão de fiabilidade 1.5.0

Estes casos foram acrescentados após a revisão do código. **Estado inicial: pendentes de execução manual num dispositivo Android real.** Os testes de CI verificam compilação e Android Lint, não simulam estes cenários.

| ID | Categoria | Ação / dados | Resultado esperado |
|---|---|---|---|
| REL-01 | Rede / reservas | Preencher uma reserva e interromper a ligação durante a gravação | Mensagem de erro; formulário mantém nome, contacto, data, número de pessoas e notas |
| REL-02 | Rede / stock | Falhar ao guardar um artigo de stock ou uma entrada/saída | Não declarar sucesso; manter os campos preenchidos para nova tentativa |
| REL-03 | Rede / autenticação | Falhar um início de sessão, registo ou recuperação de palavra-passe por indisponibilidade do servidor | Mostrar erro e manter o formulário disponível |
| REL-04 | Permissões / REST | Tentar atualizar uma mesa, artigo da carta ou pedido eliminado ou sem acesso | O servidor não deve ser apresentado como tendo alterado um registo; mostrar erro claro quando não for devolvida nenhuma linha |
| VAL-01 | Entrada numérica | Introduzir `NaN` ou infinito em preço/custo, ou custo zero na calculadora de preço | Rejeitar a entrada sem falha da app nem resultado `NaN` |
| VAL-02 | Precisão de stock | Introduzir 1,234 e 1,2345 na quantidade, limite mínimo e movimento de stock | 1,234 é aceite; 1,2345 é rejeitado pela interface e pelo servidor, sem arredondamento silencioso |
| REL-05 | Regressão | Criar, atualizar e arquivar mesa/artigo/pedido com conta autorizada | Confirmação de sucesso apenas quando o servidor devolve a linha alterada |
| REL-06 | Pedidos / perda de resposta | Enviar um pedido, simular perda da resposta após a gravação e repetir sem alterar os campos | Mantém-se um só pedido; a repetição reconhece o registo original pelo UUID |
| REL-07 | Reservas / perda de resposta | Guardar uma reserva, perder a resposta e repetir os mesmos dados | Mantém-se uma só reserva; a função RPC devolve o ID original |
| REL-08 | Idempotência / payload alterado | Reutilizar deliberadamente uma chave de reserva com conteúdo diferente | Operação rejeitada; o registo original não é alterado |
| REL-09 | Stock / perda de resposta | Registar uma entrada/saída, perder a resposta após a gravação e repetir o mesmo formulário | Um único movimento é registado; a quantidade só muda uma vez |
| REL-10 | Fidelização / perda de resposta | Atribuir/descontar pontos, perder a resposta e repetir o mesmo formulário | Uma única transação é registada; o saldo só muda uma vez |
| REL-11 | Idempotência / payload alterado | Reutilizar diretamente uma chave de stock/pontos com outro artigo, cliente ou valor | A operação é rejeitada; o movimento/saldo original não é alterado |
| REL-12 | Compatibilidade / versão antiga | Tentar executar um RPC antigo não idempotente após a migração | A role de cliente recebe erro de permissão; a operação não altera dados |
| REL-13 | Reservas / perda de resposta | Confirmar uma reserva, perder a resposta e repetir o mesmo estado | A repetição é tratada como sucesso; a reserva continua num único estado, sem falso erro |
| REL-14 | Criação de stock / perda de resposta | Criar um artigo, perder a resposta após a gravação e repetir a mesma tentativa | Devolve o mesmo ID; existe um artigo e, quando a quantidade inicial é positiva, apenas um movimento inicial |
| REL-15 | Reutilização de chave / stock | Repetir o identificador de criação de stock com nome, unidade, quantidade, limite ou nota diferentes | A operação é rejeitada; o artigo e o movimento originais permanecem inalterados |
| REL-16 | Regressão / processo terminado | Interromper o processo Android durante a gravação de pedido, reserva, ajuste de stock ou pontos e repetir o mesmo payload | A chave pendente é reutilizada; o servidor não regista operações duplicadas |
| REL-17 | Carta / perda de resposta | Criar um artigo da carta, perder a resposta e reenviar os mesmos dados | A aplicação reconhece o artigo original através do mesmo UUID; não é criado um duplicado |

## Regressão do gestor económico

Os testes unitários executam-se em todas as cinco variantes. O gestor económico usa valores manuais e fórmulas determinísticas; não é contabilidade certificada nem previsão treinada.

| ID | Categoria | Ação / dados | Resultado esperado / estado |
|---|---|---|---|
| ECON-01 | Normal | Vendas 10 000 €, custo alimentar 3 000 €, pessoal 2 500 €, custos fixos 2 000 €, outros variáveis 500 € | Resultado operacional estimado 2 000 €; ponto de equilíbrio aproximado 6 923,08 € |
| ECON-02 | Desperdício / contabilidade | Introduzir custo alimentar que já inclui desperdício e registar 200 € de desperdício | Mostra o indicador de desperdício, mas não subtrai esse valor uma segunda vez ao resultado |
| ECON-03 | Receita / arredondamento | Dois ingredientes de 0,014 € por dose, objetivo de custo alimentar 30% | Soma interna 0,028 €, total exibido 0,03 €, custos por ingrediente exibidos com precisão suficiente; teste unitário/UI está no PR #56 integrado |
| ECON-04 | Tesouraria | Preencher apenas o saldo atual; depois preencher saldo, recebimentos e pagamentos | Entradas parciais rejeitadas; os três campos completos calculam saldo + recebimentos − pagamentos e alertam para zero/negativo |
| ECON-05 | Reposição | Stock 5, consumo diário 3, prazo 4 dias, segurança 2 dias | Alvo 18 e reposição sugerida 13; não cria encomendas nem altera stock |
| ECON-06 | Limites / robustez | Valor numérico com mais de 40 caracteres; texto estruturado com 50 001 caracteres | Rejeição controlada antes de normalizar/dividir a entrada; testes no PR #55 integrado |
| ECON-07 | Ponto de equilíbrio / limite | Vendas 1 000 000 €, custo alimentar 999 999,9999 €, pessoal 1 €, fixos 1 €, outros variáveis 0 € | Não arredondar prematuramente uma contribuição positiva a zero; apresentar a limitação monetária quando o ponto calculado ultrapassa o limite. Em validação no PR #58 |
| ECON-08 | Consumo teórico vs. real | Esperado 0,0004, real 0,0008, custo unitário 1 000 € | Diferença 0,0004 e impacto +0,40 €, sem arredondar a diferença a zero. Em validação no PR #58 |
| ECON-09 | Dados inválidos | Vendas mensais zero, objetivos percentuais fora de 0–100%, quantidades negativas, desperdício de 100% ou prazo de reposição fora dos limites | Entrada rejeitada com mensagem clara; nenhum dado operativo alterado |
| ECON-10 | Separação de responsabilidades | Executar cálculo de receita, previsão simples, cenário de pessoal ou registo de desperdício | Não modifica pedidos, stock, horários ou encomendas; todos os resultados são apresentados como estimativas manuais |

Os casos ECON-07 e ECON-08 só passam a estado aprovado depois de o PR #58 e a CI do commit integrado terminarem com sucesso. A aprovação automatizada não substitui teste manual com dados fictícios num dispositivo real.


## Atualização automática da fila da cozinha

A aplicação consulta apenas os pedidos ativos a cada 15 segundos quando a secção Cozinha está aberta e a aplicação está em primeiro plano. A consulta é pausada ao sair da secção ou ao colocar a app em segundo plano. Não é uma subscrição em tempo real.

| ID | Categoria | Ação / dados | Resultado esperado / estado |
|---|---|---|---|
| KIT-01 | Âmbito / sessão | Abrir Cozinha com restaurante remoto autenticado | A fila atualiza de 15 em 15 segundos; o pedido GET contém apenas estados ativos e respeita o filtro do restaurante. Teste unitário da política e do URL acrescentado; aguarda CI. |
| KIT-02 | Eficiência | Abrir Pedidos, Início ou outra secção; colocar a app em segundo plano | Não são feitas consultas automáticas à fila da cozinha. A rotina é retomada quando a secção Cozinha volta a estar visível em primeiro plano. Teste de dispositivo pendente. |
| KIT-03 | Concorrência | Simular uma consulta lenta e deixar chegar o intervalo seguinte | Não é iniciada uma segunda consulta enquanto a primeira está ativa; a atualização seguinte retoma sem duplicar pedidos. Teste automatizado da política acrescentado; cenário de rede real pendente. |
| KIT-04 | Rede / recuperação | Perder a ligação durante a atualização automática e restabelecê-la | A última fila válida permanece visível, é mostrado um aviso não bloqueante e a consulta seguinte volta a atualizar os pedidos sem duplicados. Teste em Android real pendente. |
| KIT-05 | Operação | Criar pedido noutro dispositivo enquanto Cozinha está aberta; depois avançar um ticket no primeiro dispositivo | O novo pedido aparece automaticamente até ao ciclo seguinte; o ticket avançado desaparece da fila ativa após confirmação do servidor. Teste com dois dispositivos reais pendente. |

## Registo persistente de desperdício de stock

| ID | Categoria | Ação / dados | Resultado esperado |
|---|---|---|---|
| WST-01 | Normal | Registar uma perda com artigo, quantidade e motivo válido | O stock diminui exactamente pela quantidade registada e o movimento fica no histórico |
| WST-02 | Normal | Registar perda sem observação adicional | O motivo fica guardado e a operação é aceite |
| WST-03 | Limite | Quantidade com três casas decimais | Aceite sem arredondamento |
| WST-04 | Inválido | Quantidade zero, negativa, não numérica ou com quatro casas decimais | Rejeitada sem movimento |
| WST-05 | Stock insuficiente | Registar desperdício superior ao stock disponível | O servidor recusa a operação; o stock não fica negativo |
| WST-06 | Idempotência | Repetir a mesma tentativa após perder a resposta | O mesmo movimento é reconhecido; não é descontado duas vezes |
| WST-07 | Permissões | Conta sem acesso ou de outro restaurante tenta registar/consultar perdas | Operação/consulta recusada pelas verificações do servidor e RLS |
| WST-08 | Histórico | Consultar o registo de desperdício | Só aparecem movimentos negativos com o prefixo de desperdício, por ordem mais recente; a consulta indica o limite de 100 registos |
| WST-09 | Inválido | Observação com mais de 350 caracteres | Rejeitada antes do envio |

O registo guarda quantidade e motivo no histórico de movimentos. Quando existe custo de aquisição conhecido, os novos movimentos preservam uma fotografia desse custo e permitem estimar o valor das perdas. Movimentos antigos sem custo continuam desconhecidos.

## Atualização automática do inventário

| ID | Categoria | Ação / dados | Resultado esperado |
|---|---|---|---|
| STK-01 | Sincronização | Abrir Stock num dispositivo e alterar quantidade noutro | A alteração aparece no primeiro dispositivo no ciclo seguinte, até 30 segundos depois |
| STK-02 | Navegação | Sair de Stock para outra secção | A consulta periódica deixa de ser agendada |
| STK-03 | Segundo plano | Colocar a aplicação em segundo plano ou bloquear o telemóvel | A consulta periódica pára; é retomada ao regressar a Stock |
| STK-04 | Concorrência | Manter uma leitura lenta e chegar o ciclo seguinte | Não começa uma segunda leitura automática em paralelo |
| STK-05 | Rede | Perder a ligação durante uma leitura e restabelecê-la | Mantém a última leitura válida, mostra aviso e recupera na próxima leitura |
| STK-06 | Isolamento | Trocar de sessão/restaurante enquanto uma leitura está pendente | A resposta antiga não substitui o inventário da nova conta/restaurante |

A atualização periódica é polling, não uma subscrição Supabase Realtime, e só funciona com a página Stock visível e a aplicação em primeiro plano.

## Receitas e consumo automático de stock

| ID | Categoria | Ação / dados | Resultado esperado |
|---|---|---|---|
| RCP-01 | Configuração | Associar um artigo da carta a um ingrediente, quantidade por dose e desperdício | A linha fica guardada no restaurante correto; a receita permanece desligada |
| RCP-02 | Permissões | Funcionário tenta ativar ou desativar consumo automático | O servidor rejeita; só o proprietário pode aprovar |
| RCP-03 | Reaprovação | Editar ou remover uma linha depois de a receita estar ativa | A receita fica automaticamente desligada até nova aprovação do proprietário |
| RCP-04 | Cálculo | Pedido de duas doses com 0,300 kg por dose e 5% de desperdício | Desconto de 0,632 kg, arredondado conservadoramente a três casas decimais |
| RCP-05 | Transação | Preparar pedido com stock suficiente em todos os ingredientes | Estado e todos os movimentos são gravados na mesma transação |
| RCP-06 | Falta de stock | Um ingrediente não tem quantidade suficiente | A alteração de estado falha; não fica nenhum movimento nem desconto parcial |
| RCP-07 | Idempotência | Reabrir e voltar a preparar o mesmo pedido | Não desconta os ingredientes novamente |
| RCP-08 | Cancelamento | Cancelar um pedido ainda não preparado | Não consome stock |
| RCP-09 | Compatibilidade | Preparar um pedido antigo sem menu_item_id | Mantém o fluxo antigo e não tenta deduzir ingredientes |
| RCP-10 | Isolamento | Conta de outro restaurante tenta consultar, editar ou ativar receita | RLS/RPC impedem a leitura e a operação |
| RCP-11 | Unidades | Configurar ingredientes com unidades incompatíveis | A aplicação explica que não há conversão automática; a equipa tem de manter unidades compatíveis |

O servidor é a autoridade final para quantidades e stock. Os ensaios de CI usam uma base PostgreSQL descartável; a validação em dois dispositivos físicos continua pendente.

## Custo unitário de compra e valorização do desperdício

| ID | Categoria | Ação / dados | Resultado esperado / estado |
|---|---|---|---|
| UCV-01 | Entrada opcional | Guardar artigo com custo unitário vazio | O artigo é guardado; o custo permanece desconhecido e não é substituído por zero |
| UCV-02 | Precisão | Introduzir 12,3456 € por kg e quantidade de desperdício de 0,250 kg | A estimativa da perda é 3,09 €, arredondada a cêntimos |
| UCV-03 | Notação PT | Introduzir custo com vírgula decimal | O valor é normalizado e guardado sem arredondamento silencioso |
| UCV-04 | Limites | Introduzir custo negativo, texto, notação científica, mais de quatro casas decimais ou acima do máximo | Rejeitado na interface e no servidor; nenhum custo inválido é guardado |
| UCV-05 | Histórico | Registar desperdício com custo definido e depois alterar o custo atual | O movimento antigo mantém o custo unitário capturado na data do desperdício |
| UCV-06 | Histórico antigo | Consultar perdas criadas antes da migração ou sem custo definido | Não é inventado um custo histórico; a perda é contada como sem custo conhecido |
| UCV-07 | Idempotência | Repetir uma gravação de artigo após perder a resposta, com o mesmo custo; depois repetir com custo diferente | A mesma tentativa com payload igual devolve o artigo; payload diferente é recusado |
| UCV-08 | Stock | Consultar valor estimado de artigos com e sem custo | Soma apenas artigos com custo conhecido e apresenta quantos ficaram excluídos |
| UCV-09 | Isolamento | Tentar consultar ou alterar custo de artigo de outro restaurante | Operação recusada pelas verificações de autorização e RLS |
| UCV-10 | Arredondamento | Multiplicar 0,500 por 0,0500 € | A estimativa da perda é 0,03 € com arredondamento HALF_UP |

A migration `20261030120000_inventory_unit_cost_and_waste_snapshots.sql` tem de estar aplicada no Supabase antes de testar a nova versão Android. Estes casos exigem CI aprovada e testes ponta a ponta com contas descartáveis; a CI, por si só, não valida a configuração do projeto Supabase de produção.
## Assistente económico ligado ao inventário

| ID | Categoria | Ação / dados | Resultado esperado / estado |
|---|---|---|---|
| ASI-01 | Fonte de dados | Abrir o Assistente com o inventário Premium carregado | A IA recebe unidade, quantidade, limite mínimo e custo unitário atual de cada artigo |
| ASI-02 | Stock específico | Arroz: 2 kg disponíveis, mínimo 5 kg e custo 3,5000 €/kg | Responde 2 kg, custo unitário, valor estimado de 7,00 € e custo indicativo de 10,50 € para voltar ao mínimo |
| ASI-03 | Valor global | Somar artigos com custo conhecido e excluir artigo sem custo | Apresenta apenas a soma conhecida e indica quantos artigos ficaram excluídos |
| ASI-04 | Dados em falta | Todos os artigos sem custo unitário | Não inventa o valor do stock; indica que é necessário preencher custos |
| ASI-05 | Cache não confirmada | Perguntar pelo valor do stock antes de carregar/atualizar inventário | Não mostra valores guardados numa cache não confirmada |
| ASI-06 | Reposição | Artigo abaixo do limite mínimo, com custo unitário conhecido | Calcula apenas a quantidade e o custo para atingir o limite mínimo configurado; não cria encomenda |
| ASI-07 | Limite de recomendação | Artigo acima do limite mínimo | Não sugere reposição nem gasto desnecessário |
| ASI-08 | Sem custo | Artigo abaixo do limite mas sem custo de compra | Mostra o alerta de stock baixo sem inventar custo de reposição |
| ASI-09 | Frescura | Inventário atualizado há menos de cinco minutos e leitura bem-sucedida | A IA pode utilizar quantidades e custos confirmados |
| ASI-10 | Cache antiga/falha | Última leitura há mais de cinco minutos, sem leitura nesta sessão ou falha da atualização | A IA não apresenta valores de stock e pede atualização na secção Stock |

Os valores de stock são estimativas ao custo de compra atual e não incluem portes, embalagens, descontos, IVA recuperável, perdas futuras ou condições de fornecedor. Não são uma avaliação FIFO/custo médio nem uma ordem de compra.


## Identidade estável e fotografia histórica dos pedidos

| ID | Categoria | Ação / dados | Resultado esperado |
|---|---|---|---|
| ORD-01 | Ligação à carta | Criar pedido de artigo ativo no mesmo restaurante | Guarda o UUID do artigo, nome, preço por unidade e custo conhecido no momento |
| ORD-02 | Imutabilidade | Editar nome, preço e custo do artigo depois de criar o pedido | Pedido antigo mantém os valores fotografados |
| ORD-03 | Compatibilidade | Consultar pedido anterior à migração, sem `menu_item_id` | Pedido mantém-se visível e os campos novos permanecem desconhecidos |
| ORD-04 | Arquivo/remoção | Arquivar artigo e, em teste de base de dados, remover definitivamente o artigo ligado a um pedido | Arquivar impede nova seleção; remover o registo não apaga o pedido nem os valores históricos e deixa o UUID a NULL |
| ORD-05 | Isolamento | Tentar associar a um pedido A um artigo pertencente ao restaurante B | Chave estrangeira composta rejeita a escrita |
| ORD-06 | Validação | Enviar custo interno negativo | Restrição da base de dados rejeita o pedido |
| ORD-07 | Custo desconhecido | Artigo sem custo interno positivo confirmado | O pedido guarda custo NULL e não inventa custo zero |
| ORD-08 | Repetição | Repetir a mesma criação após perda de resposta, mantendo o UUID de tentativa; alterar depois a fotografia com a mesma chave | Repetição idêntica é reconhecida; payload diferente não é aceite como a mesma tentativa |
| ORD-09 | Fonte autoritativa | Enviar deliberadamente nome, preço e custo falsificados no pedido com UUID de artigo válido | Trigger invoker substitui os três campos pelos valores actuais da carta consultada no servidor |
| ORD-10 | Arquivado | Enviar novo pedido para um artigo inativo/arquivado | Trigger recusa o pedido; pedidos antigos permanecem válidos |
| ORD-11 | Imutabilidade | Tentar actualizar nome, preço ou custo de um pedido existente via REST | Trigger recusa a alteração; a eliminação do artigo da carta pode apenas desligar o UUID, sem mudar as fotografias |

As migrações `20261030130000_order_menu_item_and_cost_snapshots.sql` e `20261030140000_server_owned_order_menu_snapshots.sql` tem de ser aplicada antes de disponibilizar esta versão Android. Esta fase ainda **não desconta ingredientes**. A ligação de fichas técnicas e o consumo automático só podem avançar depois de revisão das unidades, receitas, transações e testes de concorrência descritos em `RECIPE-INVENTORY-LINK-DESIGN-2026-10-30.md`.
