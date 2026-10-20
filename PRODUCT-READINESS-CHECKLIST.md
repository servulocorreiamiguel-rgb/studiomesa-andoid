# StudioMesa — lista de preparação para uso profissional

**Estado de lançamento nesta revisão: não pronto para operação comercial real.**  
Usar esta lista para decidir o que pode ser demonstrado e o que ainda exige desenvolvimento/testes. Um botão presente na interface não prova que uma funcionalidade esteja completa.

## Carta e menu

- [x] Criar/editar artigos, categorias, descrição, preço e alergénios declarados.
- [x] Separar custo interno do preço e dos campos publicados para a aplicação de cliente (validar novamente por teste de permissões).
- [x] Arquivar artigos sem apagar a informação dos pedidos antigos.
- [ ] Testar todos os campos inválidos, preços, moedas e consistência ao editar em vários dispositivos.
- [ ] Fotografias, horários de disponibilidade, tradução e histórico de edição, se forem necessários para cada cliente.
- [ ] Rever a informação de alergénios com o restaurante. Os dados introduzidos não são uma garantia médica.

## Reservas

- [ ] Ainda não existe um fluxo de reservas completo identificado na versão inspecionada.
- [ ] Definir horários, duração, capacidade por sala/mesa, número de pessoas e intervalos de preparação.
- [ ] Evitar reservas sobrepostas com validação no servidor e transações.
- [ ] Estados da reserva: pedido, confirmada, cancelada, não compareceu e concluída.
- [ ] Confirmações, lembretes, cancelamento e tratamento de falhas de email/mensagens.
- [ ] Testar concorrência em dois dispositivos e alterações ao horário de funcionamento.
- [ ] Definir minimização de dados pessoais, acesso, conservação e eliminação.

## Pedidos e serviço

- [x] Mesas, pedidos, quantidade, fase do serviço, notas e estados básicos.
- [x] Histórico de criação e alteração de estado no servidor.
- [ ] Testar duplicações por repetição após falha de rede e pedidos concorrentes.
- [ ] Testar cancelamentos, sessão expirada, reconexão e sincronização entre dispositivos.
- [ ] Impressora de cozinha, POS/caixa, pagamentos ou faturação não devem ser considerados integrados sem implementação e testes específicos.

## Fidelização

- [x] Associação por código, saldo partilhado e registo de movimentos.
- [ ] Automatizar regras de acumulação/resgate só depois de definir regras comerciais e prevenção de fraude.
- [ ] Limitar tentativas repetidas de adivinhar códigos e monitorizar abusos.
- [ ] Testar saldos negativos, limites, concorrência e reconciliação dos movimentos.
- [ ] Proteger os dados dos clientes e confirmar que cada cliente só vê o próprio cartão/histórico.

## Administração e equipa

- [x] Separação de contas, papéis owner/staff e código de adesão de equipa.
- [ ] Permissões granulares por função; atualmente as operações da equipa são amplas.
- [ ] Remover/suspender funcionário e revogar imediatamente acessos.
- [ ] Rever rotação de códigos, acesso a dados exportados e trilho de auditoria administrativo completo.
- [ ] Testar que uma conta nunca consegue aceder a outro restaurante.

## Segurança e continuidade

- [x] RLS ativa nas tabelas principais do esquema público inspecionado.
- [ ] Rever a tabela privada `private.restaurant_join_codes`, cuja RLS está desativada no projeto observado. Os papéis `anon` e `authenticated` não tinham uso de esquema nem privilégios diretos sobre essa tabela na consulta realizada; ainda assim, é necessária uma decisão explícita sobre defesa em profundidade.
- [x] As funções `SECURITY DEFINER` inspecionadas exigem sessão e as permissões de execução para `anon` estavam revogadas para os RPCs listados pelo Security Advisor.
- [ ] Proteção contra palavras-passe comprometidas no Supabase Auth.
- [x] Sessões guardadas com cifragem AES-GCM usando Android Keystore no código inspecionado.
- [x] Código PKCE em OAuth, registo e recuperação, com verificador cifrado e rejeição de tokens bearer na ligação de retorno.
- [x] Logout local limpa os tokens e inicia a revogação da sessão atual no Supabase em segundo plano.
- [ ] Testar login, recuperação, registo e revogação de sessão num dispositivo real; o logout remoto é best effort quando a rede falha.
- [ ] Confirmar backups disponíveis, retenção e executar um restauro de teste num ambiente separado.
- [ ] Testar exportação/eliminação de dados, registos, conservação e resposta a incidentes.
- [ ] Não colocar chaves `service_role` ou `sb_secret_...` na aplicação. A chave `sb_publishable_...` incorporada no cliente é pública por natureza e depende de RLS/políticas corretas.

## Lançamento e suporte

- [x] Workflow atual compila APKs de demonstração/debug.
- [ ] Não reutilizar a assinatura debug para uma versão comercial.
- [ ] Criar release assinada com chave de produção protegida fora do repositório.
- [ ] Automatizar testes e análise estática e guardar os resultados de cada release.
- [ ] Definir monitorização, canal de suporte, prazo de resposta, atualizações, manutenção e procedimento de recuperação.
- [ ] Definir contrato, âmbito funcional, titularidade dos dados, privacidade, conservação e responsabilidades com aconselhamento adequado.

## Comercialização e preço

Os valores de referência atuais são 890 €, 1 490 € e 1 690 €. Ainda não constituem preços validados com clientes e o âmbito comercial tem de esclarecer pagamento único ou recorrente, alojamento, suporte, atualizações, configuração inicial e impostos aplicáveis. Não prometer uma funcionalidade marcada como pendente.
