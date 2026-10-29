# StudioMesa — configuração do backend e preparação para produção

> **Importante:** este documento substitui instruções antigas de configuração. O código e a base de dados evoluíram; não executes o ficheiro `supabase/schema.sql` no projeto ligado à aplicação sem antes comparar o esquema e validar uma migração. Esse ficheiro é um protótipo inicial da parte de fidelização e não representa, por si só, todo o esquema atualmente utilizado pela aplicação.

## Estado técnico

- A aplicação Android é nativa, escrita em Kotlin.
- A aplicação usa o cliente REST/Auth definido em `app/src/main/java/pt/studiomesa/app/SupabaseApi.kt`.
- O cliente contém o URL do projeto e uma chave Supabase publicável (`sb_publishable_...`). Uma chave publicável pode existir numa aplicação móvel; **não** é uma chave secreta.
- Nunca coloques em Kotlin, recursos Android, APKs, GitHub público ou nesta conversa chaves `sb_secret_...`, `service_role`, palavras-passe da base de dados ou credenciais privadas de OAuth.
- O backend tem tabelas de mesas, carta, pedidos, histórico de pedidos, equipa e fidelização. As regras RLS e permissões têm de ser revistas em conjunto com as funções SQL que as aplicações chamam.

## Idempotência das reservas (21/10/2026)

A tabela `public.restaurant_reservations` tem a coluna `client_request_id` e um índice único composto parcial (`restaurant_id`, `created_by`, `client_request_id`). A função `public.create_restaurant_reservation_idempotent` exige uma sessão, valida a pertença ao restaurante e devolve o ID existente quando recebe a mesma chave e o mesmo payload. Uma chave repetida com payload diferente é rejeitada. A app mantém a chave por conteúdo no formulário para que uma repetição após perda de resposta não crie outra reserva. A confirmação ponta a ponta com falha de rede ainda está pendente.

## Repetição segura de alterações de estado das reservas (21/10/2026)

A função `public.update_restaurant_reservation_status` devolve sucesso sem voltar a alterar a reserva quando o estado pedido já é o estado atual. Isto evita apresentar um erro quando o servidor confirmou uma alteração e a resposta se perdeu; as transições inválidas continuam a ser rejeitadas.

## Precisão dos valores de stock (21/10/2026)

A tabela de stock e o histórico de movimentos usam colunas `numeric(12,3)`. As funções `public.save_inventory_item` e `public.adjust_inventory_stock_idempotent` rejeitam valores com mais de três casas decimais, em vez de os arredondarem silenciosamente. A interface Android aplica a mesma validação. O caso de regressão `VAL-02` continua pendente de execução ponta a ponta num dispositivo real.

## Idempotência dos movimentos de stock e pontos (21/10/2026)

As tabelas `public.restaurant_inventory_movements` e `public.loyalty_transactions` têm a coluna `client_request_id` e índices únicos parciais compostos com restaurante e autor. As funções `public.adjust_inventory_stock_idempotent` e `public.adjust_loyalty_points_idempotent` exigem sessão, verificam a pertença ao restaurante e devolvem o resultado existente para reenvios idênticos. A reutilização de uma chave com dados diferentes é rejeitada. A app reutiliza o identificador enquanto o formulário mantém os mesmos dados; os testes reais de perda de rede continuam pendentes.

Os RPCs antigos não idempotentes `public.adjust_inventory_stock`, `public.adjust_loyalty_points` e `public.create_restaurant_reservation` já não são executáveis pelos papéis de cliente (`anon`/ `authenticated`). A aplicação deve usar as variantes idempotentes; versões antigas da demonstração podem receber erro de permissão nestas três operações, em vez de arriscar uma alteração duplicada.


A tabela `public.restaurant_reservations` tem a coluna `client_request_id` e um índice único composto parcial (`restaurant_id`, `created_by`, `client_request_id`). A função `public.create_restaurant_reservation_idempotent` exige uma sessão, valida a pertença ao restaurante e devolve o ID existente quando recebe a mesma chave e o mesmo payload. Uma chave repetida com payload diferente é rejeitada. A app mantém a chave por conteúdo no formulário para que uma repetição após perda de resposta não crie outra reserva. A confirmação ponta a ponta com falha de rede ainda está pendente.

## Criação idempotente de artigos de stock (22/10/2026)

A migração `20261022093532_inventory_item_creation_idempotency.sql` foi aplicada ao projeto Supabase `svwvkfsyevuyabelrtjk` e a sua presença no catálogo foi confirmada. Acrescenta o identificador e o payload original da criação de artigos de inventário e a função `public.create_inventory_item_idempotent`. A função exige sessão e pertença ao restaurante, valida a precisão decimal, devolve o mesmo ID para um reenvio com a mesma chave e os mesmos dados e rejeita a reutilização da chave com dados diferentes. O registo do artigo e do movimento inicial é transacional; uma repetição válida não acrescenta um segundo movimento.

A nova função tem `search_path` vazio, não pode ser executada por `anon` e concede execução a `authenticated` e `service_role`. A função é um endpoint privilegiado intencional e tem de continuar a ser revista como parte da auditoria de `SECURITY DEFINER`. A implementação Android está integrada em `main`; a CI atual reproduz as migrações e testa autorização/idempotência numa base descartável. Os testes ponta a ponta de perda real de rede e repetição com contas de teste continuam pendentes.

## Eliminação de conta e dados associados (22/10/2026)

A migração `20261022134124_account_deletion_support.sql` foi aplicada ao Supabase. A migração `20261022134453_index_account_deletion_queue.sql` cria o índice da fila temporária de pedidos de eliminação. A migração `20261022140116_account_deletion_concurrency_guard.sql` adiciona uma verificação de segurança na transação. A migração `20261022140721_account_deletion_lock_guard.sql` reforça-a bloqueando a linha do restaurante (`FOR UPDATE`) antes de verificar a equipa; assim, a verificação é serializada com adesões concorrentes e a eliminação é revertida se ainda houver membros associados. A Edge Function `delete-account` está implementada no repositório em `supabase/functions/delete-account/index.ts` e ativa no projeto.

O endpoint exige uma sessão de utilizador validada por `withSupabase({ auth: "user" })`; o cliente móvel nunca recebe uma chave privilegiada. O pedido de eliminação é preparado por `public.prepare_account_deletion`, com prazo de 15 minutos, e o endpoint só responde como concluído depois de o Auth Admin confirmar a eliminação do utilizador. Um proprietário tem de confirmar explicitamente a eliminação do restaurante e o servidor recusa o pedido enquanto houver outros membros da equipa. Quando a eliminação é aceite, o gatilho `z_studiomesa_finalize_account_deletion` apaga o restaurante sem membros e os dados operacionais em cascata. No caso de contas que não são proprietárias, as associações de fidelização são removidas e as referências de autoria que têm `ON DELETE SET NULL` são anonimizadas; os registos operacionais partilhados não são apagados por defeito.

Verificações efetuadas depois das migrações: fila sem pedidos pendentes, coluna `loyalty_transactions.created_by` nullable, chave estrangeira com `ON DELETE SET NULL` e um gatilho de eliminação presente. **Ainda falta testar a experiência completa com contas descartáveis dedicadas** (conta sem restaurante, funcionário e proprietário sem outros membros). Não uses contas reais nem dados de clientes nos testes.

A página externa `https://svwvkfsyevuyabelrtjk.supabase.co/functions/v1/account-deletion-web` está ativa e foi verificada por carregamento público. A 29/10/2026, a função protegida `delete-account` foi atualizada no projeto ativo para a versão 3, com `verify_jwt=true` e dependência `npm:@supabase/server@1.9.2` fixada. Um pedido sem autenticação recebeu HTTP `401`; a página de eliminação continuou a carregar. Esta verificação não prova a entrega do email nem a eliminação efetiva. O fluxo de email ainda exige que o URL exato seja adicionado a Authentication → URL Configuration → Redirect URLs. Antes de produção, configura SMTP próprio e confirma magic link/OTP e eliminação com contas descartáveis; não considerar o recurso operacional até esses testes passarem.

## Correção de concorrência na adesão à equipa (26/10/2026)

A migração `20261026173330_fix_staff_join_concurrent_conflict_target` foi aplicada ao projeto Supabase e verificada no catálogo de migrações. Corrige `public.join_restaurant_staff(text)`: o código anterior usava o alvo de conflito da chave primária composta, embora a tabela também imponha uma restrição única por `user_id`. A função agora usa `ON CONFLICT (user_id) DO NOTHING` e volta a ler a associação vencedora antes de confirmar o resultado. O SQL foi validado numa transação revertida e a função foi testada com dados temporários também revertidos: adesão inicial, repetição no mesmo restaurante e rejeição de uma segunda associação a outro restaurante. Uma consulta posterior confirmou zero utilizadores/restaurantes/associações de teste persistentes. O teste não substitui uma prova de concorrência real com dois pedidos simultâneos.

**Reconciliação de migrações verificada em 29/10/2026:** o catálogo do projeto Supabase ativo e o repositório estão alinhados em 40 migrações. A migração de permissões predefinidas está aplicada e registada como `20261029133656` (ver secção abaixo). Não usar `supabase/schema.sql` como substituto das migrações, porque é apenas um protótipo antigo; depois de qualquer novo deploy, confirmar de novo `supabase migration list` para detetar divergências.

## Estado verificado do backend (29/10/2026)

- `private.restaurant_join_codes` tem RLS ativa, sem políticas para acesso direto; as contas `anon` e `authenticated` não têm permissões diretas de leitura/escrita. As funções proprietárias de `postgres` foram mantidas e o utilizador `postgres` tem `BYPASSRLS`, para permitir os fluxos `SECURITY DEFINER` que fazem verificações explícitas.
- Os índices `restaurant_staff_one_restaurant_per_user_idx` e `loyalty_members_one_restaurant_per_user_idx` impõem a regra de uma associação por conta e protegem contra pedidos concorrentes.
- `menu_items`, `restaurant_orders` e `restaurant_tables` não concedem privilégios diretos a `anon`; `authenticated` tem apenas `SELECT`, `INSERT` e `UPDATE`. A RLS continua a limitar as linhas ao restaurante autorizado.
- O índice único existente `restaurant_join_codes_staff_code_idx` garante que o código de equipa identifica no máximo um restaurante; a migração `20261021154348_drop_redundant_staff_code_index.sql` remove o índice equivalente que tinha sido criado pela migração anterior. Os índices de chave estrangeira para stock, movimentos e reservas reduzem verificações caras à medida que os dados crescem.
- A organização Supabase está atualmente no plano Free e a proteção de palavras-passe comprometidas continua desativada. A [documentação oficial do Supabase](https://supabase.com/docs/guides/auth/password-security) indica que esta proteção só está disponível no plano Pro ou superior. Não foi alterado o plano nem assumido qualquer custo. **Antes de produção, esta limitação tem de ser resolvida por uma decisão explícita do responsável e uma revisão de segurança; não marcar o aviso como resolvido enquanto a opção continuar indisponível.**
- A alteração do Supabase que deixa de conceder acesso ao Data API por defeito a novas tabelas já está preparada antes da data de aplicação a todos os projetos existentes, **30/10/2026** ([aviso oficial](https://supabase.com/changelog/45329-breaking-change-tables-not-exposed-to-data-and-graphql-api-automatically)). A migração `20261029133656_revoke_postgres_default_public_privileges.sql` foi aplicada a 29/10/2026. A consulta de leitura confirmou que as predefinições de objetos novos em `public` não concedem privilégios a `anon`, `authenticated` ou `service_role`, nem execução a `PUBLIC`; as 11 tabelas públicas existentes têm RLS ativa e pelo menos uma política cada. Os privilégios e dados dos objetos existentes não foram alterados pela migração. O CI inclui teste com tabelas, sequência e função descartáveis para verificar estas predefinições.
- O Security Advisor ainda assinala 19 funções `SECURITY DEFINER` executáveis por `authenticated`. A consulta de leitura confirmou que o acesso `anon` está revogado nessas funções e que todas referenciam `auth.uid()`; o aviso continua a exigir revisão individual e testes ponta a ponta de autorização por restaurante.
- As tabelas privadas `private.account_deletion_queue` e `private.restaurant_join_codes` têm RLS sem políticas diretas, e os papéis de cliente não têm privilégios diretos sobre elas; este bloqueio de acesso direto é intencional e deve continuar coberto por testes.

- As alterações estão registadas nas migrações `20261021152901_enable_rls_private_restaurant_join_codes.sql`, `20261021153243_enforce_one_restaurant_per_user.sql`, `20261021154007_index_inventory_reservation_foreign_keys.sql`, `20261021154106_unique_staff_access_code.sql`, `20261021154348_drop_redundant_staff_code_index.sql`, `20261021154559_restrict_direct_table_privileges.sql`, `20261021160028_reservation_idempotency.sql`, `20261021160835_inventory_and_loyalty_idempotency.sql`, `20261021161858_disable_legacy_non_idempotent_adjustment_rpcs.sql`, `20261021162325_idempotent_reservation_status_retries.sql` e `20261021162652_stock_precision_validation.sql`. Os fluxos legítimos continuam pendentes de testes ponta a ponta com contas de teste.

## Antes de alterar o esquema

1. Confirma que estás no projeto Supabase correto.
2. Revê o SQL exato numa branch de desenvolvimento, nunca diretamente em produção.
3. Confirma que cada tabela exposta tem RLS e políticas alinhadas com o papel do utilizador e com o restaurante.
4. Revê as funções `SECURITY DEFINER`: devem verificar `auth.uid()`, autorizações e `search_path`, e o direito `EXECUTE` deve ser concedido apenas aos papéis necessários.
5. Não atives RLS numa tabela privada sem compreender os acessos das funções que a utilizam. RLS sem políticas bloqueia o acesso direto à tabela; isso pode ser correto para uma tabela privada, mas deve ser confirmado com testes dos fluxos legítimos.
6. Depois de cada alteração, volta a executar os Security Advisors e testa os fluxos com contas de proprietário, funcionário, cliente e utilizador não autenticado.

## Configuração de autenticação

- Email/palavra-passe: confirma as regras de palavras-passe, a confirmação de email, o fornecedor de envio de emails e o URL de retorno autorizado para recuperação.
- O código Android passou a usar PKCE: o verificador é gerado aleatoriamente, guardado cifrado no Android Keystore e usado para trocar o código de retorno por uma sessão. O callback já não aceita tokens bearer diretamente na ligação.
- Google e Microsoft só devem ser apresentados como métodos operacionais depois de configurares as credenciais OAuth reais no Supabase, autorizares o callback e testares o fluxo completo num telemóvel real.
- Não partilhes palavras-passe entre proprietário e funcionários. Cada pessoa deve ter a sua própria conta.
- A proteção contra palavras-passe comprometidas está desativada e, no plano Free atual, a documentação do Supabase indica que a opção exige Pro ou superior. Resolve esta limitação por decisão explícita antes de aceitar contas reais; revê também limites de pedidos e monitorização.

## Cópias de segurança e recuperação

A aplicação não substitui as cópias de segurança do fornecedor da base de dados. Antes de usar dados reais:

- Confirma no painel do Supabase o plano, retenção e frequência de backups efetivamente disponíveis.
- Define quem pode solicitar um restauro e qual o objetivo aceitável de perda de dados.
- Faz um restauro de teste num ambiente separado e confirma mesas, carta, pedidos, histórico e pontos.
- Documenta exportação, eliminação e conservação de dados; não guardes dados de clientes para além do necessário.

Não se considera um backup verificado até existir evidência de um restauro testado.

## Testes obrigatórios antes da venda

- Compilar todas as variantes Android e instalar os APKs num dispositivo real.
- Testar registo, login, renovação e expiração de sessão, recuperação de palavra-passe e logout.
- Testar pedidos e alterações concorrentes em dois dispositivos, incluindo perda de internet durante uma gravação.
- Testar que uma conta de um restaurante não consegue consultar ou modificar dados de outro.
- Testar códigos de cliente/equipa inválidos, tentativas repetidas e renovação de códigos.
- Confirmar que a aplicação de cliente nunca recebe custos internos, saldos de outros clientes ou dados da equipa.
- Confirmar permissões e papéis de funcionário/proprietário, cópias de segurança e o procedimento de recuperação.
- Gerar uma versão de produção assinada com uma chave privada guardada fora do repositório. O workflow normal continua a gerar APKs de demonstração/debug; foi adicionado um workflow manual para AABs assinados, documentado em `RELEASE-SETUP.md`, que ainda exige identificador de pacote final, keystore privado e segredos de GitHub configurados.

## Limitações conhecidas

A lista exata de funcionalidades por plano está no `README.md`. A existência de um botão ou de uma tabela não prova que a funcionalidade está concluída. Reservas, gestão granular de funcionários, integrações POS/caixa, pagamentos, faturação, impressoras e recuperação comprovada devem ser considerados por implementar ou validar até existirem testes e evidências específicas.

**Não utilizar esta aplicação para operar um restaurante real nem introduzir dados pessoais reais até a revisão de segurança, os testes funcionais e o restauro de backups estarem concluídos.**


## Reforço das permissões predefinidas (29/10/2026)

A migração `20261029133656_revoke_postgres_default_public_privileges.sql` foi aplicada ao projeto Supabase ativo depois de comparar os privilégios predefinidos reais com o repositório. A verificação de `pg_default_acl` confirmou que, em `public`, os novos objetos criados por `postgres` já não recebem privilégios por defeito para `anon`, `authenticated` ou `service_role`; as funções também já não recebem execução por defeito de `PUBLIC`. Os valores predefinidos do esquema `storage` não foram alterados por esta migração.

A migração afeta apenas objetos criados no futuro; não revoga permissões de objetos existentes e não modifica dados. As permissões atuais, as políticas RLS e a autorização de cada função continuam a exigir revisão independente.
