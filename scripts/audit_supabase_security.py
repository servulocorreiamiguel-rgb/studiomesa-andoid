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


def security_definer_definitions(sql: str) -> list[tuple[str, str, str]]:
    """Return (qualified name, declaration header, function body) for each definer."""
    found: list[tuple[str, str, str]] = []
    for match in FUNCTION_RE.finditer(sql):
        tail = sql[match.start():]
        body_start = re.search(r"\bas\s+\$function\$", tail, re.IGNORECASE)
        if body_start is None:
            continue
        header = tail[:body_start.start()]
        body_tail = tail[body_start.end():]
        body_end = re.search(r"\$function\$\s*;", body_tail, re.IGNORECASE)
        if body_end is None:
            continue
        if re.search(r"\bsecurity\s+definer\b", header, re.IGNORECASE):
            found.append((match.group(1).lower(), header, body_tail[:body_end.start()]))
    return found


def strip_sql_comments_and_literals(sql: str) -> str:
    """Remove SQL dollar/single/double-quoted literals before stripping comments."""
    without_dollar_quotes = re.sub(r"\$(\w*)\$[\s\S]*?\$\1\$", "", sql)
    without_quoted_literals = re.sub(
        r"\'(?:\'\'|[^\'])*\'|\"(?:\"\"|[^\"])*\"",
        "",
        without_dollar_quotes,
    )
    return re.sub(r"--[^\n]*|/\*[\s\S]*?\*/", "", without_quoted_literals)


def body_references_auth_uid(body: str) -> bool:
    """Require auth.uid() in SECURITY DEFINER bodies; comments/literals do not count."""
    return bool(
        re.search(
            r"\bauth\s*\.\s*uid\s*\(\s*\)",
            strip_sql_comments_and_literals(body),
            re.IGNORECASE,
        )
    )


def is_auth_user_delete_trigger(
    sql: str,
    function_name: str,
    header: str,
    body: str,
) -> bool:
    """Allow the one system trigger that uses OLD.id, not a request JWT identity."""
    if function_name != "private.finalize_account_deletion_after_auth_delete":
        return False
    safe_header = strip_sql_comments_and_literals(header)
    safe_body = strip_sql_comments_and_literals(body)
    trigger_is_bound_to_auth_users = re.search(
        r"\bcreate\s+trigger\s+[a-z_][a-z0-9_]*\s+"
        r"after\s+delete\s+on\s+auth\.users\b"
        r"[\s\S]*?\bexecute\s+function\s+"
        r"private\.finalize_account_deletion_after_auth_delete\s*\(\s*\)\s*;",
        sql,
        re.IGNORECASE,
    )
    return bool(
        re.search(r"\breturns\s+trigger\b", safe_header, re.IGNORECASE)
        and trigger_is_bound_to_auth_users
        and re.search(r"\bOLD\s*\.\s*id\b", safe_body, re.IGNORECASE)
        and re.search(
            r"\bfrom\s+private\.account_deletion_queue\b",
            safe_body,
            re.IGNORECASE,
        )
        and re.search(
            r"\bdelete\s+from\s+private\.account_deletion_queue\b",
            safe_body,
            re.IGNORECASE,
        )
        and re.search(
            r"\bwhere\s+q\.user_id\s*=\s*OLD\s*\.\s*id\b",
            safe_body,
            re.IGNORECASE,
        )
    )

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


def has_restaurant_membership_scope_guard(sql: str, function_name: str) -> bool:
    """Require an explicit user-and-restaurant membership check in the helper body."""
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
        body = body_tail[:body_end.start()]
        checks_restaurant = re.search(
            r"\brs\.restaurant_id\s*=\s*p_restaurant_id\b",
            body,
            re.IGNORECASE,
        )
        checks_user = re.search(
            r"\brs\.user_id\s*=\s*\(\s*select\s+auth\.uid\s*\(\s*\)\s*\)",
            body,
            re.IGNORECASE,
        )
        reads_membership_table = re.search(
            r"\bfrom\s+public\.restaurant_staff\s+(?:as\s+)?rs\b",
            body,
            re.IGNORECASE,
        )
        if reads_membership_table and checks_restaurant and checks_user:
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


def tables_with_explicit_privilege_statements(sql: str) -> set[str]:
    """Return public tables named in explicit GRANT/REVOKE statements.

    New Supabase Data API defaults must not be relied on for table privileges.
    A table may deliberately be RPC-only; an explicit REVOKE is still a valid
    declaration of that access model.
    """
    referenced: set[str] = set()
    for match in re.finditer(r"\b(?:grant|revoke)\b[^;]*;", sql, re.IGNORECASE):
        statement = match.group(0)
        if re.search(r"\bon\s+function\b", statement, re.IGNORECASE):
            continue
        object_match = re.search(
            r"\bon\s+(?:table\s+)?([\s\S]*?)\s+(?:to|from)\b",
            statement,
            re.IGNORECASE,
        )
        if object_match is None:
            continue
        referenced.update(
            name.lower()
            for name in re.findall(
                r"\bpublic\.[a-z_][a-z0-9_]*\b",
                object_match.group(1),
                re.IGNORECASE,
            )
        )
    return referenced


def public_table_grants_to_untrusted_roles(sql: str) -> list[tuple[str, str]]:
    """Find public-table GRANT statements that expose table privileges to anon/PUBLIC."""
    findings: list[tuple[str, str]] = []
    for match in re.finditer(r"\bgrant\b[^;]*;", sql, re.IGNORECASE):
        statement = match.group(0)
        if re.search(r"\bon\s+function\b", statement, re.IGNORECASE):
            continue
        object_match = re.search(
            r"\bon\s+(?:table\s+)?([\s\S]*?)\s+to\b([\s\S]*?);$",
            statement,
            re.IGNORECASE,
        )
        if object_match is None:
            continue
        objects = object_match.group(1)
        recipients = object_match.group(2)
        if not re.search(r"\b(?:public|anon)\b", recipients, re.IGNORECASE):
            continue
        table_names = {
            name.lower()
            for name in re.findall(
                r"\bpublic\.[a-z_][a-z0-9_]*\b", objects, re.IGNORECASE
            )
        }
        if not table_names and re.search(
            r"\ball\s+tables\s+in\s+schema\s+public\b",
            objects,
            re.IGNORECASE,
        ):
            table_names.add("public.*")
        for table_name in sorted(table_names):
            findings.append((table_name, recipients.strip()))
    return findings


def audit_migration_sql(sql: str) -> tuple[list[str], int, int]:
    errors: list[str] = []
    definitions = security_definer_definitions(sql)
    if not definitions:
        errors.append("Não foi encontrada nenhuma declaração SECURITY DEFINER; confirma se as migrações foram carregadas.")
    for name, header, body in definitions:
        if not re.search(r"\bset\s+search_path\s*(?:=|to)\s*''", header, re.IGNORECASE):
            errors.append(f"{name}: SECURITY DEFINER sem SET search_path = ''.")
        if not body_references_auth_uid(body) and not is_auth_user_delete_trigger(
            sql, name, header, body
        ):
            errors.append(
                f"{name}: SECURITY DEFINER sem referência a auth.uid() no corpo; "
                "exige verificação da identidade autenticada e do respetivo âmbito, "
                "ou uma exceção de trigger explicitamente validada."
            )
        revoked = revoked_roles(sql, name)
        # This one private membership helper must be executable by authenticated
        # because the app's RLS policies invoke it. Permit this narrow exception
        # only when its body checks auth.uid(), policies call it, authenticated
        # is explicitly granted EXECUTE, and PUBLIC/anon are explicitly revoked.
        safe_rls_helper = (
            name == "private.is_restaurant_staff"
            and has_restaurant_membership_scope_guard(sql, name)
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

    public_tables = {name for name in table_names if name.startswith("public.")}
    explicitly_declared = tables_with_explicit_privilege_statements(sql)
    for table_name in sorted(public_tables - explicitly_declared):
        errors.append(
            f"{table_name}: falta GRANT/REVOKE explícito de privilégios na tabela; "
            "não dependas dos privilégios por omissão da Data API."
        )
    for table_name, recipients in public_table_grants_to_untrusted_roles(sql):
        errors.append(
            f"{table_name}: GRANT de privilégios de tabela para PUBLIC/anon ({recipients}); "
            "confirma e justifica explicitamente qualquer exposição direta."
        )

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
