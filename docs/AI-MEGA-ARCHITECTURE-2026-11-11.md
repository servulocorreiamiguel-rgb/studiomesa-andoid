# StudioMesa — Mega IA com router multi-modelo (11-11-2026)

## Objetivo

Um único assistente e uma única conversa, com seleção automática do motor adequado. O utilizador não escolhe nem gere quatro chats separados.

## Motores

| Motor | Função | Situação nesta integração |
|---|---|---|
| DeepSeek R1 | Primeiro motor online para raciocínio complexo | OpenRouter: `deepseek/deepseek-r1:free` |
| Qwen3.8 27B | Programação e tarefas gerais | OpenRouter `qwen/qwen3.8-27b:free`; variante apresentada a preço zero. Só é usada quando o endpoint cumpre os filtros de privacidade enviados pelo router |
| Gemma 4 E2B | Motor local Android preferido | Integrado via LiteRT-LM, com verificação SHA-256 e fallback local para LFM2.5-1.2B |
| GPT-OSS 120B | Variante grande de raciocínio pretendida | Não é chamada no modo gratuito: não existe variante gratuita confirmada no catálogo atual |
| Cohere North Mini Code | Programação e tarefas de código | OpenRouter: `cohere/north-mini-code:free` |
| GPT-OSS 20B | Alternativa cloud de baixo custo | OpenRouter: `openai/gpt-oss-20b:free` |
| OpenRouter Free Router | Última alternativa dinâmica | `openrouter/free`, seleciona um modelo gratuito que satisfaça os requisitos |

A aplicação mantém **um único assistente e uma única conversa**. O Qwen3.8 27B (variante gratuita) também integra a cadeia de tarefas gerais, além da cadeia de programação. O utilizador não escolhe quatro chats. A Edge Function seleciona o primeiro motor gratuito adequado à categoria da pergunta e só tenta o seguinte quando o anterior falha. O sistema não executa os modelos todos em paralelo e não envia a mesma mensagem a vários fornecedores.

A variante `qwen/qwen3.8-27b:free` está listada no OpenRouter com preço zero e suporte para chamadas de ferramentas. A rota paga `qwen/qwen3.8-27b` é diferente e nunca é chamada. GPT-OSS 120B continua excluído do modo gratuito; GPT-OSS 20B mantém-se como alternativa gratuita confirmada. O router exige `data_collection: deny`, `zdr: true` e `allow_fallbacks: false`; se não existir um endpoint elegível, a tentativa falha e passa para o próximo modelo gratuito, sem enfraquecer a privacidade nem usar um modelo pago.

## Privacidade

O router online:
- requer sessão Supabase válida e papel `owner` no restaurante;
- só aceita mensagens com papel `user`;
- envia a pergunta atual e, no máximo, dez perguntas anteriores escritas pelo utilizador;
- não envia respostas anteriores do assistente, memória, resumo do restaurante, menu, stock, vendas, reservas, equipa, faturas, PDFs, imagens ou OCR;
- rejeita emails, telefones, NIF e IBAN detetáveis;
- rejeita expressamente payloads com `restaurant_context`, `memory` ou `invoice`;
- pede `data_collection: deny` e `zdr: true` ao OpenRouter. Se nenhum fornecedor gratuito respeitar esses requisitos, não tenta um fornecedor menos privado nem pago.

O filtro de identificadores não deteta todos os dados sensíveis. O modo online deve ser utilizado apenas para perguntas gerais e código sem dados pessoais ou comerciais confidenciais. A IA local é a opção para conteúdo privado do restaurante.

## Gemma no Android: decisão técnica

O repositório LiteRT Community publica Gemma 4 E2B no formato `.litertlm` para Android. O ficheiro documentado tem aproximadamente 2,58 GB. O modelo card apresenta cerca de 1,73 GB de memória de CPU no ensaio Android publicado num Galaxy S26 Ultra; esse resultado não prova desempenho ou estabilidade no OPPO A5m nem noutros telemóveis. A documentação oficial da Google identifica Gemma 4 sob Apache License 2.0; a conversão da comunidade continua a exigir a preservação dos seus avisos e atribuição. O LFM2.5-1.2B fallback usa a LFM Open License v1.0, cujo uso comercial gratuito está condicionado a receita anual inferior a USD 10 milhões para a entidade legal responsável.

A StudioMesa integra agora Gemma 4 E2B e mantém o LFM2.5-1.2B INT4 como fallback local mais leve. O gestor escolhe Gemma quando o dispositivo tem pelo menos 6 GiB de RAM total e espaço livre para o artefacto mais 512 MB de margem; caso contrário, escolhe LFM2.5. O LFM2.5 não é apagado quando Gemma é instalado. Em equipamentos que ficam com Gemma como motor principal, a aplicação oferece a instalação opcional do LFM2.5 como reserva; o fallback automático só funciona depois de esse ficheiro estar instalado e passar a verificação SHA-256. Se Gemma falhar sem reserva válida, a aplicação indica ao utilizador como instalar a alternativa. Antes de ativar Gemma em produção, continuam obrigatórios:
1. confirmar a revisão imutável do artefacto e o SHA-256 oficial;
2. verificar RAM disponível, espaço livre e compatibilidade do LiteRT-LM no dispositivo;
3. testar abertura do modelo, uma conversa longa, chamadas a ferramentas, encerramento e libertação de RAM;
4. medir latência, consumo de bateria, temperatura e falhas no OPPO A5m e em dispositivos comerciais representativos.

A integração e a transferência/validação SHA-256 estão implementadas, mas não foi ainda executado um ensaio físico de qualidade, latência, RAM, temperatura e bateria no OPPO A5m nem noutros equipamentos suportados. A seleção por RAM e espaço livre é um filtro inicial, não uma garantia de que o runtime conseguirá carregar Gemma em todos os telemóveis elegíveis.

## Integração técnica criada

- Edge Function: `supabase/functions/studiomesa-ai-router/index.ts`.
- Endpoint Android: `SupabaseApi.generateRoutedAssistantReply`.
- A interface mantém um único assistente e o consentimento atual passa a explicar a cadeia multi-modelo.
- O router online disponibiliza `calculate_exact_math`, um parser aritmético determinístico sem `eval`, limitado a 256 caracteres e 64 operações; divisão por zero, sintaxe não suportada e resultados excessivos são rejeitados. A ferramenta é apenas de leitura.
- As ferramentas económicas, consulta de dados do restaurante e OCR de faturas continuam locais; o router cloud não recebe contexto privado e não pode escrever na base de dados.
- A IA não pode escrever preços, stock, reservas, pagamentos, faturas ou permissões.

## Ativação pendente

1. Configurar `OPENROUTER_API_KEY` nos segredos do projeto Supabase `svwvkfsyevuyabelrtjk`. Não colocar a chave no APK, GitHub ou chat.
2. Publicar `studiomesa-ai-router` com `verify_jwt=true`.
3. Ativar a compilação com `-PstudiomesaAdvancedAssistantCloudEnabled=true` apenas depois de a função responder corretamente.
4. Executar testes de owner autorizado, funcionário, token expirado, identificadores sensíveis, falha de todos os modelos gratuitos e bloqueio de qualquer rota paga.
5. Rever termos, política de privacidade e transferências antes de usar conteúdo real.

A chave OpenRouter não está configurada nesta alteração. A função ainda não foi publicada e a opção cloud continua desativada por omissão. O funcionamento real de Gemma ainda precisa de validação física no OPPO A5m e em dispositivos comerciais representativos. Não declarar a Mega IA pronta para produção até esses passos estarem concluídos.

## Referências dos modelos e condições verificadas

- OpenRouter — DeepSeek R1 (variante gratuita): https://openrouter.ai/deepseek/deepseek-r1:free
- OpenRouter — Cohere North Mini Code (variante gratuita): https://openrouter.ai/cohere/north-mini-code:free
- OpenRouter — GPT-OSS 20B (variante gratuita): https://openrouter.ai/openai/gpt-oss-20b:free
- OpenRouter — catálogo de modelos gratuitos e limites: https://openrouter.ai/collections/free-models
- OpenRouter — Qwen3.8 27B (variante gratuita): https://openrouter.ai/qwen/qwen3.8-27b%3Afree
- OpenRouter — filtros de privacidade e Zero Data Retention: https://openrouter.ai/docs/guides/routing/provider-selection
- Google — Gemma 4 e LiteRT-LM Android: https://developers.google.com/edge/litert-lm/models/gemma-4

Verificação efetuada em 11 de novembro de 2026. As listas de modelos gratuitos, limites e termos mudam; repetir a verificação antes de cada lançamento comercial.
