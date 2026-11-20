# Ensaio no OPPO A5m — pré-visualização StudioMesa 1.5.0

**Build:** 20/11/2026 — commit `b2c17ee85a31a0b8f00d6c22342b10176021af20`  
**CI:** [workflow 44809660302](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/44809660302) — os quatro jobs terminaram com sucesso.  
**Dispositivo-alvo:** OPPO A5m / CPH2727 / Android 16 / ARM64  
**Tipo:** APKs ARM64 de depuração para teste; não são releases comerciais.

## Artefacto desta revisão

Artefacto GitHub Actions: `StudioMesa-1.5.0-ARM64-Preview-APKs`  
ID: `14668750452`  
SHA-256 do ZIP: `5d8fcbe59ff78867f2b8283a27a4bab9364d30d5316b19e8f2288d6711646516`

Contém cinco APKs, todos com cerca de 42,6 MB:
- Gestão Premium: `premiumRestaurante/debug/app-premium-restaurante-debug.apk`
- Cliente Premium: `premiumCliente/debug/app-premium-cliente-debug.apk`
- Gestão Profissional: `profissionalRestaurante/debug/app-profissional-restaurante-debug.apk`
- Cliente Profissional: `profissionalCliente/debug/app-profissional-cliente-debug.apk`
- Gestão Essencial: `essencialRestaurante/debug/app-essencial-restaurante-debug.apk`

Para o ensaio no OPPO, usar primeiro **Gestão Premium** e **Cliente Premium** do mesmo artefacto. Não misturar APKs de execuções diferentes.

## Antes de instalar

- Não usar contas, dados, clientes ou faturas reais.
- Não desinstalar uma versão anterior sem confirmar que não há dados locais de teste a perder.
- Se o Android indicar conflito de assinatura, parar e registar a mensagem; não contornar removendo aplicações à pressa.
- Não conceder permissões que não sejam necessárias.

## Aplicação Cliente

- [ ] Instala e abre sem fechar inesperadamente.
- [ ] Mostra o ecrã de boas-vindas.
- [ ] Apresenta os caminhos de convidado, criar conta e usar conta existente.
- [ ] O modo convidado abre o cartão digital.
- [ ] Cartão, Avisos, Carta, Pontos e Conta abrem e permitem regressar.
- [ ] Google e Microsoft/Outlook continuam ocultos, conforme decisão de adiar OAuth até depois da primeira venda.

## Aplicação Gestão

- [ ] Instala e abre no painel de operação.
- [ ] Os ecrãs disponíveis abrem e permitem regressar ao painel.
- [ ] O pedido de permissão de notificações Android 13+ só aparece no contexto de uma sessão iniciada.
- [ ] Recusar a permissão não impede a utilização normal da aplicação.

## Correcção incluída nesta build: abrir o destino da notificação

Esta revisão corrige o toque nas notificações: o aviso transporta o destino, a Gestão verifica se a página está autorizada para a sessão e o Cliente só aceita destinos conhecidos.

- [ ] Com contas fictícias e eventos de teste, tocar num aviso do Cliente abre a secção indicada.
- [ ] Tocar num aviso da Gestão abre a secção indicada (por exemplo, Cozinha, Reservas ou Pontos).
- [ ] Confirmar que um funcionário não consegue abrir uma página que não faz parte das permissões da sua sessão.

A CI inclui testes unitários da política de destinos e testes de interface para a abertura de Avisos no Cliente e Cozinha na Gestão. O ensaio físico continua necessário.

## Limitações que continuam activas

- As notificações consultam alterações enquanto a aplicação está em primeiro plano, com polling periódico. **Não há Firebase Cloud Messaging nesta build**; não esperar avisos com a aplicação fechada ou parada.
- A migração `20261108170000_customer_multi_restaurant_cards.sql` ainda não foi aplicada ao Supabase alojado. Não testar cartões de vários restaurantes numa conta real nem gerir campanhas de clientes pagantes.
- Fichas técnicas/consumo de stock, permissões granulares de funcionários, quotas/fotografias da loja e IA cloud não devem ser consideradas operacionais no Supabase alojado. Parte destas alterações está na PR #92, ainda divergente e não integrada.
- A CI executa testes em emuladores Android 8 e Android 16; isso não equivale a um teste no OPPO real.
- Esta build usa assinatura de depuração. Não é uma versão comercial nem deve ser distribuída a restaurantes para operação com dados reais.

## Fluxos já confirmados anteriormente pelo proprietário

Não é necessário repetir apenas por rotina os testes que já foram confirmados no OPPO A5m: funcionalidades gratuitas, reservas, sincronização Gestão/Cliente e isolamento de dados entre restaurantes. O objectivo deste ensaio é validar a build nova, a navegação e, sobretudo, a correcção de toque nas notificações.

## Registo do resultado

Registar apenas a aplicação, o ecrã, passou/falhou e o texto exacto do erro. Não partilhar palavras-passe, códigos, tokens, dados pessoais ou capturas com informação real de clientes.

**Critério:** só marcar o ensaio físico como aprovado depois de executar estes passos no OPPO A5m. Não transformar um teste de emulador ou uma compilação verde numa afirmação de prontidão comercial.
