# StudioMesa chat, memória e agente financeiro — estado da integração generativa (03/11/2026)

## Integração generativa acrescentada

- O Android tem agora um modo online opcional, ativado por consentimento explícito e guardado por conta/restaurante.
- Com o modo online ativo, a aplicação envia a mensagem atual, até 16 turnos anteriores e a Memória local ao endpoint autenticado `studiomesa-assistant`.
- O Android chama o endpoint através de `SupabaseApi.askStudioMesaAssistant`; o token Supabase do utilizador é enviado no cabeçalho Authorization. A chave do fornecedor nunca é embutida no APK.
- O endpoint Edge Function exige JWT válido, volta a confirmar o utilizador no Supabase Auth e valida a associação à tabela `restaurant_staff` através do REST com RLS antes de chamar o modelo.
- O endpoint usa o alias `gemini-flash-latest` por defeito, com raciocínio médio nas perguntas normais e nível alto nas tarefas que parecem exigir análise longa. O modelo pode ser substituído através do segredo `GEMINI_MODEL`.
- A chave `GEMINI_API_KEY` tem de existir nos secrets das Edge Functions do Supabase. Sem ela, o endpoint devolve erro 503 e o Android mostra a mensagem de configuração em falta. Não guardar a chave no GitHub, no código Android ou em mensagens.
- A pesquisa web via `google_search` está desligada por defeito. Só fica ativa quando o responsável configura `GEMINI_ENABLE_SEARCH=true`; o Google cobra por pesquisa no Gemini 3, pelo que essa opção requer decisão explícita sobre custos. Quando ativa, o endpoint devolve as fontes obtidas pelo modelo.
- O pedido é stateless no fornecedor: a aplicação envia o histórico necessário em cada pedido e o endpoint devolve `persistence: none`. O histórico do produto continua cifrado localmente com AES-GCM e Android Keystore.
- O modo local determinístico continua disponível quando o modo online está desligado.

## O que esta integração ainda não faz

- O histórico/memória ainda não sincroniza entre dispositivos; permanece cifrado no telemóvel.
- A IA ainda não recebe automaticamente dados da carta, pedidos, stock, reservas ou faturas. Só recebe a pergunta, os turnos recentes e a Memória selecionada/guardada.
- Não existe arquivo de faturas contabilísticas nem extração de IVA/valores fiscais; a integração de documentos permanece pendente.
- Não há ferramentas de ação ligadas ao modelo. A IA não pode gravar preços, criar pedidos, alterar stock, reservas ou permissões.
- A configuração da chave Gemini e o teste de ponta a ponta do endpoint com uma conta de proprietário e funcionário ainda são requisitos antes de declarar o modo online operacional.
- A integração não é equivalente ao ChatGPT Plus nem inclui o uso da API OpenAI. O modelo Gemini é um fornecedor externo distinto.

---

# StudioMesa chat, memória e agente financeiro — 03/11/2026

## O que esta alteração faz

- A página Assistente passa a abrir numa interface de conversa com mensagens alinhadas, caixa de escrita, envio, botão de nova conversa e pesquisa do histórico.
- Cada conversa tem título, data, mensagens e pesquisa no respetivo conteúdo. É possível abrir uma conversa antiga e eliminá-la.
- A memória é editável e pode ser consultada no chat. A aplicação inclui essa nota em respostas locais relevantes.
- O histórico e a memória são cifrados com AES-GCM; a chave é mantida no Android Keystore. O armazenamento é privado da aplicação e separado por utilizador e restaurante.
- O histórico tem limites para controlar o armazenamento: até 50 conversas, 100 mensagens por conversa e 2 500 caracteres de memória.

## Limites importantes

- A persistência implementada nesta fase é **local ao dispositivo**. Não sincroniza entre telemóveis. A sincronização cloud exige novas tabelas, políticas RLS e uma reconciliação aprovada do histórico de migrações do projeto Supabase.
- O aspeto de chat começou por usar apenas o motor local determinístico. A integração generativa foi acrescentada posteriormente e está descrita na secção de estado acima; depende da configuração da chave do fornecedor e de testes ponta a ponta.
- O agente financeiro mantém os cálculos locais e manuais existentes, agora acessíveis através de Ferramentas financeiras.
- O projeto Supabase alojado não tem arquivo de faturas contabilísticas nem integração com software de faturação. O QR de fatura usado no fluxo de fidelização não é um arquivo de documentos. A análise de faturas fica pendente de escolher o programa do restaurante ou um formato de importação seguro, definir permissões exclusivas do proprietário e testar a extração dos dados.

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
