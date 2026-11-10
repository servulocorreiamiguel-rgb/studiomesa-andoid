# Início de sessão Google/Microsoft — ativação após a primeira venda

## Decisão para a primeira versão

A primeira versão comercial mantém **criar conta e iniciar sessão por email/palavra-passe**. Os botões Google e Microsoft (Outlook) não são apresentados.

A implementação Android OAuth com PKCE continua no repositório, mas fica inativa por defeito. Não é necessário configurar credenciais sociais para a primeira versão. SMTP próprio, confirmação de email, recuperação de palavra-passe e testes reais do fluxo de email continuam a ser requisitos de lançamento.

## Ativação futura

A interface depende da propriedade Gradle:

```bash
-PstudiomesaSocialLoginButtonsEnabled=true
```

O valor por defeito é `false`. Não ativar até estarem concluídos:
- credenciais próprias de OAuth no Google Cloud e Microsoft Entra;
- ativação dos fornecedores `google` e `azure` no Supabase Auth;
- Redirect URLs autorizados e callback `studiomesa://auth-callback`;
- testes PKCE em Android físico, incluindo cancelamento, erro do fornecedor, retorno à aplicação, troca de conta e sessão expirada;
- confirmação de que o email do fornecedor corresponde à conta autenticada e que o restaurante/conta é restaurado corretamente.

As credenciais e segredos ficam apenas no fornecedor/Supabase, nunca no APK, no GitHub público ou na conversa.

## Testes obrigatórios após ativação

Testar cada fornecedor com contas descartáveis:
1. login bem-sucedido e criação de conta quando permitido;
2. cancelamento no browser e retorno sem sessão;
3. email já associado a outro método de autenticação;
4. erro de consentimento/credenciais/redirect;
5. perda de rede ou app encerrada durante o fluxo;
6. logout, renovação e troca de utilizador;
7. isolamento entre dois restaurantes.

A ativação social não deve alterar o fluxo de email/palavra-passe nem contornar as permissões do Supabase.
