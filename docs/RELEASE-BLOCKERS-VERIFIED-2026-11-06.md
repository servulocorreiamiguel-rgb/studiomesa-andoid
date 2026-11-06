# StudioMesa — bloqueios de lançamento verificados

**Verificação:** 6 de novembro de 2026  
**Branch de referência:** `main`  
**Commit de referência antes deste relatório:** `a3d4a4a45dc6b7efd61e45de34eebabdf45d9122`

## Resultado executivo

A aplicação Android e a primeira fase da IA local estão no código principal. Os ficheiros centrais `LocalAssistantModelManager.kt`, `AssistantChatStore.kt`, `MainActivity.kt` e a migração de sincronização/faturas foram comparados entre `main` e a branch da PR #85: os hashes coincidem. A PR #85 continua aberta, divergente e não mergeable; não se deve forçar a sua fusão.

A CI GitHub Actions #42110854314 terminou com sucesso no commit candidato `4d8fa85878c6c20f1ad81138fb69ffd961921083`. Passaram: compilação das cinco variantes, testes unitários das variantes, Android Lint, validação do caminho de build comercial com chave temporária de CI, replay das migrações e testes de autorização PostgreSQL, e smoke tests em emuladores Android 8/API 26 e Android 16. O teste automático `assistantComposerRemainsVisibleWhenKeyboardOpens` também existe na suite de interface.

Esta CI não é uma execução do build comercial com a chave definitiva, nem teste físico no OPPO A5m. A versão atual continua a ser uma demonstração técnica avançada, não um produto pronto para venda.

## Supabase alojado — verificações read-only

Projeto: `svwvkfsyevuyabelrtjk`, região `eu-west-1`, estado `ACTIVE_HEALTHY`.

### Divergência de migrações

- Repositório `main`: 53 ficheiros SQL em `supabase/migrations`.
- Supabase alojado: 54 versões no histórico remoto.
- Existem **cinco migrações presentes no repositório mas ausentes no histórico remoto**:
  - `20261030111815_restaurant_recipes_and_order_stock_consumption`
  - `20261030120000_inventory_unit_cost_and_waste_snapshots`
  - `20261102103000_granular_restaurant_staff_permissions`
  - `20261104100000_price_adviser_actor_indexes`
  - `20261105110500_studiomesa_assistant_sync_and_supplier_invoices`
- Existem **seis versões registadas no Supabase sem ficheiro correspondente em `main`**:
  - `20261102082151_reconcile_inventory_unit_cost_and_waste_snapshots`
  - `20261102103246_loyalty_invoice_qr`
  - `20261102103627_loyalty_invoice_qr_foreign_key_indexes`
  - `20261102104650_loyalty_invoice_qr_private_execution`
  - `20261102113719_loyalty_shop_git_reconciliation_and_redemption_queue`
  - `20261102113944_loyalty_shop_private_function_boundary`

Esta divergência impede assumir que o replay limpo da CI representa o estado remoto. É preciso recuperar ou reconstruir os seis ficheiros em falta com base no SQL efetivamente aplicado e comparar o esquema antes de aplicar as cinco migrações pendentes.

### Estado funcional da cloud

- As tabelas `restaurant_assistant_conversations`, `restaurant_assistant_messages`, `restaurant_assistant_memory` e `restaurant_supplier_invoices` não existem no projeto alojado.
- `storage.buckets` não tem buckets; o bucket privado `studiomesa-invoices` não está ativo.
- A sincronização cloud do histórico/memória e o arquivo remoto de faturas não estão operacionais. O histórico e os documentos locais continuam disponíveis segundo as permissões e o armazenamento do dispositivo.
- A Edge Function `studiomesa-assistant` está marcada como ativa, mas o seu código devolve HTTP 410 com `CLOUD_AI_DISABLED`. A IA principal é local e não depende dessa função.
- A base tem um utilizador Auth e as 19 tabelas de negócio consultadas reportaram zero registos. Isto não substitui um backup nem prova que não existam outros dados ou objetos fora dessas tabelas.

### Revisão de segurança observada

- O advisor reporta a proteção contra palavras-passe comprometidas desativada. É necessário ativá-la nas definições de Auth antes de um piloto com contas reais.
- Foram observadas 36 funções públicas `SECURITY DEFINER`; 31 são executáveis por `authenticated` e nenhuma por `anon`. A consulta confirmou que todas têm configuração de `search_path` e referência a `auth.uid()`; isso é uma verificação estrutural, não prova, por si só, que cada função limita corretamente o restaurante e o papel do utilizador.
- O advisor reporta oito tabelas RLS sem políticas. Nas duas tabelas expostas em `public` (`loyalty_rewards` e `loyalty_reward_redemptions`), os privilégios diretos de SELECT/INSERT/UPDATE/DELETE para `anon` e `authenticated` estão revogados. As restantes tabelas indicadas pertencem ao schema privado.
- O advisor de desempenho aponta duas foreign keys sem índice de cobertura e 28 índices ainda sem utilização. Não remover índices apenas por estarem sem utilização num projeto praticamente vazio.

## O que falta antes de um piloto

1. Reconciliar as 11 diferenças do histórico de migrações. Recuperar os SQL remotos em falta, verificar se as migrações locais pendentes correspondem ao esquema esperado e executar backup verificável, ensaio de restauro e replay numa cópia descartável.
2. Só depois aplicar as migrações pendentes, incluindo a migração da IA, e verificar as quatro tabelas de assistência, o bucket privado e as políticas de Storage.
3. Configurar Auth para produção: proteção contra palavras-passe comprometidas, SMTP, confirmação de email, recuperação de palavra-passe, URLs de retorno e OAuth se utilizado.
4. Testar com contas descartáveis de proprietário, funcionário e cliente, em restaurantes diferentes; confirmar RLS, isolamento, sincronização, conflitos, sessão expirada e eliminação em dois dispositivos.
5. Validar OCR e arquivo com documentos anonimizados, PDFs inválidos, NIF incorreto, totais incoerentes, duplicados e permissões de ficheiros revogadas.
6. Identificar o POS/programa de faturação do primeiro restaurante. Não existe integração direta genérica até ser conhecida uma API ou exportação oficial compatível.
7. Fazer os testes físicos no OPPO A5m: instalação, download do modelo (~736 MB), espaço, arranque, RAM, latência, temperatura, bateria, teclado e qualidade do português europeu. **Estes testes ficam deliberadamente por fazer nesta tarefa.**

## O que falta antes da distribuição comercial

- Revisão das licenças LFM Open License v1.0, LiteRT-LM/Apache 2.0 e restantes dependências.
- Política de privacidade, Google Play Data safety, retenção e eliminação de dados, backups/restauro e suporte.
- Escolher o identificador de pacote comercial definitivo.
- Criar e guardar uma keystore comercial fora do repositório; configurar os segredos de assinatura e produzir AAB/APK comerciais assinados. O build de CI usa uma chave descartável e os APKs disponibilizados são de demonstração/debug.
- Piloto controlado com dados fictícios e revisão de segurança independente antes de dados reais.

## Regra operacional

Não executar `db push`, `migration repair` ou `apply_migration` no projeto alojado enquanto a divergência não for reconciliada e não existir backup/restauro verificável. As consultas desta auditoria foram read-only; não alteraram o projeto remoto.

**Conclusão:** o código e a CI automatizada estão num estado avançado. A sincronização cloud, a configuração Auth, a revisão de segurança, a licença/distribuição e o teste físico continuam pendentes. Não classificar como pronto para venda.
