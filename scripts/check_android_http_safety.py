#!/usr/bin/env python3
"""Static safety checks for the Android Supabase HTTP client."""
from __future__ import annotations

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
    )
    for fragment, message in required_fragments:
        if fragment not in source:
            errors.append(message)

    if "stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()" in source:
        errors.append("Unbounded HTTP response reading is forbidden; use ResponseBodyReader.")

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

    print("Android HTTP safety audit passed: no redirects, no cache, bounded responses, sanitized errors.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
