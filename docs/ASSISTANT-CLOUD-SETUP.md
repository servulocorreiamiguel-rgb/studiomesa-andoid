# StudioMesa — IA online geral e pesquisa atual

## Objetivo

A aplicação mantém dois modos separados:

- **IA local:** LFM2.5-1.2B-Instruct INT4, executada no dispositivo. É a opção para trabalho offline e para informação privada do restaurante.
- **IA online geral:** Gemini 3.8 Flash, chamada por uma Supabase Edge Function autenticada. Só recebe a mensagem atual; não recebe histórico, Memória, resumo do restaurante, faturas, dados de clientes ou equipa.
- **Pesquisa atual:** Tavily, chamada no servidor apenas quando a pergunta pede explicitamente informação recente, atualidade, notícias, preços atuais, tendências ou legislação em vigor. Os excertos são dados externos não fiáveis; o modelo deve citar as ligações devolvidas.

O Assistente económico continua separado e local. Não passa faturas, OCR, números financeiros, contactos nem informação privada para Gemini ou Tavily.

## Requisitos de ativação

O código da Edge Function está no repositório, mas não fica operacional enquanto os dois segredos não estiverem configurados no projeto Supabase:

- `GEMINI_API_KEY`
- `TAVILY_API_KEY`

Configura-os em **Supabase Dashboard → Edge Functions → Secrets**. Não os coloques no Android, GitHub, ficheiros de configuração versionados ou mensagens de chat.

Depois de existir backup/restauro validado e o responsável aprovar a alteração, publica a função:

```bash
supabase functions deploy studiomesa-assistant --project-ref svwvkfsyevuyabelrtjk
```

A função deve manter `verify_jwt=true`. Não publiques uma versão que aceite pedidos anónimos.

## Limites, custos e privacidade

- A página oficial do Gemini lista `gemini-3.8-flash` e indica que a camada gratuita tem preços de entrada/saída sem custo, sujeita a quotas. A própria tabela assinala que a informação da camada gratuita pode ser usada para melhorar os produtos Google. Por isso, a opção online está desligada por defeito e a mensagem pede consentimento. Não usar esta camada gratuita para dados confidenciais ou pessoais.
- Tavily apresenta atualmente 1 000 créditos API gratuitos por mês, sem cartão bancário. O limite mensal pode parar as pesquisas até à renovação ou mudança de plano; não é uma garantia de serviço ilimitado.
- As condições, limites e preços dos fornecedores podem mudar. Confirmar as páginas oficiais antes de comercializar a funcionalidade.
- O código envia somente a mensagem atual, rejeita padrões comuns de identificadores pessoais e não envia o histórico ou os documentos do restaurante. Esta filtragem não consegue detetar todos os dados confidenciais; o aviso na app continua necessário.
- Uma pergunta geral pode ser respondida pelo Gemini sem pesquisa Web. Uma pergunta que exija atualidade tenta usar Tavily. Sem a chave de pesquisa, a função devolve erro explícito e não inventa resultados.

## Testes antes de ativar

1. Com ambos os segredos configurados, pergunta um tema geral e confirma que a resposta é guardada no histórico local.
2. Pergunta "Dá-me dicas de negócio atualizadas para 2026" e confirma que o resultado inclui fontes e ligações.
3. Confirma que uma pergunta sobre faturas nunca ativa a IA online e continua no Assistente económico.
4. Tenta enviar email, telefone, NIF ou IBAN; o servidor deve rejeitar identificadores reconhecidos.
5. Retira temporariamente cada segredo e confirma que é apresentado o erro de configuração correto.
6. Testa timeout, 429, 403, resposta vazia, ausência de fontes e sessão expirada.
7. Verifica no tráfego e nos logs que não são enviados histórico, memória, faturas, dados de clientes, equipa ou identificadores do restaurante.

## Estado desta versão

O código Android e a Edge Function estão preparados nesta branch. Ainda faltam as chaves dos fornecedores, configuração no Supabase, publicação da função e teste ponta a ponta. Até esses passos, não apresentar a IA online/pesquisa atual como ativada em produção.
