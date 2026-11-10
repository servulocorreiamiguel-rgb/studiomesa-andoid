#!/usr/bin/env python3
"""Static security-contract tests for the StudioMesa hosted GPT-OSS assistant."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
EDGE = (ROOT / "supabase/functions/studiomesa-advanced-assistant/index.ts").read_text()
ACTIVITY = (ROOT / "app/src/main/java/pt/studiomesa/app/MainActivity.kt").read_text()
CONTEXT = (ROOT / "app/src/main/java/pt/studiomesa/app/AdvancedAssistantCloudContextBuilder.kt").read_text()
DOC = (ROOT / "docs/AI-ADVANCED-GPT-OSS-2026-11-10.md").read_text()
GRADLE = (ROOT / "app/build.gradle.kts").read_text()


class GptOssAssistantPrivacyTests(unittest.TestCase):
    def test_cloud_model_is_disabled_by_default_until_release_gates_are_met(self):
        self.assertIn(
            'providers.gradleProperty("studiomesaAdvancedAssistantCloudEnabled").orNull == "true"',
            GRADLE,
        )
        self.assertIn(
            'buildConfigField("boolean", "ADVANCED_ASSISTANT_CLOUD_ENABLED", advancedAssistantCloudEnabled.toString())',
            GRADLE,
        )
        self.assertIn("BuildConfig.ADVANCED_ASSISTANT_CLOUD_ENABLED", ACTIVITY)
        self.assertIn("if (advancedAssistantCloudAvailable())", ACTIVITY)
        self.assertIn("GPT-OSS 120B online ainda não está ativo nesta versão", ACTIVITY)

    def test_cloud_ai_consent_discloses_current_token_pricing(self):
        self.assertIn("USD 0,15 por milhão de tokens de entrada", ACTIVITY)
        self.assertIn("USD 0,60 por milhão de tokens de saída", ACTIVITY)
        self.assertIn("limites de despesa", ACTIVITY)

    def test_uses_the_expected_open_weight_model_and_hides_reasoning(self):
        self.assertIn('const MODEL_ID = "openai/gpt-oss-120b"', EDGE)
        self.assertIn("include_reasoning: false", EDGE)
        self.assertNotIn("reasoning_format:", EDGE)

    def test_requires_authenticated_owner_and_provider_secret(self):
        self.assertIn("authenticateOwner(authorization, supabaseUrl, anonKey, restaurantId)", EDGE)
        self.assertIn('searchParams.set("role", "eq.owner")', EDGE)
        self.assertIn('Deno.env.get("GROQ_API_KEY")', EDGE)
        self.assertNotIn("service_role", EDGE)

    def test_public_search_receives_only_current_question(self):
        self.assertIn("const publicSearch = explicitlyRequestsPublicSearch(currentQuestion)", EDGE)
        self.assertIn('{ type: "browser_search" }', EDGE)
        self.assertIn('{ role: "user", content: currentQuestion }', EDGE)
        self.assertIn("public_search_without_restaurant_context", EDGE)

    def test_code_execution_is_available_but_not_forced(self):
        self.assertIn('{ type: "code_interpreter" }', EDGE)
        self.assertIn("const tools = publicSearch", EDGE)
        self.assertIn('...(publicSearch ? { tool_choice: "required" } : {})', EDGE)

    def test_sensitive_identifiers_are_blocked_before_provider_call(self):
        self.assertIn("containsSensitiveIdentifier(content)", EDGE)
        self.assertIn("containsSensitiveIdentifier(contextText)", EDGE)
        self.assertIn(r"\b\d{9}\b", EDGE)

    def test_memory_is_short_and_requires_new_consent(self):
        self.assertIn("MAX_CONTEXT_MEMORY_CHARS", CONTEXT)
        self.assertIn('put("assistant_memory", safeMemorySnippet)', CONTEXT)
        self.assertIn("assistant_advanced_ai_enabled_v2_", ACTIVITY)
        self.assertIn("assistantChatStore.readMemory(scope)", ACTIVITY)
        self.assertIn("uma versão curta da Memória guardada", ACTIVITY)

    def test_private_documents_and_records_are_not_in_cloud_context(self):
        self.assertIn("Never includes supplier invoices, OCR, customer records", CONTEXT)
        self.assertNotIn('"invoice_document"', CONTEXT)
        self.assertIn("Nunca incluídos no payload do modelo", DOC)

    def test_search_privacy_and_vendor_dependencies_are_disclosed(self):
        self.assertIn("Browser Search", ACTIVITY)
        self.assertIn("Code Interpreter", ACTIVITY)
        self.assertIn("Zero Data Retention", ACTIVITY)
        self.assertIn("não são enviados histórico, Memória nem resumo privado", DOC)


if __name__ == "__main__":
    unittest.main(verbosity=2)
