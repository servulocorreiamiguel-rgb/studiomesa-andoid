# StudioMesa — planos comerciais propostos

**ESTADO: RASCUNHO INTERNO — NÃO ENVIAR COMO OFERTA VINCULATIVA.**

Os valores abaixo são os valores de referência existentes no projeto. Ainda não foram validados com clientes nem aprovados em termos de IVA, custos de alojamento, suporte e contrato.

## Estrutura de preços

| Plano | Preço de referência | Aplicação prevista | Destinatário |
|---|---:|---|---|
| Essencial | **890 €** | Gestão do restaurante | Restaurante |
| Profissional | **1 490 €** | Gestão + aplicação de cliente | Restaurante e clientes |
| Premium | **1 690 €** | Gestão avançada + aplicação de cliente | Restaurante e clientes |

**Hipótese comercial:** pagamento único pela configuração/licença inicial; alojamento, manutenção, suporte alargado e alterações futuras devem ser discriminados à parte. Esta modalidade e os preços finais precisam de aprovação do responsável legal e de validação fiscal.

## Matriz de funcionalidades

| Funcionalidade | Essencial | Profissional | Premium | Estado / limite que tem de ser comunicado |
|---|:---:|:---:|:---:|---|
| Mesas e zonas | ✓ | ✓ | ✓ | Código e backend base presentes; testar em restaurante piloto. |
| Carta e artigos | ✓ | ✓ | ✓ | Custos internos não são apresentados na app cliente. |
| Pedidos e estados | ✓ | ✓ | ✓ | Fluxo operacional presente; não é POS/faturação fiscal. |
| Vista de cozinha | ✓ | ✓ | ✓ | Atualização periódica em primeiro plano; não é tempo real nem impressão automática. |
| Exportação CSV de pedidos | ✓ | ✓ | ✓ | Exportação operacional; não é SAF-T, fatura ou relatório contabilístico certificado. |
| Fidelização de clientes | — | ✓ | ✓ | Base de fidelização e QR estão no backend; testar atribuição/resgate e isolamento antes de ativar com clientes. |
| Aplicação Cliente | — | ✓ | ✓ | Variante Cliente disponível no código; confirmar package ID, login, menu e saldo no piloto. |
| Reservas | — | ✓ | ✓ | Fluxo e validações no backend; regressões PostgreSQL testadas, E2E com duas contas/dispositivos ainda pendente. |
| Inventário e movimentos | — | — | ✓ | Inventário e custos unitários no backend; testes E2E e conciliação operacional ainda pendentes. |
| Consultor de preço com aprovação | — | — | ✓ | Implementado com aprovação do proprietário; não é contabilidade nem garantia de margem. |
| Assistente IA local | ✓ | ✓ | ✓ | Modelo pequeno no dispositivo; download inicial ~736 MB, desempenho real varia por telefone e não equivale ao ChatGPT. |
| Pensar mais | ✓ | ✓ | ✓ | Altera o nível de verificação/quantidade de resposta, não muda o modelo. |
| OCR local de faturas | — | — | ✓ | Código processa PDF/imagem localmente e pede revisão manual; arquivo cloud ainda não está ativo no Supabase atual. |
| Histórico/Memória cloud da IA | — | — | — | Código preparado, mas tabelas ainda ausentes no backend atual; não vender como sincronização entre dispositivos ativa. |
| Fichas técnicas e consumo automático de stock | — | — | — | Código/migration existem, mas tabelas ainda ausentes no backend atual; fora dos planos até deployment e validação. |
| Permissões granulares por funcionário | — | — | — | Migration/tabela ainda ausente no Supabase atual; funcionários continuam com permissões gerais nesta fotografia. |

## Não incluído em nenhum plano nesta fase

- Integração com POS, caixa, terminal de pagamento, sistema de faturação ou programa de contabilidade.
- Emissão de faturas fiscais, SAF-T de faturação, comunicação fiscal ou contabilização automática.
- Pagamentos online, cobranças automáticas, subscrições e gestão automática de licenças.
- Impressoras de cozinha/talões, hardware, tablets, routers ou configuração de rede do restaurante.
- Confirmações automáticas de reservas por SMS/email, lembretes e calendário avançado.
- Conversões automáticas de unidades, gestão de vários armazéns e compras/encomendas a fornecedores.
- Garantia de disponibilidade, recuperação de dados ou backups até existir plano e teste de restauro documentados.

## Condições comerciais que têm de ser fechadas

- [ ] Confirmar se os valores incluem IVA ou acresce IVA, e qual o tratamento fiscal aplicável.
- [ ] Confirmar se o preço cobre configuração inicial, migração de dados, formação e número de dispositivos/contas.
- [ ] Definir plano de pagamento, validade da proposta, política de cancelamento e condições de reembolso.
- [ ] Definir se a licença é por restaurante, empresa, conta ou conjunto de aplicações.
- [ ] Identificar o custo e responsável pelo Supabase, email, domínio, suporte e eventuais fornecedores.
- [ ] Definir atualizações incluídas, manutenção opcional, prazo/canal de suporte e custos por alterações.
- [ ] Definir DPA, política de privacidade, propriedade dos dados e regras de exportação/eliminação.
- [ ] Confirmar que a versão contratada passou os critérios de aceitação e que os módulos cloud estão efetivamente ativos.

**Regra de venda:** apresentar apenas funcionalidades marcadas como operacionais na release contratada. Os módulos com limitações acima devem aparecer como indisponíveis, locais ou condicionados, não como funções já ativas.