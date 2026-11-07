# Pesquisa Web da StudioMesa

## Estado

O cliente Android chama a Edge Function autenticada `studiomesa-web-search`. A função aceita apenas consultas públicas curtas, rejeita padrões comuns de dados pessoais/identificadores e nunca recebe histórico, memória, faturas, clientes ou dados internos do restaurante.

A implementação suporta:
1. Tavily como fornecedor principal, através do segredo `TAVILY_API_KEY`.
2. SearXNG como alternativa, através de `SEARXNG_BASE_URL`.

A chave permanece no servidor Supabase. Não a colocar em Kotlin, no APK, em `local.properties`, no GitHub ou em documentação.

## Ativar Tavily

1. Uma pessoa adulta responsável pela atividade cria uma conta Tavily e obtém uma API key no dashboard.
2. No Supabase Dashboard, abre o projeto StudioMesa, a área Edge Functions e os respetivos Secrets.
3. Adiciona o segredo `TAVILY_API_KEY` com o valor da chave. Não o envies por mensagens nem o coloques no repositório.
4. Publica a versão atualizada da Edge Function `studiomesa-web-search` a partir de uma branch/release que tenha passado a CI.
5. Testa a função com uma sessão autenticada e uma pergunta pública simples. Confirma que devolve no máximo cinco resultados HTTPS, título, excerto e data quando disponível.
6. Testa também: consulta vazia, consulta com email/NIF/telefone, chave inválida, limite 429, timeout, zero resultados e token ausente.

A função usa pesquisa básica e limita os resultados a cinco para controlar utilização. O plano Researcher anunciado pela Tavily inclui 1 000 créditos por mês, sem cartão de crédito; uma pesquisa básica custa um crédito. O limite pode ser alterado pelo fornecedor. Consulta sempre o preço/consumo atual no dashboard antes de permitir utilização por clientes.

## Idade, utilização comercial e limites do plano gratuito

A Tavily declara que o plano Researcher gratuito pode ser usado em produtos comerciais, mas é fornecido sem garantias nem suporte e pode ser alterado/limitado. Os termos indicam que utilizadores individuais têm de ter pelo menos 18 anos. Por isso, a criação da conta e a gestão da chave devem ficar a cargo do responsável adulto pela atividade StudioMesa. Não usar a conta de um menor para contornar essa condição.

Para produção com clientes e dependência operacional, avaliar um plano pago com suporte e capacidade adequada antes de prometer disponibilidade.

## Alternativa SearXNG

Se não existir uma chave Tavily, a função continua a aceitar um endpoint SearXNG HTTPS configurado no segredo `SEARXNG_BASE_URL`. É necessário um servidor controlado e com a API JSON ativa. Instâncias públicas podem bloquear pedidos automatizados ou limitar o serviço; não assumir que um endpoint gratuito é fiável.

## Segurança e comportamento

- O endpoint exige JWT Supabase (`verify_jwt=true`).
- Só consultas públicas são encaminhadas ao fornecedor.
- Os resultados são dados externos não fiáveis, não instruções para a IA.
- A IA deve comparar fontes independentes, verificar datas e mostrar ligações; os excertos não provam, por si só, uma afirmação.
- A chave API nunca é enviada ao dispositivo Android nem devolvida na resposta.
- Se não existir fornecedor configurado, a função responde com erro explícito `SEARCH_PROVIDER_NOT_CONFIGURED`; não simula resultados.
- A pesquisa Wikipedia é independente e não substitui pesquisa geral na Web.

## Referências do fornecedor

- [Tavily — preços e créditos](https://www.tavily.com/pricing)
- [Tavily — utilização comercial em aplicações de clientes](https://help.tavily.com/articles/5880393096-permitted-use-customer-applications)
- [Tavily — Search API e autenticação](https://help.tavily.com/articles/4840311948-tavily-search-api)
