# StudioMesa Android

Aplicação Android nativa para apoiar a gestão diária de um restaurante.

## Funcionalidades desta versão de desenvolvimento

- Painel com resumo de mesas, pedidos e reservas pendentes.
- Estado de 12 mesas (livre/ocupada).
- Criação e acompanhamento de pedidos.
- Gestão simples do menu, com preço de venda e custo por unidade.
- Calculadora de preço sugerido baseada no custo e na percentagem de custo pretendida.
- Gestão local de reservas: nome, telefone, data, hora, número de pessoas, notas e estado (pendente, confirmada, recusada, cancelada ou concluída).
- Personalização básica do restaurante: nome, frase, telefone, morada e cor de destaque.
- Preços de referência dos planos StudioMesa.

## Tecnologia

- Kotlin e componentes nativos Android.
- minSdk 24 (Android 7.0 ou posterior).
- Dados guardados localmente em SharedPreferences.

## Limitação importante: reservas ainda não são online

A gestão de reservas que está nesta branch é local e serve para testar o fluxo dentro da app. Não recebe reservas de clientes a partir da Internet, não envia mensagens de confirmação e não sincroniza com outros telemóveis. A marca configurada também ainda não inclui logótipo ou fotografias.

Para reservas reais será necessário configurar uma base de dados e autenticação num serviço como Supabase, aplicar regras de segurança, criar uma página pública de reservas e ligar a app de gestão à mesma base de dados. A disponibilidade tem de respeitar o horário, capacidade e regras do restaurante. Não publicar nem vender esta versão como sistema de reservas online até testar esses pontos.

## Gerar um APK

O workflow do GitHub Actions em Actions → Build StudioMesa Android APK compila a app quando recebe alterações na main ou nesta branch de desenvolvimento. Quando a compilação terminar com sucesso, o ficheiro StudioMesa-debug-apk fica disponível como artefacto da execução durante 14 dias.

Também é possível compilar num ambiente com Java 17, Gradle 8.9 e Android SDK instalado:

    gradle --no-daemon assembleDebug

O APK de depuração é gerado em app/build/outputs/apk/debug/app-debug.apk.

## Antes de uma utilização comercial

É necessário configurar a base de dados online, contas e permissões por restaurante, regras de disponibilidade, confirmação/cancelamento, notificações, privacidade e proteção dos dados pessoais. Também são necessários testes reais em vários dispositivos. Esta branch não deve substituir ainda a versão que já instalaste.
