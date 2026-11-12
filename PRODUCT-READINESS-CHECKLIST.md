# StudioMesa — lista de preparação para uso profissional

**Estado atualizado em 12/11/2026: NÃO LIBERADA para venda operacional, piloto com dados reais ou produção.** A Mega IA da PR #113 já está integrada na `main` (merge commit `94a41413336678c9078fef9e21e0c4951cb8edaf`). Na CI da `main` [43186787267](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/43186787267), passaram os quatro jobs: compilação das cinco variantes, testes unitários, Android Lint, configuração de release, replay PostgreSQL/autorização e smoke tests Android 8 e Android 16. Foram publicados cinco APKs **debug/demo**, sem assinatura comercial definitiva.

A fotografia detalhada mais recente está em [docs/COMMERCIAL-CLOSEOUT-AUDIT-2026-11-10.md](docs/COMMERCIAL-CLOSEOUT-AUDIT-2026-11-10.md). Esta secção prevalece sobre os registos históricos abaixo.

## Atualização técnica de 12/11/2026

- [x] PR #113 integrada na `main`: assistente único com roteamento online para DeepSeek R1, Cohere North Mini Code, Qwen3.8 27B `:free`, GPT-OSS 20B `:free` e OpenRouter Free Router; GPT-OSS 120B não é usado no caminho gratuito.
- [x] PR #114 integrada: a reserva LFM2.5 pode ser novamente verificada/reparada mesmo quando o ficheiro existe com tamanho válido; a validação SHA-256 decide se é necessário voltar a descarregar. CI da PR passou nos quatro jobs (43200057302).
- [x] PR #115 integrada: documentação corrigida para 63 versões remotas correspondentes a 68 ficheiros locais, com cinco migrações locais por validar.
- [x] PR #116 integrada: os RPCs de permissões granulares não são chamados enquanto o gate está desligado; a cache de funcionários é limpa em falha de autorização; a criação de pedidos exige as permissões correspondentes no cliente e na política PostgreSQL. CI da PR passou nos quatro jobs, incluindo Android 8 e 16 (43205612141).
- [ ] CI de pós-merge da `main` após PR #116: execução 43209335761 ainda em curso na última verificação.
- [ ] O gate `GRANULAR_STAFF_PERMISSIONS_ENABLED` e o gate `RECIPE_MANAGEMENT_ENABLED` permanecem `false`. Só os ativar depois de aplicar/testar as migrações na branch Supabase de desenvolvimento e validar em duas contas/restaurantes.
- [x] IA local Android: Gemma 4 E2B como motor preferido em equipamentos com filtro inicial de RAM/espaço suficiente; LFM2.5-1.2B INT4 como alternativa local e reserva instalável (736 MB), com validação SHA-256.
- [x] O router exige JWT na configuração versionada e autentica o papel `owner`; a chamada online está desativada por omissão no build.
- [x] Edge Function `studiomesa-ai-router` publicada no Supabase alojado, versão 1, com `verify_jwt=true`.
- [ ] Confirmar/configurar a secret de servidor `OPENROUTER_API_KEY` e testar chamadas autenticadas de owner; a presença do segredo não foi confirmada nesta verificação. Manter cloud desligada até passar esses testes.
- [ ] Testes físicos de Gemma/LFM no OPPO A5m e outros Android: carregamento, qualidade, latência, RAM, temperatura, bateria, encerramento e fallback.
- [ ] O modo gratuito online tem quotas partilhadas pelo serviço/projeto e pode ficar indisponível; não prometer IA online ilimitada.
- [x] Foram publicados cinco APKs debug/demo na execução [43186787267](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/43186787267); não são pacotes comerciais assinados.

Estado detalhado e bloqueios: [atualização técnica de 12/11/2026](docs/COMMERCIAL-READINESS-UPDATE-2026-11-12.md).

- [x] Cinco variantes Android configuradas; compileSdk/targetSdk 36, minSdk 24, Java 17 e versão 1.5.0.
- [x] CI da `main` após integração da Mega IA passou os quatro jobs (43186787267): compilação/testes/Lint, replay PostgreSQL/autorização e smoke tests Android 8 e Android 16.
- [x] Os botões Google/Microsoft estão ocultos por defeito; a implementação OAuth/PKCE foi preservada e adiada até depois da primeira venda.
- [x] Projeto Supabase `ACTIVE_HEALTHY`, PostgreSQL 17.6.1.166, região eu-west-1; 26 tabelas com RLS ativo.
- [x] Verificação SQL read-only: as 11 tabelas RLS sem políticas não concedem SELECT/INSERT/UPDATE/DELETE a `anon` nem `authenticated`.
- [x] Verificação SQL read-only: 47 funções `SECURITY DEFINER`; 42 executáveis por `authenticated`, zero por `anon`; todas referenciam `auth.uid()` e têm `search_path = ''`. A revisão função a função continua obrigatória.
- [x] Comparação read-only de 12/11/2026: 63 versões no histórico remoto e 68 ficheiros SQL locais. Todas as 63 versões remotas têm ficheiro local com a mesma versão e nome; não há versões remotas sem ficheiro local. Existem cinco migrações locais ainda não aplicadas no projeto alojado:
  - `20261030111815_restaurant_recipes_and_order_stock_consumption.sql`
  - `20261102103000_granular_restaurant_staff_permissions.sql`
  - `20261102120000_require_loyalty_permission_for_reward_fulfillment.sql`
  - `20261102121000_enforce_loyalty_permission_for_staff_loyalty_operations.sql`
  - `20261104100000_price_adviser_actor_indexes.sql`
  A comparação confirmou versões/nomes, não igualdade integral do SQL histórico remoto.
- [x] Replay das 68 migrações numa base PostgreSQL isolada e testes de autorização/idempotência entre restaurantes passaram no job de CI da PR #114 ([execução 43200057302](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/43200057302)).
- [ ] Aplicar as cinco migrações apenas numa branch Supabase de desenvolvimento, testar schema/RLS/RPCs e verificar advisors. A branch custa 0,01344 USD/hora; ainda não foi criada à espera de aprovação do custo. Não executar `db push`, `migration repair`, reset ou DDL na produção antes de backup/restauro comprovados.
- [ ] Criar, reconciliar e testar as tabelas/RPCs ausentes: `public.restaurant_recipes`, `public.restaurant_recipe_ingredients` e `private.restaurant_staff_permissions`.
- [ ] Rever individualmente as 42 funções `SECURITY DEFINER`, com testes de isolamento entre restaurantes e papéis. Os avisos do Advisor não devem ser resolvidos por revogações em bloco.
- [ ] Resolver a proteção contra palavras-passe comprometidas desativada, depois de confirmar custo/plano.
- [ ] Configurar SMTP próprio e confirmar email; testar registo, recuperação, sessão, logout e eliminação com contas descartáveis.
- [x] Primeira versão sem Google/Microsoft; OAuth social explicitamente adiado para depois da primeira venda.
- [ ] Testar E2E pedidos, reservas, cozinha, stock, fidelização, campanhas, faturas, sincronização, perda de rede e isolamento multi-restaurante.
- [ ] Testar fisicamente no OPPO A5m e noutros Android o modelo local, instalação, desempenho, bateria, temperatura e atualização.
- [ ] Publicar/validar apenas os serviços cloud necessários. A Edge Function `studiomesa-assistant` devolve HTTP 410; a IA avançada não está publicada; a pesquisa Web depende de `SEARXNG_BASE_URL` configurado.
- [ ] Finalizar política de privacidade, termos, DPA, subcontratantes, retenção, eliminação, Data Safety, SBOM, licenças e suporte com a entidade responsável.
- [ ] Escolher package IDs finais, guardar keystore comercial com backup seguro, configurar secrets/variables e gerar AAB/APKs assinados. Os artefactos regulares de CI são debug/demo.
- [ ] Validar a release assinada, checksums, atualização e distribuição final.

**Não anunciar como funcionalidades comerciais já disponíveis:** fichas técnicas/consumo automático de ingredientes; permissões granulares de funcionários; IA cloud GPT-OSS; pesquisa Web até configurar/testar o motor; integração POS, pagamentos, impressoras, faturação, SAF-T ou contabilidade. O saldo de fidelização continua parcialmente manual. As previsões baseadas em pedidos não representam recebimentos bancários nem faturação contabilística.

**Limites da IA:** a versão integrada suporta Gemma 4 E2B local e LFM2.5-1.2B INT4 como alternativa/reserva; ainda exige testes físicos e revisão das licenças. O router cloud multi-modelo está no código, mas permanece desligado até publicação da Edge Function, configuração da chave e validação de privacidade. Não é ChatGPT nem equivalente ao ChatGPT Free; OCR exige revisão humana.

**Regra de lançamento:** a StudioMesa pode ser demonstrada com dados fictícios. Não deve receber dados reais de clientes ou restaurantes nem ser vendida como solução operacional até todos os bloqueios acima estarem resolvidos e testados.

---

## Registos históricos — não usar como estado atual

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
- [ ] Definir e implementar permissões granulares para as restantes operações; atualmente os funcionários têm permissões operacionais gerais.
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
