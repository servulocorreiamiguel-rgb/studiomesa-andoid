> **Estado em 10/11/2026 — NÃO LIBERADO PARA VENDA/PRODUÇÃO.** A versão principal já inclui a IA local LFM2.5-1.2B-Instruct INT4, análise semanal, previsão de sete dias, feriados nacionais, eventos locais configuráveis, padrões de maior afluência e cálculos económicos. As previsões usam valores nominais de pedidos concluídos pela data de criação; não representam pagamentos recebidos nem faturação contabilística.
>
> **Segurança Android:** PR #105 integrada na `main` no commit `52f858ad293f4ce557758fab60fdcc3a9321d6cf`. A CI da PR (`42782322112`) passou compilação das cinco variantes, testes unitários, Android Lint, validação da configuração comercial, replay PostgreSQL/autorização e smoke tests Android 8/16. A CI pós-merge em `main` (`42791471088`) ainda estava a executar Android Lint na última consulta; o job PostgreSQL/autorização já passou. APKs debug continuam a ser demonstrações, não releases comerciais assinadas. A inferência real do modelo ainda exige teste em telemóvel físico.
>
> **Supabase alojado — verificação de 09/11/2026:** projeto ativo e saudável, região `eu-west-1`, organização no plano Free. Existem 63 versões no histórico remoto e 55 ficheiros de migração nesta branch; 51 prefixos coincidem, quatro migrações só existem no repositório e doze só existem no servidor. Esta divergência bloqueia qualquer `db push` até reconciliação. As tabelas de histórico, mensagens, memória e metadados de faturas existem com RLS ativo; o bucket `studiomesa-invoices` é privado. As tabelas de fichas técnicas/ingredientes e de permissões granulares da equipa não foram encontradas no servidor.
>
> **IA, pesquisa e faturas:** a função de IA na nuvem permanece desativada e responde HTTP 410; o modelo principal é local. A função de pesquisa Web exige JWT e um endpoint SearXNG HTTPS configurado através de `SEARXNG_BASE_URL`; a configuração efetiva desse segredo não foi confirmada nesta auditoria. O OCR é local. A sincronização cloud de histórico/Memória é opcional e não tem cifragem ponta a ponta.
>
> **Segurança observada:** o Security Advisor ainda apresenta 42 avisos sobre funções `SECURITY DEFINER` executáveis por utilizadores autenticados, 11 tabelas RLS sem políticas e a proteção contra palavras-passe comprometidas desativada. As funções foram revistas quanto a validação de utilizador e `search_path`; não revogar permissões em bloco. A lista de avisos não substitui um teste de intrusão independente.
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

Com Java 17, Android Gradle Plugin 8.10.0, Gradle 8.11.1 e Android SDK 36:

```bash
gradle --no-daemon assembleEssencialRestauranteDebug assembleProfissionalRestauranteDebug assembleProfissionalClienteDebug assemblePremiumRestauranteDebug assemblePremiumClienteDebug
```

Os APKs de demonstração não devem ser tratados como versão comercial final nem apresentados como testados em produção. Para preparar bundles comerciais assinados, segue [`RELEASE-SETUP.md`](RELEASE-SETUP.md). O workflow manual cria cinco AABs assinados para a Google Play e cinco APKs assinados para instalação direta; não publica automaticamente na Google Play. Os identificadores de pacote finais têm de ser escolhidos antes da publicação.