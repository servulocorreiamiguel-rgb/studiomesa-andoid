# StudioMesa — configuração do backend e preparação para produção

> **Importante:** este documento substitui instruções antigas de configuração. O código e a base de dados evoluíram; não executes o ficheiro `supabase/schema.sql` no projeto ligado à aplicação sem antes comparar o esquema e validar uma migração. Esse ficheiro é um protótipo inicial da parte de fidelização e não representa, por si só, todo o esquema atualmente utilizado pela aplicação.

## Estado técnico

- A aplicação Android é nativa, escrita em Kotlin.
- A aplicação usa o cliente REST/Auth definido em `app/src/main/java/pt/studiomesa/app/SupabaseApi.kt`.
- O cliente contém o URL do projeto e uma chave Supabase publicável (`sb_publishable_...`). Uma chave publicável pode existir numa aplicação móvel; **não** é uma chave secreta.
- Nunca coloques em Kotlin, recursos Android, APKs, GitHub público ou nesta conversa chaves `sb_secret_...`, `service_role`, palavras-passe da base de dados ou credenciais privadas de OAuth.
- O backend tem tabelas de mesas, carta, pedidos, histórico de pedidos, equipa e fidelização. As regras RLS e permissões têm de ser revistas em conjunto com as funções SQL que as aplicações chamam.

## Estado verificado do backend (21/10/2026)

- `private.restaurant_join_codes` tem RLS ativa, sem políticas para acesso direto; as contas `anon` e `authenticated` não têm permissões diretas de leitura/escrita. As funções proprietárias de `postgres` foram mantidas e o utilizador `postgres` tem `BYPASSRLS`, para permitir os fluxos `SECURITY DEFINER` que fazem verificações explícitas.
- Os índices `restaurant_staff_one_restaurant_per_user_idx` e `loyalty_members_one_restaurant_per_user_idx` impõem a regra de uma associação por conta e protegem contra pedidos concorrentes.
- A proteção de palavras-passe comprometidas permanece desativada. O Supabase indica que a funcionalidade está disponível no plano Pro e superiores; esta configuração deve ser revista antes de operar com contas reais.
- As alterações de RLS e índices estão registadas nas migrações `20261021152901_enable_rls_private_restaurant_join_codes.sql` e `20261021153243_enforce_one_restaurant_per_user.sql`. Os fluxos legítimos continuam pendentes de testes ponta a ponta com contas de teste.

## Antes de alterar o esquema

1. Confirma que estás no projeto Supabase correto.
2. Revê o SQL exato numa branch de desenvolvimento, nunca diretamente em produção.
3. Confirma que cada tabela exposta tem RLS e políticas alinhadas com o papel do utilizador e com o restaurante.
4. Revê as funções `SECURITY DEFINER`: devem verificar `auth.uid()`, autorizações e `search_path`, e o direito `EXECUTE` deve ser concedido apenas aos papéis necessários.
5. Não atives RLS numa tabela privada sem compreender os acessos das funções que a utilizam. RLS sem políticas bloqueia o acesso direto à tabela; isso pode ser correto para uma tabela privada, mas deve ser confirmado com testes dos fluxos legítimos.
6. Depois de cada alteração, volta a executar os Security Advisors e testa os fluxos com contas de proprietário, funcionário, cliente e utilizador não autenticado.

## Configuração de autenticação

- Email/palavra-passe: confirma as regras de palavras-passe, a confirmação de email, o fornecedor de envio de emails e o URL de retorno autorizado para recuperação.
- O código Android passou a usar PKCE: o verificador é gerado aleatoriamente, guardado cifrado no Android Keystore e usado para trocar o código de retorno por uma sessão. O callback já não aceita tokens bearer diretamente na ligação.
- Google e Microsoft só devem ser apresentados como métodos operacionais depois de configurares as credenciais OAuth reais no Supabase, autorizares o callback e testares o fluxo completo num telemóvel real.
- Não partilhes palavras-passe entre proprietário e funcionários. Cada pessoa deve ter a sua própria conta.
- Ativa proteção contra palavras-passe comprometidas e revê limites de pedidos e monitorização antes da utilização comercial.

## Cópias de segurança e recuperação

A aplicação não substitui as cópias de segurança do fornecedor da base de dados. Antes de usar dados reais:

- Confirma no painel do Supabase o plano, retenção e frequência de backups efetivamente disponíveis.
- Define quem pode solicitar um restauro e qual o objetivo aceitável de perda de dados.
- Faz um restauro de teste num ambiente separado e confirma mesas, carta, pedidos, histórico e pontos.
- Documenta exportação, eliminação e conservação de dados; não guardes dados de clientes para além do necessário.

Não se considera um backup verificado até existir evidência de um restauro testado.

## Testes obrigatórios antes da venda

- Compilar todas as variantes Android e instalar os APKs num dispositivo real.
- Testar registo, login, renovação e expiração de sessão, recuperação de palavra-passe e logout.
- Testar pedidos e alterações concorrentes em dois dispositivos, incluindo perda de internet durante uma gravação.
- Testar que uma conta de um restaurante não consegue consultar ou modificar dados de outro.
- Testar códigos de cliente/equipa inválidos, tentativas repetidas e renovação de códigos.
- Confirmar que a aplicação de cliente nunca recebe custos internos, saldos de outros clientes ou dados da equipa.
- Confirmar permissões e papéis de funcionário/proprietário, cópias de segurança e o procedimento de recuperação.
- Gerar uma versão de produção assinada com uma chave privada guardada fora do repositório. O workflow atual gera apenas APKs de demonstração/debug.

## Limitações conhecidas

A lista exata de funcionalidades por plano está no `README.md`. A existência de um botão ou de uma tabela não prova que a funcionalidade está concluída. Reservas, gestão granular de funcionários, integrações POS/caixa, pagamentos, faturação, impressoras e recuperação comprovada devem ser considerados por implementar ou validar até existirem testes e evidências específicas.

**Não utilizar esta aplicação para operar um restaurante real nem introduzir dados pessoais reais até a revisão de segurança, os testes funcionais e o restauro de backups estarem concluídos.**
