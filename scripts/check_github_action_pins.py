#!/usr/bin/env python3
"""Fail CI when a GitHub Actions workflow uses a mutable action reference.

This audit intentionally has no third-party dependencies and performs no network
requests. Marketplace actions must be pinned to a full 40-character commit SHA.
Local actions (./path) are permitted because their code is reviewed with the repo.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS_DIR = ROOT / ".github" / "workflows"
USES_PATTERN = re.compile(r"^\s*uses:\s*([^\s#]+)", re.MULTILINE)
IMMUTABLE_ACTION_PATTERN = re.compile(
    r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+@[0-9a-f]{40}$"
)


def extract_action_refs(workflow_text: str) -> list[tuple[int, str]]:
    """Return (1-based line number, action reference) pairs."""
    return [
        (line_number, match.group(1))
        for line_number, line in enumerate(workflow_text.splitlines(), start=1)
        if (match := re.match(r"^\s*uses:\s*([^\s#]+)", line))
    ]


def is_immutable_action_ref(reference: str) -> bool:
    """Accept a local action path or a Marketplace action pinned to a full SHA."""
    return reference.startswith("./") or bool(
        IMMUTABLE_ACTION_PATTERN.fullmatch(reference)
    )


def audit_workflows(workflows_dir: Path) -> list[str]:
    """Return audit errors; an empty list means all action refs pass."""
    workflow_files = sorted(
        path
        for pattern in ("*.yml", "*.yaml")
        for path in workflows_dir.glob(pattern)
        if path.is_file()
    )
    if not workflow_files:
        return [f"No workflow YAML files found in {workflows_dir}"]

    errors: list[str] = []
    for path in workflow_files:
        try:
            content = path.read_text(encoding="utf-8")
        except OSError as exc:
            errors.append(f"{path}: unable to read workflow ({exc})")
            continue
        for line_number, reference in extract_action_refs(content):
            if not is_immutable_action_ref(reference):
                relative = path.relative_to(ROOT) if path.is_relative_to(ROOT) else path
                errors.append(
                    f"{relative}:{line_number}: mutable or unsupported action ref "
                    f"{reference!r}; pin owner/repo to a full 40-character commit SHA"
                )
    return errors


def main() -> int:
    errors = audit_workflows(WORKFLOWS_DIR)
    if errors:
        print("GitHub Actions pin audit failed:")
        for error in errors:
            print(f" - {error}")
        return 1

    count = sum(
        len(extract_action_refs(path.read_text(encoding="utf-8")))
        for pattern in ("*.yml", "*.yaml")
        for path in WORKFLOWS_DIR.glob(pattern)
        if path.is_file()
    )
    print(f"GitHub Actions pin audit passed ({count} action references checked).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
