# Prompt de implementação — Marketing e Fidelização StudioMesa

## Objectivo

Implementar no StudioMesa um módulo completo de **Marketing e Fidelização**, ligado à base de dados Supabase e às duas aplicações Android (Restaurante e Cliente). Não criar uma demonstração isolada nem guardar campanhas apenas no telemóvel. A informação tem de sincronizar entre dispositivos e ser validada no servidor.

Trabalhar sobre a arquitectura, os componentes visuais, os métodos de autenticação, as permissões e as convenções já existentes. Preservar as funcionalidades actuais de pontos, QR de utilização única, recompensas, reservas, stock, assistentes e contas. Não remover nem substituir o sistema de fidelização existente.

## 1. Gestão de campanhas na aplicação do restaurante

Criar uma secção de navegação **Marketing** (ou integrar claramente em Pontos, caso isso seja mais coerente com a navegação actual). Disponível ao proprietário e, futuramente, a funcionários a quem seja atribuída a permissão específica de marketing.

O restaurante deve conseguir, sem intervenção do StudioMesa:

- Criar, editar, duplicar, activar, pausar, terminar e eliminar rascunhos de campanhas.
- Escolher a data e hora exactas de início e de fim. Deve poder preparar campanhas para o futuro e agendá-las.
- Alterar uma campanha agendada antes de começar e pausar/terminar campanhas activas.
- Ver o estado: rascunho, agendada, activa, pausada ou terminada.
- Definir valor mínimo de compra, limite total de utilizações, limite por cliente, produtos/categorias elegíveis, dias/horas aplicáveis e se acumula com outras campanhas.
- Escolher o público: todos os clientes com consentimento de marketing, clientes novos, clientes frequentes, clientes inactivos há determinado número de dias ou clientes dentro de um intervalo de pontos.
- Consultar utilizações, recompensas emitidas, custo de cashback/descontos e resultados da campanha.

Guardar datas como timestamps com fuso horário. A apresentação deve usar o fuso horário configurado para o restaurante. Não activar campanhas fora do intervalo definido.

## 2. Tipos de promoção escolhidos pelo restaurante

Ao criar/editar uma campanha, o restaurante escolhe exactamente o tipo de benefício e define os respectivos valores e regras:

1. **Pontos extra** — por exemplo, pontos em dobro, triplo ou um número adicional de pontos por compra.
2. **Cashback** — por percentagem ou valor fixo, creditado na carteira de recompensas do cliente depois de uma compra elegível. Exemplo: gastar 50 € e receber 20 € de saldo de recompensa.
3. **Desconto** — percentagem ou montante fixo numa refeição, produto ou categoria. Exemplo: 20% numa refeição elegível.
4. **Vale / voucher** — por exemplo, pagar 30 € e receber um vale de 35 € para uma compra futura. O vale deve ter valor, validade, condições e código/identificador único.
5. **Oferta** — por exemplo, uma sobremesa ou produto definido pelo restaurante sem custo adicional, com condições e stock/limite de utilizações.

Não confundir cashback com desconto imediato nem com dinheiro levantável. Cashback e vales são saldos/créditos de utilização futura, salvo se existir uma integração de pagamentos que suporte expressamente outra coisa. Mostrar sempre as condições ao cliente e ao funcionário antes de aplicar a recompensa.

A aplicação deve calcular o benefício no servidor, nunca confiar no valor calculado apenas pelo telemóvel. Usar valores monetários decimais exactos (numeric/BigDecimal), não Float/Double para dinheiro.

## 3. Pontos e prazo de validade decidido pelo restaurante

Adicionar definições de fidelização onde o proprietário escolhe o prazo normal dos pontos:

- Sem expiração.
- 30, 60, 90, 180 ou 365 dias.
- Prazo personalizado em dias.

Permitir também definir um prazo diferente numa campanha específica. A regra aplicada a cada atribuição fica registada nessa atribuição e não pode ser alterada retroactivamente quando o restaurante muda a configuração global.

A implementação deve usar lotes de pontos, e não apenas um total agregado:
- Cada atribuição cria um lote com pontos originais, pontos disponíveis, data de atribuição e data de expiração.
- Resgates consomem primeiro os lotes com expiração mais próxima (FEFO).
- Pontos expirados deixam de poder ser utilizados e o saldo apresentado ao cliente deve ser actualizado.
- Preservar os pontos que já existiam antes da migração: não os apagar nem lhes atribuir uma expiração retroactiva. Inicialmente, migrá-los como lote sem expiração.
- Registar no histórico cada atribuição, utilização e expiração.
- Fazer a expiração automaticamente, com execução agendada diária no servidor. A operação deve ser idempotente, segura perante execuções repetidas e não depender de o cliente abrir a aplicação.
- Notificar o cliente antes de os pontos expirarem (por exemplo, 7 dias e 1 dia antes), sem enviar notificações duplicadas.

A mudança do prazo global só afecta pontos atribuídos a partir daí. Cada campanha pode definir explicitamente o prazo dos pontos que ela própria oferece.

## 4. Carteira de recompensas

Na aplicação Cliente, apresentar separadamente:
- Pontos disponíveis e histórico de movimentos.
- Pontos a expirar e a data de expiração por lote.
- Saldo de cashback disponível.
- Vales/vouchers activos, utilizados e expirados.
- Descontos/ofertas disponíveis e respectivas condições.

Criar um ledger imutável para cashback e vales, com créditos e débitos, origem, campanha, data e saldo resultante. Nunca permitir saldo negativo. A utilização tem de ser atómica e idempotente para impedir utilização dupla, mesmo perante falhas de rede ou dois funcionários a tentar validar o mesmo vale.

Não permitir levantar cashback como dinheiro nem transferi-lo para outra conta. Deixar claro que se trata de crédito de utilização no restaurante.

## 5. Notificações personalizadas

Criar um centro de notificações na aplicação Cliente e, depois de configurado o fornecedor, notificações push Android.

Na aplicação do restaurante, permitir:
- Criar o título e a mensagem da notificação.
- Associar a uma campanha.
- Escolher o público elegível.
- Enviar imediatamente ou agendar para uma data/hora.
- Pré-visualizar a mensagem.
- Consultar o estado de envio e erros.

Exemplos:
- Promoção de 20% numa refeição hoje.
- Cashback disponível na carteira.
- Campanha exclusiva para clientes frequentes.
- Aviso de pontos prestes a expirar.
- Vale recebido ou prestes a expirar.

Respeitar consentimento explícito para marketing, opção de desactivar comunicações promocionais, limites de frequência e horário de silêncio. Notificações estritamente necessárias para o funcionamento de uma recompensa (por exemplo, confirmação de cashback) devem estar distinguidas de marketing.

**Push não pode ser declarado funcional sem FCM**: verificar/configurar Firebase Cloud Messaging, registo seguro de tokens por instalação, rotação/remoção de tokens e credenciais apenas no servidor/Secrets. Se essas credenciais não existirem, implementar e testar primeiro o centro de notificações dentro da aplicação, e indicar claramente que push está pendente de configuração. Nunca colocar credenciais de servidor no APK.

## 6. Fluxos de compra e validação

Integrar as campanhas com o fluxo de fidelização existente por QR. Na validação da compra, o funcionário deve indicar o valor elegível da compra e seleccionar uma campanha válida (ou o servidor seleccionar automaticamente a campanha aplicável, conforme a regra definida).

O servidor deve:
- Confirmar que o QR pertence ao cliente e ao restaurante e que ainda é válido.
- Validar datas, valor mínimo, produtos elegíveis, limites e público-alvo.
- Evitar repetir a atribuição na mesma compra.
- Calcular pontos/cashback/desconto/voucher.
- Devolver o valor do desconto e o valor final a pagar, quando aplicável.
- Registar a utilização e a recompensa emitida numa única transacção.
- Exigir confirmação clara do funcionário antes de concluir a operação.

Não afirmar que a integração com o terminal de pagamento/POS está concluída sem integração real com esse sistema. Quando o POS não estiver ligado, a aplicação deve apresentar o desconto/crédito calculado e exigir que o funcionário o aplique no sistema de pagamento do restaurante.

## 7. Modelo de dados e segurança Supabase

Inspeccionar primeiro as tabelas, funções, migrações e políticas existentes. Reutilizar `restaurants`, `restaurant_staff`, `loyalty_members`, `loyalty_transactions` e os RPC de fidelização existentes sempre que adequado.

Criar migrações versionadas para, no mínimo, estruturas equivalentes a:
- campanhas e regras de campanha;
- utilização/resgate de campanhas;
- lotes de pontos e expiração;
- configurações de validade por restaurante;
- ledger de cashback e vouchers;
- preferências/consentimento de marketing;
- notificações por cliente e estado de leitura/envio;
- tokens de dispositivos push, se FCM for efectivamente configurado.

Segurança obrigatória:
- RLS activo em todas as tabelas expostas.
- Nunca usar `user_metadata` como fonte de autorização.
- Proprietário ou funcionário com permissão específica pode gerir campanhas do próprio restaurante; utilizador não pode indicar livremente outro `restaurant_id`.
- Cliente só pode consultar a própria carteira, campanhas do restaurante a que pertence e as próprias notificações.
- Funções RPC com validação de `auth.uid()`, restaurante, papel e pertença do membro.
- `SECURITY DEFINER` apenas quando justificado, com `search_path` fixo e privilégios revogados/concedidos explicitamente. Preferir `SECURITY INVOKER` quando possível.
- Não expor `service_role`, chaves secretas ou credenciais FCM.
- Resgates e atribuições com identificadores de pedido idempotentes, transacções atómicas e bloqueios adequados.
- Registos de campanha já utilizados não devem ser alterados retroactivamente quando a campanha é editada.
- Registar quem criou/alterou/pausou uma campanha.

## 8. Interface e qualidade

Manter a linguagem visual premium, calma e consistente do StudioMesa. Interfaces adequadas a ecrãs Android pequenos, sem excesso de botões ou formulários extensos. Incluir estados de carregamento, vazio, erro, confirmação e ausência de ligação.

A aplicação Restaurante deve ter um editor simples por passos: tipo de promoção, valores/condições, público, calendário, notificação e revisão antes de publicar.

A aplicação Cliente deve apresentar as campanhas activas e as recompensas de forma clara, sem esconder datas, mínimos, limites ou condições.

## 9. Testes obrigatórios

Adicionar testes unitários e/ou de integração para:
- todos os tipos de promoção e limites monetários;
- campanhas futuras, activas, pausadas, terminadas e fora do horário;
- edição de uma campanha sem alterar resgates antigos;
- pontos sem expiração, com expiração global e com validade específica da campanha;
- consumo FEFO de lotes de pontos;
- expiração diária idempotente e execução repetida;
- carteira de cashback/vouchers, saldo zero e tentativa de saldo negativo;
- utilização duplicada do mesmo QR, voucher ou identificador de compra;
- cliente a tentar ler dados de outro cliente/restaurante;
- funcionário sem permissão de marketing;
- consentimento desactivado, preferências alteradas e limites de notificações;
- falhas de rede antes/depois de uma operação confirmada;
- datas e fusos horários, incluindo mudança de hora.

Executar:
- testes unitários Android;
- testes de integração/RPC no Supabase;
- auditoria RLS e advisors Supabase;
- build de todas as variantes de app que forem afectadas (Restaurante e Cliente, planos Essencial, Profissional e Premium);
- testes de instalação/actualização no Android.

Não afirmar que a tarefa está concluída enquanto não houver código integrado, migrações aplicadas e verificadas, testes/build com resultado real e validação de permissões. Não gerar nem anunciar um APK que não tenha sido construído com sucesso.

## Resultado esperado

Entregar o módulo funcional nas duas aplicações, com campanhas configuráveis e agendadas pelo restaurante, pontos com validade definida pelo restaurante, cashback/vales/descontos/ofertas, notificações personalizadas e segurança real no servidor. No relatório final, separar claramente o que ficou implementado e testado, o que depende de credenciais externas (por exemplo FCM/POS) e o que ficou pendente.
