# StudioMesa — cópia de segurança manual do Supabase

**Estado em 30/10/2026:** procedimento documentado, ainda não executado. Não existe neste momento uma cópia exportada nem um restauro testado.

## Porque é necessário

A organização Supabase ligada ao StudioMesa está no plano Free. Esse plano não inclui cópias automáticas diárias; a documentação oficial recomenda exportações regulares com a Supabase CLI e cópias externas. Os planos Pro, Team e Enterprise incluem backups diários, com retenções diferentes. Este documento não altera o plano nem autoriza custos.

Fontes oficiais:
- [Backups da base de dados Supabase](https://supabase.com/docs/guides/platform/backups)
- [Backup e restauro com a CLI](https://supabase.com/docs/guides/platform/migrating-within-supabase/backup-restore)
- [Comando supabase db dump](https://supabase.com/docs/reference/cli/supabase-db-dump)

## Requisitos

- Um computador com Supabase CLI e Docker Desktop instalados.
- A connection string da base de dados, obtida no Dashboard do projecto através de **Connect** (ligação directa ou Session Pooler recomendado pelo painel).
- Uma pasta local fora do repositório GitHub para guardar os ficheiros.
- A palavra-passe da base de dados, guardada num gestor de palavras-passe.

**Nunca enviar a connection string, palavra-passe, ficheiros de backup ou dados de clientes para esta conversa ou para o repositório GitHub.** Executar a exportação apenas num computador privado e não partilhar o terminal durante o processo.

## Exportar a base de dados

Exemplo para Windows PowerShell. Substituir a origem da connection string por uma variável de ambiente local, configurada no próprio computador e nunca guardada no repositório:

```powershell
$ErrorActionPreference = 'Stop'
$backupDir = Join-Path $env:USERPROFILE ('StudioMesa-Backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $backupDir | Out-Null
Set-Location $backupDir

if ([string]::IsNullOrWhiteSpace($env:STUDIOMESA_DB_URL)) {
    throw 'Define STUDIOMESA_DB_URL localmente antes de continuar.'
}

supabase db dump --db-url "$env:STUDIOMESA_DB_URL" -f roles.sql --role-only
if ($LASTEXITCODE -ne 0) { throw 'A exportação de roles falhou.' }

supabase db dump --db-url "$env:STUDIOMESA_DB_URL" -f schema.sql
if ($LASTEXITCODE -ne 0) { throw 'A exportação do esquema falhou.' }

supabase db dump --db-url "$env:STUDIOMESA_DB_URL" -f data.sql --use-copy --data-only
if ($LASTEXITCODE -ne 0) { throw 'A exportação dos dados falhou.' }

$files = @('roles.sql', 'schema.sql', 'data.sql')
foreach ($file in $files) {
    if (-not (Test-Path $file) -or (Get-Item $file).Length -eq 0) {
        throw "Ficheiro de backup ausente ou vazio: $file"
    }
}
Get-FileHash -Algorithm SHA256 $files | Format-Table Path, Hash
```

A CLI usa `pg_dump` com filtros próprios do Supabase. Os três comandos exportam roles, esquema e dados; a documentação Supabase indica que os dados incluem `auth.users`. Não substituir este fluxo por `pg_dump` directo sem validar diferenças e permissões.

## Proteger e guardar a cópia

1. Cifrar os três ficheiros antes de os mover (por exemplo, arquivo 7-Zip com AES-256 ou BitLocker).
2. Guardar a palavra-passe num gestor de palavras-passe, separada do arquivo.
3. Manter pelo menos duas cópias cifradas em locais distintos, uma delas fora do computador de trabalho.
4. Nunca colocar backups em commits, GitHub Actions artifacts, mensagens, email sem cifragem ou pastas partilhadas sem controlo de acesso.
5. Guardar também a data, versão do PostgreSQL, versão da CLI e os SHA-256 dos ficheiros.

## O que não fica incluído

- Os objectos armazenados através da Supabase Storage API. A base de dados guarda metadados, mas os ficheiros têm de ser copiados separadamente.
- Configuração externa de fornecedores OAuth, SMTP, URLs de retorno e segredos runtime das Edge Functions. O código das funções está no repositório, mas os segredos do projecto não estão.

## Teste de reposição obrigatório

Não testar a reposição na base de dados de produção. Usar uma instância local/descartável compatível com PostgreSQL 17 ou outro projecto isolado preparado para o efeito. Seguir o procedimento oficial de [backup e restauro com a CLI](https://supabase.com/docs/guides/platform/migrating-within-supabase/backup-restore).

Depois do restauro, confirmar pelo menos:
- As tabelas essenciais existem e os totais de registos correspondem à origem.
- Os utilizadores Auth estão presentes.
- RLS, políticas, funções, triggers, índices e extensões essenciais estão presentes.
- A aplicação consegue autenticar uma conta de teste e consultar os dados de um restaurante de teste.
- Uma conta de outro restaurante não consegue ler nem alterar esses dados.

**Só marcar o backup como aprovado depois de os três ficheiros existirem, estarem cifrados e um restauro isolado ter terminado com as verificações acima.**
