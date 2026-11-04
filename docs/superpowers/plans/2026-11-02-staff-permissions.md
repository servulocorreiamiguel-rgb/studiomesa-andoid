# Permissões granulares da equipa — plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dar ao proprietário controlos simples por área para cada funcionário e impor as escolhas no Supabase.

**Architecture:** Criar uma tabela privada de permissões por restaurante/funcionário e uma função privada de autorização baseada em `auth.uid()`. Expor RPCs estreitas para o proprietário consultar e guardar as escolhas; atualizar as políticas/RPCs existentes e só depois adaptar o ecrã Android. A ausência de configuração mantém temporariamente o acesso operacional atual, por compatibilidade.

**Tech Stack:** Kotlin Android existente, Supabase PostgreSQL, RLS/RPC, scripts de CI PostgreSQL já existentes.

**Spec:** `docs/superpowers/specs/2026-11-02-staff-permissions-design.md`

## Global Constraints
- Nunca confiar no papel/permissão guardado localmente para autorizar operações.
- Não usar `user_metadata` em decisões de autorização.
- Funções `SECURITY DEFINER` devem ter `search_path = ''`, nomes de schema explícitos, validação de `auth.uid()` e privilégios mínimos.
- O proprietário mantém sempre acesso total; ações administrativas e económicas continuam owner-only.
- Não alterar nem reimplementar o fluxo QR/loja de pontos.
- Não expor tabelas de permissões no schema público; guardar em `private`.
- Preservar compatibilidade dos funcionários antigos até o proprietário guardar uma matriz personalizada.

## Review Focus
- Funcionário tenta alterar a sua própria permissão — testar que o servidor recusa.
- Funcionário tenta usar RPC diretamente depois de a área ser desativada — testar negação no servidor.
- Funcionário de outro restaurante tenta aceder a IDs alheios — testar isolamento.
- Proprietário revoga acesso enquanto o funcionário tem a app aberta — testar nova operação e refresh.
- Matriz vazia/ausente e dados malformados — testar comportamento por defeito e rejeição segura.

---

### Task 1: Contrato de permissões e testes SQL

**Files:**
- Modify: `scripts/ci/check_postgres_authorization_contracts.sh`
- Modify: `.github/workflows/android.yml`

**Interfaces:**
- Permissões permitidas: `orders`, `tables`, `reservations`, `menu`, `inventory`, `loyalty`.
- O teste cria owner, dois funcionários no mesmo restaurante e um utilizador noutro restaurante.
- O helper SQL esperado será `private.has_restaurant_permission(uuid,text)`.

- [ ] **Step 1: Escrever primeiro testes de autorização que falhem** para as seis permissões, permissões ausentes, owner, funcionário de outro restaurante e tentativa de autoalteração.
- [ ] **Step 2: Executar a suite SQL** e confirmar que falha porque o modelo/helper ainda não existe.
- [ ] **Step 3: Adicionar migração** com tabela privada, constraints, índices e helper. O helper devolve true para owner; para staff sem linha personalizada mantém as seis áreas; para staff configurado só devolve true se a área estiver na lista.
- [ ] **Step 4: Criar RPC owner-only** `get_restaurant_staff_permissions(uuid)` e `set_restaurant_staff_permissions(uuid,text[])`. Validar que o alvo é staff do mesmo restaurante, que a lista não contém valores desconhecidos/duplicados e que só o owner do restaurante atual pode executar.
- [ ] **Step 5: Reexecutar os testes SQL** e confirmar owner/staff/isolamento.
- [ ] **Step 6: Commit** da migração e dos testes.

### Task 2: Aplicar permissões às operações existentes

**Files:**
- Modify: migração SQL nova em `supabase/migrations/`
- Modify: `scripts/ci/check_postgres_authorization_contracts.sh`

**Interfaces:**
- Todas as operações de gestão usam `private.has_restaurant_permission(restaurant_id, '<area>')`.
- Ações exclusivas do proprietário continuam a validar explicitamente `role = 'owner'`.

- [ ] **Step 1: Adicionar testes negativos** para pedidos, mesas, reservas, carta, stock e fidelização, chamando as RPCs/tabelas diretamente.
- [ ] **Step 2: Confirmar RED** com as permissões desativadas ainda aceites pelo modelo atual.
- [ ] **Step 3: Atualizar RLS e RPCs** de cada área, sem enfraquecer isolamento entre restaurantes nem alterar a lógica de idempotência.
- [ ] **Step 4: Testar cada área** com permissão ligada/desligada, owner, staff e conta de outro restaurante.
- [ ] **Step 5: Confirmar que gerir recompensas, equipa/códigos, consultor de preços e configurações continuam owner-only.**
- [ ] **Step 6: Commit** da aplicação server-side.

### Task 3: Interface de gestão de equipa no Android

**Files:**
- Modify: `app/src/main/java/pt/studiomesa/app/SupabaseApi.kt`
- Modify: `app/src/main/java/pt/studiomesa/app/MainActivity.kt`
- Test: `app/src/test/java/pt/studiomesa/app/` (novo teste para validar/decode da matriz)

**Interfaces:**
- `SupabaseApi.getStaffPermissions(token, userId): JSONArray`
- `SupabaseApi.setStaffPermissions(token, userId, permissions): Unit`
- Os nomes e valores enviados são apenas os seis identificadores do contrato da Task 1.

- [ ] **Step 1: Escrever testes unitários** para parsing de permissões válidas, lista vazia e identificadores desconhecidos.
- [ ] **Step 2: Confirmar RED.**
- [ ] **Step 3: Implementar chamadas RPC** em `SupabaseApi.kt`.
- [ ] **Step 4: No diálogo de cada funcionário, mostrar seis interruptores** com estado carregado do servidor e botão Guardar; proprietário e funcionários removidos não podem editar.
- [ ] **Step 5: Após guardar, voltar a carregar do servidor** e atualizar o ecrã; limpar a matriz em memória ao trocar de conta/restaurante/logout.
- [ ] **Step 6: Esconder áreas sem permissão apenas como UX**, mantendo a autorização server-side como barreira real.
- [ ] **Step 7: Executar testes Kotlin e CI Android completa.**

### Task 4: Auditoria e documentação

**Files:**
- Modify: `PRODUCT-READINESS-CHECKLIST.md`
- Modify: `TEST-PLAN.md`
- Modify: `README.md`

- [ ] **Step 1: Adicionar cenários de testes** para revogação em sessão aberta, isolamento, permissões parciais e tentativas diretas ao servidor.
- [ ] **Step 2: Documentar** as seis áreas e as ações que permanecem exclusivas do proprietário.
- [ ] **Step 3: Atualizar checklist** apenas com itens realmente testados.
- [ ] **Step 4: Executar CI completa**: migrações desde base vazia, autorização PostgreSQL, unit tests, Lint e smoke tests Android 8/16.
- [ ] **Step 5: Rever o diff e não integrar na branch principal** até todos os testes passarem e a revisão final estar concluída.
