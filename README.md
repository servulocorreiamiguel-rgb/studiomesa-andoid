# StudioMesa — demonstração de gestão de restaurante

Protótipo Android de gestão de restaurante com identidade visual própria e dados de demonstração. Não é uma solução certificada para operação comercial. Antes de um restaurante o usar em serviço real, é obrigatório testar os fluxos, configurar as regras do restaurante e confirmar os requisitos legais e operacionais.

## Estrutura dos planos de demonstração

- **Essencial — 890 € (referência):** aplicação de gestão do restaurante com mesas, carta, pedidos e fidelização.
- **Profissional — 1 490 € (referência):** inclui também a calculadora de preço indicativo e a aplicação separada de cliente.
- **Premium — 1 690 € (referência):** inclui as duas aplicações, calculadora e indicadores operacionais.

Estes valores são propostas de referência para avaliação comercial, não pagamentos configurados nem prova de que o produto está pronto para venda.

## O que está implementado

- **Mesas:** 12 mesas iniciais por restaurante, com possibilidade de adicionar até 100 números de mesa e definir zona e capacidade. O estado livre/ocupada fica partilhado pela equipa.
- **Pedidos:** criação de pedidos por mesa/artigo, fluxo Aberto → Em preparação → Pronto → Concluído e cancelamento com confirmação. Os novos pedidos ligados à carta guardam o UUID do artigo, o nome/preço e o custo interno conhecido no momento da criação; editar ou arquivar o artigo não reescreve o pedido. Os pedidos antigos continuam válidos sem associação retroativa por nome. O servidor regista a criação e cada mudança de estado no histórico, com data/hora e utilizador. As tentativas de gravação usam um UUID por conteúdo e verificam também os campos fotografados após uma falha, evitando aceitar uma repetição com dados diferentes.
- **Vista de cozinha (todos os planos de gestão):** fila dedicada de pedidos ativos, com mesa, artigo, curso, notas da cozinha, estado e tempo de espera; permite avançar o pedido entre as fases. A fila consulta automaticamente o servidor a cada 15 segundos enquanto esta secção está aberta e a aplicação está em primeiro plano; o botão de atualização manual continua disponível. É atualização periódica por consulta, não uma subscrição em tempo real, e não funciona em segundo plano.
- **Carta:** artigos editáveis com categoria, descrição, preço de venda, custo interno e informação declarada de alergénios. Os custos e margens permanecem na aplicação de gestão; o cartão de cliente só recebe nome, categoria, descrição, preço e nota de alergénios.
- **Dados partilhados:** quando existe sessão e restaurante configurado, mesas, carta e pedidos são guardados no Supabase e podem ser atualizados em vários dispositivos autenticados. A sincronização é feita ao abrir a aplicação, após operações e manualmente; ainda não há atualização em tempo real por subscrição.
- **Fidelização partilhada:** adesão de clientes com código, saldo de pontos centralizado e registo das alterações efetuadas pelo restaurante. O código de clientes fica num esquema privado e o proprietário pode renová-lo. As alterações de pontos usam um identificador de tentativa para que um reenvio idêntico após perda da resposta não volte a somar ou descontar pontos.
- **Reservas (Profissional e Premium):** registo de nome, contacto opcional, data/hora, duração, número de pessoas, mesa opcional, notas e estados (pendente, confirmada, cancelada, concluída, não compareceu). A função do servidor verifica que a mesa pertence ao restaurante, tem capacidade suficiente e não tem outra reserva pendente/confirmada sobreposta no horário. A criação de reservas com mesa atribuída bloqueia a linha da mesa durante a verificação de conflitos. As tentativas repetidas com a mesma chave e os mesmos dados devolvem a reserva existente, para evitar duplicados após falhas de rede. Os contactos e notas são mantidos em memória durante a utilização e não são persistidos nas preferências locais do Android.
- **Stock e inventário (Premium):** artigos com unidade, quantidade, limite mínimo, custo de compra por unidade opcional, alertas visuais de stock baixo/esgotado, entradas/saídas e histórico de movimentos. Quantidades e movimentos aceitam no máximo três casas decimais; custos unitários aceitam quatro. O custo de compra é introduzido manualmente em euros por kg, litro ou unidade. O inventário permite registar desperdício por artigo e motivo, descontando o stock através de um movimento idempotente. Cada novo movimento guarda uma cópia do custo unitário em vigor naquele momento; assim, alterar o custo atual não reescreve a valorização histórica das perdas. O ecrã Stock apresenta uma estimativa do valor das existências com custo conhecido e o histórico calcula o custo das perdas novas. Movimentos antigos sem custo guardado continuam identificados como desconhecidos, sem estimativas retroativas. O ecrã Stock consulta o servidor automaticamente a cada 30 segundos enquanto está aberto e a aplicação está em primeiro plano; mantém a última leitura válida em caso de falha e conserva a atualização manual. A valorização é indicativa, não é método contabilístico FIFO/custo médio nem substitui faturas de compra.
- **Aplicação de cliente:** login, recuperação de palavra-passe por email, consulta de saldo, código de cartão e carta publicada pelo restaurante. A aplicação não recebe os custos ou margens internas. Os alergénios são declarações introduzidas pelo restaurante e devem ser confirmados com a equipa; não são uma garantia médica.
- **Calculadora de preço indicativo** e **indicadores operacionais** nos planos correspondentes.
- **Consultor de preços da carta (Premium):** compara o custo unitário registado com o preço de venda e um objetivo configurável. A análise inicial é só de leitura; a aplicação individual de aumentos pode ser ativada pelo proprietário, fica desligada por defeito e exige confirmação por artigo. O servidor volta a validar o proprietário, os dados atuais e o preço recomendado, rejeita reduções e regista cada aplicação numa auditoria privada. Não calcula automaticamente IVA/custos indiretos nem garante rentabilidade. Testes ponta a ponta com contas de proprietário/funcionário e Android físico continuam pendentes.
- **Assistente StudioMesa (gestão, todos os planos):** análise local gratuita de custos/preços, pedidos e, quando disponíveis, stock e reservas. No Premium, lê o custo de compra por unidade do inventário, responde ao valor estimado do stock conhecido, identifica artigos sem custo e estima quanto seria necessário para voltar ao limite mínimo registado. Não confunde estimativa de reposição com uma ordem de compra e só analisa o inventário após uma leitura bem-sucedida com menos de cinco minutos; uma cache antiga exige nova atualização em Stock. Responde por regras determinísticas; não envia informação para uma API, não é um LLM generativo e não altera registos.
- **Assistente aprofundado:** inclui resumo da operação, triagem de engenharia da carta (Estrela/Popular com margem baixa/Puzzle/Artigo a rever), análise dos custos unitários atuais e rascunhos de marketing com nome, descrição e preço registados. A engenharia exige pelo menos cinco unidades concluídas correspondentes aos artigos atuais e é uma triagem inicial, não prova estatística para decisões definitivas.
**Importante sobre a designação «IA»:** a implementação atual é um assistente local determinístico, não um LLM generativo. É útil para triagem e cálculos claros; não deve ser apresentada como aconselhamento financeiro ou previsão automática. Se vier a ser adicionado um modelo generativo online, isso terá de ser uma fase separada com escolha explícita de fornecedor, custos, consentimento e regras de privacidade.
- **Identidade personalizável:** o nome do restaurante aparece depois de configurar a respetiva conta.
- **Acesso de equipa:** código separado para juntar contas de funcionários, limitado a um restaurante por conta. O proprietário pode renovar o código; os códigos não são apresentados a clientes.
- **Gestão de equipa do proprietário:** consulta da lista de membros e remoção do acesso de um funcionário. A remoção exige autorização do proprietário e roda o código partilhado de adesão, para que o código antigo não volte a ser usado. As permissões ainda são gerais para todos os funcionários; não existe matriz de permissões por função.
- **Gestão da carta:** editar nome, categoria, descrição, preço, custo e alergénios declarados ou arquivar um artigo sem apagar artigos de pedidos antigos.
- **Histórico de pedidos no servidor:** registo de criação e alterações de estado com hora e utilizador, acessível apenas à equipa autenticada.

## Assistente StudioMesa

A primeira versão está documentada em `AI-ASSISTANT-2026-10-27.md`. Funciona sem chave de API e sem chamadas externas. O cálculo de preço usa uma referência ilustrativa de percentagem de custo, não uma recomendação comercial universal. O assistente local não altera dados por conta própria; a função separada de aplicar aumentos de preços exige ativação explícita do proprietário, confirmação individual e validação/auditoria no servidor.

A revisão de 28/10/2026 acrescenta uma área **Gestor económico · opcional**: estimativa de resultado e ponto de equilíbrio, indicadores de custo alimentar/pessoal, projeção manual de tesouraria, média simples de vendas semanais, custo de receitas por dose e quantidade teórica de reposição. Começa desativada; todos os valores são introduzidos manualmente e ficam apenas em memória durante a sessão, sem persistência ou sincronização. Não é contabilidade certificada, não faz previsão avançada nem emite encomendas. Fórmulas, hipóteses e próximos passos estão em `ECONOMIC-MANAGER-2026-10-28.md`. As novas funcionalidades só devem ser consideradas integradas após a revisão e validação da CI.

## O que ainda falta antes da venda ou utilização em serviço real

- Testes ponta a ponta em telemóveis reais, incluindo vários dispositivos, utilizadores, perda de internet, sessões expiradas e erros de servidor. A compilação de um APK não substitui esses testes.
- Regras comerciais da fidelização: acumulação automática, recompensas configuráveis, resgates, limites e prevenção de fraude. Neste momento, o saldo é ajustado manualmente pelo restaurante.
- Reservas: o fluxo inicial de criação, estados e bloqueio de conflitos por mesa está implementado no código e na migração; ainda falta teste ponta a ponta, edição de reservas, vista de calendário/turnos, lembretes automáticos, depósitos e regras de cancelamento.
- Stock: o inventário partilhado, entradas/saídas e histórico de movimentos está implementado no código e na migração; ainda falta teste ponta a ponta, avisos automáticos, consumo de ingredientes por receita, fornecedores, encomendas e inventário por armazém.
- Gestão avançada de equipas ainda incompleta: permissões granulares por função, suspensão temporária, horários/turnos e registos administrativos completos. A remoção de funcionários e a renovação automática do código de adesão já estão implementadas, mas a revogação não é propagada em tempo real a um ecrã que já esteja aberto; a aplicação limpa a cache de dados remotos numa nova sessão e quando o servidor confirma que o utilizador já não pertence ao restaurante.
- Integração com POS/caixa, pagamentos, impressoras de cozinha, recibos ou faturação.
- Cópias de segurança e procedimento testado de recuperação, monitorização, exportação e conservação de dados. O fluxo de eliminação de conta está implementado no Android e no Supabase, mas os casos de conta pessoal/funcionário/proprietário continuam pendentes de testes ponta a ponta com contas descartáveis.
- Página web externa para pedir eliminação de conta publicada em `https://svwvkfsyevuyabelrtjk.supabase.co/functions/v1/account-deletion-web`; o fluxo de email ainda exige URL autorizado no Supabase e teste ponta a ponta.
- **Bloqueio de segurança no email de eliminação:** a página usa `signInWithOtp` com `shouldCreateUser=false`. A [issue pública do Supabase Auth #1547](https://github.com/supabase/auth/issues/1547) documenta respostas HTTP/tempos que podem revelar se um email existe. A mensagem neutra da página não corrige a resposta direta da API. Testar com contas descartáveis e avaliar uma mitigação antes de produção; não considerar a proteção contra enumeração concluída.
- Política de privacidade pública e formulário Data safety da Google Play ainda por preparar e validar. O histórico de pedidos está implementado; ainda falta registar todas as ações de gestão num trilho de auditoria completo.
- Gestão avançada da carta ainda incompleta: fotografias, disponibilidade por horário, tradução de menus, histórico editorial e validação sistemática de alergénios. Os campos de categoria, descrição e alergénios já existem, mas precisam de revisão pelo restaurante.
- Testes de privacidade, segurança e proteção de dados; revisão independente da configuração de produção. A proteção de palavras-passe comprometidas no Supabase ainda não está ativada.
- Credenciais próprias de OAuth para Google/Microsoft, caso se queira disponibilizar esses métodos de login. O código Android usa PKCE para OAuth, registo e recuperação e guarda o verificador cifrado no Android Keystore; estes fluxos ainda precisam de testes ponta a ponta num dispositivo real. É necessário autorizar o URL de retorno e configurar um fornecedor SMTP para o envio real dos emails.
- Configuração de release comercial assinada e workflow manual para gerar AABs com uma chave privada guardada em GitHub Actions Secrets. Ainda é necessário escolher o identificador de pacote definitivo, configurar a chave, executar o workflow e validar as variantes em dispositivos reais; o workflow não publica automaticamente na Google Play.
- A configuração de compilação usa compileSdk/targetSdk 36, Android Gradle Plugin 8.10.0 e Gradle 8.11.1. O workflow compila os cinco APKs de demonstração/debug, executa testes unitários nas cinco variantes de distribuição e Android Lint. Mesmo com CI aprovado, a app continua a exigir testes ponta a ponta; os APKs atuais não são versões comerciais assinadas.

## Configuração técnica

O backend usa Supabase Auth e PostgreSQL com Row Level Security. Os códigos de adesão dos clientes ficam num esquema privado, separados das tabelas expostas à aplicação. As tabelas de mesas, carta e pedidos usam políticas de segurança por restaurante. Algumas funções protegidas usam SECURITY DEFINER para executar operações verificadas do lado da base de dados; as regras devem continuar a ser auditadas.

### Compilar os cinco APKs de demonstração

O workflow **Actions → Build StudioMesa Android APK** gera:

- `app-essencial-restaurante-debug.apk`
- `app-profissional-restaurante-debug.apk`
- `app-profissional-cliente-debug.apk`
- `app-premium-restaurante-debug.apk`
- `app-premium-cliente-debug.apk`

Com Java 17, Android Gradle Plugin 8.10.0, Gradle 8.11.1 e Android SDK 36:

```bash
gradle --no-daemon assembleEssencialRestauranteDebug assembleProfissionalRestauranteDebug assembleProfissionalClienteDebug assemblePremiumRestauranteDebug assemblePremiumClienteDebug
```

Os APKs de demonstração não devem ser tratados como versão comercial final nem apresentados como testados em produção. Para preparar bundles comerciais assinados, segue [`RELEASE-SETUP.md`](RELEASE-SETUP.md). O workflow manual cria cinco AABs assinados para a Google Play e cinco APKs assinados para instalação direta; não publica automaticamente na Google Play. Os identificadores de pacote finais têm de ser escolhidos antes da publicação.