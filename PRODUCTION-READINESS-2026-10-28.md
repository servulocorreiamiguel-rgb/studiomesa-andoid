# Auditoria de prontidão para produção — 28/10/2026

## Âmbito

Auditoria de leitura ao projeto Supabase alojado, após a integração do gestor económico no StudioMesa. Não foram alteradas tabelas, permissões, migrações, utilizadores ou definições de autenticação durante esta auditoria.

- Estado do projeto: `ACTIVE_HEALTHY`
- Região: `eu-west-1`
- PostgreSQL: `17.6.1.166`
- Plano da organização: **Free**
- Migrações da aplicação registadas no Supabase: **39**
- Última migração registada: `20261026173330 fix_staff_join_concurrent_conflict_target`

## Resultados positivos verificados

- As 11 tabelas no esquema `public` têm Row Level Security (RLS) ativada.
- Cada tabela pública tem pelo menos uma política RLS.
- A auditoria SQL não encontrou funções `SECURITY DEFINER` dos esquemas `public`/`private` executáveis pelo papel `anon`.
- Todas as funções `SECURITY DEFINER` auditadas têm `search_path` vazio, evitando resolução de objetos por um caminho de pesquisa controlado pelo utilizador.
- As tabelas do esquema `private` não têm privilégios diretos de tabela concedidos a `anon` ou `authenticated`.
- A CI do commit integrado confirmou a reprodução das migrações numa base isolada e os testes automatizados de autorização entre restaurantes/idempotência.

## Avisos de segurança que continuam abertos

### 1. Proteção contra palavras-passe comprometidas desativada

O Supabase Security Advisor reportou `auth_leaked_password_protection` como desativada. A organização ligada ao projeto está no plano Free; segundo a [documentação oficial de segurança de palavras-passe](https://supabase.com/docs/guides/auth/password-security), a verificação de palavras-passe comprometidas exige o plano Pro ou superior.

**Ação proposta:** decidir se se atualiza o plano para ativar esta proteção. Não houve atualização de plano nem alteração das definições de Auth nesta auditoria.

### 2. Funções `SECURITY DEFINER` executáveis por utilizadores autenticados

O Security Advisor reportou 19 funções públicas deste tipo executáveis por `authenticated`. Isto **não prova, por si só, uma vulnerabilidade**: a aplicação chama algumas destas RPCs intencionalmente, e as funções têm de confirmar a sessão e o âmbito de acesso.

A inspeção direta confirmou, entre outros controlos, verificações de `auth.uid()`, pertença ao restaurante ou papel de proprietário nas operações de stock, reservas e equipa; a execução por `anon` está revogada. Ainda assim, o aviso exige uma revisão independente de cada função e testes negativos com contas de teste reais, incluindo tentativas de aceder ao restaurante de outra conta. Não revogar permissões às cegas: isso poderia quebrar as operações legítimas.

Remediação futura: avaliar cada função individualmente e, quando for seguro, retirar a execução direta ou movê-la para um esquema não exposto; manter apenas RPCs que a aplicação realmente necessita.

### 3. Tabelas privadas com RLS, mas sem políticas

O Advisor também assinala `private.account_deletion_queue` e `private.restaurant_join_codes` por terem RLS sem políticas. Na verificação efetuada, não tinham privilégios diretos de tabela concedidos a `anon`/`authenticated`; são usadas por código privilegiado e não devem ser expostas como tabelas da API. Confirmar que o esquema `private` não está incluído nos esquemas expostos pelo Data API e manter a ausência de concessões diretas.

## Bloqueios de lançamento que não foram validados por esta auditoria

1. Testar em dispositivos físicos os fluxos de login, confirmação de email, renovação/expiração de sessão, logout, reservas, stock, fidelização, remoção de equipa e eliminação de conta.
2. Executar testes ponta a ponta no projeto alojado com contas descartáveis de proprietário, funcionário e cliente; confirmar isolamento entre dois restaurantes e recusas de permissões.
3. Configurar e testar SMTP e URLs de retorno/autorização de Auth, incluindo recuperação de palavra-passe e o fluxo web de eliminação de conta.
4. Publicar e testar uma política de privacidade coerente com os dados reais, e preencher o formulário Data safety.
5. Escolher os identificadores finais das aplicações, guardar uma chave comercial própria e os segredos em GitHub Actions, gerar cinco AAB assinados e verificar as assinaturas.
6. Decidir como atribuir os planos pagos. Os valores do README são referências comerciais; ainda não existe cobrança ou atribuição automática de planos.
7. Fazer uma revisão de segurança independente, com foco nas RPCs privilegiadas e na exclusão de dados de conta/restaurante.

## Estado de lançamento

**A aplicação ainda não está pronta para publicação comercial.** A CI aprovada confirma compilação, testes unitários, Lint, configuração de release de teste, auditoria/migrações automatizadas e testes de interface nos emuladores Android 8/16. Não confirma os fluxos reais de email/autenticação, um ensaio em telemóvel físico, a configuração da Play Console ou a segurança operacional completa.

Esta auditoria é um ponto de situação técnico, não uma certificação de segurança, jurídica, contabilística ou de conformidade.
