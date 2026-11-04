# Loja de pontos StudioMesa — Plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans para executar este plano tarefa a tarefa. Os passos usam caixas de verificação para acompanhar o progresso.

**Goal:** Ligar a loja de recompensas existente no Supabase às apps Restaurante e Cliente, com personalização por restaurante e resgates controlados.

**Architecture:** Manter Kotlin nativo e usar o cliente REST Supabase já existente. Um modelo Kotlin valida a disponibilidade das recompensas; RPCs existentes fazem a gestão e o resgate. Será acrescentado um RPC restrito à equipa para listar pedidos de entrega.

**Tech Stack:** Kotlin, Android SDK, JUnit 4, Supabase Postgres RPC, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-02-loyalty-shop-experience-design.md`

## Global Constraints
- Não introduzir dependências novas.
- Não expor a tabela de resgates diretamente às apps.
- Não permitir que o cliente escolha a quantidade de pontos a creditar nem alterar saldos por resgates manuais.
- Não substituir nem alterar o QR fiscal/ATCUD.
- Manter o isolamento entre restaurantes e o histórico de operações.

## Review Focus
- Recompensa sem stock, stock esgotado e stock ilimitado — testes do modelo e do RPC.
- Pontos insuficientes, cliente sem cartão e tentativa repetida — testes unitários e de integração PostgreSQL.
- Funcionário sem perfil de proprietário a alterar catálogo — teste de autorização.
- Cliente de outro restaurante a consultar prémios ou a resgatar — teste de isolamento.
- Pedido pendente já entregue e confirmação repetida — teste de idempotência.

---

### Task 1: Modelo de recompensa e contrato de disponibilidade

**Files:**
- Create: `app/src/main/java/pt/studiomesa/app/LoyaltyReward.kt`
- Test: `app/src/test/java/pt/studiomesa/app/LoyaltyRewardTest.kt`

**Interfaces:**
- Produces: `LoyaltyReward.fromJson(JSONObject): LoyaltyReward`, `availableStock(): Int?`, `canRedeem(currentPoints: Int): Boolean`.

- [x] Escrever primeiro testes para stock ilimitado, stock esgotado, pontos insuficientes e estado ativo por defeito.
- [x] Executar os testes e confirmar falha porque o modelo ainda não existe.
- [x] Implementar o modelo e validação mínima.
- [x] Executar os testes de novo (a primeira tentativa detetou a limitação dos stubs Android de `org.json`; o teste foi ajustado para testar a lógica sem depender do stub).

### Task 2: Camada Supabase e interface de catálogo

**Files:**
- Modify: `app/src/main/java/pt/studiomesa/app/SupabaseApi.kt`
- Modify: `app/src/main/java/pt/studiomesa/app/MainActivity.kt`

**Interfaces:**
- Consumes: `LoyaltyReward.fromJson(JSONObject)`.
- Produces: métodos para listar prémios do restaurante e cliente, guardar prémio, resgatar prémio, listar histórico do cliente e confirmar entrega.
- App Restaurante: criar/editar/ativar/desativar recompensas; só o proprietário vê os controlos de edição.
- App Cliente: separador Prémios, consulta de catálogo e histórico; resgate com identificador de pedido persistente.

- [x] Acrescentar testes de unidade para validação de custo/stock.
- [x] Ligar os RPCs existentes sem conceder acesso direto às tabelas.
- [x] Renderizar a loja de cliente e a gestão do restaurante com estados vazios e mensagens de erro da camada backend.
- [x] Compilar as variantes, executar testes unitários, lint e testes de interface Android 8/16 na versão anterior ao reforço final de sessão.\n- [x] Associar cache ao identificador do restaurante/cartão e testar isolamento.\n- [ ] Reexecutar CI completa após reforço da proteção de respostas assíncronas.

### Task 3: Fila de entregas e isolamento

**Files:**
- Create: `supabase/migrations/20261102112000_loyalty_shop_schema_and_redemption_queue.sql`
- Create: `supabase/migrations/20261102113000_loyalty_shop_private_function_boundary.sql`
- Modify: `scripts/ci/check_postgres_authorization_contracts.sh`
- Modify: `.github/workflows/android.yml`

**Interfaces:**
- Produces: `public.get_restaurant_loyalty_reward_redemptions(uuid)`, devolvendo os resgates recentes daquele restaurante, com nome do prémio, cliente, pontos e estado.
- Apenas membros autenticados da equipa do restaurante podem consultar esta fila.

- [x] Escrever primeiro testes de integração para o catálogo, resgates, fila e isolamento; confirmar que a migração é necessária.
- [x] Implementar RPC de fila com `SECURITY DEFINER`, `search_path` vazio, verificação de sessão e de equipa, sem execução para anon.
- [x] Testar isolamento entre restaurantes, autorização e confirmação repetida da entrega.
- [x] Atualizar o contrato de migrações na CI e confirmar reprodução completa numa base limpa.\n- [x] Rever o diff e os privilégios: os 7 RPCs públicos são wrappers `SECURITY INVOKER`, com implementações privilegiadas no esquema `private`.\n- [ ] Executar novamente os ensaios Android depois do reforço de isolamento da sessão.

## Definition of Done
A loja só é considerada ligada quando a app Restaurante consegue gerir prémios, a app Cliente consegue consultar e resgatar, a equipa consegue confirmar entregas, os testes de autorização passam e as cinco variantes Android compilam.
