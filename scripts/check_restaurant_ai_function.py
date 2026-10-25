#!/usr/bin/env python3
"""Static release guard for the optional restaurant AI Edge Function."""
from pathlib import Path
import re
import sys
import tomllib

ROOT = Path(__file__).resolve().parent.parent
CONFIG_PATH = ROOT / "supabase" / "config.toml"
FUNCTION_PATH = ROOT / "supabase" / "functions" / "restaurant-ai-assistant" / "index.ts"
MIGRATION_PATH = ROOT / "supabase" / "migrations" / "20261025230000_restaurant_ai_quota.sql"
MAIN_ACTIVITY_PATH = ROOT / "app" / "src" / "main" / "java" / "pt" / "studiomesa" / "app" / "MainActivity.kt"
SUPABASE_API_PATH = ROOT / "app" / "src" / "main" / "java" / "pt" / "studiomesa" / "app" / "SupabaseApi.kt"

def validate_ai_function(config: dict, source: str, migration: str, main_activity: str, api_source: str) -> list[str]:
    errors: list[str] = []
    functions = config.get("functions")
    ai_config = functions.get("restaurant-ai-assistant") if isinstance(functions, dict) else None
    if not isinstance(ai_config, dict) or ai_config.get("verify_jwt") is not True:
        errors.append("The restaurant AI function must set verify_jwt = true.")

    required_source_patterns = [
        (r'withSupabase\s*\(\s*\{\s*auth\s*:\s*["\']user["\']\s*\}', "must verify a real user session"),
        (r'Deno\.env\.get\(["\']OPENAI_API_KEY["\']\)', "must read the provider key from a server secret"),
        (r'Deno\.env\.get\(["\']STUDIOMESA_AI_ENABLED["\']\)', "must require the explicit server-side feature flag"),
        (r'\.rpc\(["\']get_my_restaurant_details["\']\)', "must resolve the restaurant through the caller-scoped RPC"),
        (r'user_role\s*!==\s*["\']owner["\']', "must restrict use to an authorized owner"),
        (r'\.rpc\(["\']consume_restaurant_ai_quota["\']\)', "must enforce a server-side usage quota"),
        (r'AbortSignal\.timeout', "must set an upstream timeout"),
        (r'MAX_BODY_BYTES', "must bound request size"),
        (r'MAX_OUTPUT_CHARS', "must bound generated output"),
        (r'review_required:\s*true', "must mark the output as requiring human review"),
        (r'containsCustomerContactData', "must reject contact details in user input"),
        (r'Never invent ingredients', "must prohibit invented restaurant facts"),
    ]
    for pattern, description in required_source_patterns:
        if not re.search(pattern, source, re.IGNORECASE):
            errors.append("The restaurant AI function " + description + ".")

    if re.search(r'\b(?:console\.log|console\.debug)\s*\(', source):
        errors.append("The restaurant AI function must not log request content or generated drafts.")
    if re.search(r'sk-[A-Za-z0-9]{12,}', source):
        errors.append("A provider secret appears to be hard-coded in the function source.")

    required_migration_patterns = [
        (r'CREATE TABLE IF NOT EXISTS private\.restaurant_ai_usage', "must create the private AI usage table"),
        (r'CREATE OR REPLACE FUNCTION public\.consume_restaurant_ai_quota', "must define the quota RPC"),
        (r"SET search_path TO ''", "must pin the privileged function search_path"),
        (r'auth\.uid\s*\(\s*\)', "must bind quota access to the authenticated user"),
        (r"rs\.role\s*=\s*'owner'", "must check owner authorization in the database"),
        (r'v_daily_limit\s+CONSTANT\s+integer\s*:=\s*10', "must enforce a fixed daily quota"),
        (r'REVOKE ALL ON FUNCTION public\.consume_restaurant_ai_quota\(\) FROM PUBLIC, anon', "must deny public and anonymous quota execution"),
        (r'GRANT EXECUTE ON FUNCTION public\.consume_restaurant_ai_quota\(\) TO authenticated', "must grant quota execution only to authenticated callers"),
        (r'REVOKE ALL ON TABLE private\.restaurant_ai_usage FROM PUBLIC, anon, authenticated', "must deny direct client access to quota records"),
    ]
    for pattern, description in required_migration_patterns:
        if not re.search(pattern, migration, re.IGNORECASE):
            errors.append("The AI quota migration " + description + ".")

    if not re.search(r'if\s*\(isPremium\)\s*\{[^}]*pages\.add\(["\']Conteúdo IA["\']\)', main_activity, re.DOTALL):
        errors.append("The AI content page must be limited to the Premium management variant.")
    if 'SupabaseApi.restaurantAiStatus' not in main_activity or 'SupabaseApi.generateRestaurantContent' not in main_activity:
        errors.append("The Android page must use the authenticated AI status and generation methods.")
    if '/functions/v1/restaurant-ai-assistant' not in api_source:
        errors.append("The Android API must call the server function endpoint.")
    if re.search(r'OPENAI_API_KEY|sk-[A-Za-z0-9]{12,}', api_source, re.IGNORECASE):
        errors.append("The Android client must never contain the provider API key.")

    return errors

def main() -> int:
    try:
        with CONFIG_PATH.open("rb") as stream:
            config = tomllib.load(stream)
        source = FUNCTION_PATH.read_text(encoding="utf-8")
        migration = MIGRATION_PATH.read_text(encoding="utf-8")
        main_activity = MAIN_ACTIVITY_PATH.read_text(encoding="utf-8")
        api_source = SUPABASE_API_PATH.read_text(encoding="utf-8")
    except (OSError, tomllib.TOMLDecodeError) as exc:
        print("Cannot read AI function configuration/source: " + str(exc), file=sys.stderr)
        return 1

    errors = validate_ai_function(config, source, migration, main_activity, api_source)
    if errors:
        print("Restaurant AI security guard FAILED:")
        for error in errors:
            print(" - " + error, file=sys.stderr)
        return 1
    print("Restaurant AI guard passed: authenticated owner, server secret gate, quota, bounds, privacy guard, and review-only output.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
