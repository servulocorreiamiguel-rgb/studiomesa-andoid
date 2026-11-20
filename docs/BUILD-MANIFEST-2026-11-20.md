# Manifesto da build StudioMesa 1.5.0 — 20/11/2026

## Identificação

- Commit do código Android validado: `b2c17ee85a31a0b8f00d6c22342b10176021af20`
- Workflow: [44809660302](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/44809660302)
- Resultado: quatro jobs aprovados — compilação/testes unitários, auditorias/Lint, replay PostgreSQL/autorização, Android 8 e Android 16.
- Branch de integração: `work/mobile-completion-2026-11-20`
- PR: [#135](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/pull/135), continua em rascunho.

## Artefacto ARM64

- Nome: `StudioMesa-1.5.0-ARM64-Preview-APKs`
- ID: `14668750452`
- Tamanho do ZIP na CI: 119 078 499 bytes
- SHA-256 do ZIP: `5d8fcbe59ff78867f2b8283a27a4bab9364d30d5316b19e8f2288d6711646516`
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
