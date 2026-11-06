# StudioMesa — plano de preparação comercial

> **Para agentes de implementação:** executar cada tarefa nesta branch, por ordem, com revisão e teste próprios. Não publicar nem aplicar alterações no Supabase alojado.

**Objetivo:** deixar o repositório, a documentação e o processo de release coerentes e auditáveis para uma futura comercialização responsável das cinco variantes StudioMesa.

**Arquitetura:** manter a app Android Kotlin e o backend Supabase. Corrigir proveniência e documentação, preparar os materiais de privacidade/termos/licenças, e deixar a publicação comercial bloqueada por segredos/identidade/legalidade e por uma migração Supabase ainda não reconciliada.

**Stack:** Android/Kotlin, Gradle, GitHub Actions, Supabase PostgreSQL/Auth/Storage/Edge Functions.

**Spec:** docs/superpowers/specs/2026-11-06-commercial-release-readiness-design.md

## Restrições globais

- Não executar db push, migration repair, apply_migration, deploy de Edge Functions ou alterações nas definições Supabase.
- Não criar nem guardar secrets/keystore/OAuth credentials.
- Não publicar política, termos, AABs ou aplicação.
- Não afirmar que o OPPO A5m foi testado.
- Não usar preços de referência como preços aprovados/finais.
- Não afirmar compatibilidade com POS, faturação, pagamentos ou impressão sem evidência.

## Foco da revisão

- Sync cloud vs cifragem E2E: documentar como RLS/proteção de servidor, não E2E.
- Migrations ausentes e schema incompleto: não ativar funcionalidades que dependem de tabelas/bucket inexistentes.
- Privacy/Data Safety: incluir local OCR, LLM local, consulta pública, cloud sync e dados de faturas sem declarações fictícias.
- Licenças: separar o modelo LFM Open License dos runtimes/componentes e não presumir elegibilidade comercial.
- Release: garantir que assinatura/identificador ainda requerem valores e credenciais do responsável.

---

### Tarefa 1 — Proveniência do backend e consistência do código

**Ficheiros:**
- Criar supabase/functions/studiomesa-assistant/index.ts com a implementação ativa que devolve 410 CLOUD_AI_DISABLED.
- Modificar app/src/main/java/pt/studiomesa/app/AssistantChatStore.kt (comentário de classe).
- Criar docs/SUPABASE-LIVE-RECONCILIATION-2026-11-06.md.

- [ ] Confirmar a versão ativa da Edge Function no Supabase e guardar o mesmo conteúdo que está implantado.
- [ ] Corrigir o comentário desatualizado: armazenamento local cifrado + sincronização cloud opcional, sendo a cópia cloud protegida por RLS e não E2E.
- [ ] Registar: 53 migrações locais, 54 remotas, 48 comuns, cinco local-only, seis remote-only; tabelas de receitas, permissões granulares, chat/memória/faturas e bucket ausentes; colunas de custo unitário existentes.
- [ ] Verificar por SQL read-only que nenhuma alteração foi aplicada ao projeto alojado.

**Verificação:** ler os blobs da branch, comparar o ficheiro da Edge Function com a versão remota e repetir queries read-only de catálogo. Não divulgar segredos.

### Tarefa 2 — Política de privacidade e Data Safety

**Ficheiros:**
- Atualizar PRIVACY-POLICY-TEMPLATE.md.
- Criar DATA-SAFETY-DRAFT.md.

- [ ] Descrever contas, restaurante/equipa, clientes, mesas, carta, pedidos, reservas, stock, pontos, sessões e auditoria.
- [ ] Descrever o LFM2.5 local; transferências públicas genéricas à Wikipédia quando a ferramenta é usada; não confundir estas chamadas com envio de prompts ao modelo cloud.
- [ ] Descrever histórico/memória cifrados localmente e sync cloud opcional com RLS, sem E2E.
- [ ] Descrever OCR local, limites PDF/imagem e que o texto OCR não é guardado; faturas e documentos para Supabase só após configuração de tabela/bucket privado e autenticação de proprietário.
- [ ] Incluir eliminação de conta, limitações de teste e placeholders para controlador, email, morada, bases legais, retenção, subprocessadores e transferências.
- [ ] Criar matriz de categorias de dados/recolha/partilha/segurança/eliminação a validar no formulário Google Play; não marcar respostas como finais.
- [ ] Corrigir datas e referências obsoletas.

**Verificação:** procurar afirmações absolutas contraditórias e todos os placeholders legais; garantir que a política permanece marcada como rascunho/não publicar.

### Tarefa 3 — Termos comerciais e responsabilidades

**Ficheiros:**
- Criar TERMS-OF-SERVICE-TEMPLATE.md.
- Criar B2B-SOFTWARE-LICENSE-TEMPLATE.md.

- [ ] Criar modelos B2B com campos para entidade/cliente, plano e módulos incluídos, pagamento, configuração, suporte, atualizações, alojamento, disponibilidade, dados, exportação, backups, fim do contrato, incidentes e responsabilidades.
- [ ] Distinguir licença de utilização da app da titularidade dos dados do restaurante.
- [ ] Explicar limites: não é contabilidade/faturação certificada, não garante previsão, não processa pagamentos nem substitui POS/ERP.
- [ ] Identificar como pendentes as condições legais, preços, IVA, prazos e SLA, com revisão profissional obrigatória.

**Verificação:** não afirmar certificações, conformidade, garantias, preços ou prazos sem aprovação do responsável.

### Tarefa 4 — Inventário de terceiros, modelo e supply chain

**Ficheiros:**
- Criar THIRD-PARTY-NOTICES.md.
- Atualizar docs/THIRD-PARTY-AI-MODEL.md se a revisão encontrar factos incorretos.

- [ ] Inventariar o LFM Open License v1.0, LiteRT-LM 0.18.0/Apache 2.0 e dependências Android diretas já declaradas.
- [ ] Marcar licenças e avisos ainda não confirmados para revisão, em vez de presumir licenças.
- [ ] Registar URL, versão, hash e origem do modelo INT4.
- [ ] Confirmar que a licença do modelo autoriza o uso da entidade que o vai comercializar; não concluir elegibilidade sem conhecer a entidade/receita.
- [ ] Documentar que não existe ainda uma licença global do repositório escolhida.

**Verificação:** comparar a lista com app/build.gradle.kts e os ficheiros de versão; nenhuma alegação legal sem fonte.

### Tarefa 5 — Release e lista de bloqueios

**Ficheiros:**
- Atualizar README.md, RELEASE-SETUP.md, SUPABASE-SETUP.md, PRODUCT-READINESS-CHECKLIST.md, docs/RELEASE-BLOCKERS-VERIFIED-2026-11-06.md.
- Criar docs/COMMERCIAL-RELEASE-GATE-2026-11-06.md.

- [ ] Marcar as cinco aplicações como variantes de demonstração até geração comercial assinada; documentar targetSdk 36 e minSdk 24.
- [ ] Documentar os cinco outputs do workflow e os quatro secrets + uma variable necessárias, sem incluir valores.
- [ ] Identificar a exigência de titularidade/idade Google Play e que a conta de programador precisa de ser detida pelo adulto/entidade legal responsável.
- [ ] Explicar que o workflow produz AAB/APK assinados mas não publica na loja.
- [ ] Registar que não existe atribuição automática de planos/pagamentos nem integração POS/contabilidade.
- [ ] Separar os bloqueios cloud/segurança/Auth/email/OAuth/backup/restore/legal/physical device.
- [ ] Atualizar o estado da PR #86 e da CI somente depois de confirmar o resultado final.

**Verificação:** revisão textual dos documentos, procura de informações antigas, testes estáticos do workflow comercial, CI completa da branch.

### Tarefa 6 — Verificação final

- [ ] Executar o workflow completo da branch depois de todas as alterações.
- [ ] Confirmar os quatro jobs, resultados de testes, Lint, replay/autorização e emuladores Android 8/16.
- [ ] Verificar diff completo e confirmar que não existem secrets, chaves ou dados reais.
- [ ] Abrir PR para main apenas com evidência verde e relatório final.
- [ ] Não fazer merge se a CI falhar; não executar release comercial nem deploy cloud.