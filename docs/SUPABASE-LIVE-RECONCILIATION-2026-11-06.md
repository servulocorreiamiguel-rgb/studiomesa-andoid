# Supabase — reconciliação do estado alojado

**Fotografia read-only:** 6 de novembro de 2026  
**Projeto:** `svwvkfsyevuyabelrtjk` · `eu-west-1` · PostgreSQL 17.6.1.166  
**Branch de código analisada:** `main` na fotografia de 06/11/2026

> Este documento descreve a diferença entre o código versionado e o projeto Supabase alojado. Não é autorização para executar migrations, reparar o histórico, fazer deploy de Edge Functions ou alterar Auth/Storage.

## Resumo

O projeto está ACTIVE_HEALTHY, mas o histórico de migrations não está alinhado com os ficheiros em `main`. As principais funcionalidades centrais alojadas (restaurantes, equipa, mesas, carta, pedidos, reservas, stock, fidelização e QR) existem. Algumas capacidades que já aparecem no código Android ainda não existem no backend alojado: fichas técnicas/consumo automático de stock, matriz granular de permissões, sincronização cloud do assistente, arquivo cloud de faturas e bucket de documentos.

## Histórico de migrations

| Medida | Valor |
|---|---:|
| Ficheiros locais em `supabase/migrations` | 53 |
| Versões registadas no Supabase | 54 |
| Prefixos comuns | 48 |
| Ficheiros locais sem versão no histórico remoto | 5 |
| Versões remotas sem ficheiro correspondente em `main` | 6 |

### Só locais — ainda não aparecem como versões aplicadas

- `20261030111815_restaurant_recipes_and_order_stock_consumption`
- `20261030120000_inventory_unit_cost_and_waste_snapshots`
- `20261102103000_granular_restaurant_staff_permissions`
- `20261104100000_price_adviser_actor_indexes`
- `20261105110500_studiomesa_assistant_sync_and_supplier_invoices`

### Só remotas — ficheiros originais não existem em `main`

- `20261102082151_reconcile_inventory_unit_cost_and_waste_snapshots`
- `20261102103246_loyalty_invoice_qr`
- `20261102103627_loyalty_invoice_qr_foreign_key_indexes`
- `20261102104650_loyalty_invoice_qr_private_execution`
- `20261102113719_loyalty_shop_git_reconciliation_and_redemption_queue`
- `20261102113944_loyalty_shop_private_function_boundary`

48 prefixos coincidem, mas essa coincidência não prova que o esquema completo, funções, políticas, triggers, índices e grants sejam idênticos. A migração de custos unitários tem efeito estrutural presente no servidor (as duas colunas abaixo existem), apesar da divergência do prefixo histórico.

## Verificações read-only do catálogo

| Capacidade | Estado observado no projeto alojado | Consequência |
|---|---|---|
| Reservas de cliente e equipa | `restaurant_reservations`, funções de pedido, consulta, cancelamento, atribuição de mesa e estado existem | A API base de reservas está alojada. A cobertura de CI em PostgreSQL descartável não substitui o teste E2E com contas reais de teste. |
| Custos unitários de stock | `restaurant_inventory_items.unit_cost_per_unit` e `restaurant_inventory_movements.unit_cost_at_movement` existem | As colunas de custo e snapshots estão presentes, apesar do histórico com prefixo diferente. |
| Fichas técnicas / consumo automático | `restaurant_recipes` e `restaurant_recipe_ingredients` não existem | O consumo de ingredientes ao iniciar preparação não está disponível no backend alojado. |
| Permissões granulares da equipa | `private.restaurant_staff_permissions` não existe | Não prometer a matriz de permissões por área como ativa na cloud. O cliente deve falhar em modo fechado quando não consegue confirmar permissões. |
| Histórico cloud do assistente | `restaurant_assistant_conversations`, `restaurant_assistant_messages` e `restaurant_assistant_memory` não existem | Histórico/memória permanecem locais; a sincronização cloud do código não pode funcionar até reconciliação e deploy aprovados. |
| Arquivo cloud de faturas | `restaurant_supplier_invoices` não existe | Metadados e estado de revisão permanecem no armazenamento local; a tabela cloud ainda não pode ser usada. |
| Documentos de faturas | `storage.buckets` não contém `studiomesa-invoices` | Upload/download remoto dos PDFs/imagens não está ativo. OCR é local. |

## Edge Functions

| Função | Estado observado |
|---|---|
| `studiomesa-assistant` | ACTIVE, versão 8, `verify_jwt=true`; o código devolve HTTP 410 com `CLOUD_AI_DISABLED` e informa que a IA funciona localmente. Não recebe mensagens. |
| `delete-account` | ACTIVE, versão 3, `verify_jwt=true`; o endpoint exige sessão verificada e usa operação administrativa no servidor para apagar a conta. |
| `account-deletion-web` | ACTIVE, versão 3, `verify_jwt=false` porque serve uma página GET pública; a página usa `resetPasswordForEmail` e PKCE, e a eliminação propriamente dita é feita pelo endpoint autenticado `delete-account`. O percurso email → navegador → confirmação → eliminação ainda não foi validado E2E. |

A versão ativa de `studiomesa-assistant` foi copiada para o repositório em `supabase/functions/studiomesa-assistant/index.ts` nesta branch para tornar reproduzível a função que está desativada. Não foi feito deploy.

## Security e performance advisors

- `auth_leaked_password_protection` está WARN / desativado. A organização está no plano Free; a documentação do Supabase indica que esta proteção requer Pro ou superior. A decisão de mudar de plano/custo pertence ao responsável e não foi tomada.
- O advisor assinala 31 funções `SECURITY DEFINER` públicas executáveis por `authenticated`. Na amostra de catálogo verificada, as funções de reservas e outras RPCs necessárias têm `search_path` vazio e não são executáveis por `anon`. Isto exige revisão função a função e testes negativos; não revogar em bloco, pois são chamadas intencionais da app e contêm validação de sessão/âmbito.
- O advisor assinala oito tabelas RLS sem políticas. Algumas são tabelas privadas fechadas e as duas tabelas públicas da loja de recompensas são RPC-only, com acesso direto revogado. Não criar políticas abertas apenas para silenciar avisos.
- O advisor de performance aponta duas FKs sem índice de cobertura (`restaurant_price_adviser_audit.acted_by` e `restaurant_price_adviser_settings.updated_by`) e 28 índices sem utilização observada. Não remover índices com base numa base quase sem tráfego.

## Plano seguro de reconciliação

1. Fazer backup verificável de acordo com o plano do projeto e testar o restauro para um ambiente separado.
2. Recuperar os seis SQL remotos ausentes a partir de histórico Git/CI/artefactos confiáveis. Se o original não puder ser recuperado, documentar uma baseline com prova do estado estrutural, sem inventar SQL histórico.
3. Comparar cada versão local-only com o catálogo alojado: tabelas, colunas, tipos, defaults, constraints, índices, funções, triggers, policies e grants.
4. Reproduzir numa base descartável a sequência canónica final e executar os testes de autorização, tenant isolation, idempotência, concorrência e integridade.
5. Rever uma ordem final única, efeitos de `IF NOT EXISTS`, rollback possível, janela de manutenção e quem autoriza a alteração.
6. Só depois preparar um pedido de aprovação para deployment em produção. O deploy deve ser uma ação separada e nunca resultado automático desta branch.

## Proibições enquanto a reconciliação não estiver aprovada

- Não executar `supabase db push`, `supabase migration repair` ou `apply_migration` neste projeto.
- Não aplicar manualmente as migrations locais apenas para ativar tabelas em falta: isso pode criar um histórico inconsistente ou duplicar objetos.
- Não alterar políticas RLS, grants, Auth, Storage, backups ou plano do projeto sem uma revisão e aprovação explícitas.
- Não introduzir dados pessoais ou faturas reais para testes.

**Conclusão:** o backend alojado não corresponde ainda ao conjunto completo de funcionalidades do código Android. O projeto não deve ser usado como solução de operação comercial real até a reconciliação, backup/restauro, Auth, testes E2E e revisão de segurança estarem concluídos.