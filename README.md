# StudioMesa Android — três demos instaláveis

Aplicação Android nativa para demonstrações presenciais a restaurantes. As variantes **Essencial**, **Profissional** e **Premium** usam o mesmo design-base, identificam o plano e instalam-se como apps separadas para não substituir a aplicação original.

## Variantes de demonstração

- **Essencial — 890 €:** mesas, pedidos, menu, reservas locais, personalização da identidade e calculadora de preço por custo.
- **Profissional — 1 490 €:** tudo do Essencial e relatórios locais de pedidos/reservas.
- **Premium — 1 690 €:** tudo do Profissional e lista de clientes/histórico derivado das reservas.

Os preços são valores de referência existentes no protótipo; a periodicidade, o alojamento, o suporte, a manutenção, as atualizações e o contrato devem ser definidos antes de uma oferta comercial.

## Dados de demonstração

Cada instalação é identificada como uma demo e começa com dados fictícios para permitir apresentar os ecrãs: mesas de exemplo, pedidos, menu e reservas. O ecrã Restaurante permite mudar o nome, frase, contactos e cor de destaque. A ação “Repor dados fictícios” apaga as alterações locais desse APK e repõe o conjunto de demonstração.

As variantes têm IDs de aplicação diferentes e podem ser instaladas lado a lado no mesmo telemóvel.

## Limitação importante: ainda não é um sistema de reservas online

As reservas são guardadas localmente no telemóvel. Não há página pública para o cliente, sincronização entre dispositivos, notificações nem confirmações automáticas. Não vender nem apresentar estas demos como serviço de reservas online pronto. Antes de uso comercial, é necessário configurar uma base de dados online com autenticação e regras de acesso, construir a página pública de reservas, implementar disponibilidade/capacidade, notificações e cancelamentos, além de testes em dispositivos diferentes e revisão da proteção dos dados pessoais.

## Compilação

O workflow **Actions → Build StudioMesa Android APK** compila as três variantes de depuração e publica o artefacto `StudioMesa-3-Demos` durante 14 dias. As tarefas são:

    gradle --no-daemon assembleEssencialDebug assembleProfissionalDebug assemblePremiumDebug

Os APKs são criados em `app/build/outputs/apk/{essencial,profissional,premium}/debug/`.

## Verificação

A compilação confirma que o código Kotlin e os recursos conseguem ser transformados em APKs. Não prova que todos os fluxos funcionam sem falhas num dispositivo; antes de uso real, testar navegação, formularios, datas/horas, guardar/repor dados, permissões, estados e comportamento em diferentes tamanhos de ecrã.
