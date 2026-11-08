# StudioMesa — checklist de aceitação no OPPO A5m

## Antes de instalar

Esta build é de demonstração/teste, não uma release comercial. Testar primeiro com contas fictícias e dados de ensaio. Não usar faturas reais, dados de clientes reais ou credenciais de um restaurante pagante.

1. Instalar a variante **Premium Restaurante** e a variante **Premium Cliente**. São as duas que expõem a maioria das áreas necessárias ao ensaio completo.
2. Instalar também **Essencial Restaurante** e **Essencial Cliente** para confirmar as restrições de plano.
3. Se aparecer conflito de assinatura ao atualizar uma instalação antiga, confirmar primeiro que o APK anterior foi assinado com a mesma chave de desenvolvimento. Só desinstalar a versão antiga depois de confirmar que não há dados de teste locais a preservar.
4. Confirmar que as seis variantes têm application IDs diferentes e que não substituem umas às outras.

## Ensaio funcional — gestão e serviço

- [ ] Criar uma conta de teste e configurar um restaurante de teste.
- [ ] Fechar a app e voltar a abrir; confirmar que a sessão é recuperada.
- [ ] Criar mesas, artigos e preços; confirmar que os dados persistem após reiniciar.
- [ ] Criar um funcionário de teste e fazê-lo entrar com o código de equipa.
- [ ] Aplicar os perfis Gerente, Empregado de mesa, Cozinha e Caixa.
- [ ] Em cada perfil, confirmar que os ecrãs desaparecem quando a permissão não existe.
- [ ] Tentar contornar a interface com chamadas não autorizadas; o servidor deve rejeitar a operação.
- [ ] Criar um prato ligado a uma ficha técnica com pelo menos dois ingredientes e unidades coerentes.
- [ ] Guardar a receita, rever e ativar pelo proprietário.
- [ ] Criar um pedido com quantidade conhecida; ao iniciar a preparação, confirmar que o stock diminui uma só vez e que existe um movimento por ingrediente.
- [ ] Reabrir o mesmo pedido e voltar a preparar; confirmar que não existe uma segunda dedução.
- [ ] Tentar preparar um pedido sem stock suficiente; confirmar que nem o estado nem o stock ficam parcialmente alterados.
- [ ] Alterar uma unidade ou ingrediente; confirmar que a receita fica desativada e exige nova aprovação do proprietário.

## Ensaio funcional — cliente e fidelização

- [ ] Associar a app Cliente ao restaurante de teste.
- [ ] Confirmar que o cartão, menu, pontos e histórico correspondem à conta autenticada.
- [ ] Gerar QR de cliente e atribuir pontos uma vez na app Restaurante.
- [ ] Repetir o mesmo pedido com o mesmo identificador; os pontos não podem ser duplicados.
- [ ] Tentar usar um QR expirado ou já utilizado; o servidor deve rejeitar.
- [ ] Criar uma recompensa, definir custo e stock e resgatá-la.
- [ ] Confirmar que os pontos são descontados uma vez e que o stock é reservado no servidor.
- [ ] Confirmar a entrega com funcionário autorizado; a conta Cliente não pode confirmar o próprio resgate.
- [ ] Testar 5/15/50 recompensas ativas nos respetivos planos e fotos apenas no Profissional/Premium.
- [ ] Confirmar que o Essencial não permite criar reservas e que a RPC do servidor também as rejeita.

## Ensaio de notificações

- [ ] Aceitar a permissão Android 13+.
- [ ] Com a app aberta, criar um pedido e uma reserva a partir de outra sessão; confirmar o aviso.
- [ ] Confirmar que o primeiro carregamento não gera avisos antigos.
- [ ] Tocar num aviso e confirmar que abre Cozinha, Reservas, Pontos ou Cartão, conforme o evento.
- [ ] Rejeitar a permissão; a aplicação deve continuar a funcionar sem notificações.
- [ ] Fechar completamente a app: não esperar alertas de novos eventos nesta versão. A consulta atual só corre em primeiro plano e não é FCM.

## Ensaio da IA

- [ ] Com o modelo local instalado, colocar perguntas gerais e testar a resposta offline.
- [ ] Ativar IA online apenas depois de o administrador configurar os segredos do fornecedor no Supabase.
- [ ] Perguntar algo geral sem pedir pesquisa; confirmar resposta Gemini.
- [ ] Perguntar «Dá-me dicas de negócio atualizadas para 2026»; confirmar pesquisa e fontes Tavily.
- [ ] Perguntar sobre faturas no Assistente económico; confirmar que não entra no serviço cloud geral.
- [ ] Confirmar que histórico, Memória, faturas e dados do restaurante não são enviados para a IA online.
- [ ] Desativar a rede e confirmar que o modo online mostra erro claro, enquanto a IA local continua disponível.

## Robustez, privacidade e segurança

- [ ] Testar rede móvel, Wi-Fi, modo de avião e recuperação de ligação.
- [ ] Testar sessão expirada, logout e mudança entre duas contas.
- [ ] Testar rotação de ecrã, voltar atrás, voltar a abrir e fecho forçado.
- [ ] Testar bateria poupada e restrições de execução em segundo plano.
- [ ] Confirmar que uma conta de um restaurante nunca vê pedidos, reservas, receitas, stock, pontos ou notificações de outro.
- [ ] Confirmar que não existem chaves service_role, segredos Gemini/Tavily ou credenciais Firebase dentro do APK.
- [ ] Confirmar que as fotografias de prémios não incluem documentos pessoais ou informação privada.

## Critérios para considerar a primeira venda

A venda só deve avançar depois de todos os ensaios relevantes passarem no dispositivo real, das migrations serem aplicadas de forma controlada ao Supabase do cliente, do plano contratado ficar atribuído no servidor, do APK comercial ter assinatura definitiva e da configuração de autenticação, recuperação de conta e termos de serviço estar validada.

**Bloqueio atual:** as migrations de receitas/permissões/planos desta branch ainda não foram aplicadas ao Supabase alojado. Portanto, a build de teste não deve ser apresentada como produto de produção nem usada para gerir stock real até a reconciliação de migrations, backup e ensaio de deployment ficarem concluídos.
