# Integração do agente financeiro com faturas — Portugal

## Estado atual na branch de desenvolvimento

O StudioMesa tem um arquivo local cifrado de faturas e notas de crédito. O proprietário regista fornecedor, número, data, base tributável, IVA, total e notas/linhas; pode pesquisar o histórico, abrir detalhes, anexar o documento original e exportar CSV.

Ao importar PDF ou imagem, a app tenta reconhecer texto **localmente** no dispositivo. Usa ML Kit para JPEG/PNG/WebP e para páginas de PDF convertidas em imagens com o Android PdfRenderer. Processa até 10 páginas e limita o texto reconhecido a 20 000 caracteres. TXT e XML são lidos localmente. O texto reconhecido é guardado de forma cifrada junto da fatura.

O chat pode apresentar o texto extraído quando lhe perguntam pelos artigos/linhas da fatura e calcula totais com os valores confirmados e introduzidos pelo proprietário. O OCR não valida números, datas, IVA ou totais, nem preenche automaticamente os campos fiscais. A pessoa responsável tem de comparar o texto com o documento original antes de guardar ou usar os valores.

O QR usado na fidelização continua a não dar acesso ao documento fiscal nem a ser um arquivo de despesas.

## SAF-T (PT) e faturas de fornecedores

A Autoridade Tributária e Aduaneira descreve o SAF-T (PT) como um ficheiro XML normalizado para exportar registos contabilísticos e de faturação. A exportação é produzida pelo próprio programa de contabilidade/faturação e depende do período. Fonte oficial: Portal das Finanças, FAQ técnica 2733 e 2738: https://info.portaldasfinancas.gov.pt/pt/apoio_contribuinte/questoes_frequentes/pages/faqs-00276.aspx.

**Limitação decisiva para compras:** a FAQ técnica 2743 da AT diz que o SAF-T (PT) do adquirente não contempla documentos comerciais emitidos pelos fornecedores. Um SAF-T de faturação do restaurante não é, por si só, um arquivo de faturas de compras. É necessário obter os documentos através do programa de contabilidade/gestão de compras, uma API do fornecedor ou importação suportada. Fonte oficial: Portal das Finanças, FAQ 2743: https://info.portaldasfinancas.gov.pt/pt/apoio_contribuinte/questoes_frequentes/pages/faqs-00276.aspx#2743.

## Funcionalidades implementadas localmente

- Registo manual validado de faturas e notas de crédito.
- Deteção de duplicados por fornecedor, número e data.
- Histórico pesquisável acessível ao proprietário.
- Anexos PDF/XML/TXT/JPEG/PNG/WebP até 12 MB, cifrados com AES-GCM e chave Android Keystore.
- OCR local de imagens e das primeiras 10 páginas de PDF; texto limitado a 20 000 caracteres e guardado cifrado.
- Leitura local de texto simples e XML.
- Consulta no chat do texto OCR/transcrito, sempre identificado como não validado.
- Resumos com totais de faturas, notas de crédito, compras líquidas registadas, IVA líquido e totais por fornecedor.
- Exportação do original e do histórico financeiro em CSV, com proteção contra fórmulas em campos de texto.
- Registo e análise reservados ao proprietário quando existe um restaurante ligado.

## Limites e riscos do OCR

- A qualidade depende da resolução, iluminação, rotação, contraste, idioma e estrutura do documento.
- Tabelas, manuscrito, linhas partidas, vírgulas decimais, percentagens de IVA, números de documento e datas podem ser reconhecidos incorretamente.
- O OCR serve para consulta e pré-leitura, não para determinar automaticamente valores contabilísticos nem validar uma fatura fiscal.
- As notas de crédito têm de ser classificadas como tal pelo proprietário; o sistema não assume que um documento é uma fatura só por encontrar números.
- Documentos com mais de 10 páginas só têm as primeiras 10 páginas analisadas nesta versão.

## Próximas etapas para integração contabilística

1. Escolher o programa de faturação/POS e o programa de contabilidade ou gestão de compras do primeiro restaurante. Podem ser produtos diferentes.
2. Confirmar APIs e formatos oficiais desses programas.
3. Avaliar importação estruturada de XML quando o fornecedor disponibilizar formato documentado.
4. Reconciliar linhas da fatura com artigos, receitas, stock e vendas, sempre com confirmação humana para correspondências incertas.
5. Definir sincronização cifrada, retenção, backups, políticas RLS por restaurante e auditoria antes de guardar documentos na cloud.
6. Preparar um conjunto de documentos anonimizados e testar duplicados, notas de crédito, IVA, documentos ilegíveis, múltiplas taxas, descontos, arredondamentos e datas/valores inconsistentes.

## Bloqueios antes da integração cloud ou automática

- Escolher e validar o software utilizado pelo restaurante.
- Reconciliar o histórico de migrações do Supabase, conforme `supabase/MIGRATION-RECONCILIATION-2026-11-02.md`, antes de criar tabelas ou buckets no projeto alojado.
- Definir permissões exclusivas do proprietário ou uma permissão financeira explícita.
- Definir sincronização, backups, retenção e eliminação segura.
- Não enviar faturas ou dados financeiros a um fornecedor externo de IA sem consentimento, minimização de dados e condições adequadas de tratamento.

## Limites da versão atual

O arquivo, a memória e os dados financeiros são locais à instalação; não sincronizam entre telemóveis e não têm backup cloud. O OCR funciona no dispositivo e não envia os documentos a um serviço externo. Os campos fiscais são introduzidos e confirmados pelo proprietário. A base Supabase de produção não foi alterada nesta fase, porque a reconciliação documentada do histórico de migrações continua a bloquear novas tabelas/buckets.

O gestor económico, as perguntas sobre faturas e os cálculos fiscais continuam a usar regras e dados locais. Existe nesta branch uma ligação opcional a Gemini para conversa geral, protegida por consentimento e filtros heurísticos; a pesquisa Google Search é também opcional e tem consentimento separado, podendo ter custos após a quota gratuita do fornecedor. Nem o arquivo, nem OCR, nem valores fiscais/financeiros são enviados nesta ligação. O código da Edge Function ainda precisa de ser publicado e configurado no Supabase antes de poder ser usado em produção. A análise não substitui contabilidade certificada, validação fiscal ou o contabilista.
