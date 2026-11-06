# StudioMesa — design de preparação para lançamento comercial

**Data:** 6 de novembro de 2026
**Âmbito:** cinco variantes Android (Essencial/Profissional/Premium × Restaurante/Cliente).

## Objetivo

Preparar o repositório para uma futura venda legal e tecnicamente responsável, tornando verificável o que está implementado, o que depende do backend alojado e o que depende do proprietário da aplicação. Não declarar o produto pronto para produção enquanto faltarem requisitos externos ou testes reais.

## Utilizadores e sucesso

O produto destina-se a restaurantes e aos respetivos clientes. O sucesso desta fase é um pacote de release reproduzível, documentação coerente com o código, matriz de dados/privacidade, avisos de terceiros e uma lista fechada de bloqueios com provas e responsáveis.

## Estado de partida confirmado

- Android Kotlin, minSdk 24, targetSdk 36, cinco variantes; workflow manual já preparado para cinco AABs/APKs assinados.
- A CI em curso da PR #86 já passou replay PostgreSQL/autorização, compilação das variantes, unit tests e Android Lint; smoke tests Android 8/16 ainda em curso.
- O Supabase alojado não tem as tabelas de sync do assistente nem o arquivo de faturas, nem o bucket privado. Também não tem a matriz granular de permissões e tabelas de receitas. O projeto está num histórico de migrações divergente: 53 ficheiros locais, 54 versões remotas, 48 versões comuns, cinco só locais e seis só remotas.
- A proteção de palavras-passe comprometidas está desativada. A organização Supabase está no plano Free e a opção pode exigir atualização paga.
- A política de privacidade é um rascunho antigo e contradiz a implementação atual (IA local, sync do histórico/memória, OCR e arquivo de faturas).
- A conta/chave de assinatura, identificadores comerciais, política pública, formulário Data safety, contratos, SMTP, OAuth e testes E2E no backend não estão concluídos.

## Abordagens consideradas

1. **Publicar imediatamente com APKs debug:** rejeitada; não serve como distribuição comercial, não tem assinatura/identidade final e o backend não suporta todas as funções anunciadas.
2. **Reescrever toda a plataforma e trocar de backend:** rejeitada nesta fase; aumenta o risco e não resolve os bloqueios legais/operacionais mais urgentes.
3. **Preparar um release controlado mantendo Android/Supabase:** escolhida; corrige documentação e proveniência, mantém publicação sob gates explícitos e não altera produção inadvertidamente.

## Decisões de implementação

- main mantém-se protegida; todo o trabalho desta fase decorre numa branch.
- Não fazer deploy de migrations, Edge Functions, configuração Auth, nem armazenamento no projeto alojado sem reconciliação/backup e aprovação explícita.
- Não criar, transmitir ou armazenar chaves privadas, passwords, credenciais OAuth ou segredos em conversas, ficheiros do repositório ou commits.
- Manter a página de privacidade, termos e Data Safety como rascunhos com campos legais por preencher; não inventar o responsável pelo tratamento, morada, email, retenção ou preço final.
- Documentar sync cloud de chat/memória como sincronização protegida por RLS, não como encriptação ponta a ponta. O modelo generativo executa localmente e as consultas públicas da Wikipédia podem sair do dispositivo.
- Documentar que OCR é local e que documentos/faturas apenas passam para cloud quando o bucket/migração e a conta de proprietário estão configurados.
- Os cinco planos/variantes e valores são referências comerciais, não pagamentos nem provisionamento automático de subscrições.
- Não incluir integração POS/contabilidade/pagamentos sem um fornecedor/API escolhido e testado.

## Fora de âmbito nesta fase

- Testes físicos no OPPO A5m (explicitamente reservados para o utilizador).
- Publicação na Google Play, criação de conta de programador, assinatura comercial e configuração de segredos.
- Aplicação de migrações ou alterações de dados/configuração ao Supabase alojado.
- Decisões jurídicas finais, identificação da entidade responsável, política de retenção, pricing/contrato finais e integração com o primeiro POS.

## Entregáveis

1. Auditoria atualizada da diferença entre código local e backend alojado.
2. Política de privacidade e Data Safety drafts coerentes com a implementação.
3. Termos de serviço/licença B2B como modelo, com placeholders e revisão legal obrigatória.
4. Inventário de componentes/licenças de terceiros e verificações de release.
5. Documentação de lançamento atualizada, com bloqueios e condições de saída verificáveis.
6. CI verde no head final da branch; nenhum teste de telefone alegado.

## Critérios de aceitação

- Os documentos não contradizem a aplicação e distinguem claramente local/cloud/pendente.
- A branch contém o código fonte da Edge Function de IA desativada que já existe em produção.
- O comentário do armazenamento de chat não afirma que nunca existe sincronização cloud.
- Nenhuma chave privada ou credencial é comprometida.
- Nenhuma migração/setting foi aplicada ao Supabase alojado.
- Os smoke tests Android 8/16 e CI da branch terminam com sucesso.
- O resultado continua classificado como **não pronto para operação comercial real** até cumprir os bloqueios externos e físicos.