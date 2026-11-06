# StudioMesa — modelo de política de privacidade

<strong>ESTADO: RASCUNHO — NÃO PUBLICAR.</strong> Este documento é um modelo técnico, não um parecer jurídico. O responsável legal pela aplicação tem de preencher os campos em falta, confirmar os fornecedores e os períodos de conservação, validar o comportamento da versão que será distribuída e obter revisão jurídica adequada.

**Versão do modelo:** 6 de novembro de 2026  
**Aplicação:** StudioMesa — Gestão e Cliente

## 1. Responsável pelo tratamento

Responsável legal / entidade: **[A PREENCHER — nome jurídico exato]**.  
Morada: **[A PREENCHER]**.  
Contacto para privacidade e exercício de direitos: **[A PREENCHER — email profissional controlado pelo responsável]**.  
Representante ou contacto na UE, se aplicável: **[A PREENCHER]**.

Não publicar enquanto estes campos não estiverem preenchidos e confirmados.

## 2. Âmbito

A StudioMesa é uma aplicação Android de gestão de restaurante e uma aplicação associada para clientes. As funcionalidades e os dados disponíveis dependem do plano contratado, da variante instalada e da configuração do backend. Esta política deve ser revista sempre que o código, os fornecedores, os endpoints ou a configuração de produção mudarem.

## 3. Categorias de dados que podem ser tratados

Consoante a funcionalidade e o papel da pessoa, podem ser tratados:

- **Conta e autenticação:** email, identificador de utilizador, tokens/sessão técnica e dados necessários ao registo, login, confirmação de email, recuperação de palavra-passe, OAuth (se ativado) e eliminação da conta.
- **Restaurante e equipa:** nome do restaurante, identificadores de restaurante, associação e papel de proprietário/funcionário, códigos de adesão, permissões, dados de configuração e histórico de ações suportado pela versão.
- **Operação do restaurante:** mesas, zonas e capacidade; artigos e categorias da carta; preços e custos internos; pedidos, quantidades, notas, estados e tempos; reservas, nome, contacto opcional, data/hora, número de pessoas, duração, mesa atribuída e notas; artigos de stock, quantidades, unidades, custos unitários e movimentos.
- **Clientes e fidelização:** nome de apresentação, identificadores/códigos de cartão, associação ao restaurante, saldo e histórico de pontos, QR temporários e histórico de recompensas quando essas funcionalidades estiverem ativadas.
- **Conteúdo do assistente:** perguntas e respostas, títulos de conversas e notas da Memória. O histórico e a Memória são cifrados localmente com uma chave do Android Keystore. O código inclui uma sincronização cloud opcional para a conta proprietária; quando essa migração está implantada, o conteúdo é guardado no Supabase e protegido por autenticação/RLS. Essa cópia cloud **não é cifrada ponta a ponta**.
- **Documentos e faturas de fornecedores:** PDFs/imagens escolhidos pelo utilizador; texto extraído por OCR local; nome e NIF/VAT do fornecedor, número e datas da fatura, base, IVA, total, avisos, estado de revisão, tamanho, tipo MIME e hash do documento. O texto bruto de OCR não é guardado pelo arquivo. A cópia local dos metadados é cifrada; a cópia cloud do documento/metadados só funciona depois de aplicar e validar a migração e o bucket privado.
- **Modelo local de IA:** o modelo LFM2.5-1.2B-Instruct INT4 é descarregado a pedido de um repositório público para armazenamento privado da aplicação, com verificação do tamanho e SHA-256. A inferência ocorre no dispositivo; esta função não envia o prompt, histórico ou dados do restaurante ao fornecedor do modelo.
- **Pesquisa pública opcional:** quando o utilizador aciona a ferramenta de pesquisa da Wikipédia, é enviada uma consulta pública curta à Wikimedia. A aplicação não deve incluir nessa consulta nomes de clientes/funcionários, contactos, faturas, valores privados ou dados operacionais.
- **Ficheiros e permissões:** uma referência Android Storage Access Framework ao documento escolhido e a autorização concedida pelo utilizador para voltar a aceder ao ficheiro. A disponibilidade de uma referência local pode depender da persistência da permissão concedida pelo fornecedor de ficheiros.
- **Dados técnicos e segurança:** informação necessária para manter sessões, prevenir duplicações, registar operações, proteger o serviço, diagnosticar falhas e processar pedidos de eliminação, conforme a configuração real dos fornecedores.

Não são recolhidos dados de cartão bancário nem processados pagamentos pela implementação atualmente verificada. Não existe integração direta verificada com POS, faturação, contabilidade, impressoras ou gateways de pagamento.

## 4. Onde os dados são tratados

**No dispositivo Android:** o modelo generativo, OCR, leitura de PDFs/imagens, algumas análises e os arquivos locais são processados no dispositivo. As sessões, o histórico do assistente e os metadados locais de faturas usam armazenamento privado/cifrado nos componentes correspondentes. Os dados de demonstração locais não devem ser confundidos com registos de um restaurante real.

**No backend:** a aplicação utiliza Supabase Auth e PostgreSQL na região `eu-west-1` do projeto atualmente ligado. As operações de restaurante configuradas sincronizam dados para o backend e usam RLS/RPCs para limitar o acesso por conta/restaurante. A localização e os subprocessadores têm de ser confirmados na configuração e nos contratos vigentes no momento da publicação.

**Estado técnico que importa antes de publicar:** em 6 de novembro de 2026, o projeto Supabase alojado ainda não contém as tabelas de sincronização de histórico/Memória, a tabela de faturas nem o bucket privado `studiomesa-invoices`. Também lhe faltam a matriz granular de permissões e as tabelas de receitas. Assim, não anunciar estas funcionalidades cloud como ativas até reconciliar o histórico de migrations e fazer um deployment aprovado. A política final tem de descrever exatamente a configuração que estiver ativa na release, não apenas o código da branch.

## 5. Finalidades e fundamentos

Os dados podem ser usados para:

- criar e gerir contas e sessões;
- disponibilizar as funcionalidades de restaurante e de cliente contratadas;
- partilhar dados operacionais com os membros autorizados do restaurante;
- gerir reservas, pedidos, mesas, carta, stock e fidelização;
- executar cálculos e sugestões locais, sem alterar operações automaticamente;
- importar, rever, arquivar e consultar documentos de fornecedores;
- sincronizar histórico/Memória quando a funcionalidade cloud estiver ativa e autorizada;
- prevenir duplicações, abuso, acessos indevidos e recuperar falhas;
- responder a pedidos de acesso, retificação, exportação ou eliminação.

**Fundamento jurídico por finalidade:** [A PREENCHER pelo responsável pelo tratamento, após análise jurídica, por exemplo execução contratual, obrigação legal, interesse legítimo ou consentimento quando aplicável].

## 6. Partilha e destinatários

Os dados podem ser disponibilizados:

- ao restaurante a que o utilizador está associado, de acordo com o papel e as permissões aplicáveis;
- ao fornecedor de alojamento/autenticação Supabase e respetivos subcontratantes, conforme os termos e o DPA contratados;
- à Wikimedia apenas quando é acionada a ferramenta pública de pesquisa;
- ao alojamento público do modelo apenas para descarregar o ficheiro do modelo, não para receber prompts;
- a outros fornecedores apenas se vierem a ser integrados e devidamente declarados.

**Subcontratantes, países, transferências internacionais e contratos:** [A PREENCHER após confirmar todos os fornecedores, regiões, DPA e mecanismos de transferência].

## 7. Segurança

A aplicação usa autenticação, armazenamento privado, cifragem local em componentes relevantes, Android Keystore, RLS e verificações do lado do servidor. A sincronização cloud do assistente, quando ativada, é protegida por RLS, mas não é cifrada ponta a ponta. O modelo local verifica o hash do ficheiro descarregado. Nenhuma destas medidas constitui garantia absoluta de segurança ou substitui auditoria independente, testes e configuração segura do serviço.

Antes de recolher dados reais, devem ser validados os privilégios das RPCs, isolamento entre restaurantes, sessões, permissões, backups/restauro, eliminação e configuração de email/Auth.

## 8. Conservação

Períodos de conservação por categoria:

- Conta e dados de autenticação: **[A PREENCHER]**.
- Dados operacionais do restaurante, pedidos, reservas, carta e stock: **[A PREENCHER; considerar contrato e obrigações legais aplicáveis]**.
- Fidelização e auditoria: **[A PREENCHER]**.
- Histórico e Memória do assistente: **[A PREENCHER]**.
- Documentos e metadados de faturas: **[A PREENCHER; confirmar obrigações legais e necessidade do restaurante]**.
- Logs, backups e cópias dos fornecedores: **[A PREENCHER após confirmar plano, configuração e DPA]**.

Não publicar períodos de conservação presumidos.

## 9. Direitos e eliminação de conta

Os titulares podem exercer os direitos que lhes assistam ao abrigo da legislação aplicável, incluindo acesso, retificação, apagamento, limitação, portabilidade e oposição quando aplicável, através de **[EMAIL DE PRIVACIDADE A PREENCHER]**.

A aplicação inclui um fluxo de eliminação da conta e existe uma página web pública em:

https://svwvkfsyevuyabelrtjk.supabase.co/functions/v1/account-deletion-web

A página solicita uma ligação de recuperação por email e usa PKCE no navegador. A eliminação efetiva é feita por um endpoint autenticado que valida a sessão e as permissões. A eliminação dos dados partilhados de um restaurante pode ter consequências irreversíveis e depende do papel do utilizador e da existência de outros membros.

**O percurso completo de email, confirmação e eliminação ainda não foi validado de ponta a ponta com contas descartáveis.** A URL tem de estar autorizada em Supabase Auth e o serviço de email tem de estar configurado antes da publicação. A política e a declaração Data Safety devem ser revistas após esses testes.

## 10. Crianças e público-alvo

Público-alvo pretendido: **[A PREENCHER — confirmar que o produto é dirigido a profissionais/empresas e aos seus clientes, e definir tratamento de contas de menores quando aplicável]**.

## 11. Contactos e reclamações

Contacto de privacidade: **[A PREENCHER]**.  
Autoridade de controlo competente e instruções para reclamação: **[A PREENCHER após validação jurídica no país de estabelecimento do responsável]**.

## 12. Alterações

Data efetiva: **[A PREENCHER após aprovação]**.  
Versão: **[A PREENCHER]**.

---

## Lista de validação — não publicar enquanto houver itens em falta

- [ ] Identidade legal e contacto do responsável confirmados.
- [ ] Fundamentos jurídicos, finalidades e papéis de responsável/subcontratante definidos.
- [ ] Lista de fornecedores, subprocessadores, regiões e DPA confirmados.
- [ ] Períodos de conservação definidos por categoria e refletidos nos sistemas.
- [ ] Configuração cloud efetivamente implantada comparada com esta política.
- [ ] Testes de acesso, exportação e eliminação concluídos com contas descartáveis.
- [ ] Política publicada numa URL pública estável e ligada na aplicação e Play Console.
- [ ] Data Safety respondida de acordo com a versão binária e SDKs efetivamente publicados.
- [ ] Revisão jurídica concluída.

**Este ficheiro é um modelo interno. Não é a política de privacidade final da StudioMesa.**