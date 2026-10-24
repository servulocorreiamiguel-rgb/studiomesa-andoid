#!/usr/bin/env python3
"""Guard Supabase gateway settings and the account-deletion authentication contract."""

from pathlib import Path
import re
import sys
import tomllib

ROOT = Path(__file__).resolve().parent.parent
CONFIG_PATH = ROOT / "supabase" / "config.toml"
DELETE_ACCOUNT_PATH = ROOT / "supabase" / "functions" / "delete-account" / "index.ts"
REQUIRED_FUNCTIONS = ("delete-account", "account-deletion-web")

AUTHENTICATED_HANDLER = re.compile(
    r"""withSupabase\s*\(\s*\{\s*auth\s*:\s*["']user["']\s*\}\s*,"""
)
VERIFIED_USER_ID = re.compile(
    r"""(?:const|let)\s+userId\s*=\s*ctx\.userClaims\?\.\s*sub\s*;"""
)
DELETE_CLAIMED_USER = re.compile(
    r"""ctx\.supabaseAdmin\.auth\.admin\.deleteUser\s*\(\s*userId\s*\)"""
)


def validate_configuration(config: dict, delete_account_source: str) -> list[str]:
    """Return failures for gateway settings or destructive-handler auth regressions."""
    functions = config.get("functions")
    if not isinstance(functions, dict):
        return ["Supabase config must define a [functions] table."]

    failures = [
        name
        for name in REQUIRED_FUNCTIONS
        if not isinstance(functions.get(name), dict)
        or functions[name].get("verify_jwt") is not False
    ]
    errors = []
    if failures:
        errors.append("Expected verify_jwt = false for: " + ", ".join(failures))

    if not AUTHENTICATED_HANDLER.search(delete_account_source):
        errors.append(
            'The delete-account handler must use withSupabase({ auth: "user" }) '
            "because gateway JWT verification is disabled for this endpoint."
        )
    if not VERIFIED_USER_ID.search(delete_account_source):
        errors.append(
            "The deletion target must come from the verified session subject (ctx.userClaims.sub)."
        )
    if not DELETE_CLAIMED_USER.search(delete_account_source):
        errors.append(
            "Account deletion must target the userId derived from the verified session."
        )
    return errors


def main() -> int:
    try:
        with CONFIG_PATH.open("rb") as config_file:
            config = tomllib.load(config_file)
        delete_account_source = DELETE_ACCOUNT_PATH.read_text(encoding="utf-8")
    except (OSError, tomllib.TOMLDecodeError) as exc:
        print(f"Cannot read valid Supabase config or delete-account source: {exc}", file=sys.stderr)
        return 1

    errors = validate_configuration(config, delete_account_source)
    if errors:
        print("Supabase function security check FAILED:")
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1

    print("Supabase gateway settings and delete-account session-auth checks are valid.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
