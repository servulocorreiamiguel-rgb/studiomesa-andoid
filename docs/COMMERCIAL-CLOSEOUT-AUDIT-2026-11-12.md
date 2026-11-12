# StudioMesa — auditoria consolidada de preparação comercial

**Data:** 12 de novembro de 2026  
**Branch principal verificada:** `main`  
**Commit mais recente:** `c5417529a797d0d63856dffda1effb3433d33f1a`  
**Decisão:** **ainda não autorizada para operação com dados reais nem venda operacional.** O código principal e as correções recentes estão integrados; o que falta inclui configuração do ambiente, reconciliação segura do backend, testes de aceitação no dispositivo e preparação jurídica/comercial.

## 1. Alterações de código e CI

- PR #119 integrada: documentação e mensagens de permissões de funcionários alinhadas com o comportamento real. Até a matriz granular ser validada e ativada, os funcionários mantêm o acesso operacional legado; o proprietário ainda não consegue retirar áreas individualmente.
- PR #120 integrada no commit `e2b23b65416a600e2fb1d8a699cba1a0d37ec9d6`: os índices de `restaurant_membership_requests` passaram para uma migração posterior à criação da tabela. A migração `20261104100000_price_adviser_actor_indexes.sql` mantém apenas os dois índices das tabelas do consultor de preços.
- CI da PR #120, execução [43219206624](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/43219206624): os quatro jobs passaram.
- PR #121 consolidou esta auditoria e foi integrada no commit `7350c02bfee437dd7b53879ada8327700798622e`; os quatro jobs passaram.
- PR #122 integrou os entitlements comerciais server-side e os testes de segurança por plano no commit `c5417529a797d0d63856dffda1effb3433d33f1a`. A execução [43241011598](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/43241011598) passou nos quatro jobs: compilação APK, replay de migrações/autorização PostgreSQL, Android 8 e Android 16.
- A CI pós-merge da `main`, execução [43247282954](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/43247282954), tinha o replay PostgreSQL/autorização concluído com sucesso; a compilação Android ainda estava em execução na última consulta. Não declarar esta CI pós-merge completa antes de terminar.
- Existem artefactos APK debug/demo. Não são assinados com a chave comercial e não constituem uma release de produção.

## 2. Migrações e estado do Supabase

Projeto `svwvkfsyevuyabelrtjk`, região `eu-west-1`, estado `ACTIVE_HEALTHY`, PostgreSQL 17.6.1.166.

Comparação read-only efetuada em 12/11/2026:
- **70** ficheiros SQL de migração no repositório.
- **63** versões no histórico Supabase.
- As **63** versões remotas têm correspondência local com a mesma versão e nome.
- Não há versões remotas sem ficheiro local.
- Existem **sete migrações locais ainda não aplicadas** no projeto alojado:

```text
20261030111815_restaurant_recipes_and_order_stock_consumption
20261102103000_granular_restaurant_staff_permissions
20261102120000_require_loyalty_permission_for_reward_fulfillment
20261102121000_enforce_loyalty_permission_for_staff_loyalty_operations
20261104100000_price_adviser_actor_indexes
20261107170000_loyalty_shop_photos_and_plan_limits
20261112120000_restaurant_membership_request_actor_indexes
```

O replay integral da sequência local e os testes de autorização passaram em PostgreSQL isolado na CI da PR #120. Isto valida a sequência local, não prova a igualdade total do SQL remoto nem autoriza a aplicação em produção.

As tabelas `public.restaurant_recipes`, `public.restaurant_recipe_ingredients`, `private.restaurant_staff_permissions` e `private.restaurant_loyalty_entitlements` continuam ausentes no Supabase alojado. Por isso, fichas técnicas, consumo automático de ingredientes, permissões granulares e aplicação real dos limites comerciais por plano ainda não estão ativos no backend alojado.

Não existe branch Supabase de desenvolvimento. O preço atual consultado para a criar é **0,01344 USD por hora**. Não foi criada nem será criada sem autorização explícita para esse custo. Nenhuma migração ou DDL foi aplicada à base de produção nesta ronda.

## 3. Segurança e desempenho observados

O Security/Performance Advisor foi consultado sem executar alterações:
- 11 tabelas com RLS ativa sem políticas diretas. As tabelas privadas e as tabelas explicitamente fechadas devem continuar sem acesso direto por `anon`/cliente; não criar políticas genéricas para silenciar o aviso.
- 42 funções `SECURITY DEFINER` executáveis pelo papel `authenticated`. A presença de `auth.uid()` e `search_path = ''` não substitui a revisão individual de autorização, tenant, parâmetros e concorrência.
- Proteção contra palavras-passe comprometidas desativada no Auth.
- O Advisor continua a reportar quatro foreign keys sem índice no projeto alojado. A correção correspondente já existe nas migrações locais, mas só produzirá efeito no projeto remoto depois da reconciliação e aplicação controlada.
- 45 índices aparecem como não utilizados no momento da análise; não os remover automaticamente sem tráfego real e medição.

## 4. IA e serviços alojados

- O assistente local mantém Gemma 4 E2B como motor preferido em dispositivos compatíveis e LFM2.5-1.2B INT4 como alternativa/reserva instalável. A transferência verifica tamanho e SHA-256.
- A Mega IA local ainda não foi validada fisicamente em OPPO A5m: carga do modelo, qualidade, latência, RAM, temperatura, bateria, libertação de memória e fallback.
- A Edge Function `studiomesa-ai-router` está ativa, versão 1, com `verify_jwt=true`. O segredo de servidor `OPENROUTER_API_KEY` não foi confirmado; a cloud permanece desligada por omissão. Não anunciar IA cloud ativa ou ilimitada.
- A utilização cloud exige testes autenticados com owner, funcionário, sessão expirada, filtro de dados, privacidade, quotas e fallback exclusivamente gratuito.
- A pesquisa Web depende de `SEARXNG_BASE_URL` HTTPS configurado. A presença desse segredo e o funcionamento real do endpoint não foram confirmados.
- Google/Microsoft permanecem ocultos por decisão de produto para a primeira venda. O login inicial é email/palavra-passe e exige SMTP próprio, confirmação e recuperação testados.

## 5. Produto, distribuição e modelo comercial

As cinco variantes de demonstração são:
- Essencial — Gestão;
- Profissional — Gestão e Cliente;
- Premium — Gestão e Cliente.

Estas variantes são builds técnicas. A PR #122 implementou no código e nas migrações o entitlement validado no servidor: o restaurante sem atribuição é Essencial; reservas e consultor de preços exigem Profissional ou Premium; fotos dos prémios exigem Profissional/Premium; stock, movimentos, receitas/consumo automático e arquivo de faturas exigem Premium; limites de prémios ativos são 5/15/50. As políticas RLS e os triggers também bloqueiam leituras/escritas e downgrades incompatíveis. **Esta proteção ainda não está ativa no Supabase alojado**, porque a migração `20261107170000_loyalty_shop_photos_and_plan_limits.sql` continua pendente. A atribuição do plano também ainda não tem painel comercial nem integração de pagamentos; deve ser feita apenas por operação administrativa privilegiada. O documento `RELEASE-SETUP.md` alerta que cinco listagens Play com apps muito semelhantes podem violar a política de conteúdo repetitivo; uma opção a avaliar é separar Gestão e Cliente e aplicar planos por entitlement seguro, ou distribuir B2B privadamente. Não publicar cinco fichas só porque o Gradle gera cinco variantes.

O workflow `.github/workflows/commercial-release.yml` está preparado para uma execução manual e protegida. Exige:
- CI verde na `main` para o commit exato;
- confirmação manual `BUILD`;
- `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`;
- variável `STUDIOMESA_RELEASE_APPLICATION_ID` com o identificador base definitivo.

Ainda não foram fornecidos nem configurados o identificador final e a keystore privada do proprietário. Não criar, escolher nem publicar estes segredos em nome do utilizador. O workflow gera artefactos assinados, mas não publica automaticamente na Google Play.

O plano de negócio tem valores de referência de 890 €, 1 490 € e 1 690 €; não são preços validados com clientes nem propostas vinculativas. O âmbito do contrato tem de explicar pagamento, alojamento, suporte, manutenção, atualizações e impostos.

## 6. Testes e preparação jurídica ainda necessários

- Configurar SMTP/domínio e executar registo, confirmação, recuperação, refresh, logout e eliminação de conta com contas descartáveis.
- Criar ambiente Supabase isolado, depois de autorizada a despesa, reconciliar/aplicar as sete migrações, executar advisors e testes de autorização antes de qualquer promoção para produção.
- Testar dois restaurantes e identidades distintas (owner, staff, cliente), isolamento de dados, reservas, pedidos, cozinha, inventário, fidelização, campanhas, faturas, sincronização, rede intermitente, sessão expirada e revogação.
- Testar fisicamente no OPPO A5m e em dispositivos Android representativos; a CI de emuladores não substitui estes testes.
- Rever uma a uma as funções privilegiadas e documentar retenção, backup/restauro, incidentes, exportação e eliminação.
- Finalizar política de privacidade, termos, DPA, subcontratantes, declaração Data Safety, suporte e revisão das licenças da IA. A LFM Open License v1.0 e as obrigações de atribuição/limite comercial devem ser verificadas pela entidade que comercializa.

## 7. Critério de fecho

A StudioMesa só deve ser declarada pronta para operação comercial após:
1. CI integral verde na `main`;
2. migrações reconciliadas e testadas numa branch Supabase isolada, incluindo a migração dos entitlements, antes de produção;
3. Auth/SMTP e isolamento multi-restaurante testados;
4. testes E2E e físicos aprovados;
5. escolha documentada da distribuição/entitlements e identificadores;
6. release assinada com chave privada controlada pelo proprietário;
7. documentos legais, licenças, suporte, backups e procedimento de incidentes revistos.

**Estado atual:** pronta para continuar o desenvolvimento e para demonstração com dados fictícios; não está pronta para operar com dados reais nem para ser vendida como plataforma operacional. Os testes que o utilizador fará no telemóvel serão uma fase necessária, mas não substituem a configuração de backend, a revisão legal nem a release assinada.
