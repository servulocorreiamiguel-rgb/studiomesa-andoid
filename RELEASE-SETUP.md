# StudioMesa — preparação para lançamento comercial

## Estado real

O workflow `.github/workflows/commercial-release.yml` foi preparado para criar cinco Android App Bundles (AAB) assinados, mas **não publica automaticamente na Google Play**. Antes de correr esse workflow, é necessário definir o identificador de aplicação final e guardar a chave de assinatura nos GitHub Actions Secrets.

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
3. O workflow verifica os segredos, valida o keystore, executa os testes unitários para as cinco variantes de distribuição e Android Lint, compila cinco AABs com assinatura e verifica as assinaturas.
4. Descarrega o artefacto `StudioMesa-Commercial-AABs` e guarda o ficheiro de checksums.

Se a variável de identificador ou qualquer segredo estiver em falta, a execução falha com uma mensagem explícita. O workflow **não** envia os AAB para a Google Play nem publica a aplicação.

## O que ainda tem de ser concluído antes de vender

- Testes ponta a ponta dos pedidos, reservas, inventário, fidelização, permissões, login, recuperação e logout em dispositivos reais e com contas de teste separadas.
- Verificação independente de privacidade, regras RLS e todos os endpoints privilegiados; o aviso sobre funções `SECURITY DEFINER` deve ser avaliado pelo comportamento e pelas permissões reais, não resolvido por revogar permissões às cegas.
- Configuração de email/SMTP e URLs de retorno de autenticação no Supabase. Os fluxos de registo, confirmação e recuperação têm de ser demonstrados de ponta a ponta.
- Cópias de segurança, teste de restauro, política de privacidade, retenção/eliminação/exportação de dados, suporte e processo de incidentes.
- Decisão sobre como os planos comerciais serão atribuídos e aplicados. Os preços no README são valores de referência, não pagamentos nem subscrições implementados.
- Configuração da ficha Google Play, classificação etária, declaração de segurança dos dados, capturas de ecrã, testes internos e revisão da loja.
- Revisão dos requisitos atuais de publicação. A configuração atual usa `targetSdk = 36`, que satisfaz o requisito de API de destino anunciado pela Google para novas aplicações móveis submetidas desde 31 de agosto de 2026: https://support.google.com/googleplay/android-developer/answer/11926878?hl=pt

A verificação da compilação, por si só, não certifica que a aplicação esteja pronta para operação comercial.
