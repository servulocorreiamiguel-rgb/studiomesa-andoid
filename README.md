# StudioMesa Android

Aplicação Android nativa para apoiar a gestão diária de um restaurante.

## Funcionalidades desta primeira versão

- Painel com resumo de mesas ocupadas e pedidos ativos.
- Estado de 12 mesas (livre/ocupada), guardado localmente no dispositivo.
- Criação de pedidos associados a uma mesa e a um artigo do menu.
- Acompanhamento de pedidos abertos e concluídos.
- Gestão simples do menu, com preço de venda e custo por unidade.
- Calculadora «Preço Justo de Mercado» baseada no custo e na percentagem de custo pretendida.
- Ecrã de referência com os preços de 890 €, 1 490 € e 1 690 €.

## Tecnologia

- Kotlin e componentes nativos Android.
- minSdk 24 (Android 7.0 ou posterior).
- Dados guardados localmente em SharedPreferences; não há ainda servidor, contas de utilizador nem sincronização entre dispositivos.

## Gerar um APK

O workflow do GitHub Actions em **Actions → Build StudioMesa Android APK** tenta compilar a aplicação em cada atualização da branch main. Quando a compilação terminar com sucesso, o ficheiro **StudioMesa-debug-apk** fica disponível como artefacto da execução por 14 dias.

Também é possível compilar num ambiente com Java 17, Gradle 8.9 e Android SDK instalado:

```bash
gradle --no-daemon assembleDebug
```

O APK de depuração é gerado em app/build/outputs/apk/debug/app-debug.apk.

## Limitações conhecidas desta versão

Este é um MVP inicial, não uma solução pronta para venda ou publicação. Os preços dos planos são apresentados como valores de referência; os módulos incluídos precisam de ser especificados. A calculadora não consulta preços concorrentes em tempo real nem calcula automaticamente IVA, desperdício, salários, rendas ou outras despesas. A aplicação ainda precisa de compilação, testes em dispositivo e validação das regras de negócio antes de ser distribuída a clientes.
