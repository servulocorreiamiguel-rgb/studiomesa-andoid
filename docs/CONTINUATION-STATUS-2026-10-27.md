# StudioMesa — estado de continuação e lançamento

Data da última revisão: 27 de outubro de 2026.

Este documento serve para retomar o trabalho sem repetir auditorias nem tratar uma versão de demonstração como produto comercial validado.

## Última versão validada automaticamente

- Commit de código testado: `f4568bce` — correção da calculadora para limpar resultados antigos quando os campos mudam.
- Execução CI: https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/40533990774
- Resultado da execução: sucesso.
- Compilação das cinco variantes de demonstração: sucesso.
- Testes unitários das cinco variantes: sucesso.
- Android Lint: sucesso.
- Validação da configuração Gradle de release com chave temporária de CI: sucesso. Isto **não** é uma assinatura comercial do proprietário.
- Reexecução das migrações PostgreSQL em base vazia e testes de autorização entre restaurantes/idempotência: sucesso.
- Testes de interface em emuladores Android 8 (API 26) e Android 16 (API 36): sucesso.
- Cada família de testes de interface produziu cinco relatórios XML, um por variante; zero falhas e zero erros. Os testes condicionados ao plano/tipo de aplicação foram ignorados quando não se aplicavam.

O artefacto `StudioMesa-1.5.0-Demo-APKs` é composto por cinco APKs debug. São úteis para demonstração e testes, não são builds comerciais assinados nem certificam operação em produção.

## Correções recentes da calculadora

1. Limita custo e preço sugerido ao máximo monetário aceite pela carta (`numeric(10,2)`: 99 999 999,99 €).
2. Rejeita percentagens-alvo fora do intervalo suportado (0,01 a 100).
3. Aplica o limite do texto original antes de o normalizar, incluindo espaços, para não aceitar entradas excessivas.
4. Limpa o resultado anterior e as mensagens de erro quando o utilizador altera qualquer campo; o preço antigo não deve parecer atual.
5. Foram acrescentados testes de regressão para os limites monetários e a entrada demasiado longa. O teste de interface para resultado desatualizado já existe em `MainActivitySmokeTest.kt`.

Os commits destas alterações estão no histórico de `main`; a execução CI acima testou a revisão final de código.

## Auditoria read-only do Supabase

Verificação em 27 de outubro de 2026, sem modificar dados ou permissões de produção:

- Projeto com estado `ACTIVE_HEALTHY`; as tabelas listadas não tinham registos.
- As 19 funções `SECURITY DEFINER` executáveis por `authenticated` que foram contabilizadas no projeto tinham verificações de identidade através de `auth.uid()`; nenhuma das funções `SECURITY DEFINER` listadas era executável pelo papel `anon`.
- As funções operacionais verificadas incluem controlos por restaurante/equipa ou por proprietário/membro, conforme o caso. O alerta geral do linter não prova, por si só, uma vulnerabilidade; manter a revisão do corpo de cada RPC e os testes de autorização no CI.
- As tabelas do esquema `private` têm RLS ativa e não concedem SELECT/INSERT direto a `anon` ou `authenticated`. O aviso “RLS enabled, no policy” nestas tabelas privadas deve ser reavaliado se a forma de exposição da API mudar; não adicionar políticas abertas para fazer desaparecer o aviso.
- O linter assinala 18 índices não utilizados. Como a base tinha zero registos e pouco histórico de utilização, não remover índices necessários para futuras consultas sem dados reais de desempenho.
- A proteção Supabase contra palavras-passe comprometidas estava desativada. A documentação oficial indica que a opção está disponível no plano Pro ou superior: https://supabase.com/docs/guides/auth/password-security. O projeto está no plano gratuito. Decidir se se mantém o plano gratuito (e rever os requisitos de palavra-passe disponíveis) ou se se atualiza o plano; não guardar credenciais nem contornar esta restrição.

## Bloqueios para lançamento comercial

A aplicação **não está ainda validada para operação comercial real**. Antes de vender como solução pronta:

1. Testar em dispositivos físicos, com contas descartáveis e vários utilizadores, incluindo perda de rede, sessões expiradas, permissões, repetição de operações e isolamento de restaurantes.
2. Completar e registar os testes de ponta a ponta em Supabase para registo, confirmação de email, login, recuperação, logout e eliminação de conta. A função externa de eliminação ainda precisa do URL de retorno autorizado e envio de email funcional.
3. Configurar SMTP próprio para emails reais; verificar as URLs de retorno OAuth e configurar credenciais próprias para Google/Microsoft apenas se esses métodos forem disponibilizados.
4. Escolher identificadores de pacote finais e criar uma chave de assinatura comercial em ambiente privado. Guardar chave e palavras-passe em local seguro/Secrets; não colocá-las no repositório ou numa conversa. O workflow comercial precisa dos quatro secrets de assinatura e da variável `STUDIOMESA_RELEASE_APPLICATION_ID`.
5. Publicar e validar a política de privacidade, a declaração Google Play Data safety, retenção/exportação/eliminação de dados, cópias de segurança e procedimento de restauro.
6. Testar reservas, inventário, fidelização, remoção de funcionários e eliminação de conta de ponta a ponta. A fidelização continua a precisar de regras de recompensas/resgate e proteção contra fraude; integrações POS, pagamentos, impressoras e faturação não estão implementadas.
7. Fazer revisão independente de autorização/RLS e ensaios com dados de teste antes de processar dados de qualquer restaurante real.
8. Só depois preparar os AABs assinados e a ficha Google Play. O workflow de release cria artefactos mas não publica automaticamente na loja.

## Próximo passo

Continuar com a revisão da configuração de lançamento e dos fluxos de autenticação/eliminação de conta, sem usar dados reais nem enfraquecer permissões para eliminar alertas. Quando uma alteração de código for feita, repetir CI e registar o commit e o resultado; não afirmar que passou um teste até existir um resultado observável.
