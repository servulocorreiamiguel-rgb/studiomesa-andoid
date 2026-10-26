# Reconciliação do histórico de migrações Supabase — 26/10/2026

## Estado inicial da comparação (antes da recuperação)

Consulta de leitura ao projeto ligado em 26/10/2026, antes de restaurar os ficheiros:

- Migrações registadas pelo Supabase: **39**.
- Ficheiros `*.sql` em `supabase/migrations` na branch `main`: **20**.
- Prefixos de versão exatos presentes em ambos: **17**.
- Registos remotos sem ficheiro local com o mesmo prefixo: **22**.
- Ficheiros locais cujo prefixo não consta do histórico remoto: **3**.

A igualdade do prefixo não prova que o conteúdo do ficheiro seja idêntico ao SQL originalmente aplicado. Da mesma forma, os três nomes abaixo são semelhantes, mas não se deve assumir que são equivalentes sem comparar o SQL e o esquema efetivo.

## Registos ausentes no inventário inicial (antes da recuperação)

| Versão registada | Nome registado |
|---|---|
| `20261019155521` | `studiomesa_loyalty_backend_reapply` |
| `20261019201226` | `studiomesa_rls_performance_tuning` |
| `20261019201547` | `studiomesa_private_join_codes` |
| `20261019204105` | `studiomesa_private_staff_helper` |
| `20261019204113` | `studiomesa_restrict_staff_helper` |
| `20261019204239` | `studiomesa_shared_operations` |
| `20261019204548` | `studiomesa_customer_menu_and_membership` |
| `20261019204832` | `studiomesa_order_author_index` |
| `20261019205006` | `studiomesa_staff_access_codes` |
| `20261019205117` | `studiomesa_scope_restaurant_accounts` |
| `20261019205201` | `studiomesa_rotate_restaurant_codes` |
| `20261019205410` | `studiomesa_order_audit_log` |
| `20261019205538` | `studiomesa_stronger_access_codes` |
| `20261019205751` | `studiomesa_order_event_actor_index` |
| `20261019205918` | `studiomesa_table_zones_and_capacity` |
| `20261019210108` | `studiomesa_menu_details_and_allergens` |
| `20261019210529` | `studiomesa_more_entropy_for_staff_codes` |
| `20261019211012` | `studiomesa_order_quantities_courses_notes` |
| `20261021090456` | `restaurant_inventory_and_reservations` |
| `20261021090901` | `restaurant_team_management` |
| `20261021091127` | `rotate_team_code_on_staff_removal` |
| `20261025225458` | `fix_join_restaurant_staff_conflict_target` |

O registo `20261026173330_fix_staff_join_concurrent_conflict_target` tem ficheiro local correspondente na versão atual da branch principal.

## Ficheiros mal versionados no inventário inicial (antes da recuperação)

| Ficheiro local | Registo remoto com nome semelhante |
|---|---|
| `20261021090000_restaurant_inventory_and_reservations.sql` | `20261021090456_restaurant_inventory_and_reservations` |
| `20261021100000_restaurant_team_management.sql` | `20261021090901_restaurant_team_management` |
| `20261021102000_rotate_team_code_on_staff_removal.sql` | `20261021091127_rotate_team_code_on_staff_removal` |

## Plano de recuperação seguro

1. **Congelar alterações de esquema não essenciais** até se perceber a divergência. Não correr `supabase/schema.sql` no projeto ligado.
2. **Guardar uma fotografia do estado atual**: exportar o esquema do banco ligado num formato revisto e seguro, incluindo tabelas, colunas, tipos, restrições, índices, políticas RLS, grants, funções, triggers e views. Não incluir dados pessoais reais num artefacto público.
3. **Recuperar os SQL originais** a partir do histórico Git, commits antigos, branches e artefactos de execução. Não reconstruir migrações somente com base nos nomes registados.
4. **Comparar estado e intenção**: validar cada migração recuperada contra o esquema atual e identificar alterações aplicadas manualmente ou cobertas por migrações com timestamp diferente.
5. **Testar a estratégia num projeto Supabase isolado e descartável**, desde uma base vazia até ao esquema esperado; correr os testes da app e as verificações de segurança.
6. **Só então escolher a estratégia de baseline/reconciliação** e documentar claramente o que se torna histórico e o que é SQL ainda por aplicar. Qualquer operação que altere o histórico remoto requer revisão específica e confirmação antes de execução.
7. **Verificar novamente** o estado final, comparar o esquema e executar os testes de regressão antes de declarar o bloqueio resolvido.

## Progresso da recuperação — branch de revisão, 26/10/2026

Depois de recuperar os ficheiros históricos e corrigir três timestamps na branch de revisão:

- Ficheiros SQL locais: **39**.
- Versões registadas no Supabase: **39**.
- Versões registadas sem ficheiro local correspondente: **0**.
- Ficheiros locais com versão não registada no servidor: **0**.
- Prefixos de versão duplicados localmente: **0**.
- Foram recuperados **19 ficheiros** a partir da branch histórica `feature/restaurant-ai-assistant-2026-10-25`.
- Os três ficheiros locais com timestamps incorretos foram substituídos por ficheiros com os timestamps registados; os conteúdos dos três pares foram comparados byte a byte e eram idênticos.
- A migração `20261025230000_restaurant_ai_quota.sql` dessa branch histórica foi deliberadamente excluída porque não aparece no histórico aplicado do projeto ligado.
- A migração final `20261026173330_fix_staff_join_concurrent_conflict_target.sql` permanece incluída.

## Testes de isolamento live — 26/10/2026

Dois ensaios executados numa transação e terminados com \`ROLLBACK\`:

1. Com duas contas e dois restaurantes temporários, cada conta viu exatamente a sua linha de restaurante, adesão de equipa, artigo de carta, mesa e pedido; a outra conta não conseguiu ler esses registos.
2. Como a conta A, as RPCs \`create_inventory_item_idempotent\` e \`create_restaurant_reservation_idempotent\` foram chamadas com o identificador do restaurante B. Ambas devolveram a mensagem de autorização esperada e não criaram dados.

Uma consulta posterior confirmou zero utilizadores, restaurantes, artigos de carta, mesas, pedidos, artigos de stock ou reservas de teste persistentes. Um ensaio inicial foi rejeitado pela restrição de números de mesa antes de chegar aos testes RLS; confirmou-se igualmente que a transação falhada não deixou resíduos.

Estes resultados são evidência útil do isolamento das tabelas principais e destas duas RPCs, não um teste integral de todas as políticas/rotas, nem substituem cenários de OAuth/cliente Android ou concorrência real.

## Estado atual e limites

**O inventário de versões está alinhado na branch de revisão; a reprodutibilidade completa continua por provar.** A presença do mesmo prefixo de versão não prova que o SQL seja idêntico ao que foi originalmente aplicado. Ainda é necessário comparar as definições efetivas do esquema e testar a execução ordenada de todas as migrações num projeto Supabase isolado e descartável.

Não foi executada qualquer alteração de esquema nem alteração do histórico de migrações no projeto de produção.
