#!/usr/bin/env python3
"""Guard PostgreSQL SECURITY DEFINER functions against unsafe migration changes.

This is a source-level guard, not a substitute for reviewing function logic or
running live authorization tests against separate accounts.
"""
from pathlib import Path
import re
import sys


FUNCTION_PATTERN = re.compile(
    r"""
    \bCREATE\s+(?:OR\s+REPLACE\s+)?FUNCTION\s+
    (?P<schema>"[^"]+"|[A-Za-z_][A-Za-z_0-9]*)\s*\.\s*
    (?P<name>"[^"]+"|[A-Za-z_][A-Za-z_0-9]*)\s*
    \(.*?\bAS\s+\$function\$
    (?P<body>.*?)
    \$function\$\s*;
    """,
    re.IGNORECASE | re.DOTALL | re.VERBOSE,
)
SECURITY_DEFINER = re.compile(r"\bSECURITY\s+DEFINER\b", re.IGNORECASE)
EMPTY_SEARCH_PATH = re.compile(
    r"\bSET\s+search_path\s+(?:TO|=)\s*''", re.IGNORECASE
)
AUTH_UID_CALL = re.compile(r"\bauth\s*\.\s*uid\s*\(\s*\)", re.IGNORECASE)


def scan_sql_text(source: str, source_name: str = "<memory>") -> tuple[int, list[str]]:
    """Return (number of SECURITY DEFINER functions, findings)."""
    count = 0
    errors: list[str] = []

    for match in FUNCTION_PATTERN.finditer(source):
        header_start = match.start()
        header = source[header_start : match.start("body")]
        body = match.group("body")
        if not SECURITY_DEFINER.search(header):
            continue

        count += 1
        schema = match.group("schema").strip('"').lower()
        name = match.group("name").strip('"')
        label = f"{source_name}: {schema}.{name}"

        if not EMPTY_SEARCH_PATH.search(header):
            errors.append(
                f"{label}: SECURITY DEFINER function must pin search_path to the empty path."
            )

        # All client-callable privileged functions in public must bind their
        # authorization to the server-verified Supabase caller identity.
        # Private-schema trigger/helper functions can have a separate rationale.
        if schema == "public" and not AUTH_UID_CALL.search(body):
            errors.append(
                f"{label}: public SECURITY DEFINER function must explicitly check auth.uid()."
            )

    return count, errors


def main() -> int:
    migration_dir = Path("supabase/migrations")
    migrations = sorted(migration_dir.glob("*.sql"))
    if not migrations:
        print("ERROR: no SQL migration files found.", file=sys.stderr)
        return 1

    total_functions = 0
    errors: list[str] = []
    for path in migrations:
        count, findings = scan_sql_text(path.read_text(encoding="utf-8"), str(path))
        total_functions += count
        errors.extend(findings)

    if total_functions == 0:
        print(
            "ERROR: no SECURITY DEFINER function definitions were inspected; "
            "the source pattern or migration layout may have changed.",
            file=sys.stderr,
        )
        return 1

    if errors:
        print("SQL SECURITY DEFINER check FAILED:")
        for error in errors:
            print(f" - {error}")
        return 1

    print(
        "SQL SECURITY DEFINER check passed "
        f"({total_functions} privileged function definitions inspected across {len(migrations)} migrations)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
