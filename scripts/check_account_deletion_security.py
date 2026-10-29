#!/usr/bin/env python3
"""Audit account-deletion Edge Function deployment and browser security settings."""
from __future__ import annotations

import re
import sys
import tomllib
from pathlib import Path

EXPECTED_PROJECT_ID = "svwvkfsyevuyabelrtjk"
EXACT_SUPABASE_JS_IMPORT = re.compile(
    r'https://cdn\.jsdelivr\.net/npm/@supabase/supabase-js@\d+\.\d+\.\d+/\+esm'
)


def audit_config_text(config_text: str, expected_project_id: str = EXPECTED_PROJECT_ID) -> list[str]:
    """Check function auth settings and ensure the config targets the expected project."""
    errors: list[str] = []
    try:
        config = tomllib.loads(config_text)
    except tomllib.TOMLDecodeError as exc:
        return [f"supabase/config.toml is not valid TOML: {exc}"]

    if config.get("project_id") != expected_project_id:
        errors.append("supabase/config.toml project_id does not match the repository's Supabase project.")

    functions = config.get("functions")
    if not isinstance(functions, dict):
        errors.append("supabase/config.toml must define the Edge Functions.")
        return errors

    delete_config = functions.get("delete-account")
    if not isinstance(delete_config, dict) or delete_config.get("enabled") is not True:
        errors.append("delete-account must be explicitly enabled in supabase/config.toml.")
    if not isinstance(delete_config, dict) or delete_config.get("verify_jwt") is not True:
        errors.append("delete-account must keep platform verify_jwt = true.")

    web_config = functions.get("account-deletion-web")
    if not isinstance(web_config, dict) or web_config.get("enabled") is not True:
        errors.append("account-deletion-web must be explicitly enabled in supabase/config.toml.")
    if not isinstance(web_config, dict) or web_config.get("verify_jwt") is not False:
        errors.append("account-deletion-web must keep verify_jwt = false so the public HTML page can load.")

    return errors


def audit_function_sources(delete_source: str, web_source: str) -> list[str]:
    """Check that the delete endpoint and public browser page retain their security boundaries."""
    errors: list[str] = []

    if not re.search(r'withSupabase\(\s*\{\s*auth\s*:\s*["\']user["\']\s*\}', delete_source):
        errors.append("delete-account must use withSupabase({ auth: \"user\" }) and RLS-scoped credentials.")
    if "ctx.supabaseAdmin.auth.admin.deleteUser(userId)" not in delete_source:
        errors.append("delete-account must delete only the verified user's account through the admin client.")
    if "typeof body.delete_restaurant_data !== \"boolean\"" not in delete_source:
        errors.append("delete-account must require an explicit boolean for restaurant-data deletion.")

    if not EXACT_SUPABASE_JS_IMPORT.search(web_source):
        errors.append("The public page must pin supabase-js to an exact x.y.z CDN version.")
    if not re.search(r'flowType\s*:\s*["\']pkce["\']', web_source):
        errors.append("The account deletion page must use the PKCE authentication flow.")
    if re.search(r'flowType\s*:\s*["\']implicit["\']', web_source):
        errors.append("The legacy implicit authentication flow must not be enabled on the account deletion page.")

    if '<script nonce="__STUDIOMESA_CSP_NONCE__" type="module">' not in web_source:
        errors.append("The inline module script must carry the per-response CSP nonce placeholder.")
    if "crypto.getRandomValues(new Uint8Array(18))" not in web_source:
        errors.append("The response CSP nonce must be generated from cryptographically secure randomness.")
    if 'PAGE.replace("__STUDIOMESA_CSP_NONCE__", nonce)' not in web_source:
        errors.append("The generated CSP nonce must be inserted into the served HTML.")
    required_csp_parts = (
        '"Content-Security-Policy": contentSecurityPolicy',
        '"default-src \'none\'"',
        '"base-uri \'none\'"',
        '"object-src \'none\'"',
        '"frame-ancestors \'none\'"',
        '"form-action \'self\'"',
        "script-src 'nonce-\u0024{nonce}' https://cdn.jsdelivr.net",
        '"X-Content-Type-Options": "nosniff"',
        '"Referrer-Policy": "no-referrer"',
        '"X-Frame-Options": "DENY"',
        '"Cache-Control": "no-store, max-age=0"',
    )
    for part in required_csp_parts:
        if part not in web_source:
            errors.append(f"The public page is missing a required security directive/header: {part}")

    if "const linkRequestResultMessage=" not in web_source or web_source.count("showStatus(linkRequestResultMessage)") < 2:
        errors.append("Account-deletion email requests must use the same generic status on success and failure to prevent email enumeration.")

    if "sb_secret_" in web_source or "service_role" in web_source:
        errors.append("The public page must not contain a secret/service-role API key.")
    return errors


def audit_repository(root: Path) -> list[str]:
    """Read and validate the repository's configuration and function sources."""
    config_path = root / "supabase" / "config.toml"
    delete_path = root / "supabase" / "functions" / "delete-account" / "index.ts"
    web_path = root / "supabase" / "functions" / "account-deletion-web" / "index.ts"
    errors: list[str] = []

    contents: dict[Path, str] = {}
    for path in (config_path, delete_path, web_path):
        try:
            contents[path] = path.read_text(encoding="utf-8")
        except OSError as exc:
            errors.append(f"{path.relative_to(root)}: cannot read file: {exc}")

    if any(path not in contents for path in (config_path, delete_path, web_path)):
        return errors

    errors.extend(audit_config_text(contents[config_path]))
    errors.extend(audit_function_sources(contents[delete_path], contents[web_path]))
    return errors


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    errors = audit_repository(root)
    if errors:
        print("\n".join(f"ERROR: {error}" for error in errors), file=sys.stderr)
        return 1
    print("Audit of account-deletion Edge Function security settings passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
