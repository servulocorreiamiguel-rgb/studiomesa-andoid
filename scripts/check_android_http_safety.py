#!/usr/bin/env python3
"""Static safety checks for the Android Supabase HTTP and private Storage client."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
API_CLIENT = ROOT / "app" / "src" / "main" / "java" / "pt" / "studiomesa" / "app" / "SupabaseApi.kt"
ANDROID_MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
FILE_PROVIDER_PATHS = ROOT / "app" / "src" / "main" / "res" / "xml" / "studiomesa_file_paths.xml"


def audit_http_client(source: str) -> list[str]:
    errors: list[str] = []

    required_fragments = (
        ("instanceFollowRedirects = false", "HTTP redirects must be disabled for authenticated API calls."),
        ("useCaches = false", "Authenticated API responses must not use the HttpURLConnection cache."),
        ("ResponseBodyReader.read(stream, responseLimitBytes)", "HTTP response bodies must use an explicit byte limit."),
        ("MAX_JSON_RESPONSE_BYTES = 4 * 1024 * 1024", "Successful JSON responses must retain the 4 MiB bound."),
        ("MAX_JSON_ERROR_BODY_BYTES = 64 * 1024", "JSON error bodies must be bounded to 64 KiB."),
        ("ApiErrorMessage.sanitize(message, code)", "HTTP error messages must be sanitized before display."),
        ("BackendHttpException(code, path,", "HTTP errors must preserve status/path metadata without exposing raw response bodies."),
        ("ResponseBodyReader.readBytes(stream, responseLimitBytes)", "Private Storage responses must use the bounded byte reader."),
        ("AssistantMessagePaginationPolicy.nextOffset(", "Assistant history must be downloaded in bounded pages."),
        ("&order=created_at.desc,id.desc&limit=$requestedLimit&offset=$offset", "Assistant history pages must use stable ordering and offsets."),
    )
    for fragment, message in required_fragments:
        if fragment not in source:
            errors.append(message)
    if source.count("ResponseBodyReader.read(stream, responseLimitBytes)") < 2:
        errors.append("Both authenticated JSON request paths must use the bounded response reader.")
    if source.count("if (success) MAX_JSON_RESPONSE_BYTES else MAX_JSON_ERROR_BODY_BYTES") < 2:
        errors.append("Both JSON request paths must use the success/error body limits.")

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

    storage_start = source.find("private fun binaryStorageRequest")
    storage_end = source.find("fun listAssistantConversations", storage_start)
    storage_source = source[storage_start:storage_end] if storage_start >= 0 and storage_end > storage_start else ""
    length_guard = storage_source.find("ResponseBodyReader.requireDeclaredLengthWithinLimit(")
    stream_open = storage_source.find("val stream = if (success) connection.inputStream else connection.errorStream")
    if not storage_source or length_guard < 0 or stream_open < 0 or length_guard > stream_open:
        errors.append("Storage Content-Length must be checked before opening the response stream.")

    if not re.search(
        r"MAX_INVOICE_DOCUMENT_BYTES\s*=\s*25\s*\*\s*1024\s*\*\s*1024",
        source,
    ):
        errors.append("Invoice document responses must retain the exact 25 MiB limit.")
    if not re.search(
        r"MAX_STORAGE_ERROR_BODY_BYTES\s*=\s*64\s*\*\s*1024",
        source,
    ):
        errors.append("Storage error body must be bounded to 64 KiB.")

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


def audit_android_manifest(manifest: str, file_paths: str) -> list[str]:
    errors: list[str] = []
    for fragment, message in (
        ('android:allowBackup="false"', "Android cloud/device backup must remain disabled for private app data."),
        ('android:usesCleartextTraffic="false"', "Android cleartext traffic must be disabled."),
        ('android:name="androidx.core.content.FileProvider"', "Invoice sharing must use Android FileProvider."),
    ):
        if fragment not in manifest:
            errors.append(message)

    provider_start = manifest.find('android:name="androidx.core.content.FileProvider"')
    provider_end = manifest.find("/>", provider_start) if provider_start >= 0 else -1
    provider_config = manifest[provider_start:provider_end] if provider_start >= 0 and provider_end > provider_start else ""
    if 'android:exported="false"' not in provider_config:
        errors.append("The invoice FileProvider must not be exported.")

    if '<cache-path name="invoice_cache" path="invoices/" />' not in file_paths:
        errors.append("The invoice FileProvider must remain limited to cache/invoices/.")
    if any(fragment in file_paths for fragment in ("<root-path", "<external-path", "<external-files-path", "<files-path")):
        errors.append("The invoice FileProvider must not expose broad or external filesystem paths.")
    return errors


def main() -> int:

    try:
        source = API_CLIENT.read_text(encoding="utf-8")
    except OSError as exc:
        print(f"Could not read Android API client: {exc}", file=sys.stderr)
        return 2

    errors = audit_http_client(source)
    try:
        manifest = ANDROID_MANIFEST.read_text(encoding="utf-8")
        provider_paths = FILE_PROVIDER_PATHS.read_text(encoding="utf-8")
    except OSError as exc:
        print(f"Could not read Android manifest/FileProvider paths: {exc}", file=sys.stderr)
        return 2
    errors.extend(audit_android_manifest(manifest, provider_paths))
    if errors:
        print("Android HTTP safety audit failed:")
        for error in errors:
            print(f" - {error}")
        return 1

    print(
        "Android HTTP safety audit passed: HTTPS, publishable client key, no redirects/cache, "
        "bounded JSON and binary responses, early Content-Length rejection, sanitized errors, disabled cleartext traffic and restricted invoice FileProvider."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
