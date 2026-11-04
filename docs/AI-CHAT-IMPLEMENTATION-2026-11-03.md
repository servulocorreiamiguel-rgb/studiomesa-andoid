# StudioMesa — chat, memória e pesquisa assistida

## Funcionalidades nesta branch

- Chat com mensagens, nova conversa, histórico pesquisável, reabertura e eliminação de conversas.
- Memória editável, separada por conta/restaurante.
- Histórico e memória cifrados localmente com AES-GCM; a chave permanece no Android Keystore.
- Motor local para perguntas sobre a operação, dados económicos, faturas e notas de crédito.
- Ligação opcional a Gemini através da Edge Function `restaurant-assistant`. Só o proprietário autenticado pode ativar. A chave do fornecedor fica no servidor, nunca no APK.
- Contexto recente limitado às últimas 10 mensagens; não é enviado o histórico completo.
- Pesquisa na Internet opcional, desligada por defeito e sujeita a consentimento separado. Quando ativada, o pedido pode usar Google Search grounding e apresentar até cinco fontes HTTPS devolvidas pelo fornecedor.
- O texto enviado ao Gemini passa por filtros locais e no servidor. Perguntas com referências a faturas, IVA, NIF, fornecedores, compras, despesas, salários, margens, tesouraria, números, valores, contactos ou outros dados sensíveis são bloqueadas e permanecem no motor local.
- OCR e leitura local de PDF/imagens/TXT/XML; os anexos, o texto OCR, a Memória e o arquivo financeiro nunca são incluídos no pedido generativo.

## Consentimento, custos e limites da IA generativa

A ativação do Gemini é opcional. O ecrã de consentimento informa que mensagens gerais e contexto recente podem ser processados pela Google e que, no escalão gratuito, a utilização pode estar sujeita a limites e às condições de utilização de dados do fornecedor.

A pesquisa Google Search tem um consentimento adicional. A documentação do Gemini indica 5 000 pesquisas gratuitas por mês para Gemini 3 e cobrança de 14 USD por 1 000 pesquisas adicionais, segundo as condições e quotas do projeto Google. Estes valores podem mudar; a app não garante custo zero. A pesquisa só é pedida quando o proprietário a ativa e o filtro de privacidade permite enviar a conversa.

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

- Histórico, Memória, arquivo de faturas e anexos não sincronizam entre dispositivos nem têm backup cloud nesta versão.
- A app não está ligada a um POS ou programa de faturação/contabilidade. A integração depende de identificar o software do restaurante e validar a API/exportação que esse fornecedor disponibiliza.
- Não assumir que o SAF-T (PT) de faturação inclui as faturas de compras emitidas pelos fornecedores.
- A Edge Function Gemini ainda precisa de deploy autenticado e configuração de segredo no Supabase.
- Não foi alterado o esquema alojado do Supabase. A reconciliação do histórico de migrações e o plano de backup/restauro têm de estar concluídos antes de criar tabelas, buckets ou sincronização cloud.

## Testes e segurança antes de distribuir

- Testes instrumentados para persistência e separação por âmbito do histórico e memória.
- Testes para parser de sugestões, duplicados, privacidade, OCR e arquivos cifrados.
- CI tem de concluir compilação das cinco variantes, testes unitários, Lint, configuração comercial, replay de migrações desde base vazia, testes de autorização e smoke tests em Android 8 e Android 16.
- Testes reais em dispositivos, contas e restaurantes de teste continuam necessários. O sucesso da CI não substitui testes de recuperação de conta, backup/restauro, utilização prolongada, rede instável ou permissões.
- O assistente não substitui contabilista, validação fiscal, aconselhamento jurídico nem decisão do proprietário.
