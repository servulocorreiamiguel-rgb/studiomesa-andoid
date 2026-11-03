#!/usr/bin/env python3
"""Regression tests for StudioMesa assistant server-side security checks."""
from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("check_studiomesa_assistant_security.py")
SPEC = importlib.util.spec_from_file_location("assistant_security", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(MODULE)

CONFIG = """project_id = "svwvkfsyevuyabelrtjk"

[functions.studiomesa-assistant]
enabled = true
verify_jwt = false
"""

SOURCE = """import { withSupabase } from "npm:@supabase/server@1.9.2";
const INVOICE_BUCKET = "restaurant-financial-invoices";
const MAX_INVOICE_FILES = 3;
const MAX_INVOICE_BYTES_EACH = 10 * 1024 * 1024;
const MAX_HISTORY_MESSAGES = 24;
const apiKey = Deno.env.get("OPENAI_API_KEY");
const url = "https://api.openai.com/v1/responses";
const apiBody = { store: false, tools: [{ type: "web_search", search_context_size: "medium" }] };
Deno.serve(withSupabase({ auth: "user" }, async (req, ctx) => {
  const { data } = await ctx.supabase.rpc("studiomesa_assistant_get_invoice_files");
  const { data: history } = await ctx.supabase.rpc("studiomesa_assistant_append_message");
  if (mime_type !== "application/pdf") return Response.json({ error: "PDF only" });
      const { data: file } = await ctx.supabase.storage
        .from(INVOICE_BUCKET)
        .download("private.pdf");
  return Response.json({ ok: true });
}));
"""


class AssistantSecurityTests(unittest.TestCase):
    def test_valid_owner_scoped_function_passes(self) -> None:
        self.assertEqual([], MODULE.audit_assistant_security(CONFIG, SOURCE))

    def test_rejects_admin_client(self) -> None:
        errors = MODULE.audit_assistant_security(CONFIG, SOURCE + "\nctx.supabaseAdmin;")
        self.assertTrue(any("admin/service-role" in error for error in errors))

    def test_rejects_missing_user_auth(self) -> None:
        errors = MODULE.audit_assistant_security(CONFIG, SOURCE.replace(
            'withSupabase({ auth: "user" }', 'withSupabase({ auth: "secret" }'
        ))
        self.assertTrue(any("withSupabase user authentication" in error for error in errors))

    def test_rejects_provider_persistence(self) -> None:
        errors = MODULE.audit_assistant_security(CONFIG, SOURCE.replace("store: false", "store: true"))
        self.assertTrue(any("provider-side response storage" in error for error in errors))

    def test_rejects_unpinned_sdk(self) -> None:
        errors = MODULE.audit_assistant_security(CONFIG, SOURCE.replace(
            "npm:@supabase/server@1.9.2", "npm:@supabase/server"
        ))
        self.assertTrue(any("pinned Supabase server SDK" in error for error in errors))

    def test_rejects_excess_invoice_limit(self) -> None:
        errors = MODULE.audit_assistant_security(CONFIG, SOURCE.replace(
            "MAX_INVOICE_FILES = 3", "MAX_INVOICE_FILES = 99"
        ))
        self.assertTrue(any("three files" in error for error in errors))

    def test_requires_manual_jwt_validation_when_platform_verification_is_disabled(self) -> None:
        insecure = CONFIG.replace("verify_jwt = false", "verify_jwt = true")
        errors = MODULE.audit_assistant_security(insecure, SOURCE)
        self.assertTrue(any("verify_jwt=false" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
