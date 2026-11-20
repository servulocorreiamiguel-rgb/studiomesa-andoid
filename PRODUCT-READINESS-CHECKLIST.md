# StudioMesa — lista de preparação para uso profissional

**Ponto de situação verificado em 20/11/2026 após a CI 44809660302:** a aplicação continua em desenvolvimento e **não está autorizada para operação com dados reais nem venda operacional**.

## Última revisão de código e testes

- A PR [#135](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/pull/135) contém a integração mais recente da aplicação Cliente, cartões multi-restaurante, campanhas, notificações locais e a correcção para abrir a secção indicada ao tocar num aviso. Continua em rascunho e não altera a `main`.
- A CI [44809660302](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/44809660302), no commit `b2c17ee85a31a0b8f00d6c22342b10176021af20`, terminou com **os quatro jobs aprovados**:
  - Compilação das cinco variantes Android e testes unitários.
  - Android Lint e verificações de segurança/configuração.
  - Replay das migrações numa base PostgreSQL descartável, testes de autorização, isolamento e idempotência.
  - Smoke tests nos emuladores Android 8 (API 26) e Android 16.
- O artefacto ARM64 [14668750452](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/44809660302) contém cinco APKs debug de pré-visualização, cada um com cerca de 42,6 MB. São para testes, não são releases comerciais assinadas.
- A CI usa uma chave temporária para verificar o caminho de assinatura. Essa chave não pertence ao proprietário e não pode ser usada para actualizações comerciais.
- O roteiro físico actualizado está em [docs/OPPO-A5M-TEST-CURRENT-1.5.0.md](docs/OPPO-A5M-TEST-CURRENT-1.5.0.md).

## Backend e integração

- O Supabase alojado mantém 63 migrações remotas aplicadas. A branch `work/mobile-completion-2026-11-20` contém 72 ficheiros de migração; nove versões locais continuam por aplicar no projecto alojado: `20261030111815`, `20261102103000`, `20261102120000`, `20261102121000`, `20261104100000`, `20261107170000`, `20261108170000`, `20261112120000` e `20261113114000`.
- Não houve `db push`, reset, migration repair nem alteração de esquema/dados de produção nesta revisão.
- A CI faz replay das migrações numa base de dados descartável. Isso não é backup nem ensaio de restauro do projecto alojado.
- A PR [#92](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/pull/92), que contém receitas/consumo de stock, matriz de permissões e limites da loja, continua divergente e não deve ser integrada em bloco. Exige reconciliação selectiva, revisão de conflitos e CI sobre a base actual.

## Testes já confirmados no dispositivo

O proprietário confirmou no OPPO A5m o funcionamento das funcionalidades gratuitas, reservas, sincronização Gestão/Cliente e isolamento de dados entre restaurantes. Estes quatro pontos não são tratados como testes por fazer. A nova build e os fluxos ainda não verificados continuam a precisar de ensaio físico.

## Bloqueios para lançamento comercial

1. **Migrações:** reconciliar as nove versões pendentes; obter backup recuperável da base de dados, Auth e Storage; testar o restauro numa instância isolada; só depois fazer dry-run e revisão humana.
2. **Autenticação:** testar registo, confirmação de conta, recuperação de palavra-passe, SMTP, refresh, logout e eliminação com contas descartáveis.
3. **Testes reais:** validar a build mais recente no OPPO A5m e executar testes E2E no backend de ensaio, sem dados de clientes reais.
4. **Segurança e privacidade:** rever RLS/RPCs, política de privacidade, termos, tratamento e conservação de dados, licenças e declaração Data Safety.
5. **Release:** decidir identificadores definitivos, criar e guardar a keystore comercial privada e configurar os secrets/variables de Actions.
6. **Notificações:** a versão actual usa polling em primeiro plano; para avisos com a aplicação fechada será necessário implementar/configurar Firebase Cloud Messaging e testar o envio real.
7. **IA e pesquisa Web:** continuam dependentes de fornecedor, segredos e validação de privacidade; não anunciar IA cloud ou pesquisa Web como activas sem configuração e teste real.

**Regra de segurança:** não executar `db push`, reset ou deployment de migrations no Supabase alojado enquanto não existir backup completo com restauro comprovado e uma revisão das autorizações. Não criar uma release comercial com a chave temporária da CI.

## Documentos operacionais

- [Roteiro do OPPO A5m](docs/OPPO-A5M-TEST-CURRENT-1.5.0.md)
- [Runbook de backup e migrações](docs/PRODUCTION-BACKUP-AND-MIGRATION-RUNBOOK.md)
- [Preparação de release](RELEASE-SETUP.md)
- [Plano para a sessão no computador de 21/11/2026](docs/COMPUTER-SESSION-2026-11-21.md)

## Registos históricos

As secções históricas abaixo referem-se à data indicada e não substituem este estado actual. As contagens de migrações da `main` em 13/11/2026 não devem ser confundidas com as 72 migrações da branch de desenvolvimento actual.
