import {
  containsCustomerContactData,
  isBodyWithinLimit,
  MAX_BODY_BYTES,
  MAX_FACTS_CHARS,
  MAX_OUTPUT_CHARS,
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

Deno.test("rejects empty, non-text, and oversized model responses", () => {
  assertEquals(validateGeneratedDraft("  "), null);
  assertEquals(validateGeneratedDraft({ draft: "text" }), null);
  assertEquals(validateGeneratedDraft("x".repeat(MAX_OUTPUT_CHARS + 1)), null);
  assertEquals(validateGeneratedDraft("  Rascunho válido  "), "Rascunho válido");
});
