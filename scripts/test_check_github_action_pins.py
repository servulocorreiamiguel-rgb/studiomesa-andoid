#!/usr/bin/env python3
"""Unit tests for the repository's immutable GitHub Actions pin audit."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from check_github_action_pins import (  # noqa: E402
    extract_action_refs,
    is_immutable_action_ref,
)


class GitHubActionPinAuditTests(unittest.TestCase):
    def test_extracts_action_refs_and_line_numbers(self):
        workflow = """name: CI
jobs:
  build:
    steps:
      - uses: actions/checkout@0123456789abcdef0123456789abcdef01234567 # v4
      - uses: ./.github/actions/local-check
"""
        self.assertEqual(
            extract_action_refs(workflow),
            [
                (5, "actions/checkout@0123456789abcdef0123456789abcdef01234567"),
                (6, "./.github/actions/local-check"),
            ],
        )

    def test_full_40_character_sha_is_accepted(self):
        self.assertTrue(
            is_immutable_action_ref(
                "actions/checkout@0123456789abcdef0123456789abcdef01234567"
            )
        )

    def test_tags_and_short_shas_are_rejected(self):
        self.assertFalse(is_immutable_action_ref("actions/checkout@v4"))
        self.assertFalse(
            is_immutable_action_ref("actions/checkout@0123456789abcdef")
        )

    def test_local_action_is_accepted(self):
        self.assertTrue(is_immutable_action_ref("./.github/actions/local-check"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
