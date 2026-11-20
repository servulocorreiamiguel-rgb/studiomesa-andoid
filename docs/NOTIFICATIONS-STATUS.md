# Notificações StudioMesa — estado e limites

## O que esta branch acrescenta

- Canal Android `Atividade do StudioMesa`.
- Pedido da permissão de notificações Android 13+ quando o utilizador tem sessão iniciada.
- Verificação periódica enquanto a aplicação está aberta/em primeiro plano.
- Cliente: alteração de estado das próprias reservas e confirmação de entrega de recompensas.
- Restaurante: novos pedidos e novas reservas; o proprietário também recebe novos resgates na loja de pontos.
- Snapshot por conta/aplicação, para evitar repetir notificações e não disparar alertas antigos no primeiro carregamento.

Os textos das notificações são genéricos e não incluem nome, telefone, valor de fatura ou conteúdo privado. Os pedidos ao servidor usam a sessão autenticada existente.

## O que ainda não é

Isto não é Firebase Cloud Messaging e não envia alertas quando a aplicação está fechada, parada ou sem execução em primeiro plano. A verificação só ocorre enquanto a Activity está retomada; usa uma consulta periódica e não é uma subscrição em tempo real. A frequência atual é de 60 segundos.

## Requisitos para push real

1. Criar um projeto Firebase sob controlo do responsável pelo produto.
2. Registar a aplicação Android e os identificadores finais de pacote.
3. Adicionar o SDK Firebase Messaging e configurar o ficheiro de serviços correto por variante, sem o publicar no repositório se contiver credenciais.
4. Pedir consentimento/permissão e obter o token FCM no dispositivo.
5. Guardar o token associado à conta/dispositivo no Supabase, com RLS e política de revogação/rotação.
6. Criar um sender server-side com credenciais Firebase guardadas exclusivamente em secrets.
7. Enviar mensagens apenas após validar a autorização e a relação com o restaurante/conta destinatária.
8. Testar logout, mudança de conta, token renovado, notificações duplicadas, app fechada, modo de poupança de bateria, Android 13+, Android 16 e dados sem rede.

Não colocar service-account JSON, chaves privadas Firebase ou tokens FCM em Kotlin, no APK, em commits ou em logs. Não ativar push até os testes de isolamento e consentimento passarem.
