# Aplicação de preços com aprovação do proprietário — 29/10/2026

## Estado desta implementação

Esta funcionalidade está numa branch de desenvolvimento e **a migração ainda não foi aplicada ao Supabase alojado**. Não ativar em restaurantes reais até a CI do commit candidato passar, o PR ser revisto/integrado e a migração ser aplicada e confirmada no projeto de teste/produção de forma controlada.

A análise da carta continua a ser apenas indicativa. A nova capacidade permite aplicar **um aumento de preço de cada vez**, depois de confirmação explícita do proprietário. Não há aplicação em lote nem alteração autónoma de preços pela IA.

## Controlos implementados no código

- A opção fica desligada por defeito; não existe autorização implícita se ainda não houver linha de configuração para o restaurante.
- Só uma conta com o papel `owner` pode ativar/desativar, aplicar um preço e consultar o histórico. Funcionários ficam em modo de leitura.
- Cada artigo apresenta a análise atual, mas a aplicação exige uma segunda confirmação específica.
- A função SQL volta a validar sessão, papel, artigo ativo, pertença ao restaurante, autorização ativada, preço esperado, custo atual e recomendação calculada no servidor.
- O servidor rejeita alterações com dados desatualizados, preços que não coincidam com o cálculo atual ou recomendações que não sejam superiores ao preço atual.
- O registo de auditoria fica no esquema privado e inclui o restaurante, identificador/nome do artigo, autor, preço anterior e aplicado, custo e objetivo.
- Um identificador de ação repetido com os mesmos dados devolve o resultado anterior sem aplicar uma segunda alteração; reutilizar o identificador com dados diferentes é rejeitado.
- A aplicação de preços na demonstração fica desativada. A interface não oferece o botão de alteração quando não existe restaurante ligado ou quando o proprietário não está autenticado/autorizado.

## Validação automatizada adicionada

A CI passa a reproduzir 41 migrações numa base de dados descartável. Os testes de autorização usam restaurantes e utilizadores temporários e verificam:

1. A autorização começa desligada.
2. Um funcionário não pode ativar, aplicar preços nem consultar o histórico do proprietário.
3. Um proprietário não pode aplicar sem ativar primeiro a opção.
4. Uma tentativa de usar um artigo de outro restaurante é recusada.
5. Um preço esperado desatualizado e um preço fornecido pelo cliente que não corresponda ao cálculo do servidor são recusados.
6. Uma recomendação que reduza o preço é recusada e não altera nem a carta nem o histórico.
7. Uma aplicação válida atualiza apenas o artigo esperado e cria exatamente uma linha de auditoria.
8. Repetir a mesma ação é idempotente; a mesma chave com outro valor é recusada.
9. O histórico é acessível pelo proprietário, não pelo funcionário, e a tabela privada não é diretamente consultável pelo papel autenticado.
10. Desativar a autorização impede novas alterações.
11. O teste de interface confirma que a experiência Premium em modo de demonstração continua apenas de leitura.

## Testes ainda necessários antes do lançamento

- Executar a CI completa do HEAD deste PR, incluindo Android 8 e Android 16.
- Rever a migração num ambiente Supabase descartável e confirmar todas as funções, grants, índices, RLS e políticas.
- Só depois de integrar a migração, aplicá-la ao projeto alojado e alinhar a versão de migração registada com o nome no repositório.
- Testar com contas descartáveis owner/staff em Android real: ativar/desativar, confirmar um aumento, confirmar rejeições por preço desatualizado, reiniciar sessão e consultar histórico.
- Simular perda de resposta de rede após a aplicação e repetir a mesma chave; confirmar que o preço não muda duas vezes. Se a rede falhar, a interface instrui a consultar o histórico/carta antes de repetir.
- Testar concorrência entre alteração do custo/preço na carta e confirmação da recomendação.
- Confirmar com o restaurante que custos e preços são valores atuais e que a sugestão não considera automaticamente IVA, mão de obra, renda, energia, comissões, desperdício, procura ou posicionamento.

Uma CI aprovada verifica a compilação e os cenários automatizados. Não certifica a segurança operacional total nem substitui testes ponta a ponta no projeto alojado e num dispositivo físico.
