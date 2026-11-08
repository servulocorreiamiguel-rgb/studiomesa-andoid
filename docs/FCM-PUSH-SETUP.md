# StudioMesa — ativação de notificações push em segundo plano

## Estado

A branch atual já integra notificações locais de atividade em primeiro plano: novos pedidos, novas reservas e novos resgates para a gestão; alterações de reservas e entrega de recompensas para o cliente. O snapshot evita duplicados e o primeiro carregamento cria apenas uma linha de base. A app consulta o servidor de 60 em 60 segundos enquanto a Activity está retomada e abre a secção certa ao tocar no aviso. Isto não é push remoto e não garante avisos depois de fechar a app.

As notificações push requerem um projeto Firebase e credenciais de servidor controladas pelo responsável pela StudioMesa. Não se deve inventar nem incluir credenciais no repositório.

## Bloqueios externos

Antes de ativar FCM, é necessário:

1. Escolher e congelar os application IDs comerciais de Gestão e Cliente.
2. Criar um projeto Firebase e registar cada aplicação Android com o application ID final.
3. Descarregar o `google-services.json` correspondente e guardá-lo no ambiente de build seguro; não reutilizar um ficheiro de outra aplicação.
4. Ativar Firebase Cloud Messaging HTTP v1.
5. Criar uma conta de serviço com permissões mínimas e guardar a credencial como segredo no servidor, nunca no APK.
6. Definir os eventos que enviam avisos: nova reserva, alteração de reserva, novo pedido, pedido pronto e resgate de recompensa.

## Implementação necessária

- SDK Firebase Messaging e serviço `FirebaseMessagingService`.
- Registo e rotação dos tokens por instalação, utilizador, restaurante e variante da app.
- Tabela privada no Supabase para tokens, com RLS fechado e RPCs autenticadas. A conta do cliente não pode escolher o restaurante nem enviar notificações.
- Edge Function que valida o evento/tenant e envia apenas aos dispositivos autorizados através da API FCM HTTP v1.
- Revogação do token quando a sessão é removida, quando o funcionário sai da equipa e quando o token deixa de ser válido.
- Notificações sem dados pessoais ou financeiros sensíveis no texto visível do ecrã bloqueado.
- Deduplicação para evitar vários avisos do mesmo evento e ligação profunda para abrir a página correta.

## Permissão Android

No Android 13 ou superior, a app tem de pedir em tempo de execução `POST_NOTIFICATIONS`; sem consentimento, o sistema não mostra notificações. Criar os canais de notificação enquanto a app está aberta e explicar ao utilizador o que vai receber.

Referência oficial: https://firebase.google.com/docs/cloud-messaging/android/get-started

## Testes obrigatórios

- Android 8 e Android 16: app aberta, em segundo plano, terminada, sem rede e depois de recuperar rede.
- Permissão permitida e negada.
- Token renovado e token antigo revogado.
- Proprietário, gerente, empregado de mesa, cozinha, caixa e utilizador de outro restaurante.
- Garantir que um pedido de A nunca notifica B e que a app Cliente não recebe notificações internas da equipa.
- Confirmar que não existe credencial de serviço dentro de APKs, logs, commits ou artefactos públicos.

**Não anunciar notificações push como concluídas até a configuração FCM, backend, autorização e ensaio em dispositivo real passarem.**
