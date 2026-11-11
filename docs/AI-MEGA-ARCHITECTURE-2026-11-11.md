# StudioMesa — Mega IA com router multi-modelo (11-11-2026)

## Objetivo

Um único assistente e uma única conversa, com seleção automática do motor adequado. O utilizador não escolhe nem gere quatro chats separados.

## Motores

| Motor | Função | Situação nesta integração |
|---|---|---|
| DeepSeek R1 | Primeiro motor para problemas complexos, análise e raciocínio | Router online usa `deepseek/deepseek-r1:free` |
| Qwen 3.8 27B | Programação, revisão de código e tarefas gerais | Router online usa `qwen/qwen3.8-27b:free` |
| Gemma 4 E2B | Motor local Android preferido, sem enviar perguntas para a nuvem | Integrado no gestor de modelos LiteRT-LM; selecionado quando existem pelo menos 6 GiB de RAM total e espaço livre suficiente; instalação manual/única |
| GPT-OSS 120B | Alternativa de raciocínio geral | Router tenta `openai/gpt-oss-120b:free` quando disponível |
| OpenRouter Free Router | Última alternativa gratuita, escolhida dinamicamente | `openrouter/free`, que só seleciona variantes gratuitas |

A seleção é feita no servidor com regras simples e auditáveis. Termos de programação direcionam para Qwen; pedidos de raciocínio complexo direcionam para DeepSeek; restantes perguntas usam Qwen. Se o modelo preferido falhar, o servidor tenta os seguintes modelos gratuitos da cadeia.

## Limite de custo: free-only

- O router usa exclusivamente IDs terminados em `:free` e `openrouter/free`.
- A cadeia seleciona um motor de cada vez; só tenta o modelo seguinte quando o anterior falha. Não executa todos em paralelo nem soma automaticamente as respostas, para reduzir consumo da quota gratuita.
- Não existe fallback para um modelo pago.
- Erros de quota, falta de disponibilidade ou ausência de fornecedor que cumpra privacidade terminam em erro controlado. O utilizador pode voltar à IA local.
- OpenRouter indica atualmente 20 pedidos por minuto e 50 pedidos por dia para variantes gratuitas sem compra de créditos. Os limites podem mudar e pedidos falhados também contam.
- DeepSeek API direta e Groq para GPT-OSS 120B têm tarifas por token; por isso não são usados pelo modo free-only.
- A quota inicial QwenCloud tem validade limitada; não é considerada uma fonte gratuita permanente neste desenho.

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

O repositório LiteRT Community publica Gemma 4 E2B no formato `.litertlm` para Android. O ficheiro documentado tem aproximadamente 2,58 GB. O modelo card apresenta cerca de 1,73 GB de memória de CPU no ensaio Android publicado num Galaxy S26 Ultra; esse resultado não prova desempenho ou estabilidade no OPPO A5m nem noutros telemóveis.

A StudioMesa integra agora Gemma 4 E2B e mantém o LFM2.5-1.2B INT4 como fallback local mais leve. O gestor escolhe Gemma quando o dispositivo tem pelo menos 6 GiB de RAM total e espaço livre para o artefacto mais 512 MB de margem; caso contrário, escolhe LFM2.5. O LFM2.5 não é apagado quando Gemma é instalado. Antes de ativar Gemma em produção, continuam obrigatórios:
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
