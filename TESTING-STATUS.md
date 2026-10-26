# StudioMesa — estado de testes e prontidão para lançamento

**Revisão:** 26-10-2026  
**Repositório:** https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid  
**PR de base:** https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/pull/19  
**Ramo IA:** `feature/restaurant-ai-assistant-2026-10-25` (implementação ainda em validação)  
**CI atual:** https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions

**Execuções CI verificadas em 26-10-2026:** PR #19 passou na run [40244814063](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/40244814063), commit `1b766f8b8037fbc40baca626d7eae38878312b92`. PR #21 passou na run [40245851885](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/40245851885), commit `f45a63f5a58963d04594b771293fc828e6447a65`. Em ambos passaram os quatro jobs visíveis: replay das migrações PostgreSQL, compilação/testes Android, smoke tests em Android 8 e smoke tests em Android 16. Estes resultados só validam os commits indicados; cada commit posterior precisa da sua própria CI verde.

## Validações automatizadas configuradas

- Compilação das cinco variantes APK de demonstração, testes unitários de cada variante e Android Lint.
- Construção das cinco combinações comerciais AAB com uma chave descartável de CI e verificação da assinatura de cada bundle. Estes ficheiros de teste não são uma assinatura comercial nem devem ser publicados.
- Testes de interface nos cinco sabores, em emuladores Android 8 (API 26) e Android 16 (API 36), com relatórios XML publicados como artefactos.
- Reconstrução das migrações SQL numa instância PostgreSQL descartável e testes de estrutura, RLS, RPCs, autorização entre restaurantes, idempotência de criação e ajuste de stock, fluxo de eliminação de conta e quota diária de IA por restaurante.
- Verificação estática das funções SECURITY DEFINER e da configuração das Edge Functions; os testes do catálogo SQL confirmam, depois da reconstrução, que as funções privilegiadas em public/private fixam um search_path vazio e não podem ser executadas por anon.
- Verificações unitárias do guardião de configuração para manter a validação da sessão autenticada no endpoint destrutivo e exigir PKCE e deteção do callback na página web de eliminação de conta.
- Guardião estático do assistente de IA: exige JWT, função de servidor, chave fora do APK, autorização de proprietário, quota, limites de entrada/saída e revisão humana.

Estes testes não substituem testes ponta a ponta contra Supabase Auth hospedado, um telemóvel Android real ou uma revisão independente de segurança.

## Estado da base de dados e migrações

- A base de dados Supabase hospedada regista 38 migrações já aplicadas. O ramo de IA inclui essas versões e acrescenta `20261025230000_restaurant_ai_quota.sql`, para um total de 39 ficheiros de migração no ramo. A migração da quota de IA **não foi aplicada ao projeto hospedado**.
- A migração corretiva usa o nome da restrição restaurant_staff_pkey no ON CONFLICT, evitando a ambiguidade com o parâmetro de saída restaurant_id da função join_restaurant_staff. Foi aplicada ao projeto Supabase hospedado e verificada depois da aplicação: a definição instalada contém ON CONFLICT ON CONSTRAINT restaurant_staff_pkey DO NOTHING.
- A consulta de verificação confirmou que a versão hospedada já não contém a forma antiga ON CONFLICT (restaurant_id, user_id). Os privilégios continuam limitados a authenticated, postgres e service_role; PUBLIC e anon não têm EXECUTE.
- As 11 tabelas do esquema public têm RLS ativo. As tabelas privadas private.account_deletion_queue e private.restaurant_join_codes também têm RLS ativo, sem políticas diretas; as consultas de catálogo não encontraram privilégios diretos de SELECT/INSERT/UPDATE/DELETE de anon ou authenticated nessas tabelas. São usadas através de funções autorizadas.
- O catálogo PostgreSQL mostra 23 funções SECURITY DEFINER no esquema public; todas as analisadas têm search_path vazio e nenhuma é executável por anon. O Security Advisor assinala 19 que podem ser executadas por authenticated. Isso é um aviso para rever privilégios e testar autorização por operação, não prova isolada de exploração. As verificações já analisadas incluem autenticação e, nas operações relevantes, pertença ao restaurante ou papel de proprietário; os testes com identidades distintas continuam incompletos.
- O Security Advisor assinala também a proteção contra palavras-passe comprometidas desativada. A documentação oficial do Supabase indica que esta opção está disponível no plano Pro e superiores: https://supabase.com/docs/guides/auth/password-security. Confirmar o plano e ativar a proteção nas definições Auth, se disponível, antes de tratar o backend como pronto para clientes.
- O Performance Advisor assinala 21 índices não utilizados. Não foram removidos: a decisão exige carga representativa e verificação de possíveis regressões.
- Foi aplicada apenas a migração corretiva `20261025225458_fix_join_restaurant_staff_conflict_target.sql` anteriormente descrita; não foram realizadas alterações a dados das tabelas de negócio. A migração de quota IA é apenas código versionado e precisa de CI verde antes de qualquer aplicação ao ambiente hospedado.

## Assistente de conteúdo com IA — implementação em validação

- A interface está limitada ao plano Premium de gestão de restaurante; as versões cliente não expõem a navegação de gestão.
- O APK não contém a chave do fornecedor. A função `restaurant-ai-assistant` exige sessão JWT, confirma o papel de proprietário, recusa o uso sem `STUDIOMESA_AI_ENABLED=true` e `OPENAI_API_KEY` no servidor, e aplica limite diário de 10 pedidos por restaurante.
- A função aceita apenas tipo de conteúdo, assunto e factos fornecidos pelo restaurante; limita os tamanhos, rejeita emails e padrões de telefone portugueses, não consulta registos de clientes e só devolve rascunhos sujeitos a revisão. Não existe publicação automática.
- **Limitação de privacidade a manter visível:** os filtros detetam padrões comuns de email e telefone português, mas não conseguem garantir a deteção de todos os dados pessoais (por exemplo, nomes ou moradas escritos livremente). A interface avisa para não introduzir dados pessoais/confidenciais, mas isto não substitui testes de privacidade nem justifica enviar dados de clientes. A IA permanece desativada até à revisão final.
- Na run #397, no commit `f45a63f5a58963d04594b771293fc828e6447a65`, passaram o replay das migrações PostgreSQL, a compilação debug APK (incluindo verificações automatizadas do projeto), o smoke test Android 8 (API 26) e o smoke test Android 16. A funcionalidade continua desativada no Supabase hospedado: a Edge Function e a migração de quota não foram implantadas, e ainda faltam testes E2E com Auth hospedado e dispositivos reais.

## Estado da aplicação

O README descreve a aplicação atual como uma demonstração/protótipo, não uma solução certificada para uso comercial diário. Os APKs produzidos pela CI são artefactos de demonstração e são assinados para testes; não constituem versões de lançamento comercial.

A workflow de release comercial exige o identificador definitivo do pacote e os segredos da chave de assinatura configurados no GitHub. A build com chave descartável usada na CI só valida o processo de compilação. Nenhum AAB assinado com a chave comercial foi produzido por esta validação.

## Bloqueios antes de vender ou publicar

1. Confirmar que todos os jobs da CI passam no commit exato que será considerado para lançamento.
2. Testar a função join_restaurant_staff com duas contas de teste distintas, incluindo adesão válida, código inválido, repetição idempotente e tentativa de aderir a outro restaurante; manter a migração já aplicada alinhada com o histórico do repositório.
3. Testar com contas e restaurantes distintos: leitura/escrita de pedidos, reservas, stock, carta, códigos da equipa e fidelização; incluir sessão ausente, utilizador de outro restaurante, reenvios idempotentes e falhas de rede.
4. Testar no Android real a criação e confirmação de conta, início/fim de sessão, recuperação de palavra-passe, renovação/expiração de sessão e revogação de acesso. Testar também a página web de eliminação de conta com uma ligação PKCE num navegador real.
5. Rever as definições Auth, confirmação de email, SMTP e URLs de retorno; verificar no ambiente hospedado o fluxo de eliminação de conta.
6. Escolher os IDs finais de pacote, configurar os segredos de assinatura comercial e testar os AABs assinados finais.
7. Preparar uma política de privacidade pública, preencher a declaração Data safety da Google Play e definir backup/restauro e resposta a incidentes.
8. Concluir testes operacionais com um restaurante-piloto e uma revisão independente dos fluxos críticos antes de aceitar pagamentos ou depender da aplicação durante o serviço.

**Estado de lançamento: não aprovado para produção.** A implementação de IA permanece num ramo separado e em validação; a migração de quota ainda não foi aplicada ao Supabase hospedado e não foi feita publicação comercial.
