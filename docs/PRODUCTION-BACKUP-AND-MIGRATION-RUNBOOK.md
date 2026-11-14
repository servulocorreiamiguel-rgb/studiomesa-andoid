# StudioMesa — backup e execução segura de migrações

**Âmbito:** projeto Supabase `svwvkfsyevuyabelrtjk` (região `eu-west-1`).
**Estado em 14/11/2026:** não executar migrações remotas. As PRs #133 e #134 estão integradas; o Supabase Free tem 63 migrações aplicadas e oito pendentes. Não existe ainda um backup completo com restauro ensaiado.

## 1. Porque é obrigatório parar antes de produção

O plano Free não inclui backups automáticos diários nem Point-in-Time Recovery. A Supabase recomenda exportação manual através da CLI. Um comando `supabase db dump` também não é um backup integral de todos os serviços: por omissão, a CLI exclui esquemas geridos como `auth` e `storage`, e o ficheiro SQL não contém os objetos binários guardados no Storage.

Fontes oficiais:
- [Backups Supabase e limitações do plano Free](https://supabase.com/docs/guides/platform/backups)
- [FAQ: backups no Free Plan](https://supabase.com/kb/faqs/platform/free-plan-backups)
- [CLI: supabase db dump](https://supabase.com/docs/reference/cli/supabase-db-dump)
- [CLI: supabase db push e dry-run](https://supabase.com/docs/reference/cli/introduction)

## 2. Exportação manual, sem aplicar alterações

Executar num computador de confiança com Supabase CLI atual, Docker disponível e acesso autorizado ao projeto. Não inserir passwords, tokens ou URLs de ligação no Git, no ChatGPT ou num ficheiro partilhado.

```powershell
supabase login
supabase link --project-ref svwvkfsyevuyabelrtjk

New-Item -ItemType Directory -Force backups

supabase db dump --remote -f backups/schema.sql
supabase db dump --remote --data-only --use-copy -f backups/data.sql
supabase db dump --remote --role-only -f backups/roles.sql

Get-FileHash backups/schema.sql -Algorithm SHA256
Get-FileHash backups/data.sql -Algorithm SHA256
Get-FileHash backups/roles.sql -Algorithm SHA256
```

Guardar os ficheiros e os respetivos hashes fora do repositório, em armazenamento privado controlado pelo responsável pelo restaurante/serviço. Confirmar que os três ficheiros existem, não estão vazios e podem ser lidos.

**Limite importante:** estes dumps não incluem os utilizadores de Supabase Auth nem os ficheiros binários do Storage. Antes de produção, documentar e testar separadamente a exportação/restauro das contas Auth, dos metadados de Storage e de todos os objetos (faturas/fotografias). Não declarar o backup completo enquanto esses elementos não forem recuperáveis.

## 3. Ensaio de restauro

Restaurar os dumps numa instância PostgreSQL/Supabase descartável e isolada, nunca na base de produção. O ensaio tem de demonstrar que:
- o SQL de schema, dados e roles restaura sem erros;
- as tabelas, restrições, índices, funções, triggers e políticas esperadas existem;
- os registos relevantes de dois restaurantes de teste continuam isolados;
- Auth e Storage foram recuperados por procedimento próprio;
- a app consegue iniciar sessão e ler/escrever dados num ambiente de teste.

O replay das migrações feito pela CI numa base PostgreSQL vazia é uma verificação adicional, mas **não substitui** este ensaio de restauro do estado real da produção.

## 4. Pré-visualização das migrações pendentes

Só depois de backup e restauro verificados, voltar a ligar o projeto e executar:

```powershell
supabase migration list --remote
supabase db push --dry-run --remote
```

Na `main` atual são esperadas **oito migrações pendentes**, por esta ordem:

1. `20261030111815_restaurant_recipes_and_order_stock_consumption`
2. `20261102103000_granular_restaurant_staff_permissions`
3. `20261102120000_require_loyalty_permission_for_reward_fulfillment`
4. `20261102121000_enforce_loyalty_permission_for_staff_loyalty_operations`
5. `20261104100000_price_adviser_actor_indexes`
6. `20261107170000_loyalty_shop_photos_and_plan_limits`
7. `20261112120000_restaurant_membership_request_actor_indexes`
8. `20261113114000_supplier_invoice_lines_stock_receipts_and_saft_sync`

A migração SAF-T/receção de mercadoria já faz parte da `main`; não é uma alteração futura nem depende de uma PR por integrar. O número e a ordem acima foram comparados com o histórico remoto em 13/11/2026. Voltar sempre a confirmar o dry-run imediatamente antes do deployment. Não executar uma lista diferente sem reconciliação explícita.

## 5. Revisão humana obrigatória antes do push

As migrações não são apenas índices. Incluem criação de tabelas, triggers, substituição de funções/RPCs, alteração de políticas RLS e operações de remoção/substituição de políticas ou funções antigas. Ler cada SQL pela ordem indicada e comparar o dry-run com a lista acima.

Confirmar:
- backup e restauro verificados, incluindo Auth e Storage;
- nenhuma alteração local não revista;
- alvo do comando é exatamente `svwvkfsyevuyabelrtjk`;
- migrações esperadas e apenas as esperadas;
- CI verde para o commit exato que será implementado;
- plano de rollback/recuperação documentado.

**Não usar `--yes`, `--include-seed` ou `db reset --remote` neste projeto.** A aplicação remota exige uma decisão consciente do responsável; sem confirmação, parar no dry-run. A migração pode ser difícil ou impossível de reverter automaticamente se já tiver alterado dados ou funções utilizadas.

## 6. Smoke tests depois do deployment autorizado

Usar duas contas descartáveis de proprietário em dois restaurantes e uma conta de funcionário. Confirmar:
- login, refresh, logout e recuperação de sessão;
- leituras/escritas apenas no restaurante autorizado;
- permissões de funcionário e proprietário;
- receitas, unidades e consumo de stock, se o plano permitir;
- limites Essencial/Profissional/Premium impostos pelo servidor;
- arquivo de faturas, SAF-T e receção de mercadoria apenas quando a migração #128 estiver integrada;
- repetição idempotente após simular perda de resposta;
- eliminação da conta e limpeza dos dados locais/cloud.

Não utilizar contas de clientes reais durante os testes. Documentar resultados, logs sem dados pessoais e o SHA da versão Android testada.

---

**Gate atual:** a CI local passou, mas ainda não existe backup integral + restauro validado. Por isso, não há autorização técnica para aplicar as migrações ao Supabase de produção nesta fase.
