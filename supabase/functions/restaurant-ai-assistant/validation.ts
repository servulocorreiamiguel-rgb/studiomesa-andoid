export const MAX_BODY_BYTES = 4096;
export const MAX_TITLE_CHARS = 120;
export const MAX_FACTS_CHARS = 1500;
export const MAX_OUTPUT_CHARS = 4000;

const ALLOWED_CONTENT_TYPES = new Set([
  "menu_description",
  "social_post",
  "campaign",
  "content_ideas",
]);

const EMAIL_PATTERN = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;
const PORTUGAL_PHONE_PATTERN = /(?:\+351[\s().-]?)?(?:9[1236](?:[\s().-]?\d){7}|2(?:[\s().-]?\d){8})/;

export type ValidAiRequest = {
  contentType: string;
  title: string;
  facts: string;
};

export type PayloadValidationResult =
  | { valid: true; value: ValidAiRequest }
  | { valid: false; message: string };

export function isBodyWithinLimit(raw: string): boolean {
  return new TextEncoder().encode(raw).byteLength <= MAX_BODY_BYTES;
}

export function containsCustomerContactData(value: string): boolean {
  return EMAIL_PATTERN.test(value) || PORTUGAL_PHONE_PATTERN.test(value);
}

export function validateRequestPayload(value: unknown): PayloadValidationResult {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return { valid: false, message: "O formato do pedido não é válido." };
  }

  const body = value as Record<string, unknown>;
  if (
    Object.keys(body).some((key) => !["content_type", "title", "facts"].includes(key))
  ) {
    return { valid: false, message: "O pedido contém campos não permitidos." };
  }

  const contentType = body.content_type;
  const title = typeof body.title === "string" ? body.title.trim() : "";
  const facts = typeof body.facts === "string" ? body.facts.trim() : "";

  if (typeof contentType !== "string" || !ALLOWED_CONTENT_TYPES.has(contentType)) {
    return { valid: false, message: "Escolhe um tipo de conteúdo válido." };
  }
  if (title.length < 2 || title.length > MAX_TITLE_CHARS) {
    return { valid: false, message: "O assunto deve ter entre 2 e 120 caracteres." };
  }
  if (facts.length < 10 || facts.length > MAX_FACTS_CHARS) {
    return { valid: false, message: "Descreve os factos confirmados em 10 a 1500 caracteres." };
  }
  if (containsCustomerContactData(title + "\n" + facts)) {
    return {
      valid: false,
      message: "Remove emails e números de telefone do texto. Não são necessários dados pessoais de clientes para gerar conteúdo.",
    };
  }

  return { valid: true, value: { contentType, title, facts } };
}

export function validateGeneratedDraft(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const draft = value.trim();
  if (!draft || draft.length > MAX_OUTPUT_CHARS) return null;
  return draft;
}
