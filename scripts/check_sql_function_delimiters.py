#!/usr/bin/env python3
"""Catch missing statement terminators on PostgreSQL $function$ bodies in migrations."""
from pathlib import Path
import re
import sys


def main() -> int:
    migration_dir = Path("supabase/migrations")
    migrations = sorted(migration_dir.glob("*.sql"))
    if not migrations:
        print("ERROR: no SQL migration files found.", file=sys.stderr)
        return 1

    errors: list[str] = []
    function_open = re.compile(r"\bAS\s+\$function\$\s*$", re.IGNORECASE)

    for path in migrations:
        inside_function = False
        for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
            line = raw_line.strip()
            if function_open.search(line):
                if inside_function:
                    errors.append(f"{path}:{line_number}: nested $function$ opening delimiter.")
                inside_function = True
                continue

            if "$function$" not in line:
                continue

            if not inside_function:
                errors.append(f"{path}:{line_number}: unexpected $function$ delimiter.")
            else:
                if line != "$function$;":
                    errors.append(
                        f"{path}:{line_number}: closing function delimiter must be exactly '$function$;'."
                    )
                inside_function = False

        if inside_function:
            errors.append(f"{path}: end of file reached before closing $function$ delimiter.")

    if errors:
        print("SQL migration delimiter check FAILED:")
        for error in errors:
            print(f" - {error}")
        return 1

    print(f"SQL migration delimiter check passed ({len(migrations)} files).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
