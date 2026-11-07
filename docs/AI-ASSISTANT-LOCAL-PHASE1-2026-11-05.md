# StudioMesa — IA local, documentos e sincronização

**Data:** 6 de novembro de 2026  
**Branch:** `main`  
**Integração:** commit `6ac9c783f6d623fedf42af840b4eb235065e1536`.  
**Validação CI:** commit candidato `4d8fa85878c6c20f1ad81138fb69ffd961921083` — compilação Android, testes unitários de todas as variantes, Android Lint, configuração de release, replay das migrações/testes PostgreSQL e testes de interface em Android 8/API 26 e Android 16 passaram.  
**Estado:** integrado no repositório; ainda não aprovado para distribuição comercial. O APK disponível é uma compilação de demonstração/debug.

## IA generativa local

- Chat no ecrã principal, com novas conversas, respostas em streaming e motor determinístico disponível mesmo antes de descarregar o modelo.
- Modelo LFM2.5-1.2B-Instruct INT4 executado no Android por LiteRT-LM 0.18.0 (CPU).
- Transferência iniciada apenas após confirmação; 736 015 744 bytes, verificação do tamanho e SHA-256. O ficheiro está fixado à revisão Hugging Face `d23bed4d8871e970d9d4d7de087377a90ecb1b4e` e ao SHA-256 `e904729a8a8a0160658cb7cd22992e270a0547c05cc45b08c47ece5c0e29425b`.
- Sem API generativa externa, chave de fornecedor ou custo por mensagem depois da transferência.
- Pesquisa externa sem chave através da API pública da Wikipédia em português, com fallback para inglês. Só envia uma consulta curta e genérica; não envia o histórico nem os dados carregados do restaurante. É uma fonte limitada, pode estar desatualizada e pode impor limites de utilização.
- O modelo é pequeno e limitado a um contexto aproximado de 4 096 tokens. Não é equivalente a modelos de grande dimensão, não deve ser apresentado como ChatGPT e pode produzir erros. O desempenho em português europeu e no OPPO A5m tem de ser medido fisicamente.
- O modo **Pensar mais** acrescenta instruções de verificação, diminui a temperatura e aumenta o limite de saída de 1 024 para 1 400 tokens. Não revela raciocínio interno privado.

## Ligação entre a IA geral e o Assistente económico

- A IA geral pode chamar a ferramenta interna `consult_economic_assistant`, mas apenas quando a sessão pertence ao proprietário autenticado de um restaurante remoto.
- A ferramenta encaminha a consulta para o mesmo leitor de metadados usado pelo Assistente económico. Não cria um segundo acesso às tabelas nem duplica a lógica de leitura.
- A ponte só devolve fornecedor, número, datas, valores e estado de revisão das faturas já carregadas no arquivo local cifrado. Não devolve PDFs, texto OCR, caminhos privados ou dados bancários.
- É só de leitura. Não aprova, rejeita, altera, paga nem elimina documentos. A equipa e as contas não proprietárias não recebem esta ferramenta.
- Se o arquivo remoto não estiver disponível ou não tiver sido atualizado, a IA deve indicar essa limitação e não inventar resultados.

## Pesquisa pública na Internet

- A ferramenta `search_public_web` usa SearXNG através da Edge Function autenticada `studiomesa-web-search`.
- A função só funciona depois de ser configurado `SEARXNG_BASE_URL` para um servidor HTTPS SearXNG que tenha a API JSON ativa. Não usa Tavily nem exige chave API de pesquisa.
- A aplicação envia apenas uma consulta pública curta; nunca envia histórico, memória, faturas, clientes, equipa ou identificadores.
- Foram testadas instâncias SearXNG públicas em 7 de novembro de 2026, mas as instâncias verificadas bloquearam pedidos automatizados ou devolveram zero resultados/motores bloqueados. A função está preparada, mas a pesquisa Web permanece desativada até existir um endpoint fiável configurado.
- Ver [docs/SEARXNG-SETUP.md](SEARXNG-SETUP.md).

## Histórico, memória e sincronização

- Histórico pesquisável, reabertura e eliminação; memória editável por conta/restaurante.
- A cópia local do histórico e da memória é cifrada com AES-GCM e chave no Android Keystore.
- Para contas de proprietário autenticadas, o histórico e a memória podem sincronizar entre dispositivos através do Supabase. Mensagens usam IDs estáveis e inserção idempotente; o merge preserva as datas remotas e a memória usa timestamps para resolver conflitos simples. A eliminação de uma conversa grava uma tombstone cloud sem conteúdo: os outros dispositivos removem a cópia local, os textos das mensagens são apagados e triggers impedem que cópias antigas voltem a criar mensagens ou restaurar o título.
- As tabelas cloud têm RLS e políticas de proprietário por restaurante. A eliminação é feita por RPC de proprietário, preservando apenas o identificador e a data de eliminação. A cópia na cloud **não é cifrada ponta a ponta**: o acesso é controlado por autenticação e RLS do Supabase. A cópia local continua cifrada.
- Não se sincroniza o histórico de contas de funcionário; o Assistente é uma área do proprietário.

## OCR de faturas e arquivo privado

- PDFs e imagens JPG/PNG/WebP são processados localmente com ML Kit bundled e Android PdfRenderer. Não é usada API de OCR/IA paga.
- Limites atuais: 25 MB por documento, até 10 páginas de PDF e até 50 000 caracteres OCR.
- O parser extrai campos candidatos: fornecedor, NIF/VAT, número, data de emissão/vencimento, base tributável, IVA e total. O NIF português é validado pelo dígito de controlo; os totais são comparados com tolerância de 0,05 €.
- Campos incertos ficam assinalados. O utilizador tem de rever e confirmar; o OCR nunca aprova ou lança uma despesa automaticamente. A deteção de duplicados usa fornecedor, número e data.
- O índice local de metadados é cifrado com Android Keystore. O texto OCR bruto não é persistido.
- Quando existe sessão do proprietário, o ficheiro original é enviado para um bucket privado Supabase e os metadados ficam acessíveis em todos os dispositivos do proprietário. Quando não existe ligação, fica guardada a metadata cifrada e uma referência SAF ao documento; o documento original não é copiado para uma pasta privada autónoma, e a sua disponibilidade local depende da persistência da permissão do fornecedor de ficheiros.
- A aplicação permite retomar envios locais depois de falhas de rede e abrir o documento original a partir do arquivo privado com sessão autenticada e verificação SHA-256.
- O agente só consulta metadados e totais. Não aprova, rejeita, paga ou altera documentos por si.
- Os totais arquivados não são prova de pagamento, reconciliação bancária, lançamento contabilístico ou valor fiscalmente dedutível.

## Segurança e limites financeiros

O modelo só recebe os dados operacionais que a aplicação já carregou e as faturas registadas no arquivo. As suas ferramentas são apenas de leitura. Não podem alterar preços, stock, pedidos, reservas, permissões, documentos ou estados de pagamento.

Os cálculos de preço e gestão económica continuam a ser determinísticos e devem indicar os dados em falta. Não se promete previsões, reconciliação bancária ou aconselhamento fiscal/contabilístico.

O SAF-T(PT) de vendas do adquirente não inclui as faturas comerciais dos fornecedores. Para obter documentos de compras, é necessária a importação do próprio documento ou uma exportação do programa de compras/contabilidade. A versão atual aceita PDFs e imagens; ainda não implementa uma integração direta com um POS/ERP específico nem um importador genérico de CSV/SAF-T. Isso depende do fornecedor efetivamente usado pelo restaurante.

## Custos e distribuição

- Sem API paga de IA e sem custo por mensagem.
- A transferência inicial do modelo consome cerca de 736 MB de Internet e espaço local. OCR e inferência consomem CPU, RAM, bateria e armazenamento do telemóvel.
- Não foi configurada faturação de Gemini ou de outro serviço de IA remoto.
- A publicação na Play Store não faz parte desta fase; os testes são distribuídos em APK. Não foi feita qualquer publicação.
- As licenças LFM Open License v1.0 do modelo e Apache 2.0 do runtime têm de ser revistas antes de qualquer uso comercial.

## Validação obrigatória

- Compilar as cinco variantes Android.
- Testes unitários das cinco variantes, Android Lint e replay das migrações numa base descartável.
- Smoke tests Android 8/API 26 e Android 16, incluindo chat, teclado, sincronização, OCR, arquivo, permissões e anexos.
- Testar isolamento entre restaurantes e recusa de acesso a funcionários a histórico, memória e faturas.
- Testar documentos truncados, MIME incorreto, PDFs com muitas páginas, NIF inválido, totais inconsistentes, faturas duplicadas, perda de rede, sincronização repetida e permissões SAF revogadas.
- Medir instalação, arranque, RAM, latência, temperatura, bateria e qualidade de respostas no OPPO A5m físico.
- Ensaiar backup/restauro e política de retenção dos documentos antes de piloto real.

**Nenhum APK com IA deve ser tratado como versão comercial até a CI da última revisão passar e os testes físicos e de privacidade estarem concluídos.**
