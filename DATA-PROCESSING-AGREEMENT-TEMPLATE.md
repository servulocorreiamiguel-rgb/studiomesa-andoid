# StudioMesa — modelo de acordo de tratamento de dados (DPA)

**RASCUNHO — não assinar nem utilizar sem revisão jurídica e preenchimento pelo responsável.**

Este documento é um modelo inicial para analisar se o fornecedor StudioMesa atua como subcontratante do restaurante em determinados tratamentos. Os papéis podem variar conforme a finalidade e a forma de operação; não assumir que o fornecedor é sempre apenas subcontratante.

## 1. Partes e papéis

**Responsável pelo tratamento:** [DENOMINAÇÃO, NIF, MORADA, CONTACTO].

**Subcontratante:** [DENOMINAÇÃO, NIF, MORADA, CONTACTO].

**Contrato principal:** [IDENTIFICAÇÃO, DATA E VERSÃO].

**Responsável de privacidade / DPO, se aplicável:** [CONTACTO].

## 2. Objeto, duração e finalidade

- Objeto do tratamento: alojamento e operação de dados necessários à utilização do StudioMesa pelo restaurante.
- Duração: [VIGÊNCIA DO CONTRATO E PERÍODO DE TRANSIÇÃO].
- Natureza e finalidade: autenticação, gestão de mesas/carta/pedidos/reservas/stock/fidelização, assistência local, sincronização quando ativada, suporte e segurança.
- Instruções documentadas do responsável: [ANEXAR / DESCREVER].

## 3. Categorias de titulares e dados

**Titulares:** proprietário e funcionários do restaurante; clientes que utilizam reservas ou fidelização; fornecedores representados em documentos de compra.

**Dados:** identificadores de conta e email; papéis e associações; nomes/contactos facultados para reservas; pedidos e preferências operacionais; pontos e histórico; dados de faturas de fornecedores (nome, NIF, número, datas, valores, documento); histórico/Memória do assistente quando a sincronização cloud estiver ativada; metadados técnicos e de segurança.

Dados especiais do artigo 9.º RGPD: [CONFIRMAR SE SÃO OU NÃO TRATADOS; não presumir].

## 4. Instruções e confidencialidade

- O subcontratante só trata dados segundo instruções documentadas do responsável, salvo obrigação legal.
- As pessoas autorizadas a tratar dados estão sujeitas a dever de confidencialidade adequado.
- O subcontratante não usa os dados para publicidade, venda de perfis, treino de modelos próprios ou finalidades incompatíveis.
- O responsável garante que tem fundamento jurídico e transparência adequados para os dados que introduz.

## 5. Segurança técnica e organizativa

Medidas a confirmar e anexar:

- autenticação e gestão de sessões;
- Row Level Security e políticas de autorização por restaurante;
- controlo de acesso administrativo e princípio do menor privilégio;
- cifragem em trânsito e cifragem local dos arquivos que a implementem;
- gestão de chaves e segredos fora da app/repositório;
- logging e auditoria de operações relevantes;
- gestão de vulnerabilidades e atualizações;
- backups e restauro testado;
- resposta a incidentes e continuidade;
- separação de ambientes e dados de teste;
- eliminação e exportação de dados no fim do serviço.

**Limite atual:** a sincronização cloud de histórico/Memória não é cifrada ponta a ponta. O backend alojado ainda não tem tabelas e bucket necessários para arquivo cloud de faturas, e o histórico de migrations está divergente. Não declarar estas capacidades como operacionais até serem reconciliadas e aprovadas.

## 6. Subcontratantes ulteriores

| Fornecedor | Serviço | Região/localização | Dados | DPA/termos |
|---|---|---|---|---|
| Supabase | Auth, PostgreSQL, Storage, Edge Functions | [CONFIRMAR REGIÃO E SUBPROCESSADORES ATUAIS] | Conta e dados operacionais; histórico/faturas apenas quando cloud sync ativa | [ANEXAR DPA] |
| Wikimedia | Pesquisa pública opcional | [CONFIRMAR] | Consulta pública genérica quando o utilizador aciona a pesquisa | [REVER TERMOS] |
| Hugging Face / litert-community | Download do modelo | [CONFIRMAR] | Pedido de download do ficheiro; não recebe prompts do modelo local | [REVER TERMOS/LICENÇA] |
| Google ML Kit / LiteRT-LM | OCR e inferência no dispositivo | [CONFIRMAR condições e telemetria] | OCR/inferência no dispositivo; confirmar práticas do runtime/SDK | [REVER TERMOS] |

Lista final de subprocessadores e países: [A PREENCHER COM BASE EM CONTRATOS, DPA E CONFIGURAÇÃO EFETIVA].

## 7. Transferências internacionais

Países terceiros, mecanismos de transferência, adequação, SCCs e avaliação de impacto: [A PREENCHER APÓS CONFIRMAÇÃO DOS SERVIÇOS E SUBPROCESSADORES].

## 8. Apoio a direitos dos titulares

O subcontratante presta assistência razoável ao responsável para responder a pedidos de acesso, retificação, apagamento, limitação, portabilidade e oposição, dentro dos limites técnicos e contratuais.

Canal e prazo de encaminhamento: [A PREENCHER].

## 9. Incidentes de dados pessoais

Contacto de incidente: [A PREENCHER].  
Prazo de notificação acordado: [A PREENCHER — considerar RGPD e contrato].  
Informação a fornecer: natureza, categorias, número aproximado de titulares/registos, consequências, medidas aplicadas e plano de mitigação.

## 10. Avaliações, auditorias e cooperação

Tipo de prova/auditoria, frequência, pré-aviso, custos e cooperação: [A PREENCHER].

## 11. Fim do contrato e eliminação

No final da prestação, o subcontratante deve, à escolha do responsável e conforme instruções, devolver ou eliminar os dados pessoais tratados por sua conta, salvo obrigação legal de conservação. Definir também tratamento de backups, logs, exportações e referências locais.

Prazo de exportação: [A PREENCHER].  
Prazo de eliminação: [A PREENCHER].  
Retenção legal/exceções: [A PREENCHER COM BASE JURÍDICA].

## 12. Responsabilidade, lei e assinaturas

Cláusulas de responsabilidade, indemnização, lei aplicável, foro e resolução de litígios: [REVISÃO JURÍDICA OBRIGATÓRIA].

Responsável: ___________________________  Data: __________

Subcontratante: _________________________  Data: __________

---

## Verificação antes de assinar

- [ ] Confirmar os papéis reais de responsável/subcontratante por finalidade.
- [ ] Confirmar o mapa de dados e a configuração ativa da release.
- [ ] Obter DPA/termos atuais de todos os fornecedores e subprocessadores.
- [ ] Confirmar regiões, transferências internacionais e SCCs, se aplicável.
- [ ] Confirmar segurança, backup/restauro, retenção, exportação e eliminação.
- [ ] Definir canal/prazos de incidentes e assistência a direitos.
- [ ] Rever com profissional jurídico especializado em proteção de dados.