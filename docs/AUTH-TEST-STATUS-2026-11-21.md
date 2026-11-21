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
