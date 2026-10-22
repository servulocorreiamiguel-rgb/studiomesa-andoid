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

## Execução automatizada disponível

O workflow `.github/workflows/android.yml` compila as cinco variantes debug, executa os testes unitários das cinco variantes de distribuição e Android Lint. Os testes unitários cobrem a descodificação de parâmetros de autenticação (incluindo componentes malformados) e a regra de até três casas decimais para os valores de stock. Não existem testes instrumentados identificados; os fluxos de autenticação contra o servidor, reservas, pedidos, stock, permissões e sincronização continuam a exigir testes manuais em Android.

Comando de compilação usado pelo workflow:

```bash
gradle --no-daemon assembleEssencialRestauranteDebug assembleProfissionalRestauranteDebug assembleProfissionalClienteDebug assemblePremiumRestauranteDebug assemblePremiumClienteDebug
```

## Registo de resultados desta revisão

| Verificação | Resultado real |
|---|---|
| Revisão das restrições SQL de quantidade/fase/nota | Inspecionada na estrutura da base de dados; não equivale a teste funcional |
| Testes manuais em Android | Não executados nesta sessão |
| Testes unitários de autenticação e precisão de stock | `AuthCallbackParserTest` e `StockDecimalValidatorTest`; o resultado final é registado pelo CI. Cobertura limitada a estes componentes puros. |
| Testes instrumentados/funcionais em Android | Não identificados; continuam pendentes em dispositivo real. |
| Compilação CI, testes unitários do parser e Android Lint | Verificar sempre a execução GitHub Actions do commit candidato a distribuição; uma execução aprovada não substitui testes de negócio em Android. |
| Testes de rede e concorrência | Não executados |
| Teste de isolamento entre restaurantes com contas reais | Não executado |

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
