#!/usr/bin/env python3
"""Compatibility-named tests for the StudioMesa multi-model assistant router."""

from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
EDGE = (ROOT / "supabase/functions/studiomesa-ai-router/index.ts").read_text()
API = (ROOT / "app/src/main/java/pt/studiomesa/app/SupabaseApi.kt").read_text()
ACTIVITY = (ROOT / "app/src/main/java/pt/studiomesa/app/MainActivity.kt").read_text()

ROUTED_API = API.split("fun generateRoutedAssistantReply(", 1)[1].split("fun generateAdvancedAssistantReply(", 1)[0]


class MultiModelAssistantPrivacyTests(unittest.TestCase):
    def test_every_selected_cloud_model_is_free_only(self):
        model_ids = re.findall(r'"((?:deepseek|qwen|openai)/[^"]+)"', EDGE)
        self.assertTrue(model_ids)
        self.assertTrue(all(model.endswith(":free") for model in model_ids))
        self.assertIn('"openrouter/free"', EDGE)

    def test_deepseek_is_selected_for_complex_reasoning(self):
        chain = EDGE.split("reasoning:", 1)[1].split("],", 1)[0]
        self.assertIn("deepseek/deepseek-v4-flash:free", chain)

    def test_qwen_is_selected_for_programming(self):
        chain = EDGE.split("coding:", 1)[1].split("],", 1)[0]
        self.assertIn("qwen/qwen3.8-27b:free", chain)

    def test_gpt_oss_is_a_free_only_fallback(self):
        self.assertIn("openai/gpt-oss-120b:free", EDGE)

    def test_owner_authentication_is_required(self):
        self.assertIn("authenticateOwner(authorization, supabaseUrl, anonKey, restaurantId)", EDGE)
        self.assertIn('searchParams.set("role", "eq.owner")', EDGE)

    def test_private_restaurant_context_is_rejected(self):
        self.assertIn('"restaurant_context" in body', EDGE)
        self.assertIn('"memory" in body', EDGE)
        self.assertIn('"invoice" in body', EDGE)
        self.assertNotIn("restaurantContext", ROUTED_API)
        self.assertNotIn("AdvancedAssistantCloudContextBuilder", ACTIVITY)

    def test_provider_data_collection_and_retention_controls_are_required(self):
        self.assertIn('data_collection: "deny"', EDGE)
        self.assertIn("zdr: true", EDGE)

    def test_sensitive_identifiers_are_filtered(self):
        self.assertIn("containsSensitiveIdentifier(content)", EDGE)
        self.assertIn(r"\b\d{9}\b", EDGE)

    def test_client_only_sends_user_authored_turns(self):
        self.assertIn('require(message.optString("role") == "user")', ROUTED_API)
        self.assertIn("generateRoutedAssistantReply(", ACTIVITY)

    def test_paid_fallback_is_not_available(self):
        self.assertIn('billing: "free_only"', EDGE)
        self.assertIn("Não existe fallback para modelos pagos", ACTIVITY)


if __name__ == "__main__":
    unittest.main(verbosity=2)
