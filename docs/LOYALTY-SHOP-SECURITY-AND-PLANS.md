# Loja de pontos — segurança, fotografias e limites por plano

**Estado:** implementação preparada na branch `feature/loyalty-shop-photos-tier-limits`. Ainda não está integrada na `main`, nem aplicada ao Supabase alojado. Os limites abaixo só passam a funcionar depois de a migration ser aprovada, aplicada e testada.

## Limites propostos

Cada prémio ativo conta como um produto da loja. Desativar um prémio liberta imediatamente um lugar, mas não apaga o histórico dos resgates.

| Plano | Limite de prémios ativos | Fotografias |
|---|---:|---|
| Essencial | 5 | Não incluídas |
| Profissional | 15 | Uma por prémio |
| Premium | 50 | Uma por prémio |

A fotografia é convertida para JPEG no telemóvel, reduzida a um máximo de 1280 px e limitada a 1,5 MB. Cada prémio usa um único objeto Storage no caminho `{restaurant_id}/{reward_id}/photo.jpg`; substituir a imagem atualiza esse mesmo objeto, em vez de acumular ficheiros.

O limite de prémios é separado do stock de cada prémio. O proprietário pode definir uma quantidade ou deixar o stock sem limite. O servidor impede resgates quando o stock definido se esgota.

## O que o cliente pode falsificar?

O cliente não escolhe o saldo final nem decide o custo do prémio. O APK envia o identificador do prémio e um identificador único de tentativa; o Supabase consulta o custo e o saldo reais, bloqueia as linhas necessárias, valida o stock, desconta os pontos e cria o registo de resgate numa única transação.

As escritas diretas nas tabelas de prémios e resgates estão revogadas. As operações passam por RPCs autenticadas. A repetição do mesmo pedido usa uma chave de idempotência, para evitar cobrar os mesmos pontos duas vezes. O cliente não pode chamar a função de entrega para se confirmar a si próprio: essa operação exige uma conta de funcionário/proprietário do restaurante associado.

A atribuição de pontos também não aceita o saldo final enviado pelo telemóvel. O cliente apresenta um QR temporário, válido por dois minutos e de utilização única; o servidor associa-o ao cartão autenticado e só aceita a atribuição por uma conta de funcionário autorizada no restaurante certo.

### Limites da proteção atual

- O QR é um código temporário do tipo bearer. Um cliente pode tentar partilhá-lo ou mostrar uma captura de ecrã durante os dois minutos; o código continua a estar ligado ao cartão que o criou e só pode ser usado uma vez.
- O número de pontos atribuídos é introduzido pelo funcionário. Sem integração com o POS ou leitura de uma fatura de venda, o sistema não comprova automaticamente o valor da compra.
- O resgate reserva os pontos e aumenta o contador de resgates. O restaurante confirma depois a entrega. Ainda não existe um fluxo completo de cancelamento de resgate com devolução automática dos pontos e reposição do stock; um resgate pendente não deve ser tratado como uma venda concluída.
- A entrega fica registada com o funcionário e a hora. A proteção contra abuso por um funcionário autorizado exige também supervisão e auditoria do restaurante.

## Fotografias e Storage

As fotografias da loja são imagens de produtos destinados a clientes, não documentos privados. Por isso, o bucket de imagens é público para leitura, mas as operações de carregamento, substituição e remoção exigem autenticação e autorização do proprietário daquele restaurante.

As políticas de Storage verificam:
- bucket e formato JPEG;
- caminho único formado pelo ID do restaurante e pelo ID do prémio;
- proprietário autenticado associado ao restaurante;
- prémio existente;
- plano Profissional ou Premium para carregar/substituir imagens;
- limite de 1,5 MB definido no bucket e validado novamente ao guardar a referência na base de dados.

O caminho fixo permite uma única imagem por prémio, mesmo que alguém tente chamar a API diretamente. O cliente vê apenas os caminhos das imagens dos prémios ativos; no Essencial o RPC de cliente não devolve imagens.

Não colocar fotografias de clientes, faturas, documentos pessoais ou qualquer conteúdo confidencial neste bucket.

## Atribuição do plano

O plano é mantido em `private.restaurant_loyalty_entitlements`, uma tabela sem acesso direto pela aplicação. Se não existir uma atribuição, o restaurante fica no Essencial. O APK não pode escolher o plano nem aumentar o próprio limite.

A atribuição comercial inicial é feita pelo responsável técnico/administrador através do Supabase SQL Editor, depois de confirmar o contrato. Exemplo para atribuir Profissional:

```sql
insert into private.restaurant_loyalty_entitlements (restaurant_id, plan_code)
values ('<UUID_DO_RESTAURANTE>', 'profissional')
on conflict (restaurant_id)
do update set plan_code = excluded.plan_code;
```

Para Premium, usar `premium`; para Essencial, usar `essencial`. Esta tabela não deve ser exposta ao Android nem receber atualizações de utilizadores. Ainda não existe cobrança automática nem integração com pagamentos.

A mudança para um plano inferior é recusada pelo servidor enquanto o restaurante tiver mais prémios ativos do que o novo limite. O proprietário deve primeiro desativar prémios até cumprir o limite.

## Testes obrigatórios antes de integrar

1. Essencial: criar 5 prémios ativos; a tentativa de criar o sexto deve falhar no servidor, mesmo que seja chamada diretamente à RPC.
2. Profissional: criar 15; o 16.º deve falhar. Premium: criar 50; o 51.º deve falhar.
3. Desativar um prémio e confirmar que é possível criar outro sem apagar o histórico.
4. Tentar ultrapassar o limite a partir de dois dispositivos em simultâneo; o bloqueio por linha do restaurante deve impedir a corrida.
5. Tentar alterar plano, custo, stock, saldo, contador de resgates ou fotografia com uma conta de cliente; tudo deve ser recusado.
6. Tentar carregar, substituir e remover uma fotografia por funcionário, proprietário de outro restaurante, utilizador anónimo e cliente; só o proprietário daquele restaurante deve conseguir.
7. Testar JPEG, PNG e WebP de entrada, transparência, imagens muito grandes, ficheiro falso e imagem superior a 1,5 MB; o Android converte para JPEG e o servidor rejeita ficheiros fora do limite.
8. Resgatar sem pontos, com stock esgotado, repetindo a mesma tentativa e em concorrência; só pode existir um desconto válido.
9. Confirmar que a entrega é registada apenas pelo restaurante correto e que uma segunda confirmação não duplica movimentos.
10. Testar a substituição da fotografia, remoção após downgrade para Essencial, cache Android, falha de rede e reautenticação.

## Decisão comercial

A recomendação é manter os limites em **5 / 15 / 50 prémios ativos**. O Essencial continua útil para uma loja pequena e o Pro/Premium acrescentam variedade e apresentação visual sem tornar o plano base ilimitado. A fotografia deve ser uma funcionalidade Pro/Premium; uma imagem por prémio é suficiente para uma loja de recompensas e mantém o consumo de Storage controlado.
