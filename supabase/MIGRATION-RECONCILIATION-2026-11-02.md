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
