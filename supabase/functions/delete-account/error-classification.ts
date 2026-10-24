/** Normalize trusted PostgreSQL messages before mapping them to HTTP status codes. */
export function isDeletionConflictMessage(message: string): boolean {
  const normalized = message.toLowerCase();
  return [
    "restantes membros da equipa",
    "confirma explicitamente",
    "não é proprietária",
  ].some((fragment) => normalized.includes(fragment));
}
