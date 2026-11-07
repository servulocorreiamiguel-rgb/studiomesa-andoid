import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

function publicSearxngUrl(): URL | null {
  const configured = Deno.env.get("SEARXNG_BASE_URL")?.trim();
  if (!configured) return null;

  try {
    const url = new URL(configured);
    if (
      url.protocol !== "https:" ||
      url.username ||
      url.password ||
      url.search ||
      url.hash ||
      !url.hostname ||
      url.hostname === "localhost" ||
      url.hostname.endsWith(".local") ||
      url.hostname.endsWith(".internal") ||
      /^(?:127\.|10\.|192\.168\.|169\.254\.|172\.(?:1[6-9]|2\d|3[01])\.)/.test(url.hostname) ||
      url.hostname.includes(":")
    ) return null;

    url.pathname = url.pathname.endsWith("/") ? url.pathname : url.pathname + "/";
    return new URL("search", url);
  } catch {
    return null;
  }
}

function queryContainsPrivateData(query: string): boolean {
  return (
    /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(query) ||
    /(?<!\d)(?:\d[ -]?){9,}(?!\d)/.test(query) ||
    /(?:\b(?:NIF|NIPC)\s*[:#]?\s*\d(?:[ -]?\d){8}\b|\bIBAN\s*[:#]?\s*[A-Z]{2}\d{2}[A-Z0-9 ]{10,30}\b)/i.test(query)
  );
}

function normalizeResults(rawResults: unknown[]): Array<Record<string, unknown>> {
  return rawResults.slice(0, 5).flatMap((rawItem) => {
    if (typeof rawItem !== "object" || rawItem === null) return [];
    const item = rawItem as Record<string, unknown>;
    const rawUrl = typeof item.url === "string" ? item.url : "";
    const title = typeof item.title === "string" ? item.title.trim() : "";
    const content = typeof item.content === "string" ? item.content.trim() : "";
    let safeUrl: string;
    try {
      const parsed = new URL(rawUrl);
      if (parsed.protocol !== "https:") return [];
      safeUrl = parsed.toString();
    } catch {
      return [];
    }
    if (!title || !content) return [];

    const publishedDate = typeof item.published_date === "string"
      ? item.published_date
      : typeof item.publishedDate === "string"
        ? item.publishedDate
        : null;

    return [{
      title: title.slice(0, 220),
      url: safeUrl,
      content: content.slice(0, 1200),
      published_date: publishedDate?.slice(0, 50) ?? null,
    }];
  });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ code: "METHOD_NOT_ALLOWED", error: "Método não permitido." }, 405);

  // Supabase validates the user's JWT because verify_jwt remains enabled.
  const authorization = req.headers.get("Authorization") ?? "";
  if (!authorization.startsWith("Bearer ")) {
    return json({ code: "AUTH_REQUIRED", error: "Inicia sessão para pesquisar na Web." }, 401);
  }

  let body: unknown;
  try {
    body = await req.json();
  } catch {
    return json({ code: "INVALID_JSON", error: "Pedido de pesquisa inválido." }, 400);
  }

  const query = typeof body === "object" && body !== null && "query" in body
    ? String((body as { query: unknown }).query ?? "").trim()
    : "";

  if (query.length < 3 || query.length > 200 || queryContainsPrivateData(query)) {
    return json({
      code: "INVALID_QUERY",
      error: "Usa uma pergunta pública com 3 a 200 caracteres e sem dados pessoais ou identificadores.",
    }, 400);
  }

  const tavilyApiKey = Deno.env.get("TAVILY_API_KEY")?.trim();
  const searxngEndpoint = publicSearxngUrl();
  if (!tavilyApiKey && !searxngEndpoint) {
    return json({
      code: "SEARCH_PROVIDER_NOT_CONFIGURED",
      error: "A pesquisa Web ainda não está ativa. Configura TAVILY_API_KEY nos segredos das Edge Functions ou um servidor SearXNG HTTPS.",
    }, 503);
  }

  try {
    let provider: "Tavily" | "SearXNG";
    let rawResults: unknown[];

    if (tavilyApiKey) {
      const response = await fetch("https://api.tavily.com/search", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${tavilyApiKey}`,
        },
        body: JSON.stringify({
          query,
          search_depth: "basic",
          topic: "general",
          max_results: 5,
          include_answer: false,
          include_raw_content: false,
          include_images: false,
        }),
        redirect: "error",
        signal: AbortSignal.timeout(15000),
      });

      if (response.status === 401 || response.status === 403) {
        return json({
          code: "TAVILY_AUTH_FAILED",
          error: "A chave do serviço de pesquisa não foi aceite. Confirma o segredo TAVILY_API_KEY no Supabase.",
        }, 502);
      }
      if (response.status === 429) {
        return json({
          code: "TAVILY_RATE_LIMITED",
          error: "O limite de pesquisas foi atingido temporariamente. Tenta novamente mais tarde.",
        }, 429);
      }
      if (!response.ok) {
        return json({
          code: "TAVILY_PROVIDER_ERROR",
          error: "O serviço de pesquisa Web não respondeu corretamente.",
        }, 502);
      }

      const payload: unknown = await response.json();
      if (
        typeof payload !== "object" ||
        payload === null ||
        !("results" in payload) ||
        !Array.isArray((payload as { results: unknown }).results)
      ) {
        return json({
          code: "TAVILY_INVALID_RESPONSE",
          error: "O serviço de pesquisa devolveu uma resposta inválida.",
        }, 502);
      }
      rawResults = (payload as { results: unknown[] }).results;
      provider = "Tavily";
    } else {
      const endpoint = searxngEndpoint;
      if (!endpoint) {
        return json({
          code: "SEARCH_PROVIDER_NOT_CONFIGURED",
          error: "Não existe um fornecedor de pesquisa configurado.",
        }, 503);
      }

      endpoint.searchParams.set("q", query);
      endpoint.searchParams.set("format", "json");
      endpoint.searchParams.set("language", "pt-PT");
      endpoint.searchParams.set("safesearch", "1");
      endpoint.searchParams.set("categories", "general");

      const response = await fetch(endpoint, {
        method: "GET",
        headers: {
          "Accept": "application/json",
          "User-Agent": "StudioMesa-Public-Web-Search/1.0",
        },
        redirect: "error",
        signal: AbortSignal.timeout(15000),
      });

      if (response.status === 403) {
        return json({
          code: "SEARXNG_JSON_DISABLED_OR_BLOCKED",
          error: "O servidor SearXNG recusou a API JSON. Verifica o formato JSON e o acesso do Supabase.",
        }, 502);
      }
      if (response.status === 429) {
        return json({
          code: "SEARXNG_RATE_LIMITED",
          error: "O servidor SearXNG limitou temporariamente as pesquisas. Tenta mais tarde.",
        }, 429);
      }
      if (!response.ok) {
        return json({
          code: "SEARXNG_PROVIDER_ERROR",
          error: "O servidor SearXNG não respondeu corretamente.",
        }, 502);
      }

      const payload: unknown = await response.json();
      if (
        typeof payload !== "object" ||
        payload === null ||
        !("results" in payload) ||
        !Array.isArray((payload as { results: unknown }).results)
      ) {
        return json({
          code: "SEARXNG_INVALID_RESPONSE",
          error: "O servidor SearXNG devolveu uma resposta inválida ou não ativou a API JSON.",
        }, 502);
      }
      rawResults = (payload as { results: unknown[] }).results;
      provider = "SearXNG";
    }

    const results = normalizeResults(rawResults);
    return json({
      provider,
      query,
      results,
      warning: "Os excertos são evidência inicial, não confirmação. Compara fontes independentes e datas antes de concluir.",
    });
  } catch {
    return json({
      code: "SEARCH_PROVIDER_UNAVAILABLE",
      error: "Não foi possível contactar o serviço de pesquisa Web. Verifica a configuração e a ligação.",
    }, 502);
  }
});
