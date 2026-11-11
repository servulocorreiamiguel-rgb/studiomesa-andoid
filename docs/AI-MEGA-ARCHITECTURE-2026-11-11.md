# StudioMesa — Mega IA com router multi-modelo (11-11-2026)

## Objetivo

Um único assistente e uma única conversa, com seleção automática do motor adequado. O utilizador não escolhe nem gere quatro chats separados.

## Motores

| Motor | Função | Situação nesta integração |
|---|---|---|
| DeepSeek R1 | Primeiro motor para problemas complexos, análise e raciocínio | Router online usa `deepseek/deepseek-r1:free` |
| Qwen 3.8 27B | Programação, revisão de código e tarefas gerais | Router online usa `qwen/qwen3.8-27b:free` |
| Gemma 4 E2B | Motor local Android, sem enviar perguntas para a nuvem | Modelo local de destino; requer integração e teste no hardware antes de substituir o modelo local atual |
| GPT-OSS 120B | Alternativa de raciocínio geral | Router tenta `openai/gpt-oss-120b:free` quando disponível |
| OpenRouter Free Router | Última alternativa gratuita, escolhida dinamicamente | `openrouter/free`, que só seleciona variantes gratuitas |

A seleção é feita no servidor com regras simples e auditáveis. Termos de programação direcionam para Qwen; pedidos de raciocínio complexo direcionam para DeepSeek; restantes perguntas usam Qwen. Se o modelo preferido falhar, o servidor tenta os seguintes modelos gratuitos da cadeia.

## Limite de custo: free-only

- O router usa exclusivamente IDs terminados em `:free` e `openrouter/free`.
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

A StudioMesa mantém o LFM2.5-1.2B já integrado como fallback local. Não substitui automaticamente esse motor por Gemma 4 E2B sem:
1. confirmar a revisão imutável do artefacto e o SHA-256 oficial;
2. verificar RAM disponível, espaço livre e compatibilidade do LiteRT-LM no dispositivo;
3. testar abertura do modelo, uma conversa longa, chamadas a ferramentas, encerramento e libertação de RAM;
4. medir latência, consumo de bateria, temperatura e falhas no OPPO A5m e em dispositivos comerciais representativos.

A substituição fica condicionada a esses testes para evitar que a IA local deixe de abrir em telemóveis com menos memória. A arquitetura suporta manter Gemma como motor local e o modelo LFM como fallback de compatibilidade.

## Integração técnica criada

- Edge Function: `supabase/functions/studiomesa-ai-router/index.ts`.
- Endpoint Android: `SupabaseApi.generateRoutedAssistantReply`.
- A interface mantém um único assistente e o consentimento atual passa a explicar a cadeia multi-modelo.
- As ferramentas determinísticas locais (cálculo exato, ferramentas económicas e OCR local) mantêm-se separadas da geração cloud.
- A IA não pode escrever preços, stock, reservas, pagamentos, faturas ou permissões.

## Ativação pendente

1. Configurar `OPENROUTER_API_KEY` nos segredos do projeto Supabase `svwvkfsyevuyabelrtjk`. Não colocar a chave no APK, GitHub ou chat.
2. Publicar `studiomesa-ai-router` com `verify_jwt=true`.
3. Ativar a compilação com `-PstudiomesaAdvancedAssistantCloudEnabled=true` apenas depois de a função responder corretamente.
4. Executar testes de owner autorizado, funcionário, token expirado, identificadores sensíveis, falha de todos os modelos gratuitos e bloqueio de qualquer rota paga.
5. Rever termos, política de privacidade e transferências antes de usar conteúdo real.

A chave OpenRouter não está configurada nesta alteração. A função ainda não foi publicada e a opção cloud continua desativada por omissão. A integração de Gemma local ainda precisa da fase de compatibilidade no Android. Não declarar a Mega IA pronta para produção até esses passos estarem concluídos.
