#!/usr/bin/env python3
"""Fail CI when the Android client regresses core privacy/security defaults."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
BLOCKED_PERMISSIONS = {
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.READ_MEDIA_IMAGES",
    "android.permission.READ_MEDIA_VIDEO",
    "android.permission.READ_MEDIA_AUDIO",
    "android.permission.READ_CONTACTS",
    "android.permission.WRITE_CONTACTS",
    "android.permission.READ_SMS",
    "android.permission.RECEIVE_SMS",
    "android.permission.READ_CALL_LOG",
    "android.permission.READ_PHONE_STATE",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.CAMERA",
    "android.permission.RECORD_AUDIO",
    "android.permission.READ_CALENDAR",
    "android.permission.WRITE_CALENDAR",
}
COMPONENT_TAGS = {"activity", "activity-alias", "service", "receiver", "provider"}


def inspect_android_security_baseline(
    manifest_xml: str,
    api_source: str,
    session_source: str,
    gradle_source: str,
    application_sources: str | None = None,
) -> list[str]:
    errors: list[str] = []
    try:
        root = ET.fromstring(manifest_xml)
    except ET.ParseError as exc:
        return [f"AndroidManifest.xml is not valid XML: {exc}"]

    application = root.find("application")
    if application is None:
        return ["AndroidManifest.xml is missing the application element."]

    if application.get(ANDROID_NS + "allowBackup") != "false":
        errors.append('Set android:allowBackup="false" to prevent unmanaged app-data backup.')
    if application.get(ANDROID_NS + "usesCleartextTraffic") != "false":
        errors.append('Set android:usesCleartextTraffic="false"; API traffic must use TLS.')
    if application.get(ANDROID_NS + "debuggable") == "true":
        errors.append("The Android application must not be configured as debuggable in the manifest.")

    for permission in root.findall("uses-permission"):
        name = permission.get(ANDROID_NS + "name", "")
        if name in BLOCKED_PERMISSIONS:
            errors.append(f"Permission {name} requires an explicit privacy/security review before it is added.")

    for component in application.iter():
        local_tag = component.tag.rsplit("}", 1)[-1]
        if local_tag not in COMPONENT_TAGS:
            continue
        if component.get(ANDROID_NS + "exported") == "true":
            name = component.get(ANDROID_NS + "name", "")
            if not (local_tag == "activity" and name in {".MainActivity", "pt.studiomesa.app.MainActivity"}):
                errors.append(f"Unexpected exported Android component: {local_tag} {name or '(unnamed)'}.")

    base_url = re.search(r'BASE_URL\s*=\s*"([^"]+)"', api_source)
    if base_url is None or not base_url.group(1).startswith("https://"):
        errors.append("Supabase BASE_URL must be an explicit HTTPS URL.")
    if re.search(r'(?i)\bsb_secret_[A-Za-z0-9_-]+', application_sources or api_source):
        errors.append("A Supabase secret key prefix was found in Android application sources.")
    if re.search(
        r'(?i)\bservice_role\s*[:=]\s*["\'][^"\']{8,}["\']|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',
        application_sources or api_source,
    ):
        errors.append("A service_role credential or private key pattern was found in Android application sources.")

    if "AndroidKeyStore" not in session_source or "AES/GCM/NoPadding" not in session_source:
        errors.append("Session storage must retain Android Keystore-backed AES-GCM encryption.")

    target_sdk = re.search(r"\btargetSdk\s*=\s*(\d+)", gradle_source)
    if target_sdk is None or int(target_sdk.group(1)) < 35:
        errors.append("Review targetSdk: this app must target Android API 35 or newer.")

    return errors


def check_repository(root: Path) -> list[str]:
    manifest = root / "app/src/main/AndroidManifest.xml"
    api_file = root / "app/src/main/java/pt/studiomesa/app/SupabaseApi.kt"
    session_file = root / "app/src/main/java/pt/studiomesa/app/SecureSessionStore.kt"
    gradle_file = root / "app/build.gradle.kts"
    required = [manifest, api_file, session_file, gradle_file]
    missing = [path.relative_to(root).as_posix() for path in required if not path.is_file()]
    if missing:
        return [f"Required security baseline file is missing: {path}" for path in missing]

    source_files = [
        path for path in (root / "app/src/main").rglob("*")
        if path.is_file() and path.suffix in {".kt", ".java", ".xml"}
    ]
    application_sources = "\n".join(path.read_text(encoding="utf-8") for path in source_files)
    return inspect_android_security_baseline(
        manifest.read_text(encoding="utf-8"),
        api_file.read_text(encoding="utf-8"),
        session_file.read_text(encoding="utf-8"),
        gradle_file.read_text(encoding="utf-8"),
        application_sources,
    )


def main() -> int:
    errors = check_repository(Path.cwd())
    if errors:
        print("Android security baseline FAILED:")
        for error in errors:
            print(f" - {error}")
        return 1
    print("Android security baseline passed: backup/cleartext defaults, exported components, permissions, HTTPS, session storage and target SDK.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
