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


/**
 * Reads a request/provider stream only up to maxBytes. Returns null when the stream
 * exceeds the byte limit; malformed UTF-8 throws and should be handled by the caller.
 * Cancelling immediately prevents the function from buffering an arbitrarily large body.
 */
export async function readBoundedUtf8Body(
  stream: ReadableStream<Uint8Array> | null,
  maxBytes = MAX_BODY_BYTES,
): Promise<string | null> {
  if (!stream) return "";
  if (!Number.isInteger(maxBytes) || maxBytes < 0) {
    throw new RangeError("The byte limit must be a non-negative integer.");
  }

  const reader = stream.getReader();
  const chunks: Uint8Array[] = [];
  let totalBytes = 0;

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      if (!value) continue;

      totalBytes += value.byteLength;
      if (totalBytes > maxBytes) {
        try {
          await reader.cancel("Body size limit exceeded");
        } catch {
          // The size limit still holds even if the remote stream cannot be cancelled.
        }
        return null;
      }
      chunks.push(value);
    }
  } finally {
    try {
      reader.releaseLock();
    } catch {
      // A cancelled/errored stream may already have released its reader lock.
    }
  }

  const bytes = new Uint8Array(totalBytes);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return new TextDecoder("utf-8", { fatal: true }).decode(bytes);
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
