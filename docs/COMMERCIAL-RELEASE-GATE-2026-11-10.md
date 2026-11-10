# StudioMesa — gate de lançamento comercial

**Data:** 10 de novembro de 2026  
**Código principal auditado:** `52f858ad293f4ce557758fab60fdcc3a9321d6cf` (merge da PR #105 em `main`).  
**Revisão de segurança:** PR #105 — `release/commercial-hardening-2026-11-10`, integrada por squash.  
**CI da PR #105:** execução `42782322112`, commit `4588aae932e880146094a0385392440b914bae5b`, todos os quatro jobs concluídos com sucesso.  
**CI mais recente em `main`:** [execução `42792956250`](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/42792956250), commit `0177c6aa3e62c5e70b20f620be4576858ab67837`; os quatro jobs terminaram com sucesso. Incluiu build/testes das cinco variantes, Android Lint, auditorias de segurança, replay PostgreSQL/autorização e smoke tests em Android 8 e 16. O artefacto APK é de demonstração/debug, não uma release comercial assinada.  
**Decisão:** **NÃO LIBERADO para piloto com dados reais, venda ou publicação.**

## O que está verificado

- [x] Estado do projeto Supabase consultado em modo read-only.
- [x] 26 tabelas nos esquemas `public` e `private` observadas; todas com RLS ativo.
- [x] Sem acesso direto `anon` a tabelas consultadas.
- [x] Bucket de faturas privado, com limite de 25 MiB e lista de MIME types restrita.
- [x] Políticas Storage consultadas: SELECT/INSERT/DELETE apenas por utilizador autenticado proprietário do restaurante do caminho.
- [x] Cliente Android usa URL HTTPS e chave de tipo publicável; não foram encontrados tokens `service_role`/segredos no código do cliente.
- [x] Auth/Storage/Edge Functions foram consultados; as Edge Functions de eliminação/pesquisa exigem JWT; a função cloud de IA está desativada (HTTP 410).
- [x] Histórico/Memória locais cifrados; sincronização cloud opcional e desligada por defeito, sem E2E.
- [x] OCR local com revisão humana obrigatória; não aprova/paga documentos automaticamente.
- [x] Análise semanal, previsão de sete dias, feriados nacionais, eventos locais configuráveis, padrões de maior afluência e cálculos económicos locais estão presentes na `main`, com testes associados. A comparação da PR #100 confirmou que a proposta antiga contém versões já existentes e versões ultrapassadas; a PR #100 foi fechada sem merge para proteger as melhorias posteriores do assistente.
- [x] CI final da PR #105 (`42782322112`): compilação das cinco variantes, testes unitários de todas as variantes, Android Lint, validação da configuração comercial, replay PostgreSQL/autorização e smoke tests Android 8 e Android 16 — todos concluídos com sucesso.
- [x] PR #105 integrada na `main` por squash no commit `52f858ad293f4ce557758fab60fdcc3a9321d6cf`.
- [x] Auditoria read-only do Supabase às 10:06 UTC de 10/11/2026: projeto `ACTIVE_HEALTHY`, 63 migrações remotas, 26 tabelas com RLS ativo e bucket de faturas privado. A inspeção confirmou que as três tabelas opcionais continuam ausentes e que não existem privilégios diretos `anon`/`authenticated`/`PUBLIC` nas 11 tabelas sem políticas assinaladas pelo Advisor.
- [x] Advisor atual consultado: 42 funções `SECURITY DEFINER` executáveis por `authenticated`, 11 tabelas RLS sem políticas, proteção de palavras-passe comprometidas desativada, quatro FKs sem índice de cobertura e 45 índices sem utilização observada. Os avisos exigem revisão individual; não foram aplicadas revogações ou alterações de esquema.
- [x] CI mais recente da `main` (`42792956250`, commit `0177c6aa3e62c5e70b20f620be4576858ab67837`): os quatro jobs passaram. Os testes incluem build e testes unitários das cinco variantes, Android Lint, auditorias de segurança, replay de migrações/autorização e smoke tests Android 8/16. Os APKs publicados como artefacto são debug/demo.

### Backend incompatível com funcionalidades opcionais

A consulta read-only de 10/11/2026 confirmou que o projeto alojado não tem as tabelas `restaurant_recipes`, `restaurant_recipe_ingredients` e `private.restaurant_staff_permissions`. Também não existem as RPCs de leitura/alteração de permissões granulares nem as RPCs de gestão de ingredientes/ativação de receitas. A interface Android passou a ocultar a configuração de receitas e a não apresentar o botão de edição de permissões por área; apresenta um aviso explícito em vez de simular uma operação. Os funcionários continuam em modo *fail closed*: sem a resposta da autorização do servidor não recebem permissões por omissão. Estas funcionalidades só devem ser reativadas depois da reconciliação e do deployment aprovado do esquema, mais testes com contas de dois restaurantes.

## Bloqueios obrigatórios

### Backend e dados
- [ ] Reconciliar 55 migrations locais com 63 versões remotas; recuperar 12 remotas, rever as 4 locais que faltam e comparar o SQL integral das 51 versões comuns.
- [ ] Produzir backup e demonstrar restauro num projeto separado antes de qualquer alteração ao projeto alojado.
- [ ] Resolver/deployar com sequência canónica as tabelas em falta: recipes, recipe ingredients e granular staff permissions.
- [ ] Rever as 42 funções `SECURITY DEFINER` sinalizadas pelo Supabase Advisor individualmente. Não revogar EXECUTE em bloco.
- [ ] Resolver os quatro índices de foreign keys sinalizados após avaliar planos de execução e carga real; não remover os 45 índices marcados “unused” sem evidência.
- [ ] Decidir como tratar a proteção de palavras-passe comprometidas desativada. Confirmar plano/configuração suportados; não assumir que é grátis.

### Autenticação e ponta a ponta
- [ ] Configurar SMTP e confirmação de email.
- [ ] Configurar credenciais OAuth Google/Microsoft e Redirect URLs.
- [ ] Testar registo, login, refresh, logout, expiração/revogação, recovery PKCE/OTP, troca de dispositivo e mudança de conta.
- [ ] Testar com contas descartáveis de dois restaurantes: proprietário A/B, funcionário A, cliente A/B e acesso negado entre tenants.
- [ ] Testar E2E reservas, pedidos, cozinha, fidelização, resgates, campanhas e consentimento de marketing.
- [ ] Testar eliminação de conta pessoal e eliminação de restaurante, including timeouts, retries e rollback.

### Privacidade, segurança e legal
- [ ] Rever todas as 42 funções privilegiadas e todos os caminhos de acesso a dados de clientes; pentest independente antes de escala comercial.
- [ ] Validar logs, rate limits, abuso de RPCs, quotas, exportação, retenção, eliminação e incident response.
- [ ] Fazer teste de sincronização entre dois dispositivos, rede interrompida, conflitos, tombstones e conta/restaurante cruzados.
- [ ] Finalizar política de privacidade, Data Safety, DPA, subcontratantes, localização/tratamento dos dados, períodos de retenção e contactos da entidade.
- [ ] Rever a LFM Open License v1.0 e licenças/transitivos de todas as dependências para entidade que vai vender.
- [ ] Gerar SBOM/notices e confirmar direitos sobre código, marca, logótipo, fontes, ícones e materiais.

### Identidade e distribuição
- [ ] Definir package ID final — builds atuais usam `pt.studiomesa.preview.*`.
- [ ] Criar keystore comercial definitiva e backups offline; configurar os GitHub Actions secrets/variables próprios.
- [ ] Executar workflow comercial com confirmação explícita `BUILD`, apenas sobre `main` com CI integral verde e assinatura válida.
- [ ] Confirmar AABs/APKs comerciais das variantes, checksums, versionamento, IDs finais, política e listagens Google Play ou canal B2B.
- [ ] Testar atualização sobre uma versão anterior e distribuição final nos dispositivos abrangidos.

### Dispositivo físico e operação
- [ ] Testar no OPPO A5m o motor LFM2.5 real, memória, latência, temperatura, bateria, downloads, modo offline e chamadas às ferramentas.
- [ ] Testar instalação/atualização Android física em aparelhos representativos.
- [ ] Definir backup operacional, monitorização, suporte, correções e processo de resposta a incidentes.

## O que pode ser demonstrado hoje

Pode ser usada uma APK debug/demo com dados fictícios, identificada claramente como demonstração. Não se devem carregar dados pessoais, faturas reais ou informação de clientes na versão demo.

## O que não deve ser prometido

Não anunciar receitas/permissões granulares enquanto as respetivas tabelas não estiverem no backend alojado. Não afirmar que a cloud é E2E cifrada. Não dizer que o modelo local é equivalente ao ChatGPT. Não afirmar que reservas, pedidos ou valores nominais confirmam pagamentos contabilísticos.

**Não executar `db push`, reset, rebase/merge de branch Supabase, deployment de funções, configuração de Auth/Storage ou alteração de plano até todos os pré-requisitos acima terem sido aprovados.**
