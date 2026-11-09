# Google Play Data Safety — matriz de preparação

**Estado: RASCUNHO INTERNO. Não copiar para a Play Console sem validar a versão binária final, os SDKs, a configuração cloud e o fluxo real de dados.**

Esta matriz ajuda a preparar a declaração Data safety. Não substitui a resposta oficial da Play Console nem determina por si só a classificação jurídica ou as categorias exatas do formulário.

## Contexto da versão analisada

- StudioMesa Android 1.5.0, cinco variantes de plano/tipo, minSdk 24 e targetSdk 36.
- Backend Supabase na região EU West 1.
- O modelo generativo LFM2.5-1.2B-Instruct corre localmente depois de ser descarregado.
- O modelo pode acionar uma pesquisa genérica na Wikipédia; só essa consulta pública é enviada à Wikimedia.
- A sincronização do histórico/Memória existe no backend Supabase, é limitada ao proprietário por RLS e passa a ficar desligada por defeito, exigindo ativação explícita na aplicação. A cópia cloud não é cifrada ponta a ponta.
- O OCR de PDFs/imagens é local. Quando o proprietário arquiva a fatura no servidor, o documento original e os metadados são enviados para a tabela de faturas e para o bucket privado `studiomesa-invoices`, com políticas de acesso limitadas ao proprietário.

## Matriz preliminar

| Dados / categoria candidata | Finalidade | Onde é processado | Transmissão / destinatário | Estado a declarar |
|---|---|---|---|---|
| Email, ID de conta e dados de sessão | Registo, login, recuperação, permissões e eliminação | Android + Supabase Auth | Supabase Auth por HTTPS; os tokens são usados para chamadas autenticadas | Confirmar com a versão e o fornecedor de email finais |
| Nome de utilizador/cliente e contacto de reserva | Identificação operacional e gestão de reservas | Android + Supabase, quando autenticado | Restaurante associado e backend, sujeito às permissões | Confirmar se o contacto é obrigatório ou opcional e as categorias da Play |
| Dados do restaurante, equipa e permissões | Gestão e controlo de acesso | Android + Supabase | Membros autorizados do mesmo restaurante e backend | Recolha cloud existente para operações implementadas |
| Pedidos, mesas, carta, preços, custos, stock e movimentos | Operação e gestão | Android + Supabase | Backend; alguns dados disponíveis à equipa autorizada | Rever quais campos são pessoais, comerciais e partilhados com o cliente |
| Reservas, nome, número de pessoas, hora, mesa, notas e contacto opcional | Pedidos/gestão de reserva | Android + Supabase | Backend e equipa autorizada do restaurante | Fluxo implementado; validar E2E e retenção |
| Pontos, cartões, QR e recompensas | Fidelização | Android + Supabase, conforme a funcionalidade ativa | Backend e equipa autorizada; o cliente vê apenas os dados da própria associação | Conferir as funcionalidades efetivamente incluídas no binário/plano |
| Perguntas, respostas, títulos e Memória da IA | Histórico e assistência | Android local cifrado; cloud sync opcional por ativação explícita do proprietário | Inferência no modelo local; quando o proprietário ativa cloud sync, o conteúdo é enviado ao Supabase e não é E2E cifrado | Validar o controlo da sincronização na versão binária final |
| PDFs/imagens, OCR e metadados de faturas | Arquivo e análise de faturas | OCR no Android; metadados cifrados localmente; arquivo cloud quando o proprietário o solicita | Documento original e metadados no Supabase; bucket privado e políticas de Storage limitadas ao proprietário | Confirmar o fluxo de aprovação, upload, leitura e eliminação com contas descartáveis |
| Consultas genéricas da Wikipédia | Pesquisa pública a pedido do utilizador | Android + Wikimedia | Apenas a consulta pública curta; não incluir dados privados de restaurante | Confirmar logs/termos do fornecedor e classificação |
| Modelo de IA (ficheiro INT4) | Instalar capacidade generativa offline | Android + alojamento público do modelo | Download do ficheiro de modelo; sem prompts/histórico para o fornecedor | Confirmar a URL/hash na release final |
| Dados técnicos/logs de segurança | Segurança, diagnóstico e prevenção de abuso | Android + fornecedores do backend | Depende dos logs configurados no Supabase/serviços | Inventariar logs, retenção e fornecedores antes de declarar |

## Perguntas que têm de ser respondidas na Play Console

- [ ] Para cada categoria, determinar se a app recolhe, partilha ou apenas processa localmente os dados no sentido específico das definições do formulário.
- [ ] Distinguir partilha com prestador de serviços (Supabase, email, Wikimedia, hosting do modelo) de partilha com o restaurante destinatário e de partilha pública.
- [ ] Confirmar se a app transmite dados pessoais/sensíveis fora do dispositivo; não marcar automaticamente “não recolhe”.
- [ ] Confirmar se os dados são cifrados em trânsito em todas as ligações e se existe cifragem em repouso em cada local aplicável. A cifragem AES-GCM de alguns arquivos locais não significa que todos os dados estejam cifrados.
- [ ] Confirmar mecanismo de eliminação na app e página web, retenção em backups e tempo de propagação.
- [ ] Confirmar público-alvo, idade mínima, conteúdo, permissões sensíveis (a app declara INTERNET e CAMERA; a câmara é opcional) e classificação etária.
- [ ] Inspecionar o AAB final e SDKs transitivos para confirmar analytics, crash reporting, publicidade, SDKs de terceiros e comportamento de rede.
- [ ] Confirmar que o conteúdo da política de privacidade coincide exatamente com os dados recolhidos/partilhados pela release e a configuração Supabase ativa.

## Limitações conhecidas

- Não foi confirmado um fornecedor SMTP de produção.
- O fluxo de eliminação de conta está implementado no código, mas o fluxo completo por email/PKCE e a eliminação com contas descartáveis continuam sem validação E2E.
- A sincronização do histórico/Memória requer ativação explícita; o arquivo cloud de faturas é iniciado pelo proprietário. Validar ambos com contas descartáveis antes de publicar.
- Não existe gateway de pagamento, analytics ou publicidade comercial configurada no código analisado; confirmar dependências transitivas e configuração do release final antes de marcar qualquer resposta como definitiva.
- Os prazos de retenção, fundamentos jurídicos, subprocessadores e transferências internacionais continuam por definir pelo responsável legal.

## Referências oficiais

- Google Play — User Data e Data safety: https://support.google.com/googleplay/android-developer/answer/10144311
- Google Play — eliminação de contas: https://support.google.com/googleplay/android-developer/answer/13327111
- Google Play — requisitos Target API: https://support.google.com/googleplay/android-developer/answer/11926878

**Não submeter esta matriz como declaração final.**