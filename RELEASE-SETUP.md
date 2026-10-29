# StudioMesa — preparação para lançamento comercial

## Estado real

O workflow `.github/workflows/commercial-release.yml` foi preparado para criar cinco Android App Bundles (AAB) e cinco APKs assinados para instalação direta. **Não publica automaticamente na Google Play**. Antes de correr esse workflow, é necessário definir o identificador de aplicação final e guardar a chave de assinatura nos GitHub Actions Secrets.

Os APKs do workflow normal continuam a usar os identificadores `pt.studiomesa.preview.*`. A configuração comercial é opt-in e não altera a identidade dos APKs de demonstração.

## Decisão obrigatória: identificadores das aplicações

A configuração de sabores atual produz aplicações separadas para Essencial/Profissional/Premium e Gestão/Cliente. O workflow comercial acrescenta os sufixos de sabor ao identificador base definido na variável `STUDIOMESA_RELEASE_APPLICATION_ID`.

Antes de publicar, decide se queres realmente distribuir cinco aplicações separadas. Se pretendes uma única aplicação de gestão com funcionalidades diferentes conforme o plano, a configuração dos sabores e a forma de ativar as funcionalidades precisam de ser revistas antes de criar as fichas na loja.

O identificador de pacote é permanente depois da publicação. Cria primeiro a aplicação na Play Console e confirma o nome do pacote que fica registado. A Google Play regista o nome de pacote da nova aplicação na conta do programador; se o nome já estiver em uso, será necessário escolher outro. Referência: https://developer.android.com/developer-verification/guides/google-play-console

## Criar uma chave de assinatura no Windows

Faz isto no teu computador, numa pasta privada que não esteja dentro da pasta do repositório. A chave privada e as palavras-passe **nunca devem ser enviadas por email, coladas numa conversa ou guardadas no GitHub como ficheiros**.

1. Instala um JDK compatível com Java 17, se ainda não estiver instalado.
2. Abre o PowerShell na pasta privada escolhida e executa:

   ```powershell
   keytool -genkeypair -v -keystore .\studiomesa-release.jks -storetype JKS -keyalg RSA -keysize 2048 -validity 10000 -alias studiomesa-release
   ```

3. Define uma palavra-passe forte para o keystore e outra para a chave quando forem pedidas. Guarda ambas num gestor de palavras-passe. Guarda também duas cópias de segurança offline do ficheiro `studiomesa-release.jks`, em locais seguros.
4. Cria uma representação Base64 para o GitHub Actions. Este ficheiro contém a chave privada codificada; trata-o como a própria chave:

   ```powershell
   [Convert]::ToBase64String([System.IO.File]::ReadAllBytes(".\studiomesa-release.jks")) | Set-Content -NoNewline ".\studiomesa-release.jks.base64"
   ```

5. Abre o ficheiro `studiomesa-release.jks.base64` localmente para copiar o respetivo conteúdo diretamente para o segredo GitHub `ANDROID_KEYSTORE_BASE64`. Não o envies nem o adiciones ao repositório. Depois de configurares o segredo, apaga de forma segura a cópia Base64 temporária.

**Não percas o keystore ou as palavras-passe.** São material crítico para assinar atualizações futuras. Não uses uma chave de demonstração/debug para distribuição comercial.

## Guardar os valores em GitHub

No repositório GitHub, abre **Settings → Secrets and variables → Actions**.

Cria estes quatro **Repository secrets**:

- `ANDROID_KEYSTORE_BASE64`: conteúdo Base64 do ficheiro privado.
- `ANDROID_KEYSTORE_PASSWORD`: palavra-passe do keystore.
- `ANDROID_KEY_ALIAS`: `studiomesa-release`, se usaste o comando acima.
- `ANDROID_KEY_PASSWORD`: palavra-passe da chave.

No separador **Variables**, cria a variável `STUDIOMESA_RELEASE_APPLICATION_ID` com o identificador base final, validado na Play Console. Não incluas os sufixos `.essencial`, `.profissional`, `.premium`, `.restaurante` ou `.cliente`; são acrescentados pela configuração Gradle. O valor tem de ser um identificador Android válido, com segmentos separados por pontos e sem hífenes. Não uses o valor de exemplo de outra pessoa.

## Executar a preparação de lançamento

1. Abre **Actions → Build signed StudioMesa commercial AABs → Run workflow**.
2. No campo de confirmação, escreve exatamente `BUILD`.
3. O workflow verifica os segredos, valida o keystore, executa os testes unitários para as cinco variantes de distribuição e Android Lint, compila cinco AABs e cinco APKs com assinatura, verifica ambos os formatos e gera checksums.
4. Descarrega o artefacto `StudioMesa-Commercial-AABs-and-APKs` e guarda o ficheiro de checksums.

Se a variável de identificador ou qualquer segredo estiver em falta, a execução falha com uma mensagem explícita. O workflow **não** envia os AAB para a Google Play nem publica a aplicação. Os APKs assinados destinam-se a instalações diretas autorizadas; distribui-os apenas por canais controlados.

## Requisitos da Google Play e conta do programador

Antes de submeter a aplicação, confirma os requisitos oficiais atuais:

- A aplicação já usa `targetSdk = 36`, o nível de API exigido pela política de destino da Google Play para novas aplicações/atualizações desde 31 de agosto de 2026: https://support.google.com/googleplay/android-developer/answer/11926878?hl=pt
- É obrigatória uma política de privacidade pública, com ligação na Play Console e dentro da aplicação, e o formulário **Data safety** tem de refletir a recolha, partilha, segurança e eliminação reais dos dados: https://support.google.com/googleplay/android-developer/answer/10144311
- A eliminação de conta está implementada, mas tem de ser testada de ponta a ponta com contas descartáveis antes do lançamento. A ligação/ação de eliminação deve permanecer acessível ao utilizador.
- As contas pessoais de programador criadas depois de 13 de novembro de 2023 têm de cumprir o requisito de teste fechado com pelo menos 12 participantes inscritos continuamente durante 14 dias consecutivos antes de pedir acesso à produção; confirma se esse requisito se aplica ao tipo de conta que vai publicar: https://support.google.com/googleplay/android-developer/answer/14151465?hl=pt
- O titular de uma conta de programador da Google Play tem de ter pelo menos 18 anos e concluir os passos de registo/verificação aplicáveis; a taxa de registo é cobrada pela Google e deve ser confirmada no fluxo oficial: https://support.google.com/googleplay/android-developer/answer/6112435?hl=pt

Não publiques ainda: continuam pendentes os testes ponta a ponta de login/recuperação, separação entre restaurantes, criação/repetição de operações e eliminação de conta, bem como a política de privacidade e o formulário Data safety.

## Recurso web externo para eliminação de conta

A página pública já está ativa em:

**https://svwvkfsyevuyabelrtjk.supabase.co/functions/v1/account-deletion-web**

A página envia uma ligação única ao email da conta; depois de a pessoa autenticar a sessão, permite pedir a eliminação. Não é seguro permitir eliminação apenas por introduzir um email.

**Configuração obrigatória antes de testar o email:**

1. Abre o [Dashboard do Supabase](https://supabase.com/dashboard/project/svwvkfsyevuyabelrtjk/auth/url-configuration) e entra em **Authentication → URL Configuration**.
2. Em **Redirect URLs**, adiciona exatamente:
   `https://svwvkfsyevuyabelrtjk.supabase.co/functions/v1/account-deletion-web`
3. Não substituas o URL principal do projeto nem removas os redirecionamentos Android já necessários para `studiomesa://auth-callback`.
4. Confirma que o fornecedor de email está ativo. Antes de produção, configura um SMTP próprio com um endereço de envio controlado pelo responsável pela app; o envio padrão de teste do Supabase pode estar sujeito a limites.
5. Com uma conta descartável, testa pedido de ligação, clique na ligação no mesmo navegador, eliminação de conta não proprietária e bloqueio de proprietário com membros. Não uses uma conta real para estes testes.

A página foi verificada por carregamento HTTP público, mas **o percurso completo por email ainda não foi validado**. O endereço acima pode ser indicado como recurso externo de eliminação na Play Console só depois de concluir os testes.

## O que ainda tem de ser concluído antes de vender

- Testes ponta a ponta dos pedidos, reservas, inventário, fidelização, permissões, login, recuperação e logout em dispositivos reais e com contas de teste separadas.
- Verificação independente de privacidade, regras RLS e todos os endpoints privilegiados; o aviso sobre funções `SECURITY DEFINER` deve ser avaliado pelo comportamento e pelas permissões reais, não resolvido por revogar permissões às cegas.
- Configuração de email/SMTP e URLs de retorno de autenticação no Supabase. Os fluxos de registo, confirmação e recuperação têm de ser demonstrados de ponta a ponta.
- Cópias de segurança, teste de restauro, política de privacidade, retenção/eliminação/exportação de dados, suporte e processo de incidentes.
- Decisão sobre como os planos comerciais serão atribuídos e aplicados. Os preços no README são valores de referência, não pagamentos nem subscrições implementados.
- Configuração da ficha Google Play, classificação etária, declaração de segurança dos dados, capturas de ecrã, testes internos e revisão da loja.
- Revisão dos requisitos atuais de publicação. A configuração atual usa `targetSdk = 36`, que satisfaz o requisito de API de destino anunciado pela Google para novas aplicações móveis submetidas desde 31 de agosto de 2026: https://support.google.com/googleplay/android-developer/answer/11926878?hl=pt


## Migrações futuras e exposição da Data API (verificado em 29/10/2026)

A Supabase anunciou que, a partir de **30/10/2026**, novas tabelas em `public` deixam de receber automaticamente os privilégios para `anon`, `authenticated` e `service_role` que permitem exposição pela Data API. As tabelas existentes mantêm os privilégios atuais; o risco principal é uma migração futura criar uma tabela sem os `GRANT`s necessários e a API REST responder com erro de permissões. As funções exigem uma revisão separada: o PostgreSQL pode conceder `EXECUTE` a `PUBLIC` por omissão, pelo que as migrações devem continuar a revogar esse acesso e conceder apenas aos papéis necessários. Referências oficiais: [alteração de plataforma](https://supabase.com/changelog/45329-breaking-change-tables-not-exposed-to-data-and-graphql-api-automatically) e [proteção da Data API](https://supabase.com/docs/guides/api/securing-your-api).

Regra para cada nova migração que crie uma tabela em `public`:

1. Ativar RLS e criar políticas que limitem o acesso ao restaurante e utilizador corretos.
2. Declarar explicitamente os privilégios mínimos necessários com `GRANT` (normalmente para `authenticated`; conceder a `anon` apenas quando a funcionalidade for realmente pública).
3. Para funções/RPCs privilegiadas, definir `SET search_path = ''`, revogar acesso a `PUBLIC`/`anon` e conceder `EXECUTE` apenas aos papéis necessários. Verificar também os privilégios efetivos em teste.
4. Validar a migração numa base de dados descartável com os novos privilégios por omissão, e executar os testes de autorização para confirmar que um restaurante não consegue ler ou alterar dados de outro.

Não aplicar um `GRANT` amplo para `anon` como solução rápida. Os privilégios da tabela controlam se o papel pode aceder ao objeto; o RLS controla as linhas que esse papel pode ver ou alterar. Ambos são necessários.

**Estado observado no projeto alojado em 29/10/2026:** as 11 tabelas de `public` têm RLS ativo; não foi encontrado acesso direto de tabela para `anon`; as tabelas existentes continuam acessíveis com os privilégios atuais. A consulta de privilégios por omissão ainda mostrou permissões amplas para objetos novos antes da data de aplicação da mudança. Não alterámos essas permissões no projeto de produção. Antes de qualquer futura alteração aos privilégios por omissão, deve ser testada uma migração completa, porque revogar EXECUTE por omissão pode afetar funções que as políticas RLS utilizam.


A verificação da compilação, por si só, não certifica que a aplicação esteja pronta para operação comercial.


## Auditoria do projeto alojado — 28/10/2026

Foi feita uma auditoria de leitura à base Supabase, registada em [`PRODUCTION-READINESS-2026-10-28.md`](PRODUCTION-READINESS-2026-10-28.md). Não foram alterados dados, permissões, migrações, utilizadores ou definições de Auth.

A auditoria confirmou RLS em todas as 11 tabelas públicas, políticas presentes, ausência de execução de funções `SECURITY DEFINER` pelo papel `anon` e `search_path` vazio nas funções auditadas. O Security Advisor mantém, no entanto, a proteção contra palavras-passe comprometidas desativada e avisa sobre funções privilegiadas chamadas por utilizadores autenticados. A organização está no plano Free; a verificação de palavras-passe comprometidas requer Pro ou superior segundo a [documentação Supabase](https://supabase.com/docs/guides/auth/password-security).

Estes resultados não substituem os testes de autenticação ponta a ponta, os testes com contas separadas de diferentes restaurantes, a revisão independente das RPCs privilegiadas nem os testes em dispositivos físicos. Consulta o relatório antes de executar a preparação comercial.
