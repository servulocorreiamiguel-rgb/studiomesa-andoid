# Revisão de design da StudioMesa — 27/10/2026

## Âmbito

Revisão estática da interface Android atual e comparação de padrões públicos de aplicações de restauração conhecidas. Esta revisão é uma avaliação de design e de fluxo; não substitui testes com funcionários de um restaurante, nem afirma que todas as integrações comerciais estão prontas.

## O que já funciona visualmente

- A paleta verde-floresta, cobre e fundos claros cria uma identidade consistente e mais suave do que um POS com cores demasiado agressivas.
- O ecrã inicial tem uma hierarquia legível, indicadores operacionais e cartões de acesso rápido.
- As páginas de gestão separam as áreas em secções distintas, e há estados vazios/mensagens de demonstração em vários fluxos.
- O aviso de demonstração é importante: evita apresentar os dados locais de exemplo como se fossem operações reais partilhadas.

A recomendação é **refinar a interface existente**, não fazer uma mudança radical de cores ou de identidade.

## Alteração prioritária implementada nesta revisão

### Navegação móvel

A barra anterior apresentava todas as secções numa faixa horizontal, obrigando a deslocar lateralmente para descobrir algumas delas. Foi substituída por uma navegação fixa com três destinos frequentes — **Início, Mesas e Pedidos** — e um botão **☰** que abre um painel lateral com todas as páginas disponíveis no plano ativo. As secções não disponíveis num plano não aparecem no painel.

Este padrão acompanha uma tendência observável em POS conhecidos: a Lightspeed descreve navegação móvel dedicada para funções operacionais frequentes e opções secundárias no menu “More”, em vez de mostrar todas as funções como separadores principais. A Lightspeed L-Series descreve cinco secções principais com acesso separado a perfil/relatórios. A Square for Restaurants e a Toast também organizam os fluxos de pedido e os indicadores operacionais em áreas específicas.

Referências públicas:
- [Lightspeed K-Series — navegação e menu More](https://k-series-support.lightspeedhq.com/hc/en-us/articles/43162671781659-About-the-new-POS-navigation)
- [Lightspeed L-Series — navegação do POS](https://resto-support.lightspeedhq.com/hc/en-us/articles/360005777873-About-navigation-in-Restaurant-POS)
- [Square for Restaurants — configuração e utilização do POS](https://squareup.com/help/au/en/article/6407-get-started-with-square-for-restaurants)
- [Toast POS — produto e interface](https://pos.toasttab.com/products/point-of-sale)

## O que falta melhorar, por prioridade

### Prioridade 1 — antes de uma utilização comercial real

1. **Estado de sincronização compreensível.** Distinguir “conta configurada” de “ligação ativa”, “a atualizar”, “sem ligação” e “última atualização”. Um rótulo estático de sistema ligado não deve ser usado como prova de que a rede está operacional naquele instante.
2. **Feedback uniforme para ações.** Rever carregamento, sucesso, falha, repetição e recuperação de rede em criar/editar pedidos, reservas, artigos, pontos e stock. O utilizador deve perceber se uma operação foi guardada no servidor ou ficou apenas local.
3. **Acessibilidade e diferentes tamanhos de ecrã.** Validar escala de texto do Android, contraste, foco por teclado/leitor de ecrã e alvos táteis; verificar telemóveis estreitos, orientação horizontal e tablets. O novo menu usa descrições acessíveis nos destinos de navegação, mas isto não significa que toda a aplicação já tenha sido auditada para acessibilidade.
4. **Dados reais no painel.** O painel deve mostrar só indicadores que o sistema possa calcular de forma fiável. Vendas do dia, receita, comparações semanais e custo laboral não devem ser simulados: só devem aparecer quando existirem dados e integrações reais para os suportar.
5. **Pesquisa e filtros nas listas longas.** Confirmar a necessidade de pesquisa por número/estado/data nas reservas e pedidos, e de pesquisa/filtro na carta e no stock. Isto torna-se importante quando o restaurante tem dezenas ou centenas de registos.

### Prioridade 2 — melhorias de produto visual

6. **Calendário visual de reservas.** A lista atual é útil para registo, mas uma vista por dia/hora/capacidade ajudaria a detetar conflitos. Só avançar depois de fechar horários de funcionamento, reagendamento e regras de capacidade.
7. **Fotografias dos artigos.** Uma carta com imagens aprovadas pelo restaurante pode facilitar a gestão visual e, numa futura experiência para clientes, tornar a carta mais apelativa. Não inventar fotografias nem publicar imagens sem aprovação do restaurante.
8. **Vista de tablet.** Em tablets pode fazer sentido uma navegação lateral persistente e mais informação lado a lado; no telemóvel deve manter-se a navegação compacta aplicada nesta revisão.
9. **Identidade por restaurante.** Depois de o restaurante configurar o espaço, considerar logótipo e nome verdadeiros no cabeçalho, sem perder a identidade StudioMesa e sem mostrar nomes de demonstração em produção.
10. **Análise visual.** Os gráficos só devem ser acrescentados quando houver dados históricos suficientes e claros. Os ecrãs de análise não devem mostrar gráficos vazios ou números decorativos.

## Aplicação de cliente: o que falta clarificar

A aplicação de cliente tem uma base visual coerente — cartão de fidelização, saldo de pontos e carta publicada —, mas o fluxo atual é essencialmente uma **experiência de fidelização e consulta da carta**, não uma aplicação completa de encomendas ou reservas.

1. **Prémios e regras de pontos:** o cartão mostra “Próximos prémios”, mas o próprio ecrã indica que as regras ainda serão definidas com o restaurante. Antes de mostrar esta área a clientes reais, definir e implementar regras, ou ocultar o bloco até existir conteúdo real.
2. **Carta mais visual:** os artigos aparecem como texto, descrição, preço e indicação de alergénios; não encontrei um fluxo de fotografias de artigos. Fotografias aprovadas, categorias e pesquisa ajudariam numa carta longa. Os alergénios devem continuar a ser declarados pelo restaurante e apresentados com a indicação para confirmar com a equipa.
3. **Separar tarefas quando o produto crescer:** o ecrã é atualmente uma página longa, com cartão, carta e perfil. Para um produto com mais tarefas, considerar três áreas claras — Cartão, Carta e Conta — em vez de acrescentar cada vez mais conteúdo à mesma página.
4. **Não prometer fluxos ainda não presentes:** não encontrei no ecrã de cliente um fluxo de encomenda ou de reserva. Se esses serviços fizerem parte da oferta comercial, terão de ser concebidos e testados como funcionalidades próprias; caso contrário, descrever o produto como cartão de fidelização e consulta da carta.
5. **Estados da conta fáceis de entender:** manter claras as diferenças entre cartão demonstrativo, conta ainda não associada, sessão iniciada e saldo atualizado. Não apresentar pontos de demonstração como saldo real.

## Fora do âmbito desta alteração

A navegação não torna automaticamente a aplicação pronta para venda. Continuam a exigir validação separada os testes em dispositivos reais, sessões expiradas/rede instável, sincronização concorrente, cópias de segurança e recuperação, privacidade, configuração de autenticação e uma compilação comercial assinada. POS/pagamentos, faturação, impressoras, lembretes automáticos e integrações só devem ser anunciados quando estiverem implementados e testados.

## Critério de aceitação da navegação

- O botão ☰ abre o painel lateral e todas as secções disponíveis no plano podem ser abertas sem deslocação horizontal.
- Início, Mesas e Pedidos estão sempre visíveis no fundo do ecrã de gestão.
- O painel respeita as funcionalidades dos planos Essencial, Profissional e Premium.
- Testes de interface cobrem a visibilidade dos destinos, o conteúdo do painel por plano e a abertura de uma secção secundária.
