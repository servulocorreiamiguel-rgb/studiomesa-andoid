# StudioMesa — auditoria de fecho comercial

**Data da auditoria:** 10 de novembro de 2026  
**Repositório:** [studiomesa-andoid](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid)  
**Commit de partida:** `38058cee6a4febc3faf37c527cb6ea4615fb4da3`  
**Decisão:** **NÃO LIBERADA para venda/produção com dados reais.** A alteração que oculta Google/Microsoft foi integrada; o gate comercial continua bloqueado por migrações divergentes, autenticação real, testes E2E/físicos e preparação de release/legal.

## 1. Alteração integrada nesta ronda

- PR [#107](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/pull/107) integrada por squash em `main`, commit `38058cee6a4febc3faf37c527cb6ea4615fb4da3`.
- Os botões Google e Microsoft ficam ocultos por defeito. A implementação OAuth/PKCE foi preservada para ativação posterior à primeira venda.
- A CI da PR passou os quatro jobs: compilação/testes, replay PostgreSQL/autorização, smoke tests Android 8 e Android 16.
- Foi acionada CI para o commit integrado: run [42865847812](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/42865847812). Na consulta desta auditoria, o job de replay PostgreSQL/autorização estava concluído com sucesso e o job de compilação ainda estava em execução. Não declarar a CI de `main` aprovada até a execução terminar com todos os jobs verdes.

## 2. Estado observado do Supabase alojado

Projeto `svwvkfsyevuyabelrtjk`, região `eu-west-1`, estado `ACTIVE_HEALTHY`, PostgreSQL 17.6.1.166.

- 26 tabelas listadas nos esquemas `public` e `private`; todas com RLS ativo. As tabelas observadas tinham zero linhas no momento da consulta.
- As tabelas `public.restaurant_recipes`, `public.restaurant_recipe_ingredients` e `private.restaurant_staff_permissions` continuam ausentes. Não ativar fichas técnicas, consumo automático de ingredientes ou permissões granulares de funcionários.
- O histórico remoto tem 63 migrações; o repositório contém 61 ficheiros SQL. Existem 58 versões comuns, três só locais e cinco só remotas.
- Três ficheiros presentes apenas no repositório:
  - `20261030111815_restaurant_recipes_and_order_stock_consumption.sql`
  - `20261102103000_granular_restaurant_staff_permissions.sql`
  - `20261104100000_price_adviser_actor_indexes.sql`
- Cinco versões presentes apenas no histórico remoto:
  - `20261102103246`
  - `20261102103627`
  - `20261102104650`
  - `20261102113719`
  - `20261102113944`
- Não foi feito `db push`, `migration repair`, reset, merge de branch Supabase nem alteração de esquema. Não existe branch de desenvolvimento Supabase disponível. A divergência tem de ser reconciliada por comparação do SQL original, backup e restauro comprovado num ambiente separado.

## 3. Verificações SQL de segurança (só leitura)

- 47 funções `SECURITY DEFINER` em `public`; 42 executáveis por `authenticated`, zero executáveis por `anon`.
- As 47 funções referenciam `auth.uid()` e têm `search_path = ''`.
- O Advisor assinala 11 tabelas com RLS sem políticas. A consulta de privilégios confirmou que nenhuma dessas 11 concede `SELECT`, `INSERT`, `UPDATE` ou `DELETE` diretamente a `anon` ou `authenticated`. Manter este modelo fechado.
- A existência de `auth.uid()` não substitui a revisão função a função: é necessário verificar o vínculo ao restaurante, o papel exigido, os parâmetros, a concorrência e a idempotência.
- O Advisor continua a assinalar a proteção contra palavras-passe comprometidas desativada, quatro foreign keys sem índice de cobertura e 45 índices sem utilização observada. Não foram feitas alterações automáticas. Não remover índices sem medir carga real.

## 4. Edge Functions observadas

| Função | Estado observado | Implicação |
|---|---|---|
| `delete-account` | Ativa, versão 3, JWT obrigatório | Endpoint de eliminação autenticado; E2E continua pendente. |
| `account-deletion-web` | Ativa, versão 3, sem JWT no gateway | Página pública de pedido de eliminação; o fluxo de identidade usa recuperação/PKCE e chama o endpoint autenticado separado. Exige teste E2E. |
| `studiomesa-assistant` | Ativa, versão 10, JWT obrigatório | Responde HTTP 410 e declara que a IA na nuvem está desativada. A IA usada na primeira versão é local. |
| `studiomesa-web-search` | Ativa, versão 3, JWT obrigatório | Só pesquisa se existir um `SEARXNG_BASE_URL` HTTPS válido; a configuração do segredo não foi possível confirmar com as ferramentas disponíveis. |
| `studiomesa-advanced-assistant` | Não aparece na lista de funções alojadas | O código existe no repositório, mas não está publicado no projeto. Requer chave Groq e validação de fornecedor, custos e privacidade. |

Não ativar a IA avançada nem anunciar pesquisa Web como operacional sem configuração, testes autenticados, limites de despesa e validação de privacidade.

## 5. Bloqueios que exigem configuração, testes ou decisão fora do código

1. **Email/SMTP:** configurar domínio/remetente e SMTP próprio no Supabase Auth; validar confirmação, recuperação, refresh, logout e eliminação com contas descartáveis. As ferramentas ligadas não permitem configurar diretamente estes parâmetros nem testar uma caixa de correio real.
2. **Migrações:** recuperar/comparar as cinco versões remotas em falta no Git e comparar integralmente as 58 versões comuns; preparar backup e comprovar restauro. Só depois planear deployment e validação das três tabelas ausentes.
3. **Autorização:** revisão das 42 funções privilegiadas, testes cruzados com proprietário/funcionário/cliente de dois restaurantes e verificação de concorrência, sessão expirada e repetição de pedidos.
4. **IA e pesquisa Web:** a IA cloud está desligada; faltam segredo Groq e publicação controlada. A pesquisa Web exige servidor SearXNG HTTPS e confirmação de configuração. O modelo LFM2.5-1.2B local precisa de teste físico de instalação, RAM, latência, aquecimento, bateria e qualidade.
5. **Testes físicos/E2E:** OPPO A5m e outros Android, dois dispositivos, perda/restauro de rede, atualização, reservas, pedidos, cozinha, fidelização, inventário, campanhas, faturas e eliminação de conta.
6. **Release:** escolher package IDs finais; criar e guardar uma keystore comercial com backup seguro; configurar GitHub Actions secrets/variables; executar o workflow manual apenas a partir de `main` com CI verde; verificar AAB/APK, assinatura, checksums e atualização. Os artefactos normais de CI são debug/demo.
7. **Privacidade e operação:** política de privacidade pública, termos, DPA, subcontratantes, retenção, exportação/eliminação, incident response, SBOM e licenças de dependências/modelo. A entidade responsável deve confirmar os dados legais e assumir os contratos e distribuição.
8. **Produto:** não anunciar integração POS, pagamentos, faturação, SAF-T, contabilidade ou impressoras; não estão implementados. Não apresentar previsões baseadas em pedidos como pagamentos recebidos ou faturação contabilística.

## 6. O que foi deliberadamente deixado intacto

- Nenhuma credencial, password, API key ou chave de assinatura foi criada, copiada ou publicada.
- Nenhuma migration foi aplicada ao Supabase alojado.
- Nenhuma função, política RLS, permissão ou tabela foi alterada em produção.
- Nenhum plano pago foi ativado nem foi assumido qualquer custo.
- Nenhuma conta ou dado de cliente foi criado ou alterado.

## Critério de autorização comercial

A StudioMesa só pode ser marcada como pronta para venda operacional depois de: (a) migrações e backup/restauro reconciliados; (b) autenticação por email testada; (c) autorização entre tenants validada; (d) E2E e testes físicos aprovados; (e) release comercial assinada validada; e (f) documentação legal/privacidade e licença do modelo verificadas pela entidade responsável.

**É possível apresentar uma demonstração com dados fictícios. Não é seguro prometer ainda operação diária com dados reais de um restaurante.**
