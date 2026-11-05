# StudioMesa — integração não-IA e preparação comercial

**Data:** 5 de novembro de 2026  
**Branch:** `integration/non-ai-core-2026-11-05`  
**Estado:** integração de código preparada para validação; ainda não integrada na `main` e não aprovada para produção.

## Âmbito desta integração

Esta branch junta à revisão atual da `main`, sem incluir o trabalho de IA:

- QR de fidelização temporário e de utilização única, emitido pelo cliente e validado pelo restaurante no servidor.
- Loja de recompensas configurável pelo proprietário, com custo em pontos, stock opcional, pedidos de resgate e confirmação de entrega.
- Histórico de resgates visível ao próprio cliente.
- Pedidos de reserva na aplicação Cliente: criação idempotente, consulta e cancelamento pelo próprio titular.
- Atribuição de mesa e confirmação sob controlo da equipa, com validação de capacidade e conflitos no servidor.
- Permissões granulares por funcionário para pedidos, mesas, reservas, carta, stock e fidelização; ações administrativas e económicas continuam reservadas ao proprietário.
- Testes unitários de permissões, testes PostgreSQL de autorização e casos de interface/documentação para os novos fluxos.

A branch preserva as funcionalidades que já estavam na `main`, incluindo mesas, carta, pedidos, cozinha, stock, desperdício, custos de inventário, gestão económica, análise por período e exportação CSV.

## IA deliberadamente excluída

Não foram integradas as PRs #79 e #80 nem qualquer modelo generativo, OCR de faturas ou serviço externo. A IA fica para uma fase separada de Codex, como pedido no projeto. Não configurar chaves, faturação de API nem Edge Functions de IA nesta fase.

## Migrações e Supabase

A integração acrescenta oito ficheiros SQL novos à branch e mantém o ficheiro de custo de inventário já existente na `main`, evitando duplicar a mesma migração com outro timestamp. A CI está configurada para reproduzir 51 ficheiros SQL numa base descartável.

**Não executar `db push`, `migration repair`, `apply_migration` nem alterações manuais no Supabase alojado.** A auditoria de 4/11 registou 54 versões remotas e 43 ficheiros SQL locais na `main`; a branch de integração altera essa contagem, mas isso não demonstra alinhamento com o histórico remoto. Antes de qualquer deploy são obrigatórios:

1. Comparação versão a versão do histórico remoto e local, incluindo a origem e o conteúdo de cada versão.
2. Backup manual cifrado e ensaio de restauro numa instância isolada.
3. Replay integral numa base descartável e revisão de RLS, privilégios e RPCs.
4. Plano de aplicação e reversão aprovado, seguido de verificações pós-deploy.

## Validação obrigatória desta branch

A CI da branch deve passar, no commit final, em todos os blocos:

- Compilação das cinco variantes Android.
- Testes unitários e Android Lint.
- Replay SQL limpo e testes PostgreSQL de isolamento entre restaurantes, autorização e idempotência.
- Smoke tests Android 8 e Android 16.

A CI histórica de branches anteriores não substitui a execução desta branch consolidada. Depois da CI, continuam obrigatórios testes num telemóvel físico e com duas contas descartáveis.

### Testes manuais antes de piloto

- Cliente A não consegue ver/cancelar reservas ou resgates do cliente B.
- Funcionário sem a permissão correspondente não consegue ler nem alterar a área no servidor.
- Um funcionário não consegue conceder permissões a si próprio nem aceder a outro restaurante.
- Um QR não pode ser reutilizado, pertence ao titular e expira.
- Resgates repetidos não descontam pontos nem stock duas vezes.
- Reservas concorrentes, lotação insuficiente e horários sobrepostos são recusados.
- Falha de rede após escrita não cria pedidos, movimentos ou reservas duplicados.
- Permissão de câmara só é pedida ao utilizar leitura QR; recusa de permissão não bloqueia o resto da aplicação.
- Logout e troca de conta limpam caches, histórico e permissões anteriores.

## Bloqueios comerciais fora do código

- Testes ponta a ponta de login, confirmação de email, recuperação, OAuth, expiração e revogação de sessão.
- Backup/restauro verificado, monitorização, suporte, resposta a incidentes e procedimento de eliminação de conta.
- Política de privacidade pública, Google Play Data safety, contrato e âmbito de suporte.
- Keystore comercial, identificador definitivo, AABs assinados e publicação/distribuição.
- Integração com POS, pagamentos, impressoras e software fiscal/contabilístico: não existe ainda integração confirmada. O CSV é apenas operacional e o QR de fidelização não é um QR fiscal/ATCUD.
- Piloto controlado com dados fictícios antes de qualquer utilização real.

Os preços indicativos Essencial 890 €, Profissional 1 490 € e Premium 1 690 € continuam por validar com clientes e não representam pagamentos ou contratos configurados.

## Sequência de trabalho

1. Validar e corrigir a CI desta branch; só depois considerar merge na `main`.
2. Fazer testes físicos e E2E com contas descartáveis.
3. Reconciliar o histórico Supabase e testar backup/restauro antes de qualquer deploy.
4. Concluir assinatura, privacidade, suporte, contrato e piloto.
5. Preparar os sites: site institucional StudioMesa, páginas de planos/demonstração, captação de contactos e páginas de demonstração específicas para restaurantes.
6. Só depois abrir a fase de IA com Codex, separada do lançamento funcional sem IA.

**Critério de fecho:** código integrado + CI completa do commit final + validação física/E2E + backend reconciliado e restaurável + documentação comercial e de privacidade + versão Android assinada.