# StudioMesa

**Estado atualizado — 14/11/2026:** a `main` está no commit `2b27ed6197d6ec5431c224ee69d277eaa2845c7c`. As PRs #133 e #134 foram integradas; a CI pós-merge da `main` [43512084404](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/43512084404) e a CI da PR #134 [43512547155](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/43512547155) passaram os quatro jobs: APK, PostgreSQL/autorização, Android 8 e Android 16. O Supabase alojado continua activo, com 71 migrações locais e 63 remotas: oito migrações locais estão por aplicar. **A app está pronta para demonstração com dados fictícios, mas não para operação com dados reais, produção ou venda operacional.** Ver [checklist de preparação](PRODUCT-READINESS-CHECKLIST.md), [runbook de backup/restauro e migrações](docs/PRODUCTION-BACKUP-AND-MIGRATION-RUNBOOK.md) e [preparação de release](RELEASE-SETUP.md).

**Entrada de clientes:** a `main` ainda não inclui o fluxo novo de convidado, pesquisa de restaurante por nome, associação por QR com aprovação do proprietário e consulta do pedido. Essa implementação está na PR #96, encadeada sobre as PRs #95 e #94, e precisa de consolidação/revalidação antes de integrar. A carteira com vários restaurantes por conta também não está na `main`: a PR #97 falhou a CI e continua por corrigir.

**IA — integração na `main` desde 12/11/2026:** um único assistente encaminha pedidos online para DeepSeek R1, Cohere North Mini Code, Qwen3.8 27B `:free`, GPT-OSS 20B `:free` e OpenRouter Free Router, sem fallback pago. No Android, Gemma 4 E2B é o motor local preferido quando há RAM/espaço suficiente; LFM2.5-1.2B INT4 é alternativa mais leve e reserva instalável. A função `studiomesa-ai-router` está publicada no Supabase alojado (versão 1, `verify_jwt=true`). A utilização dos modelos online exige a secret de servidor `OPENROUTER_API_KEY`; a sua presença não foi confirmada e a cloud continua desligada por omissão. A inferência local requer download inicial dos pesos e testes físicos de qualidade, RAM, temperatura e bateria — incluindo no OPPO A5m. Não anunciar a Mega IA online como ativa ou ilimitada.

## Funcionalidades económicas integradas — backend ainda pendente

A PR #128 já está integrada na `main` e passou os quatro jobs de CI após o merge ([execução 43474362932](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/43474362932)):

- Sincronização autenticada entre dispositivos do arquivo SAF-T de vendas.
- Previsão de sete dias da procura por prato, calculada a partir das linhas de venda importadas.
- Extração OCR local de linhas de faturas de fornecedores, sujeita a revisão pelo proprietário.
- Receção de mercadoria apenas após confirmação explícita, com associação de linhas a artigos de inventário, idempotência e atualização do custo médio ponderado.
- Nas receções parciais, o custo é rateado pela quantidade efectivamente aceite; receber 1,5 kg de uma linha de 2 kg por 20 € acrescenta 15 € ao stock, não os 20 € completos.

A migração `20261113114000_supplier_invoice_lines_stock_receipts_and_saft_sync.sql` está na `main`, mas ainda não foi aplicada ao Supabase alojado. A sincronização cloud destas linhas/receções e do arquivo SAF-T não deve ser anunciada como operacional até à reconciliação e ao deployment controlado.
## O que ainda falta antes da venda ou utilização em serviço real

- Testes de aceitação adicionais em telemóveis reais: vários dispositivos e contas, perda de internet, sessões expiradas, revogação de acesso e erros de servidor. O proprietário já confirmou no OPPO A5m o funcionamento das funcionalidades gratuitas, reservas, sincronização Gestão/Cliente e isolamento de dados entre restaurantes; estes quatro pontos não devem ser tratados como testes por fazer. A compilação de um APK não substitui os cenários adicionais.
- Regras comerciais da fidelização: acumulação automática, recompensas configuráveis, resgates, limites e prevenção de fraude. Neste momento, o saldo é ajustado manualmente pelo restaurante.
- Reservas: o fluxo inicial de criação, estados e bloqueio de conflitos por mesa está implementado no código e na migração; ainda falta teste ponta a ponta, edição de reservas, vista de calendário/turnos, lembretes automáticos, depósitos e regras de cancelamento.
- Stock: o inventário partilhado, entradas/saídas, histórico de movimentos, custo unitário e fotografia de custos estão implementados no código/backend. As fichas técnicas e o consumo automático de ingredientes existem no código/migration, mas as tabelas correspondentes ainda não existem no Supabase alojado; não anunciar essa automação até à reconciliação e deployment aprovados. Ainda faltam testes ponta a ponta, avisos automáticos, fornecedores/encomendas, inventário por armazém e conversões de unidades.
- Gestão avançada de equipas ainda incompleta: a matriz granular por função está no código/migration, mas a tabela privada necessária ainda não existe no Supabase alojado. A remoção de funcionários e a renovação automática do código de adesão estão implementadas; a revogação não é propagada em tempo real a um ecrã já aberto.
- Campanhas de reactivação de clientes: a base de dados tem a estrutura/migrações de campanhas, mas o ecrã e o fluxo Android de criação/envio ainda não estão integrados na `main`. A implementação proposta na [PR #94](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/pull/94) ainda precisa de rebase, revisão e CI.
- Centro de notificações de actividade e notificações push: os ficheiros de implementação não estão na `main`. As propostas [PR #91](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/pull/91) e [PR #92](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/pull/92) sobrepõem-se e não devem ser integradas em bloco; é necessário consolidar a implementação, rever permissões Android/FCM e testar o envio real.
- Perfis pré-configurados de funções da equipa: existe a base de permissões no cliente, mas a matriz granular não está activa no Supabase alojado e a implementação de perfis predefinidos ainda não está na `main`. A PR #92 contém uma proposta que necessita de revisão e integração controlada.
- As funcionalidades básicas de reservas, sincronização Gestão/Cliente e isolamento entre restaurantes foram testadas manualmente pelo proprietário no OPPO A5m. Continuam pendentes os cenários de aceitação mais amplos, como falha de rede, sessão expirada, revogação e concorrência.

- Integração com POS/caixa, pagamentos, impressoras de cozinha, recibos ou faturação.
- Cópias de segurança e procedimento testado de recuperação, monitorização, exportação e conservação de dados. O fluxo de eliminação de conta está implementado no Android e no Supabase, mas os casos de conta pessoal/funcionário/proprietário continuam pendentes de testes ponta a ponta com contas descartáveis.
- Página web externa para pedir eliminação de conta publicada em `https://svwvkfsyevuyabelrtjk.supabase.co/functions/v1/account-deletion-web`; o fluxo de email ainda exige URL autorizado no Supabase e teste ponta a ponta.
- **Eliminação de conta:** a página pública usa `resetPasswordForEmail` e PKCE no mesmo navegador; a eliminação propriamente dita é feita pelo endpoint separado `delete-account`, que exige JWT. O fluxo email → callback → eliminação ainda não foi testado de ponta a ponta com contas descartáveis; não afirmar que a enumeração ou a eliminação estão totalmente validadas.
- **Privacidade na app:** a secção «Privacidade e dados» está acessível no menu da Gestão e na página Conta da app Cliente. Apresenta um resumo técnico da utilização de dados nesta versão de demonstração; não substitui a política pública final, o contacto do responsável, retenção ou declaração Data safety.
- Política de privacidade pública e formulário Data safety da Google Play ainda por preparar e validar. O histórico de pedidos está implementado; ainda falta registar todas as ações de gestão num trilho de auditoria completo.
- Gestão avançada da carta ainda incompleta: fotografias, disponibilidade por horário, tradução de menus, histórico editorial e validação sistemática de alergénios. Os campos de categoria, descrição e alergénios já existem, mas precisam de revisão pelo restaurante.
- Testes de privacidade, segurança e proteção de dados; revisão independente da configuração de produção. A proteção de palavras-passe comprometidas no Supabase ainda não está ativada.
- Os botões Google e Microsoft ficam ocultos na primeira versão comercial e serão reativados apenas depois da primeira venda. A implementação OAuth/PKCE fica preservada no código, atrás da propriedade `studiomesaSocialLoginButtonsEnabled`; não é um requisito do primeiro lançamento. O login inicial será por email/palavra-passe e continua a exigir SMTP próprio, confirmação de email e testes ponta a ponta.
- Release comercial assinada: o workflow manual já existe, mas exige o identificador definitivo, keystore privada e secrets/variables de assinatura. Também bloqueia enquanto não forem confirmados como `YES` os quatro controlos: migrações/backup/restauro do backend, autenticação SMTP, testes físicos e revisão legal/privacidade/licenças. Estes valores só devem ser marcados após validação real. O workflow não publica automaticamente na Google Play.
- A configuração de compilação usa compileSdk/targetSdk 36, Android Gradle Plugin 8.13.2 e Gradle 8.13. O workflow compila os cinco APKs de demonstração/debug, executa testes unitários nas cinco variantes de distribuição, Android Lint, replay de migrações/autorização em PostgreSQL e smoke tests em emuladores Android 8 e Android 16. Isto não substitui os testes físicos; os APKs atuais não são releases comerciais assinadas.

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