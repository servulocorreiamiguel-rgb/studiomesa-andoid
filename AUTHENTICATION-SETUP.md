# StudioMesa — configuração de autenticação

Este documento descreve os passos necessários para activar autenticação de produção no projecto Supabase `svwvkfsyevuyabelrtjk`.

## Estado verificado em 8 de novembro de 2026

- Email/password: activo.
- Confirmação obrigatória de email: desactivada (`mailer_autoconfirm=true`).
- Google OAuth: desactivado.
- Microsoft/Azure OAuth: desactivado.
- Autenticação por telefone: desactivada.
- A app já suporta email/password, recuperação por ligação PKCE, troca de código PKCE e alteração de palavra-passe após recuperação.
- Nesta branch, a app também consegue validar um OTP de recuperação por email e só mostra os botões Google/Microsoft quando o Supabase os anuncia como activos.

## 1. Antes de exigir confirmação por email

**Não activar a confirmação obrigatória antes de configurar SMTP personalizado e testar a entrega.**

O SMTP predefinido do Supabase é apenas para desenvolvimento: permite enviar para endereços autorizados da equipa, tem limites muito baixos e não é apropriado para clientes reais.

1. Escolher um fornecedor SMTP (por exemplo, Resend, Brevo, Postmark, Amazon SES ou SendGrid).
2. Configurar o domínio remetente e autenticação DNS (SPF/DKIM; DMARC recomendado).
3. Introduzir host, porta, utilizador, palavra-passe e endereço remetente em Supabase → Authentication → SMTP Settings.
4. Enviar e receber mensagens reais de teste, incluindo caixas Gmail e Outlook e uma verificação da pasta Spam.
5. Só depois, em Supabase → Authentication → Providers → Email, definir **Confirm email** como activo (equivalente a `mailer_autoconfirm=false`).

A aplicação continua a usar ligações de confirmação PKCE para a criação de conta. O URL de retorno `studiomesa://auth-callback` deve constar na lista de URLs de redireccionamento permitidos.

## 2. Recuperação por código de email

A app desta branch envia o pedido de recuperação ao endpoint Auth e tem um ecrã para validar um código de seis algarismos (`type=recovery`). Para o servidor enviar esse código, em Supabase → Authentication → Email Templates → Reset Password, o template deve conter `{{ .Token }}`, em vez de depender apenas de `{{ .ConfirmationURL }}`.

O fluxo de recuperação por ligação PKCE continua disponível. O código é de utilização única e o servidor valida-o; a app nunca gera nem valida códigos localmente.

## 3. Google e Microsoft (Outlook)

Os botões são apresentados automaticamente apenas quando o respectivo fornecedor estiver activo no endpoint público de configurações Auth.

### Google
1. Criar um OAuth Client ID no Google Cloud Console.
2. Configurar o tipo de aplicação e os domínios/URIs exigidos pelo Google.
3. Em Supabase → Authentication → Providers → Google, inserir Client ID e Client Secret e activar o fornecedor.
4. No Google Cloud Console, registar como redirect URI do Supabase: `https://svwvkfsyevuyabelrtjk.supabase.co/auth/v1/callback`.
5. Testar uma conta nova e uma conta já existente com o mesmo email confirmado, verificando se a identidade é ligada à conta correcta.

### Microsoft / Outlook
1. Registar a aplicação no Microsoft Entra ID.
2. Escolher os tipos de conta Microsoft que serão aceites (contas pessoais, organizacionais ou ambas).
3. Registar como redirect URI do Supabase: `https://svwvkfsyevuyabelrtjk.supabase.co/auth/v1/callback`.
4. Em Supabase → Authentication → Providers → Azure, inserir Client ID/Secret, activar o fornecedor e configurar os scopes recomendados pela documentação Supabase.
5. Testar com uma conta pessoal Outlook.com e, se necessário, com uma conta Microsoft 365 de organização.

O OAuth Google/Microsoft não cobra por cada início de sessão, embora possam aplicar-se limites ou custos do plano Supabase à medida que a aplicação cresce. As credenciais OAuth são criadas nas consolas dos fornecedores e guardadas apenas no servidor.

## 4. Recuperação por telefone / WhatsApp — configuração futura

Supabase suporta OTP por SMS e WhatsApp, mas o canal WhatsApp só é suportado com Twilio ou Twilio Verify.

Antes de oferecer recuperação por telefone na app:
1. Implementar no ecrã Conta a associação de um número ao utilizador já autenticado.
2. Exigir confirmação desse número através de OTP antes de o considerar um método de recuperação.
3. Activar Phone Auth no Supabase e configurar Twilio/Twilio Verify e o remetente WhatsApp aprovado.
4. Implementar o pedido OTP com criação automática de utilizador desactivada, para nunca criar uma segunda conta durante a recuperação.
5. Depois de validar o OTP do número previamente associado, permitir definir a nova palavra-passe e terminar/revogar sessões adequadamente.
6. Aplicar limites por número/IP e protecção contra abuso.

**Esta branch ainda não inclui a associação/validação de telefone nem a recuperação via WhatsApp.** Não mostrar essa opção na app até todo o fluxo estar implementado e testado.

Twilio e Meta cobram por utilização no canal WhatsApp/SMS; confirmar os preços actuais antes de activar.

## 5. Testes de aceitação antes de produção

- Registo com email novo: recebe confirmação e não inicia sessão antes de confirmar.
- Email inválido e email já registado: mensagens claras, sem expor informação sensível.
- Confirmação de email: ligação PKCE abre a app e cria sessão.
- Recuperação por ligação e por OTP: ambas criam sessão de recuperação e permitem mudar a palavra-passe.
- Código errado, expirado ou reutilizado: rejeitado sem alterar a palavra-passe.
- Google e Microsoft: apenas aparecem quando activados e ambos devolvem ao StudioMesa via PKCE.
- Identidades Google/Microsoft com email já usado: verificar que não criam contas duplicadas nem perdem a associação ao restaurante.
- Isolamento: utilizadores e restaurantes continuam separados pelas políticas RLS já existentes.
- Nunca colocar service-role keys, OAuth client secrets, Twilio tokens ou SMTP passwords na aplicação Android.
