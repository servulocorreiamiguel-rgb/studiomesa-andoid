# Fecho de migrações e autorização — 10/11/2026

**Estado do código:** PR #109 integrada na `main` pelo commit `e9add398747b8d721ea556881af392ce74d19ff0`.  
**CI da PR #109:** run [42888315833](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/42888315833), quatro jobs em sucesso.  
**CI da main após o merge:** run [42892609272](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/42892609272), em execução na última consulta.  
**Decisão comercial:** NÃO LIBERADA para dados reais/venda operacional.

## Migrações recuperadas

Cinco fontes históricas da branch `feature/invoice-loyalty-qr` foram recuperadas e guardadas no Git com os identificadores presentes no ledger remoto. O replay isolado da CI passou com as cinco fontes e a migration adicional de segurança.

Estado verificado após o merge:
- 67 ficheiros SQL em `main`;
- 63 versões no histórico remoto do Supabase;
- os 63 identificadores remotos estão representados por ficheiros locais;
- nenhuma versão remota ficou sem ficheiro local;
- quatro migrações são apenas locais e continuam sem deployment aprovado.

As quatro migrações exclusivamente locais são:
1. `20261030111815_restaurant_recipes_and_order_stock_consumption.sql` — receitas e consumo automático de ingredientes;
2. `20261102103000_granular_restaurant_staff_permissions.sql` — permissões granulares por funcionário;
3. `20261102120000_require_loyalty_permission_for_reward_fulfillment.sql` — exige permissão `loyalty` para confirmar entrega de recompensas; depende da migração de permissões granulares;
4. `20261104100000_price_adviser_actor_indexes.sql` — índices de auditoria do consultor de preços.

A interface do Supabase apresenta versões e nomes das migrações, não o conteúdo SQL histórico remoto. Por isso, os ficheiros recuperados foram confirmados através da presença dos objetos esperados e do replay integral numa base de dados isolada, mas não podem ser declarados idênticos byte a byte ao SQL originalmente aplicado no alojamento.

## Correção de autorização descoberta pela CI

O replay e o contrato de autorização detetaram que a confirmação de entrega de um resgate de fidelização verificava apenas se o utilizador pertencia à equipa, não se ainda tinha a permissão granular `loyalty`.

A migration `20261102120000_require_loyalty_permission_for_reward_fulfillment.sql` altera a função interna para exigir `private.has_restaurant_permission(restaurant_id, 'loyalty')`.

O teste de regressão confirma:
- um funcionário de outro restaurante não pode confirmar o resgate;
- um funcionário autorizado confirma quando tem `loyalty`;
- depois de o proprietário retirar `loyalty`, o mesmo funcionário é bloqueado;
- depois de a permissão ser restaurada, consegue confirmar;
- a repetição da confirmação não duplica a operação.

**Limite de produção:** a migração de permissões granulares continua apenas local e a tabela `private.restaurant_staff_permissions` não existe no Supabase alojado. A correção depende dessa migração e, portanto, ainda não está ativa na base alojada. Não ativar a interface de permissões granulares nem promover esta proteção como estando já aplicada em produção.

## Validação executada

A CI da PR #109 passou:
- replay das 67 migrações numa base PostgreSQL descartável;
- verificação de esquema, RLS e RPCs;
- testes de autorização entre restaurantes, idempotência e matriz de permissões;
- compilação Android e testes unitários;
- Android Lint;
- smoke tests Android 8 (API 26) e Android 16 (API 36).

A CI de `main` após o merge tem execução própria e deve terminar com quatro jobs verdes antes de gerar novo pacote.

## Estado do Supabase alojado

Nenhuma migration foi aplicada nesta ronda. A consulta mais recente manteve:
- projeto `ACTIVE_HEALTHY`, PostgreSQL 17.6.1.166, região eu-west-1;
- 63 versões remotas;
- 26 tabelas listadas em `public` e `private`, todas com RLS ativo;
- as três tabelas `restaurant_recipes`, `restaurant_recipe_ingredients` e `private.restaurant_staff_permissions` continuam ausentes;
- 11 tabelas assinaladas com RLS sem políticas não concedem privilégios diretos de cliente;
- 47 funções `SECURITY DEFINER`, 42 executáveis por `authenticated` e nenhuma por `anon`; todas as 47 referenciam `auth.uid()` e têm `search_path = ''`.

Não foram executados `db push`, `migration repair`, reset, restore nem alterações de schema. Também não foram alterados utilizadores, dados, permissões, segredos ou planos.

## O que falta antes de qualquer deployment

1. Comparar o esquema completo do replay com o Supabase alojado, função a função e índice a índice.
2. Fazer backup e comprovar restauro num projeto separado.
3. Decidir e aprovar separadamente as quatro migrações locais ainda não aplicadas; não as enviar em bloco.
4. Testar com contas descartáveis de dois restaurantes, incluindo proprietário, funcionário com permissões alteradas e cliente.
5. Só depois planear uma janela de deployment e validar novamente RLS, grants, RPCs e Storage.

## Restantes bloqueios comerciais independentes das migrações

- SMTP próprio e confirmação/recovery de email ainda não testados.
- Testes E2E com duas contas reais e dois dispositivos ainda pendentes.
- Testes físicos no OPPO A5m e noutros aparelhos ainda pendentes.
- IA cloud GPT-OSS permanece desativada; pesquisa Web depende de SearXNG configurado.
- Package IDs definitivos e keystore comercial não configurados; APKs atuais são debug/demo.
- Política de privacidade, termos, DPA, retenção, Data Safety, SBOM e confirmação de licenças continuam pendentes.
- POS, pagamentos, faturação, SAF-T, contabilidade e impressoras não estão integrados.

**Conclusão:** a sequência de migrações pode agora ser reproduzida na CI a partir de uma base vazia e foi corrigida uma falha concreta de autorização. Isto melhora a integridade do repositório, mas não torna a aplicação pronta para operação comercial nem autoriza alterações ao Supabase alojado.
