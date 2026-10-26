#!/usr/bin/env python3
"""Static safety checks for Supabase migrations and Android client sources.

This is a source-level guard for CI, not a substitute for running PostgreSQL
RLS tests against a real Supabase database.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MIGRATIONS_DIR = ROOT / "supabase" / "migrations"
ANDROID_MAIN_DIR = ROOT / "app" / "src" / "main"

FUNCTION_RE = re.compile(
    r"\bcreate\s+(?:or\s+replace\s+)?function\s+"
    r"((?:public|private)\.[a-z_][a-z0-9_]*)\s*\(",
    re.IGNORECASE,
)
CREATE_TABLE_RE = re.compile(
    r"\bcreate\s+table\s+(?:if\s+not\s+exists\s+)?"
    r"((?:public|private)\.[a-z_][a-z0-9_]*)",
    re.IGNORECASE,
)
REVOKE_FUNCTION_RE = re.compile(
    r"\brevoke\s+(?:all(?:\s+privileges)?|execute)\s+on\s+function\b"
    r"([\s\S]*?);",
    re.IGNORECASE,
)
GRANT_FUNCTION_RE = re.compile(
    r"\bgrant\s+(?:all(?:\s+privileges)?|execute)\s+on\s+function\b"
    r"([\s\S]*?);",
    re.IGNORECASE,
)
SECRET_PATTERNS = (
    re.compile(r"\bsb_secret_[A-Za-z0-9_-]{12,}\b"),
    re.compile(r"\bSUPABASE_SERVICE_ROLE_KEY\s*[:=]\s*['\"][^'\"]+['\"]", re.IGNORECASE),
    re.compile(r"\beyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b"),
)


def compact(value: str) -> str:
    return re.sub(r"\s+", " ", value).strip()


def security_definer_headers(sql: str) -> list[tuple[str, str]]:
    """Return (qualified function name, declaration header) for each definer function."""
    found: list[tuple[str, str]] = []
    for match in FUNCTION_RE.finditer(sql):
        tail = sql[match.start():]
        body_start = re.search(r"\bas\s+\$function\$", tail, re.IGNORECASE)
        if body_start is None:
            continue
        header = tail[:body_start.start()]
        if re.search(r"\bsecurity\s+definer\b", header, re.IGNORECASE):
            found.append((match.group(1).lower(), header))
    return found


def revoked_roles(sql: str, function_name: str) -> set[str]:
    roles: set[str] = set()
    qualified_name = re.escape(function_name)
    for match in REVOKE_FUNCTION_RE.finditer(sql):
        statement = match.group(1)
        if not re.search(rf"\b{qualified_name}\s*\(", statement, re.IGNORECASE):
            continue
        from_clause = re.split(r"\bfrom\b", statement, flags=re.IGNORECASE)[-1]
        normalized = from_clause.lower()
        if re.search(r"\bpublic\b", normalized):
            roles.add("PUBLIC")
        if re.search(r"\banon\b", normalized):
            roles.add("anon")
        if re.search(r"\bauthenticated\b", normalized):
            roles.add("authenticated")
    return roles


def granted_roles(sql: str, function_name: str) -> set[str]:
    """Return roles explicitly granted EXECUTE on a named function."""
    roles: set[str] = set()
    qualified_name = re.escape(function_name)
    for match in GRANT_FUNCTION_RE.finditer(sql):
        statement = match.group(1)
        if not re.search(rf"\b{qualified_name}\s*\(", statement, re.IGNORECASE):
            continue
        to_clause = re.split(r"\bto\b", statement, flags=re.IGNORECASE)[-1].lower()
        if re.search(r"\bpublic\b", to_clause):
            roles.add("PUBLIC")
        if re.search(r"\banon\b", to_clause):
            roles.add("anon")
        if re.search(r"\bauthenticated\b", to_clause):
            roles.add("authenticated")
    return roles


def has_auth_uid_guard(sql: str, function_name: str) -> bool:
    """Require auth.uid() in the actual body of the named function."""
    for match in FUNCTION_RE.finditer(sql):
        if match.group(1).lower() != function_name.lower():
            continue
        tail = sql[match.start():]
        body_start = re.search(r"\bas\s+\$function\$", tail, re.IGNORECASE)
        if body_start is None:
            continue
        body_tail = tail[body_start.end():]
        body_end = re.search(r"\$function\$\s*;", body_tail)
        if body_end is None:
            continue
        if re.search(r"\bauth\.uid\s*\(", body_tail[:body_end.start()], re.IGNORECASE):
            return True
    return False


def referenced_by_policy(sql: str, function_name: str) -> bool:
    """Require an actual CREATE/ALTER POLICY statement to call the helper."""
    qualified_name = re.escape(function_name)
    policy_statements = re.findall(
        r"\b(?:create|alter)\s+policy\b([\s\S]*?);",
        sql,
        re.IGNORECASE,
    )
    return any(
        re.search(rf"\b{qualified_name}\s*\(", statement, re.IGNORECASE)
        for statement in policy_statements
    )


def audit_migration_sql(sql: str) -> tuple[list[str], int, int]:
    errors: list[str] = []
    definitions = security_definer_headers(sql)
    if not definitions:
        errors.append("Não foi encontrada nenhuma declaração SECURITY DEFINER; confirma se as migrações foram carregadas.")
    for name, header in definitions:
        if not re.search(r"\bset\s+search_path\s*(?:=|to)\s*''", header, re.IGNORECASE):
            errors.append(f"{name}: SECURITY DEFINER sem SET search_path = ''.")

        revoked = revoked_roles(sql, name)
        # This one private membership helper must be executable by authenticated
        # because the app's RLS policies invoke it. Permit this narrow exception
        # only when its body checks auth.uid(), policies call it, authenticated
        # is explicitly granted EXECUTE, and PUBLIC/anon are explicitly revoked.
        safe_rls_helper = (
            name == "private.is_restaurant_staff"
            and has_auth_uid_guard(sql, name)
            and referenced_by_policy(sql, name)
            and "authenticated" in granted_roles(sql, name)
            and {"PUBLIC", "anon"}.issubset(revoked)
        )
        required = {"PUBLIC", "anon"} if name.startswith("public.") or safe_rls_helper else {"PUBLIC", "anon", "authenticated"}
        missing = required - revoked
        if missing:
            errors.append(
                f"{name}: falta revogar EXECUTE a {', '.join(sorted(missing))} "
                "nas migrações versionadas."
            )

    # Tables newly created in tracked migrations must have RLS enabled.
    normalized_sql = compact(sql)
    table_names = {name.lower() for name in CREATE_TABLE_RE.findall(normalized_sql)}
    for table_name in sorted(table_names):
        escaped_name = re.escape(table_name)
        enabled = re.search(
            rf"\balter\s+table\s+(?:if\s+exists\s+)?{escaped_name}"
            rf"\s+enable\s+row\s+level\s+security\b",
            normalized_sql,
            re.IGNORECASE,
        )
        if not enabled:
            errors.append(f"{table_name}: tabela criada por migração sem ENABLE ROW LEVEL SECURITY.")

    return errors, len(definitions), len(table_names)


def audit_android_sources(source_root: Path) -> list[str]:
    errors: list[str] = []
    if not source_root.exists():
        return [f"Diretório de código Android não encontrado: {source_root}"]
    for source_file in source_root.rglob("*"):
        if not source_file.is_file() or source_file.suffix.lower() not in {".kt", ".java", ".xml", ".json", ".properties"}:
            continue
        try:
            contents = source_file.read_text(encoding="utf-8")
        except (OSError, UnicodeError) as exc:
            errors.append(f"Não foi possível ler {source_file.relative_to(ROOT)}: {exc}")
            continue
        for pattern in SECRET_PATTERNS:
            if pattern.search(contents):
                errors.append(
                    f"{source_file.relative_to(ROOT)}: possível segredo Supabase ou token JWT literal no cliente Android."
                )
                break
    return errors


def main() -> int:
    migration_files = sorted(MIGRATIONS_DIR.glob("*.sql"))
    if not migration_files:
        print("ERRO: não foram encontradas migrações SQL em supabase/migrations.", file=sys.stderr)
        return 2

    sql = "\n\n".join(path.read_text(encoding="utf-8") for path in migration_files)
    errors, function_count, table_count = audit_migration_sql(sql)
    errors.extend(audit_android_sources(ANDROID_MAIN_DIR))

    if errors:
        for error in errors:
            print(f"ERRO: {error}", file=sys.stderr)
        print(f"Auditoria estática falhou: {len(errors)} problema(s).", file=sys.stderr)
        return 1

    print(
        "Auditoria estática Supabase/Android aprovada: "
        f"{len(migration_files)} migrações, {function_count} declarações SECURITY DEFINER, "
        f"{table_count} tabelas criadas nas migrações; sem segredos detetados no código Android."
    )
    print("Nota: isto não substitui testes RLS/integração contra uma base de dados real.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
