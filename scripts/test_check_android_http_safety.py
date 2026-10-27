#!/usr/bin/env python3
"""Regression tests for Android HTTP transport safety guards."""
from __future__ import annotations

import unittest

from check_android_http_safety import audit_http_client


SAFE_CLIENT = """
instanceFollowRedirects = false
useCaches = false
val text = ResponseBodyReader.read(stream)
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
            "ResponseBodyReader.read(stream)",
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


if __name__ == "__main__":
    unittest.main()
