# StudioMesa — auditoria de segurança e dados

**Data da observação:** 10 de novembro de 2026  
**Projeto Supabase:** `svwvkfsyevuyabelrtjk` (região UE; plano Free)  
**Código base auditado:** `main` em `2ce4ad461ada5f0881f9d3c7f6f9d7d547cefd28`  
**Revisão de segurança em desenvolvimento:** PR #102, branch `release/security-hardening-2026-11-10`  
**Decisão:** **não liberar para dados reais, piloto ou venda ainda**.

Este documento regista verificações feitas de forma read-only e alterações propostas em código. Não representa uma certificação, auditoria jurídica, pentest independente ou garantia de segurança absoluta.

## 1. O que foi verificado no Supabase alojado

| Controlo | Resultado observado | Interpretação |
|---|---|---|
| Estado do projeto | Ativo/saudável na região UE | O serviço está ativo neste momento; não é garantia de disponibilidade |
| Tabelas `public` e `private` | 26 tabelas base observadas; todas com RLS ativo | Não foi encontrada uma tabela sem RLS nestes esquemas |
| Acesso direto `anon` | Sem SELECT/INSERT direto nas tabelas consultadas | Acesso público direto fechado |
| Tabelas RLS sem políticas | 11 alertas INFO do Advisor | RLS sem políticas resulta em negação por defeito; algumas são tabelas privadas/RPC-only. Confirmar que continuam sem grants diretos antes de qualquer nova migration |
| Funções `SECURITY DEFINER` | 42 alertas WARN para funções executáveis por `authenticated` | Não revogar em bloco: wrappers e funções internas têm propósitos distintos. Exige revisão função a função e testes negativos com contas descartáveis |
| `anon` nas funções privilegiadas consultadas | Sem EXECUTE para as funções `SECURITY DEFINER` consultadas | Não foi encontrado acesso anónimo nessas funções |
| `search_path` | `search_path=""` nas funções privilegiadas consultadas | Boa proteção contra shadowing de objetos; manter em futuras funções |
| Proteção de palavras-passe comprometidas | Desativada no projeto | Bloqueio de autenticação/segurança por resolver. Confirmar plano e opções suportadas antes de mudar |
| Bucket `studiomesa-invoices` | Privado; 25 MiB; PDF/JPEG/PNG/WebP | Não é público |
| Políticas Storage | INSERT, SELECT e DELETE para utilizador autenticado que seja `owner` no restaurante indicado no primeiro segmento do caminho | Acesso aos ficheiros limitado ao proprietário do restaurante. Testes com contas reais de dois restaurantes continuam obrigatórios |
| Advisors de performance | 4 FKs sem índice de cobertura; 45 índices marcados como não utilizados | Rever índices em uso real; não apagar índices apenas por estatísticas de uso ainda pequenas |

### Tabelas de funcionalidades ausentes no backend

Estas três tabelas não existem no projeto alojado consultado:

- `public.restaurant_recipes`
- `public.restaurant_recipe_ingredients`
- `public.restaurant_staff_permissions`

As respetivas migrations estão no repositório local mas não constam do histórico remoto. As funcionalidades que dependem destas tabelas não devem ser anunciadas nem ativadas para clientes até haver reconciliação, backup/restauro e deployment validado.

### Divergência de migrações — bloqueio crítico

| Fonte | Número de versões |
|---|---:|
| Ficheiros locais em `supabase/migrations` | 55 |
| Histórico remoto no Supabase | 63 |
| Prefixos/versões comuns | 51 |
| Apenas no repositório | 4 |
| Apenas no projeto alojado | 12 |

Migrações apenas locais:
- `20261030111815`
- `20261030120000`
- `20261102103000`
- `20261104100000`

Versões apenas remotas:
- `20261102082151`
- `20261102103246`
- `20261102103627`
- `20261102104650`
- `20261102113719`
- `20261102113944`
- `20261108120141`
- `20261108120201`
- `20261108124820`
- `20261108125118`
- `20261108164151`
- `20261108164623`

As 51 versões comuns não foram consideradas equivalentes só pelo prefixo/timestamp. É necessário comparar o SQL exato e recuperar as 12 versões remotas. **Não executar `supabase db push`, reset, merge, rebase ou migration manual no projeto alojado antes de reconciliar os conteúdos e testar backup/restauro.**

## 2. Proteções já existentes no cliente Android

- `allowBackup=false` e `usesCleartextTraffic=false` na aplicação Android.
- `FileProvider` não exportado e limitado à pasta `cache/invoices/`; a auditoria estática passa a bloquear a exposição de caminhos externos/root.
- Sessões guardadas com AES-GCM e Android Keystore.
- Histórico e Memória locais cifrados com AES-GCM; cópia cloud opcional e desligada por defeito desde a revisão de privacidade.
- A sincronização cloud usa Auth e RLS, mas não é cifragem ponta a ponta. O conteúdo sincronizado pode ser tecnicamente acessível ao operador de backend com privilégios adequados; a app não deve prometer E2E.
- OCR de faturas é local; texto bruto OCR não é enviado ao modelo generativo.
- O proprietário revê os campos extraídos antes de os aceitar; OCR não aprova, paga nem lança documentos automaticamente.
- Chave cliente Android começa por `sb_publishable_`; pesquisa de repositório não encontrou `sb_secret_` nem `service_role` em código do cliente.
- O modelo generativo corre localmente; a Edge Function `studiomesa-assistant` devolve HTTP 410 e não recebe prompts para inferência.

## 3. Alteração de segurança nesta revisão — PR #102

Risco identificado: `binaryStorageRequest` usava `readBytes()` sem limite, verificando o tamanho do documento só depois de o corpo ser integralmente carregado para memória.

Correção proposta em PR separada:
- Limitar respostas JSON normais a 4 MiB e corpos JSON de erro a 64 KiB.
- Paginar o download do histórico do assistente em blocos de 100 mensagens, ordenados de forma estável por data e ID, com teto de 5 000 mensagens.
- Limitar a leitura incremental de documentos Storage a 25 MiB.
- Rejeitar `Content-Length` anunciado acima do limite antes de ler o stream.
- Manter a verificação de tamanho mesmo quando o servidor não envia ou declara incorretamente `Content-Length`.
- Limitar corpos de erro de Storage a 64 KiB, preservando HTTP status/path e mensagens sanitizadas.
- Acrescentar testes unitários para o leitor binário e para a paginação, além de testes estáticos para impedir regressões de transporte, HTTPS, chaves privilegiadas e consultas de histórico sem paginação.

Estado da CI do commit atual `dae6766eb494aa8beac61c78140f7fcfc7750cff`: em validação à hora da atualização deste documento. Não marcar os passos como aprovados até os quatro jobs de CI terminarem com sucesso.

## 4. Riscos ainda não fechados

1. **Migrações divergentes:** risco de alterar ou perder dados se forem aplicadas migrações sem reconciliar histórico e schema.
2. **42 funções SECURITY DEFINER sinalizadas:** rever assinatura, owner/staff/cliente permitido, IDs de restaurante, chamadas diretas indevidas, idempotência e acesso entre restaurantes. A auditoria estática ajuda, mas não prova autorização runtime.
3. **Proteção contra palavras-passe comprometidas desativada:** avaliar plano/feature suportada e ativar antes de produção, sem assumir que está incluída no Free.
4. **SMTP, confirmação de email, Google e Microsoft OAuth:** credenciais e redirect URIs próprios ainda por configurar e provar ponta a ponta.
5. **Eliminação de conta:** testar todo o fluxo PKCE/email, fila, timeout, rollback e eliminação do restaurante com contas descartáveis.
6. **Backup/restauro:** não há evidência de restauro recente num projeto isolado com sucesso; fazer antes de migrations/deployment.
7. **Sincronização:** testar dois dispositivos, perda de rede, conflitos, logout/mudança de conta, tombstones e acesso entre dois restaurantes.
8. **Dados de produção:** não existe evidência de teste de penetração externo nem de revisão independente de incident response/logging.
9. **Legal e privacidade:** falta preencher a entidade responsável, contactos, bases jurídicas, retenção, subcontratantes, DPA e política final. O aviso de privacidade tem de descrever corretamente a sincronização cloud sem E2E e os fornecedores do modelo/OCR.
10. **Dispositivo físico:** testar OPPO A5m e aparelhos abrangidos, incluindo download do modelo, inferência, RAM, aquecimento, crash, PDFs/imagens grandes e atualizações.

## 5. Regras para proteger dados de clientes

- Só usar contas, clientes, faturas, NIFs e reservas sintéticas/anónimas em testes.
- Nunca colocar `service_role`, `sb_secret`, passwords, SMTP/OAuth secrets ou keystore em APK, Git, logs ou documentação pública.
- Não ativar sincronização ou marketing por defeito sem consentimento claro e reversível.
- Não expor dados do restaurante em pesquisa Web nem enviá-los ao modelo externo.
- Manter buckets privados; autorizar cada operação no servidor e filtrar por restaurante/conta.
- Rever logs para que não guardem tokens, PDFs, OCR, contactos ou conteúdo pessoal desnecessário.
- Definir retenção e processo operacional de acesso, exportação e eliminação.
- Não executar alteração de schema/permissões de produção sem backup, plano de rollback e teste isolado.

