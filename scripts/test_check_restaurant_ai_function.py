import unittest
from check_restaurant_ai_function import validate_ai_function

VALID_CONFIG = {"functions": {"restaurant-ai-assistant": {"verify_jwt": True}}}
VALID_SOURCE = r"""
Deno.serve(withSupabase({ auth: "user" }, async (req, ctx) => {
 const key = Deno.env.get("OPENAI_API_KEY");
 const enabled = Deno.env.get("STUDIOMESA_AI_ENABLED") === "true";
 const restaurant = await ctx.supabase.rpc("get_my_restaurant_details");
 if (restaurant.user_role !== "owner") return Response.json({});
 const quota = await ctx.supabase.rpc("consume_restaurant_ai_quota");
 const limit = MAX_BODY_BYTES;
 const timeout = AbortSignal.timeout(1000);
 // Nunca inventes ingredientes, alergénios, preços ou horários.
 const safe = containsCustomerContactData("input");
 return Response.json({ draft: "draft", max: MAX_OUTPUT_CHARS, review_required: true });
}));
"""
VALID_MIGRATION = r"""
CREATE TABLE IF NOT EXISTS private.restaurant_ai_usage ();
CREATE OR REPLACE FUNCTION public.consume_restaurant_ai_quota()
SECURITY DEFINER SET search_path TO ''
AS $function$
BEGIN
 IF (SELECT auth.uid()) IS NULL THEN RAISE EXCEPTION 'login'; END IF;
 SELECT rs.role = 'owner';
 v_daily_limit CONSTANT integer := 10;
END;
$function$;
REVOKE ALL ON TABLE private.restaurant_ai_usage FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.consume_restaurant_ai_quota() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.consume_restaurant_ai_quota() TO authenticated;
"""
VALID_MAIN = r"""
if (isPremium) {
 pages.add("Stock")
 pages.add("Análise")
 pages.add("Conteúdo IA")
}
SupabaseApi.restaurantAiStatus(backendToken())
SupabaseApi.generateRestaurantContent(backendToken(), type, title, facts)
"""
VALID_API = r"""
"/functions/v1/restaurant-ai-assistant"
"""

class RestaurantAIFunctionGuardTests(unittest.TestCase):
    def validate(self, config=VALID_CONFIG, source=VALID_SOURCE, migration=VALID_MIGRATION,
                 main=VALID_MAIN, api=VALID_API):
        return validate_ai_function(config, source, migration, main, api)

    def test_accepts_server_gated_owner_only_quota_and_review_only_design(self):
        self.assertEqual(self.validate(), [])

    def test_rejects_missing_jwt_verification(self):
        errors = self.validate(config={"functions": {"restaurant-ai-assistant": {"verify_jwt": False}}})
        self.assertTrue(any("verify_jwt = true" in error for error in errors))

    def test_rejects_provider_key_without_explicit_enable_flag(self):
        errors = self.validate(source=VALID_SOURCE.replace('Deno.env.get("STUDIOMESA_AI_ENABLED") === "true";', "true;"))
        self.assertTrue(any("feature flag" in error for error in errors))

    def test_rejects_missing_owner_check(self):
        errors = self.validate(source=VALID_SOURCE.replace('if (restaurant.user_role !== "owner") return Response.json({});', "return Response.json({});"))
        self.assertTrue(any("owner" in error for error in errors))

    def test_rejects_missing_quota_rpc(self):
        errors = self.validate(source=VALID_SOURCE.replace('await ctx.supabase.rpc("consume_restaurant_ai_quota")', "Promise.resolve({})"))
        self.assertTrue(any("quota" in error for error in errors))

    def test_rejects_unbounded_provider_timeout(self):
        errors = self.validate(source=VALID_SOURCE.replace("AbortSignal.timeout(1000)", "null"))
        self.assertTrue(any("timeout" in error for error in errors))

    def test_rejects_quota_function_without_auth_identity(self):
        errors = self.validate(migration=VALID_MIGRATION.replace("auth.uid()", "null"))
        self.assertTrue(any("authenticated user" in error for error in errors))

    def test_rejects_ai_page_in_non_premium_app(self):
        errors = self.validate(main='pages.add("Conteúdo IA")')
        self.assertTrue(any("Premium" in error for error in errors))

    def test_rejects_provider_secret_in_android_api(self):
        errors = self.validate(api='OPENAI_API_KEY = "sk-abcdefghijklmnop"')
        self.assertTrue(any("provider API key" in error for error in errors))

if __name__ == "__main__":
    unittest.main()
