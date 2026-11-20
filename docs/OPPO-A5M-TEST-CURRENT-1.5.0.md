# Ensaio no OPPO A5m — pré-visualização StudioMesa 1.5.0

**Build:** 20/11/2026 — commit `93bc6f11519d7395fd6b4c40544ca5a4c4194898`  
**Dispositivo:** OPPO A5m / Android 16 / ARM64  
**Tipo:** APKs de depuração para teste; não são versões comerciais.

## O que pode ser verificado já no telefone

Usar, preferencialmente, os APKs **Premium Gestão** e **Premium Cliente** do mesmo artefacto GitHub Actions. Cada APK ocupa cerca de 41 MB. As duas aplicações têm identificadores diferentes e podem coexistir.

Antes de instalar:
- Não usar contas, dados ou faturas de clientes reais.
- Se o Android indicar conflito de assinatura/actualização, parar. Não desinstalar a versão antiga sem confirmar primeiro que não há dados locais de teste a perder.
- Não ignorar avisos de segurança do Android nem conceder permissões que não sejam necessárias.

## Aplicação Cliente

- [ ] A aplicação abre e apresenta o ecrã de boas-vindas.
- [ ] São apresentados os três caminhos: continuar sem conta, criar conta e usar uma conta existente.
- [ ] Continuar sem conta abre o cartão digital de cliente.
- [ ] As secções Cartão, Avisos, Carta, Pontos e Conta abrem sem fechar a aplicação.
- [ ] Os botões Google e Microsoft/Outlook não aparecem; a sua activação foi adiada deliberadamente.

## Aplicação Gestão

- [ ] A aplicação abre no painel de operação.
- [ ] As secções disponíveis abrem e permitem regressar ao painel.
- [ ] A permissão de notificações Android 13+ é pedida apenas no contexto de uma sessão iniciada.
- [ ] Recusar a permissão não impede a utilização normal da aplicação.

## Notificações desta build

A aplicação consulta alterações enquanto está em primeiro plano. A consulta é periódica (60 segundos). Quando configurada e autorizada:
- Cliente: alterações de estado das próprias reservas e confirmação de entrega de recompensas.
- Gestão: novos pedidos e reservas; novos resgates na loja de pontos.

**Limite conhecido:** não há Firebase Cloud Messaging nesta build. Não esperar notificações com a aplicação fechada, parada ou sem execução em primeiro plano.

## O que não deve ser testado com dados reais nesta build

A migração `20261108170000_customer_multi_restaurant_cards.sql` ainda não foi aplicada ao Supabase alojado. Por isso, não usar esta pré-visualização para validar a associação de várias fichas de restaurante numa conta real, nem para gerir clientes ou campanhas de um restaurante pagante. Essa validação exige backend de ensaio com a migração aplicada e contas fictícias.

Também não considerar as fichas técnicas/consumo de stock, as permissões granulares de funcionários, a loja por quotas/fotografias ou a IA cloud como operacionalmente concluídas no Supabase alojado. A PR #92 que contém parte dessas alterações ainda está divergente e não foi integrada.

## Informação a enviar depois do ensaio

Registar apenas:
- aplicação testada: Gestão ou Cliente;
- ecrã que abriu;
- resultado: passou / falhou;
- texto exacto do erro, se apareceu.

Não enviar palavras-passe, códigos de autenticação, tokens, dados pessoais ou capturas que mostrem informação real de clientes.

## Critério de aceitação

O teste físico só fica aprovado depois de a aplicação abrir no OPPO A5m e os ecrãs desta checklist serem verificados no dispositivo real. Os testes automáticos Android 8/16 já passaram, mas não substituem este ensaio físico.
