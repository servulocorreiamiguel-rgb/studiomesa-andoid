# StudioMesa — demonstração Bicho Mau

Protótipo Android com identidade visual conceptual inspirada no ambiente do Bistrô Bicho Mau, em Campo de Ourique. **Não é uma aplicação oficial nem foi encomendado ou aprovado pelo restaurante.**

## Estrutura dos planos

- **Essencial — 890 € (valor de referência):** aplicação para o restaurante com mesas, pedidos, gestão da carta e painel local para registar clientes e atribuir/descontar pontos manualmente. Não inclui APK para clientes.
- **Profissional — 1 490 € (valor de referência):** aplicação para o restaurante, calculadora de preço indicativo e APK separado com a interface de cliente/cartão digital em modo demonstrativo.
- **Premium — 1 690 € (valor de referência):** as duas interfaces, calculadora, painel de indicadores locais e resumo dos pedidos/artigos guardados no dispositivo.

Os preços são propostas de referência, não pagamentos configurados nem prova de que o software está pronto para venda. O APK de cliente dos planos Profissional e Premium ainda não sincroniza pontos com a aplicação do restaurante.

## Funcionalidades implementadas

- Estado de 12 mesas (livre/ocupada).
- Criação e conclusão de pedidos associados a uma mesa e artigo.
- Gestão simples da carta, com preço de venda e custo por unidade.
- Calculadora de preço indicativo nos planos Profissional e Premium.
- **Fidelização local:** criação de cartões de cliente com código, atribuição manual de pontos e desconto de pontos, sem permitir saldo negativo.
- **Interface separada para clientes** nos planos Profissional e Premium: nome local, código de demonstração, cartão de pontos e carta de exemplos.
- Indicadores locais no plano Premium.
- Design em verde-floresta, creme e cobre, com ícone próprio.
- APKs com identificadores de aplicação diferentes para permitir instalação separada.

## Base de dados de fidelização (versão em desenvolvimento)

O projeto Supabase configurado contém tabelas protegidas por RLS para restaurantes, equipa, cartões de cliente e transações de pontos. A aplicação Android inclui chamadas de autenticação e operações de fidelização; estas alterações continuam em validação e não devem ser usadas para gerir dados reais até o build e os fluxos de conta terem sido testados em dispositivos.

## Limitações importantes antes da venda

Os pratos e preços iniciais são **fictícios**. A aplicação do restaurante guarda os dados apenas nesse telemóvel. A app de cliente também guarda os seus dados localmente, em separado: por isso, o código/saldo do cartão de cliente ainda **não está ligado** ao registo do restaurante. Os pontos são atribuídos manualmente; não há acumulação automática por compra, pagamento, prémios configuráveis ou sincronização entre dispositivos.

Ainda não existem servidor/base de dados partilhada, contas seguras, cópias de segurança, integração com caixa registadora/cozinha, sincronização, publicação na Google Play ou testes em dispositivos físicos. O painel Premium não é contabilidade oficial. **Não vender nem apresentar como serviço de fidelização operacional até implementar e testar a sincronização partilhada, a segurança e as regras comerciais com o restaurante.**

## Compilar os cinco APKs de demonstração

O workflow em **Actions → Build StudioMesa Android APK** gera:

- `app-essencial-restaurante-debug.apk`
- `app-profissional-restaurante-debug.apk`
- `app-profissional-cliente-debug.apk`
- `app-premium-restaurante-debug.apk`
- `app-premium-cliente-debug.apk`

Com Java 17, Gradle 8.9 e Android SDK, executar:

```bash
gradle --no-daemon assembleEssencialRestauranteDebug assembleProfissionalRestauranteDebug assembleProfissionalClienteDebug assemblePremiumRestauranteDebug assemblePremiumClienteDebug
```

Os APKs são de depuração/demonstração. O sucesso de compilação não substitui testes de instalação e utilização em telemóveis reais.
