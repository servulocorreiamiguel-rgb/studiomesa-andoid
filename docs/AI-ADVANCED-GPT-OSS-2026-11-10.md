# StudioMesa — IA avançada com pesos abertos

**Data:** 10 de novembro de 2026  
**Estado:** código no repositório; ainda não publicado nem configurado no Supabase.

## Objetivo

A IA local usa LFM2.5-1.2B-Instruct INT4 e um contexto aproximado de 4 096 tokens. É útil para operação offline e ferramentas determinísticas, mas não tem a capacidade de um modelo de raciocínio de grande dimensão.

Foi preparada uma opção avançada baseada no **gpt-oss-120b**, modelo de pesos abertos publicado pela OpenAI e servido através da API compatível com OpenAI da Groq. Não é o modelo proprietário do ChatGPT Free, não é servido pela API da OpenAI e não deve ser anunciado como equivalente ao ChatGPT.

## Arquitetura no código

- O Android mantém o motor local como alternativa offline.
- O proprietário pode ativar explicitamente a IA avançada online no chat.
- A aplicação chama a Edge Function autenticada `studiomesa-advanced-assistant`.
- A função confirma o JWT com Supabase Auth e consulta `restaurant_staff` com o JWT do próprio utilizador; exige papel `owner` no `restaurant_id` pedido. Não usa `service_role`.
- O segredo `GROQ_API_KEY` fica exclusivamente nos segredos das Edge Functions do Supabase.
- O modelo recebe o histórico recente e um resumo agregado gerado no dispositivo.
- A resposta é consultiva: não executa RPCs nem altera registos.
- `reasoning_effort=high` pede análise mais exigente; `reasoning_format=hidden` não apresenta raciocínio interno como resposta.

## Dados enviados e excluídos

**Enviados, após ativação explícita:**
- Pergunta atual e até dez perguntas recentes escritas pelo utilizador; respostas anteriores do assistente nunca são encaminhadas, para evitar reenviar metadados de faturas devolvidos por ferramentas locais.
- Nome/categoria dos artigos, preço e custo unitário registados.
- Quantidades e limites de stock, apenas se a cache estiver atualizada.
- Número de pedidos ativos/em preparação e número agregado de reservas pendentes.
- Estatísticas agregadas de pedidos concluídos por dia da semana e previsão nominal dos próximos sete dias.

**Excluídos do resumo:**
- PDFs, imagens, faturas e texto OCR.
- Nomes/contactos/notas de clientes ou reservas.
- Dados da equipa.
- Memória do assistente.
- Identificadores internos ou credenciais.

O endpoint bloqueia padrões conhecidos de email, telefone, NIF e IBAN. Este filtro é uma defesa adicional, não uma garantia de anonimização. Os nomes dos artigos e os valores comerciais continuam a ser informação confidencial do restaurante.

## Dados e fornecedor

A documentação de dados da Groq indica que pedidos de inferência não são retidos por defeito, mas podem ser temporariamente registados até 30 dias para fiabilidade ou prevenção de abuso; existe uma opção Zero Data Retention. A documentação também indica armazenamento de dados retidos em infraestrutura GCP nos EUA. É necessário rever o DPA, transferências internacionais, base legal, transparência e configuração ZDR antes de dados reais.

O plano gratuito tem limites sujeitos a alteração. Quando a quota é atingida, o endpoint devolve erro 429. Não há garantia de capacidade contínua, SLA ou utilização comercial gratuita.

## Configuração ainda necessária

1. Criar uma conta/organização de fornecedor sob responsabilidade do titular legal adequado e aceitar os termos aplicáveis.
2. Criar a chave API Groq e definir `GROQ_API_KEY` em Supabase → Edge Functions → Secrets. Nunca inserir a chave no Android, GitHub, ficheiros de configuração ou mensagens.
3. Publicar a Edge Function `studiomesa-advanced-assistant` com `verify_jwt=true`.
4. Testar com dois proprietários de restaurantes diferentes e uma conta staff: só o proprietário do restaurante correspondente deve receber acesso.
5. Testar bloqueio de NIF/IBAN/telefone/email, payloads grandes, 429, falhas do fornecedor, sessões expiradas e mudança de conta/restaurante.
6. Comparar recomendações com os calculadores determinísticos e testar casos financeiros sintéticos.
7. Obter revisão legal/privacidade e DPA antes de ativar em clientes.

**Estado atual:** o código Android e a Edge Function estão preparados no repositório, mas a IA avançada não funciona até o segredo ser configurado e a função publicada. A IA local continua disponível sem este fornecedor.
