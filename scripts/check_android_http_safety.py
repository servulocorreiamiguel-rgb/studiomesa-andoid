#!/usr/bin/env python3
"""Static safety checks for the Android Supabase HTTP and private Storage client."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
API_CLIENT = ROOT / "app" / "src" / "main" / "java" / "pt" / "studiomesa" / "app" / "SupabaseApi.kt"


def audit_http_client(source: str) -> list[str]:
    errors: list[str] = []

    required_fragments = (
        ("instanceFollowRedirects = false", "HTTP redirects must be disabled for authenticated API calls."),
        ("useCaches = false", "Authenticated API responses must not use the HttpURLConnection cache."),
        ("ResponseBodyReader.read(stream)", "HTTP response bodies must be read with a byte limit."),
        ("ApiErrorMessage.sanitize(message, code)", "HTTP error messages must be sanitized before display."),
        ("BackendHttpException(code, path,", "HTTP errors must preserve status/path metadata without exposing raw response bodies."),
        ("ResponseBodyReader.readBytes(stream, responseLimitBytes)", "Private Storage responses must use the bounded byte reader."),
    )
    for fragment, message in required_fragments:
        if fragment not in source:
            errors.append(message)

    if "stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()" in source:
        errors.append("Unbounded HTTP response reading is forbidden; use ResponseBodyReader.")

    if re.search(
        r"\bstream\s*\??\s*\.use\s*\{\s*it\.readBytes\s*\(\s*\)\s*\}",
        source,
    ):
        errors.append("Unbounded binary response reading is forbidden; use ResponseBodyReader.readBytes.")

    if not re.search(
        r"ResponseBodyReader\.requireDeclaredLengthWithinLimit\s*"
        r"\(\s*connection\.contentLengthLong\s*,\s*responseLimitBytes\s*\)",
        source,
        re.DOTALL,
    ):
        errors.append("Storage responses must reject an excessive Content-Length before reading the body.")

    if not re.search(
        r"MAX_INVOICE_DOCUMENT_BYTES\s*=\s*25\s*\*\s*1024\s*\*\s*1024",
        source,
    ):
        errors.append("Invoice document responses must retain the exact 25 MiB limit.")
    if not re.search(
        r"MAX_STORAGE_ERROR_BODY_BYTES\s*=\s*64\s*\*\s*1024",
        source,
    ):
        errors.append("Storage error bodies must be bounded to 64 KiB.")

    if not re.search(r'\bBASE_URL\s*=\s*"https://[^"]+"', source):
        errors.append("The Supabase API base URL must use HTTPS.")

    api_key = re.search(r'\bAPI_KEY\s*=\s*"([^"]+)"', source)
    if (
        api_key is None
        or not api_key.group(1).startswith("sb_publishable_")
        or re.search(r"(?:sb_secret_|service[_-]?role)", api_key.group(1), re.IGNORECASE)
    ):
        errors.append("The Android client may contain only a Supabase publishable key, never a privileged server key.")

    return errors


def main() -> int:
    try:
        source = API_CLIENT.read_text(encoding="utf-8")
    except OSError as exc:
        print(f"Could not read Android API client: {exc}", file=sys.stderr)
        return 2

    errors = audit_http_client(source)
    if errors:
        print("Android HTTP safety audit failed:")
        for error in errors:
            print(f" - {error}")
        return 1

    print(
        "Android HTTP safety audit passed: HTTPS, publishable client key, no redirects/cache, "
        "bounded JSON and binary responses, early Content-Length rejection and sanitized errors."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
