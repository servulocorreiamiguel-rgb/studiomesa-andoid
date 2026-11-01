# Sistema visual StudioMesa

**Estado:** guia de design em desenvolvimento. Não representa validação visual nem aprovação para produção.

## Identidade

A interface usa uma identidade discreta, inspirada em restauração contemporânea: verde profundo, verde de apoio, cobre suave e fundos quentes. O objetivo é transmitir organização e confiança sem usar cores demasiado saturadas ou efeitos decorativos excessivos.

| Token | Valor | Utilização |
|---|---|---|
| Forest | `#394E43` | Cabeçalhos e elementos de marca |
| Forest Deep | `#2B3B32` | Títulos e texto de destaque |
| Forest Soft | `#4D6557` | Texto secundário e ícones |
| Copper | `#D3B18B` | Ações principais e destaque ativo |
| Copper Pale | `#F5EDE0` | Seleção e avisos neutros |
| Paper | `#F8F8F4` | Fundo geral |
| Pale Green | `#EBF2EC` | Estados positivos e seleções suaves |
| Pale Red | `#F9EDE9` | Alertas e stock esgotado |
| Ink | `#2F3933` | Texto corrente |
| Muted | `#6A746C` | Texto auxiliar |

## Tipografia e hierarquia

- Títulos de ecrã: aproximadamente 25 sp, com peso forte.
- Títulos de secção: 17 sp.
- Títulos de cartões: 14–18 sp, conforme a importância.
- Texto corrente: 12–14 sp.
- Etiquetas auxiliares: 9–11 sp, em maiúsculas apenas quando são rótulos curtos.
- Valores económicos e quantidades importantes devem ter destaque numérico e permanecer legíveis em ecrãs estreitos.

## Componentes

### Cartões
- Fundo branco ou uma tonalidade suave com significado.
- Cantos arredondados próximos de 18–19 dp.
- Espaçamento interno consistente.
- Sombra discreta; evitar cartões fortemente elevados.
- Não colocar uma segunda ação visualmente dominante dentro de cada cartão sem necessidade.

### Ações
- Ação principal: cobre, texto escuro e altura tátil confortável.
- Ação secundária: fundo verde pálido, sem competir com a ação principal.
- Áreas clicáveis devem ter pelo menos cerca de 44 dp de altura sempre que possível.
- Elementos interativos têm descrição acessível e estado de foco.

### Estados
- Pedidos, cozinha, reservas e stock usam etiquetas arredondadas consistentes.
- A cor nunca substitui o texto do estado.
- Verde identifica situações confirmadas/positivas; cobre indica estados pendentes ou neutros; vermelho assinala risco ou stock esgotado.

### Navegação
- O menu lateral `☰` contém as áreas agrupadas por operação, gestão, assistência e conta.
- A navegação inferior mantém apenas destinos frequentes e indica claramente a secção ativa.
- A navegação inferior é ocultada no Assistente, que usa uma caixa de mensagem fixa na zona inferior.
- As áreas de gestão e cliente reutilizam o mesmo componente de navegação inferior.

### Filtros
- Filtros frequentes são apresentados como seletores visuais horizontais.
- Em ecrãs estreitos, a fila pode deslocar-se horizontalmente sem encolher os rótulos até ficarem ilegíveis.
- O filtro selecionado tem fundo e texto distintos, além de descrição acessível.

## Ecrãs

- **Início:** indicadores operacionais em grelha e acesso rápido em cartões compactos de duas colunas.
- **Mesas:** ações de adicionar e atualizar agrupadas; resumo global e filtros horizontais para todas/livres/ocupadas; cartões de mesa com estado, zona e capacidade.
- **Pedidos:** resumo por estado, filtros horizontais para todos/ativos/concluídos/cancelados, detalhes do artigo, ações secundárias agrupadas e etiquetas consistentes.
- **Cozinha:** resumo da fila por estado, filtros horizontais para todos/novos/em preparação/prontos, prioridade primeiro por estado e depois por antiguidade, mesa, notas e tempo de espera.
- **Carta:** ações de adicionar e atualizar agrupadas; indicadores de artigos/custos a rever; pesquisa por nome/categoria/descrição/alergénios e filtros horizontais por categoria na gestão e no cliente; custos organizados sem exposição na aplicação de cliente.
- **Reservas:** ações de criar e atualizar agrupadas; indicadores, filtros horizontais e cartões com data, pessoas, mesa e estado.
- **Stock:** ações de adicionar e atualizar agrupadas; resumo de artigos e alertas antes da lista; pesquisa por nome/unidade e filtros horizontais para todos/esgotados/stock baixo/sem custo; quantidade em destaque e ações de entrada, saída, edição e desperdício.
- **Gestão económica e análise:** ativação destacada, desativação/limpeza com estilo de ação perigosa, atalhos horizontais para ferramentas e períodos; valores e pressupostos identificados, sem aparência de contabilidade certificada.
- **Conta e sincronização:** dados da sessão e papel da equipa separados das ações de segurança; atalhos de configuração para Mesas, Carta e Pedidos.
- **Assistente:** conversa em primeiro plano; análises e recomendações adicionais recolhidas, mas acessíveis.
- **Fidelização:** lista de clientes com pesquisa por nome ou código do cartão, contagem de resultados e estado vazio quando não há correspondências.
- **Cliente:** cartão de fidelização, carta publicada com pesquisa e filtros horizontais por categoria, reservas e conta com estado de associação, email da sessão, restaurante ligado e ações de segurança separadas.

## Critérios para fechar o design

Antes de iniciar a bateria final de testes, rever visualmente todas as variantes e confirmar:
1. Ecrãs pequenos, médios e tablets; retrato e paisagem.
2. Teclado aberto, campos longos e deslocação até ao último elemento.
3. Títulos e botões sem cortes ou sobreposição; nomes longos de restaurante e artigos.
4. Estados vazios, carregamento, erro e dados reais.
5. Contraste, tamanhos de letra, foco, descrições acessíveis e áreas de toque.
6. Consistência entre Essencial, Profissional, Premium e aplicação de cliente.

**Importante:** os critérios acima são uma lista de revisão. Só devem ser marcados como concluídos depois de observação e evidência na aplicação compilada.