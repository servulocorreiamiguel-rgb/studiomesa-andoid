#!/usr/bin/env python3
"""Static release guard for the local StudioMesa engine and optional remote AI endpoint."""
from pathlib import Path
import re
import sys
import tomllib

ROOT = Path(__file__).resolve().parent.parent
CONFIG_PATH = ROOT / "supabase" / "config.toml"
FUNCTION_PATH = ROOT / "supabase" / "functions" / "restaurant-ai-assistant" / "index.ts"
VALIDATION_PATH = ROOT / "supabase" / "functions" / "restaurant-ai-assistant" / "validation.ts"
MIGRATION_PATH = ROOT / "supabase" / "migrations" / "20261025230000_restaurant_ai_quota.sql"
MAIN_ACTIVITY_PATH = ROOT / "app" / "src" / "main" / "java" / "pt" / "studiomesa" / "app" / "MainActivity.kt"
LOCAL_ENGINE_PATH = ROOT / "app" / "src" / "main" / "java" / "pt" / "studiomesa" / "app" / "RestaurantContentEngine.kt"
SUPABASE_API_PATH = ROOT / "app" / "src" / "main" / "java" / "pt" / "studiomesa" / "app" / "SupabaseApi.kt"

def validate_ai_function(
    config: dict,
    source: str,
    validation_source: str,
    migration: str,
    main_activity: str,
    api_source: str,
    local_engine_source: str = "",
) -> list[str]:
    errors: list[str] = []
    functions = config.get("functions")
    ai_config = functions.get("restaurant-ai-assistant") if isinstance(functions, dict) else None
    if not isinstance(ai_config, dict) or ai_config.get("verify_jwt") is not True:
        errors.append("The restaurant AI function must set verify_jwt = true.")

    required_source_patterns = [
        (r'withSupabase\s*\(\s*\{\s*auth\s*:\s*["\']user["\']\s*\}', "must verify a real user session"),
        (r'Deno\.env\.get\(["\']OPENAI_API_KEY["\']\)', "must read the provider key from a server secret"),
        (r'Deno\.env\.get\(["\']STUDIOMESA_AI_ENABLED["\']\)', "must require the explicit server-side feature flag"),
        (r'\.rpc\(\s*["\']get_my_restaurant_details["\']\s*,?\s*\)', "must resolve the restaurant through the caller-scoped RPC"),
        (r'user_role\s*!==\s*["\']owner["\']', "must restrict use to an authorized owner"),
        (r'\.rpc\(["\']consume_restaurant_ai_quota["\']\)', "must enforce a server-side usage quota"),
        (r'AbortSignal\.timeout', "must set an upstream timeout"),
        (r'MAX_BODY_BYTES', "must bound request size"),
        (r'validateRequestPayload', "must call the shared request validator"),
        (r'isBodyWithinLimit', "must bound actual UTF-8 request size"),
        (r'readBoundedUtf8Body\(req\.body', "must cap incoming bodies while streaming"),
        (r'readBoundedUtf8Body\(providerResponse\.body,\s*64\s*\*\s*1024\)', "must cap provider response bodies while streaming"),
        (r'validateGeneratedDraft', "must validate model output"),
        (r'review_required:\s*true', "must mark the output as requiring human review"),
        (r'Nunca inventes ingredientes', "must prohibit invented restaurant facts"),
    ]
    for pattern, description in required_source_patterns:
        if not re.search(pattern, source, re.IGNORECASE):
            errors.append("The restaurant AI function " + description + ".")

    required_validation_patterns = [
        (r'MAX_BODY_BYTES\s*=\s*4096', "must cap request bodies at 4096 bytes"),
        (r'readBoundedUtf8Body', "must include bounded stream reading"),
        (r'const reader = stream\.getReader\(\)', "must consume a body stream incrementally"),
        (r'await reader\.cancel', "must cancel an oversized body stream"),
        (r'MAX_TITLE_CHARS\s*=\s*120', "must cap the title field"),
        (r'MAX_FACTS_CHARS\s*=\s*1500', "must cap the facts field"),
        (r'MAX_OUTPUT_CHARS\s*=\s*4000', "must cap generated text"),
        (r'containsCustomerContactData', "must check for customer contact data"),
        (r'EMAIL_PATTERN', "must reject email addresses in prompt text"),
        (r'PORTUGAL_PHONE_PATTERN', "must reject Portuguese phone patterns in prompt text"),
        (r'!\["content_type",\s*"title",\s*"facts"\]\.includes\(key\)', "must reject unapproved request properties"),
        (r'validateGeneratedDraft', "must reject empty or oversized model responses"),
    ]
    for pattern, description in required_validation_patterns:
        if not re.search(pattern, validation_source, re.IGNORECASE):
            errors.append("The AI request validation " + description + ".")

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

    if not re.search(r'if\s*\(isPremium\s*&&\s*!isCustomerApp\)\s*\{[^}]*pages\.add\(["\']Conteúdo IA["\']\)', main_activity, re.DOTALL):
        errors.append("The AI content page must be limited to the Premium management variant.")
    if "RestaurantContentEngine.generate" not in main_activity:
        errors.append("The Android page must use the local StudioMesa content engine.")
    if "SupabaseApi.restaurantAiStatus" in main_activity or "SupabaseApi.generateRestaurantContent" in main_activity:
        errors.append("The local StudioMesa assistant must not call the remote provider endpoint.")
    if "O texto é processado no telemóvel e não é enviado para um fornecedor externo de IA." not in main_activity:
        errors.append("The Android UI must disclose that the first version processes text locally.")

    required_engine_patterns = [
        (r'object\s+RestaurantContentEngine', "must declare the local content engine"),
        (r'fun\s+generate\s*\(', "must implement a generation entry point"),
        (r'containsCustomerContactData', "must detect common customer contact details"),
        (r'emailPattern', "must detect common email patterns"),
        (r'portuguesePhonePattern', "must detect common Portuguese phone patterns"),
        (r'cleanTitle\.length\s+in\s+2\.\.120', "must bound title length"),
        (r'cleanFacts\.length\s+in\s+10\.\.1500', "must bound facts length"),
        (r'toByteArray\(Charsets\.UTF_8\)', "must bound local UTF-8 input size"),
        (r'content_ideas', "must support the content-ideas mode"),
    ]
    for pattern, description in required_engine_patterns:
        if not re.search(pattern, local_engine_source, re.IGNORECASE):
            errors.append("The local StudioMesa content engine " + description + ".")
    if re.search(r'HttpURLConnection|OkHttpClient|java\.net\.URL|ktor\.client|https?://', local_engine_source, re.IGNORECASE):
        errors.append("The local StudioMesa content engine must not make network requests.")
    if "/functions/v1/restaurant-ai-assistant" not in api_source:
        errors.append("The Android API must call the server function endpoint.")
    if re.search(r"OPENAI_API_KEY|sk-[A-Za-z0-9]{12,}", api_source, re.IGNORECASE):
        errors.append("The Android client must never contain the provider API key.")

    return errors

def main() -> int:
    try:
        with CONFIG_PATH.open("rb") as stream:
            config = tomllib.load(stream)
        source = FUNCTION_PATH.read_text(encoding="utf-8")
        validation_source = VALIDATION_PATH.read_text(encoding="utf-8")
        migration = MIGRATION_PATH.read_text(encoding="utf-8")
        main_activity = MAIN_ACTIVITY_PATH.read_text(encoding="utf-8")
        local_engine_source = LOCAL_ENGINE_PATH.read_text(encoding="utf-8")
        api_source = SUPABASE_API_PATH.read_text(encoding="utf-8")
    except (OSError, tomllib.TOMLDecodeError) as exc:
        print("Cannot read AI function configuration/source: " + str(exc), file=sys.stderr)
        return 1

    errors = validate_ai_function(config, source, validation_source, migration, main_activity, api_source, local_engine_source)
    if errors:
        print("StudioMesa AI security guard FAILED:")
        for error in errors:
            print(" - " + error, file=sys.stderr)
        return 1
    print("StudioMesa guards passed: optional server AI is gated; local engine is bounded, privacy-aware, and network-free.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
