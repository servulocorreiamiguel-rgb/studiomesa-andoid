# StudioMesa — lista de entrega e entrada em funcionamento

**Utilizar apenas depois de o gate de lançamento permitir um piloto com dados reais.**

## Antes de aceitar o cliente

- [ ] Confirmar entidade legal do fornecedor, cliente, contrato e NIF.
- [ ] Assinar proposta, licença/termos B2B e acordo de tratamento de dados, quando aplicável.
- [ ] Validar versão, plano, package IDs e dispositivos suportados.
- [ ] Confirmar que a versão comercial está assinada e os checksums foram arquivados.
- [ ] Confirmar que o backend e as migrations da release estão reconciliados e implantados.
- [ ] Confirmar email/SMTP, autenticação, URLs de retorno, política de palavras-passe e proteção Auth.
- [ ] Confirmar backups, retenção, exportação e restauro ensaiado.
- [ ] Confirmar política de privacidade pública, Data Safety e página externa de eliminação.
- [ ] Definir quem é o proprietário de conta Supabase/Play e quem administra acessos.

## Configuração do restaurante

- [ ] Criar a conta proprietária e verificar o email.
- [ ] Criar ou associar o restaurante certo.
- [ ] Confirmar nome, fuso horário, moeda, horários, zonas, capacidade e duração padrão de reservas.
- [ ] Introduzir mesas, carta, preços e alergénios fornecidos pelo cliente.
- [ ] Separar os dados internos de custos/margens dos dados visíveis na aplicação Cliente.
- [ ] Criar contas individuais para funcionários; nunca partilhar uma password entre pessoas.
- [ ] Definir permissões por papel apenas quando a matriz granular estiver ativa e testada.
- [ ] Definir regras da fidelização, pontos, recompensas, QR, resgates e limites antiabuso.
- [ ] Configurar inventário, unidades, quantidades, custos e limites mínimos, se incluído.
- [ ] Confirmar a aprovação do proprietário antes de ativar qualquer consumo automático de receita quando esse módulo estiver disponível.
- [ ] Rever as opções da IA e explicar que o modelo principal Gemma 4 E2B ocupa cerca de 2,59 GB e é descarregado a pedido; o LFM2.5-1.2B INT4 (cerca de 736 MB) só é usado como fallback quando o dispositivo não suporta o Gemma ou este não pode ser validado/inicializado, e apenas se o ficheiro alternativo passar a validação.

## Testes de aceitação com o cliente

- [ ] Login, recuperação, logout e reentrada com contas separadas.
- [ ] Um funcionário não acede às opções exclusivas do proprietário.
- [ ] Um restaurante não consegue consultar dados de outro.
- [ ] Criar, atualizar e concluir um pedido em dois dispositivos.
- [ ] Confirmar sincronização e recuperação após falha de rede.
- [ ] Fazer uma reserva, confirmar/cancelar e testar capacidade/conflitos.
- [ ] Confirmar que a app Cliente não recebe custos internos, dados da equipa ou dados de outros clientes.
- [ ] Testar exportação CSV e explicar que não é documento fiscal.
- [ ] Testar fatura/OCR apenas com documento fictício ou anonimizado; o proprietário revê todos os campos.
- [ ] Testar eliminação da conta com conta descartável, nunca com conta real durante a instalação.
- [ ] Registar os resultados e a aprovação escrita do cliente.

## Entrega e suporte

- [ ] Entregar as aplicações corretas, instruções e contactos de suporte.
- [ ] Explicar limitações, manutenção, atualizações e custos de serviços externos.
- [ ] Entregar o procedimento de exportação e o contacto de privacidade.
- [ ] Registar a versão/build, checksums, data e responsável pela instalação.
- [ ] Registar incidentes e alterações solicitadas; alterações fora do âmbito exigem orçamento separado.
- [ ] Agendar revisão inicial após utilização controlada, com autorização do cliente.

## Não ativar antes de existir prova

- [ ] Sincronização cloud da IA e arquivo cloud de faturas.
- [ ] Fichas técnicas/consumo automático de stock.
- [ ] Permissões granulares de funcionários.
- [ ] Integrações POS, ERP, faturação, contabilidade, pagamentos ou impressoras.
- [ ] Backups/recuperação anunciados como garantidos.

**A instalação só passa a produção após todos os pontos aplicáveis estarem comprovados e assinados pelo responsável.**