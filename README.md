> **Estado em 07/11/2026 — código avançado, ainda sem autorização para piloto/venda com dados reais.** Existem cinco variantes Android; os APKs atuais são demonstração/debug, não releases comerciais assinadas.
>
> **IA e faturas:** o modelo local LFM2.5, o histórico/memória cifrados e o OCR local estão no Android. As tabelas cloud de conversas, mensagens, memória e arquivo de faturas de fornecedores, bem como o bucket privado de documentos, foram aplicados no Supabase através das migrations de 07/11/2026. A ligação da IA geral ao Assistente económico é de leitura e apenas para o proprietário; não envia PDFs integrais nem texto OCR à IA geral.
>
> **Pesquisa na Internet:** a IA já contém a ferramenta `search_public_web`, mas a pesquisa geral só funciona depois de configurar `TAVILY_API_KEY` nos segredos das Edge Functions do Supabase. A branch atual acrescenta Tavily como fornecedor principal e mantém SearXNG como alternativa. Até existir chave válida, a pesquisa geral continua indisponível; a pesquisa Wikipedia mantém-se separada.
>
> **Loja de pontos:** criação/edição/ativação de recompensas pelo proprietário, custo em pontos, stock, resgate transacional, deduplicação, confirmação de entrega e histórico estão presentes na `main` e no Supabase alojado. A validação ponta a ponta com contas de cliente/restaurante continua pendente.
>
> **Notificações nesta branch:** estão a ser acrescentadas notificações locais em primeiro plano para novos pedidos, pedidos de reserva e eventos de recompensas. Não são notificações push quando a app está fechada; isso exige configuração Firebase/FCM, ainda não fornecida.
>
> **Backend:** o projeto Supabase está ativo. A reconciliação de migrations continua incompleta; não aplicar migrations em bloco nem alterar o esquema alojado sem backup, comparação e aprovação. As tabelas de fichas técnicas/consumo automático de stock e a tabela de permissões granulares por funcionário continuam ausentes no projeto alojado.
>
> **Preparação comercial:** faltam testes E2E com contas e dispositivos reais, backup/restauro comprovados, política de privacidade/Data Safety, configuração Auth/SMTP/OAuth, chave de assinatura e decisão sobre publicação Google Play. A integração POS/caixa/faturação/contabilidade não está implementada. Consultar [gate comercial](docs/COMMERCIAL-RELEASE-GATE-2026-11-06.md) e [bloqueios verificados](docs/RELEASE-BLOCKERS-VERIFIED-2026-11-06.md).

## O que ainda falta antes da venda ou utilização em serviço real

- Testes ponta a ponta em telemóveis reais, incluindo vários dispositivos e utilizadores, perda de Internet, sessões expiradas, notificações e erros do servidor.
- Configurar e testar `TAVILY_API_KEY` no Supabase para ativar pesquisa geral na Internet. Nunca colocar a chave no APK, no GitHub ou em código Android.
- Para notificações com a app fechada: criar/configurar o projeto Firebase, associar a aplicação Android, guardar as credenciais de servidor apenas em secrets e implementar o envio/renovação dos tokens FCM. A implementação desta branch é apenas local e em primeiro plano; ver [estado e requisitos FCM](docs/NOTIFICATIONS-STATUS.md).
- Fidelização: loja configurável já implementada; falta ensaio E2E de criação/edição, stock concorrente, resgate, entrega, saldos e isolamento entre dois restaurantes.
- Reservas: criação, estados e prevenção de conflitos por mesa estão implementados; ainda faltam edição/reagendamento, calendário/turnos, lembretes, depósitos e regras de cancelamento.
- Stock: inventário, entradas/saídas, histórico de movimentos, custo unitário e fotografia histórica estão implementados. Fichas técnicas e consumo automático de ingredientes continuam bloqueados até deployment aprovado das tabelas em falta; ainda faltam avisos automáticos, fornecedores/encomendas, armazéns e conversões de unidades.
- Gestão de equipa: criação/remoção de funcionários e códigos estão implementados. A matriz granular por funcionário existe no código/migrations, mas a tabela privada correspondente ainda não está ativa no Supabase alojado; não anunciar permissões granulares como funcionais.
- Cópias de segurança e procedimento de recuperação, monitorização, exportação, conservação e eliminação de dados têm de ser comprovados. O fluxo de eliminação de conta existe, mas continua a exigir ensaios E2E com contas descartáveis.
- Política de privacidade pública, Data Safety, termos B2B e DPA precisam de validação final e identificação do responsável legal.
- OAuth Google/Microsoft e envio de emails dependem de credenciais próprias, SMTP e URLs de retorno autorizados; os fluxos precisam de testes num dispositivo real.
- Configuração de release comercial assinada: escolher package IDs definitivos, criar keystore fora do repositório, configurar secrets, gerar AAB/APK assinados e verificar a assinatura. O workflow não publica automaticamente na Google Play.
- Integração com POS/caixa, pagamentos, impressoras de cozinha, recibos, faturação fiscal ou contabilidade não está incluída.


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
- [Gate de lançamento](docs/COMMERCIAL-RELEASE-GATE-2026-11-06.md) — a versão continua não liberada para piloto/venda.

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