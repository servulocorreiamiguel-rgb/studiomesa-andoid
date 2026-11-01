# StudioMesa — estado de continuação

**Data:** 1 de novembro de 2026  
**Branch de desenvolvimento:** `feat/order-menu-snapshots-2026-10-30`  
**Pull request:** [#72 — receitas, gestão económica e reservas de cliente](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/pull/72)

## Estado da branch

- A PR #72 continua aberta, em rascunho, e não foi integrada em `main`.
- A branch reúne as alterações de receitas/consumo de stock, identidade estável dos artigos e snapshots económicos dos pedidos, reservas iniciadas pela aplicação de cliente e melhorias de gestão.
- Não foram aplicadas migrações novas ao Supabase alojado nesta fase.
- Não foram usados dados reais de restaurantes.

## Trabalho feito nesta fase

1. Corrigida a expectativa do teste `scenarioSimulatorComparesBaselineWithExplicitPercentageChanges`: o ponto de equilíbrio esperado é **7 570,09 €**, não 7 570,59 €. No cenário: vendas de 9 000 €, custo alimentar de 3 150 €, custos variáveis de 500 € e custos tratados como fixos de 4 500 €. A taxa de contribuição é 5 350 / 9 000; o ponto de equilíbrio é 4 500 / (5 350 / 9 000).
2. O cabeçalho da aplicação de gestão passou a distinguir:
   - sincronização em curso;
   - última sincronização concluída, com hora;
   - falha ao atualizar, indicando que a aplicação está a usar os últimos dados recebidos;
   - falha antes de qualquer sincronização confirmada.
3. A atualização manual das mesas, carta e pedidos mostra o estado no próprio ecrã, em vez de depender exclusivamente de uma janela modal.
4. As mensagens de estado foram extraídas para `RemoteOperationsSyncStatus.kt` e foram acrescentados cinco testes unitários para os diferentes estados.

## Validação — não confundir com aprovação final

A execução CI #943, anterior a estas correções, confirmou:
- compilação das variantes Android: passou;
- auditorias estáticas e verificações de segurança incluídas antes dos testes: passaram;
- reprodução das migrações da branch numa base PostgreSQL isolada e testes de autorização/idempotência: passaram;
- testes unitários: falhou um teste do simulador económico por causa do valor esperado incorreto;
- Lint, compilação comercial de CI e testes de interface emuladores Android 8/16: foram ignorados por a tarefa de testes unitários ter falhado.

**Não foi executada uma nova CI depois das alterações desta fase.** A correção do teste e os novos testes de estado de sincronização ainda não estão validados por uma execução observável. Não afirmar que a branch está aprovada.

## Bloqueios que continuam a impedir a venda como produto pronto para operação real

- A branch candidata tem 47 migrações; o projeto Supabase alojado tinha 41 migrações aplicadas na última verificação documentada. As seis migrações novas não foram aplicadas em produção.
- Antes de qualquer migração live são obrigatórios backup manual cifrado, ensaio de restauro, plano de reversão, aplicação controlada e verificações posteriores.
- Faltam os testes finais ponta a ponta com contas descartáveis e dois dispositivos físicos, incluindo autenticação, reservas, pedidos, stock, concorrência, rede interrompida/restaurada e isolamento entre restaurantes.
- SMTP próprio, confirmação/recuperação por email e URLs de retorno de autenticação ainda precisam de configuração e validação.
- Faltam política de privacidade, declaração Google Play Data safety, retenção/exportação/eliminação de dados, cópias de segurança e procedimento de restauro validados.
- Falta a chave definitiva de assinatura, identificadores finais da aplicação e AAB/APK comercial assinado.
- Não anunciar integrações POS, pagamentos, impressoras ou faturação; não estão implementadas.

## Próximos passos

1. Continuar a revisão de funcionalidades e experiência, sem ampliar o âmbito com funcionalidades que não aumentem o valor para o primeiro restaurante.
2. Rever feedback de gravação/falha/repetição nos fluxos de pedidos, reservas, carta e stock.
3. Fechar os fluxos de autenticação e a experiência inicial do restaurante.
4. Quando o desenvolvimento estiver fechado, executar uma única ronda final completa de testes, corrigir todas as falhas e repetir os testes afetados.
5. Só depois rever a PR, planear com segurança as migrações e preparar uma demonstração comercial. A PR deve continuar em rascunho até essa validação.

**Objetivo comercial:** conseguir o primeiro restaurante interessado e pagante, sem prometer funcionalidades ou prontidão operacional que ainda não tenham sido demonstradas.
