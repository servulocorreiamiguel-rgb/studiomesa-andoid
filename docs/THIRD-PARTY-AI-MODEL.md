# Avisos de terceiros — IA local StudioMesa

A funcionalidade de IA generativa da StudioMesa utiliza componentes de terceiros. A StudioMesa não treinou nem é proprietária do modelo de linguagem.

## Modelos locais

### Gemma 4 E2B

- Publicação do modelo base: Google.
- Formato Android: ficheiro LiteRT-LM convertido/publicado pela comunidade `litert-community` no Hugging Face.
- Licença do modelo Gemma 4: **Apache License 2.0**, conforme a documentação oficial Google AI Edge.
- Ficheiro usado pela aplicação: `gemma-4-E2B-it.litertlm`, tamanho esperado de 2 588 147 712 bytes.
- Revisão imutável usada pela aplicação: `6e5c4f1e395deb959c494953478fa5cec4b8008f`; SHA-256 é validado antes da inferência.
- Referências: [Google AI Edge — Gemma 4](https://developers.google.com/edge/litert-lm/models/gemma-4) · [ficheiro LiteRT-LM convertido](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm).
- Preservar a licença Apache 2.0 e os avisos/atribuição existentes no repositório da conversão comunitária.

### LFM2.5-1.2B-Instruct

- Publicação do modelo base: Liquid AI.
- Conversão para LiteRT-LM: comunidade `litert-community`.
- Licença declarada: **LFM Open License v1.0**. O uso comercial gratuito está condicionado a a entidade legal responsável não atingir nem ultrapassar 10 milhões USD de receita anual. Atingido esse limiar, é necessária licença comercial da Liquid AI. São também obrigatórios os avisos e a atribuição previstos na licença.
- Texto oficial: [LFM License — Liquid AI](https://www.liquid.ai/lfm-license).

- Nome: LFM2.5-1.2B-Instruct.
- Publicação de pesos: Liquid AI.
- Conversão para o formato LiteRT-LM: comunidade `litert-community`.
- Repositório e ficheiro: [LFM2.5-1.2B-Instruct no Hugging Face](https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct).
- Licença declarada pelo repositório: **LFM Open License v1.0**. O modelo base tem condições específicas para utilização comercial e atribuição; a licença descreve uma concessão sem royalties para organizações abaixo do limiar de 10 milhões de dólares de receita anual, sujeita aos restantes termos. Não presumir que essa condição se aplica a qualquer entidade sem ler a licença integral.
- Texto da licença: [LICENSE no repositório do modelo](https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct/blob/main/LICENSE).

O repositório `litert-community` é uma conversão comunitária e declara não ser afiliado da Liquid AI. O ficheiro utilizado pela aplicação é uma conversão/quantização do modelo base, não um modelo treinado pela StudioMesa.

## Runtime

- Biblioteca: LiteRT-LM Android `0.18.0`.
- Projeto e licença: [Google AI Edge — LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM).
- Licença do runtime: Apache License 2.0, disponível no repositório do projeto.
- O runtime executa o modelo no dispositivo Android.
- A versão `0.18.0` é necessária para interpretar as chamadas às ferramentas do modelo LFM2 reempacotado em 13/10/2026. As versões `0.15` a `0.17` devolvem essas chamadas como texto e não executam as ferramentas automaticamente.
- A biblioteca `0.18.0` requer Kotlin 2.3 ou superior; o projeto usa Kotlin `2.3.0` com Android Gradle Plugin `8.13.2` e Gradle `8.13`.
- Cada sessão nativa usa um par próprio `Engine` + `Conversation`; a conversa é fechada antes do motor. A sessão é reconstruída periodicamente a partir do histórico local para respeitar a janela de contexto.

## Ficheiro transferido

- Variante: `LFM2.5-1.2B-Instruct_int4.litertlm`.
- Tamanho esperado: 736 015 744 bytes.
- SHA-256 esperado: `e904729a8a8a0160658cb7cd22992e270a0547c05cc45b08c47ece5c0e29425b`.
- URL fixo da aplicação (revisão imutável `d23bed4d8871e970d9d4d7de087377a90ecb1b4e`): [Hugging Face — ficheiro INT4](https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct/resolve/d23bed4d8871e970d9d4d7de087377a90ecb1b4e/LFM2.5-1.2B-Instruct_int4.litertlm).
- O SHA-256 acima coincide com o manifesto publicado do artefacto INT4. A aplicação verifica o hash antes da inferência.
- A aplicação transfere o ficheiro para armazenamento privado e confirma tamanho e SHA-256 antes de o aceitar.
- O modelo é descarregado a pedido; não está incluído no APK.

## Privacidade e utilização

A geração do texto continua local. A pesquisa enciclopédica pode consultar a Wikipédia. A pesquisa geral na Web usa uma Edge Function autenticada do Supabase e um endpoint SearXNG configurado pelo operador através do segredo `SEARXNG_BASE_URL`. A aplicação envia apenas uma pergunta pública curta; não envia o histórico completo nem o contexto operacional da StudioMesa. O endpoint tem de usar HTTPS e a API JSON. Se não estiver configurado, a ferramenta devolve indisponibilidade em vez de simular resultados. A Edge Function bloqueia padrões comuns de emails, números de telefone, NIF/NIPC e IBAN, mas esse filtro não deteta todos os dados confidenciais. Os resultados incluem excertos, ligações e, quando disponível, a data de publicação. São pistas para verificação, não uma garantia de verdade. A IA deve comparar fontes independentes, preferir fontes primárias e admitir quando não consegue confirmar uma afirmação.

Depois da instalação, a inferência é executada localmente. As mensagens, o histórico e o contexto selecionado para o modelo não são enviados a um serviço externo de IA por esta funcionalidade. A transferência inicial do ficheiro requer Internet e consome cerca de 736 MB de dados.

## Ferramentas económicas locais

No modo Assistente Económico, o modelo pode chamar a ferramenta local `calculate_restaurant_economics`. A ferramenta usa o motor determinístico `RestaurantEconomicManager` e suporta análise mensal, custo por receita, desperdício registado, comparação de fornecedores, desvios de orçamento, consumo teórico vs. real, cenário de custo de pessoal e reposição indicativa de stock.

A ferramenta:
- executa cálculos no dispositivo, sem enviar os parâmetros a um serviço externo;
- só calcula os parâmetros que recebe na chamada; não consulta automaticamente POS, banco, contabilidade ou documentos;
- identifica a origem dos valores como não verificada e pede confirmação ao utilizador antes de tratar o resultado como um dado do negócio;
- é apenas de leitura: não altera preços, stock, faturas, pagamentos, encomendas ou outros registos;
- devolve avisos e limitações, por exemplo que um custo de pessoal estimado não é uma escala de trabalho nem aconselhamento laboral, e que uma quantidade de reposição não faz uma encomenda.

A ferramenta de consulta de faturas continua separada e devolve metadados do arquivo. Não dá ao modelo acesso ao PDF original nem ao texto bruto de OCR.

## Sincronização cloud separada da inferência

O modelo generativo e o runtime processam as perguntas no próprio dispositivo. Existe, separadamente, um componente `AssistantCloudSyncCoordinator` que pode sincronizar conversas e Memória do proprietário com o Supabase quando a migration e as tabelas correspondentes estiverem instaladas. Essa sincronização não envia o conteúdo ao fornecedor do modelo nem a uma API externa de inferência, mas cria uma cópia cloud protegida por autenticação/RLS; **não é encriptação ponta a ponta**.

Em 9 de novembro de 2026, as tabelas de sincronização já existem no projeto Supabase ligado. No código comercial em revisão, a sincronização do histórico/Memória está desligada por defeito e só é ativada após consentimento explícito do proprietário; a cópia cloud é protegida por autenticação/RLS, mas não é cifrada ponta a ponta. Desativar a opção impede novos envios, mas não apaga automaticamente cópias já enviadas.

Quando o utilizador aciona as ferramentas de pesquisa pública, pode ser enviada uma consulta genérica à Wikipédia ou ao endpoint SearXNG configurado. Estas chamadas são separadas da geração local; não devem conter histórico, prompts completos, faturas, contactos ou dados internos.

A execução local não garante que as respostas estejam corretas. O modelo pode falhar, inventar informação ou não compreender contextos complexos. Verificar os cálculos e decisões importantes. A IA não substitui um contabilista certificado nem executa alterações financeiras ou operacionais.

## Condições antes de comercializar

Antes de distribuir a StudioMesa comercialmente:
- rever o texto integral da LFM Open License v1.0 e confirmar a elegibilidade da entidade;
- conservar e fornecer os avisos de atribuição e cópias das licenças aplicáveis;
- rever as condições do repositório de conversão e manter a atribuição à Liquid AI e à comunidade litert-community;
- confirmar que o mecanismo de transferência continua a apontar para o artefacto e hash verificados;
- repetir os testes de compatibilidade e desempenho em dispositivos físicos suportados.
