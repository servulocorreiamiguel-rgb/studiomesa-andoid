# StudioMesa — bloqueios de lançamento verificados

**Verificação:** 6 de novembro de 2026  
**Branch analisada:** `main`  
**Commit analisado:** `a6e78a3a8388389f521e78a1a167da4dab676a85`

## Resultado

O código Android da primeira fase do Assistente local e a migração SQL correspondente estão presentes em `main`. A CI do commit candidato `4d8fa85878c6c20f1ad81138fb69ffd961921083` terminou com sucesso na execução GitHub Actions `42110854314`: compilação das variantes, testes unitários, Android Lint, replay de migrações/testes PostgreSQL e smoke tests em Android 8/API 26 e Android 16.

Isto valida o candidato de código em ambiente automatizado. **Não valida a instalação num telefone físico, nem a configuração do projeto Supabase alojado, nem a aptidão comercial.**

## Verificação read-only do Supabase alojado

Projeto: `svwvkfsyevuyabelrtjk`.

A consulta de migrações e verificações SQL read-only em 06/11/2026 confirmaram:

- A migração `20261105110500_studiomesa_assistant_sync_and_supplier_invoices` não consta do histórico remoto.
- As tabelas `public.restaurant_assistant_conversations`, `public.restaurant_assistant_messages`, `public.restaurant_assistant_memory` e `public.restaurant_supplier_invoices` não existem no projeto alojado.
- Não existe qualquer bucket em `storage.buckets`.
- A lista remota de migrações continua divergente do conjunto local; a auditoria anterior registou versões remotas sem ficheiro local correspondente.

Consequência: a sincronização cloud do histórico/memória e o arquivo remoto de faturas **não estão operacionais no Supabase alojado**. As funcionalidades locais podem ser testadas, mas não se deve prometer sincronização entre dispositivos nem arquivo cloud ativo.

### Regra de segurança

Não executar `db push`, `migration repair`, `apply_migration` nem aplicar manualmente a migração de IA ao projeto de produção enquanto a divergência do histórico não for reconciliada. Antes disso são necessários backup verificável, ensaio de restauro, replay numa cópia descartável e plano de reversão. A verificação atual foi apenas de leitura; não alterou o projeto alojado.

## Bloqueios restantes antes de piloto comercial

1. Reconciliar com segurança o histórico de migrações Supabase e só depois preparar o deploy da migração de IA.
2. Criar o bucket privado de documentos e políticas de Storage restritas ao proprietário/restaurante, com limites de tamanho e MIME; validar upload, download e eliminação.
3. Configurar Auth real: SMTP, confirmação de email, recuperação de palavra-passe, URLs de retorno e OAuth, se aplicável.
4. Executar testes com contas descartáveis distintas: proprietário, funcionário, cliente e dois restaurantes; confirmar isolamento e recusa de acesso cruzado.
5. Testar sincronização entre dois dispositivos, conflitos, eliminação de conversas, sessão expirada e falhas de rede.
6. Testar OCR e arquivo com faturas reais anonimizadas, PDFs inválidos, imagens, NIF inválido, totais inconsistentes, duplicados e permissões SAF revogadas.
7. Instalar no OPPO A5m físico e medir instalação do modelo (~736 MB), espaço, arranque, RAM, latência, temperatura, bateria, teclado e qualidade do português europeu.
8. Rever as licenças LFM Open License v1.0 e Apache 2.0 antes de qualquer utilização comercial.
9. Definir identificador de pacote comercial, política de privacidade, Google Play Data safety, retenção/eliminação de dados, backup/restauro e procedimento de suporte.
10. Criar keystore comercial fora do repositório, configurar secrets de assinatura e produzir AABs comerciais assinados. Os APKs debug não são adequados para venda/publicação.
11. Fazer piloto controlado com dados fictícios num restaurante de teste e obter revisão independente de segurança antes de dados reais.

## Pull request da IA

A PR #85 continua aberta e divergente: o head tem 102 commits à frente e 5 atrás de `main`, e o GitHub indica que não é atualmente mergeable. Os ficheiros centrais do modelo local, histórico cifrado e migração SQL foram comparados entre `main` e a branch e têm os mesmos hashes. Não se deve tentar forçar o merge dessa branch divergente. A presença dos ficheiros em `main` e a CI acima são as referências para a implementação integrada.

## Critério de conclusão

StudioMesa só pode ser marcado como **pronto para piloto** depois dos pontos 1–7 estarem concluídos e registados. Só pode ser marcado como **pronto para venda/distribuição comercial** depois dos pontos 8–11, da revisão legal/privacidade e da validação de uma build assinada. Até lá, classificar como demonstração técnica avançada, não como produto comercial pronto.
