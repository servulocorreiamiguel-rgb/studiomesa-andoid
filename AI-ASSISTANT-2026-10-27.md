# Assistente StudioMesa — capacidades e limites (revisão 28/10/2026)

## O que é
Assistente de apoio operacional gratuito e executado no dispositivo. Usa regras determinísticas e os dados que já estão carregados na app. **Não é um modelo generativo de linguagem** e não envia dados do restaurante para um fornecedor de IA.

## O que analisa
- Custos e preços registados na carta, com uma referência matemática quando o custo é válido.
- Pedidos ativos e pedidos em preparação nos dados carregados.
- Stock baixo/esgotado, quando a função existe no plano Premium e a lista foi carregada.
- Reservas pendentes, quando a função existe nos planos Profissional/Premium e a lista foi carregada.
- Diagnóstico inicial de problemas de conta/sincronização, sem fingir que mede a Internet ou a saúde do servidor.
- Perguntas simples sobre a equipa e o acesso, sem alterar permissões.

## Segurança e honestidade
- Nunca altera preços, pedidos, reservas, stock, permissões ou dados de clientes.
- Os botões abrem a secção relevante; qualquer gravação fica a cargo da pessoa autorizada.
- A referência de 30% para custo/preço é apenas uma aproximação de triagem. Não considera automaticamente IVA, salários, renda, energia, desperdício, taxas, procura ou concorrência.
- Sem custo válido ou dados carregados, diz que falta informação em vez de inventar uma resposta.
- Não mostra nomes, contactos ou notas de clientes/reservas ao assistente.
- Perguntas e respostas não são enviadas para a rede nem guardadas como histórico.
- O diagnóstico de ligação é orientativo e não mede conectividade em tempo real.

## Capacidades acrescentadas em 28/10/2026

- **Resumo da operação:** mostra os dados carregados da carta, pedidos, custos em falta e, quando carregados, stock e reservas. Não é previsão de vendas.
- **Engenharia da carta:** classifica os artigos em Estrela, Popular com margem baixa, Margem alta com pouca procura e Rever artigo. Exige pelo menos cinco unidades de pedidos concluídos correspondentes aos artigos atuais. Usa os custos/preços atuais, não reconstrói a contabilidade histórica.
- **Rascunhos de marketing:** gera texto a partir de nome, descrição e preço registados e permite copiar para revisão manual antes de publicar.

## Limites
Não é um LLM generativo, não faz pesquisa na Internet, não prevê procura, não consulta fornecedores e não executa ações autónomas. Uma futura integração com modelo generativo exigiria uma decisão separada sobre fornecedor, custo, consentimento, minimização de dados e segurança.

## Critérios de aceitação
- Disponível nos planos de gestão Essencial, Profissional e Premium.
- Responde sobre preços/custos, pedidos/cozinha, stock, reservas, acesso e problemas de sincronização.
- Distingue demonstração, dados carregados e áreas ainda sem dados.
- As sugestões não escrevem alterações.
- Testes cobrem cálculo, dados em falta, intenções, stock, reservas e diagnóstico.
