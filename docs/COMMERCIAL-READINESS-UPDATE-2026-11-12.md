# StudioMesa — atualização de integração e preparação comercial

**Data:** 12 de novembro de 2026  
**Commit de integração de código na `main`:** `94a41413336678c9078fef9e21e0c4951cb8edaf`  
**Decisão:** **não liberada para operação com dados reais nem venda comercial final.** A Mega IA foi integrada e a Edge Function foi publicada com JWT obrigatório; faltam a chave do fornecedor, ativação cloud controlada, testes físicos, autenticação real e preparação de release.

## O que ficou integrado

- PR [#113](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/pull/113) integrada na branch `main`.
- Uma só conversa e um só assistente, sem escolha manual de motores.
- Router online free-only: DeepSeek R1 para raciocínio; Cohere North Mini Code e Qwen3.8 27B `:free` para programação; GPT-OSS 20B `:free` e OpenRouter Free Router como alternativas. GPT-OSS 120B não é chamado.
- O router pede `data_collection: deny`, `zdr: true` e `allow_fallbacks: false`; exige autenticação Supabase e papel `owner`. Só aceita perguntas gerais escritas pelo utilizador e rejeita contexto privado, documentos e identificadores comuns. Os filtros não garantem deteção de todos os dados sensíveis.
- IA local: Gemma 4 E2B, ficheiro de 2,59 GB e SHA-256 fixado; LFM2.5-1.2B INT4, 736 MB, como alternativa de menor dimensão. Existe opção para instalar o LFM como reserva adicional em equipamentos com Gemma. O fallback automático só é possível se o LFM estiver instalado e validado.
- Google e Microsoft continuam ocultos por defeito, conforme a decisão de adiar OAuth social até depois da primeira venda.
- A configuração versionada em `supabase/config.toml` define `studiomesa-ai-router` com `verify_jwt = true`.

## Validação CI na branch principal

Execução [43186787267](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/43186787267), no commit acima:

| Verificação | Resultado observado |
|---|---|
| Compilação das variantes de demonstração | Passou |
| Testes unitários das cinco variantes | Passaram |
| Android Lint | Passou |
| Validação do workflow de release | Passou; não criou release comercial |
| Replay de migrations numa base PostgreSQL isolada | Passou |
| Testes de autorização/idempotência nessa base | Passaram |
| Smoke tests Android 8 / API 26 | Passaram |
| Smoke tests Android 16 / API 36 | Passou |

O workflow publicou cinco APKs `debug/demo` no artefacto `StudioMesa-1.5.0-Demo-APKs`. Não têm assinatura comercial definitiva. A compilação não prova autenticação por email, configuração real do Supabase, performance em equipamento físico ou adequação legal.

## IA online — configuração ainda em falta

- A Edge Function `studiomesa-ai-router` foi publicada no projeto Supabase `svwvkfsyevuyabelrtjk`: versão 1, estado `ACTIVE`, `verify_jwt=true`, com SHA-256 da implementação `7291c4699512f25161c03ff9d2bc2139ce1607ac407d6505d66cb231b14eae4a`.
- A chave `OPENROUTER_API_KEY` é necessária em segredo de servidor; a sua presença/configuração não foi confirmada com as ferramentas disponíveis. Sem ela, o código responde `MODEL_NOT_CONFIGURED` (503) e não chama fornecedores.
- A propriedade Gradle `studiomesaAdvancedAssistantCloudEnabled` continua falsa por omissão. Não a ativar antes de confirmar/configurar a chave no secret store e executar testes autenticados de owner/funcionário/token expirado, filtros de dados, privacidade, quotas e fallback.
- As variantes gratuitas OpenRouter estão sujeitas a disponibilidade e limites partilhados pela chave de serviço; não prometer serviço online ilimitado.

## Bloqueios comerciais que permanecem

1. **Supabase/migrações:** a auditoria de 10/11 registou 61 ficheiros de migração locais contra 63 versões remotas, com versões diferentes entre ambos. Não executar `db push`, `migration repair`, reset ou deployment de schema até comparar integralmente SQL/objetos, obter backup e comprovar restauro em ambiente separado. Continuam ausentes `restaurant_recipes`, `restaurant_recipe_ingredients` e `restaurant_staff_permissions`.
2. **Autenticação:** configurar SMTP próprio/domínio e testar registo, confirmação, recuperação, refresh, logout e eliminação de conta com contas descartáveis. Google/Microsoft estão deliberadamente adiados.
3. **E2E e autorização:** testar dois restaurantes com owners, staff e clientes; pedidos, reservas, cozinha, stock, fidelização, campanhas, faturas, sincronização, perda de rede, repetição, sessão expirada e revogação de permissões.
4. **Testes físicos:** OPPO A5m e dispositivos Android representativos, incluindo instalação/atualização, Gemma/LFM, RAM, latência, bateria, temperatura e libertação de memória.
5. **Segurança:** rever as RPCs privilegiadas uma a uma; manter fechadas as tabelas privadas sem políticas diretas. O Advisor continua a reportar proteção contra palavras-passe comprometidas desativada (o projeto está no plano Free), além de avisos de SECURITY DEFINER que exigem revisão contextual; não revogar permissões em bloco.
6. **Release comercial:** definir package ID final, criar keystore privada, backup seguro da chave, GitHub Actions secrets/variables, executar workflow manual comercial, verificar assinatura/checksums e testar atualização. Os artefactos atuais são de demonstração.
7. **Legal/privacidade:** terminar política pública, termos, DPA, retenção, subprocessadores, declaração Data Safety e revisão das licenças pela entidade que vende. Gemma 4 usa Apache 2.0; LFM2.5 usa LFM Open License v1.0, com limiar/obrigações comerciais a verificar.

## Próximos passos técnicos seguros

1. Concluído: smoke tests Android 16 passaram na CI 43186787267. Prosseguir para testes físicos e E2E.
2. Manter IA cloud desligada até confirmação/configuração da secret e testes autenticados da função já publicada.
3. Reconciliar o Supabase em ambiente separado com backup/restauro confirmado; não alterar o schema alojado por tentativa.
4. Fazer os testes E2E e físicos com dados fictícios/contas descartáveis.
5. Só então criar a release assinada definitiva e autorizar o piloto/venda.

**Conclusão:** o código da Mega IA foi integrado e a CI principal passou as etapas de build, testes unitários, Lint e PostgreSQL. Existem APKs de demonstração instaláveis. A StudioMesa ainda não está pronta para receber dados reais ou ser vendida como plataforma operacional, porque os bloqueios indicados continuam abertos.
