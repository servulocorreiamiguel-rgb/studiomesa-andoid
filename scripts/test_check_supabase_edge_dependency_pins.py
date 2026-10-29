#!/usr/bin/env python3
"""Unit tests for the Supabase Edge Function dependency pin audit."""
from __future__ import annotations

import unittest

from check_supabase_edge_dependency_pins import (
    find_unpinned_npm_imports,
    is_exact_npm_specifier,
)


class DependencyPinAuditTests(unittest.TestCase):
    def test_accepts_exact_versions_for_scoped_and_unscoped_packages(self) -> None:
        self.assertTrue(is_exact_npm_specifier("npm:@supabase/server@1.9.2"))
        self.assertTrue(is_exact_npm_specifier("npm:example-package@2.4.0"))
        self.assertTrue(is_exact_npm_specifier("npm:@scope/package@1.0.0/subpath"))
        self.assertTrue(is_exact_npm_specifier("npm:@scope/package@1.0.0-beta.1"))

    def test_rejects_floating_tags_and_version_ranges(self) -> None:
        for specifier in (
            "npm:@supabase/server",
            "npm:@supabase/server@latest",
            "npm:@supabase/server@1",
            "npm:@supabase/server@^1.9.2",
            "npm:@supabase/server@~1.9.2",
        ):
            with self.subTest(specifier=specifier):
                self.assertFalse(is_exact_npm_specifier(specifier))

    def test_finds_only_unpinned_quoted_npm_imports(self) -> None:
        source = '''
import { withSupabase } from "npm:@supabase/server@1.9.2";
import { createClient } from "npm:@supabase/supabase-js";
import something from "https://deno.land/x/example/mod.ts";
'''
        self.assertEqual(
            find_unpinned_npm_imports(source),
            ["npm:@supabase/supabase-js"],
        )


if __name__ == "__main__":
    unittest.main()
