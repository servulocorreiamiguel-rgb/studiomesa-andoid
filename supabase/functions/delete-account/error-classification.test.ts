import { isDeletionConflictMessage } from "./error-classification.ts";

Deno.test("owner confirmation message is a conflict regardless of capitalization", () => {
  const message = "A tua conta é proprietária de um restaurante. Confirma explicitamente a eliminação permanente.";
  if (!isDeletionConflictMessage(message)) {
    throw new Error("Owner confirmation error should map to HTTP 409.");
  }
});

Deno.test("remaining team members map to a conflict", () => {
  if (!isDeletionConflictMessage("Antes de eliminar, remove os restantes membros da equipa.")) {
    throw new Error("Team-members error should map to HTTP 409.");
  }
});

Deno.test("unrelated server errors do not map to a conflict", () => {
  if (isDeletionConflictMessage("database connection failed")) {
    throw new Error("Unrelated server errors must retain the generic failure status.");
  }
});
