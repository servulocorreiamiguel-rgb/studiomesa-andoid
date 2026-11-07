# Avisos de terceiros — IA local StudioMesa

A funcionalidade de IA generativa da StudioMesa utiliza componentes de terceiros. A StudioMesa não treinou nem é proprietária do modelo de linguagem.

## Modelo

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
- SHA-256 esperado: `a28b5c59ac204e2e51c1f98d2d6db6982f0e12da59a268fe498edcb33237e906`.
- URL: [Hugging Face — ficheiro INT4](https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct/resolve/main/LFM2.5-1.2B-Instruct_int4.litertlm).
- A aplicação transfere o ficheiro para armazenamento privado e confirma tamanho e SHA-256 antes de o aceitar.
- O modelo é descarregado a pedido; não está incluído no APK.

## Privacidade e utilização

A geração do texto continua local. A pesquisa enciclopédica pode consultar a Wikipédia. A integração de pesquisa geral na Web usa uma Edge Function autenticada do Supabase e o fornecedor Tavily. A aplicação envia apenas uma pergunta pública curta; não envia o histórico completo nem o contexto operacional da StudioMesa. A chave Tavily fica guardada nos Secrets do servidor e nunca é incluída no APK. A pesquisa geral requer sessão iniciada e configuração de TAVILY_API_KEY no Supabase. O plano gratuito anunciado pelo Tavily tem um limite mensal; não é utilização ilimitada. Os resultados incluem excertos, ligações e, quando disponível, a data de publicação. São pistas para verificação, não uma garantia de verdade. A IA deve comparar fontes independentes, preferir fontes primárias e admitir quando não consegue confirmar uma afirmação.

Depois da instalação, a inferência é executada localmente. As mensagens, o histórico e o contexto selecionado para o modelo não são enviados a um serviço externo de IA por esta funcionalidade. A transferência inicial do ficheiro requer Internet e consome cerca de 736 MB de dados.

## Sincronização cloud separada da inferência

O modelo generativo e o runtime processam as perguntas no próprio dispositivo. Existe, separadamente, um componente `AssistantCloudSyncCoordinator` que pode sincronizar conversas e Memória do proprietário com o Supabase quando a migration e as tabelas correspondentes estiverem instaladas. Essa sincronização não envia o conteúdo ao fornecedor do modelo nem a uma API externa de inferência, mas cria uma cópia cloud protegida por autenticação/RLS; **não é encriptação ponta a ponta**.

Em 6 de novembro de 2026, as tabelas de sincronização ainda não existem no projeto Supabase alojado. Portanto, a sync cloud não está operacional nesse ambiente e não deve ser anunciada como ativa até reconciliação/deployment e teste E2E aprovados.

Quando o utilizador aciona a ferramenta de pesquisa, a aplicação pode enviar uma consulta genérica à Wikipédia. Isto é uma chamada pública separada da geração local; não deve conter histórico, prompts completos, faturas, contactos ou dados internos.

A execução local não garante que as respostas estejam corretas. O modelo pode falhar, inventar informação ou não compreender contextos complexos. Verificar os cálculos e decisões importantes. A IA não substitui um contabilista certificado nem executa alterações financeiras ou operacionais.

## Condições antes de comercializar

Antes de distribuir a StudioMesa comercialmente:
- rever o texto integral da LFM Open License v1.0 e confirmar a elegibilidade da entidade;
- conservar e fornecer os avisos de atribuição e cópias das licenças aplicáveis;
- rever as condições do repositório de conversão e manter a atribuição à Liquid AI e à comunidade litert-community;
- confirmar que o mecanismo de transferência continua a apontar para o artefacto e hash verificados;
- repetir os testes de compatibilidade e desempenho em dispositivos físicos suportados.
