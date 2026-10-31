# Fase seguinte — ligar fichas técnicas, carta, pedidos e inventário

**Estado em 30/10/2026:** a implementação candidata está na PR #72 (`feat/order-menu-snapshots-2026-10-30`) e foi reproduzida com sucesso numa base PostgreSQL descartável pela CI #857. **Ainda não foi aplicada ao Supabase de produção nem validada ponta a ponta em telemóveis reais.**
**Base de trabalho:** PR #72, commit de código `6707d530bd6c6cf6ff0222e173b396c7b5168cc7`.
**Princípio:** nenhuma quantidade é descontada sem associação estável, receita válida, validação de unidade e uma transação idempotente no servidor.

## Estado real da implementação candidata (PR #72)

- **Fase A — implementada no candidato:** pedidos novos guardam o UUID do artigo e o servidor cria snapshots de nome, preço e custo; pedidos antigos continuam sem associação retroativa. Os snapshots são imutáveis.
- **Fase B — parcialmente implementada:** existem `restaurant_recipes` e `restaurant_recipe_ingredients`, com quantidade por dose, desperdício, unidade fotografada e isolamento por restaurante. Cada artigo tem uma ficha actual, não uma tabela de versões. Alterações podem exigir nova aprovação, mas **não existe ainda histórico formal de revisões de receita** nem fotografia da composição completa da receita no momento de cada consumo.
- **Fase C — consumo transaccional implementado no candidato:** ao passar um pedido de `Aberto` para `Em preparação`, o trigger desconta os ingredientes e cria movimentos ligados ao pedido. Uma chave única por pedido/ingrediente evita repetir o mesmo consumo; falta de stock reverte toda a operação. A activação é feita por receita e só o proprietário a pode aprovar; começa desligada.
- **Histórico:** os movimentos registam custo e unidade no momento do movimento. Não existe ainda uma tabela separada com linhas completas de consumo por pedido/revisão da receita. Registos anteriores à migração não têm unidade histórica recuperável e são apresentados como desconhecidos.
- **Limites ainda em aberto:** não há conversão automática de unidades, nem garantia de histórico versionado da ficha técnica. Os testes CI passaram na base descartável, mas continuam pendentes os testes E2E com dois Android físicos, falhas de rede e contas reais descartáveis, além da migração de produção com backup e reversão testados.

As secções seguintes preservam o desenho e os critérios originais da fase. Onde o desenho propõe versões formais de receitas ou uma tabela de consumo por linha, esses pontos são **trabalho futuro**, não funcionalidades concluídas da PR #72.

## 1. Problema confirmado no modelo atual

- `menu_items` já tem um UUID estável por artigo, além do nome, preço e custo interno.
- `restaurant_orders` guarda `item_name` e `amount`, mas não guarda o UUID do artigo da carta nem o custo interno na data do pedido.
- O formulário de novo pedido escolhe um artigo da carta, mas envia apenas o nome, quantidade, valor e outros campos.
- Os movimentos do inventário já têm idempotência e, na fase atual, podem guardar o custo unitário em vigor no momento do movimento.
- As unidades do inventário são texto livre. Não é seguro converter automaticamente `kg` em `g`, `l` em `ml` ou `caixa` em `unidades` sem regras explícitas.

Consequência: ligar receitas diretamente ao nome do prato seria frágil. Renomear um prato ou alterar a carta poderia associar um pedido antigo à receita errada.

## 2. Ordem de implementação

### Fase A — identidade estável e fotografia económica do pedido

1. Adicionar a `restaurant_orders` um `menu_item_id UUID NULL`, com relação composta que garanta que o artigo pertence ao mesmo restaurante do pedido.
2. Quando o artigo da carta for eliminado, manter o pedido e os seus campos de fotografia; o identificador pode ficar nulo. Arquivar um artigo não deve apagar pedidos nem receitas históricas.
3. Guardar um campo de custo unitário no pedido, anulável quando desconhecido. O preço já registado em `amount` mantém a semântica atual de preço por unidade.
4. Atualizar o formulário para enviar o UUID, nome, preço e custo atuais do artigo selecionado.
5. No servidor, verificar que o UUID pertence ao restaurante e que o artigo está ativo na criação. O trigger `private.prepare_restaurant_order()` executa como SECURITY INVOKER, bloqueia a linha do artigo com `FOR SHARE`, consulta sob RLS e substitui nome, preço e custo enviados pelo cliente pelos valores actuais lidos no PostgreSQL. O mesmo trigger impede alterações posteriores às fotografias de nome/preço/custo e só permite desligar o UUID ao eliminar o artigo. Os valores fotografados não dependem de futuras alterações à carta.
6. Os pedidos antigos mantêm `menu_item_id = NULL`; nunca tentar associá-los retroativamente só pelo nome.

**Critérios de aceitação:** pedido novo ligado ao artigo certo; nome/preço/custo preservados depois de editar a carta; pedido antigo continua visível; UUID de artigo de outro restaurante recusado; artigos arquivados não podem ser usados para pedidos novos.

### Fase B — fichas técnicas

Modelo previsto:
- Uma receita pertence a um `menu_item_id` e ao mesmo `restaurant_id`.
- Cada ingrediente identifica um `inventory_item_id`, quantidade por dose, percentagem opcional de desperdício e unidade explicitamente confirmada.
- As relações compostas impedem associar um prato ou ingrediente de outro restaurante.
- A unidade da receita tem de coincidir com a unidade atual do artigo de stock nesta primeira versão. Mudança de unidade invalida a receita para consumo até ser revista; não existe conversão silenciosa.
- Quantidades e desperdício usam precisão e limites validados no cliente e no servidor.
- O proprietário pode rever o custo por dose calculado a partir dos custos de compra unitários conhecidos. Ingredientes sem custo mantêm o custo da receita incompleto, em vez de serem tratados como custo zero.
- Alterar uma receita cria uma revisão identificável. A análise de um pedido já preparado usa a revisão efetivamente registada na altura do consumo, não a receita atual.

**Critérios de aceitação:** não pode existir receita com artigo de stock de outro restaurante, unidade incompatível, quantidade zero/negativa, percentagem fora de intervalo ou ingrediente duplicado sem consolidação explícita.

### Fase C — consumo real de ingredientes

1. O consumo ocorre numa única operação transacional no servidor, ao iniciar a preparação (`Em preparação`), e nunca ao criar o pedido.
2. Uma opção do proprietário ativa/desativa o consumo automático. Por defeito começa desligada até as fichas técnicas estarem revistas.
3. Quando ligada, um pedido associado a prato sem ficha técnica válida não pode avançar silenciosamente para preparação: a interface explica o motivo e permite ao responsável corrigir a ficha ou usar um fluxo manual autorizado.
4. A operação bloqueia o pedido, valida o restaurante e a revisão da receita, bloqueia as linhas de stock por ordem determinística de UUID, valida que todas as quantidades estão disponíveis e só depois altera stock, cria movimentos e muda o estado do pedido na mesma transação.
5. A chave única da operação de consumo por pedido impede que repetição HTTP, toque duplo ou retoma da rede descontem ingredientes duas vezes. A chave não deve ser partilhada entre ingredientes de um modo que viole a unicidade atual dos movimentos.
6. Cada movimento de saída referencia o pedido e guarda o custo unitário fotografado pelo trigger. Uma tabela de consumo liga pedido, ingrediente, quantidade, unidade e revisão da receita.
7. Se um ingrediente não tiver stock suficiente, toda a transação falha: não se descontam parcialmente os restantes ingredientes e o pedido não muda para preparação.
8. Cancelar um pedido depois de começar a preparação não repõe stock automaticamente. O consumo pode já ter acontecido; qualquer correção é um movimento de stock explícito com motivo e auditoria.
9. Pedidos antigos sem `menu_item_id` continuam no fluxo atual sem consumo automático.
10. Edições à receita depois do consumo não alteram movimentos passados.

**Critérios de aceitação:** consumo único em transações concorrentes; nenhum saldo negativo; rollback total se faltar um ingrediente; repetição idempotente; tentativa entre restaurantes rejeitada; cancelamento não repõe stock ficticiamente; custos históricos continuam estáveis.

## 3. Segurança e autorização

- RLS nas novas tabelas e privilégios de Data API explicitamente declarados.
- As RPCs de escrita têm `SECURITY DEFINER` apenas quando necessário, `SET search_path = ''`, verificação de `auth.uid()`, associação de equipa e âmbito do restaurante.
- Apenas o proprietário pode alterar a opção global de consumo automático; a equipa pode criar pedidos e iniciar preparação conforme os papéis definidos.
- O servidor valida novamente todos os UUIDs, estados, quantidades, unidades e versões; não confia nos preços ou custos calculados no Android.
- Não disponibilizar custo interno nem dados de inventário na RPC de menu do cliente.
- Testes negativos com dois restaurantes, proprietário, funcionário e cliente; verificar também os papéis efetivos e os privilégios diretos.

## 4. Não fazer nesta fase

- Não descontar stock por comparação de nomes de pratos.
- Não inferir ingredientes ou quantidades com IA.
- Não converter unidades por aproximação.
- Não repor stock automaticamente num cancelamento depois de preparação.
- Não reescrever custos, quantidades ou movimentos antigos.
- Não atualizar automaticamente o custo comercial da carta sem aprovação do proprietário.
- Não aplicar migrações de fichas técnicas à base de dados de produção antes de replay completo, testes de autorização, backup/restauro e validação ponta a ponta.

## 5. Testes obrigatórios antes de ativar consumo automático

| Cenário | Resultado obrigatório |
|---|---|
| Pedido novo seleciona um artigo existente | Guarda UUID e fotografia de nome/preço/custo |
| Nome ou preço do artigo é alterado depois | Pedido antigo mantém fotografia original |
| Artigo é arquivado | Pedidos antigos continuam legíveis; novos pedidos não o selecionam |
| Artigo de outro restaurante | Escrita recusada |
| Pedido antigo sem UUID | Não consome stock automaticamente |
| Receita válida com unidades compatíveis | Calcula quantidade total por dose e quantidade pedida |
| Unidade de inventário muda | Receita fica bloqueada até revisão |
| Stock insuficiente num ingrediente | Nenhum ingrediente é descontado; pedido não muda de estado |
| Duplo toque/retry de rede | Um único consumo por pedido |
| Dois pedidos concorrentes para o mesmo stock | Sem saldos negativos nem movimentos duplicados |
| Cancelamento após preparação | Não há reposição automática |
| Receita editada após consumo | Histórico do consumo antigo permanece igual |
| Custo unitário desconhecido | Custo histórico permanece desconhecido; não inventar preço |

## 6. Critério de saída da fase

Só ativar a funcionalidade por restaurante depois de todos os testes de unidade, PostgreSQL, RLS, concorrência e Android passarem, e de o proprietário confirmar as fichas técnicas. Até lá, o inventário continua manual e o assistente apresenta apenas estimativas com os custos conhecidos.
