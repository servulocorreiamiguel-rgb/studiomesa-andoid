# StudioMesa — lista de preparação para uso profissional

**Estado de lançamento nesta revisão (23/10/2026): não pronto para operação comercial real.**  
Usar esta lista para decidir o que pode ser demonstrado e o que ainda exige desenvolvimento/testes. Um botão presente na interface não prova que uma funcionalidade esteja completa.

### Resultados de verificação confirmados nesta revisão

- [x] GitHub Actions executou compilação, testes unitários, Android Lint, validação do caminho de assinatura comercial com uma chave temporária de CI e testes de interface no emulador Android 16. Execução: [run #247](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/39923900578).
- [x] Os relatórios XML dos testes de interface registaram **5 testes aprovados, 0 falhas e 3 casos ignorados intencionalmente** por variarem entre os modos de gestão e cliente. Isto é um smoke test de demonstração, não uma validação ponta a ponta do backend.
- [x] A consulta read-only ao PostgreSQL confirmou RLS ativa nas tabelas principais públicas e sem permissões de execução para `anon` nas funções inspecionadas. As funções públicas `SECURITY DEFINER` verificadas usam `SET search_path TO ''` e fazem referência a `auth.uid()`; falta validar isolamento com duas contas reais de restaurantes diferentes.
- [ ] Bloqueio de reprodutibilidade do backend: a base de dados ativa contém migrações de 19/10 que não estão no repositório; além disso, as primeiras três migrações locais têm versões diferentes das versões registadas no Supabase. A discrepância está registada em [issue #16](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/issues/16). Não executar `db push` nem editar a história remota até reconciliar e testar numa base de dados descartável.

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
- [x] Vista de cozinha separada para pedidos ativos, com tempo de espera, notas e avanço de estado; a fila ainda precisa de atualização manual.
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
- [ ] Testar concorrência, valores-limite, sessão expirada e isolamento entre restaurantes.
- [ ] Avisos automáticos, fornecedores/encomendas, consumo automático de ingredientes por receita, desperdício e inventário por local.

## Administração e equipa

- [x] Separação de contas, papéis owner/staff e código de adesão de equipa.
- [x] Proprietário pode consultar membros, remover o acesso de um funcionário e renovar automaticamente o código de adesão após a remoção.
- [ ] Permissões granulares por função; atualmente os funcionários têm permissões operacionais gerais.
- [ ] Suspensão temporária, horários/turnos e trilho de auditoria administrativo completo.
- [ ] Verificar em testes que a revogação é refletida em todos os dispositivos; ecrãs ativos podem continuar a mostrar dados já carregados até atualizar/reabrir.
- [ ] Testar que uma conta nunca consegue aceder a outro restaurante.

## Segurança e continuidade

- [x] RLS ativa nas tabelas principais do esquema público inspecionado.
- [x] Permissões de execução revogadas para os RPCs antigos não idempotentes de reservas, stock e pontos; a app atual usa as variantes com chaves de idempotência. Confirmado no catálogo PostgreSQL que os três endpoints antigos não são executáveis por `anon` nem por `authenticated`.
- [x] Retiradas permissões de tabela desnecessárias: `anon` já não tem acesso direto a carta, pedidos ou mesas; `authenticated` conserva apenas `SELECT`, `INSERT` e `UPDATE` nessas tabelas. RLS mantém-se ativa.
- [x] Índices únicos na base de dados impõem uma única associação de equipa e um único cartão de fidelização por conta (migração `20261021153243`), prevenindo associações duplicadas por pedidos concorrentes; as tabelas estavam vazias antes da aplicação.
- [x] O código de acesso da equipa é `NOT NULL` e continua protegido pelo índice único existente `restaurant_join_codes_staff_code_idx`. A migração corretiva `20261021154348` remove o índice duplicado criado temporariamente em `20261021154106`.
- [x] RLS ativada na tabela privada `private.restaurant_join_codes` (migração `20261021152901`). Não existem políticas de acesso direto; confirmado que `anon` e `authenticated` não têm privilégios diretos de leitura/escrita. As funções necessárias são propriedade de `postgres`, com `BYPASSRLS`. O aviso atual do Security Advisor é informativo: RLS ativa sem política, intencional para esta tabela privada.
- [x] As funções `SECURITY DEFINER` inspecionadas exigem sessão e as permissões de execução para `anon` estavam revogadas para os RPCs listados pelo Security Advisor.
- [ ] Proteção contra palavras-passe comprometidas no Supabase Auth. O alerta está ativo. A documentação oficial indica que a funcionalidade não está incluída no plano Free e está disponível no Pro ou superior; não atualizar o plano sem decisão explícita sobre o custo. Confirmar disponibilidade e custos na altura da ativação ([documentação oficial](https://supabase.com/docs/guides/auth/password-security), [planos](https://supabase.com/pricing)).
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
- [ ] Autorizar o URL de redirecionamento em Supabase Auth e testar envio/click de magic link com contas de teste.
- [ ] Publicar uma política de privacidade pública e configurar a secção Data safety na Play Console; confirmar exportação, conservação e resposta a incidentes.
- [ ] Não colocar chaves `service_role` ou `sb_secret_...` na aplicação. A chave `sb_publishable_...` incorporada no cliente é pública por natureza e depende de RLS/políticas corretas.

## Lançamento e suporte


- [x] Workflow atual compila APKs de demonstração/debug e executa testes unitários nas cinco variantes de distribuição.
- [x] Configuração usa compileSdk/targetSdk 36, AGP 8.10.0 e Gradle 8.11.1; a última execução verificada compilou e carregou os cinco APKs debug de demonstração.
- [x] Adicionado workflow manual que prepara cinco AABs comerciais assinados, exige confirmação explícita, executa testes e Lint e verifica as assinaturas.
- [ ] Escolher e validar o identificador base definitivo, criar o keystore privado, configurar os GitHub Actions Secrets/Variables e executar o workflow comercial com sucesso.
- [ ] Terminar os testes ponta a ponta antes de instalar a versão comercial num restaurante real.
- [x] Automatizados os controlos de separadores SQL e adicionada uma verificação CI de contratos mínimos para funções `SECURITY DEFINER`; a execução de cada commit candidato tem de passar.
- [ ] Expandir a cobertura com testes ponta a ponta do backend, incluindo isolamento entre restaurantes, autenticação, reservas, pedidos, stock, fidelização e eliminação de conta, e guardar os resultados de cada release.
- [ ] Definir monitorização, canal de suporte, prazo de resposta, atualizações, manutenção e procedimento de recuperação.
- [ ] Definir contrato, âmbito funcional, titularidade dos dados, privacidade, conservação e responsabilidades com aconselhamento adequado.

## Comercialização e preço

Os valores de referência atuais são 890 €, 1 490 € e 1 690 €. Ainda não constituem preços validados com clientes e o âmbito comercial tem de esclarecer pagamento único ou recorrente, alojamento, suporte, atualizações, configuração inicial e impostos aplicáveis. Não prometer uma funcionalidade marcada como pendente.
