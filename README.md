> **Estado em 07/11/2026 — NÃO PRONTO PARA VENDA/PRODUÇÃO.** A branch `feature/loyalty-shop-photos-tier-limits` prepara seis variantes Android, loja de pontos com limites/fotos, fichas técnicas e consumo automático, matriz granular de permissões, notificações locais em primeiro plano e IA cloud geral opcional. A validação final Android e os smoke tests continuam em curso; os APKs que já existem são builds de demonstração/debug, não releases comerciais.
>
> **CI e validação da branch:** o replay das migrations numa PostgreSQL descartável e os testes de autorização/isolamento passaram. A compilação Android, testes unitários, Lint, TypeScript das duas Edge Functions e smoke tests Android 8/16 ainda têm de passar no commit final da branch. O replay de CI não altera o Supabase alojado.
>
> **Fichas técnicas e permissões:** as tabelas de receitas/ingredientes, consumo transacional ao iniciar preparação e matriz por função estão no código/migrations da branch. A migration de permissões também exige a permissão `inventory` para consultar/editar receitas, incluindo chamadas diretas a RPCs. **Ainda não foram aplicadas ao Supabase alojado.** Não usar esta automação em stock real até à reconciliação das migrations, backup/restauro e deployment controlado.
>
> **Notificações:** a branch contém avisos locais de novos pedidos, reservas e resgates, com deduplicação, pedido de permissão Android 13+ e abertura da secção correspondente ao tocar. Consulta a cada 60 segundos apenas enquanto a app está em primeiro plano. **Não é FCM e não entrega avisos quando a app está fechada.**
>
> **IA:** mantém-se a IA local LFM2.5-1.2B para conversa geral e trabalho offline. Foi acrescentada uma opção online separada para conversa geral com Gemini e pesquisa atual Tavily/SearXNG. Exige segredos server-side `GEMINI_API_KEY` e `TAVILY_API_KEY`, publicação autenticada da Edge Function e testes ponta a ponta. Não envia histórico, Memória, faturas nem dados do restaurante. O Assistente económico continua separado. Até configurar as chaves e publicar a função, não apresentar a IA online/pesquisa atual como ativa em produção.
>
> **Backend Supabase:** a história de migrations continua divergente: 57 versões no projeto alojado versus 55 ficheiros na `main` (quatro versões locais e seis só remotas). Não aplicar migrations em bloco. As atribuições de plano Pro/Premium também exigem mapeamento administrativo; a tabela privada usa Essencial por omissão. Ver [reconciliação da história de migrations](docs/SUPABASE-LIVE-MIGRATION-HISTORY-2026-11-07.md).
>
> **Ainda fora de âmbito:** FCM real, testes físicos no OPPO A5m, migrations de produção, assinatura comercial definitiva, identificadores finais de aplicação, credenciais Auth/SMTP/OAuth, recuperação/backup verificados, integração POS/contabilidade e publicação Google Play. Ver [checklist OPPO A5m e primeira venda](docs/OPPO-A5M-ACCEPTANCE-TEST.md), [preparação FCM](docs/FCM-PUSH-SETUP.md) e [gate comercial](docs/COMMERCIAL-RELEASE-GATE-2026-11-06.md).

## O que ainda falta antes da venda ou utilização em serviço real

- Testes ponta a ponta em telemóveis reais, incluindo vários dispositivos, utilizadores, perda de internet, sessões expiradas e erros de servidor. A compilação de um APK não substitui esses testes.
- Fidelização: QR temporário de utilização única para atribuição pelo restaurante, loja de prémios configurável, desconto transacional de pontos no servidor, stock e confirmação de entrega. A pontuação por compra continua a ser atribuída por funcionário; não existe integração POS que calcule automaticamente pontos a partir do valor da venda. O fluxo de cancelamento de um resgate pendente com reposição de pontos/stock ainda não existe.
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
- A configuração de compilação usa compileSdk/targetSdk 36, Android Gradle Plugin 8.13.2 e Gradle 8.13. O workflow na branch atual compila seis APKs de demonstração/debug e executa testes unitários nas seis variantes de distribuição e Android Lint. Mesmo com CI aprovado, a app continua a exigir testes ponta a ponta; os APKs atuais não são versões comerciais assinadas.

## Preparação comercial

- [Planos e preços propostos](SALES-PLANS-DRAFT.md) — 890 €, 1 490 € e 1 690 € como valores de referência, não oferta vinculativa.
- [Modelo de proposta/orçamento](COMMERCIAL-QUOTATION-TEMPLATE.md).
- [Lista de entrada em funcionamento](COMMERCIAL-ONBOARDING-CHECKLIST.md).
- [Termos B2B](TERMS-OF-SERVICE-TEMPLATE.md), [licença de software](B2B-SOFTWARE-LICENSE-TEMPLATE.md) e [DPA](DATA-PROCESSING-AGREEMENT-TEMPLATE.md) — rascunhos para revisão jurídica.
- [Inventário de licenças de terceiros](THIRD-PARTY-NOTICES.md).
- [Gate de lançamento](docs/COMMERCIAL-RELEASE-GATE-2026-11-06.md) — a versão continua não liberada para piloto/venda.

## Fidelização e loja de pontos

A versão principal já tem QR temporário, saldo partilhado, catálogo de recompensas, custo em pontos, stock, resgates transacionais, confirmação de entrega e histórico. O QR é de utilização única e o servidor valida a conta e o restaurante; o cliente não pode editar o saldo nem o custo do prémio.

A branch `feature/loyalty-shop-photos-tier-limits` prepara também limites server-side de prémios ativos (Essencial 5, Profissional 15, Premium 50), uma fotografia JPEG por prémio nos dois planos superiores e a variante Cliente Essencial (cartão, carta e loja de pontos, sem reservas). A migration não foi aplicada ao Supabase alojado e a branch ainda precisa de CI, revisão e integração. Não anunciar estes limites/fotografias como ativos na versão principal antes disso.

O sistema não calcula pontos automaticamente a partir de compras porque não existe integração POS. Os pontos são atribuídos por funcionário através do QR. Um resgate pendente reserva pontos e stock; ainda falta uma operação de cancelamento com devolução automática.

## Configuração técnica

O backend usa Supabase Auth e PostgreSQL com Row Level Security. Os códigos de adesão dos clientes ficam num esquema privado, separados das tabelas expostas à aplicação. As tabelas de mesas, carta e pedidos usam políticas de segurança por restaurante. Algumas funções protegidas usam SECURITY DEFINER para executar operações verificadas do lado da base de dados; as regras devem continuar a ser auditadas.

### Compilar os APKs de demonstração (seis variantes na branch em preparação)

O workflow **Actions → Build StudioMesa Android APK** gera:

- `app-essencial-restaurante-debug.apk`
- `app-essencial-cliente-debug.apk` (nova variante da branch em preparação)
- `app-profissional-restaurante-debug.apk`
- `app-profissional-cliente-debug.apk`
- `app-premium-restaurante-debug.apk`
- `app-premium-cliente-debug.apk`

Com Java 17, Android Gradle Plugin 8.10.0, Gradle 8.11.1 e Android SDK 36:

```bash
gradle --no-daemon assembleEssencialRestauranteDebug assembleEssencialClienteDebug assembleProfissionalRestauranteDebug assembleProfissionalClienteDebug assemblePremiumRestauranteDebug assemblePremiumClienteDebug
```

Os APKs de demonstração não devem ser tratados como versão comercial final nem apresentados como testados em produção. Para preparar bundles comerciais assinados, segue [`RELEASE-SETUP.md`](RELEASE-SETUP.md). O workflow manual prepara seis AABs assinados e seis APKs assinados para instalação direta; não publica automaticamente na Google Play. Os identificadores de pacote finais têm de ser escolhidos antes da publicação.