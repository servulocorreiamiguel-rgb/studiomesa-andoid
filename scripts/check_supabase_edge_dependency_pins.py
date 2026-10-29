#!/usr/bin/env python3
"""Reject unpinned npm: imports in Supabase Edge Function TypeScript."""
from __future__ import annotations

import re
import sys
from pathlib import Path

# A full x.y.z version is required. Ranges such as ^1, ~1.2, latest and tags
# are intentionally rejected so CI doesn't silently resolve a different build.
SPECIFIER_RE = re.compile(r"""["'](npm:[^"']+)["']""")
EXACT_NPM_SPECIFIER_RE = re.compile(
    r"npm:(?:@[^/]+/)?[^@/]+@"
    r"\d+\.\d+\.\d+"
    r"(?:-[0-9A-Za-z.-]+)?"
    r"(?:\+[0-9A-Za-z.-]+)?"
    r"(?:/.*)?$"
)


def is_exact_npm_specifier(specifier: str) -> bool:
    """Return True only for npm package specifiers with a fixed x.y.z version."""
    return bool(EXACT_NPM_SPECIFIER_RE.fullmatch(specifier))


def find_unpinned_npm_imports(source: str) -> list[str]:
    """Return quoted npm: specifiers that are not pinned to an exact version."""
    return [
        specifier
        for specifier in SPECIFIER_RE.findall(source)
        if not is_exact_npm_specifier(specifier)
    ]


def audit_directory(root: Path) -> list[str]:
    """Return file-and-line findings for unpinned imports under root."""
    if not root.is_dir():
        return [f"Diretório de Edge Functions não encontrado: {root}"]

    findings: list[str] = []
    for path in sorted(root.rglob("*.ts")):
        try:
            source = path.read_text(encoding="utf-8")
        except OSError as exc:
            findings.append(f"{path}: não foi possível ler o ficheiro: {exc}")
            continue

        for line_number, line in enumerate(source.splitlines(), start=1):
            for specifier in find_unpinned_npm_imports(line):
                findings.append(
                    f"{path}:{line_number}: dependência npm sem versão exata: "
                    f"{specifier}"
                )
    return findings


def main() -> int:
    repository_root = Path(__file__).resolve().parents[1]
    functions_root = repository_root / "supabase" / "functions"
    findings = audit_directory(functions_root)
    if findings:
        print("\n".join(findings), file=sys.stderr)
        return 1

    print("Auditoria de dependências npm das Supabase Edge Functions aprovada.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
