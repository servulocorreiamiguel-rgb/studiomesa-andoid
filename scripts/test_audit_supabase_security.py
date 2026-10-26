#!/usr/bin/env python3
"""Regression tests for the StudioMesa static Supabase security audit."""
import unittest

from audit_supabase_security import audit_migration_sql


class SupabaseSecurityAuditTests(unittest.TestCase):
    def test_accepts_restricted_definer_and_rls_table(self):
        sql = """
        CREATE OR REPLACE FUNCTION public.safe_read(p_id uuid)
        RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
        AS $function$ BEGIN RETURN p_id; END; $function$;
        REVOKE ALL ON FUNCTION public.safe_read(uuid) FROM PUBLIC, anon;
        CREATE TABLE public.safe_items (id uuid PRIMARY KEY);
        ALTER TABLE public.safe_items ENABLE ROW LEVEL SECURITY;
        """
        errors, function_count, table_count = audit_migration_sql(sql)
        self.assertEqual([], errors)
        self.assertEqual(1, function_count)
        self.assertEqual(1, table_count)

    def test_rejects_definer_without_empty_search_path(self):
        sql = """
        CREATE FUNCTION public.unsafe() RETURNS text LANGUAGE sql SECURITY DEFINER
        AS $function$ SELECT 'unsafe'; $function$;
        REVOKE ALL ON FUNCTION public.unsafe() FROM PUBLIC, anon;
        """
        errors, _, _ = audit_migration_sql(sql)
        self.assertTrue(any("SET search_path" in error for error in errors))

    def test_rejects_public_definer_when_anon_not_revoked(self):
        sql = """
        CREATE FUNCTION public.unsafe() RETURNS text LANGUAGE sql SECURITY DEFINER SET search_path = ''
        AS $function$ SELECT 'unsafe'; $function$;
        REVOKE ALL ON FUNCTION public.unsafe() FROM PUBLIC;
        """
        errors, _, _ = audit_migration_sql(sql)
        self.assertTrue(any("anon" in error for error in errors))

    def test_rejects_new_public_table_without_rls(self):
        sql = "CREATE TABLE public.open_data (id uuid PRIMARY KEY);"
        errors, _, _ = audit_migration_sql(sql)
        self.assertTrue(any("ENABLE ROW LEVEL SECURITY" in error for error in errors))

    def test_private_definer_must_revoke_authenticated(self):
        sql = """
        CREATE FUNCTION private.internal() RETURNS text LANGUAGE sql SECURITY DEFINER SET search_path = ''
        AS $function$ SELECT 'ok'; $function$;
        REVOKE ALL ON FUNCTION private.internal() FROM PUBLIC, anon;
        """
        errors, _, _ = audit_migration_sql(sql)
        self.assertTrue(any("authenticated" in error for error in errors))


if __name__ == "__main__":
    unittest.main(verbosity=2)
