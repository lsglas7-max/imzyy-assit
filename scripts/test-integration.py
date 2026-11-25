#!/usr/bin/env python3
"""Offline fixture test for the Amethyst overlay patcher."""
from __future__ import annotations
import os
import subprocess
import sys
import tempfile
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PATCHER = ROOT / "scripts/prepare-amethyst.py"
ANDROID = "{http://schemas.android.com/apk/res/android}"


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="imzyy-amethyst-test-") as tmp:
        engine = Path(tmp) / "engine"
        app = engine / "app_pojavlauncher"
        java = app / "src/main/java/net/kdt/pojavlaunch"
        java.mkdir(parents=True)
        (engine / "settings.gradle").write_text("rootProject.name='fixture'\n")
        manifest = app / "src/main/AndroidManifest.xml"
        manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <application android:name="net.kdt.pojavlaunch.PojavApplication" android:label="Amethyst">
    <activity android:name="net.kdt.pojavlaunch.TestStorageActivity" android:exported="true">
      <intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter>
    </activity>
    <activity android:name=".LauncherActivity" android:exported="false"/>
  </application>
</manifest>''', encoding="utf-8")
        (java / "TestStorageActivity.java").write_text('''package net.kdt.pojavlaunch;
import android.content.Intent;
class TestStorageActivity { void exit() { new Intent(this, LauncherActivity.class); } }
''', encoding="utf-8")
        gradle = app / "build.gradle"
        gradle.write_text("""plugins { id 'com.android.application' }
android {
    defaultConfig {
        minSdkVersion 21
    }
}
dependencies {
}
""", encoding="utf-8")

        env = os.environ.copy()
        env["IMZYY_ABI_FILTER"] = "arm64-v8a"
        command = [sys.executable, str(PATCHER), "--engine-dir", str(engine), "--project-root", str(ROOT)]
        subprocess.run(command, check=True, env=env)
        subprocess.run(command, check=True, env=env)  # Idempotency check.

        xml = ET.parse(manifest).getroot()
        application = xml.find("application")
        assert application is not None
        assert application.get(ANDROID + "name") == "net.kdt.pojavlaunch.PojavApplication"
        test_storage = next(x for x in application if x.tag == "activity" and x.get(ANDROID + "name", "").endswith("TestStorageActivity"))
        filters = test_storage.findall("intent-filter")
        assert any(any(a.get(ANDROID + "name") == "android.intent.action.MAIN" for a in f.findall("action")) and
                   any(c.get(ANDROID + "name") == "android.intent.category.LAUNCHER" for c in f.findall("category")) for f in filters)
        dashboard = next(x for x in application if x.tag == "activity" and x.get(ANDROID + "name") == "com.imzyy.xllauncher.MainActivity")
        assert dashboard.get(ANDROID + "exported") == "false"
        assert not dashboard.findall("intent-filter")

        source = (java / "TestStorageActivity.java").read_text(encoding="utf-8")
        assert "com.imzyy.xllauncher.MainActivity.class" in source
        assert "LauncherActivity.class" not in source

        gradle_text = gradle.read_text(encoding="utf-8")
        assert "minSdkVersion 26" in gradle_text
        assert "abiFilters 'arm64-v8a'" in gradle_text
        assert gradle_text.count("androidx.documentfile:documentfile") == 1
        assert "commons-compress" not in gradle_text
        assert not (ROOT / "web").exists(), "Android-only project must not contain a website frontend"

        launcher_source = (ROOT / "android/app/src/main/java/com/imzyy/xllauncher/MainActivity.kt").read_text(encoding="utf-8")
        assert "android.webkit.WebView" not in launcher_source
        assert "DecelerateInterpolator" in launcher_source and "OnPreDrawListener" in launcher_source
        assert "OvershootInterpolator" in launcher_source and "pressable()" in launcher_source
        assert '"Play Minecraft"' in launcher_source
        assert "WebView" not in launcher_source and "setJavaScriptEnabled" not in launcher_source
        ui_source = (ROOT / "android/app/src/main/java/com/imzyy/xllauncher/ui/UiKit.kt").read_text(encoding="utf-8")
        assert "class Meter" in ui_source and "class PulseDot" in ui_source and "class DotGridDrawable" in ui_source
        assert "GradientDrawable.Orientation" not in launcher_source, "Flat design: no gradient backgrounds"
        assert 'thread(name = "IMZYY-home-probe")' in launcher_source, "Disk probes must stay off the main thread"
        assert "engineChecks" in launcher_source
        assert (ROOT / "scripts/install-android-sdk-components.sh").is_file()
        assert "clearLauncherCache" in launcher_source and "cacheDir.deleteRecursively()" in launcher_source
        bootstrap_source = (ROOT / "android/app/src/main/java/com/imzyy/xllauncher/engine/AmethystBootstrapper.kt").read_text(encoding="utf-8")
        workflow = (ROOT / ".github/workflows/android-debug.yml").read_text(encoding="utf-8")
        assert "__amethyst_preferences" in launcher_source
        assert "LauncherPreferences" in launcher_source and "loadPreferences" in launcher_source
        assert '"amethyst-root/"' in launcher_source and '"amethyst-active-profile/"' in launcher_source
        assert '"amethyst-controls/"' in launcher_source
        assert "isSensitiveAmethystKey" in launcher_source and "isSensitiveAmethystPath" in launcher_source
        assert "defaultGameDirectory" in bootstrap_source and "controlMapDirectory" in bootstrap_source
        assert "jre8-pojav" in workflow and "arm64-v8a" in workflow
        assert "TestStorageActivity" in workflow or "scripts/build-amethyst.sh build" in workflow
        assert "android-actions/setup-android@v4" in workflow
        assert "cmdline-tools-version: '15859902'" in workflow
        assert "packages: 'platform-tools'" in workflow
        assert "setup-android@v3" not in workflow
        assert "sdkmanager tools" not in workflow
        assert "install-android-sdk-components.sh" in workflow
        assert "platforms;android-37" not in workflow

        # Reproduce a fresh runner with the API 37.0 minor-version package name.
        sdk_root = Path(tmp) / "android-sdk"
        mock_sdkmanager = Path(tmp) / "mock-sdkmanager"
        mock_sdkmanager.write_text("""#!/usr/bin/env bash
set -euo pipefail
mkdir -p \"$MOCK_SDK_ROOT/platforms/android-37.0\" \"$MOCK_SDK_ROOT/ndk/27.3.13750724\"
printf 'mock android jar' > \"$MOCK_SDK_ROOT/platforms/android-37.0/android.jar\"
printf 'Pkg.Revision = 27.3.13750724\n' > \"$MOCK_SDK_ROOT/ndk/27.3.13750724/source.properties\"
""", encoding="utf-8")
        mock_sdkmanager.chmod(0o755)
        install_env = os.environ.copy()
        install_env["ANDROID_SDK_ROOT"] = str(sdk_root)
        install_env["SDKMANAGER"] = str(mock_sdkmanager)
        install_env["MOCK_SDK_ROOT"] = str(sdk_root)
        subprocess.run(["bash", str(ROOT / "scripts/install-android-sdk-components.sh")], check=True, env=install_env)
        api_alias = sdk_root / "platforms/android-37"
        assert api_alias.is_symlink()
        assert os.readlink(api_alias) == "android-37.0"
        assert (api_alias / "android.jar").is_file()
        assert (sdk_root / "ndk/27.3.13750724/source.properties").is_file()

    print("Amethyst integration tests passed (native-only flat UI, animation, async probes, safe cache action, manifest, startup, Gradle, ABI, backup/restore, profile paths, control maps, JRE asset, SDK API-37 alias and idempotency).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
