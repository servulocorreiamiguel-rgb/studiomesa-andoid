# StudioMesa — estado de testes e prontidão para lançamento

**Revisão:** 25-10-2026  
**Repositório:** https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid  
**PR de trabalho:** https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/pull/19  
**CI atual:** https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions

O estado do PR e os resultados da execução mais recente no commit atual devem ser confirmados na página de CI. Uma execução bem-sucedida de uma versão anterior não valida automaticamente um commit posterior.

## Validações automatizadas configuradas

- Compilação das cinco variantes APK de demonstração, testes unitários de cada variante e Android Lint.
- Construção das cinco combinações comerciais AAB com uma chave descartável de CI e verificação da assinatura de cada bundle. Estes ficheiros de teste não são uma assinatura comercial nem devem ser publicados.
- Testes de interface nos cinco sabores, em emuladores Android 8 (API 26) e Android 16 (API 36), com relatórios XML publicados como artefactos.
- Reconstrução das migrações SQL numa instância PostgreSQL descartável e testes de estrutura, RLS, RPCs, autorização entre restaurantes, idempotência de criação e ajuste de stock e fluxo de eliminação de conta.
- Verificação estática das funções SECURITY DEFINER e da configuração das Edge Functions; os testes do catálogo SQL confirmam, depois da reconstrução, que as funções privilegiadas em public/private fixam um search_path vazio e não podem ser executadas por anon.
- Verificações unitárias do guardião de configuração para manter a validação da sessão autenticada no endpoint destrutivo e exigir PKCE e deteção do callback na página web de eliminação de conta.

Estes testes não substituem testes ponta a ponta contra Supabase Auth hospedado, um telemóvel Android real ou uma revisão independente de segurança.

## Estado da base de dados e migrações

- A base de dados Supabase ativa regista 37 versões de migração. A branch de trabalho contém 38 ficheiros SQL: as 37 versões já registadas e uma migração corretiva adicional, 20261024110000_fix_join_restaurant_staff_conflict_target.sql.
- A migração corretiva usa o nome da restrição restaurant_staff_pkey no ON CONFLICT, evitando a ambiguidade com o parâmetro de saída restaurant_id da função join_restaurant_staff. A correção já foi incluída na reconstrução descartável da CI, mas ainda **não foi aplicada à base de dados hospedada**.
- Uma consulta apenas de leitura à função atualmente instalada mostrou que a versão hospedada ainda contém a forma antiga ON CONFLICT (restaurant_id, user_id). Não assumir que a correção está ativa até ser aplicada através de um procedimento controlado e verificada no projeto hospedado.
- As 11 tabelas do esquema public têm RLS ativo. As tabelas privadas private.account_deletion_queue e private.restaurant_join_codes também têm RLS ativo, sem políticas diretas; as consultas de catálogo não encontraram privilégios diretos de SELECT/INSERT/UPDATE/DELETE de anon ou authenticated nessas tabelas. São usadas através de funções autorizadas.
- O catálogo PostgreSQL mostra 23 funções SECURITY DEFINER no esquema public; todas as analisadas têm search_path vazio e nenhuma é executável por anon. O Security Advisor assinala 19 que podem ser executadas por authenticated. Isso é um aviso para rever privilégios e testar autorização por operação, não prova isolada de exploração. As verificações já analisadas incluem autenticação e, nas operações relevantes, pertença ao restaurante ou papel de proprietário; os testes com identidades distintas continuam incompletos.
- O Security Advisor assinala também a proteção contra palavras-passe comprometidas desativada. A documentação oficial do Supabase indica que esta opção está disponível no plano Pro e superiores: https://supabase.com/docs/guides/auth/password-security. Confirmar o plano e ativar a proteção nas definições Auth, se disponível, antes de tratar o backend como pronto para clientes.
- O Performance Advisor assinala 21 índices não utilizados. Não foram removidos: a decisão exige carga representativa e verificação de possíveis regressões.
- Não foram feitas alterações DDL ou de dados na base de dados de produção durante esta auditoria.

## Estado da aplicação

O README descreve a aplicação atual como uma demonstração/protótipo, não uma solução certificada para uso comercial diário. Os APKs produzidos pela CI são artefactos de demonstração e são assinados para testes; não constituem versões de lançamento comercial.

A workflow de release comercial exige o identificador definitivo do pacote e os segredos da chave de assinatura configurados no GitHub. A build com chave descartável usada na CI só valida o processo de compilação. Nenhum AAB assinado com a chave comercial foi produzido por esta validação.

## Bloqueios antes de vender ou publicar

1. Confirmar que todos os jobs da CI passam no commit exato que será considerado para lançamento.
2. Planear e aplicar a migração corretiva da função join_restaurant_staff num procedimento controlado, depois confirmar a função instalada no projeto hospedado. Não aplicar migrações automaticamente durante os testes.
3. Testar com contas e restaurantes distintos: leitura/escrita de pedidos, reservas, stock, carta, códigos da equipa e fidelização; incluir sessão ausente, utilizador de outro restaurante, reenvios idempotentes e falhas de rede.
4. Testar no Android real a criação e confirmação de conta, início/fim de sessão, recuperação de palavra-passe, renovação/expiração de sessão e revogação de acesso. Testar também a página web de eliminação de conta com uma ligação PKCE num navegador real.
5. Rever as definições Auth, confirmação de email, SMTP e URLs de retorno; verificar no ambiente hospedado o fluxo de eliminação de conta.
6. Escolher os IDs finais de pacote, configurar os segredos de assinatura comercial e testar os AABs assinados finais.
7. Preparar uma política de privacidade pública, preencher a declaração Data safety da Google Play e definir backup/restauro e resposta a incidentes.
8. Concluir testes operacionais com um restaurante-piloto e uma revisão independente dos fluxos críticos antes de aceitar pagamentos ou depender da aplicação durante o serviço.

**Estado de lançamento: não aprovado para produção.** O PR permanece em rascunho e separado de main; a migração corretiva não foi aplicada à base de dados hospedada e não foi feita publicação comercial.
