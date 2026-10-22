# StudioMesa — rascunho de política de privacidade

> **ESTADO: RASCUNHO — NÃO PUBLICAR AINDA.** Antes de usar esta política na Google Play, o responsável legal pela aplicação tem de preencher todos os campos `[A PREENCHER]`, confirmar os períodos de conservação e os fornecedores reais, e verificar que cada declaração corresponde ao comportamento publicado da aplicação. Não inventar identidade legal, morada, email de contacto, retenções ou certificações.

**Última revisão técnica do código:** 22 de outubro de 2026.  
**Nome da aplicação:** StudioMesa.

## 1. Responsável pelo tratamento

Responsável legal: **[A PREENCHER — nome legal ou entidade que opera o StudioMesa]**.  
Contacto para privacidade e exercício de direitos: **[A PREENCHER — endereço de email profissional controlado pelo responsável]**.  
Morada ou outro contacto legal exigível: **[A PREENCHER após confirmação dos requisitos aplicáveis]**.

## 2. Dados tratados

Conforme as funcionalidades utilizadas, o StudioMesa pode tratar:

- **Conta e autenticação:** endereço de email, identificador de conta e dados técnicos da sessão geridos pelo serviço de autenticação. A aplicação Android não recebe nem guarda uma chave administrativa do Supabase; a palavra-passe é submetida ao fornecedor de autenticação e não é apresentada pelo StudioMesa.
- **Dados do restaurante e da equipa:** nome do restaurante, contas e papéis de proprietários/funcionários, códigos de adesão e dados de configuração.
- **Operação:** mesas e zonas, artigos e categorias da carta, preços e custos internos, pedidos, quantidades, estados, notas de cozinha, reservas, números de pessoas, datas e horas, contactos fornecidos para reservas, inventário e movimentos de stock.
- **Fidelização:** nome apresentado, código de cliente, associação ao restaurante, pontos e respetivo histórico.
- **Dados locais de demonstração:** a versão local de demonstração guarda dados de exemplo no armazenamento do dispositivo. Os testes de produção devem confirmar que nenhuma cache de uma conta anterior é mostrada a outra conta.
- **Dados técnicos de segurança:** metadados da sessão e referências de auditoria de operações necessárias ao funcionamento e à prevenção de duplicações. Não foram identificadas integrações de publicidade, pagamentos ou analytics no código revisto.

O proprietário do restaurante deve introduzir apenas os dados necessários à operação e informar os clientes/funcionários sobre o tratamento de dados que lhes diz respeito.

## 3. Finalidades e fundamento

Os dados são usados para autenticar contas, permitir acesso por papel ao restaurante correto, sincronizar operações, gerir pedidos/reservas/inventário/fidelização, evitar gravações duplicadas, proteger sessões e processar pedidos de eliminação.

**[A PREENCHER — o responsável deve confirmar os fundamentos jurídicos aplicáveis a cada finalidade, incluindo execução contratual, obrigação legal, interesse legítimo ou consentimento quando necessário.]**

## 4. Fornecedores e localização

O backend atual usa Supabase, com o projeto configurado na região EU West 1. O envio de mensagens de autenticação depende da configuração do serviço de email do projeto.

**[A PREENCHER — confirmar região real de todos os componentes, SMTP/provedor de email, subprocessadores aplicáveis, retenção de cópias de segurança e eventuais transferências internacionais. Rever os termos e anexos de tratamento de dados do fornecedor.]**

## 5. Acesso e segurança

A aplicação utiliza uma chave publicável do Supabase no cliente móvel. Essa chave não é uma credencial administrativa; o acesso a dados é condicionado por autenticação, Row Level Security e funções do servidor. As funções privilegiadas são mantidas no backend, não na aplicação Android.

Foram implementadas verificações de sessão e autorização nas operações analisadas, bem como separação de dados de restaurante e uma função de eliminação de conta. Isto não substitui a auditoria independente nem os testes funcionais de segurança antes do lançamento.

## 6. Conservação e eliminação

Os períodos de conservação de cada tipo de registo devem ser definidos pelo responsável:

- Conta e perfil: **[A PREENCHER]**.
- Dados operacionais do restaurante, pedidos e reservas: **[A PREENCHER]**.
- Histórico de inventário, auditoria e fidelização: **[A PREENCHER — considerar obrigações legais e necessidades legítimas de auditoria]**.
- Cópias de segurança e logs do fornecedor: **[A PREENCHER após confirmar as definições e o plano de alojamento]**.

A app inclui uma opção para iniciar a eliminação da conta e existe um recurso web externo:  
https://svwvkfsyevuyabelrtjk.supabase.co/functions/v1/account-deletion-web

A eliminação da conta de um proprietário pode apagar permanentemente os dados do restaurante associado, depois de confirmação explícita e apenas quando não existirem outros membros da equipa. Para utilizadores não proprietários, os registos operacionais partilhados podem ter de ser preservados; quando suportado, a autoria é anonimizada. Estas regras devem ser confirmadas por testes com contas descartáveis e refletidas com precisão na declaração Data safety. Não tratar o fluxo como validado até que os testes de ponta a ponta passem.

## 7. Direitos das pessoas

Sujeito à legislação aplicável, os titulares podem solicitar acesso, correção, eliminação, limitação, portabilidade e apresentar oposição ao tratamento quando aplicável. Para pedidos de privacidade ou dúvidas sobre dados, contactar **[A PREENCHER — email profissional]**. O responsável deve definir e cumprir os prazos de resposta e explicar como apresentar reclamação à autoridade de controlo competente.

## 8. Crianças e contas

**[A PREENCHER — indicar o público-alvo real, as restrições etárias e como são tratadas contas de menores, caso possam existir.]** O titular da conta de programador Google Play tem de cumprir os requisitos de idade e verificação da Google.

## 9. Alterações

Esta política será atualizada quando as funcionalidades, fornecedores, finalidades ou obrigações legais mudarem. Data efetiva: **[A PREENCHER após aprovação]**.

---

## Lista de bloqueios antes de publicar

- [ ] Identidade legal do responsável pela app confirmada.
- [ ] Email de privacidade/contacto profissional sob controlo do responsável.
- [ ] Fundamentos jurídicos, conservação e subprocessadores confirmados.
- [ ] Testes de eliminação de conta e de dados concluídos com contas descartáveis.
- [ ] Declarações da Play Console Data safety alinhadas com o código e com esta política.
- [ ] Revisão final pelo responsável e, quando necessário, por profissional qualificado.
