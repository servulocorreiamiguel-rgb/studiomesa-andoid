#!/usr/bin/env python3
"""Ensure public StudioMesa Edge Functions stay enabled and JWT protected."""
from __future__ import annotations

import unittest
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "supabase" / "config.toml"


class EdgeFunctionConfigTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        with CONFIG.open("rb") as stream:
            cls.config = tomllib.load(stream)

    def test_general_assistant_is_enabled_and_requires_supabase_jwt(self) -> None:
        settings = self.config["functions"]["studiomesa-assistant"]
        self.assertIs(settings["enabled"], True)
        self.assertIs(settings["verify_jwt"], True)

    def test_public_search_is_enabled_and_requires_supabase_jwt(self) -> None:
        settings = self.config["functions"]["studiomesa-web-search"]
        self.assertIs(settings["enabled"], True)
        self.assertIs(settings["verify_jwt"], True)

    def test_public_website_webhook_remains_separately_configured(self) -> None:
        settings = self.config["functions"]["account-deletion-web"]
        self.assertIs(settings["enabled"], True)
        self.assertIs(settings["verify_jwt"], False)


if __name__ == "__main__":
    unittest.main()
