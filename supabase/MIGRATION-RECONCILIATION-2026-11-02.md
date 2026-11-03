# Reconciliação do Supabase e das branches — 02/11/2026

## Estado observado

Consulta de leitura ao projeto Supabase `svwvkfsyevuyabelrtjk` e comparação com a branch `feature/customer-reservations`:

- Migrações registadas no projeto alojado: **54**.
- Ficheiros SQL locais nesta branch: **51**.
- Prefixos de versão presentes em ambos: **41**.
- Ficheiros locais sem versão correspondente no histórico remoto: **10**.
- Registos remotos sem ficheiro local com o mesmo prefixo: **13**.
- Não foi encontrada uma divergência de nome nos prefixos que coincidem; a divergência está nas versões que existem só de um lado.

A declaração de alinhamento de 29/10 em `SUPABASE-SETUP.md` é histórica e já não descreve o estado de 02/11.

## Ficheiros locais sem versão remota correspondente

| Versão local | Nome |
|---|---|
| `20261030120000` | `inventory_unit_cost_and_waste_snapshots` |
| `20261102102500` | `loyalty_invoice_qr` |
| `20261102104100` | `loyalty_invoice_qr_foreign_key_indexes` |
| `20261102104800` | `loyalty_invoice_qr_private_execution` |
| `20261102112000` | `loyalty_shop_schema_and_redemption_queue` |
| `20261102113000` | `loyalty_shop_private_function_boundary` |
| `20261102135000` | `legacy_loyalty_rpc_private_boundary` |
| `20261102141000` | `customer_reservation_flow_private_boundary` |
| `20261102150000` | `add_price_adviser_actor_fk_indexes` |
| `20261102160000` | `granular_staff_permissions` |

## Registos remotos sem ficheiro local com a mesma versão

| Versão remota | Nome |
|---|---|
| `20261102075735` | `loyalty_shop_and_single_use_qr` |
| `20261102080624` | `secure_loyalty_qr_only_awards_and_index_fks` |
| `20261102080905` | `consistent_loyalty_qr_lock_order` |
| `20261102081603` | `fix_loyalty_qr_token_cleanup_ambiguity` |
| `20261102082151` | `reconcile_inventory_unit_cost_and_waste_snapshots` |
| `20261102090849` | `customer_loyalty_redemption_history` |
| `20261102091511` | `customer_reservations_and_staff_table_assignment` |
| `20261102091833` | `idempotent_customer_reservation_and_table_confirmation` |
| `20261102103246` | `loyalty_invoice_qr` |
| `20261102103627` | `loyalty_invoice_qr_foreign_key_indexes` |
| `20261102104650` | `loyalty_invoice_qr_private_execution` |
| `20261102113719` | `loyalty_shop_git_reconciliation_and_redemption_queue` |
| `20261102113944` | `loyalty_shop_private_function_boundary` |

Alguns nomes indicam funcionalidades semelhantes (por exemplo, QR de fidelização, loja de recompensas e reservas), mas **não são prova de que os SQL sejam equivalentes**. A versão `20261030120000` e `20261102082151` também parecem tratar o mesmo tema de stock/custos, mas requerem comparação de definições, índices, funções e efeitos antes de qualquer reconciliação.

## Branches e pedidos de integração

- PR **#75** (`feature/loyalty-shop-qr-security`) é uma branch paralela baseada em `main`. Inclui QR de utilização única, reservas e permissões granulares de funcionários.
- PR **#76** (`feature/invoice-loyalty-qr`) também parte de `main`. Inclui QR de fidelização associado à fatura e loja de recompensas. Tem migrações com versões diferentes das que aparecem no histórico remoto.
- PR **#77** (`feature/customer-reservations`) está empilhada sobre #76. Além das reservas do cliente, a branch contém a loja/QR, permissões granulares da equipa, filtragem da navegação e revalidação da associação/matriz ao regressar à app.
- As três branches alteram ficheiros centrais como `MainActivity.kt`, `SupabaseApi.kt` e/ou o workflow Android. Não devem ser integradas em sequência sem uma revisão explícita das sobreposições.
- A implementação granular da PR #75 foi portada e adaptada em #77. A migração `20261102160000_granular_staff_permissions.sql` aplica as seis áreas no servidor, rejeita identificadores desconhecidos, repetidos e elementos NULL, e conserva os wrappers privados existentes. A UI só mostra os códigos de adesão e as ações de rotação ao proprietário.

## Alteração de desempenho acrescentada à branch #77

A auditoria Performance Advisor identificou duas chaves estrangeiras sem índice:

- `private.restaurant_price_adviser_audit.acted_by`
- `private.restaurant_price_adviser_settings.updated_by`

Foram acrescentadas as migrações `20261102150000_add_price_adviser_actor_fk_indexes.sql` (dois índices parciais) e `20261102160000_granular_staff_permissions.sql` (matriz de seis áreas e enforcement no servidor). O workflow verifica os dois índices e os objectos de permissões após replay limpo.

Validação da nova cadeia de permissões:
- A primeira tentativa de replay falhou porque `private.loyalty_qr_tokens` era usada pelo QR legado mas não era criada na sequência limpa local.
- A migração `20261102135000_legacy_loyalty_rpc_private_boundary.sql` foi corrigida para criar a tabela QR com `IF NOT EXISTS`, RLS, índices e privilégios directos revogados, preservando dados caso já exista.
- O workflow completo passou na execução [41481734799](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/41481734799), no commit `247ed883b514e514826e2ca56be4b7156d126a9b`: replay das 51 migrações locais desde base vazia, testes de autorização/idempotência PostgreSQL, compilação, unit tests de todas as variantes, Android Lint e smoke tests em Android 8 e Android 16.
- A execução seguinte [41495700617](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/41495700617) está em curso no commit `6c14e6d`. Inclui o teste de rejeição de elementos NULL na matriz e as verificações owner-only dos códigos. A verificação live da associação ao restaurante foi acrescentada depois desse commit (`3bb5d53)) e requer uma execução CI no head atual.
- O sucesso da CI de desenvolvimento não corrige a divergência de migrações do projeto alojado e não autoriza deploy.

## Estado de segurança observado no projeto alojado
> A migração `20261102160000_granular_staff_permissions.sql` e a recuperação versionada da tabela QR existem apenas na branch. Não foram aplicadas ao projecto Supabase alojado.

- As oito tabelas assinaladas pelo Advisor com RLS sem políticas não concedem privilégios diretos de leitura/escrita a `anon` ou `authenticated`. As duas tabelas públicas de recompensas/resgates são RPC-only; as tabelas privadas também permanecem sem acesso direto. Não criar políticas abertas para eliminar avisos informativos.
- A consulta de funções encontrou 31 funções públicas `SECURITY DEFINER` executáveis por `authenticated`, nenhuma executável por `anon`; todas tinham referência a `auth.uid()` e `search_path` vazio. Isto não substitui a revisão do corpo e do âmbito de cada função.
- O aviso de proteção de palavras-passe comprometidas continua ativo e deve ser reavaliado na configuração de Auth antes do lançamento.
- Os dois índices referidos acima ainda não foram aplicados ao projeto alojado.

## Plano de reconciliação

1. Manter o projeto alojado sem alterações de esquema enquanto se compara cada migração local-only/remote-only com o esquema e as funções efetivamente existentes.
2. Obter o diff das branches #75, #76 e #77 e decidir qual é a implementação canónica da loja, QR, reservas e permissões de equipa.
3. Reconciliar as versões de migração sem editar retroativamente SQL que já tenha sido aplicado. Para cada versão remota sem ficheiro, recuperar o SQL original do histórico Git/CI ou documentar uma baseline comprovada; para cada ficheiro local-only, determinar se é novo, duplicado ou uma alteração necessária.
4. Reproduzir a sequência final numa base PostgreSQL/Supabase descartável e correr testes de autorização, idempotência, concorrência, RLS e interface.
5. Rever a ordem e o plano de deploy. Não executar `supabase db push`, `supabase migration repair`, nem aplicar manualmente estas migrações até existir um plano reconciliado e revisto.
6. Só depois aplicar migrações aprovadas, confirmar o catálogo remoto e voltar a correr os Advisors.

**Não foi executada nenhuma alteração de esquema, dados, permissões ou histórico de migrações no projeto alojado durante esta revisão.**


## Recuperação dos originais remotos — verificação adicional

Foi feita uma pesquisa nas branches GitHub, sem alterar o projeto Supabase alojado. Oito dos 13 ficheiros que faltavam no histórico local foram recuperados da branch `feature/loyalty-shop-qr-security` (PR #75), com os seguintes blobs Git:

| Versão remota | Ficheiro recuperado | Blob SHA |
|---|---|---|
| `20261102075735` | `loyalty_shop_and_single_use_qr.sql` | `3fb81184634688f6bde3fdb9a4cb15965b44a4eb` |
| `20261102080624` | `secure_loyalty_qr_only_awards_and_index_fks.sql` | `8f16627cb43d58b4aa3d3164d71b7b5f0bcadfe8` |
| `20261102080905` | `consistent_loyalty_qr_lock_order.sql` | `a0db40f09655d747b924e3a1232504af768c73d2` |
| `20261102081603` | `fix_loyalty_qr_token_cleanup_ambiguity.sql` | `7dc75abb385dcf816963fc6d45a69e5ac937fb76` |
| `20261102082151` | `reconcile_inventory_unit_cost_and_waste_snapshots.sql` | `dd8574507d07b195384d60afca15ae535228457a` |
| `20261102090849` | `customer_loyalty_redemption_history.sql` | `c06820b8f1099a535a79fa3b997ae837e29eb27e` |
| `20261102091511` | `customer_reservations_and_staff_table_assignment.sql` | `f8dbc8a1700af33c0df2034e7d70a92f92da75d8` |
| `20261102091833` | `idempotent_customer_reservation_and_table_confirmation.sql` | `f64d85d0616b070698fb3d06670c46bccb8cfdcf` |

### Equivalência confirmada

O ficheiro remoto `20261102082151_reconcile_inventory_unit_cost_and_waste_snapshots.sql` tem o mesmo blob SHA do ficheiro local `20261030120000_inventory_unit_cost_and_waste_snapshots.sql`. É, portanto, conteúdo byte a byte idêntico, apesar de ter uma versão diferente no histórico alojado.

### Equivalências ainda não aprovadas

Os ficheiros de loja/QR recuperados partilham funções, tabelas e índices com as migrações locais `20261102112000`, `20261102113000` e `20261102135000`. As migrações remotas de reservas partilham funções com `20261102141000` e `20261102160000`. Esta sobreposição de objectos não prova equivalência semântica: as versões locais incluem alterações de segurança, wrappers privados e/ou lógica posterior. Não marcar estas versões como reconciliadas apenas por coincidência de nomes.

### Ficheiros ainda não recuperados

Continuam sem original encontrado nas branches consultadas os cinco registos remotos:

- `20261102103246_loyalty_invoice_qr`
- `20261102103627_loyalty_invoice_qr_foreign_key_indexes`
- `20261102104650_loyalty_invoice_qr_private_execution`
- `20261102113719_loyalty_shop_git_reconciliation_and_redemption_queue`
- `20261102113944_loyalty_shop_private_function_boundary`

Existem candidatos locais com versões e nomes próximos (`20261102102500`, `20261102104100`, `20261102104800`, `20261102112000` e `20261102113000`), mas não são cópias históricas dos cinco ficheiros remotos. É necessário comparar cada definição com o estado actual do catálogo PostgreSQL e com as funções e tabelas do projecto antes de decidir uma baseline ou uma sequência de migrações.

**Decisão mantida:** não executar `db push`, `migration repair`, `apply_migration` ou qualquer alteração ao histórico/schema de produção até a reconciliação integral estar documentada, revista e reproduzida numa base descartável.


## Fotografia do projecto alojado — 02/11/2026, 20:25 UTC

Leitura feita directamente ao projecto Supabase `svwvkfsyevuyabelrtjk`. Foram executadas apenas consultas de leitura.

### Diferenças relevantes face à branch `feature/customer-reservations`

- O histórico alojado tem **54 versões**, terminando em `20261102113944`.
- A tabela `private.restaurant_staff_permissions` **não existe** no projecto alojado. As políticas activas para Mesas, Carta, Pedidos, Reservas, Membros e Transacções de Fidelização continuam a usar `private.is_restaurant_staff`, sem a matriz granular por área.
- A migração local `20261102150000_add_price_adviser_actor_fk_indexes.sql` ainda não está aplicada: o Performance Advisor continua a assinalar as FKs `restaurant_price_adviser_audit.acted_by` e `restaurant_price_adviser_settings.updated_by` sem índices.
- As tabelas `private.loyalty_invoice_qr_codes`, `private.loyalty_qr_tokens`, `public.loyalty_rewards`, `public.loyalty_reward_redemptions` e `public.restaurant_reservations` existem no projecto. Foram encontrados os índices de QR, idempotência e reservas esperados.
- `public.loyalty_rewards` e `public.loyalty_reward_redemptions` não têm políticas RLS, mas também não concedem SELECT/INSERT directos a `anon` ou `authenticated`. Este desenho RPC-only é intencional; não adicionar políticas abertas só para remover o aviso informativo do Advisor.

### Security Advisor — leitura do catálogo

Foram encontradas **36** funções `SECURITY DEFINER` no schema `public`; **31** podem ser executadas por `authenticated`, correspondendo ao aviso do Advisor. Nas 36 funções verificadas:

- nenhuma permite EXECUTE a `anon`;
- nenhuma permite EXECUTE a `PUBLIC`;
- todas referenciam `auth.uid()`;
- todas fixam `search_path` vazio.

Isto reduz a superfície anónima, mas não prova por si só que cada RPC esteja correctamente limitada ao restaurante, titular, papel e operação. Os 31 avisos têm de ser revistos função a função; não revogar em bloco nem converter indiscriminadamente para `SECURITY INVOKER`, porque algumas operações precisam de atravessar tabelas privadas/RLS e já têm validação própria.

O Security Advisor mantém também o aviso de **Leaked Password Protection Disabled**. Esta opção requer alteração nas definições de Auth do projecto e continua por tratar.

### Consequência para a preparação comercial

A aplicação Android e a branch de desenvolvimento têm uma implementação de permissões mais recente do que a base alojada. Até reconciliar e aplicar a migração aprovada, não anunciar permissões individuais de funcionários como já activas em produção. A integração da PR #77 e o plano de migração continuam bloqueados pela reconciliação do histórico, não pela compilação Android.


## Comparação por estado de catálogo — QR de fatura e loja de recompensas

A comparação seguinte usa as definições efectivas do PostgreSQL alojado, não apenas nomes de ficheiros.

### QR de fidelização associado à fatura

O catálogo alojado apresenta o estado esperado pelas migrações locais:

- A tabela `private.loyalty_invoice_qr_codes` tem as colunas, chaves estrangeiras, restrições de validade, unicidade da referência e idempotência descritas em `20261102102500_loyalty_invoice_qr.sql`.
- Os índices `loyalty_invoice_qr_member_idx`, `loyalty_invoice_qr_expiry_idx`, `loyalty_invoice_qr_issued_by_idx` e `loyalty_invoice_qr_redeemed_by_idx` existem.
- As funções privilegiadas estão em `private.issue_loyalty_invoice_qr_internal` e `private.redeem_loyalty_invoice_qr_internal`, com `SECURITY DEFINER`, `search_path` vazio e execução autenticada.
- As funções públicas `issue_loyalty_invoice_qr` e `redeem_loyalty_invoice_qr` são wrappers SQL `SECURITY INVOKER`; `anon` não tem execução.

O estado final corresponde à sequência local `20261102102500` + `20261102104100` + `20261102104800`. Isto é uma equivalência do estado pós-migração observável, não prova que os três ficheiros sejam os mesmos SQL históricos dos registos remotos `20261102103246`, `20261102103627` e `20261102104650`. Não alterar o histórico só com base nesta correspondência.

### Loja de recompensas

O catálogo alojado também apresenta o estado estrutural e funcional descrito pelas migrações locais `20261102112000` e `20261102113000`:

- As tabelas `public.loyalty_rewards` e `public.loyalty_reward_redemptions` existem com restrições de stock, pontos, estado e chave de idempotência.
- Os cinco índices de loja/resgates previstos em `20261102112000` existem.
- As implementações privilegiadas da loja estão em funções `private.*_internal`; as funções públicas equivalentes são wrappers `SECURITY INVOKER`.
- As tabelas de recompensas não concedem leitura ou escrita directa a `anon` ou `authenticated`; o acesso é feito pelas RPCs.

Isto torna `20261102112000` e `20261102113000` candidatos fortes para reconciliar o estado final dos registos remotos `20261102113719` e `20261102113944`, mas não substitui a recuperação/comparação dos SQL originais nem autoriza reparar versões.

### QR legado e reservas: diferenças ainda activas

O estado alojado ainda contém endpoints antigos `SECURITY DEFINER` no schema `public`:

- `create_loyalty_qr_token()`
- `award_loyalty_points_from_qr(uuid, integer, uuid, text)`
- `get_restaurant_reward_redemptions(uuid)`
- As RPCs operacionais de reservas, incluindo `create_customer_reservation_request`, `cancel_my_restaurant_reservation`, `assign_restaurant_reservation_table`, `get_my_restaurant_reservations` e `update_restaurant_reservation_status`.

Na leitura efectuada, estes endpoints não podem ser executados por `anon` ou `PUBLIC`, exigem autenticação e têm verificações de utilizador/restaurante no corpo. Contudo, continuam a ser funções privilegiadas directamente expostas no schema público e não aplicam a futura matriz granular de permissões.

A migração local `20261102135000_legacy_loyalty_rpc_private_boundary.sql` prepara a passagem dos três RPCs legados de QR/resgates para implementações privadas com wrappers públicos `SECURITY INVOKER`. A migração `20261102141000_customer_reservation_flow_private_boundary.sql` prepara a mesma separação para o fluxo de reservas. A migração `20261102160000_granular_restaurant_staff_permissions.sql` introduz a matriz de seis áreas e substitui as políticas antigas por verificações de permissão. Nenhuma destas três migrações está aplicada ao projecto alojado.

**Classificação actual:** o QR de fatura e a loja de recompensas apresentam correspondência forte no estado final com os candidatos locais; QR legado, reservas e permissões granulares continuam a precisar da sequência local posterior. Não executar reparação do histórico nem deploy até a sequência completa ser reproduzida e revista numa base descartável.


## Revisão por grupos das 31 RPCs SECURITY DEFINER — 02/11/2026

Foi extraída a lista completa das 31 funções públicas `SECURITY DEFINER` executáveis por `authenticated` e verificada a presença dos controlos no corpo das funções. Resultado da revisão estática:

| Grupo de operações | Controlos observados no código alojado | Limite actual |
|---|---|---|
| Preços e consultor económico | As três operações de consulta/activação e aplicação verificam o papel `owner` do utilizador na associação ao restaurante | O consultor continua reservado ao proprietário; manter assim |
| Equipa e códigos | Lista de equipa, remoção de funcionário e rotação de códigos verificam o proprietário; detalhes devolvem os códigos apenas ao proprietário | Não permitir que funcionários vejam ou rodem códigos |
| Stock | As RPCs de criação, edição e ajuste verificam sessão e pertença ao restaurante indicado | Ainda não distinguem a permissão `inventory` da matriz por funcionário |
| Pedidos e reservas da equipa | Atribuição de mesa, criação idempotente de reserva e alteração de estado verificam sessão e pertença ao restaurante | Ainda não distinguem `orders`, `tables` e `reservations` |
| Fidelização da equipa | Atribuição por QR e consulta da fila de resgates verificam pertença ao restaurante; o registo de QR do cliente está ligado ao cartão da própria conta | Ainda não distinguem a permissão `loyalty`; os RPCs legados continuam em `public` |
| Reservas do cliente | Criação, consulta e cancelamento estão limitados ao restaurante associado ao cartão e/ou a reservas com `created_by = auth.uid()` | A implementação alojada continua em funções públicas `SECURITY DEFINER`; a migração local 141000 prepara wrappers `SECURITY INVOKER` |
| Conta e adesão | Cancelamento da eliminação apaga apenas a fila da própria conta; criar restaurante bloqueia segunda associação; adesão de cliente/equipa usa códigos e associa a conta autenticada | Continuar a testar concorrência e abuso de códigos |

### Resultado e limites

- Nas 31 funções: execução por `anon` = **0**; execução por `PUBLIC` = **0**; todas usam `search_path` vazio e referenciam `auth.uid()`.
- A amostra de corpos consultada confirma controlos de proprietário, pertença ao restaurante ou titularidade do cartão/reserva, consoante a operação.
- Não foi identificado, nesta passagem estática, um caminho óbvio em que um utilizador anónimo consiga invocar estas RPCs ou escolher livremente os dados de outra conta.
- Isto não é um teste de penetração nem substitui testes adversariais de cada função/argumento. Os testes PostgreSQL em CI cobrem cenários importantes de isolamento, idempotência e revogação; a matriz granular ainda não está activa no projecto alojado.
- O aviso do Advisor é, portanto, parcialmente estrutural: as funções são realmente endpoints privilegiados para utilizadores autenticados, mas não estão abertas a `anon`/`PUBLIC`. Não revogar em bloco. A prioridade é migrar os endpoints legados para wrappers `SECURITY INVOKER`, activar a matriz granular de permissões e manter os testes de autorização correspondentes.

**Estado:** revisão estática inicial concluída para os 31 nomes/assinaturas e controlos principais; revisão aprofundada de limites, condições de corrida e argumentos continua a exigir testes dedicados por função. Nenhuma alteração SQL foi aplicada ao Supabase alojado.


## Plano candidato para corrigir o histórico Supabase

A documentação actual do Supabase confirma que `migration repair` só altera `supabase_migrations.schema_migrations`: não executa nem desfaz SQL. Esta separação permite corrigir versões históricas quando o esquema já representa essas alterações. Referências: [Database Migrations](https://supabase.com/docs/guides/deployment/database-migrations) e [CLI migration repair](https://supabase.com/docs/reference/cli/supabase-migration-repair).

**Este é um plano de reconciliação, não uma autorização para o executar.** Antes de o usar, é obrigatório confirmar o projecto ligado, guardar o resultado de `supabase migration list`, ter um backup/restauro adequado e repetir a sequência numa cópia isolada do estado actual.

### A. Versões remotas candidatas a marcar como `reverted`

Os seguintes 13 registos existem no histórico alojado, mas não têm ficheiro com a mesma versão na branch. As alterações estruturais/funcionais correspondentes estão representadas por migrações locais posteriores ou por uma migração com o mesmo conteúdo. Marcar como `reverted` remove apenas os registos históricos; não remove nenhum objecto SQL.

```text
20261102075735
20261102080624
20261102080905
20261102081603
20261102082151
20261102090849
20261102091511
20261102091833
20261102103246
20261102103627
20261102104650
20261102113719
20261102113944
```

### B. Versões locais candidatas a marcar como `applied` sem executar SQL

A existência das estruturas correspondentes foi confirmada no catálogo alojado: QR de fatura, índices de QR, wrappers privados do QR de fatura, loja de recompensas, fila de resgates e wrappers privados da loja. O ficheiro de inventário abaixo tem ainda o mesmo blob SHA da versão remota `20261102082151`.

```text
20261030120000
20261102102500
20261102104100
20261102104800
20261102112000
20261102113000
```

### C. Migrações locais que devem continuar pendentes para aplicação real

Estas quatro versões correspondem a alterações que **não** existem ainda no esquema alojado:

```text
20261102135000  legacy_loyalty_rpc_private_boundary
20261102141000  customer_reservation_flow_private_boundary
20261102150000  add_price_adviser_actor_fk_indexes
20261102160000  granular_restaurant_staff_permissions
```

Confirmações específicas feitas no servidor:

- A migração `135000` é necessária para mover RPCs legados de fidelização para funções privadas; as implementações legadas ainda aparecem em `public`.
- A migração `141000` cria wrappers `SECURITY INVOKER` para os fluxos de reserva; as funções públicas actuais ainda aparecem como `SECURITY DEFINER`.
- A migração `150000` cria os dois índices de chaves estrangeiras que ainda aparecem em falta no Performance Advisor.
- A migração `160000` cria `private.restaurant_staff_permissions` e actualiza políticas/RPCs para as seis áreas; essa tabela ainda não existe no alojamento.

### D. Sequência de comandos para uma futura janela de manutenção

Executar primeiro apenas no ambiente isolado e rever a saída de cada comando. A sintaxe abaixo segue a CLI documentada; os comandos de reparação não executam SQL.

```bash
supabase migration list

supabase migration repair \
  20261102075735 20261102080624 20261102080905 20261102081603 \
  20261102082151 20261102090849 20261102091511 20261102091833 \
  20261102103246 20261102103627 20261102104650 20261102113719 \
  20261102113944 --status reverted

supabase migration repair \
  20261030120000 20261102102500 20261102104100 \
  20261102104800 20261102112000 20261102113000 --status applied

supabase migration list
supabase db push --dry-run
```

A saída do último comando deve propor apenas as quatro migrações da secção C. Se aparecer outra versão, parar e reconciliar de novo; não avançar por tentativa.

Apenas depois de um replay isolado do estado actual, revisão SQL, backup verificado, teste de restauração e aprovação da janela de manutenção se poderá executar `supabase db push` no projecto real. Depois, voltar a consultar migrações e Advisors e testar com contas descartáveis de proprietário, funcionário e cliente.

**Não executar `db reset --linked` no projecto real.** Esse comando apaga o esquema remoto e é reservado a ambientes descartáveis.

### E. CI da documentação

O commit `56ececd8e0a060152fb922810eb74e2c5e677dd6` foi validado pela execução [41520562311](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/41520562311): replay de migrações, testes de autorização/idempotência, compilação, testes unitários, Android Lint e smoke tests nos emuladores Android 8 e 16 concluíram com sucesso. Esta validação não aplica migrações ao alojamento nem substitui o teste de replay contra uma cópia do seu estado actual.


---

## Verificação adicional do estado alojado — 02/11/2026, 21:22 UTC

Esta passagem foi feita com consultas SQL apenas de leitura e com os Advisors. Não foi executado DDL, reparação do histórico, alteração de privilégios ou deploy no projecto alojado.

### Estado real das permissões por funcionário

- `private.restaurant_staff_permissions` continua inexistente no alojamento.
- As nove políticas relevantes verificadas em `restaurant_tables`, `menu_items`, `restaurant_orders`, `restaurant_order_events`, `restaurant_inventory_items`, `restaurant_inventory_movements`, `restaurant_reservations`, `loyalty_members` e `loyalty_transactions` ainda usam `private.is_restaurant_staff` para o acesso operacional da equipa. A matriz de seis áreas da migração `20261102160000` ainda não está activa.
- A tabela privada futura deve continuar sem privilégios directos para `anon` ou `authenticated`; o acesso deve ser feito apenas através das RPCs de proprietário e da função de verificação.
- A tabela de reservas já contém `created_by` e `client_request_id`, e já existe o índice único parcial `(restaurant_id, created_by, client_request_id)`. Os índices de reserva por restaurante/mesa/horário também existem. Isto confirma os pré-requisitos estruturais observados para a migração `141000`.
- O código Android da branch consulta novamente a matriz quando o funcionário regressa à app, fecha as áreas enquanto valida a sessão e remove caches das áreas revogadas. A autorização efectiva continua dependente das verificações de servidor/RLS; a interface não é tratada como fronteira de segurança.

### Advisors na mesma verificação

| Aviso | Quantidade | Leitura |
|---|---:|---|
| RLS activo sem políticas | 8 INFO | Não adicionar políticas permissivas só para eliminar o aviso: as tabelas verificadas não dão acesso directo às roles de API e são acedidas por funções controladas |
| SECURITY DEFINER executável por authenticated | 31 WARN | Rever por função e manter testes de autorização; a execução por `anon` não foi observada nas funções da aplicação examinadas |
| Protecção de palavras-passe comprometidas desactivada | 1 WARN | Bloqueio para a preparação de produção até ser possível activar e verificar esta protecção |
| Chaves estrangeiras sem índice | 2 INFO | Corrigidas pelo candidato `20261102150000`, ainda pendente |
| Índices sem utilização observada | 28 INFO | Não remover sem tráfego real e análise de planos; falta de utilização no ambiente actual não prova redundância |

### Contrato RPC a confirmar antes da aplicação

Na consulta ao catálogo, `public.adjust_loyalty_points_idempotent(uuid,uuid,integer,text)` estava com execução de `authenticated` revogada no alojamento. O método correspondente existe em `SupabaseApi.kt`; nesta revisão não foi encontrada uma chamada a esse método na `MainActivity.kt`. A migração `160000` volta a conceder execução autenticada depois de reforçar a verificação da permissão `loyalty`. Antes do deploy, confirmar se o método está legado ou se existe outro consumidor e manter o teste de autorização correspondente.

### CI da branch de reservas — execução 41526996865

Link: https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/41526996865

No momento desta verificação:
- Replay das 51 migrações e testes PostgreSQL de autorização/idempotência: concluídos com sucesso.
- Compilação Android, testes unitários de todas as variantes e Android Lint: concluídos com sucesso.
- Validação da configuração de release: concluída com sucesso.
- Smoke tests de interface nos emuladores Android 8 e Android 16: ainda em execução.
- A execução global ainda não tinha conclusão final.

O pipeline de CI usa uma base PostgreSQL descartável; não testa nem altera a base de dados alojada. O replay limpo prova a sequência local de migrações, mas não substitui a reprodução isolada do estado actual do projecto alojado.

**Conclusão desta passagem:** o código e os testes da matriz granular estão preparados na branch, mas o projecto alojado ainda não tem permissões granulares. Continuam bloqueados a reparação do histórico e o deploy até existir uma cópia isolada do estado real, backup com restauro comprovado e revisão final das quatro migrações pendentes.


---

## Fecho da CI e inventário de integrações — 03/11/2026

### Execução completa da branch de reservas

A execução **41528216195** terminou com conclusão global **success**, no commit `19d34d982a135c80b672ff4b13817f83ec1ecadd`.

Todos os quatro jobs terminaram com sucesso:

1. **Compilação Android:** auditoria de pins/actions, dependências Edge Functions, segurança da eliminação de conta, compilação, testes unitários de todas as variantes, Android Lint, validação da configuração comercial e publicação do artefacto DEBUG.
2. **Base de dados descartável:** replay das 51 migrações, verificação de esquema/RLS/RPCs e testes de autorização entre restaurantes e idempotência.
3. **Emulador Android 8 (API 26):** smoke tests concluídos.
4. **Emulador Android 16:** smoke tests concluídos.

A execução anterior `41526996865` foi cancelada durante os smoke tests, mas foi substituída pela execução completa acima, no commit actualizado. Não deve ser usada como resultado final.

Link da execução completa: https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/41528216195

### Artefactos de demonstração

A execução produziu o artefacto `StudioMesa-1.5.0-Demo-APKs`, com cinco variantes DEBUG:

- Essencial — Restaurante
- Profissional — Restaurante
- Profissional — Cliente
- Premium — Restaurante
- Premium — Cliente

O projecto declara `versionName = 1.5.0`, `versionCode = 7`, `minSdk = 24` e aplicação de demonstração `pt.studiomesa.preview` com sufixos por plano/tipo. Os APKs são de demonstração/teste; não são os pacotes RELEASE assinados para venda ou Play Store.

O ZIP descarregado nesta revisão foi comparado com o digest SHA-256 do artefacto fornecido pelo GitHub e coincide. Os cinco APKs passaram a verificação de integridade ZIP. Isto não substitui a instalação e teste físico em dispositivos reais.

O artefacto do GitHub tem expiração indicada para **16/11/2026 às 21:31 UTC**.

### Mapa actual das propostas de integração

| PR | Conteúdo | Estado/decisão técnica |
|---|---|---|
| #72 | Receitas, snapshots de pedidos/custos, gestor económico e reservas | Base funcional ampla, CI final da revisão actual ainda por executar; integrar apenas numa sequência controlada |
| #74 | Redesenho e navegação móvel, conversões de unidades e custos de receita | Depende de #72, mas actualmente está `dirty`/não mergeable; precisa de rebase ou reconstrução sobre o head actualizado de #72 e bateria completa de testes |
| #75 | Loja de fidelização, QR e permissões granulares — implementação paralela | CI própria passou; sobrepõe funcionalidades e ficheiros à cadeia #76/#77. Não combinar às cegas com essa cadeia |
| #76 | QR de fidelização associado à referência da fatura e loja de recompensas | CI própria passou; base da cadeia de reservas |
| #77 | Reservas de cliente, wrappers privados e matriz granular de permissões | CI completa passou; depende de #76 |
| #78 | Visibilidade imediata da resposta do assistente | PR independente; CI completa passou |

A sequência local mais coerente para o fluxo de QR/fidelização e reservas é **#76 → #77**. A PR #78 é independente. A implementação #75 é uma linha paralela e deve ser comparada com #76/#77 antes de qualquer integração, para evitar duplicar ou sobrepor migrações e lógica. A PR #74 não deve avançar antes de resolver a divergência com a sua base #72. A PR #71 está identificada na própria #72 como supersedida e não deve ser integrada separadamente.

Mesmo com CI verde em cada PR, as linhas #72/#74 e #76/#77 alteram ficheiros centrais comuns, como `MainActivity.kt` e `SupabaseApi.kt`. O conjunto integrado necessita de uma nova bateria de regressão depois de reconciliar essas branches.

**Estado final desta revisão:** CI da branch #77 concluída com sucesso e APKs DEBUG disponíveis para teste. Nenhum PR foi integrado, nenhuma migração foi aplicada ao Supabase alojado e a preparação comercial continua bloqueada por backup/restauro, autenticação real, teste físico, política de privacidade e assinatura RELEASE.
