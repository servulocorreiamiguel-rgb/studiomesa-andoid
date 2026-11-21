# StudioMesa — estado dos testes de autenticação
**Verificação do código:** 21/11/2026  
**Branch:** `work/mobile-completion-2026-11-20`  
**CI:** [44815698449](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/44815698449) — quatro jobs aprovados.

## O que existe no código Android

A revisão estática de `app/src/main/java/pt/studiomesa/app/SupabaseApi.kt` confirmou estes caminhos:

- Início de sessão por email/palavra-passe: `POST /auth/v1/token?grant_type=password`.
- Criação de conta com PKCE S256 e redirect `studiomesa://auth-callback`.
- Renovação de sessão por refresh token.
- Recuperação de palavra-passe por `/auth/v1/recover`.
- Validação do código OTP de recuperação de seis algarismos por `/auth/v1/verify`.
- Alteração da palavra-passe autenticada.
- Troca de authorization code por sessão PKCE.
- Encerramento de sessão.

A interface contém os fluxos de recuperação e definição de nova palavra-passe. Isto confirma a existência dos percursos no código, não a sua interoperabilidade com a configuração real do Supabase.

## O que continua por testar no projecto alojado

Não foi executado um ensaio E2E com contas descartáveis através do SMTP real. O acesso de produção não deve ser usado para experimentar ou substituir credenciais de uma conta existente.

Antes de marcar `STUDIOMESA_AUTH_SMTP_VERIFIED=YES`, validar:

1. SMTP: remetente/domínio verificado, entrega e tratamento de rejeições/limites.
2. Confirmação de conta: mensagem recebida, link abre a aplicação e callback PKCE cria sessão.
3. Login e renovação: credenciais correctas/incorrectas, refresh token rotativo e sessão expirada.
4. Recuperação: a template de email envia o código `{{ .Token }}` (não apenas um link mágico), o OTP tem seis dígitos, é de utilização única e expira.
5. Alteração de palavra-passe: a nova palavra-passe funciona, a antiga deixa de funcionar e a sessão fica coerente.
6. Logout e eliminação: sessão encerrada, dados locais limpos e eliminação efectivamente concluída no backend.
7. Redirects: `studiomesa://auth-callback` está autorizado na configuração Auth do projecto.

## Configuração que não pode ser inferida do repositório

A configuração Auth/SMTP do projecto (templates, remetente, URLs autorizados, chaves e estado do fornecedor) não está disponível para inspecção através das ferramentas read-only usadas nesta auditoria. Os logs Auth existentes não substituem o envio e recepção de mensagens de teste.

O Supabase Advisor também assinala `auth_leaked_password_protection` como desactivada. É uma advertência de segurança que deve ser avaliada com o plano/configuração disponíveis antes da venda; não alterar o plano nem assumir custos sem decisão.

Google e Microsoft/Outlook continuam ocultos por decisão do proprietário e não fazem parte do gate da primeira versão.

**Estado:** implementação de código presente; validação real SMTP/Auth E2E pendente. Não definir a variável de prontidão como `YES` antes de todos os cenários acima passarem.


## Incidente observado no OPPO A5m — 21/11/2026

Os logs Auth do projecto alojado confirmaram, sem expor o endereço de email:
- 15:54:12 UTC — pedido `user_recovery_requested` recebido da aplicação Android 16.
- 15:54:13 UTC — evento `mail.send`.
- 15:54:41 UTC — redireccionamento HTTP 303 e evento Auth `login` num browser Firefox Android.
- 15:55:12 UTC — repetição do pedido/link, seguida de `403: Email link is invalid or has expired`.

Os logs demonstram que o email foi enviado e que houve uma primeira validação/entrada; a repetição do link foi recusada. O user-agent da própria requisição de verificação não foi registado, pelo que não é possível determinar se a primeira abertura foi feita pelo utilizador, pela pré-visualização do cliente de email ou por outro componente. A documentação Supabase alerta que mecanismos de pré-carregamento de links em emails podem consumir links de utilização única antes de o utilizador os abrir.

## Correcção recomendada: template de recuperação com OTP

A aplicação já implementa `POST /auth/v1/verify` com `type: recovery` e exige seis algarismos. Para alinhar o servidor com essa implementação, foi adicionado o ficheiro versionado [`supabase/templates/recovery.html`](../supabase/templates/recovery.html), que contém `{{ .Token }}` e não inclui uma ligação de verificação.

No Dashboard do Supabase, em **Authentication → Email Templates → Reset Password**, substituir o corpo da mensagem pelo conteúdo desse ficheiro e usar o assunto:

`Código para recuperar a palavra-passe StudioMesa`

Importante:
- Alterar o template **Reset Password / Recovery**, não o template **Magic Link**.
- Não usar `{{ .ConfirmationURL }}` neste template OTP.
- Guardar e enviar um novo pedido de recuperação. Emails antigos e links já enviados não são reutilizáveis.
- O email novo deve apresentar um código de seis algarismos; introduzi-lo na caixa de recuperação da app e depois definir a nova palavra-passe.
- Confirmar primeiro num endereço de teste/conta descartável. Só depois considerar o fluxo aceite.

A documentação oficial confirma que `{{ .Token }}` é o OTP de seis algarismos e pode ser usado em vez de `{{ .ConfirmationURL }}`. Ver [Supabase — Email Templates](https://supabase.com/docs/guides/auth/auth-email-templates) e [Passwordless email / OTP](https://supabase.com/docs/guides/auth/auth-email-passwordless).

**Estado:** o template correcto está agora versionado na branch da PR. Ainda não foi aplicado ao Dashboard alojado nem foi possível confirmar um email novo com OTP. Até essa alteração e o teste E2E passarem, a recuperação de palavra-passe continua bloqueada para uso comercial.
