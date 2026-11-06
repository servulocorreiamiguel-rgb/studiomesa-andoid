# StudioMesa — bloqueios de lançamento verificados

**Fotografia read-only:** 6 de novembro de 2026  
**Main:** `0770ad772f6abb2a44027a462187908a085011cf`  
**Branch desta preparação:** `release/production-readiness-2026-11-06`

## Resultado executivo

O código Android tem cinco variantes e a CI automatizada está verde na revisão de reservas: workflow #1332 terminou com sucesso nos quatro jobs. A PR #86 foi integrada na main no commit `0770ad7`.

Passaram no workflow #1332:
- Replay PostgreSQL limpo e suite de autorização/tenant isolation/idempotência.
- Compilação das cinco variantes, testes unitários, Android Lint e validação da configuração de release com chave temporária da CI.
- Smoke tests em emuladores Android 8/API 26 e Android 16.
- Os testes UI de reservas foram executados nas variantes correspondentes (Profissional/Premium Restaurante e Profissional/Premium Cliente). Os skips nas variantes sem a página/funcionalidade correspondente são esperados.

Os testes PostgreSQL de reserva adicionais passaram: repetição idempotente, slot consecutivo exatamente no fim da reserva anterior, rejeição de sobreposição na mesma mesa, capacidade insuficiente, mesa inexistente, retry após atribuição/confirmada e nova verificação de conflitos ao atribuir mesa a um pedido existente.

Esta evidência não é teste físico do OPPO A5m, não é teste E2E com contas reais de clientes, não é deployment no Supabase alojado e não é uma build assinada com a chave definitiva. A aplicação continua **não pronta para piloto ou venda**.

## Supabase alojado — estado read-only

Projeto `svwvkfsyevuyabelrtjk`, região `eu-west-1`, estado `ACTIVE_HEALTHY`.

### Divergência do histórico

| Medida | Estado |
|---|---:|
| Ficheiros locais em `supabase/migrations` | 53 |
| Versões no histórico remoto | 54 |
| Prefixos comuns | 48 |
| Só locais | 5 |
| Só remotas | 6 |

**Cinco só locais:**
- `20261030111815_restaurant_recipes_and_order_stock_consumption`
- `20261030120000_inventory_unit_cost_and_waste_snapshots`
- `20261102103000_granular_restaurant_staff_permissions`
- `20261104100000_price_adviser_actor_indexes`
- `20261105110500_studiomesa_assistant_sync_and_supplier_invoices`

**Seis só remotas:**
- `20261102082151_reconcile_inventory_unit_cost_and_waste_snapshots`
- `20261102103246_loyalty_invoice_qr`
- `20261102103627_loyalty_invoice_qr_foreign_key_indexes`
- `20261102104650_loyalty_invoice_qr_private_execution`
- `20261102113719_loyalty_shop_git_reconciliation_and_redemption_queue`
- `20261102113944_loyalty_shop_private_function_boundary`

48 prefixos comuns não provam igualdade do esquema inteiro. É necessário recuperar os seis SQL remotos ausentes e comparar tabelas, colunas, defaults, constraints, índices, funções, triggers, policies e grants antes de aplicar as migrations só locais.

### Objetos e funcionalidades em falta no projeto alojado

| Objeto/feature | Verificação | Consequência |
|---|---|---|
| `public.restaurant_recipes` e `public.restaurant_recipe_ingredients` | Ausentes | Fichas técnicas/consumo automático de ingredientes não funcionam no backend alojado. |
| `private.restaurant_staff_permissions` | Ausente | A matriz granular de permissões não está ativa no servidor; não anunciar este controlo como operacional. |
| `public.restaurant_assistant_conversations`, `public.restaurant_assistant_messages`, `public.restaurant_assistant_memory` | Ausentes | Sync cloud de histórico/Memória não está ativa; o histórico local continua funcional. |
| `public.restaurant_supplier_invoices` | Ausente | A tabela cloud de metadados de faturas não está ativa. |
| Storage bucket `studiomesa-invoices` | Ausente; `storage.buckets` está vazio | Upload e recuperação cloud de PDFs/imagens não estão ativos; OCR local não depende deste bucket. |
| Colunas de custo unitário e fotografia histórica | Existem | O efeito de custo está presente no schema, apesar do prefixo histórico remoto divergente. |
| Reservas de cliente/equipa | Tabela e RPCs principais existem | O fluxo de base está alojado; faltam testes E2E com contas de teste e configuração operacional. |

Detalhes e plano seguro: [docs/SUPABASE-LIVE-RECONCILIATION-2026-11-06.md](SUPABASE-LIVE-RECONCILIATION-2026-11-06.md).

## Edge Functions e Auth

- `studiomesa-assistant`: ACTIVE, versão 8, `verify_jwt=true`; devolve HTTP 410 `CLOUD_AI_DISABLED`. A IA generativa principal corre localmente; esta função não recebe mensagens. O código atual da função foi agora versionado nesta branch para reprodutibilidade, mas ainda não foi integrado na main/deployed de novo.
- `delete-account`: ACTIVE, versão 3, `verify_jwt=true`, requer sessão verificada.
- `account-deletion-web`: ACTIVE, versão 3, serve GET público; a página usa `resetPasswordForEmail` e PKCE. A eliminação é feita por endpoint separado e autenticado.
- O percurso email → callback no mesmo browser → sessão PKCE → eliminação ainda não passou testes E2E com contas descartáveis. SMTP e Redirect URLs ainda precisam de configuração/validação.
- `auth_leaked_password_protection` está desativado. A organização Supabase está no plano Free; o advisor/documentação indicam que a proteção contra palavras-passe comprometidas requer Pro ou superior. Não houve upgrade nem custo assumido.

## Revisão de segurança observada

- A consulta ao catálogo confirmou funções de reserva com `search_path` vazio e sem execução anónima. O advisor assinala 31 funções públicas `SECURITY DEFINER` executáveis por `authenticated`; não revogar em bloco, porque as RPCs necessárias devem ser avaliadas função a função.
- O advisor assinala oito tabelas com RLS mas sem políticas. Algumas são tabelas privadas intencionalmente fechadas; as tabelas públicas da loja de recompensas usam RPCs e os privilégios diretos estão revogados. Não abrir tabelas só para remover o aviso.
- O advisor de performance assinala duas FKs sem índice de cobertura e 28 índices sem utilização observada. Não remover índices com base em telemetria de um projeto com pouco/nenhum tráfego.
- O fluxo de reserva tem validações no servidor: sessão, restaurante, idempotência, duração, capacidade e conflito de horário quando uma mesa está atribuída. A suite de regressão foi executada apenas numa base PostgreSQL descartável.

## Preparação comercial ainda pendente

- Package IDs finais das cinco variantes.
- Keystore comercial criada e guardada fora do repositório; quatro secrets de assinatura e a variável do base application ID configurados no GitHub.
- AAB/APK comerciais assinados e verificados com a chave definitiva. O workflow normal produz APKs debug de demonstração; o workflow comercial produz artefactos mas não faz upload/publicação na Play.
- Política de privacidade definitiva, URL pública, Data Safety alinhada com o binário/configuração e termos B2B/DPA aprovados.
- SBOM e revisão de todas as licenças transitivas. O modelo usa LFM Open License v1.0 e o runtime LiteRT-LM 0.18.0 usa Apache 2.0; ML Kit, restantes dependências e direitos da entidade devem ser confirmados.
- Responsável legal, morada, NIF, contacto profissional, preços, IVA, suporte, retenção, subcontratantes e responsabilidades definidos.
- Conta Google Play tem de pertencer a um titular com pelo menos 18 anos. A taxa de registo oficial consultada é US$25, uma vez. Para contas pessoais criadas após 13/11/2023 pode aplicar-se o teste fechado com 12 testers contínuos durante 14 dias.
- SMTP/OAuth e a proteção contra palavras-passe comprometidas resolvidos.
- Backup e restauro testados; processo de suporte/incidentes/atualizações definido.
- Identificar o primeiro POS/ERP se o âmbito comercial incluir integração. Não existe integração genérica validada.
- Testes em Android físico — incluindo OPPO A5m — deliberadamente por fazer nesta tarefa.

## Regra operacional

Não executar `db push`, `migration repair`, `apply_migration`, deploy de Edge Functions, alteração de Auth/Storage ou upgrade do plano enquanto a divergência não estiver reconciliada e não existir backup/restauro verificável e aprovação explícita. Todas as consultas desta auditoria foram read-only; nenhuma alteração foi aplicada ao projeto Supabase alojado.

**Conclusão:** o código e a CI estão num estado avançado e as reservas passaram a regressão automatizada. O backend alojado, segurança Auth, assinatura comercial, documentação legal, licenças, configuração de operação e testes físicos impedem ainda declarar o StudioMesa pronto para vender.