# Reconciliação do Supabase e das branches — 02/11/2026

## Estado observado

Consulta de leitura ao projeto Supabase `svwvkfsyevuyabelrtjk` e comparação com a branch `feature/customer-reservations`:

- Migrações registadas no projeto alojado: **54**.
- Ficheiros SQL locais nesta branch: **50**.
- Prefixos de versão presentes em ambos: **41**.
- Ficheiros locais sem versão correspondente no histórico remoto: **9**.
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
- PR **#77** (`feature/customer-reservations`) está empilhada sobre #76. Acrescenta reservas na app Cliente e uma migração que coloca funções públicas de reserva como `SECURITY INVOKER`, deixando a lógica privilegiada em funções privadas.
- As três branches alteram ficheiros centrais como `MainActivity.kt`, `SupabaseApi.kt` e/ou o workflow Android. Não devem ser integradas em sequência sem uma revisão explícita das sobreposições.
- A branch #77 não contém a API de permissões granulares `get_restaurant_staff_permissions` / `set_restaurant_staff_permissions` que existe na branch #75. Essa funcionalidade precisa de ser portada ou a decisão de a adiar deve ficar documentada.

## Alteração de desempenho acrescentada à branch #77

A auditoria Performance Advisor identificou duas chaves estrangeiras sem índice:

- `private.restaurant_price_adviser_audit.acted_by`
- `private.restaurant_price_adviser_settings.updated_by`

Foi acrescentada a migração `20261102150000_add_price_adviser_actor_fk_indexes.sql`, com índices parciais para valores não nulos, e o workflow Android passou a verificar a existência dos dois índices após replay limpo.

Estado da CI para o commit `10dcaa5dbd967a81f91ba6fa88ff304a8bce0bf6`:
- Replay de todas as migrações e testes PostgreSQL de autorização: **sucesso**.
- Compilação Android e testes de interface: em execução no momento da última consulta.
- Execução: https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/41476112833

## Estado de segurança observado no projeto alojado

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
