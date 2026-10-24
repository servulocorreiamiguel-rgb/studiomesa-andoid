#!/usr/bin/env python3
"""Guard the intended Supabase Edge Function gateway-auth configuration."""

from pathlib import Path
import sys
import tomllib

ROOT = Path(__file__).resolve().parent.parent
CONFIG_PATH = ROOT / "supabase" / "config.toml"
REQUIRED_FUNCTIONS = ("delete-account", "account-deletion-web")

def main() -> int:
    try:
        with CONFIG_PATH.open("rb") as config_file:
            config = tomllib.load(config_file)
    except (OSError, tomllib.TOMLDecodeError) as exc:
        print(f"Cannot read valid Supabase config at {CONFIG_PATH}: {exc}", file=sys.stderr)
        return 1

    functions = config.get("functions")
    if not isinstance(functions, dict):
        print("Supabase config must define a [functions] table.", file=sys.stderr)
        return 1

    failures = [
        name
        for name in REQUIRED_FUNCTIONS
        if not isinstance(functions.get(name), dict)
        or functions[name].get("verify_jwt") is not False
    ]
    if failures:
        print("Expected verify_jwt = false for: " + ", ".join(failures), file=sys.stderr)
        return 1

    print("Supabase Edge Function gateway settings are valid.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
