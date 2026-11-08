#!/usr/bin/env python3
"""Regression checks for the public-web search Supabase Edge Function."""
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FUNCTION = ROOT / "supabase" / "functions" / "studiomesa-web-search" / "index.ts"


class WebSearchEdgeFunctionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = FUNCTION.read_text(encoding="utf-8")

    def test_requires_auth_and_only_accepts_short_public_questions(self) -> None:
        self.assertIn('startsWith("Bearer ")', self.source)
        self.assertIn("query.length < 3 || query.length > 200", self.source)
        self.assertIn("queryContainsPrivateData(query)", self.source)

    def test_tavily_is_primary_and_uses_server_side_bearer_key(self) -> None:
        self.assertIn('Deno.env.get("TAVILY_API_KEY")', self.source)
        self.assertIn('"Authorization": `Bearer ${tavilyApiKey}`', self.source)
        self.assertIn('fetch("https://api.tavily.com/search"', self.source)
        self.assertIn("search_depth: \"basic\"", self.source)
        self.assertIn("max_results: 5", self.source)
        self.assertIn("include_raw_content: false", self.source)

    def test_searxng_is_fallback_and_unconfigured_search_fails_closed(self) -> None:
        self.assertIn("publicSearxngUrl()", self.source)
        self.assertIn("if (!tavilyApiKey && !searxngEndpoint)", self.source)
        self.assertIn("SEARCH_PROVIDER_NOT_CONFIGURED", self.source)

    def test_provider_failures_are_explicit(self) -> None:
        for code in (
            "TAVILY_AUTH_FAILED",
            "TAVILY_RATE_LIMITED",
            "TAVILY_PROVIDER_ERROR",
            "SEARXNG_RATE_LIMITED",
            "SEARCH_PROVIDER_UNAVAILABLE",
        ):
            with self.subTest(code=code):
                self.assertIn(code, self.source)

    def test_search_results_are_https_and_bounded(self) -> None:
        self.assertIn('parsed.protocol !== "https:"', self.source)
        self.assertIn("rawResults.slice(0, 5)", self.source)
        self.assertIn("content.slice(0, 1200)", self.source)


if __name__ == "__main__":
    unittest.main()
