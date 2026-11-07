# StudioMesa — planos comerciais propostos

**ESTADO: RASCUNHO INTERNO — NÃO É UMA OFERTA VINCULATIVA.**

Este documento descreve o produto e a divisão de funcionalidades pretendida. Os preços são valores de referência existentes no projeto; ainda não foram aprovados com clientes nem validados quanto a IVA, custos de alojamento, suporte, licenciamento e contrato.

## 1. Estrutura dos planos

| Plano | Preço de referência | Aplicações previstas | Destinatário |
|---|---:|---|---|
| **Essencial** | **890 €** | StudioMesa Gestão | Proprietário e equipa |
| **Profissional** | **1 490 €** | Gestão + StudioMesa Cliente | Restaurante e clientes |
| **Premium** | **1 690 €** | Gestão avançada + StudioMesa Cliente | Restaurante e clientes |

**Modelo comercial em avaliação:** pagamento único pela configuração e licença inicial. Alojamento, manutenção, suporte alargado, migração de dados e alterações específicas devem ser orçamentados separadamente. Não anunciar estes valores como mensalidades nem como preços finais sem aprovação.

## 2. Funcionalidades incluídas em todos os planos

| Funcionalidade | Essencial | Profissional | Premium |
|---|:---:|:---:|:---:|
| Conta do proprietário e configuração de um restaurante | ✓ | ✓ | ✓ |
| Gestão básica de funcionários e códigos de acesso | ✓ | ✓ | ✓ |
| Mesas, ocupação, zonas e capacidade | ✓ | ✓ | ✓ |
| Carta digital, categorias, descrições, preços e alergénios | ✓ | ✓ | ✓ |
| Criação e gestão de pedidos | ✓ | ✓ | ✓ |
| Quantidades, observações e sequência de serviço | ✓ | ✓ | ✓ |
| Estados dos pedidos e fila de cozinha | ✓ | ✓ | ✓ |
| Exportação CSV de pedidos | ✓ | ✓ | ✓ |
| Assistente conversacional com modelo generativo local | ✓ | ✓ | ✓ |
| Modo «Pensar mais» | ✓ | ✓ | ✓ |
| Histórico local cifrado e memória do assistente | ✓ | ✓ | ✓ |
| Sincronização cloud opcional do histórico/memória do proprietário | ✓* | ✓* | ✓* |

### O que inclui a IA comum

A IA geral usa o modelo LFM2.5-1.2B-Instruct, executado no próprio Android. Permite conversar sobre assuntos gerais e usar ferramentas de leitura para consultar os dados operacionais que a aplicação já carregou, quando esses dados estão disponíveis. O modo «Pensar mais» aumenta o orçamento de resposta e reforça as instruções de verificação; não troca o modelo por outro maior.

- A primeira instalação do modelo requer uma transferência aproximada de **736 MB** e espaço livre adicional no telemóvel.
- Depois de instalado, o modelo gera respostas localmente e não tem custo por mensagem de um fornecedor de IA.
- O modelo é pequeno e tem limites de contexto, velocidade e qualidade. **Não é equivalente ao ChatGPT** e o desempenho depende do telemóvel.
- A consulta à Wikipédia pode ser usada como fonte pública limitada. A pesquisa geral na Internet está preparada no código, mas continua desativada até existir um servidor de pesquisa estável configurado.
- A IA não altera preços, stock, pedidos, reservas, permissões ou faturas. As ferramentas de consulta são de leitura.
- O histórico local é cifrado. A sincronização cloud é apenas para a conta do proprietário, protegida por autenticação e RLS; a cópia cloud não é cifrada ponta a ponta.

\* **Condicionada:** as tabelas cloud de histórico e memória já existem no Supabase ativo, mas a sincronização entre dois dispositivos, conflitos e eliminação ainda precisam de testes ponta a ponta antes de ser prometida como funcionalidade comercial validada.

## 3. Plano Essencial — 890 €

**Objetivo:** dar ao restaurante as ferramentas digitais fundamentais para organizar o serviço diário.

Inclui tudo o que está na secção 2:
- Aplicação de Gestão para o proprietário e funcionários autorizados.
- Configuração de mesas, zonas e capacidade.
- Gestão da carta e dos artigos, com categorias, descrições, preços e informação de alergénios.
- Registo de pedidos com quantidades, observações e sequência de serviço.
- Acompanhamento do estado dos pedidos e vista da cozinha.
- Exportação dos pedidos para CSV.
- Assistente IA local, conversação geral, consulta dos dados carregados e modo «Pensar mais».

**Não inclui:** aplicação Cliente, reservas para clientes, programa de pontos, inventário avançado, fichas técnicas, consumo automático de ingredientes, arquivo de faturas ou Assistente económico Premium.

## 4. Plano Profissional — 1 490 €

**Objetivo:** acrescentar uma experiência digital para os clientes e mecanismos de fidelização e reservas.

Inclui tudo do Essencial, mais:

### Aplicação Cliente
- Registo e autenticação do cliente.
- Adesão ao restaurante através de código.
- Consulta da carta e dos dados dos artigos disponibilizados pelo restaurante.
- Acesso ao cartão de cliente e ao saldo de pontos.
- Consulta do histórico de movimentos e recompensas.
- Geração/utilização de QR nos fluxos de fidelização suportados.

### Fidelização
- Atribuição de pontos através do fluxo QR autorizado.
- Loja de recompensas configurada pelo proprietário.
- Pedidos de resgate feitos pelo cliente.
- Consulta dos resgates e confirmação de entrega pela equipa autorizada.
- Histórico de pontos e movimentos.

### Reservas
- Pedido de reserva pelo cliente.
- Registo de nome, contacto, número de pessoas, data/hora e observações.
- Consulta e gestão dos estados da reserva pelo restaurante.
- Associação de uma reserva a uma mesa, quando aplicável.
- Validações de capacidade e prevenção de conflitos previstas no backend.

**Limites:** a fidelização não é um sistema de pagamentos nem uma integração com a caixa. O saldo e os resgates dependem dos fluxos da aplicação e da confirmação do restaurante. As reservas não incluem, nesta fase, lembretes automáticos por SMS/email, depósitos, calendário avançado ou sincronização com plataformas externas.

## 5. Plano Premium — 1 690 €

**Objetivo:** acrescentar controlo de stock, custos, documentos de fornecedores e apoio à decisão económica.

Inclui tudo do Profissional, mais:

### Inventário e custos
- Registo de ingredientes, bebidas e consumíveis.
- Unidades, quantidade disponível e limite mínimo configurável.
- Entradas, saídas e ajustes manuais de stock.
- Histórico de movimentos.
- Registo de custo unitário de compra e estimativa do valor de stock com custo conhecido.
- Registo de desperdício e consulta do respetivo impacto quando existe custo conhecido.
- Indicadores e avisos apresentados na aplicação quando o stock atinge o limite configurado.

### Fichas técnicas e consumo automático
- Associação de ingredientes a cada artigo da carta.
- Quantidade usada por dose e percentagem de desperdício.
- Revisão e aprovação da ficha técnica pelo proprietário.
- Desconto automático dos ingredientes quando o pedido passa de «Aberto» para «Em preparação».
- Registo do movimento de stock associado ao pedido, evitando descontar duas vezes o mesmo pedido.
- Bloqueio de preparação quando o stock é insuficiente ou quando uma ficha técnica alterada precisa de nova aprovação.

**Estado:** a interface Android e a migração PostgreSQL estão no repositório, mas as tabelas correspondentes ainda não existem no Supabase alojado. Esta funcionalidade só pode ser ativada e incluída numa proposta depois de reconciliar as migrações, fazer deployment aprovado e concluir os testes de stock/pedidos.

### Faturas de fornecedores e OCR
- Importação de faturas em PDF ou imagem suportada.
- Extração local de fornecedor, NIF/VAT, número, datas, base tributável, IVA e total.
- Assinalar campos incertos, possíveis duplicados e totais incoerentes.
- Revisão e confirmação manual pelo proprietário; o OCR não aprova despesas sozinho.
- Arquivo privado do documento e dos respetivos metadados cloud para a conta autorizada.
- Consulta dos metadados de faturas pelo Assistente económico, sem expor o PDF integral nem o texto OCR à IA geral.

### Assistente económico e consultor de preços
- Indicadores e cálculos de custo alimentar, custo de pessoal, custos operacionais e resultado estimado, com base nos dados fornecidos.
- Ponto de equilíbrio e cenários de tesouraria introduzidos manualmente.
- Custo indicativo por receita/dose, desperdício e quantidade de reposição.
- Comparação manual de custos e preços de fornecedores quando os artigos/unidades são comparáveis.
- Análise de artigos da carta e referência de preço com base no custo registado.
- Sugestões de preço sujeitas à aprovação explícita do proprietário.
- Ligação de leitura entre a IA geral e o Assistente económico, disponível apenas para o proprietário autenticado.

**Limites financeiros:** não existe ligação bancária nem integração POS/ERP/contabilidade. Os cálculos são estimativas e não são contabilidade, aconselhamento fiscal, confirmação de pagamento ou garantia de margem. A IA não aprova, paga, edita ou elimina faturas e não altera preços ou stock autonomamente.

### Permissões personalizadas da equipa
O proprietário poderá configurar o acesso de cada funcionário por área:
- Pedidos e cozinha.
- Mesas.
- Reservas.
- Carta.
- Stock.
- Fidelização operacional.

O proprietário conserva sempre o acesso total. Gestão da equipa, códigos, catálogo de recompensas, consultor económico, definições e eliminação do restaurante continuam reservados ao proprietário.

**Limite atual dos cargos:** ainda não existem perfis predefinidos completos de gerente, empregado de mesa, cozinha e caixa. A versão atual usa permissões por área. Algumas áreas ainda não separam consultar de alterar, pelo que não se deve prometer que cada cargo já tem um conjunto perfeito de ações próprias. Também não existe módulo de caixa para cobrar pagamentos.

**Estado:** a interface Android e a migração com validação no servidor já existem no repositório, mas a tabela privada de permissões ainda não existe no Supabase alojado. Não apresentar esta função como ativa até à migração, testes negativos de autorização e validação ponta a ponta.

## 6. Limitações comuns a comunicar

- A vista de cozinha atualiza os dados periodicamente em primeiro plano; não deve ser descrita como tempo real garantido nem como impressão automática.
- A exportação CSV é operacional, não é SAF-T, faturação certificada ou relatório contabilístico.
- O assistente local pode errar e depende do desempenho do dispositivo; a instalação do modelo requer cerca de 736 MB de transferência.
- A pesquisa geral na Internet não está ativa neste momento; a consulta pública limitada à Wikipédia não substitui pesquisa Web geral.
- As funcionalidades de cloud dependem de autenticação, conectividade, políticas RLS e disponibilidade do fornecedor.
- O keepalive silencioso do Supabase aumenta a probabilidade de evitar pausa por inatividade no plano gratuito, mas não garante disponibilidade permanente.
- Nenhum módulo está validado em produção apenas por existir no código. As funcionalidades dependentes de deployment ou testes devem ser vendidas como condicionadas até à respetiva aceitação.

## 7. Não incluído em nenhum plano nesta fase

- Integração com POS, caixa, terminal de pagamento, faturação ou programa de contabilidade.
- Emissão de faturas fiscais, SAF-T de faturação, comunicação fiscal ou contabilização automática.
- Pagamentos online, gestão automática de subscrições e cobrança recorrente.
- Impressoras de cozinha/talões, tablets, routers ou configuração de hardware/rede.
- Confirmações e lembretes de reservas por SMS/email, depósitos e sincronização com calendários externos.
- Encomendas automáticas a fornecedores, receção integrada de compras e gestão de vários armazéns.
- Conversões automáticas entre unidades de compra e unidades de receita.
- Integração bancária e reconciliação de pagamentos.
- Garantia de disponibilidade, backups ou recuperação de dados até existir um plano definido e um restauro ensaiado.

## 8. Condições comerciais a fechar

- [ ] Aprovar preços e confirmar o tratamento do IVA.
- [ ] Definir se a licença é por restaurante, empresa ou conjunto de aplicações.
- [ ] Decidir o número de dispositivos, contas e funcionários incluídos.
- [ ] Definir configuração inicial, migração de dados e formação.
- [ ] Definir custos e responsável por alojamento, domínio, email, manutenção e suporte.
- [ ] Definir atualizações incluídas, tempos/canais de suporte e custos de alterações.
- [ ] Fechar contrato, DPA, privacidade, retenção, exportação e eliminação dos dados.
- [ ] Implementar direitos/entitlements por plano validados no servidor. Neste momento, as variantes de APK não constituem, por si só, um sistema de licenciamento seguro.
- [ ] Concluir E2E, segurança, backup/restauro, testes físicos e critérios de aceitação antes de piloto/venda.

## 9. Regra de venda

Só podem ser apresentadas como disponíveis as funções que estejam instaladas no backend, autorizadas pelo plano do restaurante e validadas na release entregue. As funções marcadas como condicionadas não devem constar de uma oferta vinculativa até concluírem deployment e testes.

Documento técnico de acompanhamento: [Estado de implementação de 7 de novembro de 2026](docs/IMPLEMENTATION-STATUS-2026-11-07.md).
