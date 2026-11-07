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

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ code: "METHOD_NOT_ALLOWED", error: "Método não permitido." }, 405);

  // Supabase validates the user's JWT because verify_jwt remains enabled.
  const authorization = req.headers.get("Authorization") ?? "";
  if (!authorization.startsWith("Bearer ")) {
    return json({ code: "AUTH_REQUIRED", error: "Inicia sessão para pesquisar na Web." }, 401);
  }

  const apiKey = Deno.env.get("TAVILY_API_KEY");
  if (!apiKey) {
    return json({
      code: "WEB_SEARCH_NOT_CONFIGURED",
      error: "A pesquisa Web ainda não foi configurada. Adiciona TAVILY_API_KEY aos Secrets da Edge Function no Supabase.",
    }, 503);
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

  const containsPrivateData =
    /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(query) ||
    /(?<!\d)(?:\d[ -]?){9,}(?!\d)/.test(query) ||
    /(?:\b(?:NIF|NIPC)\s*[:#]?\s*\d(?:[ -]?\d){8}\b|\bIBAN\s*[:#]?\s*[A-Z]{2}\d{2}[A-Z0-9 ]{10,30}\b)/i.test(query);

  if (query.length < 3 || query.length > 200 || containsPrivateData) {
    return json({
      code: "INVALID_QUERY",
      error: "Usa uma pergunta pública com 3 a 200 caracteres e sem dados pessoais ou identificadores.",
    }, 400);
  }

  try {
    const response = await fetch("https://api.tavily.com/search", {
      method: "POST",
      headers: {
        "Authorization": "Bearer " + apiKey,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        query,
        topic: "general",
        search_depth: "basic",
        max_results: 5,
        include_answer: false,
        include_raw_content: false,
        include_images: false,
        include_favicon: false,
      }),
      signal: AbortSignal.timeout(15000),
    });

    if (!response.ok) {
      return json({
        code: response.status === 429 ? "WEB_SEARCH_LIMIT_REACHED" : "WEB_SEARCH_PROVIDER_ERROR",
        error: response.status === 429
          ? "O limite temporário da pesquisa Web foi atingido."
          : "O serviço de pesquisa Web não respondeu corretamente.",
      }, response.status === 429 ? 429 : 502);
    }

    const payload = await response.json();
    const rawResults = Array.isArray(payload?.results) ? payload.results : [];
    const results = rawResults.slice(0, 5).flatMap((item: Record<string, unknown>) => {
      const url = typeof item.url === "string" ? item.url : "";
      const title = typeof item.title === "string" ? item.title.trim() : "";
      const content = typeof item.content === "string" ? item.content.trim() : "";
      if (!url.startsWith("https://") || !title || !content) return [];
      return [{
        title: title.slice(0, 220),
        url,
        content: content.slice(0, 1200),
        published_date: typeof item.published_date === "string" ? item.published_date : null,
        score: typeof item.score === "number" ? item.score : null,
      }];
    });

    return json({
      provider: "Tavily",
      query,
      results,
      warning: "Os excertos são evidência inicial, não confirmação. Compara fontes independentes e datas antes de concluir.",
    });
  } catch {
    return json({
      code: "WEB_SEARCH_UNAVAILABLE",
      error: "Não foi possível contactar o serviço de pesquisa Web. Verifica a ligação e tenta novamente.",
    }, 502);
  }
});
