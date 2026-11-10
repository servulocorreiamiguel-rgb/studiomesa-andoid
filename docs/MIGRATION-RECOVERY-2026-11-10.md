# Recuperação do histórico de migrações — 10/11/2026

## Resultado desta alteração

Foram recuperados cinco ficheiros-fonte históricos a partir da branch `feature/invoice-loyalty-qr` e gravados no repositório com os identificadores que aparecem no histórico remoto do Supabase:

| Versão remota | Ficheiro-fonte histórico recuperado |
|---|---|
| `20261102103246` — `loyalty_invoice_qr` | `20261102102500_loyalty_invoice_qr.sql` |
| `20261102103627` — `loyalty_invoice_qr_foreign_key_indexes` | `20261102104100_loyalty_invoice_qr_foreign_key_indexes.sql` |
| `20261102104650` — `loyalty_invoice_qr_private_execution` | `20261102104800_loyalty_invoice_qr_private_execution.sql` |
| `20261102113719` — `loyalty_shop_git_reconciliation_and_redemption_queue` | `20261102112000_loyalty_shop_schema_and_redemption_queue.sql` |
| `20261102113944` — `loyalty_shop_private_function_boundary` | `20261102113000_loyalty_shop_private_function_boundary.sql` |

Os hashes Git dos cinco ficheiros históricos de origem foram registados na PR de recuperação. O nome/versão dos ficheiros repostos coincide agora com as cinco entradas remotas.

## Verificação read-only no Supabase

A consulta ao projeto alojado confirmou a existência dos objetos esperados:
- `private.loyalty_invoice_qr_codes`;
- oito índices associados à tabela de QR de faturas, incluindo os índices de `issued_by` e `redeemed_by`;
- duas funções internas privadas de QR de faturas;
- sete funções internas privadas da loja de pontos;
- nove wrappers públicos para essas operações.

Isto é evidência de que os ficheiros recuperados pertencem à linha funcional presente no servidor. Não é uma comparação byte a byte com o SQL original executado, porque a interface de gestão consultada fornece as versões e os nomes das migrações, mas não devolve o conteúdo SQL histórico remoto.

## Estado do histórico após a recuperação

- 66 ficheiros SQL no repositório (61 anteriores + 5 recuperados).
- 63 versões no histórico remoto.
- 63 identificadores de versão remotos agora representados por ficheiros locais.
- Três migrações continuam apenas locais e não devem ser aplicadas automaticamente:
  - `20261030111815_restaurant_recipes_and_order_stock_consumption.sql`;
  - `20261102103000_granular_restaurant_staff_permissions.sql`;
  - `20261104100000_price_adviser_actor_indexes.sql`.

As duas primeiras correspondem a tabelas/funcionalidades que continuam ausentes no projeto alojado. A terceira também permanece fora do histórico aplicado e carece de avaliação própria.

## Validação e segurança

A CI da PR executa a sequência completa de migrações numa base PostgreSQL isolada, testa autorização e executa os smoke tests Android 8/16. Uma CI verde valida a reprodutibilidade do repositório, não prova que o SQL histórico remoto era idêntico ao ficheiro de origem recuperado.

**Não foi executado** `db push`, `migration repair`, reset, migração manual nem alteração de esquema no Supabase alojado. Não foram alterados dados, funções, políticas, permissões, chaves ou segredos.

Antes de qualquer deployment futuro, ainda é necessário:
1. comparar as definições relevantes do esquema remoto com o resultado do replay;
2. preparar backup verificável e ensaiar restauro num projeto separado;
3. rever as três migrações locais não aplicadas;
4. aprovar explicitamente qualquer alteração de produção.

A recuperação reduz a divergência documental do histórico; não autoriza por si só a ativação de receitas, permissões granulares ou a publicação comercial.
