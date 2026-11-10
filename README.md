> **Estado em 10/11/2026 — NÃO LIBERADO PARA VENDA/PRODUÇÃO.** A versão principal já inclui a IA local LFM2.5-1.2B-Instruct INT4, análise semanal, previsão de sete dias, feriados nacionais, eventos locais configuráveis, padrões de maior afluência e cálculos económicos. As previsões usam valores nominais de pedidos concluídos pela data de criação; não representam pagamentos recebidos nem faturação contabilística.
>
> **CI Android do código de aplicação:** a execução [42792956250](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/42792956250), no commit `0177c6aa3e62c5e70b20f620be4576858ab67837`, terminou com sucesso nos quatro jobs. A validação da versão com as migrations recuperadas e deduplicadas decorre na execução [42800159352](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/42800159352); o replay PostgreSQL e o build Android ainda estavam em curso na última consulta. Os APKs são demonstrações/debug, não releases comerciais assinadas. A inferência real do modelo ainda exige teste físico.
>
> **Supabase alojado — verificação read-only de 10/11/2026 às 10:06 UTC:** projeto `ACTIVE_HEALTHY`, região `eu-west-1`. Existem 63 versões no histórico remoto e 61 ficheiros de migração nesta branch; 58 prefixos coincidem, três migrações só existem no repositório e cinco só existem no servidor. Sete ficheiros originais foram recuperados de branches históricas e uma cópia local byte-idêntica à versão remota foi removida; não foram aplicadas migrations ao servidor. Esta divergência bloqueia qualquer `db push` até reconciliação. Foram observadas 26 tabelas, todas com RLS ativo; continuam ausentes as tabelas de fichas técnicas/ingredientes e permissões granulares da equipa. O bucket `studiomesa-invoices` está privado.
>
> **IA, pesquisa e faturas:** a função de IA na nuvem permanece desativada e responde HTTP 410; o modelo principal é local. A função de pesquisa Web exige JWT e um endpoint SearXNG HTTPS configurado através de `SEARXNG_BASE_URL`; a configuração efetiva desse segredo não foi confirmada nesta auditoria. O OCR é local. A sincronização cloud de histórico/Memória é opcional e não tem cifragem ponta a ponta.
>
> **Segurança observada:** na consulta atual, o Security Advisor assinala 42 funções `SECURITY DEFINER` executáveis por utilizadores autenticados, 11 tabelas RLS sem políticas e a proteção contra palavras-passe comprometidas desativada. A inspeção read-only não encontrou privilégios diretos para `anon`, `authenticated` ou `PUBLIC` nessas 11 tabelas; continuam a ser necessárias a revisão individual das funções/RPCs e a validação de cada caminho de acesso. O Performance Advisor assinala quatro chaves estrangeiras sem índice de cobertura e 45 índices sem utilização observada. Não remover nem revogar permissões em bloco. Estes avisos não substituem um teste de intrusão independente.
>
> **Bloqueios antes da venda:** reconciliação das migrações e testes E2E do backend, configuração e teste de OAuth/SMTP, chave e assinatura comercial definitivas, política de privacidade e Data Safety final, revisão de termos/DPA/licenças, backups/restauro, testes físicos e uma estratégia de publicação Play compatível com as regras atuais. Ver [auditoria de prontidão de 09/11/2026](docs/RELEASE-READINESS-2026-11-09.md), [gate comercial atualizado](docs/COMMERCIAL-RELEASE-GATE-2026-11-10.md), [configuração de release](RELEASE-SETUP.md) e [bloqueios anteriores](docs/RELEASE-BLOCKERS-VERIFIED-2026-11-06.md).
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
- Credenciais próprias de OAuth para Google/Microsoft, caso se queira disponibilizar esses métodos de login. O código Android usa PKCE para OAuth, registo e recuperação e guarda o verificador cifrado no Android Keystore; estes fluxos ainda precisam de testes ponta a ponta num dispositivo real. É necessário autorizar o URL de retorno e configurar um fornecedor SMTP para o envio real dos emails.
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