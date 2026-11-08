# StudioMesa — funcionalidades e sequência de conclusão

## Já presentes no código principal

- Gestão de mesas, pedidos e cozinha (fila de preparação, estados e tempos).
- Carta, categorias, descrições, preços e alergénios.
- Reservas base, estados, atribuição de mesas e validação de conflitos.
- Inventário, movimentos, quantidades mínimas e custo unitário.
- Análise operacional no plano Premium.
- Fidelização: QR de uso único, pontos, loja de recompensas, stock e confirmação de entrega.
- OCR local e arquivo privado de faturas de fornecedores.
- Assistente local geral e Assistente económico, histórico e memória cifrada.
- Consultor de preços com aprovação do proprietário.

## Concluído no código da branch de integração, ainda não aplicado na base ativa

- Fichas técnicas por artigo e ingredientes por dose.
- Desconto atómico de ingredientes e criação de movimentos quando um pedido passa de "Aberto" para "Em preparação". A dedução ocorre ao iniciar a preparação, não ao entregar o prato: assim o stock é reservado/consumido no momento em que a cozinha começa a usar os ingredientes. O mesmo pedido não pode descontar duas vezes; stock insuficiente bloqueia a mudança de estado.
- Matriz de permissões operacionais para pedidos, mesas, reservas, carta, stock e fidelização.
- Perfis rápidos de permissões: Gerente, Empregado de mesa, Cozinha e Caixa. São modelos para as permissões armazenadas; o servidor valida as permissões, não confia no nome do perfil.
- Limites e fotografias da loja de pontos (5/15/50 prémios ativos; fotos nos dois planos superiores) e variante Cliente Essencial.
- Permissão de inventário necessária para consultar/editar receitas, incluindo bloqueio server-side das RPCs e teste de chamadas sem permissão.
- Notificações locais de atividade no Android, com primeira linha de base sem alertas antigos, deduplicação e navegação por evento. Só verificam a cada 60 segundos enquanto a app está aberta.
- IA geral online opcional com envio apenas da pergunta atual; Gemini para conversa e Tavily/SearXNG para pesquisa pública atual. A IA económica permanece separada. Exige segredos no servidor e publicação da Edge Function.

## Funcionalidades recomendadas para uma fase seguinte

1. **Compras e fornecedores:** cadastro de fornecedor, artigos por fornecedor, encomenda, receção parcial/total, atualização de custos e stock com uma transação auditável.
2. **Reservas avançadas:** calendário diário/semanal, turnos, lista de espera, edição, cancelamento com regras, lembretes e depósitos através de fornecedor de pagamentos.
3. **Operação da cozinha:** a página Cozinha já existe. Melhorar filtros por estação/bar/cozinha, prioridade de pedidos, tempo estimado e histórico de tempos.
4. **Indicadores avançados:** a página Análise já existe. Acrescentar períodos comparáveis, margem real baseada em vendas importadas, desperdício, custo de pessoal e exportação; não estimar lucro apenas a partir de faturas de compra.
5. **Integração comercial:** escolher um POS/faturação certificado usado pelo primeiro cliente. Não criar um emissor fiscal próprio nesta fase.
6. **Push FCM:** notificações em segundo plano dependem de Firebase, application IDs finais, tokens registados com segurança e credencial HTTP v1. Ver `FCM-PUSH-SETUP.md`.

A estratégia é fechar a operação, a segurança e os testes ponta a ponta antes de multiplicar funcionalidades. Não criar cinco listagens Google Play quase iguais só para representar os planos.
