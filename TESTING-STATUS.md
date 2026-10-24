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