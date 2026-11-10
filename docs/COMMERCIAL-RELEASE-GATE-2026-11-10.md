# StudioMesa — gate de lançamento comercial

**Data:** 10 de novembro de 2026  
**Código principal auditado:** `2ce4ad461ada5f0881f9d3c7f6f9d7d547cefd28`  
**Revisão de segurança:** PR #102 — `release/security-hardening-2026-11-10`  
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
- [x] CI do código principal anteriormente passou build, 5 variantes, testes unitários, Android Lint, replay PostgreSQL/autorização e emuladores Android 8/16.
- [ ] CI de PR #102 no commit final ainda tem de passar todos os jobs antes de integrar o hardening do Storage, os limites dos corpos JSON e a paginação do histórico do assistente.

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
