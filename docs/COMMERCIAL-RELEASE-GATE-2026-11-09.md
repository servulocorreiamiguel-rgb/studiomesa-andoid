# StudioMesa — gate de lançamento comercial

**Verificação:** 9 de novembro de 2026  
**Código:** integrado em `main` no commit `5fc1526c64ef4bf589a5bd4211dc876aa2d38265`  
**Decisão:** NÃO LIBERADO PARA PILOTO COM DADOS REAIS OU VENDA.

Este documento substitui a fotografia de 6 de novembro. Uma funcionalidade só deve ser anunciada num plano comercial quando o respetivo fluxo estiver implementado, ativo no backend alojado e testado ponta a ponta.

## O que já passou

- [x] Melhorias de IA local e privacidade integradas em `main` (PR #98).
- [x] Análise semanal por segunda-feira–domingo, padrões de maior afluência e previsão, com confiança, amostras e limites para evitar extrapolações excessivas.
- [x] Reconhecimento de feriados nacionais portugueses; Carnaval identificado como facultativo.
- [x] Configuração de feriados municipais/eventos locais na página Análise. O calendário ainda é local ao dispositivo e não sincroniza.
- [x] Ferramentas de cálculo económico locais, apenas de leitura, com dados de entrada e avisos.
- [x] Parser de faturas com testes para data de emissão/vencimento e separação de total/IVA.
- [x] Resumo de faturas com filtro de datas/fornecedor/estado e totais aprovados/pendentes/rejeitados separados.
- [x] Sincronização do histórico/Memória desligada por defeito; consentimento do proprietário associado à conta/restaurante.
- [x] CI completa do código Android antes da integração: workflow [#42690365320](https://github.com/servulocorreiamiguel-rgb/studiomesa-andoid/actions/runs/42690365320) passou build das cinco variantes, testes unitários, Lint, replay de migrações/autorização e smoke tests Android 8/API 26 e Android 16/API 36.
- [x] Pipeline comercial existente, protegida por confirmação explícita, branch `main`, CI integral para o mesmo commit e validação de keystore, assinatura e checksums.
- [x] Hash e URL do modelo LFM2.5 corrigidos nos avisos de terceiros; toolchain de release alinhada com AGP 8.13.2/Gradle 8.13.

## Bloqueios do backend

- [ ] Reconciliar as 55 migrações locais com as 63 versões remotas do Supabase: 51 prefixos comuns, quatro só locais e doze só remotas.
- [ ] Recuperar SQL das versões remotas sem ficheiro local, comparar o conteúdo completo das versões comuns e preparar sequência canónica.
- [ ] Backup e restauro testados num ambiente separado antes de qualquer alteração.
- [ ] Não fazer `db push`, deploy de função ou alteração ao Auth/Storage enquanto a reconciliação não estiver aprovada.
- [ ] Criar/validar no backend alojado as tabelas de fichas técnicas/ingredientes e permissões granulares atualmente ausentes.
- [ ] Rever RLS, grants e as 42 funções SECURITY DEFINER sinalizadas pelo Advisor. Não revogar permissões em bloco.
- [ ] Resolver a proteção de palavras-passe comprometidas desativada dentro do plano/configuração suportados.

## Autenticação e testes de produto

- [ ] Configurar e testar SMTP e confirmação de email.
- [ ] Configurar credenciais OAuth próprias Google/Microsoft, Redirect URLs e sessões.
- [ ] Testar login, recuperação, logout, expiração/revogação e mudança de dispositivo.
- [ ] E2E com contas descartáveis: dois restaurantes, proprietário, funcionário e cliente; reservas, pedidos, fidelização, inventário, faturas, sync, exportação e eliminação.
- [ ] Testar o modelo LFM2.5 real em Android físico: instalação inicial (~736 MB), inferência, chamadas às ferramentas, tempo de resposta, RAM, aquecimento, estabilidade e recuperação após falta de memória.
- [ ] Testar instalação/atualização e notificações em pelo menos um aparelho Android de cada classe de suporte prevista.

## Privacidade, legalidade e licenças

- [ ] Identificar a entidade/responsável pelo tratamento, morada, NIF e contacto profissional.
- [ ] Definir finalidades, bases jurídicas, prazos de retenção, processo de eliminação/exportação e gestão de incidentes.
- [ ] Confirmar fornecedores, subcontratantes, regiões, DPA e transferências internacionais.
- [ ] Finalizar e publicar política de privacidade pública; retirar placeholders do modelo.
- [ ] Finalizar Data Safety com base no AAB efetivamente distribuído e na configuração real de backend.
- [ ] Testar o fluxo de eliminação de conta por email/PKCE com contas descartáveis.
- [ ] Rever termos B2B, licença, DPA e condições fiscais por profissional competente.
- [ ] Rever integralmente a LFM Open License v1.0 e confirmar a elegibilidade comercial da entidade.
- [ ] Gerar SBOM e notices completos das dependências transitivas, nativas e ativos.
- [ ] Confirmar titularidade/direitos de código, marca, logótipo, fontes, ícones e materiais.

## Identidade, assinatura e distribuição

- [ ] Escolher package ID definitivo. Não usar `pt.studiomesa.preview.*` em produção.
- [ ] Decidir estratégia de distribuição: cinco variantes Gradle não significam automaticamente cinco listagens Play. Avaliar Gestão e Cliente como aplicações distintas, com entitlement seguro, ou distribuição B2B privada.
- [ ] Criar a keystore definitiva fora do repositório, guardar backups offline e proteger passwords.
- [ ] Confirmar GitHub Actions secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` e variable `STUDIOMESA_RELEASE_APPLICATION_ID`.
- [ ] Gerar os cinco AAB/APK comerciais a partir de `main` com CI verde para o mesmo commit e validar assinaturas/checksums.
- [ ] Preparar listagens, screenshots reais, classificação de conteúdo, URL de privacidade e recurso de eliminação.
- [ ] Cumprir requisitos da conta de programador e testes de publicação aplicáveis ao tipo de conta.
- [ ] Decidir e testar backups, monitorização, suporte, atualizações e resposta a incidentes.

## Decisão atual

**Demonstração:** permitida com APK debug e dados fictícios, claramente identificados como demonstração.

**Piloto com dados reais:** ainda não permitido. Os bloqueios de base de dados, Auth, E2E, privacidade/contratos e recuperação precisam de ser resolvidos.

**Venda/publicação:** ainda não permitido. Faltam, além dos anteriores, elegibilidade de licença do modelo, pacote definitivo, assinatura comercial, Data Safety e distribuição aprovada.

A execução comercial só deve ser iniciada quando todos os gates relevantes estiverem fechados. O workflow não publica automaticamente na Google Play.
