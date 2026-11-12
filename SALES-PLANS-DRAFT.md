# StudioMesa — planos comerciais propostos

**ESTADO: RASCUNHO INTERNO — NÃO ENVIAR COMO OFERTA VINCULATIVA.**

Estado técnico consolidado em 12/11/2026: ver [auditoria comercial consolidada](docs/COMMERCIAL-CLOSEOUT-AUDIT-2026-11-12.md). Os planos e preços continuam por validar com clientes e não existe ainda entitlement/licença automática por plano no backend.

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
| Assistente IA local | ✓ | ✓ | ✓ | Gemma 4 E2B é o motor preferido em dispositivos com RAM/espaço suficientes; LFM2.5-1.2B INT4 (736 MB) é alternativa/reserva. A qualidade, compatibilidade, RAM, temperatura e bateria ainda precisam de testes físicos. Não é ChatGPT nem foi treinado pela StudioMesa. |
| Pensar mais | ✓ | ✓ | ✓ | Reforça a verificação e aumenta o limite de resposta; não muda o modelo nem garante melhor raciocínio. |
| Gestor Económico IA | ✓ | ✓ | ✓ | Cálculos determinísticos locais, apenas de leitura, com dados introduzidos/consultados; não liga automaticamente a POS, bancos ou contabilidade. |
| Análise dos sete dias da semana e previsão na conversa | ✓ | ✓ | ✓ | Ferramenta do Assistente Económico baseada em pedidos concluídos e data de criação; apresenta amostras/confiança e não afirma pagamentos recebidos. |
| Painel visual de análise semanal, padrões de afluência e previsão de 7 dias | — | — | ✓ | A página Análise está disponível no plano Premium. A afluência usa linhas de pedidos, não número de pessoas. |
| Feriados nacionais e Carnaval facultativo na previsão | ✓ | ✓ | ✓ | Calendário nacional reconhecido automaticamente; o Carnaval é facultativo e o efeito só é medido com amostras históricas. |
| Feriados municipais e eventos configuráveis | — | — | ✓ | Proprietário configura na página Análise; atualmente fica guardado neste dispositivo e não sincroniza com outros equipamentos. |
| OCR local de faturas | — | — | ✓ | PDF/imagem processados localmente e campos sujeitos a revisão do proprietário. |
| Arquivo cloud de faturas | — | — | ✓ | Tabela e bucket privado existem no Supabase atual; exige conta/permissão de proprietário e E2E de upload/leitura/eliminação ainda pendente. |
| Histórico/Memória cloud da IA | — | — | — | Tabelas existem; sincronização desligada por defeito e só ativa com consentimento do proprietário. A cópia cloud não tem cifragem ponta a ponta e ainda requer E2E em vários dispositivos. |
| Fichas técnicas e consumo automático de stock | — | — | — | Código/migration existem, mas as tabelas não foram encontradas no Supabase alojado; não anunciar como funcional no backend. |
| Permissões granulares por funcionário | — | — | — | Migration/tabela não encontrada no Supabase alojado; a matriz granular não está pronta para uso real. |

## Não incluído em nenhum plano nesta fase

- Integração com POS, caixa, terminal de pagamento, sistema de faturação ou programa de contabilidade.
- Emissão de faturas fiscais, SAF-T de faturação, comunicação fiscal ou contabilização automática.
- Pagamentos online, cobranças automáticas, subscrições e gestão automática de licenças.
- Impressoras de cozinha/talões, hardware, tablets, routers ou configuração de rede do restaurante.
- Confirmações automáticas de reservas por SMS/email, lembretes e calendário avançado.
- Conversões automáticas de unidades, gestão de vários armazéns e compras/encomendas a fornecedores.
- Garantia de disponibilidade, recuperação de dados ou backups até existir plano e teste de restauro documentados.

## Limite atual da distribuição por plano

As cinco variantes Gradle são artefactos técnicos de demonstração. O controlo de acesso a funcionalidades pagas por entitlement validado no servidor ainda não está implementado. Não tratar a separação de builds como mecanismo anti-pirataria nem publicar automaticamente cinco listagens Google Play. Antes de uma oferta comercial definitiva, escolher entre uma aplicação Gestão e uma Cliente com planos/entitlements seguros ou distribuição B2B privada, e validar essa estratégia na Play Console.

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