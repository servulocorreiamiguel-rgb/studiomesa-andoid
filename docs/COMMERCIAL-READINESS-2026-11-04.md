# StudioMesa — auditoria de prontidão comercial

**Data de referência:** 4 de novembro de 2026  
**Estado:** **NÃO PRONTO para venda como solução de operação comercial real.**  
**Branch de preparação:** `release/production-readiness-2026-11-04`

Esta auditoria distingue código presente, testes automatizados, estado do backend alojado e requisitos que dependem de configuração/validação externa. Um APK de demonstração ou uma CI verde não constitui certificação de segurança, conformidade ou aptidão para operar um restaurante.

## Atualização de verificação — 05/11/2026

- A revisão atual da `main` é `e86e09296ecfd42fee539e6081a2c508dbfd3267`. A execução [41790761282](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/41790761282) terminou com sucesso nos quatro jobs principais, mas foi executada sobre o commit anterior `bc7ad0025d61b9e1d218fdcf0c6553f6701ca741`. Não a apresentar como CI do commit `e86e092`.
- A execução produziu o artefacto `StudioMesa-1.5.0-Demo-APKs`, com cinco APKs debug. São adequados para testes de demonstração, não para distribuição comercial; o artefacto do GitHub tem expiração indicada para 18/11/2026.
- O commit `e86e092` acrescenta uma migração local de índices e documentação. Não foi confirmada uma nova build para esse SHA nesta verificação. O artefacto APK anterior não contém alterações Android posteriores, embora a aplicação Kotlin não tenha sido alterada nesse commit.
- A branch de receitas `feat/recipes-and-order-stock-consumption-2026-10-30` tem uma execução CI completa aprovada no commit `e209f53`, mas está divergente da `main` e a nova migração ainda não foi aplicada ao Supabase alojado.
- A branch `feature/loyalty-shop-qr-security` tem CI aprovada no commit `4aece3d`, mas está divergente da `main`; as migrações de QR/loja/permissões continuam sem deploy aprovado.
- A branch `feature/customer-reservations` foi atualizada depois da execução bem-sucedida histórica: o head verificado `d9f0f15` tem a execução `41640097317` com conclusão **failure**. Não considerar a revisão atual validada.
- A branch `feature/assistant-chat-history-invoices` tem a execução `42011814895` com conclusão **failure**. A integração generativa continua separada, depende de configuração de servidor e de uma chave externa, e não foi ativada nesta revisão.
- Nenhuma destas verificações autoriza alterações ao Supabase alojado. O histórico remoto/local continua a exigir reconciliação, backup, ensaio de restauro e plano de reversão.

## 1. Estado técnico observado

| Área | Estado observado |
|---|---|
| Repositório principal | `main`, snapshot de partida `bc7ad0025d61b9e1d218fdcf0c6553f6701ca741` |
| Android | Kotlin nativo; `compileSdk/targetSdk 36`; Java 17; versão de código `1.5.0` |
| Variantes | Cinco variantes Essencial/Profissional/Premium × Restaurante/Cliente |
| CI principal antes da exportação CSV | Execução `41724038589`: compilação, testes unitários, Lint, replay SQL/autorização e testes de interface Android 8/16 concluídos com sucesso |
| Exportação CSV | PR #81 integrado no commit `bc7ad002`; a CI da branch `bee7d415` passou nos quatro jobs (build, migrações/autorização, Android 8 e Android 16). A CI da nova revisão `main` foi iniciada na execução [41790761282](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/41790761282) e deve terminar antes de uma nova build de release |
| Publicação comercial | Não existe AAB comercial assinado verificado nem publicação na Google Play |
| IA generativa | Não integrada na versão principal; manter para fase posterior com Codex, conforme decisão do projeto |

## 2. Supabase — leitura ao projeto alojado

Projeto `svwvkfsyevuyabelrtjk`, estado `ACTIVE_HEALTHY`, PostgreSQL 17.6.1.166, região `eu-west-1`. A organização está no plano **Free**.

Na leitura de 4/11/2026:
- 19 tabelas listadas (esquemas `public` e `private`); todas tinham RLS ativa.
- As 19 tabelas estavam sem registos. É uma base vazia, não um ensaio com dados operacionais de clientes.
- A consulta de privilégios não encontrou concessões diretas de tabela a `anon`. As permissões SQL de `authenticated` existentes em algumas tabelas operacionais continuam sujeitas a RLS.
- O Security Advisor assinala oito tabelas com RLS sem políticas: seis tabelas privadas e as duas tabelas RPC-only da loja de recompensas. A leitura dos privilégios não encontrou concessões diretas de tabela a `anon`/ `authenticated` nessas tabelas. Não criar políticas abertas apenas para remover avisos informativos.
- Foram inspecionadas 36 funções `SECURITY DEFINER` públicas: todas tinham `search_path` vazio; nenhuma era executável por `anon`; 31 eram executáveis por `authenticated`. Isso não demonstra, por si só, que cada RPC esteja corretamente limitada por titular, restaurante, papel, argumentos e concorrência. A revisão e os testes negativos função a função continuam pendentes.
- O Auth Advisor mantém `Leaked Password Protection Disabled`. A organização está no plano Free; a configuração não foi alterada nem foi aceite qualquer custo de atualização.
- O Performance Advisor assinala duas chaves estrangeiras sem índice e 28 índices sem utilização observada. Não remover os 28 índices sem tráfego e planos de execução reais.

### Migrações — bloqueio de produção

Na comparação de 4/11/2026, a branch `main` tinha 42 ficheiros SQL de migração e o projeto alojado tinha 54 versões registadas:
- 41 prefixos de versão coincidentes;
- 13 versões remotas sem ficheiro local com o mesmo prefixo;
- 1 ficheiro local sem versão remota correspondente: `20261030120000_inventory_unit_cost_and_waste_snapshots.sql`.

A branch de preparação acrescenta `20261104100000_price_adviser_actor_indexes.sql`, que é uma migração local nova e ainda não está aplicada ao projeto alojado. A divergência histórica mantém-se.

**Não executar `db push`, `migration repair`, `apply_migration` nem alterações manuais ao histórico/schema alojado até existir uma reconciliação aprovada, replay numa cópia descartável, backup verificado e plano de reversão.** A migração nova só prepara os dois índices identificados pelo Advisor; não corrige o histórico antigo.

## 3. Funcionalidades — o que ainda não pode ser prometido

- **Receitas e consumo automático de ingredientes:** a implementação está na PR #72, ainda não integrada. A PR divergiu da `main` e a última CI completa disponível para um commit anterior falhou num teste unitário; a revisão posterior corrigiu a expectativa do teste, mas precisa de CI no head atual.
- **Loja de recompensas e QR de fidelização:** as implementações estão nas PRs #75 e #76, não integradas. Têm CI aprovada nos seus heads de 2/11, mas a sequência de migrações e as sobreposições com outras branches precisam de reconciliação.
- **Reservas na aplicação Cliente e permissões granulares:** a PR #77 não está integrada e a última CI falhou em testes de interface Android 8/16. Não apresentar permissões por área como ativas na produção.
- **Fidelização atual na `main`:** o saldo pode ser ajustado pela equipa; não anunciar como sistema automático completo de campanhas/recompensas até a integração e validação das PRs correspondentes.
- **Integrações externas:** não existe integração confirmada com POS, faturação/contabilidade, pagamentos ou impressoras. A exportação CSV é um relatório operacional e não um documento fiscal, contabilístico ou comprovativo de pagamento.
- **Assistente:** a versão principal usa regras/cálculos determinísticos locais; não é um modelo generativo, não é previsão financeira e não deve ser anunciada como ChatGPT.
- **Sincronização:** a aplicação consulta/sincroniza os dados em operações e em atualização periódica nas áreas aplicáveis; não anunciar sincronização universal em tempo real.
- **Reservas:** faltam ainda calendário/turnos, edição/reagendamento, lembretes e regras de cancelamento configuráveis.

## 4. Bloqueios obrigatórios para produção

### Autenticação e backend
- Configurar e testar SMTP, confirmação de email, recuperação de palavra-passe, URLs de redirecionamento e PKCE em Android físico.
- Testar sessões expiradas, logout, troca de conta, duas contas em dois restaurantes e tentativa de acesso cruzado.
- Testar as RPCs privilegiadas com contas descartáveis de proprietário, funcionário e cliente; incluir chamadas com IDs de outro restaurante, dados inválidos, pedidos repetidos e concorrência.
- Testar eliminação de conta para cliente, funcionário e proprietário sem equipa associada; validar o pedido web, os emails e a eliminação real.
- Decidir se se mantém o plano Free ou se se atualiza para um plano que permita os controlos adicionais necessários. Não atualizar automaticamente.

### Testes operacionais
- Testar em telemóveis físicos, não apenas em emuladores: serviço completo, pedidos simultâneos, atualização de cozinha, reservas concorrentes, stock, fidelização, exportação, falha/recuperação de rede e sessão expirada.
- Executar um piloto controlado num restaurante de teste com dados fictícios, formação, backup e procedimento de recuperação.
- Medir desempenho e estabilidade com carga e histórico representativos; a base atualmente vazia não fornece essa evidência.
- Fazer revisão de segurança independente antes de guardar dados reais de clientes.

### Publicação e assinatura
- Confirmar se a distribuição será feita como cinco aplicações separadas ou outro modelo. Os identificadores atuais de demonstração usam `pt.studiomesa.preview.*`; o identificador base comercial ainda tem de ser escolhido antes da publicação.
- Criar o keystore comercial fora do repositório e guardá-lo com cópias de segurança seguras. Configurar os quatro GitHub Actions Secrets exigidos pelo workflow e a variável `STUDIOMESA_RELEASE_APPLICATION_ID`.
- Executar o workflow manual de release a partir de `main`, confirmar os cinco AABs/APKs assinados e verificar os checksums. Não distribuir os APKs debug como versões comerciais.
- Completar a ficha da Google Play, política de privacidade pública e formulário Data safety. O workflow de release não publica automaticamente na loja.

### Privacidade, contrato e suporte
- A política de privacidade atual é um rascunho com campos por preencher. O responsável pelo tratamento tem de ser identificado e confirmar finalidade, fundamento jurídico, subprocessadores, transferências, retenção, eliminação e contactos.
- Definir termos de serviço/contrato, âmbito de cada plano, titularidade e exportação dos dados, limites do serviço, suporte, atualizações, backups, incidentes e término do contrato.
- Definir e validar preço, pagamento único ou recorrente, alojamento, configuração, manutenção, suporte e impostos. Os valores de 890 €, 1 490 € e 1 690 € continuam a ser referências, não preços validados nem cobranças configuradas.
- Criar canal de suporte, processo de onboarding e procedimento de recuperação de dados.

## 5. Próxima sequência de trabalho

1. **Preparação de código:** integrar apenas melhorias com CI completa no head atual; corrigir a CI das branches de receitas/reservas e resolver conflitos com a `main`.
2. **Reconciliação Supabase:** recuperar/comparar o histórico antigo, reproduzir a sequência final numa cópia isolada e aprovar backup/restauro; só depois planear a janela de migração de produção.
3. **Validação de produção:** contas descartáveis, testes de autorização, SMTP/OAuth, eliminação de conta, concorrência e testes em Android físico.
4. **Piloto:** restaurante de teste com dados fictícios, formação, métricas, suporte e recuperação.
5. **Preparação comercial:** preencher documentos legais/comerciais, decidir preço/contrato, criar keystore e configurar Play Console.
6. **Release:** gerar AABs comerciais assinados a partir de um commit da `main` com CI completa aprovada e só depois iniciar publicação/distribuição.

**Conclusão:** o StudioMesa tem uma base Android funcional, CI automatizada e funcionalidades úteis, mas ainda não está pronto para ser vendido como solução de gestão de restaurante em produção. Os bloqueios acima são requisitos de lançamento, não tarefas cosméticas.


## 6. Requisitos atuais da Google Play

Consulta das páginas oficiais da Google Play em 4/11/2026:

- **Target API:** desde 31/08/2026, novas aplicações e atualizações móveis submetidas à Play Store devem visar Android 16 / API 36 ou superior. O projeto já usa `targetSdk = 36`; voltar a confirmar o valor no AAB comercial final. Fonte: https://support.google.com/googleplay/android-developer/answer/11926878
- **Conta de programador:** o titular que se regista tem de ter pelo menos 18 anos. A taxa oficial indicada é de 25 USD, paga uma única vez. Confirmar o montante e as verificações no fluxo oficial de registo. Fonte: https://support.google.com/googleplay/android-developer/answer/6112435
- **Teste fechado:** para contas pessoais criadas após 13/11/2023, a Google exige pelo menos 12 participantes inscritos continuamente num teste fechado durante 14 dias antes de solicitar acesso à produção. Esta condição depende do tipo/data da conta. Fonte: https://support.google.com/googleplay/android-developer/answer/14151465
- **Privacidade e Data safety:** a política tem de estar disponível numa URL pública não editável, ligada na ficha da Play Store e acessível dentro da aplicação. O formulário Data safety tem de refletir os dados e fornecedores realmente usados. O rascunho atual contém campos por preencher, pelo que não pode ser publicado. Fonte: https://support.google.com/googleplay/android-developer/answer/10144311
- **Eliminação de conta:** como o StudioMesa permite criar contas, é necessário manter uma via clara de eliminação dentro da aplicação e uma via web pública, eliminando os dados associados conforme as regras de retenção legitimamente aplicáveis. O endpoint web existe, mas falta testar o percurso completo com contas descartáveis e validar o resultado no backend. Fonte: https://support.google.com/googleplay/android-developer/answer/13327111

A Google Play não é o único canal possível para uma instalação Android, mas APK debug não deve ser usado como pacote comercial. Mesmo para distribuição direta, assinatura comercial, contrato, política de privacidade, suporte, backups e testes operacionais continuam necessários.


### Risco específico — descoberta de emails no pedido web de eliminação

**Correção efetuada em 04/11/2026:** a Edge Function `account-deletion-web` foi atualizada no projeto alojado da versão 2 para a versão 3. A função continua a servir a página por GET público (`verify_jwt=false`), mas o formulário passou de `signInWithOtp(shouldCreateUser=false)` para `resetPasswordForEmail` com redirect PKCE. O endpoint de eliminação continua separado, autenticado por JWT (`delete-account`, versão 3, `verify_jwt=true`). A leitura após a implantação confirmou a versão 3, o novo método e a ausência de `signInWithOtp` na página.

A documentação atual do Supabase diz que `resetPasswordForEmail()` não revela se existe uma conta para o email. Há, contudo, uma issue pública recente (#2702) que descreve uma possível diferença de resposta quando o registo público está desativado. A definição efetiva de registo público do projeto não foi confirmada nesta auditoria. Antes da publicação, testar endereços existentes e inexistentes com contas descartáveis e confirmar o comportamento do Auth do projeto; não assumir que a troca de método elimina qualquer hipótese de enumeração em todas as configurações.

A função `delete-account` alojada está ativa, exige JWT e prepara um pedido de eliminação de curta duração antes de chamar o Auth Admin. O percurso completo (email → sessão → confirmação → apagamento → tratamento dos dados associados) continua por testar com contas descartáveis. Não usar contas reais nem dados de clientes para esses ensaios.
