# Integração do agente financeiro com faturas — Portugal

## Estado atual

O StudioMesa ainda não armazena faturas contabilísticas de fornecedores nem está ligado a um programa de faturação ou contabilidade. O QR usado no percurso de fidelização não é acesso ao documento fiscal nem um arquivo de despesas. Não se deve mostrar ao utilizador que o agente já consulta documentos que não foram importados.

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

## Bloqueios antes de implementar

- Escolher o programa de faturação e o programa de contabilidade/gestão de fornecedores do primeiro restaurante.
- Confirmar formatos e API oficialmente documentados por esses fornecedores.
- Reconciliar as migrações do Supabase, conforme supabase/MIGRATION-RECONCILIATION-2026-11-02.md, antes de criar tabelas ou buckets no projeto alojado.
- Definir armazenamento cifrado, retenção, backups, RLS por restaurante e acesso exclusivo do proprietário.
- Criar amostras anonimizadas para testes de extração, duplicados, notas de crédito, IVA, documentos ilegíveis e datas/valores inconsistentes.

## Regra do produto

O agente financeiro deve distinguir sempre entre dados de vendas, movimentos contabilísticos, faturas de fornecedores importadas, estimativas e informação em falta. Nunca deve inventar documentos nem apresentar um custo calculado a partir de stock como se fosse o total oficial de compras.
