# StudioMesa chat, memória e agente financeiro — 03/11/2026

## O que esta alteração faz

- A página Assistente passa a abrir numa interface de conversa com mensagens alinhadas, caixa de escrita, envio, botão de nova conversa e pesquisa do histórico.
- Cada conversa tem título, data, mensagens e pesquisa no respetivo conteúdo. É possível abrir uma conversa antiga e eliminá-la.
- A memória é editável e pode ser consultada no chat. A aplicação inclui essa nota em respostas locais relevantes.
- O histórico e a memória são cifrados com AES-GCM; a chave é mantida no Android Keystore. O armazenamento é privado da aplicação e separado por utilizador e restaurante.
- O histórico tem limites para controlar o armazenamento: até 50 conversas, 100 mensagens por conversa e 2 500 caracteres de memória.

## Limites importantes

- A persistência do histórico, memória, indicadores económicos e registo de faturas é **local ao dispositivo**, cifrada e separada por conta/restaurante. O botão «Histórico» permanece visível no chat; permite pesquisar, reabrir e eliminar conversas. Não sincroniza entre telemóveis. A sincronização cloud exige novas tabelas, políticas RLS e uma reconciliação aprovada do histórico de migrações do projeto Supabase.
- O aspeto de chat não significa que exista já um modelo ChatGPT, Gemini ou Grok ligado. As respostas continuam a usar o motor local determinístico existente. Pesquisa na Internet, respostas generativas e chamadas a ferramentas ainda não estão ativas.
- O gestor económico mantém os cálculos locais, mas os campos e relatórios passam a persistir cifrados no dispositivo, separados por conta/restaurante. A informação não é sincronizada entre dispositivos nem enviada para um serviço externo.
- O assistente tem um arquivo local de faturas e notas de crédito. O proprietário pode registar fornecedor, número, data, base tributável, IVA, total e notas/linhas transcritas; pesquisar o histórico; abrir detalhes; importar um anexo PDF, XML, TXT, JPEG, PNG ou WebP até 12 MB; exportar o original ou eliminá-lo. Os anexos ficam cifrados com AES-GCM numa pasta privada da aplicação e a chave permanece no Android Keystore. O agente calcula totais registados, notas de crédito, IVA líquido e totais por fornecedor. Os valores têm de ser conferidos e introduzidos pelo utilizador. Não existe OCR/leitura automática de PDFs, sincronização cloud nem integração com software de faturação.

## Segurança

- A memória e o histórico são separados pelo ID da conta e do restaurante.
- A chave AES-GCM não é armazenada nas preferências da aplicação; é criada e mantida pelo Android Keystore.
- A aplicação não envia mensagens nem documentos para um fornecedor de IA nesta fase.
- Não foram feitas alterações à base de dados alojada nem às suas migrações. A reconciliação documentada em `supabase/MIGRATION-RECONCILIATION-2026-11-02.md` continua a bloquear alterações de esquema/deploy até revisão.

## Testes

Foi acrescentado um teste instrumentado Android para:
- Persistência de conversas e memória entre instâncias da store.
- Separação entre âmbitos de conta.
- Pesquisa de conversas.
- Eliminação de conversa sem apagar a memória.
- Rejeição de respostas sem uma conversa iniciada pelo utilizador.

A compilação, testes instrumentados e CI têm de passar antes de considerar esta alteração pronta para integrar ou distribuir.
