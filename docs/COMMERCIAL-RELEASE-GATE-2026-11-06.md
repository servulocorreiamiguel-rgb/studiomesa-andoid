# StudioMesa — gate de lançamento comercial

**Fotografia:** 6 de novembro de 2026  
**Estado:** NÃO LIBERADO PARA VENDA/PRODUÇÃO

Este documento é a lista única de saída para as cinco variantes. Um checkbox só pode ser marcado quando existir prova verificável no repositório, CI, configuração do fornecedor ou teste E2E. Presença de um botão não prova que o backend esteja funcional.

## Estado técnico confirmado

| Área | Estado |
|---|---|
| Android | Kotlin nativo; cinco variantes; minSdk 24; targetSdk 36; versionName 1.5.0. |
| CI | Workflow #1332 totalmente verde: replay PostgreSQL/autorização, build das variantes, unit tests, Lint e smoke tests Android 8/16. PR #86 integrada na main no commit `0770ad7`. Os skips de teste ocorrem nas variantes sem a página correspondente. |
| Reservas | Regressões de idempotência, acesso por restaurante, capacidade, mesa inexistente, horários sobrepostos e retry após atribuição/confirmada passaram no PostgreSQL descartável da CI #1332. |
| IA | LFM2.5 local; histórico/Memória locais cifrados. Sync cloud está no código, mas falta deploy das tabelas no Supabase alojado. |
| Faturas | OCR local; metadados locais cifrados. Tabela e bucket cloud ainda ausentes no Supabase alojado. |
| Receita/consumo de stock | Código/migration local existente; tabelas de receitas ausentes no Supabase alojado. |
| Permissões da equipa | Matriz granular no código/migration local; tabela privada ausente no Supabase alojado. |
| Supabase | 53 migrations locais, 54 remotas, 48 comuns, cinco só locais e seis só remotas. Não fazer deploy antes da reconciliação, backup/restauro e replay separado. |
| Assinatura | Workflow para cinco AABs e cinco APKs assinados existe; falta package ID final, keystore e secrets/variable. Os APKs debug não são release comercial. |
| Publicação | Workflow não publica automaticamente na Google Play. |

## Gate A — produto e funcionalidades

- [x] Smoke tests de CI Android 8/API 26 e Android 16 terminaram com sucesso na CI #1332 da PR #86, agora integrada na main.
- [ ] Testes E2E com contas descartáveis: cliente, proprietário, funcionário e dois restaurantes; login, recuperação, logout, revogação, reservas, pedidos, mesas, carta, stock, fidelização, permissões, exportação e eliminação.
- [ ] Fichas técnicas e consumo automático de ingredientes validados após a migration aprovada estar alojada.
- [ ] Sincronização do assistente validada em dois dispositivos e conflitos/tombstones/eliminação validados após migration aprovada.
- [ ] OCR e arquivo de faturas validados com documentos anonimizados; testar duplicados, NIF inválido, totais incoerentes, MIME inválido, ficheiro truncado, perda de rede e permissões SAF revogadas.
- [ ] Definidos horários de funcionamento, intervalos, duração padrão, capacidade de sala e regras de cancelamento das reservas.
- [ ] Integrações POS/ERP/pagamentos/impressão descritas como não incluídas até existir fornecedor/API e teste de aceitação.
- [ ] Testes físicos OPPO A5m por fazer e reservados ao utilizador; não bloquear o restante trabalho, mas não declarar o dispositivo validado.

## Gate B — backend, segurança e continuidade

- [ ] Recuperar/validar seis SQL remotos sem ficheiro e comparar cinco migrations locais sem prefixo remoto.
- [ ] Backup verificável e restauro ensaiado num ambiente separado.
- [ ] Replay da sequência canónica final numa base descartável, com RLS, grants, RPCs, triggers, índices e isolamento entre restaurantes.
- [ ] Plano de deployment/reversão e aprovação explícita antes de qualquer alteração ao Supabase alojado.
- [ ] Rever as 31 funções SECURITY DEFINER assinaladas pelo Security Advisor, sem revogar grants em bloco; cobrir cada grupo com testes negativos.
- [ ] Resolver o alerta Leaked Password Protection Disabled por configuração suportada/decisão de plano explícita; não assumir que o projeto está seguro enquanto estiver desativado.
- [ ] Configurar e testar SMTP, confirmação de email, reset de palavra-passe, URLs de callback e OAuth que forem apresentados como disponíveis.
- [ ] Testar eliminação de conta por email e por dentro da app com contas descartáveis; confirmar proprietário, funcionário, cascatas, anonimização, expiração e recurso web.
- [ ] Definir frequência/retention de backups, executar restauro, política de incidente, canal de suporte e monitorização.

## Gate C — privacidade, legalidade e terceiros

- [ ] Identificar o responsável legal/controlador, morada, NIF e email profissional de privacidade.
- [ ] Definir papéis de responsável/subcontratante por finalidade, DPA e lista de subcontratantes/regiões.
- [ ] Definir bases jurídicas, retenção e eliminação por categoria, exportação, pedidos de titulares e resposta a incidentes.
- [x] Adicionada à app uma área «Privacidade e dados» na Gestão e na Conta da app Cliente; é um resumo técnico, não a política completa.
- [ ] Identificar o responsável legal, completar a política, publicá-la numa URL estável e ligar o URL na app/ficha Play; substituir todos os placeholders do rascunho.
- [ ] Rever e submeter Data Safety com base no AAB exato, no código final, SDKs e configuração cloud ativa.
- [ ] Revisão jurídica dos termos B2B, licença de utilização, contrato/DPA, IVA, preços, suporte e limitações.
- [ ] Gerar SBOM e notices das dependências transitivas; rever licenças do modelo LFM Open v1.0, LiteRT-LM, ML Kit e todas as bibliotecas/ativos.
- [ ] Confirmar titularidade dos direitos de código, marca, logótipo, fontes, ícones e materiais.

## Decisão de distribuição na Google Play

**Não assumir que os cinco artefactos Gradle correspondem a cinco fichas Play.** A política atual da Google proíbe publicar múltiplas aplicações com funcionalidade, conteúdo e experiência altamente semelhantes; para apps white-label recomenda fichas distintas e valor/identidade real por app, mas isso não substitui a política de conteúdo repetitivo.

A recomendação técnica inicial é avaliar **duas aplicações com propósitos realmente distintos** (Gestão para proprietários/equipa e Cliente para consumidores), e controlar os planos Essencial/Profissional/Premium através de direitos/entitlements validados no backend, não apenas através de uma variante APK que o utilizador pode instalar. Isto exige implementação e testes adicionais no Supabase, que não devem ser feitos antes da reconciliação de migrations e do modelo de licenciamento. Não é uma decisão comercial final.

As três variantes de Gestão e duas de Cliente podem continuar a existir como artefactos de demonstração/QA enquanto a estratégia não estiver fechada. Não criar cinco listagens Play com diferenças apenas de plano/limite sem uma revisão de elegibilidade.

Alternativas a avaliar:
- Duas listagens com entitlement/licença do restaurante validada no servidor.
- Distribuição privada/gerida ou entrega B2B de builds dedicadas, após assinatura e suporte.
- Builds dedicadas por cliente apenas quando exista valor, identidade, conteúdo e experiência realmente próprios e compatíveis com as políticas da Play.

Fontes oficiais consultadas em 06/11/2026:
- [Política de conteúdo repetitivo e funcionalidade](https://support.google.com/googleplay/android-developer/answer/18265346).
- [Boas práticas para programadores de apps white-label](https://support.google.com/googleplay/android-developer/answer/15884185).
## Gate D — identidade, assinatura e Google Play

- [ ] Definir a entidade que vende/contrata e o titular adulto da conta de programador; o titular tem de ter pelo menos 18 anos.
- [ ] Decidir a estratégia Play. Não assumir cinco listagens semelhantes; avaliar duas apps (Gestão/Cliente) e entitlements de plano no servidor ou distribuição white-label descentralizada por cliente. Rever a política de conteúdo repetitivo antes de definir package IDs.
- [ ] Criar keystore de release fora do repositório; guardar duas cópias offline seguras e passwords num gestor de segredos.
- [ ] Configurar os GitHub Actions secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` e a variable `STUDIOMESA_RELEASE_APPLICATION_ID` sem expor os valores em commits/conversas.
- [ ] Executar o workflow comercial a partir de `main` com CI completa verde, validar cinco AABs e cinco APKs com assinatura e conservar checksums.
- [ ] Criar apenas as fichas compatíveis com a arquitetura e políticas aprovadas; usar listagens, ícones, imagens e descrições próprios que correspondam à funcionalidade real.
- [ ] Preparar ícones e screenshots reais, descrição individual, classificação de conteúdo, público-alvo, contactos, página de privacidade, URL de eliminação, Data Safety e credenciais de revisão.
- [x] targetSdk 36 no código atual (mínimo Play para novas apps/updates desde 31/08/2026); confirmar no AAB final.
- [ ] Se a conta for pessoal criada após 13/11/2023, cumprir 12 testers em teste fechado por 14 dias contínuos e verificação de dispositivo físico pelo Play Console.
- [ ] Se publicar em nome de uma organização/atividade empresarial, usar o tipo de conta correto e validar o requisito D-U-N-S.
- [ ] O titular de uma conta Play Console tem de ter pelo menos 18 anos. A taxa de registo oficial é 25 USD, uma única vez. Se a conta for pessoal e tiver sido criada após 13/11/2023, aplicar o requisito de teste fechado de pelo menos 12 testers inscritos continuamente durante 14 dias antes de pedir acesso à produção.
- [ ] Passar pela revisão de políticas e publicação Play; o workflow interno apenas gera artefactos e não publica.

## Valores que têm de ser fornecidos pelo responsável legal

- [ ] Nome legal/entidade e NIF.
- [ ] Morada e contacto profissional.
- [ ] Titular adulto da conta Play Console ou entidade com capacidade para a deter.
- [ ] Package IDs definitivos e decisão de cinco fichas separadas ou arquitetura alternativa.
- [ ] Preços, modalidade de pagamento, IVA, suporte, manutenção e alojamento.
- [ ] SMTP, OAuth e configuração do serviço de backend aprovados.
- [ ] Keystore e credenciais de assinatura criadas e guardadas fora do repositório.
- [ ] Identificação do primeiro POS/ERP se a proposta incluir integração.

## Documentos comerciais preparados nesta branch

- [x] Planos e matriz de funcionalidades: `SALES-PLANS-DRAFT.md`.
- [x] Proposta/orçamento: `COMMERCIAL-QUOTATION-TEMPLATE.md`.
- [x] Onboarding e aceitação: `COMMERCIAL-ONBOARDING-CHECKLIST.md`.
- [x] Termos B2B, licença de software e DPA: `TERMS-OF-SERVICE-TEMPLATE.md`, `B2B-SOFTWARE-LICENSE-TEMPLATE.md` e `DATA-PROCESSING-AGREEMENT-TEMPLATE.md`.
- [x] Avisos preliminares de terceiros: `THIRD-PARTY-NOTICES.md`; SBOM transitivo e revisão legal ainda pendentes.
- [x] Estratégia de distribuição Play: `docs/PLAY-DISTRIBUTION-STRATEGY-2026-11-06.md`.
- [x] PR draft #87 criada; aguardando CI do head final antes de marcar pronta ou integrar.

## Decisão de saída

**Pode ser demonstrada:** versão debug/APK de demonstração com dados fictícios e ambiente claramente identificado.

**Pode ser pilotada com dados reais:** apenas depois dos Gates A, B e C concluídos, backend reconciliado e configurado, contas E2E testadas, DPA/privacidade/backup/suporte aprovados.

**Pode ser vendida/publicada:** apenas depois dos Gates A–D concluídos, release AAB/APK assinado verificado, documentação legal aprovada e responsabilidades comerciais aceites.

**Estado desta fotografia:** não cumpre ainda os critérios de piloto nem venda. A CI e o código estão avançados; há bloqueios de backend, Auth, legalidade, assinatura e configuração que não podem ser resolvidos por compilação.