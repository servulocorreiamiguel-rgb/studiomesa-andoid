# Integração do agente financeiro com faturas — Portugal

## Estado atual

O StudioMesa tem agora um arquivo local cifrado de faturas e notas de crédito. O proprietário regista e confirma fornecedor, número, data, base tributável, IVA, total e linhas/notas; também pode anexar um documento original e pesquisar o histórico. O agente consulta os valores registados e apresenta totais, notas de crédito, IVA e valores por fornecedor. O QR usado na fidelização continua a não ser acesso ao documento fiscal nem um arquivo de despesas. A aplicação ainda não está ligada a um programa de faturação ou contabilidade e não extrai automaticamente campos de PDFs.

## O que os formatos portugueses permitem

A Autoridade Tributária e Aduaneira descreve o SAF-T (PT) como um ficheiro XML normalizado para exportar registos contabilísticos e de faturação. A exportação é produzida pelo próprio programa de contabilidade/faturação; o ficheiro é estruturado e dependente do período. Fonte oficial: Portal das Finanças, FAQ técnica 2733 e 2738: https://info.portaldasfinancas.gov.pt/pt/apoio_contribuinte/questoes_frequentes/pages/faqs-00276.aspx.

**Limitação decisiva para compras:** a FAQ técnica 2743 da AT diz expressamente que o SAF-T (PT) do adquirente não contempla documentos comerciais a fornecedores. Portanto, um SAF-T de faturação do restaurante não é, por si só, um arquivo de faturas de compras. É necessário obter as faturas dos fornecedores através do programa de contabilidade/gestão de compras, de uma integração específica ou de uma importação de documentos suportados. Fonte: Portal das Finanças, FAQ 2743: https://info.portaldasfinancas.gov.pt/pt/apoio_contribuinte/questoes_frequentes/pages/faqs-00276.aspx#2743.

## Caminho técnico recomendado

1. Identificar o programa de faturação/POS e o programa de contabilidade ou gestão de compras usados pelo restaurante. Podem ser produtos diferentes.
2. Para vendas, avaliar a exportação SAF-T (PT) produzida pelo programa do restaurante ou uma API oficial do fornecedor.
3. Para compras, preferir API/exportação do programa de contabilidade/gestão de fornecedores; em alternativa, importar PDF/XML de cada fatura através de um fluxo autenticado e controlado pelo proprietário.
4. Extrair fornecedor, data, número do documento, linhas/artigos, quantidades, valores, IVA, totais e vencimento; conservar referência ao ficheiro original e os dados de proveniência.
5. Detetar duplicados por fornecedor + número + data + total e, quando possível, hash do documento. Não somar novamente notas de crédito nem documentos anulados.
6. Cruzar custos de compra com artigos/receitas, inventário e vendas do StudioMesa. Assinalar correspondências incertas e pedir confirmação humana.
7. Limitar visualização e importação ao proprietário ou a uma permissão financeira explícita. Registar auditoria de quem importou, consultou, exportou ou apagou documentos; não transmitir documentos para um fornecedor de IA sem consentimento e minimização de dados.
8. Não tratar a análise do agente como contabilidade certificada, validação fiscal ou substituto do contabilista.

## O que já está implementado localmente\n\n- Registo manual validado de faturas e notas de crédito, com controlo de duplicados por fornecedor, número e data.\n- Arquivo e pesquisa acessíveis na secção «Ferramentas financeiras».\n- Anexo original cifrado com AES-GCM e chave do Android Keystore; PDF, XML, TXT, JPEG, PNG e WebP até 12 MB; exportação e eliminação do anexo.\n- Resumo no chat de faturas, notas de crédito, compras líquidas registadas, IVA líquido e totais por fornecedor.\n- Exportação do histórico financeiro em CSV com escolha do destino pelo utilizador e proteção contra fórmulas em texto introduzido.\n- Valores e notas são introduzidos/conferidos pelo proprietário. A aplicação não afirma ter lido automaticamente o conteúdo do documento.\n\n## Bloqueios antes de uma integração automática

- Escolher o programa de faturação e o programa de contabilidade/gestão de fornecedores do primeiro restaurante.
- Confirmar formatos e API oficialmente documentados por esses fornecedores.
- Reconciliar as migrações do Supabase, conforme supabase/MIGRATION-RECONCILIATION-2026-11-02.md, antes de criar tabelas ou buckets no projeto alojado.
- Definir armazenamento cifrado, retenção, backups, RLS por restaurante e acesso exclusivo do proprietário.
- Criar amostras anonimizadas para futuros testes de extração/OCR, duplicados, notas de crédito, IVA, documentos ilegíveis e datas/valores inconsistentes.

## Limites da versão atual\n\nO arquivo de faturas e a memória financeira são locais à instalação e ficam separados por utilizador/restaurante; não há sincronização entre telemóveis nem backup cloud. O anexo original é cifrado, mas os campos numéricos são transcritos e validados manualmente. O ficheiro PDF/XML não é ainda interpretado automaticamente. A base Supabase de produção não foi alterada, porque a reconciliação documentada do histórico de migrações ainda bloqueia novas migrações e buckets.\n\n## Regra do produto

O agente financeiro deve distinguir sempre entre dados de vendas, movimentos contabilísticos, faturas de fornecedores importadas, estimativas e informação em falta. Nunca deve inventar documentos nem apresentar um custo calculado a partir de stock como se fosse o total oficial de compras.
