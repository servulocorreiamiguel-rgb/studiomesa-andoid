> **Atualização após o merge da PR #107 — 10/11/2026:** a alteração que oculta os botões Google/Microsoft está na `main` (commit `38058cee6a4febc3faf37c527cb6ea4615fb4da3`). A CI de `main` estava ainda em execução na última consulta. Para o estado atual do Supabase, Edge Functions, migrações e bloqueios comerciais, consultar [a auditoria de fecho comercial](COMMERCIAL-CLOSEOUT-AUDIT-2026-11-10.md). A app continua **não liberada para venda operacional**.
>
> **CI mais recente — 10/11/2026, 16:38 UTC: SUCESSO.** A execução [42852321328](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/42852321328), no commit `68da629fc3ecb0a68fea220b0a89b139d033f381`, terminou com os quatro jobs em sucesso. Passaram compilação das cinco variantes de demonstração, testes unitários, Android Lint, validação de cinco AAB/APKs com chave CI temporária, replay de migrations e autorização numa base PostgreSQL isolada e testes de interface em emuladores Android 8 (API 26) e Android 16 (API 36). Os APKs publicados como artefacto são **debug/demo**, não são releases comerciais e não usam a chave definitiva do titular. Esta CI não valida OAuth real, SMTP, dados do Supabase alojado, execução no OPPO A5m, DPA ou aprovação legal.
>
# StudioMesa — atualização de preparação comercial

**Data:** 10 de novembro de 2026  
**Repositório:** `servulocorreiamiguel-rgb/studiomesa-andoid`  
**Estado:** **NÃO AUTORIZADO para venda, piloto com dados reais ou publicação.**  
**Última CI integral:** execução [42844678804](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/42844678804), commit `210f4199502484d4b7c350851f5de003632db9ed`, concluída em sucesso a 10/11/2026. Passaram os quatro jobs: build/testes, Lint e validação de assinatura temporária das cinco variantes, replay PostgreSQL/autorização e UI smoke tests em Android 8/16. Os APKs do artefacto são debug/demo; a assinatura comercial definitiva continua pendente.

## Trabalho confirmado

- A integração de código do GPT-OSS 120B foi integrada na `main` por merge da PR #106.
- A CI do commit `c217bfdf` concluiu os quatro jobs: compilação e testes unitários das cinco variantes, Android Lint, replay de migrations/testes de autorização PostgreSQL e smoke tests em emuladores Android 8 e Android 16.
- O código mantém a IA local LFM2.5-1.2B INT4, OCR local com revisão humana, histórico/memória cifrados no dispositivo, funcionalidades económicas determinísticas e previsão baseada nos pedidos existentes.
- O projeto Supabase consultado está `ACTIVE_HEALTHY` na região `eu-west-1`.
- A função avançada GPT-OSS não está publicada no Supabase alojado. Não foi configurada uma chave `GROQ_API_KEY`.
- A integração cloud foi, por isso, colocada atrás da propriedade de build `studiomesaAdvancedAssistantCloudEnabled`, desativada por omissão. Enquanto o serviço, privacidade, custos e limites não forem aprovados, a interface não oferece a ativação online; a IA local continua disponível.
- O código GPT-OSS não dá à IA permissões de escrita no restaurante. A pesquisa pública está isolada do histórico, da memória e dos dados privados do restaurante.

## IA: custos e licenças

### GPT-OSS 120B através da Groq

O serviço não deve ser anunciado como gratuito ou com custos ilimitados. A página de modelos da Groq publica, na data desta revisão, **USD 0,15 por milhão de tokens de entrada** e **USD 0,60 por milhão de tokens de saída** para GPT-OSS 120B. Limites e preços podem mudar; a organização deve confirmar o plano e configurar limites de despesa antes de o ativar.

A inferência, Browser Search e Code Interpreter são serviços do fornecedor externo. A utilização de pesquisa e execução de código não deve receber dados pessoais, faturas, texto OCR, dados de clientes ou dados bancários. A revisão do DPA, subcontratantes, transferências internacionais, retenção e informação ao cliente continua pendente.

Fontes oficiais consultadas:
- Groq — modelos e preços: https://console.groq.com/docs/models
- Groq — limites: https://console.groq.com/docs/rate-limits
- Groq — Browser Search: https://console.groq.com/docs/tool-use/built-in-tools/browser-search
- Groq — Code Execution: https://console.groq.com/docs/tool-use/built-in-tools/code-execution
- Supabase — segredos de Edge Functions: https://supabase.com/docs/guides/functions/secrets

### Modelo local LFM2.5

O modelo LFM2.5-1.2B-INT4 distribuído pela aplicação é identificado como sujeito à **LFM Open License v1.0**. A Liquid AI indica que a utilização comercial ao abrigo dessa licença deixa de estar autorizada quando a receita anual da entidade legal abrangida atinge USD 10 milhões ou mais; acima do limite é necessária uma licença comercial separada. A licença e a situação da entidade que vai vender devem ser confirmadas por aconselhamento jurídico antes da distribuição comercial.

Fontes:
- Modelo e licença indicada no cartão: https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct
- Texto e FAQ da Liquid AI: https://www.liquid.ai/lfm-license

O modelo ocupa cerca de 736 MB e é descarregado no dispositivo. A qualidade, estabilidade, consumo de RAM/bateria, temperatura e latência ainda não foram medidos no OPPO A5m nem numa matriz suficiente de telemóveis de clientes.

## Bloqueios do backend e dos dados

A fotografia read-only de 10/11/2026 confirma:
- 61 migrations SQL no repositório e 63 versões no histórico remoto; 58 versões têm timestamp comum, três existem apenas no repositório e cinco apenas no servidor. As três locais são `20261030111815` (receitas/consumo de stock), `20261102103000` (permissões granulares) e `20261104100000` (índices do consultor de preços). As cinco apenas remotas são `20261102103246`, `20261102103627`, `20261102104650`, `20261102113719` e `20261102113944` (grupo de migrações de QR/fidelização e fronteiras de funções privadas). Os 58 timestamps comuns não provam que o SQL seja byte a byte igual.
- As tabelas `public.restaurant_recipes`, `public.restaurant_recipe_ingredients` e `private.restaurant_staff_permissions` não existem no backend alojado.
- Histórico, mensagens, memória e metadados de faturas existem no backend, assim como o bucket privado `studiomesa-invoices`; a sincronização/arquivo ainda exigem testes E2E com contas de teste.
- O Security Advisor identifica 42 funções `SECURITY DEFINER` executáveis por `authenticated`, 11 tabelas com RLS sem políticas e a proteção contra palavras-passe comprometidas desativada. Uma consulta SQL read-only posterior confirmou que as 42 funções executáveis por `authenticated` têm `search_path` explicitamente vazio, nenhuma é executável por `anon` e todas referenciam `auth.uid()`; nenhuma das 11 tabelas sem políticas concede privilégios diretos `SELECT`/`INSERT` a `anon` ou `SELECT`/`INSERT`/`UPDATE`/`DELETE` a `authenticated`. Estes resultados reduzem o risco de exposição direta, mas não substituem a revisão semântica função a função nem os testes de isolamento entre restaurantes. Não revogar permissões em bloco. A proteção contra palavras-passe comprometidas continua desativada.
- A consulta direta ao catálogo confirmou que as 42 funções públicas `SECURITY DEFINER` executáveis por `authenticated` têm `search_path` explicitamente vazio; nenhuma é executável por `anon` e todas referenciam `auth.uid()`. A verificação não substitui a revisão da autorização de cada operação, especialmente de acessos entre restaurantes.
- Nas 11 tabelas com RLS ativo mas sem políticas, os privilégios diretos de `anon` e `authenticated` para SELECT/INSERT/UPDATE/DELETE foram confirmados como falsos. O Advisor assinala a ausência de políticas, mas a verificação não encontrou exposição direta por privilégios de tabela.
- O bucket `studiomesa-invoices` foi confirmado como privado, com limite de 25 MiB e MIME types PDF, JPEG, PNG e WebP. As políticas Storage de leitura, inserção e eliminação exigem associação do utilizador como proprietário do restaurante indicado no primeiro segmento do caminho. Falta testar upload, download, substituição e eliminação com duas contas reais de restaurantes diferentes.
- As políticas RLS atuais das tabelas `restaurant_assistant_conversations`, `restaurant_assistant_messages`, `restaurant_assistant_memory` e `restaurant_supplier_invoices` exigem associação `owner` ao mesmo `restaurant_id` e `auth.uid()`. As inserções verificam também `created_by`/`updated_by`, e as mensagens têm de pertencer a uma conversa ativa do mesmo restaurante. Funcionários não recebem acesso direto a estes registos via Data API; a separação entre duas contas reais ainda precisa de testes E2E.
- O Advisor de desempenho continua a assinalar quatro chaves estrangeiras sem índice e 45 índices sem utilização observada. Não remover nem criar índices indiscriminadamente sem analisar carga, planos e impacto.
- A proteção contra palavras-passe comprometidas continua desativada no Supabase Auth; é necessário decidir a ativação e confirmar eventuais requisitos de plano/custo.
- A organização Supabase está no plano `Free`. O primeiro lançamento usa email/palavra-passe: é obrigatório configurar SMTP próprio e confirmação de email. Os botões Google/Microsoft ficam ocultos e só serão ativados depois da primeira venda; a configuração OAuth é uma melhoria posterior, não um bloqueio do primeiro lançamento. A proteção contra palavras-passe comprometidas exige Pro ou superior. Não foi feito upgrade: https://supabase.com/pricing e https://supabase.com/docs/guides/auth/password-security.
- O workflow GitHub `supabase-keepalive.yml` está configurado para quatro chamadas diárias a uma RPC read-only que apenas devolve `true`. Foram encontradas sete execuções agendadas concluídas com sucesso; a mais recente foi [42782343177](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/42782343177), às 08:24 UTC de 10/11/2026. O plano Free pode pausar projetos após uma semana de inatividade; o heartbeat reduz esse risco, mas GitHub Actions não fornece garantia de execução pontual nem SLA: https://supabase.com/pricing.
- O histórico remoto não está integralmente reconciliado com os ficheiros do repositório. Não executar `db push`, `migration repair`, reset ou deployment de migrations até comparar SQL/objetos e provar backup e restauro num ambiente separado.

## Bloqueios de autenticação e operação

Ainda não estão demonstrados, com contas descartáveis e percurso ponta a ponta:
- registo, confirmação de email, SMTP, login, recuperação PKCE, refresh, logout, expiração e revogação;
- Google/Microsoft OAuth e URLs de retorno ficam adiados até depois da primeira venda; os botões não aparecem na versão inicial.
- isolamento entre dois restaurantes para proprietário, funcionário e cliente;
- reservas, pedidos, cozinha, fidelização, resgates, campanhas e eliminação de conta;
- sincronização do histórico/memória entre dispositivos, conflitos, rede interrompida e eliminação/tombstones;
- backup e restauro verificados, monitorização, retenção e resposta a incidentes.

## Bloqueios de distribuição e conformidade

- Os APKs produzidos pela CI são debug/demo, não pacotes comerciais assinados.
- Os identificadores finais de pacote ainda usam `pt.studiomesa.preview.*`; o identificador comercial definitivo ainda não foi escolhido.
- Falta criar e guardar fora do repositório um keystore comercial definitivo, com backup seguro, e configurar os GitHub Actions secrets/variables exigidos pelo workflow.
- Falta produzir e validar a release assinada AAB/APK, checksums, atualizações sobre uma versão anterior e distribuição em dispositivos físicos.
- A política de privacidade pública, Data Safety, termos, DPA, lista de subcontratantes, retenção, contactos do responsável e processo de suporte/incidentes têm de refletir a configuração final. Não se podem preencher declarações legais com dados ou contactos inventados.
- A Google Play exige política de privacidade acessível e uma declaração Data Safety coerente com o tratamento real, incluindo bibliotecas e SDKs: https://support.google.com/googleplay/android-developer/answer/18265346
- Se o StudioMesa atuar como subcontratante dos restaurantes, o contrato de tratamento tem de definir instruções, segurança, subprocessadores e o que acontece aos dados no fim do serviço. A Comissão Europeia explica as obrigações e as cláusulas aplicáveis: https://commission.europa.eu/law/law-topic/data-protection/information-business-and-organisations/application-gdpr_en

## Próxima sequência segura

1. Passar uma nova CI integral depois das alterações que desativam a IA cloud por omissão e atualizam a divulgação dos custos.
2. Executar os testes físicos do modelo local e da aplicação em aparelhos representativos, incluindo OPPO A5m.
3. Obter/confirmar o backup e o restauro do Supabase; reconciliar individualmente as migrations e as definições do catálogo antes de qualquer alteração remota.
4. Configurar SMTP e testar os percursos de email/conta E2E com contas e dados fictícios de dois restaurantes. Depois da primeira venda, ativar e testar Google/Microsoft OAuth, se for necessário.
5. Decidir se o serviço GPT-OSS online será incluído no produto. Caso sim, configurar a conta/chave Groq, limites de despesa, proteção contra abuso, DPA e divulgação; publicar a Edge Function só depois da revisão.
6. Confirmar a licença do modelo local e todas as licenças/transitivas das dependências, SBOM e avisos de terceiros para a entidade que vai distribuir.
7. Escolher o identificador de pacote, criar e salvaguardar o keystore comercial, executar o workflow assinado e validar atualização/distribuição.
8. Finalizar política de privacidade, Data Safety, contrato com o restaurante, retenção, suporte e resposta a incidentes com os dados e entidade reais.

**Decisão:** o código tem uma base funcional e passou uma CI abrangente, mas a aplicação não deve ser vendida nem usada com dados reais até os bloqueios de backend, autenticação, E2E, licenças, privacidade, assinatura e testes físicos estarem fechados. Uma build verde não é uma certificação nem substitui a validação de produção.
