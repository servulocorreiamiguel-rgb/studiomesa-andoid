# StudioMesa — demonstração de gestão de restaurante

Protótipo Android de gestão de restaurante com identidade visual própria e dados de demonstração. Não é uma solução certificada para operação comercial. Antes de um restaurante o usar em serviço real, é obrigatório testar os fluxos, configurar as regras do restaurante e confirmar os requisitos legais e operacionais.

## Estrutura dos planos de demonstração

- **Essencial — 890 € (referência):** aplicação de gestão do restaurante com mesas, carta, pedidos e fidelização.
- **Profissional — 1 490 € (referência):** inclui também a calculadora de preço indicativo e a aplicação separada de cliente.
- **Premium — 1 690 € (referência):** inclui as duas aplicações, calculadora e indicadores operacionais.

Estes valores são propostas de referência para avaliação comercial, não pagamentos configurados nem prova de que o produto está pronto para venda.

## O que está implementado

- **Mesas:** 12 mesas iniciais por restaurante, com possibilidade de adicionar até 100 números de mesa e definir zona e capacidade. O estado livre/ocupada fica partilhado pela equipa.
- **Pedidos:** criação de pedidos por mesa/artigo, fluxo Aberto → Em preparação → Pronto → Concluído e cancelamento com confirmação. O servidor regista a criação e cada mudança de estado no histórico, com data/hora e utilizador.
- **Vista de cozinha (todos os planos de gestão):** fila dedicada de pedidos ativos, com mesa, artigo, curso, notas da cozinha, estado e tempo de espera; permite avançar o pedido entre as fases. A fila pode ser atualizada manualmente, mas ainda não usa subscrições em tempo real.
- **Carta:** artigos editáveis com categoria, descrição, preço de venda, custo interno e informação declarada de alergénios. Os custos e margens permanecem na aplicação de gestão; o cartão de cliente só recebe nome, categoria, descrição, preço e nota de alergénios.
- **Dados partilhados:** quando existe sessão e restaurante configurado, mesas, carta e pedidos são guardados no Supabase e podem ser atualizados em vários dispositivos autenticados. A sincronização é feita ao abrir a aplicação, após operações e manualmente; ainda não há atualização em tempo real por subscrição.
- **Fidelização partilhada:** adesão de clientes com código, saldo de pontos centralizado e registo das alterações efetuadas pelo restaurante. O código de clientes fica num esquema privado e o proprietário pode renová-lo.
- **Reservas (Profissional e Premium):** registo de nome, contacto opcional, data/hora, duração, número de pessoas, mesa opcional, notas e estados (pendente, confirmada, cancelada, concluída, não compareceu). A função do servidor verifica que a mesa pertence ao restaurante, tem capacidade suficiente e não tem outra reserva pendente/confirmada sobreposta no horário. A criação de reservas com mesa atribuída bloqueia a linha da mesa durante a verificação de conflitos. Os contactos e notas são mantidos em memória durante a utilização e não são persistidos nas preferências locais do Android.
- **Stock e inventário (Premium):** artigos com unidade, quantidade, limite mínimo, alertas visuais de stock baixo/esgotado, entradas/saídas, histórico de movimentos com saldo após cada movimento e autor registado. As alterações são feitas por funções verificadas no servidor.
- **Aplicação de cliente:** login, recuperação de palavra-passe por email, consulta de saldo, código de cartão e carta publicada pelo restaurante. A aplicação não recebe os custos ou margens internas. Os alergénios são declarações introduzidas pelo restaurante e devem ser confirmados com a equipa; não são uma garantia médica.
- **Calculadora de preço indicativo** e **indicadores operacionais** nos planos correspondentes.
- **Identidade personalizável:** o nome do restaurante aparece depois de configurar a respetiva conta.
- **Acesso de equipa:** código separado para juntar contas de funcionários, limitado a um restaurante por conta. O proprietário pode renovar o código; os códigos não são apresentados a clientes.
- **Gestão de equipa do proprietário:** consulta da lista de membros e remoção do acesso de um funcionário. A remoção exige autorização do proprietário e roda o código partilhado de adesão, para que o código antigo não volte a ser usado. As permissões ainda são gerais para todos os funcionários; não existe matriz de permissões por função.
- **Gestão da carta:** editar nome, categoria, descrição, preço, custo e alergénios declarados ou arquivar um artigo sem apagar artigos de pedidos antigos.
- **Histórico de pedidos no servidor:** registo de criação e alterações de estado com hora e utilizador, acessível apenas à equipa autenticada.

## O que ainda falta antes da venda ou utilização em serviço real

- Testes ponta a ponta em telemóveis reais, incluindo vários dispositivos, utilizadores, perda de internet, sessões expiradas e erros de servidor. A compilação de um APK não substitui esses testes.
- Regras comerciais da fidelização: acumulação automática, recompensas configuráveis, resgates, limites e prevenção de fraude. Neste momento, o saldo é ajustado manualmente pelo restaurante.
- Reservas: o fluxo inicial de criação, estados e bloqueio de conflitos por mesa está implementado no código e na migração; ainda falta teste ponta a ponta, edição de reservas, vista de calendário/turnos, lembretes automáticos, depósitos e regras de cancelamento.
- Stock: o inventário partilhado, entradas/saídas e histórico de movimentos está implementado no código e na migração; ainda falta teste ponta a ponta, avisos automáticos, consumo de ingredientes por receita, fornecedores, encomendas e inventário por armazém.
- Gestão avançada de equipas ainda incompleta: permissões granulares por função, suspensão temporária, horários/turnos e registos administrativos completos. A remoção de funcionários e a renovação automática do código de adesão já estão implementadas, mas a revogação não é propagada em tempo real a um ecrã que já esteja aberto; a aplicação limpa a cache de dados remotos numa nova sessão e quando o servidor confirma que o utilizador já não pertence ao restaurante.
- Integração com POS/caixa, pagamentos, impressoras de cozinha, recibos ou faturação.
- Cópias de segurança e procedimento testado de recuperação, monitorização, exportação/eliminação de dados e política de conservação de dados. O histórico de pedidos está implementado; ainda falta registar todas as ações de gestão num trilho de auditoria completo.
- Gestão avançada da carta ainda incompleta: fotografias, disponibilidade por horário, tradução de menus, histórico editorial e validação sistemática de alergénios. Os campos de categoria, descrição e alergénios já existem, mas precisam de revisão pelo restaurante.
- Testes de privacidade, segurança e proteção de dados; revisão independente da configuração de produção. A proteção de palavras-passe comprometidas no Supabase ainda não está ativada.
- Credenciais próprias de OAuth para Google/Microsoft, caso se queira disponibilizar esses métodos de login. O código Android usa PKCE para OAuth, registo e recuperação e guarda o verificador cifrado no Android Keystore; estes fluxos ainda precisam de testes ponta a ponta num dispositivo real. É necessário autorizar o URL de retorno e configurar um fornecedor SMTP para o envio real dos emails.
- Compilação de release, assinatura de lançamento controlada pelo proprietário, testes de compatibilidade e preparação da eventual publicação na Google Play. Os APKs gerados atualmente são de demonstração/debug.
- A configuração de compilação usa compileSdk/targetSdk 36, Android Gradle Plugin 8.10.0 e Gradle 8.11.1. O workflow compila os cinco APKs de demonstração/debug, executa testes unitários para o parser dos parâmetros de autenticação e Android Lint. Mesmo com CI aprovado, a app continua a exigir testes ponta a ponta; os APKs atuais não são versões comerciais assinadas.

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

Os APKs de demonstração não devem ser tratados como versão comercial final nem apresentados como testados em produção.