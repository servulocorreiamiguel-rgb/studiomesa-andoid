import unittest

from check_sql_security_definers import scan_sql_text


SAFE_PUBLIC = """
CREATE OR REPLACE FUNCTION public.safe_rpc()
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'É necessário iniciar sessão.';
  END IF;
  RETURN true;
END;
$function$;
"""


class SecurityDefinerGuardTests(unittest.TestCase):
    def test_accepts_public_function_with_empty_search_path_and_auth_guard(self) -> None:
        count, errors = scan_sql_text(SAFE_PUBLIC, "fixture.sql")
        self.assertEqual(count, 1)
        self.assertEqual(errors, [])

    def test_rejects_public_function_without_empty_search_path(self) -> None:
        source = SAFE_PUBLIC.replace("SET search_path TO ''", "SET search_path TO public")
        count, errors = scan_sql_text(source, "fixture.sql")
        self.assertEqual(count, 1)
        self.assertTrue(any("pin search_path" in error for error in errors))

    def test_rejects_public_function_without_caller_identity_check(self) -> None:
        source = SAFE_PUBLIC.replace(
            "  IF (SELECT auth.uid()) IS NULL THEN\n"
            "    RAISE EXCEPTION 'É necessário iniciar sessão.';\n"
            "  END IF;\n",
            "",
        )
        count, errors = scan_sql_text(source, "fixture.sql")
        self.assertEqual(count, 1)
        self.assertTrue(any("must explicitly check auth.uid()" in error for error in errors))

    def test_private_trigger_function_can_use_separate_auth_model(self) -> None:
        source = """
        CREATE OR REPLACE FUNCTION private.audit_trigger()
        RETURNS trigger
        LANGUAGE plpgsql
        SECURITY DEFINER
        SET search_path TO ''
        AS $function$
        BEGIN
          RETURN NEW;
        END;
        $function$;
        """
        count, errors = scan_sql_text(source, "fixture.sql")
        self.assertEqual(count, 1)
        self.assertEqual(errors, [])

    def test_ignores_security_invoker_functions(self) -> None:
        source = """
        CREATE OR REPLACE FUNCTION public.read_only_function()
        RETURNS boolean
        LANGUAGE sql
        SECURITY INVOKER
        AS $function$
          SELECT true;
        $function$;
        """
        count, errors = scan_sql_text(source, "fixture.sql")
        self.assertEqual(count, 0)
        self.assertEqual(errors, [])


if __name__ == "__main__":
    unittest.main()
