# StudioMesa — planos comerciais propostos

**RASCUNHO INTERNO — não é uma oferta vinculativa.**

Os preços são os valores de referência já definidos para o projeto. Ainda não foram validados com clientes, IVA, alojamento, suporte, manutenção ou contrato.

## Preços de referência

| Plano | Preço proposto | Aplicações |
|---|---:|---|
| **Essencial** | **890 €** | Gestão + Cliente básico |
| **Profissional** | **1 490 €** | Gestão + Cliente com reservas |
| **Premium** | **1 690 €** | Gestão + Cliente + ferramentas avançadas de stock e análise |

**Modelo de cobrança em avaliação:** pagamento único pela instalação, configuração e licença inicial. Alojamento, manutenção, suporte contínuo, personalizações e integrações devem ser discriminados à parte. IVA e condições de pagamento ainda por decidir.

## Limites da Loja de Pontos

| Funcionalidade | Essencial | Profissional | Premium |
|---|---:|---:|---:|
| Prémios ativos na loja | **5** | **15** | **50** |
| Fotografias por prémio | — | **1** | **1** |
| Tamanho máximo da fotografia | — | **1,5 MB** | **1,5 MB** |
| Resolução máxima guardada | — | **1280 px** | **1280 px** |
| Criar, editar e desativar prémios | ✓ | ✓ | ✓ |
| Custo configurável em pontos | ✓ | ✓ | ✓ |
| Stock definido pelo restaurante | ✓ | ✓ | ✓ |
| Resgate pelo cliente na app Cliente | ✓ | ✓ | ✓ |
| Desconto de pontos no servidor | ✓ | ✓ | ✓ |
| Confirmação de entrega pelo restaurante | ✓ | ✓ | ✓ |
| Histórico dos resgates | ✓ | ✓ | ✓ |

Um prémio ativo conta para o limite. Desativá-lo liberta um lugar sem apagar os registos antigos. O stock de cada prémio é separado do número de produtos: o restaurante define quantas unidades disponibiliza ou deixa o campo sem limite.

O Essencial tem uma loja funcional com até cinco prémios, mas sem fotografias. Profissional e Premium permitem uma imagem por prémio. O servidor, e não a versão do APK, aplica os limites.

**Estado técnico:** os limites server-side, as fotografias e a variante Cliente Essencial estão a ser preparados na branch feature/loyalty-shop-photos-tier-limits. Não devem ser anunciados como já disponíveis na versão principal até a PR passar a CI, ser integrada e a migration ser aplicada de forma controlada no Supabase.

## Funcionalidades por plano

| Funcionalidade | Essencial | Profissional | Premium |
|---|:---:|:---:|:---:|
| Gestão de mesas e zonas | ✓ | ✓ | ✓ |
| Carta, categorias, preços e alergénios declarados | ✓ | ✓ | ✓ |
| Pedidos, quantidades e estados | ✓ | ✓ | ✓ |
| Ecrã de cozinha com atualização em primeiro plano | ✓ | ✓ | ✓ |
| Exportação operacional CSV | ✓ | ✓ | ✓ |
| IA local no Android | ✓ | ✓ | ✓ |
| Histórico e memória do assistente | ✓ | ✓ | ✓ |
| Assistente económico para o proprietário | ✓ | ✓ | ✓ |
| OCR local de faturas de fornecedores | — | — | ✓ |
| Loja de pontos e fidelização | 5 prémios | 15 prémios + fotos | 50 prémios + fotos |
| Aplicação Cliente | Cartão, carta e loja | Tudo do Essencial + reservas | Tudo do Profissional |
| Pedidos de reserva pelo cliente | — | ✓ | ✓ |
| Gestão de reservas e atribuição de mesa | — | ✓ | ✓ |
| Calculadora e consultor de preço | — | ✓ | ✓ |
| Inventário, entradas/saídas e custos unitários | — | — | ✓ |
| Análise operacional avançada | — | — | ✓ |

### Essencial — 890 €

- Aplicação Gestão para o restaurante.
- Aplicação Cliente básica para cartão, carta, pontos e resgates.
- Loja de pontos com até cinco prémios ativos, sem fotografias.
- Mesas, carta, pedidos e ecrã de cozinha.
- IA local e Assistente económico para o proprietário.

Não inclui reservas pela aplicação Cliente, fotografias dos prémios, inventário avançado nem análise Premium.

### Profissional — 1 490 €

Inclui o Essencial, mais:

- Até 15 prémios ativos, com uma fotografia por prémio.
- Reservas do cliente e gestão de reservas pelo restaurante.
- Atribuição de mesa e validação de conflitos.
- Ferramenta de cálculo e consultoria de preços.

### Premium — 1 690 €

Inclui o Profissional, mais:

- Até 50 prémios ativos, com uma fotografia por prémio.
- Inventário e movimentos de stock.
- Custos unitários e histórico de custo.
- Análise operacional avançada.
- OCR local de faturas de fornecedores e ferramentas adicionais do Assistente económico.

As fichas técnicas e o consumo automático de ingredientes ainda não devem ser prometidos como ativos no Supabase alojado.

## Segurança e regras da fidelização

O cliente não escolhe o seu saldo nem o custo do prémio. No resgate, o servidor consulta o custo e o saldo reais, valida o stock, bloqueia as linhas relevantes, desconta os pontos e cria o resgate numa única transação. A repetição da mesma tentativa não deve descontar pontos duas vezes.

O restaurante confirma a entrega com a sua conta autenticada. O cliente não pode confirmar a própria entrega nem alterar diretamente o catálogo. O QR de acumulação é temporário, ligado ao cartão autenticado e de utilização única.

**Limitação atual:** ainda falta um fluxo completo de cancelamento de um resgate pendente com devolução automática de pontos e reposição de stock. Até esse fluxo existir, um resgate pendente deve ser tratado como uma unidade reservada, não como uma entrega concluída.

Ver [auditoria de segurança, fotografias e limites da loja](docs/LOYALTY-SHOP-SECURITY-AND-PLANS.md).

## Limitações gerais / não incluído nesta fase

- Integração com POS, caixa, terminal de pagamento, faturação ou programa de contabilidade.
- Emissão de faturas fiscais, SAF-T, comunicação fiscal ou contabilização automática.
- Pagamentos online, cobranças automáticas, subscrições e gestão automática de licenças.
- Impressoras de cozinha, hardware, tablets ou configuração de rede.
- Notificações push quando a aplicação está fechada.
- Confirmações automáticas por SMS/email ou calendário externo.
- Vários armazéns, encomendas automáticas a fornecedores ou consumo automático de ingredientes.
- Garantia de disponibilidade ou SLA enquanto não existir plano e contrato de alojamento/suporte.

## Condições comerciais por fechar

- [ ] Confirmar se os preços incluem IVA ou se o IVA acresce.
- [ ] Definir o que está incluído na instalação, migração de dados e formação.
- [ ] Definir número de dispositivos, utilizadores e restaurantes por licença.
- [ ] Definir quem suporta os custos do Supabase, email, domínio e suporte.
- [ ] Definir manutenção, atualizações, prazos e canal de suporte.
- [ ] Definir pagamento, cancelamento, reembolso e validade da proposta.
- [ ] Aprovar política de privacidade, DPA, retenção e eliminação de dados.
- [ ] Testar a app Cliente Essencial e resgates ponta a ponta antes da venda.

**Regra de venda:** apresentar como disponível apenas o que estiver ativo no servidor e validado na versão entregue. Os módulos em preparação, dependentes de credenciais ou ainda sem testes E2E devem ser descritos como condicionados.
