#!/usr/bin/env python3
"""Static security contract for the StudioMesa assistant Edge Function."""
from __future__ import annotations

import re
import sys
import tomllib
from pathlib import Path

EXPECTED_PROJECT_ID = "svwvkfsyevuyabelrtjk"


def audit_assistant_security(config_text: str, source: str) -> list[str]:
    errors: list[str] = []
    try:
        config = tomllib.loads(config_text)
    except tomllib.TOMLDecodeError as exc:
        return [f"supabase/config.toml is not valid TOML: {exc}"]

    if config.get("project_id") != EXPECTED_PROJECT_ID:
        errors.append("supabase/config.toml project_id does not match the StudioMesa project.")

    function_config = config.get("functions", {}).get("studiomesa-assistant")
    if not isinstance(function_config, dict) or function_config.get("enabled") is not True:
        errors.append("studiomesa-assistant must be explicitly enabled.")
    if not isinstance(function_config, dict) or function_config.get("verify_jwt") is not False:
        errors.append("studiomesa-assistant must use verify_jwt=false and validate the user JWT in withSupabase.")

    required = (
        ('import { withSupabase } from "npm:@supabase/server@1.9.2";', "a pinned Supabase server SDK import"),
        ('withSupabase({ auth: "user" }', "withSupabase user authentication"),
        ('Deno.env.get("OPENAI_API_KEY")', "an Edge Function environment secret for the OpenAI key"),
        ('"https://api.openai.com/v1/responses"', "the Responses API endpoint"),
        ('store: false', "disabled provider-side response storage"),
        ('{ type: "web_search", search_context_size: "medium" }', "the Responses API web_search tool"),
        ('.storage\n        .from(INVOICE_BUCKET)\n        .download(', "user-scoped download from the private invoice bucket"),
        ('"studiomesa_assistant_get_invoice_files"', "owner-scoped invoice metadata lookup"),
        ('"studiomesa_assistant_append_message"', "database-backed conversation history"),
    )
    for needle, description in required:
        if needle not in source:
            errors.append(f"The assistant function is missing {description}.")

    if re.search(r"ctx\.supabaseAdmin|SUPABASE_SERVICE_ROLE_KEY|sb_secret_|service_role", source, re.IGNORECASE):
        errors.append("The assistant function must not use an admin/service-role client or secret API key.")
    if re.search(r"console\.(?:log|info|debug)\s*\([^)]*(?:message|invoice|file_data|base64)", source, re.IGNORECASE):
        errors.append("The assistant function must not log user prompts or invoice content.")
    if not re.search(r"MAX_INVOICE_BYTES_EACH\s*=\s*10\s*\*\s*1024\s*\*\s*1024", source):
        errors.append("The assistant function must cap each attached invoice at 10 MiB.")
    if not re.search(r"MAX_INVOICE_FILES\s*=\s*3", source):
        errors.append("The assistant function must cap invoice attachments at three files.")
    if not re.search(r"MAX_HISTORY_MESSAGES\s*=\s*24", source):
        errors.append("The assistant function must bound the amount of conversation history sent to the model.")
    if "application/pdf" not in source:
        errors.append("The assistant function must restrict its financial-document reader to PDFs.")
    return errors


def audit_repository(root: Path) -> list[str]:
    config_path = root / "supabase" / "config.toml"
    source_path = root / "supabase" / "functions" / "studiomesa-assistant" / "index.ts"
    try:
        config = config_path.read_text(encoding="utf-8")
    except OSError as exc:
        return [f"{config_path.relative_to(root)}: cannot read file: {exc}"]
    try:
        source = source_path.read_text(encoding="utf-8")
    except OSError as exc:
        return [f"{source_path.relative_to(root)}: cannot read file: {exc}"]
    return audit_assistant_security(config, source)


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    errors = audit_repository(root)
    if errors:
        print("\n".join(f"ERROR: {error}" for error in errors), file=sys.stderr)
        return 1
    print("StudioMesa assistant Edge Function security contract passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
