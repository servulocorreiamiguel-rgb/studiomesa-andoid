import {
  containsCustomerContactData,
  isBodyWithinLimit,
  MAX_BODY_BYTES,
  MAX_FACTS_CHARS,
  MAX_OUTPUT_CHARS,
  readBoundedUtf8Body,
  validateGeneratedDraft,
  validateRequestPayload,
} from "./validation.ts";
import { assert, assertEquals } from "jsr:@std/assert@1";

const validPayload = {
  content_type: "menu_description",
  title: "Hambúrguer da casa",
  facts: "Pão brioche, carne de vaca, alface e molho da casa.",
};

Deno.test("accepts and trims a valid restaurant content request", () => {
  const result = validateRequestPayload({
    ...validPayload,
    title: "  Hambúrguer da casa  ",
    facts: "  Pão brioche, carne de vaca e alface.  ",
  });
  assert(result.valid);
  assertEquals(result.value.title, "Hambúrguer da casa");
  assertEquals(result.value.facts, "Pão brioche, carne de vaca e alface.");
});

Deno.test("rejects unsupported content types and malformed payloads", () => {
  assertEquals(
    validateRequestPayload({ ...validPayload, content_type: "publish_now" }).valid,
    false,
  );
  assertEquals(validateRequestPayload(null).valid, false);
  assertEquals(validateRequestPayload([]).valid, false);
  assertEquals(validateRequestPayload({ ...validPayload, restaurant_id: "other-tenant" }).valid, false);
});

Deno.test("enforces title and facts length bounds", () => {
  assertEquals(validateRequestPayload({ ...validPayload, title: "x" }).valid, false);
  assertEquals(validateRequestPayload({ ...validPayload, title: "x".repeat(121) }).valid, false);
  assertEquals(validateRequestPayload({ ...validPayload, facts: "short" }).valid, false);
  assertEquals(validateRequestPayload({ ...validPayload, facts: "x".repeat(MAX_FACTS_CHARS + 1) }).valid, false);
});

Deno.test("rejects email and Portuguese phone details before provider calls", () => {
  assert(containsCustomerContactData("contacta cliente@example.com para confirmar"));
  assert(containsCustomerContactData("O contacto era +351 912 345 678"));
  assertEquals(
    validateRequestPayload({ ...validPayload, facts: "Cliente cliente@example.com confirmou o prato" }).valid,
    false,
  );
  assertEquals(
    validateRequestPayload({ ...validPayload, facts: "Ligaram do 912345678 para confirmar o prato" }).valid,
    false,
  );
});

Deno.test("checks request size using UTF-8 bytes, not JavaScript character count", () => {
  assert(isBodyWithinLimit('{"text":"' + "á".repeat(500) + '"}'));
  assertEquals(isBodyWithinLimit("á".repeat(MAX_BODY_BYTES)), false);
});

Deno.test("reads UTF-8 streams under the byte limit", async () => {
  const stream = new Response("Prato com maçã").body;
  assertEquals(await readBoundedUtf8Body(stream), "Prato com maçã");
  assertEquals(await readBoundedUtf8Body(null), "");
});

Deno.test("cancels oversized streams before buffering the full body", async () => {
  let cancelled = false;
  const stream = new ReadableStream<Uint8Array>({
    pull(controller) {
      controller.enqueue(new TextEncoder().encode("x".repeat(MAX_BODY_BYTES + 1)));
    },
    cancel() {
      cancelled = true;
    },
  });

  assertEquals(await readBoundedUtf8Body(stream), null);
  assert(cancelled);
});

Deno.test("rejects malformed UTF-8 when reading a bounded stream", async () => {
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(new Uint8Array([0xff]));
      controller.close();
    },
  });
  let rejected = false;
  try {
    await readBoundedUtf8Body(stream);
  } catch {
    rejected = true;
  }
  assert(rejected);
});

Deno.test("applies an independent limit to provider response streams", async () => {
  const providerLimit = 16;
  const oversized = new Response("short provider text").body;
  assertEquals(await readBoundedUtf8Body(oversized, providerLimit), null);
  assertEquals(
    await readBoundedUtf8Body(new Response("exactly 16 bytes").body, providerLimit),
    "exactly 16 bytes",
  );
  assertEquals(
    await readBoundedUtf8Body(new Response("short").body, providerLimit),
    "short",
  );
});

Deno.test("rejects empty, non-text, and oversized model responses", () => {
  assertEquals(validateGeneratedDraft("  "), null);
  assertEquals(validateGeneratedDraft({ draft: "text" }), null);
  assertEquals(validateGeneratedDraft("x".repeat(MAX_OUTPUT_CHARS + 1)), null);
  assertEquals(validateGeneratedDraft("  Rascunho válido  "), "Rascunho válido");
});
