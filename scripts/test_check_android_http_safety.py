#!/usr/bin/env python3
"""Regression tests for Android HTTP transport safety guards."""
from __future__ import annotations

import unittest

from check_android_http_safety import audit_http_client


SAFE_CLIENT = """
private const val BASE_URL = "https://example.supabase.co"
private const val API_KEY = "sb_publishable_test_key"
private const val MAX_JSON_RESPONSE_BYTES = 4 * 1024 * 1024
private const val MAX_JSON_ERROR_BODY_BYTES = 64 * 1024
private const val MAX_INVOICE_DOCUMENT_BYTES = 25 * 1024 * 1024
private const val MAX_STORAGE_ERROR_BODY_BYTES = 64 * 1024
instanceFollowRedirects = false
useCaches = false
val responseLimitBytes = if (success) MAX_JSON_RESPONSE_BYTES else MAX_JSON_ERROR_BODY_BYTES
val textResponseLimitBytes = if (success) MAX_JSON_RESPONSE_BYTES else MAX_JSON_ERROR_BODY_BYTES
val text = ResponseBodyReader.read(stream, textResponseLimitBytes)
val responseLimitBytes = if (success) MAX_JSON_RESPONSE_BYTES else MAX_JSON_ERROR_BODY_BYTES
val response = ResponseBodyReader.read(stream, responseLimitBytes)
val responseLimitBytes = if (success) MAX_INVOICE_DOCUMENT_BYTES else MAX_STORAGE_ERROR_BODY_BYTES
require(bytes.size in 1..MAX_INVOICE_DOCUMENT_BYTES)
ResponseBodyReader.requireDeclaredLengthWithinLimit(connection.contentLengthLong, responseLimitBytes)
val bytes = ResponseBodyReader.readBytes(stream, responseLimitBytes)
AssistantMessagePaginationPolicy.nextOffset(currentOffset = offset, requestedLimit = limit, returnedRows = page.length(), totalRows = combined.length())
val historyQuery = "&order=created_at.asc,id.asc&limit=$requestedLimit&offset=$offset"
throw BackendHttpException(code, path, ApiErrorMessage.sanitize(message, code))
"""


class AndroidHttpSafetyAuditTests(unittest.TestCase):
    def test_accepts_a_hardened_http_client(self) -> None:
        self.assertEqual([], audit_http_client(SAFE_CLIENT))

    def test_rejects_redirects(self) -> None:
        changed = SAFE_CLIENT.replace("instanceFollowRedirects = false", "instanceFollowRedirects = true")
        self.assertTrue(any("redirects must be disabled" in error for error in audit_http_client(changed)))

    def test_rejects_http_caching(self) -> None:
        changed = SAFE_CLIENT.replace("useCaches = false", "useCaches = true")
        self.assertTrue(any("must not use" in error for error in audit_http_client(changed)))

    def test_rejects_unbounded_response_reads(self) -> None:
        changed = SAFE_CLIENT.replace(
            "ResponseBodyReader.read(stream, responseLimitBytes)",
            "stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()",
        )
        errors = audit_http_client(changed)
        self.assertTrue(any("byte limit" in error for error in errors))
        self.assertTrue(any("Unbounded HTTP response reading" in error for error in errors))

    def test_rejects_unsanitized_http_errors(self) -> None:
        changed = SAFE_CLIENT.replace(
            "ApiErrorMessage.sanitize(message, code)",
            "message",
        )
        self.assertTrue(any("sanitized" in error for error in audit_http_client(changed)))

    def test_requires_http_error_metadata(self) -> None:
        changed = SAFE_CLIENT.replace("BackendHttpException(code, path,", "IllegalStateException(")
        self.assertTrue(any("status/path metadata" in error for error in audit_http_client(changed)))


    def test_rejects_unbounded_binary_storage_response_reads(self) -> None:
        changed = SAFE_CLIENT.replace(
            "ResponseBodyReader.readBytes(stream, responseLimitBytes)",
            "stream?.use { it.readBytes() } ?: ByteArray(0)",
        )
        errors = audit_http_client(changed)
        self.assertTrue(any("bounded byte reader" in error for error in errors))
        self.assertTrue(any("Unbounded binary response reading" in error for error in errors))

    def test_requires_content_length_early_rejection_for_storage_responses(self) -> None:
        changed = SAFE_CLIENT.replace(
            "ResponseBodyReader.requireDeclaredLengthWithinLimit(connection.contentLengthLong, responseLimitBytes)",
            "val ignoredLength = connection.contentLengthLong",
        )
        self.assertTrue(any("Content-Length" in error for error in audit_http_client(changed)))

    def test_requires_fixed_25_mib_invoice_response_limit(self) -> None:
        changed = SAFE_CLIENT.replace(
            "MAX_INVOICE_DOCUMENT_BYTES = 25 * 1024 * 1024",
            "MAX_INVOICE_DOCUMENT_BYTES = 100 * 1024 * 1024",
        )
        self.assertTrue(any("25 MiB" in error for error in audit_http_client(changed)))

    def test_requires_bounded_storage_error_body(self) -> None:
        changed = SAFE_CLIENT.replace(
            "MAX_STORAGE_ERROR_BODY_BYTES = 64 * 1024",
            "MAX_STORAGE_ERROR_BODY_BYTES = 1024 * 1024",
        )
        self.assertTrue(any("error body" in error.lower() for error in audit_http_client(changed)))


    def test_rejects_non_https_backend_endpoint(self) -> None:
        changed = SAFE_CLIENT.replace(
            'BASE_URL = "https://example.supabase.co"',
            'BASE_URL = "http://example.supabase.co"',
        )
        self.assertTrue(any("HTTPS" in error for error in audit_http_client(changed)))

    def test_rejects_privileged_backend_key_in_android_client(self) -> None:
        changed = SAFE_CLIENT.replace(
            'API_KEY = "sb_publishable_test_key"',
            'API_KEY = "sb_secret_do_not_ship"',
        )
        self.assertTrue(any("publishable" in error.lower() for error in audit_http_client(changed)))


    def test_rejects_unpaginated_assistant_message_history(self) -> None:
        changed = SAFE_CLIENT.replace(
            "AssistantMessagePaginationPolicy.nextOffset(currentOffset = offset, requestedLimit = limit, returnedRows = page.length(), totalRows = combined.length())",
            "return JSONArray(call(\"GET\", \"/restaurant_assistant_messages?limit=5000\"))",
        )
        self.assertTrue(any("bounded pages" in error for error in audit_http_client(changed)))

    def test_requires_stable_order_and_offset_for_assistant_history_pages(self) -> None:
        changed = SAFE_CLIENT.replace(
            "&order=created_at.asc,id.asc&limit=$requestedLimit&offset=$offset",
            "&order=created_at.asc&limit=5000",
        )
        self.assertTrue(any("stable ordering" in error for error in audit_http_client(changed)))


    def test_requires_bounded_json_success_and_error_limits(self) -> None:
        changed = SAFE_CLIENT.replace(
            "MAX_JSON_ERROR_BODY_BYTES = 64 * 1024",
            "MAX_JSON_ERROR_BODY_BYTES = 1024 * 1024",
        )
        self.assertTrue(any("JSON error bodies" in error for error in audit_http_client(changed)))

    def test_requires_bounded_response_reader_in_both_json_request_paths(self) -> None:
        changed = SAFE_CLIENT.replace(
            "val response = ResponseBodyReader.read(stream, responseLimitBytes)",
            "val response = stream.bufferedReader().readText()",
        )
        self.assertTrue(any("Both authenticated JSON request paths" in error for error in audit_http_client(changed)))


if __name__ == "__main__":
    unittest.main()
