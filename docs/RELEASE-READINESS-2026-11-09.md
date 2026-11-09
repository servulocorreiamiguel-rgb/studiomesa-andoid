# StudioMesa — auditoria de prontidão e bloqueios

**Data da verificação:** 9 de novembro de 2026  
**Projeto Supabase:** `svwvkfsyevuyabelrtjk` (`eu-west-1`, `ACTIVE_HEALTHY`)  
**Branch Android:** `security/privacy-sync-consent-20261109`  
**PR:** #98 — alterações de privacidade e melhoria da IA  
**Decisão:** NÃO LIBERADO PARA VENDA/PRODUÇÃO.

Esta fotografia foi feita por leitura do repositório, estado remoto do Supabase, Edge Functions, Security Advisor e workflow GitHub Actions. Não foi executado qualquer `db push`, alteração ao Auth, alteração ao Storage, deploy de função ou upgrade do plano durante esta verificação.

## IA e análise de vendas

- O modelo generativo principal é `LFM2.5-1.2B-Instruct` INT4 e corre localmente no dispositivo. É um modelo de terceiros, não um modelo criado pelo StudioMesa.
- O modo económico usa agora uma instrução de sistema compacta separada da instrução geral, para manter disponíveis as regras críticas de consulta de faturas, cálculos, análise semanal, previsão e ação `holiday_calendar` dentro do limite de contexto do modelo local. A memória relevante no modo económico fica limitada a 300 caracteres.
- A função `studiomesa-assistant` está ativa com JWT, mas responde HTTP 410 (`CLOUD_AI_DISABLED`); não recebe mensagens do assistente principal.
- As ferramentas locais incluem análise económica determinística, referência de preço, metadados de faturas, dados operacionais e análise de vendas. As ferramentas são apenas de leitura.
- A análise semanal usa pedidos em estado `Concluído`, data de criação e valor/quantidade válidos. Exclui o dia atual e dias sem dados não são interpretados como zero.
- A previsão usa observações recentes do mesmo dia da semana, amostra, confiança e tendência limitada a ±15% quando há pelo menos oito observações. Sem dados comparáveis, não produz um valor fictício.
- A afluência é estimada pelo número de linhas de pedidos concluídos; não equivale a pessoas, clientes ou reservas. O valor é nominal e não confirma pagamentos recebidos.
- Os feriados nacionais portugueses são reconhecidos por regra de calendário. O Carnaval é facultativo. Feriados municipais e eventos locais são configurados pelo proprietário no ecrã Análise.
- O calendário municipal/eventos é guardado localmente no dispositivo e ainda não sincroniza com outros dispositivos.
- O motor de previsão, o reconhecimento de feriados, as ferramentas económicas e o parser de faturas têm testes unitários e instrumentados. O teste de interface Premium valida a abertura do ecrã, a configuração de um evento e a sua presença na previsão.
- **CI concluída com sucesso em 9/11/2026:** workflow `42690365320`, commit `e3f9edff57c1c98c5a90e68cdb6654790ef9e765`. Passaram compilação das cinco variantes, testes unitários das cinco variantes, Android Lint, replay de migrações e testes de autorização/RLS, validação de configuração de release com chave temporária de CI e smoke tests completos em Android 8/API 26 e Android 16/API 36. Os artefactos desta execução são APKs debug de demonstração e relatórios de teste, não builds comerciais assinadas.
- A CI não executa inferência real do modelo LFM2.5 em telemóvel físico. Velocidade, memória, aquecimento, estabilidade prolongada e qualidade de resposta no OPPO A5m e noutros equipamentos ainda exigem teste físico.
- A pesquisa Web é separada do modelo local. `studiomesa-web-search` está ativa com JWT obrigatório; requer `SEARXNG_BASE_URL` HTTPS configurado. A presença efetiva do segredo não foi confirmada nesta leitura. A função rejeita alguns padrões comuns de dados pessoais, mas nenhum filtro é completo.
- OCR de faturas é local. O resumo económico usa metadados aprovados/pendentes/rejeitados; aprovação pelo proprietário não prova pagamento nem dedutibilidade fiscal.
- A sincronização cloud do histórico/Memória está desligada por defeito e requer consentimento; a cópia cloud é protegida por RLS, mas não tem cifragem ponta a ponta.

## Supabase — estado real verificado

### Histórico de migrações

| Medida | Valor |
|---|---:|
| Ficheiros `supabase/migrations` nesta branch | 55 |
| Versões registadas no Supabase alojado | 63 |
| Prefixos comuns | 51 |
| Só locais | 4 |
| Só remotas | 12 |

**Migrações só locais:**
- `20261030111815_restaurant_recipes_and_order_stock_consumption`
- `20261030120000_inventory_unit_cost_and_waste_snapshots`
- `20261102103000_granular_restaurant_staff_permissions`
- `20261104100000_price_adviser_actor_indexes`

**Migrações só remotas:**
- `20261102082151_reconcile_inventory_unit_cost_and_waste_snapshots`
- `20261102103246_loyalty_invoice_qr`
- `20261102103627_loyalty_invoice_qr_foreign_key_indexes`
- `20261102104650_loyalty_invoice_qr_private_execution`
- `20261102113719_loyalty_shop_git_reconciliation_and_redemption_queue`
- `20261102113944_loyalty_shop_private_function_boundary`
- `20261108120141_restaurant_reactivation_campaigns`
- `20261108120201_reactivation_campaign_foreign_key_indexes`
- `20261108124820_fix_reactivation_campaign_conflict_target`
- `20261108125118_reactivation_campaign_owner_deletion`
- `20261108164151_customer_restaurant_discovery_and_membership_requests`
- `20261108164623_customer_membership_request_staff_self_join_guard`

51 prefixos comuns não provam que os conteúdos SQL são idênticos. Antes de qualquer deployment é necessário recuperar/validar as migrações remotas sem ficheiro, comparar checksums e estruturas, preparar backup/restauro e ensaiar uma sequência canónica num projeto separado.

### Tabelas e Storage verificados

| Objeto | Resultado observado |
|---|---|
| `public.restaurant_assistant_conversations` | Existe, RLS ativo; políticas de proprietário para SELECT/INSERT/UPDATE/DELETE |
| `public.restaurant_assistant_messages` | Existe, RLS ativo; política de proprietário |
| `public.restaurant_assistant_memory` | Existe, RLS ativo; política de proprietário |
| `public.restaurant_supplier_invoices` | Existe, RLS ativo; políticas de proprietário |
| `public.restaurant_orders` | Existe, RLS ativo |
| `public.restaurant_recipes` | Não encontrada |
| `public.restaurant_recipe_ingredients` | Não encontrada |
| `private.restaurant_staff_permissions` | Não encontrada |
| Storage `studiomesa-invoices` | Existe, privado (`public=false`), limite de 25 MB, PDF/JPEG/PNG/WebP |

As tabelas de receita/ingredientes e permissões granulares não devem ser anunciadas como funcionais com backend alojado enquanto o esquema estiver neste estado.

### Edge Functions

| Função | Estado observado | Significado |
|---|---|---|
| `studiomesa-assistant` | ACTIVE, JWT obrigatório, versão 10 | Devolve HTTP 410 e não recebe a conversa |
| `studiomesa-web-search` | ACTIVE, JWT obrigatório, versão 3 | Encaminha consultas públicas para SearXNG quando `SEARXNG_BASE_URL` está configurado |
| `delete-account` | ACTIVE, JWT obrigatório, versão 3 | Endpoint de eliminação autenticado |
| `account-deletion-web` | ACTIVE, acesso público GET, versão 3 | Página web; o fluxo completo de email/PKCE/eliminação necessita de E2E |

## Segurança e saúde

Observações do Supabase Security Advisor a 9/11/2026:
- 42 funções `SECURITY DEFINER` executáveis por `authenticated`. A revisão anterior confirmou checks `auth.uid()`, `search_path` vazio e ausência de acesso `anon`; as RPCs têm de continuar a ser testadas por operação e por restaurante. Não revogar em bloco.
- 11 tabelas com RLS mas sem política. Algumas são tabelas `private` intencionalmente fechadas; as tabelas públicas dependem de RPCs e privilégios diretos revogados, o que deve manter-se validado.
- Proteção de palavras-passe comprometidas desativada. O projeto está numa organização Supabase Free; resolver por uma configuração/plano suportado, sem assumir que uma palavra-passe foi comprometida.
- Performance Advisor: quatro FKs sem índice de cobertura e 45 índices sem utilização observada. Os índices não devem ser removidos com base em telemetria limitada.

A auditoria não é um pentest independente e não garante ausência de vulnerabilidades.

## Bloqueios de lançamento da app

1. **Reconciliar a base de dados** — resolver as quatro migrações locais e doze remotas, comparar conteúdo e estado do esquema e criar um plano de rollout/reversão. Não executar `db push` enquanto isto não estiver resolvido.
2. **Fichas técnicas e permissões granulares** — as tabelas exigidas não foram encontradas no Supabase alojado; a funcionalidade correspondente no código não está pronta para produção.
3. **Autenticação e email** — configurar credenciais próprias OAuth Google/Microsoft, SMTP, Redirect URLs e validar login, recuperação, logout, revogação e sessões em dispositivos reais.
4. **Testes ponta a ponta** — testar isolamento de dois restaurantes, proprietário/funcionário/cliente, reservas, pedidos, fidelização, inventário, faturas, sincronização em dois dispositivos, exportação e eliminação de conta.
5. **Privacidade e contratos** — responsável legal/controlador, morada, NIF, contacto, bases jurídicas, retenção, subcontratantes, DPA, termos B2B, política pública e Data Safety final.
6. **Release** — package IDs finais, estratégia Play (não criar cinco listagens semelhantes sem revisão), keystore definitiva, secrets protegidos, AAB/APKs assinados e verificação de assinatura.
7. **Operação** — backups e restauro ensaiado, processo de suporte/incidentes/atualizações, monitorização e testes físicos incluindo OPPO A5m.
8. **Integrações** — não existe integração genérica validada com POS, ERP, contabilidade, pagamentos, impressoras ou faturação certificada.

## Próximas verificações

- A CI integral da revisão #98 terminou com sucesso no commit `e3f9edff57c1c98c5a90e68cdb6654790ef9e765`; confirmar novamente o estado da PR antes de integrar.
- Depois da integração, exigir nova CI completa em `main` e produzir uma nova build de demonstração para os testes físicos.
- Não executar `db push` nem assinar/distribuir a versão comercial enquanto a divergência de migrações, os bloqueios legais/Auth e a chave definitiva não estiverem resolvidos.
- Resolver os bloqueios de base de dados e lançamento por etapas, sem alterar a produção por tentativa.
