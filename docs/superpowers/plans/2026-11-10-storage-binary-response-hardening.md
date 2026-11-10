# Storage Binary Response Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bound memory usage when reading private invoice documents and Storage errors in the Android client.

**Architecture:** Extend the existing `ResponseBodyReader` with one bounded byte-reading primitive, then reuse it from the existing string reader and Supabase Storage transport. Reject oversized successful responses as soon as possible using the declared length and enforce the same limit while streaming.

**Tech Stack:** Kotlin, Android/JVM streams, JUnit 4, GitHub Actions, Supabase Storage.

**Spec:** `docs/superpowers/specs/2026-11-10-storage-binary-response-hardening-design.md`

## Global Constraints

- Keep the Storage invoice payload ceiling at exactly 25 MiB (`25 * 1024 * 1024` bytes).
- Keep Storage bucket access private and owner-scoped; do not change grants, RLS or live migrations.
- Keep HTTPS, disable redirects and caches for authenticated requests.
- Do not add paid APIs, new runtime dependencies, service-role keys or new data recipients.
- Keep the demo/release signing workflow unchanged.
- Do not read arbitrary Storage error bodies without a small bounded cap.

## Review Focus

- An oversized stream with a missing Content-Length must stop at the limit, not be fully buffered.
- A server that advertises an excessive Content-Length must be rejected before opening/reading the body.
- A stream whose bulk read returns zero must still progress without a busy loop.
- Uploads and delete responses must keep their existing success/error behavior.
- Storage error responses must remain sanitized and preserve HTTP status/path metadata.

---

### Task 1: Add bounded byte-stream tests

**Files:**
- Modify: `app/src/test/java/pt/studiomesa/app/ResponseBodyReaderTest.kt`
- Modify later: `app/src/main/java/pt/studiomesa/app/ResponseBodyReader.kt`

**Interface to produce:**
- `ResponseBodyReader.readBytes(inputStream: InputStream?, maxBytes: Int): ByteArray`
- Null input returns an empty byte array.
- Negative limits are rejected.
- Limit is enforced while reading; exact limit is accepted, limit + 1 is rejected.

- [ ] Add tests for exact-limit acceptance, over-limit rejection, null stream and zero-returning bulk reads.
- [ ] Run the unit test before implementation; confirm it fails because `readBytes` does not exist.
- [ ] Implement the minimal bounded byte reader in `ResponseBodyReader.kt`.
- [ ] Refactor `read` to decode the bounded byte result as UTF-8 without changing its public behavior.
- [ ] Run the focused test and the full unit-test suite for all five distributed variants.

### Task 2: Bound Storage responses

**Files:**
- Modify: `app/src/main/java/pt/studiomesa/app/SupabaseApi.kt`
- Modify: `app/src/test/java/pt/studiomesa/app/ResponseBodyReaderTest.kt` (only if a new reader edge case is discovered)

**Behavior:**
- Add a fixed 25 MiB maximum for successful Storage response bodies.
- Reject a successful response before reading when a positive Content-Length exceeds 25 MiB.
- Use bounded streaming for successful bodies, so a missing/false Content-Length cannot bypass the limit.
- Limit error bodies to 64 KiB before attempting JSON message extraction.
- Preserve existing `BackendHttpException` status/path and sanitized messages.

- [ ] Add a testable helper for the declared-length guard if the existing reader tests cannot cover it without Android networking.
- [ ] Implement the guard and bounded stream read.
- [ ] Verify the existing 25 MiB upload input validation and 25 MiB download result contract remain unchanged.
- [ ] Run all five variant unit-test tasks, Android Lint and the complete Android emulator smoke workflow.

### Task 3: Record release evidence

**Files:**
- Create: `docs/SECURITY-AUDIT-2026-11-10.md`
- Update: `docs/COMMERCIAL-RELEASE-GATE-2026-11-09.md` only by superseding it with a dated note, without marking external blockers complete.

- [ ] Record the current read-only Supabase audit, exact migration drift, RLS/Storage posture, CI evidence and external launch blockers.
- [ ] Verify every recorded status against the final commit and latest live queries.
- [ ] Do not run migrations, deploy functions, enable/disable Auth settings or upgrade the Free plan.

