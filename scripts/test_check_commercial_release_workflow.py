#!/usr/bin/env python3
"""Regression tests for the privileged commercial release workflow audit."""
from __future__ import annotations

import unittest

from check_commercial_release_workflow import audit_ci_test_bundle, audit_workflow


SAFE_WORKFLOW = """name: Build signed StudioMesa commercial AABs

on:
  workflow_dispatch:
    inputs:
      confirm:
        required: true
        type: string

permissions:
  contents: read

jobs:
  build:
    steps:
      - name: Require explicit confirmation
        env:
          CONFIRM: ${{ inputs.confirm }}
        run: |
          if [ "$CONFIRM" != "BUILD" ]; then
            exit 1
          fi

      - name: Require the reviewed main branch
        env:
          REF: ${{ github.ref }}
        run: |
          set -euo pipefail
          if [ "$REF" != "refs/heads/main" ]; then
            echo "Stopped: commercial releases must be built from main."
            exit 1
          fi

      - name: Check out source
        uses: actions/checkout@0000000000000000000000000000000000000000

      - name: Validate signing secrets and keystore
        env:
          ANDROID_KEYSTORE_PASSWORD: ${{ secrets.ANDROID_KEYSTORE_PASSWORD }}
        run: |
          test -n "$ANDROID_KEYSTORE_PASSWORD"

      - name: Verify signed bundles
        run: |
          verification_output="$(jarsigner -verify "$bundle" 2>&1)" || exit 1
          if ! grep -Fq "jar verified." <<< "$verification_output"; then
            exit 1
          fi
"""

SAFE_ANDROID_WORKFLOW = """      - name: Verify temporary CI AAB
        run: |
          verification_output="$(jarsigner -verify "$AAB_PATH" 2>&1)" || exit 1
          if ! grep -Fq "jar verified." <<< "$verification_output"; then
            exit 1
          fi
"""


class CommercialReleaseWorkflowAuditTests(unittest.TestCase):
    def test_accepts_manual_confirmed_main_only_workflow(self) -> None:
        self.assertEqual([], audit_workflow(SAFE_WORKFLOW))

    def test_rejects_a_guard_that_allows_another_branch(self) -> None:
        changed = SAFE_WORKFLOW.replace(
            'if [ "$REF" != "refs/heads/main" ]',
            'if [ "$REF" != "refs/heads/release" ]',
        )
        self.assertTrue(
            any("refs/heads/main" in error for error in audit_workflow(changed))
        )

    def test_rejects_a_guard_after_checkout_or_secret_validation(self) -> None:
        lines = SAFE_WORKFLOW.splitlines()
        confirmation = lines.index("      - name: Require explicit confirmation")
        guard = lines.index("      - name: Require the reviewed main branch")
        checkout = lines.index("      - name: Check out source")
        validation = lines.index("      - name: Validate signing secrets and keystore")
        reordered = (
            lines[:guard]
            + lines[guard:checkout]
            + lines[checkout:validation]
            + lines[validation:]
        )
        # Move the branch guard to after secret validation, while preserving every step.
        guard_block = reordered[guard:guard + (checkout - guard)]
        without_guard = reordered[:guard] + reordered[guard + len(guard_block):]
        insert_at = next(
            i for i, line in enumerate(without_guard)
            if line == "      - name: Validate signing secrets and keystore"
        )
        mutated = without_guard[:insert_at] + guard_block + without_guard[insert_at:]
        self.assertTrue(any("order" in error for error in audit_workflow("\n".join(mutated))))

    def test_rejects_automatic_triggers(self) -> None:
        changed = SAFE_WORKFLOW.replace("on:\n  workflow_dispatch:", "on:\n  push:\n    branches: [main]\n  workflow_dispatch:")
        self.assertTrue(
            any("automatically" in error for error in audit_workflow(changed))
        )

    def test_requires_explicit_confirmation(self) -> None:
        changed = SAFE_WORKFLOW.replace("      - name: Require explicit confirmation\n", "")
        self.assertTrue(
            any("Require explicit confirmation" in error for error in audit_workflow(changed))
        )

    def test_rejects_jarsigner_check_without_explicit_success_marker(self) -> None:
        changed = SAFE_WORKFLOW.replace(
            '          if ! grep -Fq "jar verified." <<< "$verification_output"; then\\n            exit 1\\n          fi\\n',
            '',
        )
        self.assertTrue(
            any("explicitly reject AABs" in error for error in audit_workflow(changed))
        )

    def test_android_ci_must_verify_its_temporary_aab_signature(self) -> None:
        self.assertEqual([], audit_ci_test_bundle(SAFE_ANDROID_WORKFLOW))
        changed = SAFE_ANDROID_WORKFLOW.replace(
            '          if ! grep -Fq "jar verified." <<< "$verification_output"; then\\n            exit 1\\n          fi\\n',
            '',
        )
        self.assertTrue(
            any("explicit" in error for error in audit_ci_test_bundle(changed))
        )

    def test_rejects_printing_a_signing_secret(self) -> None:
        changed = SAFE_WORKFLOW.replace(
            '          test -n "$ANDROID_KEYSTORE_PASSWORD"',
            '          echo "$ANDROID_KEYSTORE_PASSWORD"',
        )
        self.assertTrue(
            any("Do not print signing secrets" in error for error in audit_workflow(changed))
        )


if __name__ == "__main__":
    unittest.main()
