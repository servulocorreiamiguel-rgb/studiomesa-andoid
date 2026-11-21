# StudioMesa — sessão no computador
**Data planeada:** sábado, 21/11/2026, a partir das 09:00 (hora de Portugal)  
**Branch de trabalho:** `work/mobile-completion-2026-11-20`  
**PR de integração:** [#135](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/pull/135)  
**Commit validado:** `4b9242e51d3d914f24dbeafdbd2a750efb31effd`

## Objectivo da sessão
Preparar a build validada para ensaio físico, confirmar o ambiente Windows, recolher resultados do OPPO A5m e avançar nos bloqueios de lançamento sem alterar a produção de forma arriscada.

## 1. Confirmar o ambiente Windows

Abrir PowerShell e executar:

```powershell
git --version
java -version
docker --version
adb version
```

Na última verificação registada, Git funcionava; Java e Docker não eram reconhecidos pelo PowerShell. Voltar a confirmar porque o ambiente pode ter mudado. Docker só é necessário para executar serviços locais/ensaios Supabase; não é necessário para descarregar APKs nem acompanhar GitHub Actions.

O portátil tem 4 GB de RAM. Preferir GitHub Actions para compilar e executar emuladores; evitar executar Android Emulator localmente. Não instalar várias ferramentas pesadas ao mesmo tempo nem apagar ficheiros para libertar espaço sem verificar primeiro o que contêm.

## 2. Confirmar o código e preservar a branch

Se o repositório já estiver clonado:

```powershell
cd CAMINHO\studiomesa-andoid
git status --short
git fetch origin
git switch work/mobile-completion-2026-11-20
git pull --ff-only
git rev-parse HEAD
```

O último comando deve devolver o commit validado acima, excepto se houver uma revisão mais recente. Confirmar sempre o SHA e o estado da CI da revisão exacta antes de usar qualquer APK. Nesta revisão, os quatro jobs da CI `44815698449` terminaram com sucesso.

Se o repositório ainda não existir no computador:

```powershell
git clone https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid.git
cd studiomesa-andoid
git fetch origin
git switch --track origin/work/mobile-completion-2026-11-20
git rev-parse HEAD
```

Não fazer commits directamente em `main`. Não forçar pushes, resets nem reescrever o histórico. Não integrar a PR #92 em bloco.

## 3. Ensaiar os APKs mais recentes no OPPO A5m

Usar o artefacto GitHub Actions `StudioMesa-1.5.0-ARM64-Preview-APKs`, ID `14669494611`, gerado pela CI `44815698449`. Para o primeiro ensaio, usar Gestão Premium e Cliente Premium do mesmo ZIP. O roteiro de aceitação está em [OPPO-A5M-TEST-CURRENT-1.5.0.md](OPPO-A5M-TEST-CURRENT-1.5.0.md).

Não usar dados, contas, reservas, clientes ou faturas reais. Não desinstalar uma versão antiga para contornar conflitos sem perceber primeiro se existem dados locais de teste. Registar o texto exacto de qualquer erro.

Os fluxos gratuitos, reservas, sincronização Gestão/Cliente e isolamento entre restaurantes já foram confirmados anteriormente pelo proprietário no OPPO A5m; não os marcar como pendentes sem motivo. Concentrar o ensaio na build nova, navegação e toque nas notificações.

## 4. Rever autenticação sem expor credenciais

Usar apenas contas descartáveis de teste. Confirmar registo, confirmação de email, login, recuperação de palavra-passe, refresh, logout e eliminação. Não colocar passwords, códigos, tokens, chaves API ou ficheiros de assinatura em conversas, issues, commits ou ficheiros do repositório.

Google/Microsoft OAuth continua adiado por decisão do proprietário até depois da primeira venda; não activar nesta sessão.

## 5. Supabase: primeiro backup e restauro, sem deployment

Ler integralmente [PRODUCTION-BACKUP-AND-MIGRATION-RUNBOOK.md](PRODUCTION-BACKUP-AND-MIGRATION-RUNBOOK.md).

O Supabase alojado tem 63 migrações aplicadas e a branch actual tem nove pendentes. O plano Free não fornece backups automáticos diários nem PITR. Um dump PostgreSQL isolado não inclui automaticamente utilizadores Auth nem objectos Storage.

Ordem obrigatória:
1. Exportar schema, dados e roles para uma pasta privada fora do repositório.
2. Confirmar os ficheiros e SHA-256.
3. Preparar exportação/restauro separado de Auth e Storage.
4. Restaurar tudo numa instância descartável e validar tabelas, utilizadores, ficheiros, RLS, RPCs e isolamento entre dois restaurantes de teste.
5. Só depois executar `supabase migration list --remote` e `supabase db push --dry-run --remote`, rever o resultado e parar para aprovação humana.

**Não executar `supabase db push`, `db reset --remote`, migration repair ou qualquer alteração de produção nesta sessão sem backup/restauro comprovados, revisão e autorização explícita.** Não criar uma branch Supabase paga nem activar serviços pagos sem decisão de custo.

## 6. Preparar a release comercial — só depois dos bloqueios

A CI normal usa assinatura debug. A CI de validação usou uma chave temporária; nenhuma delas é a keystore comercial do proprietário.

Antes de gerar uma release:
- definir os IDs definitivos das aplicações;
- criar a keystore comercial num computador privado, com cópias de segurança seguras;
- configurar os quatro secrets de assinatura e a variável de Application ID no GitHub Actions;
- aprovar política de privacidade, termos, licenças, Data Safety e titularidade da publicação;
- preencher as variáveis de prontidão apenas depois de cada requisito estar realmente cumprido;
- executar o workflow comercial e verificar os AAB/APK e checksums.

Não partilhar a keystore ou palavras-passe com o assistente. A conta de publicação, contratos e aprovação legal devem ficar a cargo de um adulto/responsável legal, uma vez que o proprietário do projecto é menor.

## Critério de conclusão

A StudioMesa só pode ser marcada como pronta para venda operacional quando houver: CI verde sobre a revisão final; teste físico aprovado; Auth/SMTP E2E aprovado; backup e restauro demonstrados; migrações reconciliadas e deployment controlado; isolamento e permissões validados no backend real; keystore comercial e identificadores finais; privacidade, termos e licenças aprovados. Até lá, a build actual é de pré-visualização/teste.
