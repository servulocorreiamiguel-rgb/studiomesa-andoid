#!/usr/bin/env python3
"""Static contract tests for the StudioMesa free-only multi-model AI router."""

from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
EDGE = (ROOT / "supabase/functions/studiomesa-ai-router/index.ts").read_text()
API = (ROOT / "app/src/main/java/pt/studiomesa/app/SupabaseApi.kt").read_text()
ROUTED_API = API.split("fun generateRoutedAssistantReply(", 1)[1].split("fun generateAdvancedAssistantReply(", 1)[0]
ACTIVITY = (ROOT / "app/src/main/java/pt/studiomesa/app/MainActivity.kt").read_text()


class MultiModelRouterContractTests(unittest.TestCase):
    def test_every_cloud_model_is_explicitly_free(self):
        model_ids = re.findall(r'"((?:deepseek|qwen|openai)/[^"]+)"', EDGE)
        self.assertTrue(model_ids)
        self.assertTrue(all(model.endswith(":free") for model in model_ids))

    def test_free_router_is_final_fallback(self):
        self.assertIn('"openrouter/free"', EDGE)
        self.assertIn('billing: "free_only"', EDGE)
        self.assertIn("No provider error details or prompts are logged", EDGE)

    def test_routes_complex_reasoning_to_deepseek(self):
        chain = EDGE.split("reasoning:", 1)[1].split("],", 1)[0]
        self.assertIn("deepseek/deepseek-r1:free", chain)

    def test_routes_programming_to_qwen(self):
        chain = EDGE.split("coding:", 1)[1].split("],", 1)[0]
        self.assertIn("qwen/qwen3.8-27b:free", chain)

    def test_gpt_oss_is_an_optional_free_alternative(self):
        self.assertIn("openai/gpt-oss-120b:free", EDGE)

    def test_requires_authenticated_owner(self):
        self.assertIn("authenticateOwner(authorization, supabaseUrl, anonKey, restaurantId)", EDGE)
        self.assertIn('searchParams.set("role", "eq.owner")', EDGE)

    def test_private_context_is_rejected_and_not_forwarded(self):
        self.assertIn('"restaurant_context" in body', EDGE)
        self.assertIn('"memory" in body', EDGE)
        self.assertNotIn("AdvancedAssistantCloudContextBuilder", ROUTED_API)
        self.assertNotIn("restaurantContext", ROUTED_API)

    def test_provider_privacy_constraints_are_required(self):
        self.assertIn('data_collection: "deny"', EDGE)
        self.assertIn("zdr: true", EDGE)

    def test_sensitive_identifiers_are_blocked(self):
        self.assertIn("containsSensitiveIdentifier(content)", EDGE)
        self.assertIn(r"\b\d{9}\b", EDGE)

    def test_client_sends_user_messages_only(self):
        self.assertIn('require(message.optString("role") == "user")', API)
        self.assertIn("generateRoutedAssistantReply(", ACTIVITY)
        self.assertNotIn("generateAdvancedAssistantReply(", ACTIVITY)

    def test_no_paid_fallback(self):
        self.assertIn("Não foi utilizado nenhum modelo pago", EDGE)
        self.assertIn("Não existe fallback para modelos pagos", ACTIVITY)


if __name__ == "__main__":
    unittest.main(verbosity=2)
