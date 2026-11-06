# StudioMesa — estratégia de distribuição Google Play

**Data:** 6 de novembro de 2026  
**Estado:** recomendação técnica/comercial para decisão; não é uma decisão publicada nem uma confirmação de aceitação pela Google.

## Situação atual do repositório

O Gradle gera cinco variantes de distribuição:

- Essencial Restaurante
- Profissional Restaurante
- Profissional Cliente
- Premium Restaurante
- Premium Cliente

O workflow comercial pode gerar cinco AABs e cinco APKs assinados. Isto é uma capacidade de build, não uma decisão de catálogo nem uma aprovação da Google Play.

## Risco de cinco listagens semelhantes

As três apps de Gestão partilham grande parte da mesma finalidade e interface, variando sobretudo o plano/funcionalidades; as duas apps Cliente também são semelhantes. Publicar cinco fichas diferentes só porque o identificador e alguns módulos variam pode levantar questões de conteúdo repetitivo, funcionalidade e qualidade de cada listagem.

A Google indica que cada app white-label precisa de uma presença de loja realmente própria, com descrição, ícones, gráficos e capturas de ecrã relevantes e diferentes; as capturas têm de mostrar funções realmente disponíveis. Também recomenda contas de programador descentralizadas por cliente, porque um problema de política numa app de uma conta centralizada pode afetar todas as restantes apps dessa conta.

Fontes oficiais consultadas em 06/11/2026:

- [Práticas recomendadas para programadores de apps white-label](https://support.google.com/googleplay/android-developer/answer/15884185)
- [Política de conteúdo repetitivo](https://support.google.com/googleplay/android-developer/answer/9898783)
- [Política de funcionalidade mínima e qualidade](https://support.google.com/googleplay/android-developer/answer/11926878)

## Opção recomendada para o StudioMesa multi-restaurante

**Avaliar duas aplicações com finalidades distintas:**

1. **StudioMesa Gestão:** uma app para proprietários e funcionários, com as funcionalidades autorizadas pelo plano do restaurante.
2. **StudioMesa Cliente:** uma app para clientes consultarem a carta, cartão de fidelização e reservas do restaurante.

Os planos Essencial/Profissional/Premium seriam direitos/entitlements associados à conta/restaurante no backend. O servidor deveria validar esses direitos em cada operação e a interface esconder/desativar apenas por conveniência, nunca como único controlo de segurança.

### O que falta para esta opção

- [ ] Criar no backend o modelo de plano/entitlements por restaurante e a respetiva gestão pelo proprietário/operador.
- [ ] Definir a forma de pagamento/licença e prova de compra, sem confiar apenas em um APK instalado ou numa variável local.
- [ ] Fazer com que a app Gestão tenha um package ID estável, sem separação Essencial/Profissional/Premium no nome do pacote.
- [ ] Fazer o mesmo para a app Cliente, com as permissões e os fluxos próprios de consumidor.
- [ ] Testar tentativas de alterar entitlement localmente, trocar de restaurante, expiração, renovação, downgrade e contas sem licença.
- [ ] Atualizar as permissões RLS/RPC do Supabase depois de reconciliar migrations e ensaiar em base descartável.
- [ ] Refazer os AABs, smoke tests, política de privacidade e Data Safety para os dois produtos finais.

**Limitação:** os entitlements não estão implementados no estado atual. Não remover os cinco sabores nem criar um sistema de planos improvisado antes de desenhar/testar a autorização no servidor.

## Opção white-label por restaurante

Se cada restaurante quiser uma app com a sua própria marca, nome, ícone, conteúdo e experiência, a recomendação oficial da Google para fornecedores white-label é avaliar o modelo descentralizado:

- o restaurante/cliente é titular da sua conta Play Console e publica a própria app;
- o fornecedor pode receber acesso técnico gerido, por convite e com permissões limitadas;
- cada listagem deve refletir conteúdo, marca e experiência reais daquele restaurante;
- o contrato deve definir titularidade, manutenção, publicação, suporte, dados e saída do fornecedor.

Esta opção reduz a concentração do risco de política numa única conta central, mas exige processos de onboarding, suporte e gestão de atualizações por cliente. A conta do cliente também tem de cumprir idade, verificação e demais requisitos de publicação.

## Opção de distribuição B2B direta

A entrega de APK assinado pode ser considerada para um número limitado de restaurantes, mas requer:

- assinatura de release estável e gestão segura da chave de assinatura;
- canal privado de distribuição e atualizações controladas;
- instruções de instalação, verificação da origem e suporte;
- verificação das regras de distribuição Android aplicáveis no país e no momento;
- contrato, privacidade, segurança, backups e processo de atualização.

Esta opção não elimina os requisitos legais/privacidade nem transforma um APK debug numa release comercial.

## Decisão e critério de saída

**Recomendação atual:** manter os cinco sabores para desenvolvimento, testes e demonstração; antes de criar fichas Play, decidir entre duas apps multi-restaurante com entitlements no servidor ou white-label descentralizado por cliente. Não submeter cinco listagens semelhantes sem uma avaliação documentada da política.

Decisão aprovada por: [RESPONSÁVEL LEGAL/PRODUTO]  
Modelo escolhido: [DUAS APPS / WHITE-LABEL DESCENTRALIZADO / DISTRIBUIÇÃO PRIVADA]  
Data: [A PREENCHER]

Esta decisão é bloqueante para escolher os package IDs comerciais e executar o workflow definitivo de assinatura.