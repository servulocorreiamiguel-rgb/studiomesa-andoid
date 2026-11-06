# StudioMesa — gate de lançamento comercial

**Fotografia:** 6 de novembro de 2026  
**Estado:** NÃO LIBERADO PARA VENDA/PRODUÇÃO

Este documento é a lista única de saída para as cinco variantes. Um checkbox só pode ser marcado quando existir prova verificável no repositório, CI, configuração do fornecedor ou teste E2E. Presença de um botão não prova que o backend esteja funcional.

## Estado técnico confirmado

| Área | Estado |
|---|---|
| Android | Kotlin nativo; cinco variantes; minSdk 24; targetSdk 36; versionName 1.5.0. |
| CI | Replay PostgreSQL/autorização, build das variantes, unit tests e Lint já passaram na CI da PR #86; smoke em Android 8/16 aguarda resultado final. |
| Reservas | Regressões de idempotência, acesso por restaurante, capacidade, mesa inexistente, horários sobrepostos e retry após atribuição/confirmada passaram no PostgreSQL descartável da CI #1332. |
| IA | LFM2.5 local; histórico/Memória locais cifrados. Sync cloud está no código, mas falta deploy das tabelas no Supabase alojado. |
| Faturas | OCR local; metadados locais cifrados. Tabela e bucket cloud ainda ausentes no Supabase alojado. |
| Receita/consumo de stock | Código/migration local existente; tabelas de receitas ausentes no Supabase alojado. |
| Permissões da equipa | Matriz granular no código/migration local; tabela privada ausente no Supabase alojado. |
| Supabase | 53 migrations locais, 54 remotas, 48 comuns, cinco só locais e seis só remotas. Não fazer deploy antes da reconciliação, backup/restauro e replay separado. |
| Assinatura | Workflow para cinco AABs e cinco APKs assinados existe; falta package ID final, keystore e secrets/variable. Os APKs debug não são release comercial. |
| Publicação | Workflow não publica automaticamente na Google Play. |

## Gate A — produto e funcionalidades

- [ ] Smoke tests de CI Android 8/API 26 e Android 16 terminam com sucesso no head final da branch/main.
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
- [ ] Publicar uma política de privacidade real numa URL estável e integrá-la na app/ficha Play; substituir todos os placeholders do rascunho.
- [ ] Rever e submeter Data Safety com base no AAB exato, no código final, SDKs e configuração cloud ativa.
- [ ] Revisão jurídica dos termos B2B, licença de utilização, contrato/DPA, IVA, preços, suporte e limitações.
- [ ] Gerar SBOM e notices das dependências transitivas; rever licenças do modelo LFM Open v1.0, LiteRT-LM, ML Kit e todas as bibliotecas/ativos.
- [ ] Confirmar titularidade dos direitos de código, marca, logótipo, fontes, ícones e materiais.

## Gate D — identidade, assinatura e Google Play

- [ ] Definir a entidade que vende/contrata e o titular da conta de programador.
- [ ] Escolher os cinco package IDs finais e confirmar que ainda não estão registados; os IDs não podem ser mudados após publicação.
- [ ] Criar keystore de release fora do repositório; guardar duas cópias offline seguras e passwords num gestor de segredos.
- [ ] Configurar os GitHub Actions secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` e a variable `STUDIOMESA_RELEASE_APPLICATION_ID` sem expor os valores em commits/conversas.
- [ ] Executar o workflow comercial a partir de `main` com CI completa verde, validar cinco AABs e cinco APKs com assinatura e conservar checksums.
- [ ] Criar as fichas das cinco aplicações ou decidir formalmente uma arquitetura diferente antes de publicar.
- [ ] Preparar ícone, screenshots reais, descrição, classificação de conteúdo, público-alvo, contactos, página de privacidade, URL de eliminação, Data Safety e credenciais de revisão.
- [ ] Para uma nova app, target Android 16/API 36 ou superior desde 31/08/2026; o projeto usa targetSdk 36 no código atual. Confirmar no AAB final.
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

## Decisão de saída

**Pode ser demonstrada:** versão debug/APK de demonstração com dados fictícios e ambiente claramente identificado.

**Pode ser pilotada com dados reais:** apenas depois dos Gates A, B e C concluídos, backend reconciliado e configurado, contas E2E testadas, DPA/privacidade/backup/suporte aprovados.

**Pode ser vendida/publicada:** apenas depois dos Gates A–D concluídos, release AAB/APK assinado verificado, documentação legal aprovada e responsabilidades comerciais aceites.

**Estado desta fotografia:** não cumpre ainda os critérios de piloto nem venda. A CI e o código estão avançados; há bloqueios de backend, Auth, legalidade, assinatura e configuração que não podem ser resolvidos por compilação.