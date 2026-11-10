# StudioMesa — reconciliação do histórico Supabase

**Fotografia read-only:** 10 de novembro de 2026, após reposição/deduplicação em GitHub.
**Projeto:** `svwvkfsyevuyabelrtjk` · `eu-west-1` · PostgreSQL 17.
**Regra:** este documento não autoriza `db push`, `migration repair`, `apply_migration`, reset, deployment de Edge Functions ou qualquer alteração ao projeto alojado.

## 1. Estado atual dos históricos

| Medida | Repositório `main` | Supabase alojado |
|---|---:|---:|
| Ficheiros/versões | 61 migrations SQL | 63 versões registadas |
| Versões com timestamp comum | 58 | 58 |
| Só no repositório | 3 | — |
| Só no histórico remoto | — | 5 |

### Três migrations só locais

- `20261030111815_restaurant_recipes_and_order_stock_consumption.sql`
- `20261102103000_granular_restaurant_staff_permissions.sql`
- `20261104100000_price_adviser_actor_indexes.sql`

Estas versões não constam do histórico remoto. As duas primeiras correspondem a estruturas/funcionalidades ainda ausentes no catálogo alojado; a terceira exige verificar os índices e os seus pré-requisitos no catálogo antes de qualquer decisão.

### Cinco versões só remotas

- `20261102103246` — QR de fatura
- `20261102103627` — índices de foreign keys do QR de fatura
- `20261102104650` — fronteira de execução privada do QR de fatura
- `20261102113719` — reconciliação da loja de fidelização/fila de resgates
- `20261102113944` — fronteira de funções privadas da loja de fidelização

Os SQL originais destas cinco versões não foram recuperados nas branches Git consultadas. Existem ficheiros locais com timestamps diferentes que parecem descrever partes do mesmo estado funcional, mas não são os SQL históricos originais. Não renomear, copiar ou marcar essas versões como iguais só com base nos nomes ou em semelhança funcional.

## 2. Ficheiros históricos recuperados

Foram recuperados sete ficheiros SQL através dos blobs originais encontrados em branches históricas do próprio repositório. Os hashes dos blobs recuperados coincidem com os hashes de origem:

| Versão recuperada | Origem / SHA do blob |
|---|---|
| `20261102082151_reconcile_inventory_unit_cost_and_waste_snapshots.sql` | `dd8574507d07b195384d60afca15ae535228457a` |
| `20261108120141_restaurant_reactivation_campaigns.sql` | `34c9d172aa423315438e6e07322a5b81e2b25da4` |
| `20261108120201_reactivation_campaign_foreign_key_indexes.sql` | `8a0b541ea62d6f4d1002884aca203eb0bb354ebb` |
| `20261108124820_fix_reactivation_campaign_conflict_target.sql` | `8df9ed04f5f7fa6a9172806dd58acd0d81176cda` |
| `20261108125118_reactivation_campaign_owner_deletion.sql` | `67a23d29b27a8583ed7b6b721befce854b07e2dd` |
| `20261108164151_customer_restaurant_discovery_and_membership_requests.sql` | `b91e39c0a975272ba424d14900a4887c1b40228a` |
| `20261108164623_customer_membership_request_staff_self_join_guard.sql` | `befdd40f3a339d779f952e2be4420a4eb234a916` |

Uma migration local antiga — `20261030120000_inventory_unit_cost_and_waste_snapshots.sql` — tinha exatamente o mesmo SHA de blob do ficheiro recuperado `20261102082151`. A cópia com timestamp antigo foi removida do `main` para impedir a execução duplicada. A cadeia limpa deve continuar a incluir apenas o ficheiro com a versão que consta do histórico remoto.

## 3. Estado estrutural relevante observado no projeto alojado

As consultas read-only de 10/11/2026 observaram:

- Projeto `ACTIVE_HEALTHY`, região `eu-west-1`.
- 26 tabelas nos esquemas `public`/`private`, todas com RLS ativo.
- As tabelas `restaurant_assistant_conversations`, `restaurant_assistant_messages`, `restaurant_assistant_memory` e `restaurant_supplier_invoices` existem; o bucket `studiomesa-invoices` é privado, limite 25 MiB e MIME types PDF/JPEG/PNG/WebP.
- As tabelas `public.restaurant_recipes`, `public.restaurant_recipe_ingredients` e `private.restaurant_staff_permissions` não existem no projeto alojado.
- O inventário encontrou 47 funções `SECURITY DEFINER` públicas: todas referenciam `auth.uid()` e usam `search_path = ''`; nenhuma tem EXECUTE para `anon`, 42 têm EXECUTE para `authenticated`. A revisão de autorização de cada função e os testes cross-tenant continuam necessários.
- As 11 tabelas indicadas pelo Advisor como RLS sem políticas não concediam SELECT/INSERT/UPDATE/DELETE diretamente a `anon` ou `authenticated` na consulta realizada.
- Não foi criada branch Supabase, não foi executado backup/restauro demonstrado e não foi aplicada qualquer migration durante esta reconciliação.

## 4. Resultado da CI da cadeia local

A execução `42799443309` passou os controlos estáticos, compilação, testes unitários e Android Lint; também completou com sucesso o job de replay PostgreSQL/autorização. Foi cancelada durante a validação comercial devido a um commit posterior que removeu a migration duplicada.

A execução posterior `42800159352` foi iniciada já com a cópia duplicada removida. O resultado final dessa execução deve ser consultado no GitHub; não se deve declarar a pipeline verde com base apenas no sucesso do job de replay.

## 5. Sequência segura para fechar a reconciliação

1. Recuperar os cinco SQL remotos em falta a partir de backup/artefactos confiáveis do projeto ou demonstrar a equivalência exata por comparação completa de definições do catálogo. Não inventar os ficheiros.
2. Comparar as 58 versões comuns por SQL integral e verificar, além do timestamp, tabelas, colunas, tipos, constraints, índices, funções, triggers, policies e grants.
3. Determinar o destino das três migrations locais-only, com atenção às estruturas de receitas e permissões que ainda não existem no backend.
4. Fazer backup verificável e testar restauro num projeto separado antes de qualquer alteração ao projeto alojado.
5. Executar a cadeia canónica num ambiente descartável, executar testes de autorização, isolamento entre restaurantes, concorrência e idempotência, e obter revisão independente.
6. Preparar um plano de deployment separado, aprovado pelo responsável, com rollback e verificação pós-deployment.

**Decisão nesta data:** histórico parcialmente reconciliado, mas não fechado. O Supabase alojado permanece sem alterações nesta operação. O StudioMesa não está autorizado para dados reais, piloto comercial ou venda enquanto estes bloqueios, a autenticação, testes E2E, privacidade/licenças, backup/restauro e release assinada não estiverem concluídos.