# StudioMesa — modelo de política de privacidade

<strong>ESTADO: RASCUNHO — NÃO PUBLICAR.</strong> Este documento é um modelo técnico, não um parecer jurídico. O responsável legal pela aplicação tem de preencher os campos em falta, confirmar os fornecedores e os períodos de conservação, validar o comportamento da versão que será distribuída e obter revisão jurídica adequada.

**Versão do modelo:** 9 de novembro de 2026  
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
- **Conteúdo do assistente:** perguntas e respostas, títulos de conversas e notas da Memória. O histórico e a Memória são cifrados localmente com uma chave do Android Keystore. A sincronização cloud fica **desativada por defeito** e só é ativada após confirmação explícita do proprietário na aplicação. Quando ativada, o histórico e a Memória são enviados para o Supabase, protegidos por autenticação e RLS; a cópia cloud **não é cifrada ponta a ponta**. Desativar a sincronização impede novos envios, mas não elimina automaticamente cópias já enviadas.
- **Documentos e faturas de fornecedores:** PDFs/imagens escolhidos pelo utilizador; texto extraído por OCR local; nome e NIF/VAT do fornecedor, número e datas da fatura, base, IVA, total, avisos, estado de revisão, tamanho, tipo MIME e hash do documento. O texto bruto de OCR não é guardado pelo arquivo. A cópia local dos metadados é cifrada. Quando o proprietário escolhe arquivar uma fatura no servidor, o documento original e os metadados são enviados para a tabela `restaurant_supplier_invoices` e para o bucket privado `studiomesa-invoices` do Supabase; as políticas atuais limitam o acesso ao proprietário do restaurante.
- **Modelo local de IA:** o modelo principal é o Gemma 4 E2B em formato LiteRT-LM (cerca de 2,59 GB), descarregado a pedido de uma origem pública com revisão fixa; a aplicação valida o tamanho exato e o SHA-256 antes de o utilizar. O LFM2.5-1.2B-Instruct INT4 (cerca de 736 MB) é apenas um modelo de recurso, utilizado quando o dispositivo não cumpre os requisitos do Gemma ou quando este não pode ser validado/inicializado, e apenas se o próprio ficheiro alternativo passar a validação de integridade. A inferência decorre no dispositivo: os prompts, o histórico e os dados do restaurante não são enviados ao alojamento dos ficheiros do modelo. O pedido de download pode, contudo, revelar ao alojamento os metadados normais de uma ligação (por exemplo, endereço IP e hora).
- **Pesquisa pública opcional:** quando o utilizador aciona a pesquisa pública na Web, a consulta curta é enviada ao endpoint SearXNG configurado no backend. A função Web rejeita consultas que pareçam conter email, telefone, NIF ou IBAN. Este filtro não deteta toda a informação confidencial; não devem ser enviados nomes de clientes/funcionários, faturas, valores privados ou dados operacionais. Não declarar fornecedores de pesquisa adicionais sem verificar a implementação e a configuração da versão publicada.
- **Ficheiros e permissões:** uma referência Android Storage Access Framework ao documento escolhido e a autorização concedida pelo utilizador para voltar a aceder ao ficheiro. A disponibilidade de uma referência local pode depender da persistência da permissão concedida pelo fornecedor de ficheiros.
- **Dados técnicos e segurança:** informação necessária para manter sessões, prevenir duplicações, registar operações, proteger o serviço, diagnosticar falhas e processar pedidos de eliminação, conforme a configuração real dos fornecedores.

Não são recolhidos dados de cartão bancário nem processados pagamentos pela implementação atualmente verificada. Não existe integração direta verificada com POS, faturação, contabilidade, impressoras ou gateways de pagamento.

## 4. Onde os dados são tratados

**No dispositivo Android:** o modelo generativo, OCR, leitura de PDFs/imagens, algumas análises e os arquivos locais são processados no dispositivo. As sessões, o histórico do assistente e os metadados locais de faturas usam armazenamento privado/cifrado nos componentes correspondentes. Os dados de demonstração locais não devem ser confundidos com registos de um restaurante real.

**No backend:** a aplicação utiliza Supabase Auth e PostgreSQL na região `eu-west-1` do projeto atualmente ligado. As operações de restaurante configuradas sincronizam dados para o backend e usam RLS/RPCs para limitar o acesso por conta/restaurante. A localização e os subprocessadores têm de ser confirmados na configuração e nos contratos vigentes no momento da publicação.

**Estado técnico verificado em 9 de novembro de 2026:** o projeto Supabase atualmente ligado contém as tabelas de sincronização do histórico/Memória, a tabela `restaurant_supplier_invoices` e o bucket `studiomesa-invoices`, que está privado. As políticas consultadas limitam o histórico/Memória e o arquivo de faturas ao proprietário do restaurante. A sincronização do assistente passa a exigir ativação explícita na aplicação. A política final tem de ser novamente comparada com a versão binária e a configuração efetivamente ativa no momento da publicação; a existência de tabelas não substitui testes de autorização com contas separadas.

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
- ao endpoint SearXNG configurado, apenas quando o utilizador aciona a pesquisa pública, recebendo a consulta de pesquisa e não o histórico completo;
- ao alojamento público do modelo apenas para descarregar o ficheiro do modelo, não para receber prompts;
- a outros fornecedores apenas se vierem a ser integrados e devidamente declarados.

**Subcontratantes, países, transferências internacionais e contratos:** [A PREENCHER após confirmar todos os fornecedores, regiões, DPA e mecanismos de transferência].

## 7. Segurança

A aplicação usa autenticação, armazenamento privado, cifragem local em componentes relevantes, Android Keystore, RLS e verificações do lado do servidor. A sincronização cloud do assistente é desativada por defeito e, quando ativada pelo proprietário, é protegida por RLS, mas não é cifrada ponta a ponta. Os documentos de faturas ficam num bucket privado com políticas de acesso de proprietário. O modelo local verifica o hash do ficheiro descarregado. Nenhuma destas medidas constitui garantia absoluta de segurança ou substitui auditoria independente, testes e configuração segura do serviço.

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