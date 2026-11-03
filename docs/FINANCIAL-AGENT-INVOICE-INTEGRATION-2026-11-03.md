# Integração do agente financeiro com faturas — Portugal

## Estado atual na branch de desenvolvimento

O StudioMesa já tem um arquivo local cifrado de faturas e notas de crédito. O proprietário regista e confirma fornecedor, número, data, base tributável, IVA, total e notas/linhas. Pode pesquisar o histórico, abrir cada documento, anexar o original e exportar os registos em CSV.

O assistente consegue consultar os valores introduzidos e calcular totais de faturas, notas de crédito, compras líquidas registadas, IVA líquido e valores por fornecedor. Os dados são transcritos pelo proprietário; o assistente não está a ler automaticamente o PDF ou a imagem.

O arquivo e os anexos ficam cifrados na instalação Android e separados por conta/restaurante. Ainda não há sincronização entre telemóveis nem arquivo remoto no Supabase. O QR usado na fidelização não é acesso ao documento fiscal nem um arquivo de despesas.

## SAF-T (PT) e faturas de fornecedores

A Autoridade Tributária e Aduaneira descreve o SAF-T (PT) como um ficheiro XML normalizado para exportar registos contabilísticos e de faturação. A exportação é produzida pelo próprio programa de contabilidade/faturação e depende do período. Fonte oficial: Portal das Finanças, FAQ técnica 2733 e 2738: https://info.portaldasfinancas.gov.pt/pt/apoio_contribuinte/questoes_frequentes/pages/faqs-00276.aspx.

**Limitação decisiva para compras:** a FAQ técnica 2743 da AT diz que o SAF-T (PT) do adquirente não contempla documentos comerciais emitidos pelos fornecedores. Por isso, um SAF-T de faturação do restaurante não é, por si só, um arquivo de faturas de compras. É necessário obter os documentos através do programa de contabilidade/gestão de compras, uma API do fornecedor ou importação suportada. Fonte oficial: Portal das Finanças, FAQ 2743: https://info.portaldasfinancas.gov.pt/pt/apoio_contribuinte/questoes_frequentes/pages/faqs-00276.aspx#2743.

## Funcionalidades implementadas localmente

- Registo manual validado de faturas e notas de crédito.
- Deteção de duplicados por fornecedor, número e data.
- Arquivo pesquisável e acessível em «Ferramentas financeiras».
- Anexo original cifrado com AES-GCM e chave do Android Keystore.
- Formatos de anexo: PDF, XML, TXT, JPEG, PNG e WebP, até 12 MB.
- Exportação e eliminação do anexo.
- Resumos no chat com totais de faturas, notas de crédito, compras líquidas registadas, IVA líquido e totais por fornecedor.
- Exportação do histórico financeiro em CSV, com escolha do destino e proteção contra fórmulas em campos de texto.
- Registo e análise reservados ao proprietário quando existe um restaurante ligado.

## Próximas etapas para automatizar a leitura

1. Escolher o programa de faturação/POS e o programa de contabilidade ou gestão de compras do primeiro restaurante. Podem ser produtos diferentes.
2. Confirmar as APIs e os formatos de exportação oficiais desses programas.
3. Implementar importação estruturada de XML quando o fornecedor disponibilizar um formato documentado.
4. Avaliar extração local de texto de PDF e OCR no dispositivo, com confirmação humana dos campos antes de os guardar.
5. Distinguir faturas, notas de crédito e documentos anulados; detetar duplicados por fornecedor, número, data, total e, quando possível, hash.
6. Cruzar linhas de compra com artigos, receitas, inventário e vendas, assinalando correspondências incertas.
7. Definir sincronização cifrada, retenção, backups, políticas RLS por restaurante e auditoria antes de guardar documentos na cloud.

## Bloqueios antes da integração cloud ou automática

- Escolher e validar o software utilizado pelo restaurante.
- Reconciliar o histórico de migrações do Supabase, conforme `supabase/MIGRATION-RECONCILIATION-2026-11-02.md`, antes de criar tabelas ou buckets no projeto alojado.
- Definir permissões exclusivas do proprietário ou uma permissão financeira explícita.
- Preparar amostras anonimizadas para testar extração, duplicados, notas de crédito, IVA, documentos ilegíveis e datas/valores inconsistentes.
- Não enviar faturas ou dados financeiros a um fornecedor externo de IA sem consentimento, minimização de dados e condições adequadas de tratamento.

## Limites da versão atual

O arquivo, a memória e os dados financeiros são locais à instalação; não sincronizam entre telemóveis e não têm backup cloud. Os campos numéricos são transcritos e conferidos manualmente. O PDF/XML não é ainda interpretado automaticamente. A base Supabase de produção não foi alterada nesta fase, porque a reconciliação documentada do histórico de migrações continua a bloquear novas migrações e buckets.

O assistente atual usa regras e cálculos locais. Não existe ainda uma ligação a um modelo generativo ChatGPT, Gemini ou Grok.

## Regra do produto

O agente financeiro deve distinguir dados de vendas, movimentos contabilísticos, documentos importados, estimativas e informação em falta. Nunca deve inventar documentos nem apresentar o custo de stock como se fosse o total oficial de compras. A análise não substitui contabilidade certificada, validação fiscal ou o contabilista.
