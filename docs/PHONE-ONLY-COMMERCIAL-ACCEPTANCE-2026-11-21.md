# StudioMesa — aceitação comercial a partir do telemóvel

**Data:** 21/11/2026  
**Build de teste:** branch `work/mobile-completion-2026-11-20`, commit `97c8d1653005040cd6a16055b314e310095da7c8`  
**PR de código:** [#135](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/pull/135)  
**CI exacta:** [44886406846](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/44886406846) — quatro jobs concluídos com sucesso.  
**Artefacto:** `StudioMesa-1.5.0-ARM64-Preview-APKs`, ID `14700641398`.  
**Objectivo:** permitir que o proprietário faça a aceitação física da nova build apenas com o OPPO A5m.

> Esta é uma build de pré-visualização/debug. Não é uma release comercial, não deve ser distribuída a restaurantes e não deve ser usada com dados reais de clientes.

## 1. Descarregar no OPPO A5m

1. Abre a [execução CI 44886406846](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/44886406846) no navegador do telemóvel e inicia sessão no GitHub, se for pedido.
2. Na secção **Artifacts**, escolhe `StudioMesa-1.5.0-ARM64-Preview-APKs` (ID `14700641398`).
3. Descarrega o ZIP e extrai-o com a aplicação Ficheiros do Android.
4. Usa apenas os dois APKs da mesma execução:
   - Gestão Premium: `premiumRestaurante/debug/app-premium-restaurante-debug.apk`
   - Cliente Premium: `premiumCliente/debug/app-premium-cliente-debug.apk`

Não mistures APKs de execuções diferentes. O ZIP do artefacto contém seis variantes ARM64; não é necessário instalar as seis.

## 2. Instalação segura

- Instala primeiro a Gestão Premium e depois o Cliente Premium.
- O Android pode pedir autorização para instalar aplicações provenientes do navegador ou da aplicação Ficheiros. Autoriza apenas a aplicação que estás efectivamente a usar para abrir o APK e volta a desactivar essa autorização depois, se já não for necessária.
- Não desinstales versões anteriores para resolver um conflito sem confirmar primeiro se há dados de teste locais que pretendes conservar.
- Se surgir um erro de assinatura, instalação ou compatibilidade, pára e regista a mensagem exacta. Não apagues dados nem contornes o erro à pressa.

## 3. Testes físicos desta revisão

Usa apenas dados fictícios ou o modo convidado. Não introduzas nomes, contactos, faturas, reservas ou dados de clientes reais.

### Cliente
- [ ] A instalação termina sem erro.
- [ ] A aplicação abre e não fecha inesperadamente.
- [ ] O ecrã inicial apresenta as opções de convidado, criação de conta e acesso a conta existente.
- [ ] O modo convidado abre sem exigir uma conta.
- [ ] Os separadores Cartão, Avisos, Carta, Pontos e Conta abrem e permitem regressar.
- [ ] Os botões Google e Microsoft/Outlook não aparecem, conforme a decisão de os adiar até depois da primeira venda.

### Gestão
- [ ] A instalação termina sem erro.
- [ ] A aplicação abre no painel de gestão.
- [ ] Os ecrãs acessíveis abrem e permitem regressar ao painel.
- [ ] A permissão Android de notificações só é pedida no contexto previsto pela aplicação.
- [ ] Recusar a permissão não impede a utilização normal dos restantes ecrãs.

### Notificações e destinos
- [ ] Quando existir um aviso de teste, tocar nele abre a secção correspondente.
- [ ] Na Gestão, um utilizador sem permissão não consegue abrir uma página que não esteja autorizada para a sua sessão.
- [ ] No Cliente, um destino desconhecido não abre uma secção arbitrária.

A nova revisão inclui testes automáticos para as políticas de destino e testes de interface para Avisos no Cliente e Cozinha na Gestão. Se não surgir nenhum aviso de teste, assinala-o como **não testado**, não como falha.

## 4. O que não deves testar nesta build

- Não faças registo com o teu email principal nem uses contas reais de restaurantes.
- Não testes adesão multi-restaurante, campanhas ou novos cartões com contas reais: a migração necessária ainda não foi aplicada ao Supabase alojado.
- Não assumas que as notificações chegam com a aplicação fechada. A implementação actual consulta alterações em primeiro plano; ainda não existe FCM configurado nesta revisão.
- Não assumas que a IA cloud, as receitas/consumo automático de stock, os limites de planos ou as permissões granulares estão activos no backend alojado.
- Não faças alterações ao Supabase de produção.

Os testes anteriormente confirmados pelo proprietário no OPPO A5m — funcionalidades gratuitas, reservas, sincronização Gestão/Cliente e isolamento entre restaurantes — não precisam de ser repetidos por rotina. Este ensaio serve para validar a nova build e os percursos alterados.

## 5. Como devolver os resultados

Envia apenas um resumo simples, por exemplo:

```text
Gestão Premium: instalou / não instalou
Cliente Premium: instalou / não instalou
Cliente abre: passou / falhou
Modo convidado: passou / falhou
Separadores: passou / falhou
Gestão abre: passou / falhou
Navegação: passou / falhou
Notificações: passou / falhou / não testado
Erros exactos:
```

Não envies passwords, códigos OTP, tokens, chaves API, keystore, dados pessoais nem capturas com informação de clientes. Com os resultados consigo corrigir o código numa branch separada, abrir uma PR e voltar a executar a CI.

## 6. O que pode ser feito só com o telemóvel e o que ainda exige passos externos

| Trabalho | Só com o telemóvel? |
|---|---|
| Descarregar e instalar APKs de teste | Sim |
| Executar testes físicos e comunicar erros | Sim |
| Acompanhar CI, branches e PRs | Sim |
| Rever código e corrigir problemas no GitHub através do assistente | Sim, quando as ferramentas GitHub estão ligadas |
| Configurar SMTP/URLs Auth no dashboard | Pode ser feito no navegador, com acesso do proprietário e contas descartáveis |
| Testar email/recuperação com contas descartáveis | Sim, desde que o SMTP e o ambiente de teste estejam configurados |
| Backup/restauro completo de PostgreSQL, Auth e Storage | Requer procedimento técnico num ambiente isolado; não deve ser improvisado no telemóvel |
| Gerar, guardar e proteger a keystore comercial | Deve ficar sob controlo do proprietário/responsável, fora do chat; um computador de confiança é a opção mais segura |
| Aprovação de privacidade, contratos e publicação por um menor | Exige participação do responsável legal |

## 7. Bloqueios que continuam a impedir declarar a versão comercial pronta

1. Backup recuperável de PostgreSQL, Auth e Storage e prova de restauro numa instância isolada.
2. Reconciliação e revisão das nove migrações que faltam no Supabase alojado na branch #135. Não executar `db push`, reset ou deployment sem backup/restauro, revisão e autorização explícita.
3. Testes E2E de registo, confirmação de email, login, recuperação, renovação e logout com SMTP real e contas descartáveis.
4. Validação física desta build no OPPO A5m e testes E2E adicionais no backend de ensaio.
5. IDs definitivos, keystore comercial privada e secrets de assinatura guardados directamente no GitHub; nunca os partilhar no chat.
6. Política de privacidade pública, Data Safety, termos, retenção/eliminação de dados e licenças revistos pelo responsável legal.
7. Decisão explícita sobre notificações push e serviços externos de IA/pesquisa. Não anunciar como activas funcionalidades que dependam de configuração ainda ausente.

**Regra:** os controlos de prontidão do workflow comercial só podem ser marcados como `YES` depois de cada prova real estar concluída. A CI verde, por si só, não liberta a aplicação para venda operacional.
