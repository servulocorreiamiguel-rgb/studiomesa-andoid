#!/usr/bin/env python3
"""Tests for the account-deletion Edge Function security audit."""
from __future__ import annotations

import unittest

from check_account_deletion_security import audit_config_text, audit_function_sources


VALID_CONFIG = """
project_id = "svwvkfsyevuyabelrtjk"

[functions.delete-account]
enabled = true
verify_jwt = true

[functions.account-deletion-web]
enabled = true
verify_jwt = false
"""

VALID_DELETE_SOURCE = """
Deno.serve(
  withSupabase({ auth: "user" }, async (_req, ctx) => {
    if (typeof body.delete_restaurant_data !== "boolean") return;
    await ctx.supabaseAdmin.auth.admin.deleteUser(userId);
  }),
);
"""

VALID_WEB_SOURCE = """
import { createClient } from "https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2.118.0/+esm";
const PAGE = `<script nonce="__STUDIOMESA_CSP_NONCE__" type="module">
const supabase = createClient(url, key, { auth: { flowType: "pkce" } });
const linkRequestResultMessage="If the address matches an eligible account, a link may be sent."; showStatus(linkRequestResultMessage); showStatus(linkRequestResultMessage);
</script>`;
const nonceBytes = crypto.getRandomValues(new Uint8Array(18));
const nonce = btoa(String.fromCharCode(...nonceBytes));
const page = PAGE.replace("__STUDIOMESA_CSP_NONCE__", nonce);
const contentSecurityPolicy = [
  "default-src 'none'",
  "base-uri 'none'",
  "connect-src https://svwvkfsyevuyabelrtjk.supabase.co",
  "form-action 'self'",
  "frame-ancestors 'none'",
  "object-src 'none'",
  `script-src 'nonce-${nonce}' https://cdn.jsdelivr.net`,
  "style-src 'unsafe-inline'",
].join("; ");
return new Response(page, { headers: {
  "Content-Security-Policy": contentSecurityPolicy,
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "no-referrer",
  "X-Frame-Options": "DENY",
  "Cache-Control": "no-store, max-age=0"
}});
"""


class AccountDeletionSecurityTests(unittest.TestCase):
    def test_accepts_explicit_safe_function_configuration(self) -> None:
        self.assertEqual([], audit_config_text(VALID_CONFIG))

    def test_rejects_disabling_platform_jwt_check_on_delete_endpoint(self) -> None:
        config = VALID_CONFIG.replace(
            "[functions.delete-account]\nenabled = true\nverify_jwt = true",
            "[functions.delete-account]\nenabled = true\nverify_jwt = false",
        )
        errors = audit_config_text(config)
        self.assertTrue(any("delete-account must keep platform verify_jwt = true" in e for e in errors))

    def test_requires_public_html_page_to_remain_unauthenticated_at_gateway(self) -> None:
        config = VALID_CONFIG.replace(
            "[functions.account-deletion-web]\nenabled = true\nverify_jwt = false",
            "[functions.account-deletion-web]\nenabled = true\nverify_jwt = true",
        )
        errors = audit_config_text(config)
        self.assertTrue(any("account-deletion-web must keep verify_jwt = false" in e for e in errors))

    def test_accepts_user_scoped_delete_endpoint_pkce_and_nonce_csp(self) -> None:
        self.assertEqual([], audit_function_sources(VALID_DELETE_SOURCE, VALID_WEB_SOURCE))

    def test_rejects_different_magic_link_success_and_failure_messages(self) -> None:
        source = VALID_WEB_SOURCE.replace(
            "showStatus(linkRequestResultMessage);",
            'showStatus("The email could not be sent.");',
            1,
        )
        errors = audit_function_sources(VALID_DELETE_SOURCE, source)
        self.assertTrue(any("same generic status" in e for e in errors))

    def test_rejects_implicit_flow(self) -> None:
        source = VALID_WEB_SOURCE.replace('flowType: "pkce"', 'flowType: "implicit"')
        errors = audit_function_sources(VALID_DELETE_SOURCE, source)
        self.assertTrue(any("must use the PKCE" in e for e in errors))
        self.assertTrue(any("implicit authentication flow" in e for e in errors))

    def test_rejects_missing_csp_nonce_and_security_headers(self) -> None:
        source = VALID_WEB_SOURCE.replace(
            'nonce="__STUDIOMESA_CSP_NONCE__"',
            "",
        ).replace('"X-Frame-Options": "DENY",', "")
        errors = audit_function_sources(VALID_DELETE_SOURCE, source)
        self.assertTrue(any("nonce placeholder" in e for e in errors))
        self.assertTrue(any("X-Frame-Options" in e for e in errors))

    def test_rejects_floating_supabase_js_dependency(self) -> None:
        source = VALID_WEB_SOURCE.replace(
            "@supabase/supabase-js@2.118.0",
            "@supabase/supabase-js",
        )
        errors = audit_function_sources(VALID_DELETE_SOURCE, source)
        self.assertTrue(any("pin supabase-js" in e for e in errors))

    def test_rejects_unscoped_delete_handler(self) -> None:
        source = VALID_DELETE_SOURCE.replace('withSupabase({ auth: "user" },', "withSupabase({},")
        errors = audit_function_sources(source, VALID_WEB_SOURCE)
        self.assertTrue(any("auth: \"user\"" in e for e in errors))

    def test_rejects_service_role_secret_in_browser_source(self) -> None:
        source = VALID_WEB_SOURCE + '\nconst leaked = "sb_secret_forbidden";'
        errors = audit_function_sources(VALID_DELETE_SOURCE, source)
        self.assertTrue(any("secret/service-role" in e for e in errors))


if __name__ == "__main__":
    unittest.main()
