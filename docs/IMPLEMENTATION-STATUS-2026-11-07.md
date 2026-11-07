# StudioMesa — estado de implementação

**Verificação:** 7 de novembro de 2026.  
**Repositório:** `servulocorreiamiguel-rgb/studiomesa-andoid`, branch principal `main`.  
**Projeto Supabase:** `svwvkfsyevuyabelrtjk`, região `eu-west-1`.

## Estado atual confirmado

| Área | Código Android / repositório | Supabase alojado | Validação pendente |
|---|---|---|---|
| Mesas, carta, pedidos e cozinha | Implementados | Tabelas e políticas presentes | E2E com contas e dispositivos reais |
| Inventário e movimentos | Implementados | Tabelas e movimentos presentes | E2E e conciliação operacional |
| Reservas | Implementadas | Tabela e RPCs presentes | E2E completo com cliente/equipa |
| Fidelização, QR e recompensas | Implementadas | Tabelas e RPCs presentes | E2E, regras comerciais e antifraude |
| IA generativa local | Modelo LFM2.5-1.2B, ferramentas locais de leitura e modo «Pensar mais» | Não depende de API generativa cloud | Desempenho e qualidade no OPPO A5m; revisão da licença comercial |
| Histórico e memória cloud da IA | Sincronização e armazenamento local cifrado no código | Tabelas e RPCs presentes | Testes em dois dispositivos, conflitos, eliminação e isolamento |
| Faturas de fornecedores | OCR local, arquivo e revisão no código | Tabela e bucket privado presentes | E2E com PDFs/imagens anonimizados, duplicados, falhas e permissões |
| Fichas técnicas e consumo automático | Interface, API, migração e trigger no repositório | **Tabelas ausentes** | Reconciliação, deployment aprovado e E2E |
| Permissões granulares de funcionários | Interface, API, migração e testes no repositório | **Tabela/helper/RPCs ausentes** | Reconciliação, deployment aprovado e testes de autorização |

O projeto Supabase foi observado como `ACTIVE_HEALTHY` durante a verificação. Isso descreve o estado do projeto nesse momento; não constitui garantia de disponibilidade futura.

## Confirmação das funções recentes

Uma consulta de catálogo ao PostgreSQL confirmou:
- Histórico/memória do assistente: tabelas existentes.
- Faturas de fornecedores: tabela existente.
- Keepalive silencioso: RPC existente.
- Receitas e linhas de receita: ainda não existem.
- Permissões privadas de funcionários e respetivo helper/RPC: ainda não existem.

O workflow silencioso do GitHub Actions está configurado para chamar o keepalive quatro vezes por dia. A função apenas devolve `true`, não consulta nem altera dados do restaurante e não apresenta mensagens na aplicação. O agendamento não garante que o Supabase nunca seja pausado.

## Próximo trabalho técnico

### 1. Reconciliar as migrações
- Comparar novamente as migrações locais e remotas e recuperar a história completa.
- Fazer um backup verificável e ensaiar o restauro num ambiente separado.
- Reproduzir a sequência canónica de migrações numa base descartável.
- Rever RLS, grants, RPCs, triggers, índices e isolamento entre restaurantes.

### 2. Ativar receitas e permissões
- Só depois da reconciliação, backup/restauro e revisão, preparar o deployment das duas migrações já existentes.
- Aplicar as migrações ao projeto alojado apenas com aprovação explícita para esta alteração de produção.
- Confirmar a existência das tabelas e funções através de consultas read-only.
- Testar a ficha técnica, insuficiência de stock, alteração de unidades, reaprovação, reenvio de pedidos e prevenção de consumo duplicado.
- Testar as seis permissões com proprietário, funcionário autorizado, funcionário sem acesso e conta de outro restaurante; tentar também chamadas diretas às RPC/REST para confirmar que o servidor bloqueia acessos indevidos.

**Não criar tabelas duplicadas:** as migrações completas já estão em `supabase/migrations/`.

### 3. Segurança do backend
Na verificação dos Advisors:
- Foram assinaladas 31 funções `SECURITY DEFINER` acessíveis à função `authenticated`. Cada RPC deve ser revista quanto a `auth.uid()`, validação de pertença ao restaurante, `search_path` e privilégios. Não revogar permissões em bloco sem testes.
- A proteção de palavras-passe comprometidas do Supabase Auth estava desativada.
- Foram assinaladas oito tabelas com RLS ativo e sem políticas explícitas; é necessário confirmar que o acesso é intencionalmente fechado e coberto por RPCs controladas.
- Foram assinaladas duas chaves estrangeiras sem índice de cobertura em tabelas privadas do consultor de preços.

Referências:
- [Security Advisor — políticas RLS](https://supabase.com/docs/guides/database/database-linter?lint=0008_rls_enabled_no_policy)
- [Security Advisor — funções SECURITY DEFINER](https://supabase.com/docs/guides/database/database-linter?lint=0029_authenticated_security_definer_function_executable)
- [Proteção contra palavras-passe comprometidas](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection)

## Outros bloqueios antes de piloto/venda

- Testes ponta a ponta com contas descartáveis: proprietário, funcionário, cliente e dois restaurantes.
- Validar confirmação de email, recuperação de palavra-passe, SMTP e OAuth antes de anunciar esses métodos como funcionais.
- Configurar e testar backup/restauro, retenção, suporte e resposta a incidentes.
- Testar OCR e arquivo de faturas com documentos anonimizados.
- Medir instalação do modelo, RAM, velocidade, temperatura, bateria e estabilidade no OPPO A5m.
- Confirmar elegibilidade/licença comercial do modelo LFM e licenças das dependências.
- Aprovar a entidade legal, contratos, DPA, política de privacidade e Data Safety.
- Definir package IDs, keystore e assinatura comercial.
- Implementar direitos de plano/entitlements verificados no servidor. As cinco variantes de build existentes não são, isoladamente, um sistema de licenciamento.

## Decisão de estado

**Demonstração:** pode usar builds debug com dados fictícios e avisos de versão de demonstração.

**Piloto com dados reais:** só depois de concluir a reconciliação, segurança, deployment aprovado, E2E, backups/restauro e documentação de privacidade.

**Venda/publicação:** só depois de concluir também a revisão legal, licenciamento, assinatura comercial, modelo de planos no servidor e validação final da release.

Este documento não autoriza alterações ao Supabase de produção nem substitui um plano de deployment e reversão.
