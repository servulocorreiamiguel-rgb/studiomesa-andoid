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
 const bodySize = MAX_BODY_BYTES;
 const bounded = isBodyWithinLimit("body");
 const requestStream = readBoundedUtf8Body(req.body, MAX_BODY_BYTES);
 const providerStream = readBoundedUtf8Body(providerResponse.body, 64 * 1024);
 const prompt = validateRequestPayload({});
 const output = validateGeneratedDraft("draft");
 const timeout = AbortSignal.timeout(1000);
 // Nunca inventes ingredientes, alergénios, preços ou horários.
 return Response.json({ draft: "draft", review_required: true });
}));
"""
VALID_VALIDATION = r"""
export const MAX_BODY_BYTES = 4096;
export const MAX_TITLE_CHARS = 120;
export const MAX_FACTS_CHARS = 1500;
export const MAX_OUTPUT_CHARS = 4000;
export async function readBoundedUtf8Body(stream: ReadableStream<Uint8Array> | null, maxBytes = MAX_BODY_BYTES) {
 const reader = stream.getReader();
 await reader.read();
 await reader.cancel("Body too large");
}
const EMAIL_PATTERN = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;
const PORTUGAL_PHONE_PATTERN = /(?:\+351)?9[1236]\d{7}/;
export function containsCustomerContactData(value: string) {
 return EMAIL_PATTERN.test(value) || PORTUGAL_PHONE_PATTERN.test(value);
}
Object.keys(body).some((key) => !["content_type", "title", "facts"].includes(key));
export function validateGeneratedDraft(value: unknown) { return value; }
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
if (isPremium && !isCustomerApp) {
 pages.add("Stock")
 pages.add("Análise")
 pages.add("Conteúdo IA")
}
RestaurantContentEngine.generate(type, title, facts)
O texto é processado no telemóvel e não é enviado para um fornecedor externo de IA.
"""
VALID_ENGINE = r"""
object RestaurantContentEngine {
 val allowedTypes = setOf("menu_description", "social_post", "campaign", "content_ideas")
 private val emailPattern = Regex("email")
 private val portuguesePhonePattern = Regex("phone")
 fun generate(contentType: String, title: String, facts: String): String {
  val cleanTitle = title.trim()
  val cleanFacts = facts.trim()
  require(contentType in allowedTypes)
  require(cleanTitle.length in 2..120)
  require(cleanFacts.length in 10..1500)
  require((cleanTitle + cleanFacts).toByteArray(Charsets.UTF_8).size <= 4096)
  require(!containsCustomerContactData(cleanFacts))
  return "draft"
 }
 fun containsCustomerContactData(value: String): Boolean = emailPattern.containsMatchIn(value) || portuguesePhonePattern.containsMatchIn(value)
}
"""
VALID_API = r'"/functions/v1/restaurant-ai-assistant"'

class RestaurantAIFunctionGuardTests(unittest.TestCase):
    def validate(self, config=VALID_CONFIG, source=VALID_SOURCE, validation=VALID_VALIDATION,
                 migration=VALID_MIGRATION, main=VALID_MAIN, api=VALID_API, engine=VALID_ENGINE):
        return validate_ai_function(config, source, validation, migration, main, api, engine)

    def test_accepts_server_gated_owner_only_quota_and_review_only_design(self):
        self.assertEqual(self.validate(), [])

    def test_rejects_missing_jwt_verification(self):
        errors = self.validate(config={"functions": {"restaurant-ai-assistant": {"verify_jwt": False}}})
        self.assertTrue(any("verify_jwt = true" in error for error in errors))

    def test_rejects_provider_key_without_explicit_enable_flag(self):
        errors = self.validate(source=VALID_SOURCE.replace('Deno.env.get("STUDIOMESA_AI_ENABLED") === "true";', "true;"))
        self.assertTrue(any("feature flag" in error for error in errors))

    def test_rejects_missing_owner_check(self):
        source = VALID_SOURCE.replace('if (restaurant.user_role !== "owner") return Response.json({});', "return Response.json({});")
        errors = self.validate(source=source)
        self.assertTrue(any("owner" in error for error in errors))

    def test_rejects_missing_quota_rpc(self):
        source = VALID_SOURCE.replace('await ctx.supabase.rpc("consume_restaurant_ai_quota")', "Promise.resolve({})")
        errors = self.validate(source=source)
        self.assertTrue(any("quota" in error for error in errors))

    def test_rejects_provider_secret_in_android_api(self):
        errors = self.validate(api='OPENAI_API_KEY = "sk-abcdefghijklmnop"')
        self.assertTrue(any("provider API key" in error for error in errors))

    def test_rejects_quota_function_without_auth_identity(self):
        errors = self.validate(migration=VALID_MIGRATION.replace("auth.uid()", "null"))
        self.assertTrue(any("authenticated user" in error for error in errors))

    def test_rejects_unbounded_provider_timeout(self):
        errors = self.validate(source=VALID_SOURCE.replace("AbortSignal.timeout(1000)", "null"))
        self.assertTrue(any("timeout" in error for error in errors))

    def test_rejects_unbounded_incoming_request_stream(self):
        source = VALID_SOURCE.replace(
            "readBoundedUtf8Body(req.body, MAX_BODY_BYTES)",
            "req.text()",
        )
        errors = self.validate(source=source)
        self.assertTrue(any("incoming bodies while streaming" in error for error in errors))

    def test_rejects_unbounded_provider_response_stream(self):
        source = VALID_SOURCE.replace(
            "readBoundedUtf8Body(providerResponse.body, 64 * 1024)",
            "providerResponse.json()",
        )
        errors = self.validate(source=source)
        self.assertTrue(any("provider response bodies while streaming" in error for error in errors))

    def test_rejects_validation_helper_without_incremental_stream_reader(self):
        validation = VALID_VALIDATION.replace("const reader = stream.getReader();", "const reader = stream;")
        errors = self.validate(validation=validation)
        self.assertTrue(any("consume a body stream incrementally" in error for error in errors))

    def test_rejects_missing_email_guard(self):
        errors = self.validate(validation=VALID_VALIDATION.replace("EMAIL_PATTERN", "EMAIL_CHECK"))
        self.assertTrue(any("email addresses" in error for error in errors))

    def test_rejects_missing_phone_guard(self):
        errors = self.validate(validation=VALID_VALIDATION.replace("PORTUGAL_PHONE_PATTERN", "PHONE_CHECK"))
        self.assertTrue(any("phone patterns" in error for error in errors))

    def test_rejects_ai_page_outside_premium_management(self):
        errors = self.validate(main='pages.add("Conteúdo IA")')
        self.assertTrue(any("Premium" in error for error in errors))


    def test_rejects_android_ui_that_calls_external_provider(self):
        main = VALID_MAIN + ' SupabaseApi.generateRestaurantContent(token, type, title, facts)'
        errors = self.validate(main=main)
        self.assertTrue(any("must not call the remote provider endpoint" in error for error in errors))

    def test_rejects_local_engine_with_network_dependency(self):
        errors = self.validate(engine=VALID_ENGINE + ' val endpoint = "https://example.invalid"')
        self.assertTrue(any("must not make network requests" in error for error in errors))

    def test_rejects_local_engine_without_contact_data_guard(self):
        engine = VALID_ENGINE.replace("containsCustomerContactData", "contactCheck")
        errors = self.validate(engine=engine)
        self.assertTrue(any("detect common customer contact details" in error for error in errors))

if __name__ == "__main__":
    unittest.main()
