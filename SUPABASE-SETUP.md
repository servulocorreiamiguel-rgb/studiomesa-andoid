# Configurar a base de dados partilhada do StudioMesa

Este passo prepara os pontos para poderem ser partilhados entre a aplicação do restaurante e a aplicação do cliente. A aplicação Android **ainda não está ligada** a esta base de dados; depois de criares o projeto, o código Android terá de ser ligado e compilado novamente.

## 1. Criar o projeto

1. Abre https://supabase.com e cria uma conta/projeto novo.
2. Escolhe uma palavra-passe forte para a base de dados e guarda-a num gestor de palavras-passe.
3. Espera que o projeto fique pronto.

## 2. Criar as tabelas e as regras de segurança

1. No projeto, abre **SQL Editor**.
2. Abre o ficheiro `supabase/schema.sql` deste repositório.
3. Copia todo o SQL, cola no SQL Editor e executa-o uma vez.
4. Se aparecer um erro, não continues a configuração: guarda o texto exato do erro para o corrigirmos.

O esquema cria restaurantes, membros de equipa, cartões de fidelização e histórico de movimentos. As tabelas têm Row Level Security (RLS), os utilizadores só podem ler o que lhes diz respeito ou o que a sua função de equipa permite, e apenas uma função validada pode alterar pontos. Revê sempre o Security Advisor antes de usar dados reais.

## 3. Obter apenas os dados públicos da app

1. No painel do Supabase, abre **Connect** ou **Settings → API Keys**.
2. Copia o **Project URL**.
3. Copia a chave **Publishable** que começa por `sb_publishable_` (ou a chave pública `anon` de um projeto antigo).
4. Guarda-os para configurar a aplicação.

**Nunca partilhes nem coloques no Android, GitHub ou nesta conversa a chave `sb_secret_`, `service_role`, a palavra-passe da base de dados ou qualquer chave secreta.** A chave publicável pode estar numa app móvel, mas só é segura com RLS e políticas de acesso bem configuradas.

## 4. O que será ligado no código

Depois de o projeto estar criado, a implementação Android tem de acrescentar:
- registo e início de sessão seguros;
- criação do restaurante e conta de equipa;
- entrada de clientes através do código do restaurante;
- leitura do saldo de pontos real;
- atribuição/desconto de pontos através de função verificada, com histórico;
- tratamento de erros, falta de rede e sessões expiradas.

O fluxo de pontos vai usar transações no servidor; a app não deve poder alterar diretamente o campo de saldo. Os pontos só passam a sincronizar entre dispositivos depois de o código Android estar ligado ao projeto e testado.

## 5. Estado atual

O ficheiro SQL está preparado no repositório, mas **a base de dados ainda não foi criada nem testada num projeto Supabase**. As atuais apps são demonstrações locais. Não registes clientes reais nem prometas sincronização até a ligação e os testes ficarem concluídos.

Repositório: https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid
