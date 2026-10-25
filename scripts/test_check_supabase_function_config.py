import unittest

from check_supabase_function_config import validate_configuration


VALID_CONFIG = {
    "functions": {
        "delete-account": {"verify_jwt": False},
        "account-deletion-web": {"verify_jwt": False},
    }
}

VALID_ACCOUNT_DELETION_WEB_SOURCE = """
const supabase = createClient(projectUrl, publishableKey, {
  auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true, flowType: "pkce" }
});
"""

VALID_DELETE_ACCOUNT_SOURCE = """
Deno.serve(
  withSupabase({ auth: "user" }, async (req, ctx) => {
    const userId = ctx.userClaims?.sub;
    if (!userId) throw new Error("Unauthenticated");
    await ctx.supabaseAdmin.auth.admin.deleteUser(userId);
    return Response.json({ deleted: true });
  }),
);
"""


class SupabaseFunctionConfigTests(unittest.TestCase):
    def test_accepts_public_page_and_authenticated_delete_handler(self) -> None:
        self.assertEqual(
            validate_configuration(VALID_CONFIG, VALID_DELETE_ACCOUNT_SOURCE, VALID_ACCOUNT_DELETION_WEB_SOURCE),
            [],
        )

    def test_rejects_missing_functions_configuration(self) -> None:
        errors = validate_configuration({}, VALID_DELETE_ACCOUNT_SOURCE)
        self.assertTrue(any("[functions]" in error for error in errors))

    def test_rejects_gateway_jwt_verification_for_public_page(self) -> None:
        config = {
            "functions": {
                "delete-account": {"verify_jwt": False},
                "account-deletion-web": {"verify_jwt": True},
            }
        }
        errors = validate_configuration(config, VALID_DELETE_ACCOUNT_SOURCE, VALID_ACCOUNT_DELETION_WEB_SOURCE)
        self.assertTrue(any("verify_jwt = false" in error for error in errors))

    def test_rejects_delete_handler_without_user_auth_wrapper(self) -> None:
        source = VALID_DELETE_ACCOUNT_SOURCE.replace(
            'withSupabase({ auth: "user" },',
            "withSupabase({},",
        )
        errors = validate_configuration(VALID_CONFIG, source, VALID_ACCOUNT_DELETION_WEB_SOURCE)
        self.assertTrue(any("withSupabase" in error for error in errors))

    def test_rejects_delete_target_not_bound_to_verified_session(self) -> None:
        source = VALID_DELETE_ACCOUNT_SOURCE.replace(
            "const userId = ctx.userClaims?.sub;",
            'const userId = "arbitrary-user-id";',
        )
        errors = validate_configuration(VALID_CONFIG, source, VALID_ACCOUNT_DELETION_WEB_SOURCE)
        self.assertTrue(any("verified session subject" in error for error in errors))

    def test_rejects_admin_deletion_not_targeted_to_verified_user(self) -> None:
        source = VALID_DELETE_ACCOUNT_SOURCE.replace(
            "deleteUser(userId)",
            'deleteUser("different-user")',
        )
        errors = validate_configuration(VALID_CONFIG, source, VALID_ACCOUNT_DELETION_WEB_SOURCE)
        self.assertTrue(any("verified session" in error for error in errors))


    def test_rejects_implicit_flow_on_account_deletion_page(self) -> None:
        page = VALID_ACCOUNT_DELETION_WEB_SOURCE.replace('flowType: "pkce"', 'flowType: "implicit"')
        errors = validate_configuration(VALID_CONFIG, VALID_DELETE_ACCOUNT_SOURCE, page)
        self.assertTrue(any("flowType" in error for error in errors))

    def test_rejects_missing_pkce_callback_detection(self) -> None:
        page = VALID_ACCOUNT_DELETION_WEB_SOURCE.replace("detectSessionInUrl: true,", "")
        errors = validate_configuration(VALID_CONFIG, VALID_DELETE_ACCOUNT_SOURCE, page)
        self.assertTrue(any("detectSessionInUrl" in error for error in errors))

if __name__ == "__main__":
    unittest.main()
