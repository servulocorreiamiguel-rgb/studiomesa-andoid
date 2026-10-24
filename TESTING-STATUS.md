# StudioMesa — registo de testes e estado de lançamento

**Última revisão registada:** 2026-10-24 (atualizada após o CI mais recente)  
**PR de trabalho:** [#19 — Reforçar validação, rede e testes Android](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/pull/19)  
**Branch:** `fix/price-calculator-input-bounds`  
**Último commit de código validado pelo CI:** `865673fca1aa20890765d460293cc8e78ad2243d`  
**Última execução GitHub Actions:** [ID 40106562821](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/40106562821)  
**Execução anterior com XML detalhados verificados:** [ID 40081574672](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/40081574672)  
**Resultado dessa execução:** sucesso (ambos os jobs).

> Este registo distingue testes automáticos de validações de leitura e de testes manuais ainda não executados. A compilação e o emulador não substituem os testes ponta a ponta num restaurante ou em dispositivos reais.

## Testes automáticos concluídos — commit b346539 (execução anterior com resultados XML verificados)

| Verificação | Resultado |
|---|---|
| Validação dos delimitadores das funções SQL das migrações | Passou |
| Compilação das cinco variantes APK de demonstração | Passou |
| Testes unitários das cinco variantes | Passou |
| Android Lint | Passou |
| Bundle comercial de teste, com keystore temporária descartável | Passou |
| Testes de interface no emulador Android 16, nas cinco variantes | Passou |
| Upload dos APKs e dos relatórios XML de teste | Passou |

O job de compilação executou as tarefas de teste unitário de Essencial, Profissional e Premium, incluindo variantes de gestão/cliente. O teste de release usou uma identidade de pacote e uma chave temporárias; **não** representa a assinatura comercial final e não publica na Google Play.

### Resultados dos testes de interface — execução anterior (ID 40081574672)

Os cinco XMLs do artefacto `StudioMesa-Android16-Emulator-Test-Results` indicam 25 execuções de casos de teste em cinco variantes:

- **14 aprovadas**
- **11 ignoradas intencionalmente** por não serem aplicáveis à variante (por exemplo, calculadora na edição Essencial ou ecrã de gestão na aplicação de cliente)
- **0 falhas**
- **0 erros**

Os casos exercitaram o arranque, criação local de um pedido, abertura do fluxo de conta da aplicação de cliente, cálculo indicativo do preço e validação de custo excessivamente longo, conforme aplicável a cada variante.

Artefacto dos relatórios: [resultado da execução #295](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/40081574672).  
Artefacto dos APKs: `StudioMesa-1.5.0-Demo-APKs`, criado pela mesma execução e com retenção até 2026-11-07.

## Execução CI mais recente — commit 865673f

Execução [GitHub Actions ID 40106562821](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/40106562821), concluída em 24-10-2026:

| Verificação | Resultado |
|---|---|
| Compilação das cinco variantes de demonstração | Passou |
| Testes unitários das cinco variantes | Passou |
| Android Lint | Passou |
| Validação da configuração de bundle comercial com chave temporária | Passou |
| Testes de interface no emulador Android 16, nas cinco variantes | Passou |
| Publicação dos APKs e dos relatórios de interface | Passou |

Artefactos desta execução: [APKs de demonstração e relatórios](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/40106562821). O estado global e os dois jobs foram confirmados como sucesso: compilação/testes unitários/Lint/configuração de bundle comercial e testes de interface no emulador Android 16. A execução atual alargou os testes de interface às cinco variantes. A contagem detalhada de casos desta execução ainda não foi inspecionada a partir dos XML. Isto continua a ser validação de CI/emulador, não teste num telemóvel real nem prova de prontidão comercial.

## Verificações adicionais efetuadas

- Descarreguei os relatórios XML e confirmei os estados por variante, não apenas o estado global do CI.
- Descarreguei os cinco APKs de demonstração e executei `unzip -t` sobre cada um: todos passaram a verificação de integridade do arquivo ZIP, e cada um contém `AndroidManifest.xml`, `classes.dex` e `resources.arsc`. Não foi possível fazer verificação criptográfica independente das assinaturas APK neste ambiente porque `apksigner` não está instalado.
- Executei uma bateria auxiliar local de 19 verificações Kotlin focadas na lógica da calculadora, no leitor de respostas e no formatador de erros; passaram 19/19. Esta bateria foi auxiliar, não substitui o CI nem os testes do projeto.
- Fiz consultas **só de leitura** ao Supabase: RLS está ativo nas 11 tabelas públicas e nas 2 privadas; o papel `anon` não tem privilégios diretos de leitura/escrita nessas tabelas.
- Consultei os avisos de segurança do Supabase. O auditor continua a assinalar proteção contra palavras-passe comprometidas desativada e 19 funções `SECURITY DEFINER` acessíveis ao papel autenticado; estas funções são RPCs da aplicação e têm de continuar a ser verificadas por operação e testadas com contas de papéis diferentes. As tabelas privadas sem políticas RLS diretas são inacessíveis por privilégios diretos e são usadas pelas funções de servidor.

## Alterações de código abrangidas pelo PR #19

- Limite de 64 caracteres para os valores numéricos da calculadora antes de construir um `BigDecimal`.
- Testes para entradas demasiado longas na calculadora.
- Correção dos pressupostos dos testes Android para não exigir a calculadora em variantes que não a incluem.
- Leitor das respostas HTTP limitado a 2 MiB.
- Mensagens de erro do servidor limitadas a 400 caracteres, em linha única e apenas a partir de campos JSON reconhecidos.
- Redirecionamentos HTTP automáticos e cache desativados na camada de comunicação REST/Auth.

## Auditoria adicional de segurança — 24-10-2026

Consulta read-only aos avisos atuais do Supabase e leitura das definições de funções privilegiadas:

- O projeto Supabase está ativo e saudável.
- O advisor de segurança assinala a proteção contra palavras-passe comprometidas desativada. **Pendente:** ativar nas definições de Auth e validar o fluxo de registo/alteração de palavra-passe.
- O advisor assinala 19 funções `SECURITY DEFINER` executáveis por utilizadores autenticados. Isto não prova, por si só, uma vulnerabilidade: várias funções verificam a sessão e a pertença ao restaurante. É obrigatório testar cada operação com proprietário, funcionário, utilizador de outro restaurante e utilizador sem sessão, e rever as verificações de autorização antes de alterar privilégios.
- As tabelas `private.account_deletion_queue` e `private.restaurant_join_codes` têm RLS ativo sem políticas. O advisor classifica isto como informativo; confirmar que a ausência de políticas é intencional e que o acesso é feito apenas pelas funções privilegiadas autorizadas.
- A inspeção read-only de `create_restaurant`, `create_restaurant_reservation_idempotent`, `get_member_restaurant_menu`, `prepare_account_deletion`, `save_inventory_item`, `adjust_inventory_stock_idempotent` e `update_restaurant_reservation_status` mostrou verificações explícitas de sessão e, nas operações relevantes, pertença ao restaurante. Esta leitura estática não substitui testes de autorização com identidades separadas.
- O advisor de desempenho também assinala índices não utilizados; não foram removidos, pois isso exige dados de carga reais e pode prejudicar consultas que ainda não foram executadas.
- Não foram feitas alterações ao esquema nem aos dados de produção durante esta auditoria.

## Continuação da auditoria — 24-10-2026 (reconciliação de migrações)

- Confirmei novamente a execução CI [40106562821](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/40106562821): os dois jobs terminaram com sucesso. O job de compilação passou a compilação, testes unitários das cinco variantes, Android Lint e validação da configuração comercial; o job de interface no emulador Android 16 também passou. Os relatórios XML desta execução ainda precisam de inspeção para contar casos aprovados/ignorados.
- Comparei o histórico de migrações do projeto Supabase com os ficheiros versionados no repositório: a base de dados regista 37 migrações, mas a branch contém 19 ficheiros SQL em `supabase/migrations/`. As versões mais antigas, incluindo as registadas em 19-10-2026, não estão todas representadas por ficheiros de migração no repositório. **Bloqueio importante:** não executar uma reconstrução limpa nem declarar que o backend é reproduzível até reconciliar o histórico com cuidado e validar numa base de dados de teste.
- Os avisos atuais do Supabase continuam a listar 19 funções `SECURITY DEFINER` executáveis por utilizadores autenticados e duas tabelas do esquema `private` com RLS sem políticas diretas. Estes avisos não provam exploração; exigem validação das permissões pretendidas e testes com identidades distintas. Não fiz alterações à base de dados nesta ronda.
- A proteção contra palavras-passe comprometidas continua desativada. A ativação tem de ser feita na configuração Auth e depois validada com testes de registo/alteração de palavra-passe; não foi alterada nesta ronda.

## Pendências antes de vender/publicar

1. Confirmar o CI de cada alteração futura; não presumir que uma execução anterior valida código posterior.
2. Testes ponta a ponta em Android real: criar conta, confirmar email, iniciar sessão, terminar sessão, recuperação de palavra-passe, OAuth (quando estiver configurado), renovação/expiração de sessão e revogação de acesso.
3. Testes com duas contas e dois restaurantes para provar que pedidos, reservas, stock, carta e pontos não podem ser lidos ou alterados entre restaurantes.
4. Testes de rede interrompida, timeout, respostas repetidas e reenvio das operações idempotentes com contas e dados de teste descartáveis.
5. Ativar e testar a proteção de palavras-passe comprometidas nas definições do Supabase Auth.
6. Configurar SMTP e URLs de retorno permitidos; validar o fluxo de eliminação de conta e os emails.
7. Escolher o identificador definitivo de pacote, guardar a chave comercial em secrets, validar assinatura comercial e testar os AABs finais antes da publicação.
8. Preparar política de privacidade, Data safety da Google Play, processo de backup/restauro e revisão independente de segurança.

**Estado de lançamento:** não aprovado para produção. O PR permanece em rascunho e não altera diretamente `main`.

## Continuação da auditoria — 24-10-2026 (pipeline comercial e configuração Supabase)

- Foram alinhados os nomes de três ficheiros de migração com as versões remotas: `20261021090456_restaurant_inventory_and_reservations`, `20261021090901_restaurant_team_management` e `20261021091127_rotate_team_code_on_staff_removal`. Foi uma alteração apenas de ficheiros no GitHub; não executei DDL nem alterei dados de produção.
- A discrepância histórica continua: 37 versões no histórico remoto e 19 ficheiros SQL locais. As 18 versões antigas de 19-10-2026 não foram recriadas artificialmente. `supabase/schema.sql` é um script de baseline, mas não prova, por si só, que as alterações incrementais estejam reproduzidas. Não declarar reconstrução limpa reproduzível até recuperar/verificar essas migrações e executar a reconstrução numa base descartável.
- Foram versionadas as definições de gateway de `delete-account` e `account-deletion-web` em `supabase/config.toml`, ambas com `verify_jwt = false`. A função `delete-account` valida a sessão no handler com `withSupabase({ auth: "user" })`; a página web é pública e exige autenticação de utilização única antes de chamar o endpoint destrutivo. Foi acrescentado `scripts/check_supabase_function_config.py` e a CI principal executa-o para evitar alterações acidentais a esta configuração.
- O fluxo de release comercial agora valida os delimitadores das migrações e a configuração de gateway, corre os testes unitários e Android Lint, e exige testes de interface Android 16 nas cinco variantes antes de construir/assinar AABs. Os resultados de emulador são publicados como artefacto mesmo se um teste falhar.
- Auditoria read-only adicional ao catálogo PostgreSQL: as funções `SECURITY DEFINER` analisadas usam `search_path = ''`; nenhuma das funções listadas pode ser executada por `anon`. As funções RPC expostas a `authenticated` continuam a necessitar de testes comportamentais com proprietário, funcionário, utilizador de outro restaurante e sessão ausente; os avisos do advisor não são prova isolada de uma exploração.
- A proteção contra palavras-passe comprometidas continua desativada no Supabase Auth; não a alterei nesta auditoria. Também não removi os 21 índices assinalados como não usados, porque a decisão exige dados de carga e risco de regressão.
- Na execução CI `40112220200` sobre o commit de código imediatamente anterior a esta atualização documental, compilação e testes unitários das cinco variantes passaram; as verificações de delimitadores SQL e configuração do Supabase passaram; Android Lint estava em curso quando o registo foi preparado. Essa execução não deve ser considerada validação final. O commit documental desencadeia nova CI; só considerar a branch atual validada quando todos os jobs, incluindo os testes de interface Android 16, concluírem com sucesso.
- O PR `#19` continua em rascunho, separado de `main`; não foi efetuado merge nem publicação comercial.

## Bloqueios de lançamento — atualizados em 24-10-2026

1. Reconciliar as 18 migrações históricas em falta e testar a reconstrução numa base de dados descartável.
2. Concluir a CI relativa ao commit final, incluindo testes de interface nas cinco variantes.
3. Fazer testes ponta a ponta com identidades diferentes e dois restaurantes, incluindo permissões, códigos de acesso, pedidos, reservas, inventário, menu, fidelização e eliminação de conta.
4. Ativar e testar a proteção contra palavras-passe comprometidas no Auth; validar email/SMTP, URLs de retorno e fluxos de conta.
5. Testar em telemóvel Android real rede interrompida, expiração/renovação de sessão, recuperação de palavra-passe, revogação de acesso e eliminação de conta.
6. Escolher o ID de pacote final, configurar/guardar a chave comercial e secrets, testar AABs assinados e concluir política de privacidade, Data safety e revisão pré-publicação.

**Estado:** não aprovado para produção até à resolução e validação das pendências acima.

## Continuação da auditoria — cadeia de dependências CI/CD — 24-10-2026

- Os workflows Android e de release comercial foram atualizados para fixar as GitHub Actions usadas a commits completos, em vez de etiquetas móveis: `actions/checkout` v7.0.1, `actions/setup-java` v6.1.0, `gradle/actions/setup-gradle` v6.4.0, `actions/upload-artifact` v4.6.2 e `ReactiveCircus/android-emulator-runner` v2.38.0. Isto torna a referência à ação determinística; as atualizações futuras devem rever os novos commits e respetivas notas antes de atualizar os pins.
- A execução `40112220200` corresponde ao commit anterior a esta alteração e tinha passado compilação, testes unitários das cinco variantes e Lint, além da validação de delimitadores SQL e da configuração Supabase; a fase de configuração de bundle comercial ainda estava em execução. Não a tratar como CI final, porque não valida estes pins novos.
- A alteração de pins e deste registo requer uma nova CI sobre a nova cabeça da branch. A validação final exige sucesso de ambos os jobs: compilação/testes/Lint/configuração comercial e testes de interface no emulador Android 16 nas cinco variantes.
