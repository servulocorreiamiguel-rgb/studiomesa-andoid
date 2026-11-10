# StudioMesa — IA avançada GPT-OSS 120B

**Data:** 10 de novembro de 2026  
**Branch:** `feature/gpt-oss-agent-tools-2026-11-10`  
**Estado do código:** integração Android e Edge Function preparadas numa branch de revisão; ainda não publicadas no Supabase nem configuradas com uma chave Groq.

## Objetivo e limites

O StudioMesa mantém dois motores:

- **IA local:** LFM2.5-1.2B-Instruct INT4, executada através de LiteRT-LM. Continua disponível offline depois da transferência inicial do modelo.
- **IA avançada opcional:** `openai/gpt-oss-120b`, executada pela API compatível com OpenAI da Groq. É um modelo de pesos abertos da OpenAI, mas não é o ChatGPT Free, não é servido pela API da OpenAI e não deve ser anunciado como equivalente ao ChatGPT.

O GPT-OSS 120B é um modelo de texto. A análise de faturas continua a ser feita pelo OCR local do StudioMesa e exige confirmação humana. O modelo não aprova, altera ou paga documentos e não escreve na base de dados.

## Capacidades previstas nesta integração

1. **Raciocínio aprofundado:** `reasoning_effort=high` nas perguntas normais; o conteúdo de raciocínio interno é excluído da resposta.
2. **Execução de código:** `code_interpreter` permite ao modelo executar Python num ambiente isolado para apoiar cálculos e análise de dados. Não recebe permissões para escrever no StudioMesa.
3. **Pesquisa na Web:** `browser_search` é disponibilizado quando a pergunta pede explicitamente pesquisa pública ou informação atual. Nesse pedido, apenas a pergunta atual é enviada ao fornecedor; não são enviados histórico, Memória nem resumo privado do restaurante.
4. **Memória curta:** nas perguntas normais, o modelo recebe um resumo da Memória guardada, limitado a 350 caracteres. A autorização antiga deixa de ser válida e o proprietário tem de aceitar novamente o novo aviso.
5. **Continuidade:** podem ser enviadas até dez perguntas anteriores escritas pelo utilizador. As respostas anteriores do assistente não são encaminhadas, para evitar retransmitir dados provenientes de ferramentas locais.

A pesquisa web é isolada do contexto privado. A execução de código é disponibilizada sem ser obrigatória em todas as mensagens. Nenhuma destas ferramentas pode alterar preços, stock, pedidos, reservas, documentos, permissões ou pagamentos.

## Dados enviados e excluídos

**Perguntas normais, após consentimento renovado:**
- pergunta atual e até dez perguntas recentes escritas pelo utilizador;
- Memória curta editável, limitada a 350 caracteres;
- nomes/categorias dos artigos, preços e custos unitários registados;
- quantidades/limites de stock quando a cache foi carregada;
- contagens agregadas de pedidos/reservas e estatísticas/previsões de vendas.

**Pesquisa pública explícita:**
- apenas a pergunta atual;
- não envia Memória, histórico nem resumo operacional do restaurante.

**Nunca incluídos no payload do modelo nesta integração:**
- PDFs, imagens, faturas e texto OCR;
- nomes/contactos/notas de clientes ou reservas;
- dados da equipa, credenciais, identificadores internos ou dados bancários.

O endpoint bloqueia padrões conhecidos de email, telefone, NIF e IBAN. Este filtro é uma defesa adicional, não uma garantia de anonimização. Os nomes dos artigos, preços, custos e estatísticas comerciais continuam a ser informação confidencial.

## Fornecedores e privacidade

- Inferência: Groq.
- Pesquisa web: Browser Search integrado da Groq, alimentado por Exa.
- Execução de código: Code Interpreter integrado da Groq, num ambiente sandbox.

A documentação da Groq indica que os pedidos de inferência não são retidos por defeito. Em situações limitadas de fiabilidade ou prevenção de abuso, entradas e saídas podem ser registadas até 30 dias; a organização pode ativar Zero Data Retention nas definições de dados. Dados retidos são armazenados em buckets GCP nos EUA. Browser Search e Code Interpreter não estão disponíveis em endpoints regionais/soberanos. Antes de dados reais, é necessária revisão do DPA, subcontratantes, transferências internacionais, base legal, transparência, retenção e configuração ZDR.

O escalão gratuito tem limites que podem mudar; não existe garantia de quota contínua, SLA ou utilização comercial gratuita. As ferramentas integradas podem ter condições próprias.

## Segurança implementada no código

- A Edge Function confirma o JWT com Supabase Auth.
- Consulta a associação do utilizador em `restaurant_staff` e exige papel `owner` no restaurante pedido.
- Não utiliza `service_role`; a chave Groq fica apenas nos segredos da Edge Function.
- Mensagens, contexto e Memória são validados e limitados antes do envio.
- A pesquisa pública não recebe histórico, Memória nem contexto do restaurante.
- O raciocínio interno e os dados brutos de execução de ferramentas não são devolvidos à aplicação.
- Não existe ferramenta de escrita na base de dados.

## Configuração ainda necessária

1. Criar uma conta de fornecedor sob responsabilidade do titular legal adequado e rever os termos.
2. Criar uma chave Groq e guardá-la como `GROQ_API_KEY` nos segredos das Edge Functions do Supabase. Nunca colocar a chave no APK, GitHub ou mensagens.
3. Publicar `studiomesa-advanced-assistant` com `verify_jwt=true`.
4. Testar duas contas de proprietários de restaurantes diferentes e uma conta de funcionário.
5. Testar bloqueio de identificadores, pedidos grandes, erros 429, falhas do fornecedor, sessões expiradas, pesquisa pública e isolamento de contexto.
6. Rever DPA, política de privacidade, subcontratantes, transferências e retenção antes de dados reais.
7. Executar testes físicos no OPPO A5m e medir latência, RAM, bateria e estabilidade da IA local.

**Estado real:** o projeto Supabase consultado não tem ainda a Edge Function `studiomesa-advanced-assistant` publicada. O código nesta branch não ativa o serviço online por si só. Falta configurar a chave e publicar a função depois da revisão e aprovação. A IA local continua a ser o caminho alternativo.
