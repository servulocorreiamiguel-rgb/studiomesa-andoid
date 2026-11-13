# StudioMesa — lista de preparação para uso profissional

**Ponto de situação verificado em 13/11/2026:** a `main` está pronta para demonstração com dados fictícios, mas **não está autorizada para operação com dados reais nem venda operacional**. A PR #128 já foi integrada na `main` (merge commit `2b06b232f6282b73c8cae79ae074804d5ea244b2`) e acrescenta arquivo/sincronização SAF-T, previsões económicas, OCR local de faturas e receção confirmada de mercadoria. A PR #130 também foi integrada e bloqueia a assinatura comercial até os requisitos de produção serem comprovados. A documentação foi actualizada na PR #132 (merge `319c68fe69d7047ba99e4595fe93146f1bf26877`). A CI da `main` para este último merge documental estava em execução na última consulta; a última CI completa do código, no commit `cbfc0e8c671d647bc0c6b3ddea80af3695c637c5`, passou os quatro jobs.

Auditoria histórica de 12/11: [docs/COMMERCIAL-CLOSEOUT-AUDIT-2026-11-12.md](docs/COMMERCIAL-CLOSEOUT-AUDIT-2026-11-12.md). O ponto de situação abaixo substitui as contagens e estados históricos que se seguem.

## Código e CI

- [x] A `main`, commit `cbfc0e8c671d647bc0c6b3ddea80af3695c637c5`, passou os quatro jobs na execução [43486619914](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/43486619914): compilação APK, replay PostgreSQL/autorização, Android 8 e Android 16.
- [x] A PR #128 foi integrada no merge commit `2b06b232f6282b73c8cae79ae074804d5ea244b2`; a CI pós-merge da `main` [43474362932](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/43474362932) passou os quatro jobs.
- [x] A PR #128 inclui arquivo e sincronização de vendas SAF-T, previsões de procura por prato/dia, OCR local de faturas de fornecedores e receção de mercadoria após confirmação explícita, com idempotência e custo médio ponderado. Os testes abrangem isolamento por conta, sincronização, cifragem, repetição e receções parciais.
- [x] Testes manuais já confirmados pelo proprietário no OPPO A5m: funcionalidades gratuitas, reservas, sincronização entre a aplicação de gestão e a aplicação Cliente e isolamento dos dados entre restaurantes. Esta confirmação não volta a ser tratada como pendente.
- [ ] A CI e estes testes manuais não substituem a validação física dos fluxos ainda não testados, SMTP/autenticação reais, migrações alojadas e cenário de perda de rede móvel.
- [x] Existem APKs de demonstração/debug. Não são releases comerciais assinadas e não foram testadas fisicamente no OPPO A5m.
- [x] Um único assistente integra modelos locais Gemma 4 E2B e LFM2.5-1.2B INT4, com router cloud publicado e JWT obrigatório.
- [ ] Validar fisicamente os modelos locais, qualidade, latência, RAM, temperatura, bateria, libertação de memória e fallback.

## Supabase alojado e migrações

Projeto `svwvkfsyevuyabelrtjk`: estado `ACTIVE_HEALTHY`, PostgreSQL 17.6.1.166, região `eu-west-1`. A organização está no plano Free.

Comparação read-only feita em 13/11/2026:
- `main`: 71 ficheiros SQL de migração; 63 migrações no histórico remoto.
- As 63 versões remotas têm correspondência exata por versão e nome no repositório; não há versões remotas sem ficheiro local.
- Oito migrações existentes em `main` ainda não foram aplicadas no Supabase alojado:
  - `20261030111815_restaurant_recipes_and_order_stock_consumption`
  - `20261102103000_granular_restaurant_staff_permissions`
  - `20261102120000_require_loyalty_permission_for_reward_fulfillment`
  - `20261102121000_enforce_loyalty_permission_for_staff_loyalty_operations`
  - `20261104100000_price_adviser_actor_indexes`
  - `20261107170000_loyalty_shop_photos_and_plan_limits`
  - `20261112120000_restaurant_membership_request_actor_indexes`
  - `20261113114000_supplier_invoice_lines_stock_receipts_and_saft_sync`
- A migração `20261113114000` já faz parte da `main`; não é uma alteração futura. No Supabase alojado continuam por aplicar as oito migrações listadas acima.
- As tabelas `public.restaurant_recipes`, `public.restaurant_recipe_ingredients`, `private.restaurant_staff_permissions` e `private.restaurant_loyalty_entitlements` continuam ausentes no servidor. Por isso, fichas técnicas/consumo automático de ingredientes, matriz granular de permissões e limites comerciais por plano ainda não estão activos no backend alojado. A migração `20261113114000` também está pendente e é necessária para activar a sincronização cloud das linhas de faturas, receções de stock e arquivo SAF-T. O código Android e os testes de CI existem, mas estas funções não devem ser anunciadas como operacionais até à migração controlada.
- [ ] Rever as migrações e o schema, garantir backup/restauro verificável e executar os testes de autorização antes de qualquer alteração no projeto de produção.
- [ ] Criar uma branch Supabase isolada e validar nela as migrações, RLS, RPCs e advisors. A criação consultada custa 0,01344 USD/hora; não foi criada por não existir autorização para esse custo.

Procedimento passo a passo para exportação, ensaio de restauro e pré-visualização antes de produção: [runbook de backup e migrações](docs/PRODUCTION-BACKUP-AND-MIGRATION-RUNBOOK.md). O plano Free não inclui backups automáticos; não aplicar DDL enquanto o restauro de Auth/Storage e da base de dados não estiver verificado.

## Auditoria de segurança no servidor

Consultas read-only e Supabase Advisors em 13/11/2026:
- 20 tabelas públicas observadas com RLS ativada.
- O Advisor assinala 11 tabelas com RLS e sem políticas diretas: seis no esquema privado e cinco no esquema público. A consulta de privilégios confirmou que nem `anon` nem `authenticated` têm permissões diretas SELECT/INSERT/UPDATE/DELETE nessas 11 tabelas. Não criar políticas genéricas apenas para silenciar o aviso.
- Nenhuma função `SECURITY DEFINER` nos esquemas `public`/`private` é executável por `anon`; todas as 52 funções verificadas têm `search_path = ''`. O Advisor identifica 42 RPCs públicas executáveis por `authenticated`, em grande parte chamadas intencionais da aplicação. Ainda é necessária revisão funcional individual de autorização, âmbito do restaurante, parâmetros e concorrência; não revogar permissões em bloco.
- [ ] A proteção de palavras-passe comprometidas está desativada. A organização está no plano Free; a documentação Supabase indica que esta opção exige Pro ou superior. Não foi alterado o plano nem assumido qualquer custo: [documentação oficial](https://supabase.com/docs/guides/auth/password-security).
- [ ] O Performance Advisor assinala quatro foreign keys sem índice no projeto alojado; as correções correspondentes existem em migrações locais ainda não aplicadas.
- [ ] O Performance Advisor mostra 45 índices sem utilização observada. Não remover índices automaticamente enquanto não houver tráfego real e medição.

## Autenticação, IA e integrações

- [ ] Configurar SMTP/domínio e testar registo, confirmação, recuperação, refresh, logout e eliminação de conta com contas descartáveis.
- [ ] Confirmar/configurar `OPENROUTER_API_KEY` como segredo do servidor e executar testes autenticados de proprietário, funcionário, token expirado, privacidade, filtros, quotas e fallback. A IA cloud permanece desligada por omissão até à validação.
- [ ] Confirmar `SEARXNG_BASE_URL` HTTPS e testar pesquisa Web sem enviar dados privados.
- [x] Google/Microsoft ocultos por decisão para a primeira venda; OAuth/PKCE preservado para fase posterior.
- [ ] Testar sincronização do histórico/memória entre dois dispositivos com consentimento, perda de rede e eliminação. A cópia cloud não é encriptação ponta a ponta.

## Funcionalidades económicas integradas no código — implantação cloud pendente

- A PR #128 já está integrada na `main`: o código inclui sincronização autenticada do arquivo de vendas SAF-T, previsão de procura por prato e dia da semana, OCR local de faturas de fornecedores e receção de mercadoria com confirmação explícita do proprietário, validação de unidades, idempotência e custo médio ponderado.
- O OCR é uma sugestão para revisão; não movimenta stock sozinho. O proprietário tem de aprovar a correspondência de cada linha e confirmar a receção.
- A migração `20261113114000_supplier_invoice_lines_stock_receipts_and_saft_sync` ainda não está aplicada ao Supabase alojado. Por isso, a sincronização cloud das linhas de faturas/receções e do arquivo SAF-T não deve ser considerada operacional em produção até à reconciliação, backup/restauro e deployment controlado.

## Funcionalidades comerciais ainda não integradas na aplicação

- [ ] **Campanhas de reactivação:** as tabelas `public.restaurant_reactivation_campaigns` e `public.restaurant_customer_notifications` existem no Supabase, mas a `main` ainda não contém o editor/envio Android para o proprietário. A implementação está na [PR #94](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/pull/94), que precisa de rebase, revisão de conflitos e CI antes de ser integrada.
- [ ] **Centro de notificações e push:** a `main` ainda não contém o centro de notificações de actividade nem a integração FCM/push. As propostas [PR #91](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/pull/91) e [PR #92](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/pull/92) sobrepõem-se; é necessário consolidar uma implementação, rever permissões Android e configuração do servidor, e testar notificações reais.
- [ ] **Perfis pré-configurados da equipa:** existe `StaffPermissionSet.kt` no cliente, mas a tabela privada `private.restaurant_staff_permissions` ainda não existe no Supabase alojado e a implementação de perfis pré-configurados proposta na PR #92 não está na `main`. Até à migração e aos testes, não anunciar a matriz granular como activa.
- [ ] A geração de ideias de conteúdo pelo assistente não equivale a campanhas automáticas, envio de ofertas a clientes inactivos ou notificações push. Estas capacidades só devem ser vendidas depois de integradas e testadas.


## Produto, segurança e testes de aceitação

- [ ] E2E com dois restaurantes e contas de proprietário, funcionário e cliente: isolamento, menus, pedidos, cozinha, reservas, inventário, fidelização, campanhas, faturas e revogação de acesso.
- [ ] Testar falhas de rede, repetição idempotente, sessão expirada, conflitos concorrentes, restauro e atualização em dispositivos reais.
- [ ] Testar Gemma/LFM no OPPO A5m e em equipamentos Android representativos.
- [ ] Rever retenção, exportação, eliminação, resposta a incidentes, backups e suporte.
- [ ] Não anunciar integração directa com POS, pagamentos, contabilidade ou impressoras. A importação/análise de ficheiros SAF-T não equivale a ligação directa ao POS/contabilidade nem prova pagamentos recebidos; a sincronização cloud SAF-T continua dependente da migração pendente.
- [ ] Finalizar termos, política de privacidade, DPA, subprocessadores, declaração Data Safety, licenças da IA e suporte com a entidade responsável.

## Release comercial e distribuição

- [x] Workflow manual de release assinado existe e bloqueia builds sem CI verde para o commit exato, confirmação manual, keystore e package ID.
- [ ] Escolher os identificadores finais após decidir a estratégia de distribuição; ficam permanentes após publicação.
- [ ] Criar keystore privada e backup sob controlo do proprietário; configurar GitHub Actions Secrets/Variables. Nunca enviar chaves ou passwords por chat ou Git.
- [ ] Rever a estratégia Google Play: cinco variantes técnicas não significam cinco listagens permitidas. Considerar apps Gestão/Cliente e entitlements seguros por plano ou distribuição B2B privada.
- [ ] Gerar AAB/APKs assinados com a chave comercial, verificar assinaturas/checksums e testar atualização em dispositivos reais. O workflow não publica automaticamente na Play Store.
- [ ] Confirmar procedimento de backups/restauro antes de operar com dados de restaurantes.
- [ ] Definir uma pessoa adulta/entidade com capacidade legal como titular dos contratos, faturação, responsabilidades de proteção de dados e conta Play Console. Não usar a identidade de terceiros como fachada; o Código Civil português regula a capacidade dos menores nos artigos 122.º–127.º e a Play Console exige titular com pelo menos 18 anos.

**Regra de lançamento:** a StudioMesa só fica autorizada para operação comercial depois de todos os bloqueios de backend, Auth, autorização, E2E/físicos, release e legal/privacidade acima estarem resolvidos e documentados.

---

## Registos históricos — não usar como estado atual

Os registos abaixo descrevem auditorias anteriores e podem conter números, caminhos e estados entretanto alterados. Em caso de conflito, prevalecem o estado consolidado no início deste ficheiro e a auditoria comercial de 12/11/2026.


Os registos e checkboxes abaixo foram escritos em 29/10 e 05/11/2026. Alguns referem estados do código e do Supabase que mudaram desde então. Servem apenas para histórico; em caso de conflito, prevalece a fotografia atual acima e o gate de lançamento de 10/11/2026.

**Nota:** os registos abaixo são históricos (29/10 e 05/11/2026). Quando diferirem da fotografia atual, prevalece o topo deste ficheiro e `docs/COMMERCIAL-RELEASE-GATE-2026-11-10.md`, não o relatório de 04/11.


**Estado de lançamento nesta revisão: não pronto para operação comercial real.**  
**Auditoria de segurança, versão e preparação comercial — 29/10/2026**

- [x] Consulta do projeto Supabase ligado: estado ativo, PostgreSQL 17.6 e RLS ativa nas 15 tabelas públicas/privadas listadas. Um ensaio transacional com duas contas confirmou isolamento em restaurantes, equipa, carta, mesas e pedidos; duas RPCs também recusaram operações sobre o restaurante alheio. Faltam os restantes fluxos E2E, sessões reais e concorrência.
- [x] Revisão das funções `SECURITY DEFINER` existentes no banco: `search_path` vazio em todas as funções inspecionadas e execução anónima não permitida. Os avisos para o papel `authenticated` são, em grande parte, RPCs de aplicação intencionais; não os remover sem testes de autorização.
- [x] As tabelas privadas `private.restaurant_join_codes`, `private.account_deletion_queue`, `private.restaurant_price_adviser_settings` e `private.restaurant_price_adviser_audit` têm RLS ativa, sem políticas de acesso direto; os privilégios diretos de cliente estão revogados. O Security Advisor assinala isso como informativo; o acesso direto deve continuar bloqueado.
- [x] **Alinhamento das versões de migração verificado em 29/10/2026:** o Supabase alojado e o repositório têm agora as mesmas 41 versões aplicadas, incluindo `20261029133656_revoke_postgres_default_public_privileges.sql` e `20261029203424_restaurant_price_adviser_owner_approval.sql`. A migração de permissões predefinidas foi aplicada ao projeto ativo e a consulta confirmou que novos objetos em `public` não recebem privilégios API por omissão. A CI reproduziu as 41 migrações numa base descartável e testou permissões por criação e eliminação de objetos temporários. Isto prova alinhamento da versão de migração e reprodutibilidade da sequência atual, mas não é uma comparação integral de todas as definições do esquema entre o servidor alojado e a base descartável.
- [x] **Consultor de preços com aprovação do proprietário:** a migração `20261029203424_restaurant_price_adviser_owner_approval.sql` está aplicada no Supabase alojado e alinhada com o repositório (41 migrações). A opção fica desligada por defeito; as quatro RPCs usam `search_path = ''`, negam execução a `anon`/`PUBLIC`, autorizam apenas `authenticated` e verificam o papel de proprietário no servidor. A CI da `main` passou em compilação, testes unitários, Lint, replay de migrações/autorização e emuladores Android 8/16. A validação de produção real ainda está pendente.
- [ ] Testar ponta a ponta a autorização de preços com contas descartáveis de proprietário e funcionário no Supabase alojado, incluindo opção desligada, alteração individual confirmada, recusa de reduções, preço desatualizado, repetição idempotente, acesso entre restaurantes e consulta do histórico; repetir num telemóvel Android físico.
- [ ] **Bloqueio de autenticação:** a proteção de palavras-passe comprometidas continua desativada. A organização Supabase está no plano Free e a [documentação oficial](https://supabase.com/docs/guides/auth/password-security) indica que esta proteção requer Pro ou superior. Não foi alterado o plano nem assumido qualquer custo; resolver por decisão explícita antes da utilização comercial com contas reais.
- [x] Correção de concorrência de `join_restaurant_staff` aplicada ao Supabase pela migração `20261026173330_fix_staff_join_concurrent_conflict_target`. Verificações pós-aplicação: `anon` não pode executar a função, `authenticated` pode, `search_path` está vazio, a repetição no mesmo restaurante passa e a tentativa de aderir a um segundo restaurante é recusada. O ensaio reverteu todos os dados temporários; ainda falta concorrência real em duas sessões.
- [x] A CI executa agora um verificador estático de segurança e testes unitários desse verificador. É uma salvaguarda de código, não um substituto de testes reais de autorização, concorrência e rede.
- [x] Preparação para a alteração do Supabase de 30/10/2026, que deixa de conceder acesso ao Data API por omissão a novas tabelas `public`: a migração de permissões predefinidas foi aplicada e verificada um dia antes; as 11 tabelas públicas atuais têm RLS ativa e pelo menos uma política. As concessões existentes continuam a ser governadas pelas permissões e políticas atuais. Ver [aviso oficial](https://supabase.com/changelog/45329-breaking-change-tables-not-exposed-to-data-and-graphql-api-automatically).
- [ ] O Supabase Performance Advisor mostra índices sem utilização observada. Como as tabelas estão atualmente vazias, não remover índices com base nesse aviso isolado.

Usar esta lista para decidir o que pode ser demonstrado e o que ainda exige desenvolvimento/testes. Um botão presente na interface não prova que uma funcionalidade esteja completa.

## Carta e menu

- [x] Criar/editar artigos, categorias, descrição, preço e alergénios declarados.
- [x] Separar custo interno do preço e dos campos publicados para a aplicação de cliente (validar novamente por teste de permissões).
- [x] Arquivar artigos sem apagar a informação dos pedidos antigos.
- [ ] Testar todos os campos inválidos, preços, moedas e consistência ao editar em vários dispositivos.
- [ ] Fotografias, horários de disponibilidade, tradução e histórico de edição, se forem necessários para cada cliente.
- [ ] Rever a informação de alergénios com o restaurante. Os dados introduzidos não são uma garantia médica.

## Reservas

- [x] A atualização de estado no servidor trata uma repetição do mesmo estado como sucesso, evitando um falso erro após perda de resposta. O cenário de perda de rede tem de ser validado em Android real.

- [x] Fluxo inicial de registo e estados (pendente, confirmada, cancelada, concluída, não compareceu).
- [x] Validação no servidor da capacidade da mesa e da sobreposição de reservas pendentes/confirmadas atribuídas à mesma mesa.
- [ ] Definir horários de funcionamento, capacidade agregada por sala e regras de intervalos/preparação.
- [ ] Edição/reagendamento, calendário visual, lembretes, confirmações por email e regras configuráveis de cancelamento.
- [ ] Testar concorrência em dois dispositivos e alterações ao horário de funcionamento.
- [x] Os contactos/notas das reservas não são persistidos nas preferências locais e a cache legada é limpa no arranque/logout.
- [x] A criação de reservas usa um identificador de tentativa único e índice composto para que uma repetição da mesma operação possa devolver a reserva original; teste real de perda de rede continua pendente.
- [ ] Definir minimização de dados pessoais no servidor, acesso, conservação e eliminação.

## Pedidos e serviço

- [x] Mesas, pedidos, quantidade, fase do serviço, notas e estados básicos.
- [x] A criação de pedidos reutiliza o mesmo UUID para tentativas idênticas dentro do formulário e verifica o registo pelo servidor antes de declarar sucesso após um erro de escrita. Teste real de perda de rede continua pendente.
- [x] Vista de cozinha separada para pedidos ativos, com tempo de espera, notas e avanço de estado; a fila consulta automaticamente apenas os pedidos ativos a cada 15 segundos enquanto a secção está aberta e a app em primeiro plano, mantendo também atualização manual. A consulta não corre em segundo plano e não é uma subscrição em tempo real.
- [ ] Testar a atualização automática da cozinha em dois dispositivos reais, incluindo criação simultânea de pedidos, perda/recuperação da rede, mudança de página, app em segundo plano e retoma; confirmar que a fila não perde pedidos e que não são feitas consultas concorrentes.
- [x] Histórico de criação e alteração de estado no servidor.
- [ ] Testar duplicações por repetição após falha de rede e pedidos concorrentes.
- [ ] Testar cancelamentos, sessão expirada, reconexão e sincronização entre dispositivos.
- [ ] Impressora de cozinha, POS/caixa, pagamentos ou faturação não devem ser considerados integrados sem implementação e testes específicos.

## Fidelização

- [x] Movimentos manuais de pontos têm identificador de tentativa e deduplicação no servidor; a validação com perda real de rede num dispositivo ainda está pendente.

- [x] Associação por código, saldo partilhado e registo de movimentos.
- [ ] Automatizar regras de acumulação/resgate só depois de definir regras comerciais e prevenção de fraude.
- [ ] Limitar tentativas repetidas de adivinhar códigos e monitorizar abusos.
- [ ] Testar saldos negativos, limites, concorrência e reconciliação dos movimentos.
- [ ] Proteger os dados dos clientes e confirmar que cada cliente só vê o próprio cartão/histórico.

## Stock e inventário

- [x] Movimentos de stock têm identificador de tentativa e deduplicação no servidor; a validação com perda real de rede num dispositivo ainda está pendente.

- [x] Artigos com unidade, quantidade atual e limite mínimo; estado de baixo stock/esgotado no ecrã.
- [x] A interface e o servidor rejeitam quantidades, limites e movimentos com mais de três casas decimais, evitando arredondamento silencioso; o cenário ponta a ponta ainda requer validação em Android real.
- [x] Entradas e saídas transacionais no servidor, com saldo após movimento, nota, data e utilizador no histórico.
- [x] Registo de desperdício por artigo, com motivo controlado, desconto transacional de stock, chave anti-duplicados e consulta das 100 perdas mais recentes.
- [x] Atualização automática do inventário a cada 30 segundos quando a secção Stock está aberta em primeiro plano; a leitura manual mantém-se disponível e a última cópia válida é preservada em falhas de rede.
- [ ] Testar desperdício e atualização automática ponta a ponta em Android físico, incluindo dois dispositivos, perda/restauro de rede, repetição idempotente, stock insuficiente e isolamento entre restaurantes.
- [ ] Testar concorrência, valores-limite, sessão expirada e isolamento entre restaurantes.
- [ ] Avisos automáticos, fornecedores/encomendas, consumo automático de ingredientes por receita, desperdício e inventário por local.

## Administração e equipa

- [x] Separação de contas, papéis owner/staff e código de adesão de equipa.
- [x] Proprietário pode consultar membros, remover o acesso de um funcionário e renovar automaticamente o código de adesão após a remoção.
- [x] Verificado em teste transacional que operações administrativas de proprietário (consultar equipa, renovar códigos e remover membros) são recusadas a uma conta `staff`.
- [ ] Ativar permissões granulares para as operações restantes depois de criar/testar a branch Supabase de desenvolvimento e aplicar as migrações aprovadas. Até lá, o gate continua desligado e os funcionários existentes mantêm o acesso operacional legado.
- [ ] Suspensão temporária, horários/turnos e trilho de auditoria administrativo completo.
- [ ] Verificar em testes que a revogação é refletida em todos os dispositivos; ecrãs ativos podem continuar a mostrar dados já carregados até atualizar/reabrir.
- [x] Ensaios transacionais live: duas contas só viram os próprios dados em `restaurants`, `restaurant_staff`, `menu_items`, `restaurant_tables` e `restaurant_orders`; RPCs de criação de stock/reserva para o restaurante alheio foram recusadas. Uma conta `staff` não conseguiu listar a equipa, renovar códigos de equipa/clientes ou remover outro membro. Os testes foram revertidos e verificou-se que não ficaram registos temporários.
- [ ] Alargar os testes de isolamento a fidelização, restantes RPCs, cliente Android com sessões reais e pedidos concorrentes.

## Segurança e continuidade

- [x] RLS ativa nas tabelas principais do esquema público inspecionado.
- [x] Permissões de execução revogadas para os RPCs antigos não idempotentes de reservas, stock e pontos; a app atual usa as variantes com chaves de idempotência. Confirmado no catálogo PostgreSQL que os três endpoints antigos não são executáveis por `anon` nem por `authenticated`.
- [x] Retiradas permissões de tabela desnecessárias: `anon` já não tem acesso direto a carta, pedidos ou mesas; `authenticated` conserva apenas `SELECT`, `INSERT` e `UPDATE` nessas tabelas. RLS mantém-se ativa.
- [x] Índices únicos na base de dados impõem uma única associação de equipa e um único cartão de fidelização por conta (migração `20261021153243`), prevenindo associações duplicadas por pedidos concorrentes; as tabelas estavam vazias antes da aplicação.
- [x] O código de acesso da equipa é `NOT NULL` e continua protegido pelo índice único existente `restaurant_join_codes_staff_code_idx`. A migração corretiva `20261021154348` remove o índice duplicado criado temporariamente em `20261021154106`.
- [x] RLS ativada na tabela privada `private.restaurant_join_codes` (migração `20261021152901`). Não existem políticas de acesso direto; confirmado que `anon` e `authenticated` não têm privilégios diretos de leitura/escrita. As funções necessárias são propriedade de `postgres`, com `BYPASSRLS`. O aviso atual do Security Advisor é informativo: RLS ativa sem política, intencional para esta tabela privada.
- [x] As funções `SECURITY DEFINER` inspecionadas exigem sessão e as permissões de execução para `anon` estavam revogadas para os RPCs listados pelo Security Advisor.
- [ ] Proteção contra palavras-passe comprometidas no Supabase Auth. O alerta está ativo e o plano da organização é Free; a documentação do Supabase indica esta proteção nos planos Pro ou superiores. Rever antes de produção ([documentação oficial](https://supabase.com/docs/guides/auth/password-security)).
- [x] Sessões guardadas com cifragem AES-GCM usando Android Keystore no código inspecionado.
- [x] Código PKCE em OAuth, registo e recuperação, com verificador cifrado e rejeição de tokens bearer na ligação de retorno.
- [x] Logout local limpa os tokens e inicia a revogação da sessão atual no Supabase em segundo plano.
- [x] Nome de perfil e código sintético de cliente guardados localmente são limpos ao terminar sessão e ao estabelecer uma nova sessão autenticada.
- [x] A cache de dados operacionais é limpa sempre que se estabelece uma nova sessão, mesmo para a mesma conta, e quando o servidor confirma que a conta já não pertence a um restaurante. A cache é depois reposta a partir do servidor; ainda falta validar estes casos no dispositivo real.
- [ ] Testar login, recuperação, registo e revogação de sessão num dispositivo real; o logout remoto é best effort quando a rede falha.
- [ ] Confirmar backups disponíveis, retenção e executar um restauro de teste num ambiente separado.
- [x] Implementado o fluxo de eliminação de conta autenticada no Android e no Supabase; as contas de proprietário exigem confirmação dos dados do restaurante e não podem ser eliminadas enquanto houver outros membros.
- [ ] Testar eliminação de conta com contas de teste dedicadas (não foram apagadas contas reais); confirmar anonimização de autoria, cascatas e expiração da fila.
- [x] Publicada a página web externa para iniciar pedidos de eliminação: `https://svwvkfsyevuyabelrtjk.supabase.co/functions/v1/account-deletion-web`.
- [x] A função protegida `delete-account` está na versão 3 do projeto Supabase, com `verify_jwt=true` e a dependência `@supabase/server@1.9.2` fixada. Pedido HTTP público sem token rejeitado com `401`; página web externa confirmada disponível. O percurso completo por email e a eliminação efetiva com uma conta descartável continuam pendentes.
- [ ] Autorizar o URL de redirecionamento em Supabase Auth e testar envio/click de magic link com contas de teste.
- [x] **Mitigação de enumeração na página de eliminação:** o pedido público foi alterado de `signInWithOtp` para `resetPasswordForEmail` com redirecionamento PKCE para a mesma página; a documentação do Supabase indica que o método de recuperação não revela se o endereço está registado. O verificador estático exige este fluxo e rejeita o uso de OTP nesta página. A alteração de código ainda depende da CI e da publicação da Edge Function.
- [ ] **Validação Auth ponta a ponta:** comparar respostas HTTP e tempos com contas descartáveis existentes/inexistentes; confirmar envio do email de recuperação, allowlist do URL de redirecionamento, regresso ao mesmo navegador com sessão PKCE e eliminação segura após confirmação. Não considerar este fluxo aprovado para uso comercial antes destes testes.
- [ ] Publicar uma política de privacidade pública e configurar a secção Data safety na Play Console; confirmar exportação, conservação e resposta a incidentes.
- [ ] Não colocar chaves `service_role` ou `sb_secret_...` na aplicação. A chave `sb_publishable_...` incorporada no cliente é pública por natureza e depende de RLS/políticas corretas.

## Lançamento e suporte


- [x] Workflow atual compila APKs de demonstração/debug e executa testes unitários nas cinco variantes de distribuição.
- [x] Configuração usa compileSdk/targetSdk 36, AGP 8.10.0 e Gradle 8.11.1; a última execução verificada compilou e carregou os cinco APKs debug de demonstração.
- [x] Adicionado workflow manual que prepara cinco AABs comerciais assinados, exige confirmação explícita, executa testes e Lint e verifica as assinaturas.
- [ ] Escolher e validar o identificador base definitivo, criar o keystore privado, configurar os GitHub Actions Secrets/Variables e executar o workflow comercial com sucesso.
- [ ] Terminar os testes ponta a ponta antes de instalar a versão comercial num restaurante real.
- [ ] Automatizar testes e análise estática e guardar os resultados de cada release.
- [ ] Definir monitorização, canal de suporte, prazo de resposta, atualizações, manutenção e procedimento de recuperação.
- [ ] Definir contrato, âmbito funcional, titularidade dos dados, privacidade, conservação e responsabilidades com aconselhamento adequado.

## Comercialização e preço

Os valores de referência atuais são 890 €, 1 490 € e 1 690 €. Ainda não constituem preços validados com clientes e o âmbito comercial tem de esclarecer pagamento único ou recorrente, alojamento, suporte, atualizações, configuração inicial e impostos aplicáveis. Não prometer uma funcionalidade marcada como pendente.
