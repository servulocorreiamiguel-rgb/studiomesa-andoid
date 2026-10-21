# Plano de testes — pedidos, quantidades, fases e notas

**Versão-alvo:** StudioMesa 1.5.0 (APK de demonstração/debug)  
**Funcionalidade:** quantidade por artigo, fase de serviço e nota de cozinha por pedido.  
**Estado:** plano preparado; os testes de execução ainda não foram feitos num telemóvel real.

## Ambiente e pré-condições

- Instalar a mesma variante de demonstração em dois dispositivos Android compatíveis.
- Usar uma conta de teste de proprietário e outra de funcionário do mesmo restaurante; criar também uma conta de outro restaurante e uma conta de cliente.
- Confirmar que só existem dados de teste. Não usar nomes, contactos ou pedidos de clientes reais.
- Guardar o commit, a variante instalada e os resultados de cada execução.
- O workflow compila as cinco variantes debug, executa `testEssencialRestauranteDebugUnitTest` (testes unitários do parser de parâmetros de autenticação) e Android Lint. Estes controlos não substituem os testes funcionais abaixo.

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

## Execução automatizada disponível

O workflow `.github/workflows/android.yml` compila as cinco variantes debug, executa `testEssencialRestauranteDebugUnitTest` e Android Lint. Os testes unitários cobrem quatro casos de descodificação de parâmetros de autenticação, incluindo componentes malformados. Não existem testes instrumentados identificados; os fluxos de autenticação contra o servidor, reservas, pedidos, stock, permissões e sincronização continuam a exigir testes manuais em Android.

Comando de compilação usado pelo workflow:

```bash
gradle --no-daemon assembleEssencialRestauranteDebug assembleProfissionalRestauranteDebug assembleProfissionalClienteDebug assemblePremiumRestauranteDebug assemblePremiumClienteDebug
```

## Registo de resultados desta revisão

| Verificação | Resultado real |
|---|---|
| Revisão das restrições SQL de quantidade/fase/nota | Inspecionada na estrutura da base de dados; não equivale a teste funcional |
| Testes manuais em Android | Não executados nesta sessão |
| Testes unitários de parâmetros de autenticação | Quatro casos em `AuthCallbackParserTest`; o resultado final é registado pelo CI. Cobertura limitada ao parser. |
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


## Casos adicionais da revisão de fiabilidade 1.5.0

Estes casos foram acrescentados após a revisão do código. **Estado inicial: pendentes de execução manual num dispositivo Android real.** Os testes de CI verificam compilação e Android Lint, não simulam estes cenários.

| ID | Categoria | Ação / dados | Resultado esperado |
|---|---|---|---|
| REL-01 | Rede / reservas | Preencher uma reserva e interromper a ligação durante a gravação | Mensagem de erro; formulário mantém nome, contacto, data, número de pessoas e notas |
| REL-02 | Rede / stock | Falhar ao guardar um artigo de stock ou uma entrada/saída | Não declarar sucesso; manter os campos preenchidos para nova tentativa |
| REL-03 | Rede / autenticação | Falhar um início de sessão, registo ou recuperação de palavra-passe por indisponibilidade do servidor | Mostrar erro e manter o formulário disponível |
| REL-04 | Permissões / REST | Tentar atualizar uma mesa, artigo da carta ou pedido eliminado ou sem acesso | O servidor não deve ser apresentado como tendo alterado um registo; mostrar erro claro quando não for devolvida nenhuma linha |
| VAL-01 | Entrada numérica | Introduzir `NaN` ou infinito em preço/custo, ou custo zero na calculadora de preço | Rejeitar a entrada sem falha da app nem resultado `NaN` |
| REL-05 | Regressão | Criar, atualizar e arquivar mesa/artigo/pedido com conta autorizada | Confirmação de sucesso apenas quando o servidor devolve a linha alterada |
