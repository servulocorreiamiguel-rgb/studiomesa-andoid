# Permissões granulares da equipa — desenho

## Objetivo
Permitir que o proprietário escolha que áreas operacionais cada funcionário pode utilizar, sem depender apenas da distinção atual entre `owner` e `staff`. A autorização real tem de ser aplicada no Supabase; esconder botões no Android não é segurança.

## Estado atual observado
- O servidor identifica membros em `public.restaurant_staff` com os papéis `owner` e `staff`.
- O ecrã de equipa permite ao proprietário consultar e remover funcionários, mas não configurar permissões por funcionário.
- A RLS atual dá a qualquer funcionário acesso operacional amplo a mesas e pedidos.
- As operações de inventário, reservas, fidelização e menu têm RPCs/políticas próprias e precisam de verificação individual; não basta alterar a interface.
- A aplicação já guarda o papel da sessão em preferências locais para apresentação. Esse valor não pode ser fonte de autorização.

## Abordagens consideradas
1. **Só ocultar secções e botões no Android:** simples, mas inseguro; as chamadas diretas ao servidor continuariam possíveis. Rejeitada.
2. **Criar novos papéis fixos (gerente, cozinha, sala):** fácil de explicar, mas rígido para restaurantes pequenos e não permite adaptar funções. Rejeitada nesta fase.
3. **Permissões por área, guardadas no servidor:** o proprietário ativa/desativa módulos por funcionário; as políticas RLS e RPCs verificam a permissão no servidor. Escolhida por ser flexível, manter a experiência simples e preservar a arquitetura existente.

## Decisões de produto
- Áreas configuráveis: **Pedidos**, **Mesas**, **Reservas**, **Carta**, **Stock** e **Fidelização operacional**.
- Cada área é um interruptor que permite consultar e executar operações normais dessa área.
- O proprietário mantém sempre acesso total e não pode ser limitado por esta matriz.
- Ações sensíveis permanecem exclusivas do proprietário, mesmo que a área correspondente esteja ativa: gerir equipa/códigos, gerir catálogo e custo em pontos das recompensas, consultor económico/preços, configurações do restaurante e eliminação do restaurante.
- A permissão de fidelização permite ao funcionário atribuir pontos através do fluxo QR e confirmar entregas de recompensas, mas não alterar o catálogo.
- Para não bloquear restaurantes já configurados, funcionários existentes mantêm o acesso operacional atual até o proprietário guardar explicitamente uma matriz personalizada. Depois disso, só ficam autorizadas as áreas selecionadas.
- Uma revogação é aplicada no servidor imediatamente. Os ecrãs Android devem atualizar as permissões após resposta do servidor e não devem confiar numa cache antiga.
- O utilizador que não pertence ao restaurante, um membro removido ou uma sessão expirada não recebe permissões.

## Fora de âmbito
- Criar pagamentos, POS, faturação certificada ou permissões por ação individual.
- Alterar QR, loja de recompensas ou o desenho de resgate já desenvolvido, salvo os controlos de autorização necessários.
- Tornar o plano Supabase pago ou prometer backups que o plano atual não garante.

## Critérios de aceitação
1. Só o proprietário consegue ler e alterar as permissões de um funcionário do seu próprio restaurante.
2. Um funcionário não consegue conceder permissões a si próprio, a outro funcionário ou a um membro de outro restaurante.
3. Desativar uma área bloqueia a leitura/escrita relevante no servidor, mesmo chamando diretamente REST/RPC.
4. Ativar uma área permite as operações previstas dessa área, sem dar acesso a ações exclusivas do proprietário.
5. A remoção de um funcionário revoga todas as permissões e a sessão deixa de conseguir operar no restaurante.
6. Os funcionários existentes mantêm o comportamento anterior até o proprietário guardar a configuração.
7. A matriz é carregada do servidor ao iniciar sessão e após alterações; o cliente não usa preferências locais como fonte de autorização.
8. Os testes cobrem owner/staff, duas contas de restaurantes diferentes, permissões vazias, permissões parciais, sessão expirada e tentativas diretas ao servidor.