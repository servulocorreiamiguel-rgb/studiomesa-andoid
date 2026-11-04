# Loja de pontos StudioMesa — desenho

## Intenção
Completar a fidelização já existente com uma loja de recompensas útil para o cliente e simples de gerir pelo restaurante. Cada catálogo pertence a um único restaurante, e os pontos continuam a ser controlados no servidor.

## Objetivos
- O proprietário cria, altera, ativa e desativa recompensas.
- Cada recompensa tem nome, descrição, custo em pontos e limite de stock opcional.
- O cliente consulta as recompensas disponíveis, troca pontos e acompanha os pedidos.
- O restaurante consulta pedidos pendentes e confirma a entrega.
- Um resgate desconta os pontos e reserva stock numa única transação, sem duplicações.
- A autorização é sempre validada no Supabase; a interface não substitui as verificações do servidor.

## Fora desta fase
- Fotografias de prémios, logótipos personalizados e temas visuais por restaurante.
- Pagamentos, devoluções de dinheiro e integração com faturação.
- Alteração automática da regra de acumulação por euro. A regra do QR da fatura continua independente e explícita.

## Abordagens consideradas
1. Reescrever a aplicação em framework diferente: rejeitada, porque a app atual é Kotlin nativo e o backend já tem RPCs de fidelização.
2. Criar uma loja Web separada: rejeitada nesta fase, porque aumentaria a manutenção e criaria outro fluxo de autenticação.
3. Completar a experiência nas apps Kotlin existentes e reutilizar os RPCs Supabase: escolhida como decisão de implementação, porque aproveita o trabalho já feito e mantém os dados por restaurante.

## Comportamento e segurança
- O cliente só vê recompensas do restaurante associado ao seu cartão.
- O servidor valida saldo, estado, stock e idempotência antes de aceitar um resgate.
- Apenas o proprietário pode criar ou editar recompensas.
- A equipa autorizada do restaurante pode consultar e confirmar entregas.
- O cliente só consulta o próprio histórico.
- O QR de fidelização continua separado do QR fiscal da fatura.

## Interface proposta
- App Restaurante, secção Pontos: gestão do catálogo e lista de resgates pendentes.
- App Cliente, separador Prémios: catálogo, pontos necessários, disponibilidade e histórico de resgates.
- Nesta fase, os cartões de recompensa são textuais, com layout consistente com o design atual da StudioMesa.
