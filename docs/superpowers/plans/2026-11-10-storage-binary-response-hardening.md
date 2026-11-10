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

- [x] Add tests for exact-limit acceptance, over-limit rejection, null stream and zero-returning bulk reads.
- [x] Run the unit test before implementation; CI confirmed compilation fails because `readBytes` and `requireDeclaredLengthWithinLimit` did not exist.
- [x] Implement the bounded byte reader in `ResponseBodyReader.kt`.
- [x] Refactor `read` to decode the bounded byte result as UTF-8.
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

- [x] Add and test a deterministic helper for the declared-length guard.
- [x] Implement the declared-length guard and bounded stream read in `SupabaseApi.kt`.
- [ ] Verify the existing 25 MiB upload input validation and 25 MiB download result contract remain unchanged.
- [ ] Run all five variant unit-test tasks, Android Lint and the complete Android emulator smoke workflow.

### Task 3: Record release evidence

**Files:**
- Create: `docs/SECURITY-AUDIT-2026-11-10.md`
- Update: `docs/COMMERCIAL-RELEASE-GATE-2026-11-09.md` only by superseding it with a dated note, without marking external blockers complete.

- [x] Record the read-only Supabase audit, migration drift, RLS/Storage posture and external launch blockers in the security and commercial release reports.
- [ ] Verify every recorded status against the final commit and latest live queries.
- [x] No live migrations, function deployments, Auth changes, Storage changes or plan upgrades were performed.



---

### Task 4: Bound JSON responses and paginate assistant history

**Files:**
- Modify: `app/src/main/java/pt/studiomesa/app/SupabaseApi.kt`
- Create: `app/src/main/java/pt/studiomesa/app/AssistantMessagePaginationPolicy.kt`
- Create: `app/src/test/java/pt/studiomesa/app/AssistantMessagePaginationPolicyTest.kt`
- Modify: `scripts/check_android_http_safety.py`
- Modify: `scripts/test_check_android_http_safety.py`

**Behavior:**
- Limit successful JSON API bodies to 4 MiB and error bodies to 64 KiB.
- Keep assistant message download pages to 100 rows, with stable `created_at,id` ordering.
- Stop at the existing local-history ceiling of 5,000 messages.
- Test full/short pages, cap handling, invalid offsets and the maximum serialized page budget.

- [x] Add the bounded pagination policy and deterministic unit tests.
- [x] Implement paginated history download in `SupabaseApi.listAssistantMessages`.
- [x] Apply separate success/error limits to both JSON request paths.
- [x] Add static guardrails for stable ordering, offsets, bounded JSON bodies and page limit.
- [ ] Run the complete unit, Lint, PostgreSQL authorization and Android emulator CI for the final branch commit.
