# StudioMesa — planos comerciais propostos

**ESTADO: RASCUNHO INTERNO — NÃO É UMA OFERTA VINCULATIVA.**

Os preços abaixo são os valores de referência que já estavam definidos para o projeto. Não foram ainda validados com restaurantes, custos de alojamento, IVA, suporte, manutenção ou contrato. A proposta comercial deve ser revista pelo responsável legal antes de ser enviada.

## Preços de referência

| Plano | Preço proposto | Aplicações incluídas | Perfil |
|---|---:|---|---|
| **Essencial** | **890 €** | Gestão do restaurante | Operação diária e presença digital interna |
| **Profissional** | **1 490 €** | Gestão + Cliente | Restaurantes que querem reservas e fidelização com app de cliente |
| **Premium** | **1 690 €** | Gestão + Cliente | Restaurantes que precisam de stock, análise e ferramentas económicas avançadas |

**Modelo de cobrança em avaliação:** pagamento único pela instalação, configuração e licença inicial. Alojamento Supabase, manutenção, suporte contínuo, personalizações e integrações devem ser discriminados separadamente. Ainda não existe uma mensalidade definida nem uma decisão final de IVA.

## Funcionalidades por plano

| Funcionalidade | Essencial | Profissional | Premium |
|---|:---:|:---:|:---:|
| Gestão de mesas e zonas | ✓ | ✓ | ✓ |
| Carta, categorias, preços e alergénios | ✓ | ✓ | ✓ |
| Pedidos, quantidades e estados | ✓ | ✓ | ✓ |
| Ecrã de cozinha com atualização em primeiro plano | ✓ | ✓ | ✓ |
| Exportação operacional CSV | ✓ | ✓ | ✓ |
| IA local no Android | ✓ | ✓ | ✓ |
| Histórico e memória do assistente | ✓ | ✓ | ✓ |
| Loja de pontos configurável pelo restaurante | ✓ | ✓ | ✓ |
| Gestão de clientes e saldos de fidelização | ✓ | ✓ | ✓ |
| Aplicação Cliente para o consumidor | — | ✓ | ✓ |
| Cliente consulta saldo, recompensas e resgates | — | ✓ | ✓ |
| Cliente pede reservas na aplicação | — | ✓ | ✓ |
| Gestão de reservas e atribuição de mesa | — | ✓ | ✓ |
| Calculadora e consultor de preço | — | ✓ | ✓ |
| Inventário, entradas/saídas e custos unitários | — | — | ✓ |
| Análise operacional avançada | — | — | ✓ |
| OCR local de faturas de fornecedores | ✓ | ✓ | ✓ |
| Assistente económico (proprietário) com consulta de faturas | ✓ | ✓ | ✓ |
| Histórico/memória cloud e arquivo privado de faturas | — | — | ✓ |
| Notificações locais em primeiro plano | ✓ | ✓ | ✓ |

## O que cada plano entrega

### Essencial — 890 €

- Aplicação de Gestão para o restaurante.
- Mesas, zonas e ocupação.
- Carta com categorias, descrições, preços e alergénios declarados.
- Pedidos e respetivos estados.
- Ecrã de cozinha com atualização periódica enquanto está aberto.
- Exportação CSV de dados operacionais.
- IA local para perguntas e apoio geral.
- Loja de fidelização configurável pelo proprietário: recompensas, custo em pontos, stock e ativação/desativação.

**Limite:** não inclui a aplicação Cliente independente nem o fluxo completo de reservas do cliente. A pesquisa geral na Internet depende da configuração de um fornecedor externo; não deve ser prometida como ativa antes disso.

### Profissional — 1 490 €

Inclui o Essencial, mais:

- Aplicação Cliente independente.
- Carta e consulta do restaurante pelo cliente.
- Cartão de fidelização, saldo e histórico de movimentos.
- Loja de pontos com resgate de recompensas.
- Pedido de reserva pelo cliente, histórico e cancelamento nos estados permitidos.
- Gestão de reservas pelo restaurante, atribuição de mesa e validação de conflitos.
- Ferramenta de cálculo e consultoria de preços.

**Limite:** as reservas ainda precisam de testes ponta a ponta com várias contas e dispositivos. Não existe confirmação automática por SMS/email, depósitos ou sincronização com calendários externos.

### Premium — 1 690 €

Inclui o Profissional, mais:

- Inventário e movimentos de stock.
- Custos unitários e histórico de custo.
- Análise operacional avançada.
- OCR local de faturas de fornecedores.
- Assistente económico com ferramentas de leitura de dados do restaurante e de metadados das faturas.
- Histórico e memória cloud do assistente e arquivo privado cloud de faturas, sujeitos a testes de sincronização e recuperação.

**Limites:** as fichas técnicas e o consumo automático de ingredientes ainda não estão ativos no Supabase alojado. O OCR exige revisão humana antes de qualquer utilização contabilística. A IA não aprova, altera nem paga faturas e não substitui um contabilista certificado.

## Estado técnico e condições a comunicar

- **IA e pesquisa Web:** a IA usa um modelo local no dispositivo. A pesquisa geral está preparada no código, mas só fica ativa depois de configurar uma chave Tavily (ou um endpoint SearXNG estável) nos secrets do Supabase. A pesquisa Wikipedia é separada. Não prometer pesquisa Web geral até ser testada com uma chave válida.
- **Faturas:** OCR local e arquivo privado no backend estão implementados; a ligação da IA geral ao Assistente económico é de leitura e só para o proprietário. A IA geral recebe metadados/totais, não os PDFs nem o texto OCR integral.
- **Loja de pontos:** gestão de recompensas, custo em pontos, stock, resgate transacional e entrega estão implementados no código principal e no Supabase. Testes E2E de autorização, concorrência, saldos e isolamento continuam pendentes.
- **Notificações:** a branch em preparação acrescenta notificações locais enquanto a app está aberta. Notificações push quando a app está fechada ainda não estão implementadas; requerem Firebase/FCM e credenciais próprias.
- **Permissões de equipa:** a matriz granular por funcionário está no código, mas a tabela correspondente ainda não está ativa no Supabase alojado. Não vender este controlo granular até ao deployment aprovado e aos testes de autorização.
- **Reservas e stock:** existem fluxos de base; testes ponta a ponta, regras de operação do restaurante e alguns módulos avançados ainda estão pendentes.
- **Cloud:** o projeto Supabase está ativo, mas o histórico das migrations não está totalmente reconciliado. Não fazer deployments em bloco.
- **Comercial:** os APKs de demonstração não são releases assinadas. Testes físicos, backups/restauro, política de privacidade, Data Safety, DPA, termos, suporte e assinatura comercial têm de estar concluídos antes da venda.

## Não incluído em nenhum plano nesta fase

- Integração com POS, caixa, terminal de pagamento ou programa de faturação/contabilidade.
- Emissão de faturas fiscais, SAF-T, comunicação fiscal ou contabilização automática.
- Pagamentos online, subscrições e cobranças automáticas.
- Impressoras de cozinha/talões, tablets, routers ou hardware.
- Notificações push quando a aplicação está fechada.
- SMS, emails automáticos de confirmação/lembrete ou calendário externo.
- Fichas técnicas e consumo automático de ingredientes.
- Gestão de vários armazéns, compras automáticas e encomendas a fornecedores.
- Garantia de disponibilidade, backups/restauro ou SLA enquanto estes serviços não estiverem contratados e testados.

## Guias técnicos

- [Ativar pesquisa Web com Tavily/SearXNG](docs/WEB-SEARCH-SETUP.md).
- [Notificações locais e requisitos para push Firebase](docs/NOTIFICATIONS-STATUS.md).

## Condições comerciais por fechar

- [ ] Confirmar se os preços incluem IVA ou se o IVA acresce.
- [ ] Definir o que está incluído na instalação, migração de dados e formação.
- [ ] Definir número de dispositivos, contas, utilizadores e restaurantes por licença.
- [ ] Escolher se o alojamento e suporte são pagos anualmente, mensalmente ou diretamente pelo cliente.
- [ ] Definir prazo e canal de suporte, manutenção, atualizações e custos de personalizações.
- [ ] Definir política de pagamento, cancelamento, reembolso e validade das propostas.
- [ ] Aprovar política de privacidade, DPA, retenção e eliminação de dados.
- [ ] Fazer teste de aceitação com o restaurante antes de receber pagamentos finais.

**Regra de venda:** apenas apresentar como disponível aquilo que estiver ativo no servidor e validado na release entregue. Funcionalidades dependentes de credenciais, deployment, fornecedor externo ou testes devem ser descritas como condicionadas, nunca como prontas.
