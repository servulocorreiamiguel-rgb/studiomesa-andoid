# StudioMesa chat, memória e agente financeiro — 03/11/2026

## Funcionalidades desta branch

- Chat com mensagens alinhadas, nova conversa, pesquisa e histórico acessível diretamente no ecrã.
- O histórico permite pesquisar, reabrir e eliminar conversas.
- Memória editável, consultável no chat e separada por conta/restaurante.
- Histórico e memória cifrados com AES-GCM; chave mantida no Android Keystore.
- Persistência local dos campos e relatórios do gestor económico.
- Arquivo de faturas/notas de crédito, pesquisa, registo e exportação CSV.
- Anexos originais cifrados com AES-GCM em armazenamento privado, com chave Android Keystore.
- OCR local com ML Kit para imagens JPEG/PNG/WebP e páginas PDF convertidas localmente em imagens. O PDF é limitado a 10 páginas por leitura; o texto extraído é limitado a 20 000 caracteres. TXT e XML são lidos localmente.
- O texto OCR fica guardado de forma cifrada junto da fatura e é pesquisável/consultável no assistente. É apresentado como texto por confirmar, não como validação do documento.
- O assistente calcula totais registados de faturas/notas de crédito, IVA líquido e valores por fornecedor; pode devolver linhas/texto OCR para inspeção.
- O proprietário mantém de confirmar fornecedor, número, data, base tributável, IVA e total. O OCR não preenche nem altera automaticamente estes campos.

## Limites importantes

- Toda a persistência de histórico, memória, dados económicos, registo de faturas e texto OCR é **local ao dispositivo**, cifrada e separada por conta/restaurante. Não existe sincronização entre telemóveis nem backup cloud.
- A interface de chat não significa que exista já um modelo ChatGPT, Gemini ou Grok ligado. As respostas continuam a usar o motor local determinístico e as análises explícitas. Não há respostas generativas, pesquisa na Internet nem chamadas a ferramentas externas.
- O OCR é executado localmente e não envia documentos nem texto extraído para um serviço cloud. Pode interpretar mal números, datas, IVA, tabelas ou nomes; qualquer valor fiscal tem de ser confirmado contra o original.
- O reconhecimento de PDF processa até 10 páginas por importação. Documentos com mais páginas, baixa resolução, texto manuscrito, tabelas complexas ou formatos não suportados podem não produzir resultado útil.
- O arquivo local não está ligado a software de faturação/contabilidade. O SAF-T de faturação do restaurante não deve ser assumido como arquivo das faturas de compras de fornecedores.
- A base Supabase alojada não foi alterada. A reconciliação documentada em `supabase/MIGRATION-RECONCILIATION-2026-11-02.md` continua a bloquear novas tabelas, buckets e sincronização cloud até revisão.

## Segurança

- Dados separados pelo âmbito do ID de conta e restaurante.
- Histórico, memória, entradas financeiras, metadados da fatura e texto OCR guardados cifrados com AES-GCM; chave no Android Keystore.
- Anexos originais cifrados numa pasta privada da app, também com chave no Android Keystore.
- A análise financeira e o OCR não transmitem documentos a fornecedores externos nesta versão.
- O acesso à área financeira/faturas fica limitado ao proprietário quando existe um restaurante remoto ligado. A proteção local do dispositivo não substitui gestão remota de sessões ou controlo de permissões cloud.

## Testes

- Testes instrumentados para persistência e separação por âmbito do histórico e memória.
- Testes instrumentados para persistência cifrada de entradas económicas e faturas, pesquisa e rejeição de duplicados.
- Ainda é necessário confirmar a nova dependência ML Kit, o OCR de imagens/PDF em dispositivos/emuladores e o percurso completo de importação.
- A compilação, testes unitários, Lint, smoke tests Android 8/16 e replay/autorização das migrações têm de passar antes de integrar na branch principal ou distribuir APK.
