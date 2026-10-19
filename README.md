# StudioMesa — demonstração Bicho Mau

Protótipo Android de gestão de restaurante, com identidade visual conceptual inspirada no ambiente do Bistrô Bicho Mau, em Campo de Ourique. **Não é uma aplicação oficial nem um projeto aprovado pelo restaurante.**

## Três variantes

- **Essencial — 890 € (valor de referência):** painel inicial, estado de 12 mesas, registo e acompanhamento de pedidos e gestão da carta de demonstração.
- **Profissional — 1 490 € (valor de referência):** inclui o Essencial e a calculadora de preço indicativo com base no custo.
- **Premium — 1 690 € (valor de referência):** inclui o Profissional e um resumo local dos pedidos e artigos registados.

Os preços são referências comerciais de proposta, não pagamentos configurados na aplicação nem uma confirmação de que o produto está pronto para venda.

## Funcionalidades implementadas

- Estado de 12 mesas (livre/ocupada).
- Criação e conclusão de pedidos associados a uma mesa e a um artigo.
- Gestão simples da carta, com preço de venda e custo por unidade.
- Calculadora de preço indicativo nas variantes Profissional e Premium.
- Resumo local de pedidos e artigos na variante Premium.
- Design em verde-floresta, creme e cobre, com ícone de aplicação próprio.
- Os três APKs têm identificadores de aplicação separados, por isso podem ser instalados lado a lado.

## Dados e limites

Os artigos e valores iniciais da carta são **fictícios e servem apenas para demonstração**. Os dados são guardados localmente no dispositivo. Não existe servidor, conta de utilizador, sincronização entre telemóveis, integração com uma caixa registadora ou envio de pedidos para a cozinha. A análise Premium é um resumo dos registos locais, não contabilidade nem um relatório financeiro oficial.

Esta versão foi compilada com sucesso em modo debug. Antes de a utilizar num restaurante real ou a vender, ainda deve ser instalada e testada em dispositivos físicos, revista com o restaurante, e complementada com requisitos de segurança, cópias de segurança, privacidade, suporte e condições comerciais.

## Gerar os APKs

O workflow do GitHub Actions em **Actions → Build StudioMesa Android APK** compila as três variantes. O artefacto **StudioMesa-BichoMau-3-APKs** inclui os APKs:

- `app-essencial-debug.apk`
- `app-profissional-debug.apk`
- `app-premium-debug.apk`

Com Java 17, Gradle 8.9 e Android SDK instalado, executar:

```bash
gradle --no-daemon assembleEssencialDebug assembleProfissionalDebug assemblePremiumDebug
```

Os ficheiros são gerados em `app/build/outputs/apk/<variante>/debug/`.
