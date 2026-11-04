# StudioMesa — chat, memória e assistente generativo

## Funcionalidades nesta branch

- Chat com mensagens, nova conversa, histórico pesquisável, reabertura e eliminação de conversas.
- Memória editável, separada por conta/restaurante.
- Histórico e memória cifrados localmente com AES-GCM; a chave permanece no Android Keystore.
- Motor local para perguntas sobre a operação, dados económicos, faturas e notas de crédito.
- Ligação opcional a Gemini através da Edge Function `restaurant-assistant`. Só o proprietário autenticado da app de gestão pode ativar. A chave do fornecedor fica no servidor, nunca no APK.
- Contexto recente limitado às últimas 10 mensagens; não é enviado o histórico completo.
- O assistente não pesquisa a Internet nesta versão. A funcionalidade de Google Search foi adiada até ser possível apresentar as sugestões oficiais de pesquisa junto de cada resposta, cumprir os termos específicos e testar a experiência sem alterar os resultados apresentados.
- O texto enviado ao Gemini passa por filtros locais e no servidor. Perguntas com referências a faturas, IVA, NIF, fornecedores, compras, despesas, salários, margens, tesouraria, números, valores, contactos ou outros dados sensíveis são bloqueadas e permanecem no motor local.
- OCR e leitura local de PDF/imagens/TXT/XML; os anexos, o texto OCR, a Memória e o arquivo financeiro nunca são incluídos no pedido generativo.

## Consentimento, idade, região e custos da IA generativa

A Gemini API não deve ser apresentada como serviço gratuito ilimitado. Para disponibilizar a API a utilizadores no Espaço Económico Europeu, o Google exige acesso através de um projeto Cloud com conta de faturação ativa. O consumo pode ficar dentro de quotas sem custo, mas pode gerar cobranças quando essas quotas acabam ou as condições/preços mudam.

Os termos da Gemini API também exigem que quem usa os serviços tenha pelo menos 18 anos e proíbem a sua utilização como parte de aplicações dirigidas a menores ou provavelmente acedidas por menores. A ativação tem de ficar limitada à app de gestão destinada a operadores adultos; não disponibilizar a função na app cliente. O responsável adulto pelo projeto Cloud deve configurar a chave e aceitar os termos.

O consentimento da app informa que mensagens gerais e até 10 mensagens recentes podem ser processadas pela Google. Não incluir informação pessoal, confidencial ou empresarial sensível. As condições de tratamento de dados dependem do projeto de faturação ativo e dos termos em vigor da Google.

A integração de código está versionada nesta branch, mas não fica operacional em produção até publicar a Edge Function no projeto Supabase e configurar o segredo `GEMINI_API_KEY` (e, opcionalmente, `GEMINI_MODEL`). Não incluir chaves no código Android, commits, logs ou ficheiros de configuração públicos.

Os filtros são heurísticos. Não são uma garantia de anonimização nem de prevenção de fuga de dados. Podem bloquear perguntas legítimas por conterem números ou palavras sensíveis. Não escrever informação confidencial nas conversas generativas.

## Arquivo de faturas e gestor económico

- Arquivo cifrado local de faturas e notas de crédito, pesquisa, deteção de duplicados, anexos e exportação.
- OCR local de imagens e das primeiras 10 páginas de PDF; TXT/XML são lidos localmente.
- O parser apresenta sugestões editáveis de fornecedor, número, data, tipo de documento, base tributável, IVA e total.
- O tipo de documento (fatura/nota de crédito) é apresentado e pode ser alterado antes da confirmação.
- O proprietário tem de rever os campos sugeridos contra o documento original e confirmar. A pontuação apresentada é uma correspondência textual heurística, não uma probabilidade nem uma validação fiscal.
- O gestor económico consulta os totais das faturas e notas de crédito que foram confirmadas no arquivo. Não soma automaticamente compras ao custo alimentar consumido, para evitar dupla contagem.
- Perguntas financeiras, dados fiscais e documentos continuam no motor local; não são enviados ao Gemini.

## O que ainda não está feito

- Pesquisa na Internet com fontes: adiada até cumprir as exigências de exibição das sugestões oficiais Google Search e validar o custo/região.
- Histórico, Memória, arquivo de faturas e anexos não sincronizam entre dispositivos nem têm backup cloud nesta versão.
- A app não está ligada a um POS ou programa de faturação/contabilidade. A integração depende de identificar o software do restaurante e validar a API/exportação que esse fornecedor disponibiliza.
- Não assumir que o SAF-T (PT) de faturação inclui faturas de compras emitidas pelos fornecedores.
- A Edge Function Gemini ainda precisa de deploy autenticado e configuração de segredo no Supabase.
- Não foi alterado o esquema alojado do Supabase. A reconciliação do histórico de migrações e o plano de backup/restauro têm de estar concluídos antes de criar tabelas, buckets ou sincronização cloud.

## Testes e segurança antes de distribuir

- Testes instrumentados para persistência e separação por âmbito do histórico e memória.
- Testes para parser de sugestões, duplicados, privacidade, OCR e arquivos cifrados.
- CI tem de concluir compilação das cinco variantes, testes unitários, Lint, configuração comercial, replay de migrações desde base vazia, testes de autorização e smoke tests em Android 8 e Android 16.
- Testes reais em dispositivos, contas e restaurantes de teste continuam necessários. O sucesso da CI não substitui testes de recuperação de conta, backup/restauro, utilização prolongada, rede instável ou permissões.
- O assistente não substitui contabilista, validação fiscal, aconselhamento jurídico nem decisão do proprietário.
