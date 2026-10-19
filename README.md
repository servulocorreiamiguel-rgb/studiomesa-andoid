# StudioMesa — demonstração de gestão de restaurante

Protótipo Android de gestão de restaurante com identidade visual própria e dados de demonstração. Não é uma solução certificada para operação comercial. Antes de um restaurante o usar em serviço real, é obrigatório testar os fluxos, configurar as regras do restaurante e confirmar os requisitos legais e operacionais.

## Estrutura dos planos de demonstração

- **Essencial — 890 € (referência):** aplicação de gestão do restaurante com mesas, carta, pedidos e fidelização.
- **Profissional — 1 490 € (referência):** inclui também a calculadora de preço indicativo e a aplicação separada de cliente.
- **Premium — 1 690 € (referência):** inclui as duas aplicações, calculadora e indicadores operacionais.

Estes valores são propostas de referência para avaliação comercial, não pagamentos configurados nem prova de que o produto está pronto para venda.

## O que está implementado

- **Mesas:** 12 mesas iniciais por restaurante, com estado livre/ocupada.
- **Pedidos:** criação de pedidos por mesa/artigo e fluxo de estado Aberto → Em preparação → Pronto → Concluído.
- **Carta e custos:** artigos, preço de venda e custo estimado; os custos permanecem na aplicação de gestão.
- **Dados partilhados:** quando existe sessão e restaurante configurado, mesas, carta e pedidos são guardados no Supabase e podem ser atualizados em vários dispositivos autenticados. A sincronização é feita ao abrir a aplicação, após operações e manualmente; ainda não há atualização em tempo real por subscrição.
- **Fidelização partilhada:** adesão de clientes com código, saldo de pontos centralizado e registo das alterações efetuadas pelo restaurante.
- **Aplicação de cliente:** login/recuperação de adesão, consulta de saldo, código de cartão e carta com nomes e preços publicados pelo restaurante. A aplicação de cliente não recebe os custos ou margens internas.
- **Calculadora de preço indicativo** e **indicadores operacionais** nos planos correspondentes.
- **Identidade personalizável:** o nome do restaurante aparece depois de configurar a respetiva conta.

## O que ainda falta antes da venda ou utilização em serviço real

- Testes ponta a ponta em telemóveis reais, incluindo vários dispositivos, utilizadores, perda de internet, sessões expiradas e erros de servidor. A compilação de um APK não substitui esses testes.
- Regras comerciais da fidelização: acumulação automática, recompensas configuráveis, resgates, limites e prevenção de fraude. Neste momento, o saldo é ajustado manualmente pelo restaurante.
- Gestão de equipas e permissões por função, incluindo um processo seguro para convidar trabalhadores.
- Integração com POS/caixa, pagamentos, impressoras de cozinha, recibos ou faturação.
- Cópias de segurança e procedimento de recuperação, monitorização, registos de auditoria para as ações de gestão e política de conservação/eliminação de dados.
- Gestão completa da carta (categorias, descrições, fotografias, alergénios, disponibilidade por horário, idiomas e validação editorial).
- Testes de privacidade, segurança e proteção de dados; revisão independente da configuração de produção. A proteção de palavras-passe comprometidas no Supabase ainda não está ativada.
- Credenciais próprias de OAuth para Google/Microsoft, caso se queira disponibilizar esses métodos de login. Para emails de confirmação fora do teste, é necessário configurar um fornecedor SMTP apropriado.
- Compilação de release, assinatura de lançamento controlada pelo proprietário, testes de compatibilidade e preparação da eventual publicação na Google Play. Os APKs gerados atualmente são de demonstração/debug.

## Configuração técnica

O backend usa Supabase Auth e PostgreSQL com Row Level Security. Os códigos de adesão dos clientes ficam num esquema privado, separados das tabelas expostas à aplicação. As tabelas de mesas, carta e pedidos usam políticas de segurança por restaurante. Algumas funções protegidas usam SECURITY DEFINER para executar operações verificadas do lado da base de dados; as regras devem continuar a ser auditadas.

### Compilar os cinco APKs de demonstração

O workflow **Actions → Build StudioMesa Android APK** gera:

- `app-essencial-restaurante-debug.apk`
- `app-profissional-restaurante-debug.apk`
- `app-profissional-cliente-debug.apk`
- `app-premium-restaurante-debug.apk`
- `app-premium-cliente-debug.apk`

Com Java 17, Gradle 8.9 e Android SDK:

```bash
gradle --no-daemon assembleEssencialRestauranteDebug assembleProfissionalRestauranteDebug assembleProfissionalClienteDebug assemblePremiumRestauranteDebug assemblePremiumClienteDebug
```

Os APKs de demonstração não devem ser tratados como versão comercial final nem apresentados como testados em produção.