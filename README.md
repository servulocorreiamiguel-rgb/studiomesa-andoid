> **Auditoria de fecho comercial — 10/11/2026:** a PR #107 já foi integrada na `main` no commit `38058cee6a4febc3faf37c527cb6ea4615fb4da3`; os botões Google/Microsoft estão ocultos por defeito. A CI acionada para esse commit (run [42865847812](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/42865847812)) ainda estava em execução na última consulta. A auditoria read-only confirmou 63 migrações no Supabase contra 61 ficheiros no Git, 26 tabelas com RLS e ausência das tabelas de receitas/ingredientes/permissões granulares. **A app continua NÃO LIBERADA para venda operacional.** Ver [auditoria de fecho comercial](docs/COMMERCIAL-CLOSEOUT-AUDIT-2026-11-10.md).
>
> **CI mais recente — 10/11/2026, 16:38 UTC: SUCESSO.** A execução [42852321328](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/42852321328), no commit `68da629fc3ecb0a68fea220b0a89b139d033f381`, terminou com os quatro jobs em sucesso. Passaram compilação das cinco variantes de demonstração, testes unitários, Android Lint, validação de cinco AAB/APKs com chave CI temporária, replay de migrations e autorização numa base PostgreSQL isolada e testes de interface em emuladores Android 8 (API 26) e Android 16 (API 36). Os APKs publicados como artefacto são **debug/demo**, não são releases comerciais e não usam a chave definitiva do titular. Esta CI não valida OAuth real, SMTP, dados do Supabase alojado, execução no OPPO A5m, DPA ou aprovação legal.
>
> **Estado atualizado em 10/11/2026 — NÃO LIBERADO PARA VENDA/PRODUÇÃO.** A CI integral da `main` no commit `c217bfdfb4fccb30b2e71f0e844f8b1dbbe18327` passou nos quatro jobs: compilação/testes das cinco variantes, Android Lint, replay PostgreSQL/testes de autorização e smoke tests Android 8/16. Ver [execução 42834074139](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/42834074139). As alterações posteriores passaram na CI integral 42844678804 (commit 210f419); a chave utilizada para validar o caminho de release foi temporária, não comercial.
>
> **IA — arquitetura em desenvolvimento:** a PR #113 prepara um único assistente com DeepSeek R1 gratuito para raciocínio, Cohere North Mini Code gratuito para programação, GPT-OSS 20B gratuito como alternativa e OpenRouter Free Router como fallback. No Android, Gemma 4 E2B é o modelo local preferido; LFM2.5-1.2B INT4 é o fallback para equipamentos com menos RAM/espaço. **Qwen 3.8 27B e GPT-OSS 120B não são usados no caminho de custo zero:** as rotas que verificámos são pagas. O router não deve tentar modelos pagos. O código do router e o cliente Android estão no PR #113; ainda falta criar/configurar a chave `OPENROUTER_API_KEY` no Supabase, publicar a Edge Function com JWT obrigatório e ativar o build cloud apenas após testes. O modo cloud permanece desligado por omissão. A inferência local de Gemma/LFM continua a exigir transferência do ficheiro; os testes de qualidade, RAM, temperatura e bateria em telemóveis físicos — incluindo o OPPO A5m — continuam pendentes. Não anunciar a Mega IA como serviço ativo ou ilimitado.
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