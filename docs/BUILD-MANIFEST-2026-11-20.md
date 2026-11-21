# Manifesto da build StudioMesa 1.5.0 — 20/11/2026

## Identificação

- Commit do código Android validado: `4b9242e51d3d914f24dbeafdbd2a750efb31effd`
- Workflow: [44815698449](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/44815698449)
- Resultado: quatro jobs aprovados — compilação/testes unitários, auditorias/Lint, replay PostgreSQL/autorização, Android 8 e Android 16.
- Branch de integração: `work/mobile-completion-2026-11-20`
- PR: [#135](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/pull/135), continua em rascunho.

## Artefacto ARM64

- Nome: `StudioMesa-1.5.0-ARM64-Preview-APKs`
- ID: `14669494611`
- Tamanho do ZIP na CI: 119 078 541 bytes
- SHA-256 do ZIP: `ce797eb3553da23c5e3a642b4a031d440d8a0db1126c883c2354d78911656e06`
- Cinco APKs, cerca de 42,6 MB cada, com apenas bibliotecas nativas `arm64-v8a`.

## Conteúdo

| Variante | Caminho no artefacto | Tamanho |
|---|---|---:|
| Gestão Essencial | `essencialRestaurante/debug/app-essencial-restaurante-debug.apk` | 42 628 535 bytes |
| Gestão Profissional | `profissionalRestaurante/debug/app-profissional-restaurante-debug.apk` | 42 628 531 bytes |
| Cliente Profissional | `profissionalCliente/debug/app-profissional-cliente-debug.apk` | 42 628 535 bytes |
| Gestão Premium | `premiumRestaurante/debug/app-premium-restaurante-debug.apk` | 42 628 543 bytes |
| Cliente Premium | `premiumCliente/debug/app-premium-cliente-debug.apk` | 42 628 535 bytes |

## Utilização

Para o primeiro ensaio no OPPO A5m, usar Gestão Premium e Cliente Premium do mesmo artefacto. O procedimento e os critérios estão em [OPPO-A5M-TEST-CURRENT-1.5.0.md](OPPO-A5M-TEST-CURRENT-1.5.0.md).

## Limites

Estes APKs são **debug/preview**, assinados com a chave de depuração persistente da CI. Não são comerciais, não devem ser usados com dados reais e não substituem a keystore comercial. A CI de configuração de release usou uma chave temporária, eliminada no fim do job.

A aprovação automática não cobre o OPPO físico, SMTP/Auth E2E, backup/restauro do Supabase alojado, aplicação das nove migrações pendentes, privacidade/legal ou o envio de notificações com a app fechada.

## APKs Premium extraídos e verificados

- Gestão Premium — 42 628 543 bytes; SHA-256 `2428ca337c248cb303752f042daabe9cc147f6cf96f19f18d01ce00735fb36a9`.
- Cliente Premium — 42 628 535 bytes; SHA-256 `814ec0ef20fd5ead8e68990040b56ddab6fe8bb077750c7299be078e81d178be`.
- ZIP de instalação dos dois APKs e instruções — SHA-256 `2dd216e7afd5f5cc5429a70dbe2ca9c7a7dda84930009c51ff9be46f6b6302a7`.
- O ZIP foi reaberto e a integridade foi verificada; os APKs correspondem aos membros do artefacto GitHub Actions acima.
