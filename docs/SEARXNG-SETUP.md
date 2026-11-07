# SearXNG na StudioMesa — configuração da pesquisa pública

A função `studiomesa-web-search` usa SearXNG através da API JSON. O cliente
Android envia apenas a consulta pública; não envia histórico, memória, faturas,
nomes de clientes, equipa ou identificadores do restaurante.

## Requisitos do servidor

A StudioMesa precisa de um servidor SearXNG HTTPS que permaneça disponível e
aceite pedidos do Supabase Edge Runtime.

Na configuração SearXNG, a API JSON tem de estar ativa em
`search.formats`, por exemplo:

```yaml
search:
  formats:
    - html
    - json
```

A API de pesquisa é `GET /search?q=...&format=json`. Algumas instâncias públicas
desativam JSON ou bloqueiam pedidos automatizados. Não basta a página HTML abrir.

## Configurar o Supabase

Depois de instalar e testar o servidor, configura a variável da Edge Function:

```bash
supabase secrets set SEARXNG_BASE_URL=https://seu-servidor.example
supabase functions deploy studiomesa-web-search
```

Ou define `SEARXNG_BASE_URL` nos Secrets das Edge Functions do Dashboard.
Não é necessária uma chave API SearXNG. O endpoint é configurado apenas pelo
administrador; a aplicação nunca pode escolher livremente o host, o que evita
usar a função como proxy/SSRF.

A função mantém `verify_jwt=true`, aceita consultas curtas e rejeita padrões
com emails, números de telefone, NIF/NIPC e IBAN. Só devolve até cinco resultados
HTTPS, com excertos limitados. Se o servidor não estiver configurado, devolverá
`SEARXNG_NOT_CONFIGURED`; se JSON estiver desativado ou houver um bloqueio,
devolve um erro explícito, sem simular resultados.

## Validação antes de ativar

Testa diretamente no próprio servidor:

```bash
curl --fail --get 'https://seu-servidor.example/search' \
  --data-urlencode 'q=site:gov.pt legislação Portugal' \
  --data-urlencode 'format=json'
```

Confirma que a resposta contém `results` e que os motores têm resultados úteis,
sem CAPTCHAs, bloqueios 403 ou limites permanentes. Depois executa a Edge Function
com uma conta autenticada e confirma que os resultados incluem títulos, URLs HTTPS
e excertos. Testa também: pesquisa sem resultados, timeout, HTTP 403/429, consulta
com PII e token inválido.

## Estado do teste em 7 de novembro de 2026

Foram testadas várias instâncias públicas listadas em `searx.space`. As que
foram verificadas bloquearam pedidos automatizados com 403, Anubis ou verificação
humana; a instância gratuita Hugging Face testada devolveu JSON, mas sem resultados
e com motores em CAPTCHA/limite. Não foi encontrada uma instância pública estável
suficientemente fiável para produção.

Por isso, o código está preparado para SearXNG, mas a pesquisa geral na Internet
continua desativada até existir um endpoint SearXNG próprio/fiável configurado.
Não se envia informação privada para uma instância pública desconhecida.

## Custos e disponibilidade

O software SearXNG é open source e não exige uma chave de pesquisa. Isso não torna
gratuito o alojamento sempre disponível: um serviço gratuito que adormece, bloqueia
motores ou limita pedidos não serve para uma aplicação comercial. Para produção,
usa alojamento persistente sob controlo do responsável pelo serviço e monitoriza
a disponibilidade.
