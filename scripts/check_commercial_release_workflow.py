#!/usr/bin/env python3
"""Static checks for the privileged, manually dispatched commercial release workflow."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github" / "workflows" / "commercial-release.yml"


def audit_workflow(text: str) -> list[str]:
    errors: list[str] = []

    if not re.search(r"(?m)^  workflow_dispatch:\s*$", text):
        errors.append("Commercial release must require a manual workflow_dispatch trigger.")
    if re.search(r"(?m)^  (?:push|pull_request|schedule|workflow_call):\s*$", text):
        errors.append("Commercial signing workflow must not be triggered automatically.")

    confirmation_step = re.search(
        r"(?ms)^      - name: Require explicit confirmation\s*\n(.*?)(?=^      - name: |\Z)",
        text,
    )
    if confirmation_step is not None:
        body = confirmation_step.group(1)
        if not re.search(r"(?m)^\s*CONFIRM: \$\{\{ inputs\.confirm \}\}\s*$", body):
            errors.append("The release confirmation must read the workflow_dispatch confirm input.")
        if 'if [ "$CONFIRM" != "BUILD" ]' not in body:
            errors.append("The release workflow must require the exact confirmation value BUILD.")
        if not re.search(r"(?m)^\s*exit 1\s*$", body):
            errors.append("The release workflow must stop when the confirmation value is not BUILD.")

    def step_position(name: str) -> int | None:
        match = re.search(rf"(?m)^      - name: {re.escape(name)}\s*$", text)
        return match.start() if match else None

    required_order = (
        "Require explicit confirmation",
        "Require the reviewed main branch",
        "Check out source",
        "Validate signing secrets and keystore",
    )
    positions = [step_position(name) for name in required_order]
    if any(position is None for position in positions):
        missing = [
            name for name, position in zip(required_order, positions) if position is None
        ]
        errors.append("Missing required release safeguard step(s): " + ", ".join(missing) + ".")
    elif positions != sorted(positions):
        errors.append(
            "Release safeguards must run in order: confirmation, main-branch guard, checkout, secret validation."
        )

    branch_step = re.search(
        r"(?ms)^      - name: Require the reviewed main branch\s*\n(.*?)(?=^      - name: |\Z)",
        text,
    )
    if branch_step is not None:
        body = branch_step.group(1)
        if not re.search(r"(?m)^\s*REF: \$\{\{ github\.ref \}\}\s*$", body):
            errors.append("The release branch guard must read github.ref through an environment variable.")
        if 'if [ "$REF" != "refs/heads/main" ]' not in body:
            errors.append("The release branch guard must reject every ref except refs/heads/main.")
        if not re.search(r"(?m)^\s*exit 1\s*$", body):
            errors.append("The release branch guard must stop the workflow when the selected ref is not main.")

    # These strings would expose the base64 keystore or passwords if used in shell output.
    unsafe_echo_patterns = (
        r"(?im)^\s*echo\s+.*ANDROID_KEYSTORE_BASE64",
        r"(?im)^\s*echo\s+.*ANDROID_KEYSTORE_PASSWORD",
        r"(?im)^\s*echo\s+.*ANDROID_KEY_PASSWORD",
    )
    for pattern in unsafe_echo_patterns:
        if re.search(pattern, text):
            errors.append("Do not print signing secrets to workflow logs.")
            break

    if 'verification_output="$(jarsigner -verify "$bundle" 2>&1)"' not in text:
        errors.append("The commercial workflow must capture jarsigner verification output.")
    if 'grep -Fq "jar verified."' not in text:
        errors.append("The commercial workflow must explicitly reject AABs without the 'jar verified.' result.")
    release_apk_tasks = (
        "assembleEssencialRestauranteRelease",
        "assembleProfissionalRestauranteRelease",
        "assembleProfissionalClienteRelease",
        "assemblePremiumRestauranteRelease",
        "assemblePremiumClienteRelease",
    )
    if not all(task in text for task in release_apk_tasks):
        errors.append("The commercial workflow must build all five signed release APK variants.")
    if "mapfile -d '' apks < <(find app/build/outputs/apk -type f -path '*/release/*.apk' -print0)" not in text:
        errors.append("The commercial workflow must enumerate the release APKs before uploading them.")
    if '"$APKSIGNER" verify --verbose "$apk"' not in text:
        errors.append("The commercial workflow must verify each release APK with apksigner.")
    if "app/build/outputs/apk/**/release/*.apk" not in text:
        errors.append("The commercial artifact must include the signed APKs for direct installation.")

    return errors


def audit_ci_test_bundle(text: str) -> list[str]:
    """Require CI to prove the temporary commercial test AAB is actually signed."""
    errors: list[str] = []
    if 'verification_output="$(jarsigner -verify "$AAB_PATH" 2>&1)"' not in text:
        errors.append("Android CI must run jarsigner verification against its temporary release AAB.")
    if 'grep -Fq "jar verified."' not in text:
        errors.append("Android CI must require the explicit 'jar verified.' result for its test AAB.")
    if "assembleEssencialRestauranteRelease" not in text:
        errors.append("Android CI must build a temporary signed release APK.")
    if 'apksigner" verify --verbose "$APK_PATH"' not in text:
        errors.append("Android CI must verify the temporary signed APK with apksigner.")
    if "name: StudioMesa-1.5.0-Demo-APKs" not in text:
        errors.append("Android CI must upload the named demo APK artifact.")
    if "path: app/build/outputs/apk/**/debug/*.apk" not in text:
        errors.append("Android CI must upload only debug APKs to the demo artifact.")
    if "path: app/build/outputs/apk/**/*.apk" in text:
        errors.append("The demo artifact must exclude release APKs signed with the temporary CI key.")
    return errors


def main() -> int:
    try:
        text = WORKFLOW.read_text(encoding="utf-8")
        android_text = (ROOT / ".github" / "workflows" / "android.yml").read_text(encoding="utf-8")
    except OSError as exc:
        print(f"Could not read workflow file: {exc}", file=sys.stderr)
        return 2

    errors = audit_workflow(text) + audit_ci_test_bundle(android_text)
    if errors:
        print("Commercial release workflow audit failed:")
        for error in errors:
            print(f" - {error}")
        return 1

    print("Commercial release workflow audit passed: manual trigger, confirmation, main-only guard and secret ordering.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
