# StudioMesa — auditoria read-only do Supabase
**Data:** 21/11/2026  
**Project ref:** `svwvkfsyevuyabelrtjk` · **Região:** `eu-west-1` · **PostgreSQL:** 17.6.1.166  
**Estado do projecto:** `ACTIVE_HEALTHY`

> Esta auditoria é apenas de leitura. Não foram aplicadas migrações, modificadas políticas, alteradas funções ou feitos deployments nesta verificação.

## Migrações e tabelas

- Histórico remoto: 63 migrações aplicadas.
- Branch `work/mobile-completion-2026-11-20`: 72 ficheiros SQL; nove versões ainda não constam do histórico remoto: `20261030111815`, `20261102103000`, `20261102120000`, `20261102121000`, `20261104100000`, `20261107170000`, `20261108170000`, `20261112120000` e `20261113114000`.
- Foram encontradas 26 tabelas nos esquemas `public` e `private`; **26/26 têm RLS activo**.
- Continuam ausentes as tabelas `public.restaurant_recipes`, `public.restaurant_recipe_ingredients`, `private.restaurant_staff_permissions`, `private.restaurant_loyalty_entitlements`, `public.customer_restaurant_cards`, `public.restaurant_supplier_invoice_lines` e `public.restaurant_stock_receipts`.
- Já existem no backend alojado as tabelas de pedidos de adesão a restaurantes, campanhas de reactivação e notificações de clientes.

Consequência: receitas/consumo automático de ingredientes, matriz granular de permissões, entitlements de planos, cartões multi-restaurante e recepção cloud de linhas de faturas/stock não devem ser anunciados como activos no servidor alojado.

## RLS e funções privilegiadas

- O Advisor assinala 11 tabelas com RLS sem políticas directas. A consulta de privilégios confirmou que nenhuma das 11 dá acesso directo SELECT/INSERT/UPDATE/DELETE a `anon` ou `authenticated`. Algumas são tabelas internas usadas por RPCs; não criar políticas genéricas para eliminar o aviso.
- Foram observadas 59 funções `SECURITY DEFINER` em `public` e `private`; nenhuma é executável por `anon`. 52 das 59 referenciam `auth.uid()`. A contagem pública foi de 47 funções com configuração de `search_path`; 42 são executáveis por `authenticated`, nenhuma por `anon`, e as 42 referenciam `auth.uid()`.
- O Supabase Advisor continua a assinalar 42 RPCs públicas `SECURITY DEFINER` executáveis por utilizadores autenticados. A presença de `auth.uid()` não prova, por si só, que todos os parâmetros, estados e limites de restaurante estão correctos. Fazer revisão funcional individual e manter os testes de autorização; não revogar permissões em bloco.

## Avisos de desempenho

- Quatro foreign keys não têm índice de cobertura.
- 45 índices aparecem como não utilizados na observação actual. Não os eliminar sem tráfego representativo, medição e revisão das consultas.
- A protecção contra palavras-passe comprometidas do Supabase Auth está desactivada; o Advisor assinala-a. A elegibilidade de plano/configuração deve ser confirmada sem activar custos sem aprovação.

## Edge Functions

As cinco funções registadas estão activas:
- `studiomesa-web-search` v3, JWT obrigatório.
- `studiomesa-ai-router` v1, JWT obrigatório.
- `studiomesa-assistant` v10, JWT obrigatório.
- `delete-account` v3, JWT obrigatório.
- `account-deletion-web` v3, sem validação JWT no gateway; a função é uma entrada pública do fluxo de eliminação web e a operação final exige endpoint separado autenticado.

Comparação de código:
- Router IA: código publicado coincide com o ficheiro da branch. A presença/configuração de chaves externas não foi confirmada nesta auditoria.
- Assistente antigo: código publicado coincide com a branch e devolve `410 CLOUD_AI_DISABLED`; não recebe mensagens na cloud.
- Pesquisa Web: código publicado v3 é a implementação SearXNG anterior; não coincide com a revisão local da branch que acrescenta Tavily. Não foi feito deployment. A pesquisa Web não deve ser anunciada como funcional sem fornecedor configurado e teste autenticado.
- As funções `delete-account` e `account-deletion-web` coincidem com os ficheiros locais consultados.

## Backups, branches e alterações

- Não foi demonstrado backup completo e restauro do estado alojado, incluindo Auth e Storage.
- Não existem branches de desenvolvimento Supabase.
- Não foi executado `db push`, reset, migration repair, alteração de DDL/RLS ou deployment de Edge Functions.
- A CI da aplicação faz replay das migrações numa base PostgreSQL descartável; não substitui um backup/restauro do projecto alojado.

## Decisão

**O Supabase está activo, mas não está pronto para receber as nove migrações pendentes em produção.** Primeiro obter e testar um backup recuperável de PostgreSQL, Auth e Storage; reconciliar o histórico; rever cada SQL/RPC e validar tudo num ambiente isolado. Só depois considerar dry-run e aprovação consciente do deployment.
