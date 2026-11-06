> **Estado em 06/11/2026 — NÃO PRONTO PARA VENDA/PRODUÇÃO.** O código Android inclui cinco variantes, mas os APKs habituais são builds debug de demonstração e ainda não existe assinatura comercial definitiva.
>
> **CI de reservas #1332:** os quatro jobs passaram: replay PostgreSQL/autorização, compilação/unit tests/Lint, smoke Android 8 e smoke Android 16. A PR #86 foi integrada na `main` no commit `0770ad772f6abb2a44027a462187908a085011cf`. Os testes de interface são condicionados ao plano/tipo da variante; os skips nas variantes sem a respetiva secção são esperados.
>
> **Backend em produção:** o Supabase alojado tem 54 versões de migrations e o repositório tem 53 ficheiros, com cinco versões só locais e seis só remotas. Faltam no backend as tabelas de receitas, permissões granulares, sincronização cloud do assistente e arquivo de faturas, além do bucket privado de documentos. Não executar migrations/deploy antes de reconciliação, backup/restauro e replay descartável.
>
> **Fora de âmbito desta revisão:** testes físicos no OPPO A5m, assinatura comercial, credenciais Auth/SMTP/OAuth, publicação Google Play e integração POS/contabilidade. Consultar [gate de lançamento](docs/COMMERCIAL-RELEASE-GATE-2026-11-06.md), [reconciliação Supabase](docs/SUPABASE-LIVE-RECONCILIATION-2026-11-06.md) e [bloqueios verificados](docs/RELEASE-BLOCKERS-VERIFIED-2026-11-06.md).

## O que ainda falta antes da venda ou utilização em serviço real

- Testes ponta a ponta em telemóveis reais, incluindo vários dispositivos, utilizadores, perda de internet, sessões expiradas e erros de servidor. A compilação de um APK não substitui esses testes.
- Regras comerciais da fidelização: acumulação automática, recompensas configuráveis, resgates, limites e prevenção de fraude. Neste momento, o saldo é ajustado manualmente pelo restaurante.
- Reservas: o fluxo inicial de criação, estados e bloqueio de conflitos por mesa está implementado no código e na migração; ainda falta teste ponta a ponta, edição de reservas, vista de calendário/turnos, lembretes automáticos, depósitos e regras de cancelamento.
- Stock: o inventário partilhado, entradas/saídas, histórico de movimentos, custo unitário opcional, fotografia de custos e valorização indicativa de stock/desperdício estão implementados no código. As fichas técnicas ligam ingredientes aos artigos da carta e, quando aprovadas pelo proprietário, descontam stock transacionalmente ao iniciar a preparação. Ainda faltam testes ponta a ponta em Android físico e dois dispositivos, avisos automáticos, fornecedores/encomendas, inventário por armazém, conversões de unidades e cálculo automático do custo real de cada receita.
- Gestão avançada de equipas ainda incompleta: permissões granulares por função, suspensão temporária, horários/turnos e registos administrativos completos. A remoção de funcionários e a renovação automática do código de adesão já estão implementadas, mas a revogação não é propagada em tempo real a um ecrã que já esteja aberto; a aplicação limpa a cache de dados remotos numa nova sessão e quando o servidor confirma que o utilizador já não pertence ao restaurante.
- Integração com POS/caixa, pagamentos, impressoras de cozinha, recibos ou faturação.
- Cópias de segurança e procedimento testado de recuperação, monitorização, exportação e conservação de dados. O fluxo de eliminação de conta está implementado no Android e no Supabase, mas os casos de conta pessoal/funcionário/proprietário continuam pendentes de testes ponta a ponta com contas descartáveis.
- Página web externa para pedir eliminação de conta publicada em `https://svwvkfsyevuyabelrtjk.supabase.co/functions/v1/account-deletion-web`; o fluxo de email ainda exige URL autorizado no Supabase e teste ponta a ponta.
- **Eliminação de conta:** a página pública usa `resetPasswordForEmail` e PKCE no mesmo navegador; a eliminação propriamente dita é feita pelo endpoint separado `delete-account`, que exige JWT. O fluxo email → callback → eliminação ainda não foi testado de ponta a ponta com contas descartáveis; não afirmar que a enumeração ou a eliminação estão totalmente validadas.
- Política de privacidade pública e formulário Data safety da Google Play ainda por preparar e validar. O histórico de pedidos está implementado; ainda falta registar todas as ações de gestão num trilho de auditoria completo.
- Gestão avançada da carta ainda incompleta: fotografias, disponibilidade por horário, tradução de menus, histórico editorial e validação sistemática de alergénios. Os campos de categoria, descrição e alergénios já existem, mas precisam de revisão pelo restaurante.
- Testes de privacidade, segurança e proteção de dados; revisão independente da configuração de produção. A proteção de palavras-passe comprometidas no Supabase ainda não está ativada.
- Credenciais próprias de OAuth para Google/Microsoft, caso se queira disponibilizar esses métodos de login. O código Android usa PKCE para OAuth, registo e recuperação e guarda o verificador cifrado no Android Keystore; estes fluxos ainda precisam de testes ponta a ponta num dispositivo real. É necessário autorizar o URL de retorno e configurar um fornecedor SMTP para o envio real dos emails.
- Configuração de release comercial assinada e workflow manual para gerar AABs com uma chave privada guardada em GitHub Actions Secrets. Ainda é necessário escolher o identificador de pacote definitivo, configurar a chave, executar o workflow e validar as variantes em dispositivos reais; o workflow não publica automaticamente na Google Play.
- A configuração de compilação usa compileSdk/targetSdk 36, Android Gradle Plugin 8.10.0 e Gradle 8.11.1. O workflow compila os cinco APKs de demonstração/debug, executa testes unitários nas cinco variantes de distribuição e Android Lint. Mesmo com CI aprovado, a app continua a exigir testes ponta a ponta; os APKs atuais não são versões comerciais assinadas.

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