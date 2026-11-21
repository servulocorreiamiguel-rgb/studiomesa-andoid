#!/usr/bin/env python3
"""Guard the hosted Supabase password-recovery template contract.

This checks the versioned HTML only. It cannot verify that the Dashboard template
has been updated; that still requires a real email end-to-end test.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
TEMPLATE = ROOT / "supabase" / "templates" / "recovery.html"


def main() -> int:
    try:
        content = TEMPLATE.read_text(encoding="utf-8")
    except OSError as exc:
        print(f"Could not read recovery email template: {exc}", file=sys.stderr)
        return 2

    errors = []
    if content.count("{{ .Token }}") != 1:
        errors.append("Recovery email must contain exactly one {{ .Token }} OTP placeholder.")
    if "{{ .ConfirmationURL }}" in content or "{{ .TokenHash }}" in content:
        errors.append("OTP recovery email must not contain one-time confirmation links.")
    if "seis algarismos" not in content.lower():
        errors.append("Recovery email must tell users that the code has six digits.")
    if "<a " in content.lower() or "href=" in content.lower():
        errors.append("OTP recovery email must not include a clickable link.")

    if errors:
        print("Supabase recovery email template audit failed:")
        for error in errors:
            print(f" - {error}")
        return 1

    print("Supabase recovery email template audit passed: six-digit OTP, no confirmation link.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
