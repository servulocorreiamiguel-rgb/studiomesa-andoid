export type AiProviderName = "openai" | "gemini";

export type AiProviderConfig = {
  provider: AiProviderName;
  apiKey: string;
  model: string;
};

export type ProviderRequest = {
  url: string;
  headers: Record<string, string>;
  body: string;
};

/**
 * Resolve provider credentials only on the server.
 * Explicit configuration wins. If no provider is selected, prefer Gemini when
 * its key is present so a configured free-tier key is not accidentally ignored.
 */
export function resolveAiProviderConfig(
  getEnv: (name: string) => string | undefined,
): AiProviderConfig | null {
  const requested = (getEnv("STUDIOMESA_AI_PROVIDER") ?? "").trim().toLowerCase();
  const geminiKey = (getEnv("GEMINI_API_KEY") ?? "").trim();
  const openAiKey = (getEnv("OPENAI_API_KEY") ?? "").trim();

  const provider = requested || (geminiKey ? "gemini" : openAiKey ? "openai" : "");
  if (provider === "gemini" && geminiKey) {
    return {
      provider: "gemini",
      apiKey: geminiKey,
      model: (getEnv("GEMINI_MODEL") ?? "").trim() || "gemini-3.5-flash-lite",
    };
  }
  if (provider === "openai" && openAiKey) {
    return {
      provider: "openai",
      apiKey: openAiKey,
      model: (getEnv("OPENAI_MODEL") ?? "").trim() || "gpt-4.1-mini",
    };
  }
  return null;
}

export function buildProviderRequest(
  config: AiProviderConfig,
  systemPrompt: string,
  userPrompt: string,
): ProviderRequest {
  if (config.provider === "gemini") {
    return {
      url: "https://generativelanguage.googleapis.com/v1beta/models/" +
        encodeURIComponent(config.model) + ":generateContent",
      headers: {
        "Content-Type": "application/json",
        "x-goog-api-key": config.apiKey,
      },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: systemPrompt }] },
        contents: [{ role: "user", parts: [{ text: userPrompt }] }],
        generationConfig: { temperature: 0.5, maxOutputTokens: 450 },
      }),
    };
  }

  return {
    url: "https://api.openai.com/v1/chat/completions",
    headers: {
      Authorization: "Bearer " + config.apiKey,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      model: config.model,
      messages: [
        { role: "system", content: systemPrompt },
        { role: "user", content: userPrompt },
      ],
      temperature: 0.5,
      max_completion_tokens: 450,
    }),
  };
}

export function extractProviderText(
  provider: AiProviderName,
  payload: unknown,
): string | null {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) return null;
  const data = payload as Record<string, unknown>;

  if (provider === "gemini") {
    const candidates = Array.isArray(data.candidates) ? data.candidates : [];
    const first = candidates[0];
    if (!first || typeof first !== "object" || Array.isArray(first)) return null;
    const candidate = first as Record<string, unknown>;
    const content = candidate.content;
    if (!content || typeof content !== "object" || Array.isArray(content)) return null;
    const parts = (content as Record<string, unknown>).parts;
    if (!Array.isArray(parts)) return null;
    const textParts = parts
      .filter((part): part is Record<string, unknown> =>
        Boolean(part) && typeof part === "object" && !Array.isArray(part)
      )
      .map((part) => typeof part.text === "string" ? part.text : "")
      .filter((part) => part.length > 0);
    return textParts.length > 0 ? textParts.join("\n") : null;
  }

  const choices = Array.isArray(data.choices) ? data.choices : [];
  const first = choices[0];
  if (!first || typeof first !== "object" || Array.isArray(first)) return null;
  const message = (first as Record<string, unknown>).message;
  if (!message || typeof message !== "object" || Array.isArray(message)) return null;
  const text = (message as Record<string, unknown>).content;
  return typeof text === "string" ? text : null;
}
