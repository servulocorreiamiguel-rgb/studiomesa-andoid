# StudioMesa — IA local, fase 1

**Data:** 5 de novembro de 2026  
**Branch:** `feature/assistant-local-ai-phase1-2026-11-05`  
**Base:** `integration/non-ai-core-2026-11-05`  
**Estado:** implementação em validação; não integrada na branch de operação e não aprovada para produção.

## Incluído nesta fase

- Chat no ecrã principal do Assistente com mensagens, novas conversas, resposta em streaming e continuação do motor determinístico quando o modelo local ainda não foi instalado.
- Modelo generativo LFM2.5-1.2B-Instruct quantizado INT4, executado no próprio Android através de LiteRT-LM 0.18.0.
- Transferência iniciada pelo utilizador, de cerca de 736 MB, para a pasta privada da aplicação; valida tamanho e SHA-256 antes de aceitar o ficheiro.
- Histórico cifrado com AES-GCM e chave no Android Keystore, pesquisável, reabrível e eliminável.
- Memória editável, também cifrada e separada por conta/restaurante.
- Ferramentas locais de leitura para resumo da operação, carta, pedidos, stock e reservas que já estejam carregados na aplicação; cálculo matemático de referência de preço com custos registados.
- Modo **Pensar mais**, que acrescenta instruções de verificação, utiliza temperatura inferior e aumenta o limite de saída de 1 024 para 1 400 tokens. Não é uma exposição de raciocínio interno, nem muda o modelo instalado.
- Libertação do motor nativo ao colocar a app em segundo plano e encerramento no fim do ciclo de vida da Activity.

## Custos e utilização

A inferência é local. Não foi introduzida uma API de IA, uma chave de fornecedor, uma Edge Function de IA ou uma cobrança por mensagem.

A primeira instalação transfere aproximadamente 736 MB e precisa de espaço adicional temporário. Recomenda-se Wi-Fi: se o utilizador escolher dados móveis, o consumo fica sujeito ao tarifário da operadora. O modelo usa memória, CPU, bateria e armazenamento do próprio telemóvel.

Não foi configurado Google Gemini nem outro serviço de inferência remota. A publicação na Play Store também não faz parte desta fase; os testes são distribuídos por APK. Qualquer decisão de publicação deve ser tomada separadamente depois de avaliar os custos e requisitos aplicáveis.

## Contexto financeiro e segurança

O modelo recebe apenas o contexto operacional que a aplicação já tem disponível. As ferramentas são apenas de leitura: não podem alterar preços, pedidos, stock, reservas, pagamentos, permissões ou documentos.

Os cálculos de custos e preços continuam apoiados no motor determinístico já existente. O modelo pode explicar, resumir e comparar, mas não deve inventar dados em falta. Custos, margens e stock podem estar desatualizados ou não carregados; a aplicação deve indicar essa limitação. A resposta generativa não é um registo contabilístico nem uma decisão financeira.

O histórico e a memória ficam cifrados no dispositivo e separados por conta/restaurante. Não sincronizam entre dispositivos. Os dados financeiros e operacionais não são enviados para um fornecedor de IA nesta fase.

## Limitações conhecidas

- O modelo de 1,2 mil milhões de parâmetros é muito mais limitado do que modelos de grande dimensão como os usados no ChatGPT; a qualidade em português europeu ainda exige avaliação.
- O desempenho e a memória ainda precisam de ser medidos no OPPO A5m físico.
- O contexto é limitado a 4 096 tokens; a reconstrução nativa usa um conjunto reduzido de mensagens recentes.
- A IA só pode consultar dados efetivamente carregados na sessão; não tem acesso autónomo a todos os registos do restaurante.
- Não inclui OCR nem arquivo de faturas, integração com POS/faturação/contabilidade, pesquisa na Internet ou sincronização cloud de histórico/memória.
- A licença do modelo LFM e a licença Apache 2.0 do runtime têm de ser revistas e cumpridas antes de qualquer venda ou distribuição comercial. Consultar `docs/THIRD-PARTY-AI-MODEL.md`.

## Validação obrigatória

- Compilar as cinco variantes Android.
- Executar testes unitários, Android Lint e smoke tests Android 8/API 26 e Android 16.
- Confirmar que histórico e memória não atravessam contas/restaurantes.
- Confirmar o botão Pensar mais, os limites de saída e a manutenção do modo normal.
- Confirmar que as ferramentas de IA não alteram dados.
- Testar instalação, integridade, transferência interrompida, espaço insuficiente e recuperação de sessão.
- Medir arranque, RAM, latência, temperatura e autonomia num telemóvel físico, incluindo o OPPO A5m.

**Nenhum APK com IA deve ser tratado como versão de venda até a CI desta branch passar e a execução física ser avaliada.**
