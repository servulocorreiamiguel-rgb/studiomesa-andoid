> **Atualização da auditoria — 10/11/2026:** PR #107 e PR #108 integradas na `main`. O login Google/Microsoft está oculto por defeito. A CI Android integral da `main` no commit `c206388d812fb696ff1d4076bbc2815f2838f5ec` passou os quatro jobs (run [42886931540](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/42886931540)). Foram recuperados cinco ficheiros de migrações em falta a partir de uma branch histórica, com os identificadores do Supabase; ficam 66 ficheiros locais, 63 versões remotas e três migrações apenas locais. A recuperação está em validação na PR #109 — não aplicar migrações ao Supabase alojado. **A app continua NÃO LIBERADA para venda operacional.** Ver [relatório de recuperação](docs/MIGRATION-RECOVERY-2026-11-10.md) e [auditoria comercial](docs/COMMERCIAL-CLOSEOUT-AUDIT-2026-11-10.md).
>
> **CI mais recente — 10/11/2026, 16:38 UTC: SUCESSO.** A execução [42852321328](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/42852321328), no commit `68da629fc3ecb0a68fea220b0a89b139d033f381`, terminou com os quatro jobs em sucesso. Passaram compilação das cinco variantes de demonstração, testes unitários, Android Lint, validação de cinco AAB/APKs com chave CI temporária, replay de migrations e autorização numa base PostgreSQL isolada e testes de interface em emuladores Android 8 (API 26) e Android 16 (API 36). Os APKs publicados como artefacto são **debug/demo**, não são releases comerciais e não usam a chave definitiva do titular. Esta CI não valida OAuth real, SMTP, dados do Supabase alojado, execução no OPPO A5m, DPA ou aprovação legal.
>
> **Estado atualizado em 10/11/2026 — NÃO LIBERADO PARA VENDA/PRODUÇÃO.** A CI integral da `main` no commit `c217bfdfb4fccb30b2e71f0e844f8b1dbbe18327` passou nos quatro jobs: compilação/testes das cinco variantes, Android Lint, replay PostgreSQL/testes de autorização e smoke tests Android 8/16. Ver [execução 42834074139](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/42834074139). As alterações posteriores passaram na CI integral 42844678804 (commit 210f419); a chave utilizada para validar o caminho de release foi temporária, não comercial.
>
> **IA:** a integração GPT-OSS 120B, Browser Search e Code Interpreter está no código, mas a Edge Function `studiomesa-advanced-assistant` não está publicada no Supabase e falta a chave `GROQ_API_KEY`. O acesso online foi desativado por omissão na app até serem aprovados fornecedor, privacidade e limites de despesa. A IA local LFM2.5-1.2B continua disponível após transferência de cerca de 736 MB; ainda precisa de testes físicos no OPPO A5m e noutros dispositivos. A tarifa atualmente publicada para GPT-OSS 120B na Groq é USD 0,15/1M tokens de entrada e USD 0,60/1M tokens de saída; não prometer uso gratuito.
>
> **Licença do modelo local:** o LFM Open License v1.0 condiciona o uso comercial a uma receita anual inferior a USD 10 milhões para a entidade legal abrangida. Confirmar a licença e a entidade que vai distribuir antes da venda: https://www.liquid.ai/lfm-license
>
> **Supabase:** projeto `ACTIVE_HEALTHY`, região `eu-west-1`. Existem 66 ficheiros de migration no branch de recuperação e 63 versões no servidor; os 63 identificadores remotos estão agora representados por ficheiros locais e três migrações permanecem apenas locais. A recuperação dos cinco SQL históricos está sujeita a replay CI e comparação do esquema; não aplicar em produção. Não executar `db push`, `migration repair` ou alterações de produção até reconciliar SQL e objetos e demonstrar backup/restauro num ambiente separado. As tabelas de receitas, ingredientes e permissões granulares continuam ausentes no servidor.
>
> **Segurança e lançamento:** o Advisor assinala 42 funções `SECURITY DEFINER` executáveis por `authenticated` e 11 tabelas RLS sem políticas. Verificação SQL read-only: as 42 funções têm `search_path` vazio, nenhuma pode ser chamada por `anon` e todas referenciam `auth.uid()`; as 11 tabelas não concedem privilégios diretos aos papéis de cliente. Isto não substitui a revisão de autorização por restaurante. A proteção contra palavras-passe comprometidas continua desativada. É necessária revisão individual, testes E2E e autenticação real. Os APKs gerados são debug/demo; faltam package IDs definitivos, keystore comercial, AAB/APK assinados, política de privacidade, Data Safety, DPA, licenças/notices, backup/restauro e testes em dispositivos físicos.
>
> **Relatório atual de preparação comercial:** [COMMERCIAL-READINESS-UPDATE-2026-11-10.md](docs/COMMERCIAL-READINESS-UPDATE-2026-11-10.md). Decisão: não abrir piloto com dados reais nem vender enquanto estes bloqueios não forem resolvidos.
>
## O que ainda falta antes da venda ou utilização em serviço real

- Testes ponta a ponta em telemóveis reais, incluindo vários dispositivos, utilizadores, perda de internet, sessões expiradas e erros de servidor. A compilação de um APK não substitui esses testes.
- Regras comerciais da fidelização: acumulação automática, recompensas configuráveis, resgates, limites e prevenção de fraude. Neste momento, o saldo é ajustado manualmente pelo restaurante.
- Reservas: o fluxo inicial de criação, estados e bloqueio de conflitos por mesa está implementado no código e na migração; ainda falta teste ponta a ponta, edição de reservas, vista de calendário/turnos, lembretes automáticos, depósitos e regras de cancelamento.
- Stock: o inventário partilhado, entradas/saídas, histórico de movimentos, custo unitário e fotografia de custos estão implementados no código/backend. As fichas técnicas e o consumo automático de ingredientes existem no código/migration, mas as tabelas correspondentes ainda não existem no Supabase alojado; não anunciar essa automação até à reconciliação e deployment aprovados. Ainda faltam testes ponta a ponta, avisos automáticos, fornecedores/encomendas, inventário por armazém e conversões de unidades.
- Gestão avançada de equipas ainda incompleta: a matriz granular por função está no código/migration, mas a tabela privada necessária ainda não existe no Supabase alojado. A remoção de funcionários e a renovação automática do código de adesão estão implementadas; a revogação não é propagada em tempo real a um ecrã já aberto.
- Integração com POS/caixa, pagamentos, impressoras de cozinha, recibos ou faturação.
- Cópias de segurança e procedimento testado de recuperação, monitorização, exportação e conservação de dados. O fluxo de eliminação de conta está implementado no Android e no Supabase, mas os casos de conta pessoal/funcionário/proprietário continuam pendentes de testes ponta a ponta com contas descartáveis.
- Página web externa para pedir eliminação de conta publicada em `https://svwvkfsyevuyabelrtjk.supabase.co/functions/v1/account-deletion-web`; o fluxo de email ainda exige URL autorizado no Supabase e teste ponta a ponta.
- **Eliminação de conta:** a página pública usa `resetPasswordForEmail` e PKCE no mesmo navegador; a eliminação propriamente dita é feita pelo endpoint separado `delete-account`, que exige JWT. O fluxo email → callback → eliminação ainda não foi testado de ponta a ponta com contas descartáveis; não afirmar que a enumeração ou a eliminação estão totalmente validadas.
- **Privacidade na app:** a secção «Privacidade e dados» está acessível no menu da Gestão e na página Conta da app Cliente. Apresenta um resumo técnico da utilização de dados nesta versão de demonstração; não substitui a política pública final, o contacto do responsável, retenção ou declaração Data safety.
- Política de privacidade pública e formulário Data safety da Google Play ainda por preparar e validar. O histórico de pedidos está implementado; ainda falta registar todas as ações de gestão num trilho de auditoria completo.
- Gestão avançada da carta ainda incompleta: fotografias, disponibilidade por horário, tradução de menus, histórico editorial e validação sistemática de alergénios. Os campos de categoria, descrição e alergénios já existem, mas precisam de revisão pelo restaurante.
- Testes de privacidade, segurança e proteção de dados; revisão independente da configuração de produção. A proteção de palavras-passe comprometidas no Supabase ainda não está ativada.
- Os botões Google e Microsoft ficam ocultos na primeira versão comercial e serão reativados apenas depois da primeira venda. A implementação OAuth/PKCE fica preservada no código, atrás da propriedade `studiomesaSocialLoginButtonsEnabled`; não é um requisito do primeiro lançamento. O login inicial será por email/palavra-passe e continua a exigir SMTP próprio, confirmação de email e testes ponta a ponta.
- Configuração de release comercial assinada e workflow manual para gerar AABs com uma chave privada guardada em GitHub Actions Secrets. Ainda é necessário escolher o identificador de pacote definitivo, configurar a chave, executar o workflow e validar as variantes em dispositivos reais; o workflow não publica automaticamente na Google Play.
- A configuração de compilação usa compileSdk/targetSdk 36, Android Gradle Plugin 8.13.2 e Gradle 8.13. O workflow compila os cinco APKs de demonstração/debug, executa testes unitários nas cinco variantes de distribuição e Android Lint. Mesmo com CI aprovado, a app continua a exigir testes ponta a ponta; os APKs atuais não são versões comerciais assinadas.

## Preparação comercial

- [Planos e preços propostos](SALES-PLANS-DRAFT.md) — 890 €, 1 490 € e 1 690 € como valores de referência, não oferta vinculativa.
- [Modelo de proposta/orçamento](COMMERCIAL-QUOTATION-TEMPLATE.md).
- [Lista de entrada em funcionamento](COMMERCIAL-ONBOARDING-CHECKLIST.md).
- [Termos B2B](TERMS-OF-SERVICE-TEMPLATE.md), [licença de software](B2B-SOFTWARE-LICENSE-TEMPLATE.md) e [DPA](DATA-PROCESSING-AGREEMENT-TEMPLATE.md) — rascunhos para revisão jurídica.
- [Inventário de licenças de terceiros](THIRD-PARTY-NOTICES.md).
- [Gate de lançamento](docs/COMMERCIAL-RELEASE-GATE-2026-11-10.md) — estado atual após a integração do endurecimento Android; continua sem autorização para piloto/venda.

## Configuração técnica

O backend usa Supabase Auth e PostgreSQL com Row Level Security. Os códigos de adesão dos clientes ficam num esquema privado, separados das tabelas expostas à aplicação. As tabelas de mesas, carta e pedidos usam políticas de segurança por restaurante. Algumas funções protegidas usam SECURITY DEFINER para executar operações verificadas do lado da base de dados; as regras devem continuar a ser auditadas.

### Compilar os cinco APKs de demonstração

O workflow **Actions → Build StudioMesa Android APK** gera:

- `app-essencial-restaurante-debug.apk`
- `app-profissional-restaurante-debug.apk`
- `app-profissional-cliente-debug.apk`
- `app-premium-restaurante-debug.apk`
- `app-premium-cliente-debug.apk`

Com JDK 17, Android Gradle Plugin 8.13.2, Gradle 8.13 e Android SDK 36:

```bash
gradle --no-daemon assembleEssencialRestauranteDebug assembleProfissionalRestauranteDebug assembleProfissionalClienteDebug assemblePremiumRestauranteDebug assemblePremiumClienteDebug
```

Os APKs de demonstração não devem ser tratados como versão comercial final nem apresentados como testados em produção. Para preparar bundles comerciais assinados, segue [`RELEASE-SETUP.md`](RELEASE-SETUP.md). O workflow manual cria cinco AABs assinados para a Google Play e cinco APKs assinados para instalação direta; não publica automaticamente na Google Play. Os identificadores de pacote finais têm de ser escolhidos antes da publicação.