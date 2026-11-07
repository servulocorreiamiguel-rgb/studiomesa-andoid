#!/usr/bin/env python3
"""Regression checks for the online StudioMesa assistant Edge Function."""
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FUNCTION = ROOT / "supabase" / "functions" / "studiomesa-assistant" / "index.ts"


class AssistantEdgeFunctionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = FUNCTION.read_text(encoding="utf-8")

    def test_gemini_prompt_is_a_complete_template_literal(self) -> None:
        self.assertIn("const prompt = `", self.source)
        self.assertIn("${message}", self.source)
        self.assertIn("${sourceText}", self.source)
        self.assertIn("`.trim();", self.source)
        self.assertIn("parts: [{ text: prompt }]", self.source)

    def test_uses_current_stable_gemini_flash_model_and_header_key(self) -> None:
        self.assertIn("gemini-3.8-flash:generateContent", self.source)
        self.assertIn('"x-goog-api-key": apiKey', self.source)
        self.assertNotIn("?key=${encodeURIComponent(apiKey)}", self.source)

    def test_provider_keys_are_read_only_from_server_environment(self) -> None:
        self.assertIn('Deno.env.get("GEMINI_API_KEY")', self.source)
        self.assertIn('Deno.env.get("TAVILY_API_KEY")', self.source)
        self.assertNotIn("sb_secret_", self.source)
        self.assertNotIn("SUPABASE_SERVICE_ROLE_KEY", self.source)

    def test_public_search_is_optional_and_fails_closed(self) -> None:
        self.assertIn("searchWeb && !tavilyKey", self.source)
        self.assertIn("searchWeb, geminiKey", self.source)
        self.assertIn("TAVILY_NOT_CONFIGURED", self.source)
        self.assertIn("TAVILY_RATE_LIMITED", self.source)
        self.assertIn("A pesquisa pública foi tentada mas não devolveu fontes utilizáveis", self.source)

    def test_request_requires_auth_and_rejects_common_identifiers(self) -> None:
        self.assertIn('startsWith("Bearer ")', self.source)
        self.assertIn("containsPersonalIdentifiers(message)", self.source)
        self.assertIn("message.length > 4000", self.source)

    def test_external_search_results_are_https_only(self) -> None:
        self.assertIn('parsed.protocol !== "https:"', self.source)
        self.assertIn("include_raw_content: false", self.source)
        self.assertIn("max_results: 5", self.source)


if __name__ == "__main__":
    unittest.main()
