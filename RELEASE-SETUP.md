# StudioMesa — preparação para lançamento comercial

> **Estado verificado em 14/11/2026: NÃO LIBERADO PARA OPERAÇÃO COM DADOS REAIS OU VENDA OPERACIONAL.** As PRs #133 e #134 estão integradas. A CI pós-merge da `main` [43512084404](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/43512084404) e a CI da PR #134 [43512547155](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/43512547155) passaram compilação APK, replay PostgreSQL/autorização, Android 8 e Android 16.
>
> **Backend:** o Supabase está activo e saudável, com 63 migrações remotas e oito migrações locais pendentes. As estruturas de receitas/consumo de stock, permissões granulares e entitlements por plano continuam ausentes. A migração de linhas de faturas, recepção de mercadoria e sincronização SAF-T também não foi aplicada. Não executar `db push` em produção antes de backup/restauro comprovados, validação isolada e aprovação.
>
> **Backups e custo:** a organização está no plano Free, sem backups automáticos diários nem Point-in-Time Recovery. Não existe ainda um backup integral com restauro ensaiado. O runbook exige exportação e ensaio separados de PostgreSQL, Auth e Storage antes de qualquer migração remota.
>
> **Auth e segurança:** SMTP próprio, confirmação de email, recuperação e eliminação de conta ainda precisam de testes ponta a ponta com contas descartáveis. A protecção contra palavras-passe comprometidas está desactivada. Os avisos de funções `SECURITY DEFINER`, tabelas com RLS sem políticas e índices devem ser avaliados individualmente; não aplicar revogações ou políticas genéricas em massa.
>
> **Release:** continuam por definir o identificador final da aplicação, a keystore comercial e os secrets/variables de assinatura. O workflow comercial exige quatro variáveis de prontidão exactamente iguais a `YES`: migrações/backup/restauro do backend, autenticação SMTP, testes físicos e aprovação legal/privacidade/licenças. Estes valores só devem ser definidos após os controlos serem efectivamente concluídos. Política de privacidade, Data Safety, termos, DPA e revisão de licenças continuam por aprovar.


## Toolchain Android

A configuração atual usa Kotlin `2.3.0`, Android Gradle Plugin `8.13.2`, Gradle `8.13` e JDK 17. O AGP 8.13.2 incorpora R8 8.13.19, a versão indicada pela documentação Android para tratar metadados Kotlin 2.3. A CI completa tem de validar esta combinação em todas as variantes e no build de release antes de usar artefactos comerciais.

Referências: [compatibilidade Kotlin/AGP/D8/R8](https://developer.android.com/build/kotlin-support) · [notas AGP 8.13.2](https://developer.android.com/build/releases/agp-8-13-0-release-notes) · [versões mínimas de Gradle por AGP](https://developer.android.com/build/releases/about-agp).

## Estado real

O workflow `.github/workflows/commercial-release.yml` foi preparado para criar cinco Android App Bundles (AAB) e cinco APKs assinados para instalação direta. **Não publica automaticamente na Google Play**. Antes de correr esse workflow, é necessário definir o identificador de aplicação final e guardar a chave de assinatura nos GitHub Actions Secrets.

Os APKs do workflow normal continuam a usar os identificadores `pt.studiomesa.preview.*`. A configuração comercial é opt-in e não altera a identidade dos APKs de demonstração.

## Início de sessão social — adiado até depois da primeira venda

Os botões **Continuar com Google** e **Continuar com Microsoft (Outlook)** estão ocultos por defeito na primeira versão comercial. O início de sessão inicial mantém-se por email/palavra-passe; SMTP próprio e confirmação de email continuam obrigatórios.

A implementação OAuth com PKCE foi preservada. Para voltar a mostrar os botões numa versão posterior, depois de criar e configurar as credenciais Google/Microsoft no Supabase e testar os Redirect URLs, compila com:

```bash
-PstudiomesaSocialLoginButtonsEnabled=true
```

Não ativar esta propriedade na primeira release. O valor por defeito é `false`, incluindo nos builds de demonstração e no workflow comercial.

## Decisão obrigatória: identificadores das aplicações

A configuração Gradle atual gera cinco variantes técnicas de demonstração/release, separadas por plano e tipo de app. Isso não significa que devam existir cinco listagens Play. O código dos entitlements Essencial/Profissional/Premium está implementado e coberto por CI, mas a migração que o ativa ainda está pendente no Supabase alojado.

**Política de conteúdo repetitivo:** a Google Play não permite várias apps com funcionalidade, conteúdo e experiência altamente semelhantes e recomenda agregar apps pouco diferenciadas. A estratégia preferível a avaliar é uma app de Gestão e uma app de Cliente, com os planos aplicados por entitlement seguro no servidor, ou distribuição B2B privada. A app Cliente ainda só suporta um cartão de restaurante por conta na `main`; a PR #97 propõe múltiplos cartões, mas a CI [42505026254](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/42505026254) terminou com falha. O novo fluxo de convidado, pesquisa por nome e associação por QR com aprovação do proprietário está na PR #96, encadeada sobre as PRs #95 e #94, e não foi ainda consolidada na `main`. Não prometer carteira multi-restaurante nem a nova entrada por pesquisa/QR até integrar e validar estas funcionalidades. Fontes: https://support.google.com/googleplay/android-developer/answer/18265346 e https://support.google.com/googleplay/android-developer/answer/15884185.

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

Verifica sempre a documentação oficial antes da submissão, porque os requisitos e os fluxos de verificação podem mudar.

- **Target API:** desde 31 de agosto de 2026, novas apps Android móveis e atualizações têm de usar Android 16 / API 36 ou superior. O StudioMesa está atualmente com `targetSdk = 36`; confirma o valor no AAB final. Fonte: https://support.google.com/googleplay/android-developer/answer/11926878?hl=pt
- **Idade e titularidade:** o titular que cria a conta Play Console tem de ter pelo menos 18 anos. A conta deve pertencer à pessoa ou entidade que realmente publica e responde pela aplicação; não utilizar identidade ou dados de terceiros como se fossem teus. Fonte: https://support.google.com/googleplay/android-developer/answer/6112435
- **Tipo de conta:** a Google distingue conta pessoal e conta de organização. A documentação recomenda conta de organização quando a publicação é feita em nome de uma empresa/atividade comercial; requer número D‑U‑N‑S e verificação da organização. A conta pessoal é descrita para uso individual/estudante/hobby e tem requisitos adicionais de testes e dispositivo. Fonte: https://support.google.com/googleplay/android-developer/answer/13634885
- **Taxa:** a inscrição custa atualmente US$25, paga uma única vez. A Google indica cartões de crédito/débito aceites por localização e não aceita cartões pré-pagos; confirmar o método disponível em Portugal no momento do registo. Fonte: https://support.google.com/googleplay/android-developer/answer/6112435
- **Contas pessoais novas:** as contas pessoais criadas depois de 13 de novembro de 2023 precisam de um teste fechado com pelo menos 12 participantes inscritos continuamente durante 14 dias antes de pedir acesso à produção. Fonte: https://support.google.com/googleplay/android-developer/answer/14151465
- **Verificação de dispositivo pessoal:** novas contas pessoais também têm de demonstrar acesso a um dispositivo Android físico, não root, com Android 10 ou superior, através da aplicação Play Console. Fonte: https://support.google.com/googleplay/android-developer/answer/14316361
- **Privacidade:** cada aplicação precisa de política de privacidade pública, não editável e acessível globalmente, além de ligação/texto dentro da app. A secção Data safety tem de ser respondida para cada app e corresponder ao binário, aos SDKs e à configuração cloud efetivamente publicada. Fontes: https://support.google.com/googleplay/android-developer/answer/10144311 e https://support.google.com/googleplay/android-developer/answer/10144311?hl=pt
- **Eliminação de conta:** como o StudioMesa permite criar conta, tem de haver um caminho facilmente acessível dentro da app e um recurso web externo para solicitar a eliminação da conta e dos dados associados. A retenção excecional tem de ter fundamento legítimo e ser claramente explicada. O fluxo atual está implementado, mas ainda precisa de teste E2E com contas descartáveis. Fonte: https://support.google.com/googleplay/android-developer/answer/13327111

**Situação do projeto:** os cinco AAB/APKs comerciais ainda não foram gerados com uma chave definitiva; não existe ficha Play Console aprovada; a política pública e Data safety continuam em rascunho. A versão atual não está liberada para submissão.

Não publiques ainda: permanecem pendentes a reconciliação e deployment seguro do Supabase, autenticação/SMTP, E2E com contas descartáveis, política de privacidade, Data safety, licença do modelo, assinatura comercial, package IDs e testes de aceitação.
## Recurso web externo para eliminação de conta

A página pública já está ativa em:

**https://svwvkfsyevuyabelrtjk.supabase.co/functions/v1/account-deletion-web**

A página envia uma ligação única ao email da conta; depois de a pessoa autenticar a sessão, permite pedir a eliminação. Não é seguro permitir eliminação apenas por introduzir um email.

**Estado atual da página de eliminação:** a versão ativa usa `resetPasswordForEmail` com callback PKCE e mensagem neutra, não `signInWithOtp`. A eliminação efetiva é feita por `delete-account`, num endpoint separado que exige JWT. O envio por email, callback no mesmo navegador, troca PKCE e eliminação com contas descartáveis continuam sem teste E2E; por isso, não afirmar que a proteção contra enumeração ou a eliminação estão totalmente validadas. Ver `SUPABASE-SETUP.md` e `docs/SUPABASE-LIVE-RECONCILIATION-2026-11-06.md`.

**Configuração obrigatória antes de testar o email:**

1. Abre o [Dashboard do Supabase](https://supabase.com/dashboard/project/svwvkfsyevuyabelrtjk/auth/url-configuration) e entra em **Authentication → URL Configuration**.
2. Em **Redirect URLs**, adiciona exatamente:
   `https://svwvkfsyevuyabelrtjk.supabase.co/functions/v1/account-deletion-web`
3. Não substituas o URL principal do projeto nem removas os redirecionamentos Android já necessários para `studiomesa://auth-callback`.
4. Confirma que o fornecedor de email está ativo. Antes de produção, configura um SMTP próprio com um endereço de envio controlado pelo responsável pela app; o envio padrão de teste do Supabase pode estar sujeito a limites.
5. Com uma conta descartável, testa pedido de ligação, clique na ligação no mesmo navegador, eliminação de conta não proprietária e bloqueio de proprietário com membros. Não uses uma conta real para estes testes.

A página foi verificada por carregamento HTTP público, mas **o percurso completo por email ainda não foi validado**. O endereço acima pode ser indicado como recurso externo de eliminação na Play Console só depois de concluir os testes.

**Verificação técnica de 29/10/2026:** o endpoint privado `delete-account` foi atualizado no projeto Supabase para a versão 3, com `verify_jwt=true` e dependência `npm:@supabase/server@1.9.2`, alinhada com o código versionado. Um pedido HTTP público sem token recebeu `401`, e a página pública continuou a responder com o formulário de eliminação. Isto comprova a barreira de autenticação e a disponibilidade da página; não comprova a entrega do email, a troca PKCE nem a eliminação real de uma conta descartável.

## Consultor de preços com aprovação do proprietário — estado de 29/10/2026

A migração `20261029203424_restaurant_price_adviser_owner_approval.sql` está aplicada ao Supabase alojado. A opção começa desligada; só o proprietário pode ativá-la e cada aumento exige confirmação individual. O servidor recalcula o preço com os dados atuais, bloqueia reduções e grava o histórico numa tabela privada. A CI da versão principal passou na execução [40925456259](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/40925456259). **Ainda não considerar pronta para uso comercial** até executar testes ponta a ponta com contas descartáveis de proprietário/funcionário e validar o comportamento em Android físico.

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

[REGISTO HISTÓRICO — 29/10/2026, não usar como fotografia atual] Naquela data foram verificadas 41 versões aplicadas e privilégios por omissão fechados. Esta fotografia foi ultrapassada: em 06/11/2026 o histórico tem 54 versões remotas e 53 ficheiros locais, com cinco versões só locais e seis só remotas. O estado atual está em `docs/SUPABASE-LIVE-RECONCILIATION-2026-11-06.md`. A migração de privilégios por omissão foi aplicada historicamente, mas a divergência atual impede qualquer novo deployment.


A verificação da compilação, por si só, não certifica que a aplicação esteja pronta para operação comercial.


## Auditoria do projeto alojado — histórico de 28/10 e atualização de 29/10/2026

O relatório [`PRODUCTION-READINESS-2026-10-28.md`](PRODUCTION-READINESS-2026-10-28.md) regista a auditoria de leitura realizada a 28/10, quando ainda não tinha sido alterada a configuração de privilégios por omissão. A 29/10 foi aplicada a migração `20261029133656_revoke_postgres_default_public_privileges.sql` e as predefinições foram verificadas novamente; lê o estado atualizado acima e em `PRODUCT-READINESS-CHECKLIST.md`. O relatório datado de 28/10 é um registo histórico e não deve ser usado isoladamente como estado atual.

A organização continua no plano Free. A proteção contra palavras-passe comprometidas permanece desativada e requer Pro ou superior segundo a [documentação oficial de segurança de palavras-passe](https://supabase.com/docs/guides/auth/password-security). O aviso de funções privilegiadas chamadas por utilizadores autenticados continua a exigir revisão individual e testes de autorização.

Estes resultados não substituem os testes de autenticação ponta a ponta, os testes com contas separadas de diferentes restaurantes, a revisão independente das RPCs privilegiadas nem os testes em dispositivos físicos.
