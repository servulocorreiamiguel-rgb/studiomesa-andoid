#!/usr/bin/env python3
"""Unit tests for the Android security baseline CI guard."""
import unittest

from check_android_security_baseline import inspect_android_security_baseline

SAFE_MANIFEST = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <uses-permission android:name="android.permission.INTERNET" />
  <application android:allowBackup="false" android:usesCleartextTraffic="false">
    <activity android:name=".MainActivity" android:exported="true">
      <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
      </intent-filter>
    </activity>
  </application>
</manifest>
"""
SAFE_API = """private const val BASE_URL = "https://example.supabase.co"
private const val API_KEY = "sb_publishable_not_a_secret"
"""
SAFE_SESSION = """KeyStore.getInstance("AndroidKeyStore")
Cipher.getInstance("AES/GCM/NoPadding")
"""
SAFE_GRADLE = """defaultConfig { targetSdk = 36 }
"""


class AndroidSecurityBaselineTests(unittest.TestCase):
    def inspect(self, manifest=SAFE_MANIFEST, api=SAFE_API, session=SAFE_SESSION,
                gradle=SAFE_GRADLE, sources=None):
        return inspect_android_security_baseline(manifest, api, session, gradle, sources)

    def test_secure_baseline_passes_and_public_publishable_key_is_allowed(self):
        self.assertEqual([], self.inspect())

    def test_flags_backup_cleartext_and_unreviewed_permissions(self):
        manifest = SAFE_MANIFEST.replace('android:allowBackup="false"', 'android:allowBackup="true"')
        manifest = manifest.replace('android:usesCleartextTraffic="false"', 'android:usesCleartextTraffic="true"')
        manifest = manifest.replace(
            '<uses-permission android:name="android.permission.INTERNET" />',
            '<uses-permission android:name="android.permission.INTERNET" />'
            '<uses-permission android:name="android.permission.READ_CONTACTS" />',
        )
        errors = self.inspect(manifest=manifest)
        self.assertTrue(any("allowBackup" in error for error in errors))
        self.assertTrue(any("Cleartext" in error or "cleartext" in error for error in errors))
        self.assertTrue(any("READ_CONTACTS" in error for error in errors))

    def test_rejects_unexpected_exported_component(self):
        manifest = SAFE_MANIFEST.replace(
            "</application>",
            '<service android:name=".LeakyService" android:exported="true" />'
            "</application>",
        )
        self.assertTrue(any("LeakyService" in error for error in self.inspect(manifest=manifest)))

    def test_rejects_secret_key_or_private_key_in_app_sources(self):
        errors = self.inspect(sources=SAFE_API + '\nval key = "sb_secret_do_not_ship_this"')
        self.assertTrue(any("secret key prefix" in error for error in errors))
        errors = self.inspect(sources=SAFE_API + "\n-----BEGIN PRIVATE KEY-----")
        self.assertTrue(any("private key pattern" in error for error in errors))

    def test_requires_android_keystore_aes_gcm_and_modern_target_sdk(self):
        errors = self.inspect(session='Cipher.getInstance("AES/CBC/PKCS5Padding")', gradle="targetSdk = 34")
        self.assertTrue(any("Keystore-backed AES-GCM" in error for error in errors))
        self.assertTrue(any("targetSdk" in error for error in errors))

    def test_invalid_manifest_fails_closed(self):
        self.assertTrue(any("valid XML" in error for error in self.inspect(manifest="<manifest")))


if __name__ == "__main__":
    unittest.main()
