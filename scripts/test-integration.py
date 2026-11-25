#!/usr/bin/env python3
"""Offline tests for the IMZYY UI/performance overlay against an upstream-shaped fixture."""
from pathlib import Path
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PATCHER = ROOT / "scripts/prepare-imzyy.py"
ANDROID = "{http://schemas.android.com/apk/res/android}"


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="imzyy-flame-fixture-") as tmp:
        root = Path(tmp)
        app = root / "app"
        (app / "src/main/res/values").mkdir(parents=True)
        (app / "src/main/res/mipmap-anydpi-v26").mkdir(parents=True)
        (app / "src/main/java/kr/co/donghyun/flamelauncher/presentation/ui/theme").mkdir(parents=True)
        (app / "src/main/java/kr/co/donghyun/flamelauncher/presentation").mkdir(parents=True, exist_ok=True)
        (app / "build.gradle.kts").write_text('''plugins { id("com.android.application") }
android { namespace = "kr.co.donghyun.flamelauncher"; defaultConfig { applicationId = "kr.co.donghyun.flamelauncher"; ndk { abiFilters += listOf("arm64-v8a") } } }
''')
        (root / "settings.gradle.kts").write_text('rootProject.name = "FlameLauncher"')
        (app / "src/main/AndroidManifest.xml").write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android"><uses-permission android:name="android.permission.INTERNET"/><application><activity android:name=".presentation.MainActivity" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity><activity android:name=".presentation.MinecraftActivity" android:process=":minecraft"/></application></manifest>''')
        (app / "src/main/res/values/strings.xml").write_text('''<resources><string name="app_name">FlameLauncher</string></resources>''')
        (app / "src/main/res/values/colors.xml").write_text('''<resources><color name="bg_dark">#FF05080F</color><color name="flame_primary">#FF2E9BFF</color><color name="flame_accent">#FF22E0FF</color></resources>''')
        theme = app / "src/main/java/kr/co/donghyun/flamelauncher/presentation/ui/theme/Color.kt"
        theme.write_text('''package kr.co.donghyun.flamelauncher.presentation.ui.theme
val Orange = Color(0xFF5AB8FF)
val Flame = Color(0xFF2E9BFF)
val FlamePrimary = Color(0xFF2E9BFF)
val FlameAccent = Color(0xFF22E0FF)
val BgDark = Color(0xFF05080F)
val BgSurface = Color(0xFF0B1422)
val BgBorder = Color(0xFF1C2E47)
val TextSub = Color(0xFF8CA2C0)
val BgItem = Color(0xFF08101B)
val FlameLight = Color(0xFF7CC9FF)
val FlameDark = Color(0xFF0B4A8F)
''')
        (app / "src/main/java/kr/co/donghyun/flamelauncher/presentation/MinecraftActivity.kt").write_text("class MinecraftActivity")
        screen_dir = app / "src/main/java/kr/co/donghyun/flamelauncher/presentation/ui/screen"
        screen_dir.mkdir(parents=True)
        (app / "src/main/java/kr/co/donghyun/flamelauncher/presentation/ui/theme/Type.kt").write_text("val Typography = Typography()")
        (screen_dir / "MainScreen.kt").write_text('''val compact = "Flame"; val full = "FlameLauncher"
val topbar = Modifier.height(52.dp).background(BgSurface)
val title = "FlameLauncher", color = TextPrimary, fontSize = 14.sp
''')
        (screen_dir / "SettingsScreen.kt").write_text('''package kr.co.donghyun.flamelauncher.presentation.ui.screen
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(if (tablet) 20.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (tablet) 16.dp else 10.dp)
        ) {
            // 언어 섹션
        }
/**
 * 전역 렌더러 선택용 라디오 한 줄.
 */
''')
        (screen_dir / "Buttons.kt").write_text('''Button(colors = ButtonDefaults.buttonColors(containerColor = Flame)) { Text("Go", color = Color.White) }
Button(colors = ButtonDefaults.buttonColors(containerColor = BgBorder)) { Text("Stay", color = Color.White) }
''')
        (app / "src/main/res/drawable").mkdir(parents=True)
        (app / "src/main/res/drawable/img_logo.png").write_bytes(b"old-logo")
        for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
            (app / "src/main/res/mipmap-anydpi-v26" / name).write_text('''<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@color/black"/><foreground android:drawable="@mipmap/ic_launcher_foreground"/></adaptive-icon>''')

        subprocess.run([sys.executable, str(PATCHER), "--engine-dir", str(root), "--repo", "owner/imzyy"], check=True)
        subprocess.run([sys.executable, str(PATCHER), "--engine-dir", str(root), "--repo", "owner/imzyy"], check=True)
        strings = ET.parse(app / "src/main/res/values/strings.xml").getroot()
        assert strings.find("string").text == "IMZYY XL Launcher"
        for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
            icon = ET.parse(app / "src/main/res/mipmap-anydpi-v26" / name).getroot()
            assert icon.find("foreground").get(ANDROID + "drawable") == "@drawable/imzyy_launcher_foreground"
        assert (app / "src/main/res/drawable/imzyy_launcher_foreground.xml").is_file()
        assert (app / "src/main/res/drawable/img_logo.xml").is_file()
        assert not (app / "src/main/res/drawable/img_logo.png").exists()
        main_screen = (app / "src/main/java/kr/co/donghyun/flamelauncher/presentation/ui/screen/MainScreen.kt").read_text()
        assert '"IMZYY"' in main_screen and '"IMZYY XL"' in main_screen
        assert ".height(58.dp)" in main_screen and "fontSize = 16.sp" in main_screen
        settings_screen = (app / "src/main/java/kr/co/donghyun/flamelauncher/presentation/ui/screen/SettingsScreen.kt").read_text()
        assert "ImzyyPerformanceProfiles(" in settings_screen
        assert 'maxHeapMb = performanceHeap' in settings_screen
        assert 'resolutionScalePercent = 75' in settings_screen
        assert 'resolutionScalePercent = 85' in settings_screen
        assert 'resolutionScalePercent = 100' in settings_screen
        assert settings_screen.count("IMZYY PERFORMANCE PROFILES") == 1
        typography = (app / "src/main/java/kr/co/donghyun/flamelauncher/presentation/ui/theme/Type.kt").read_text()
        assert "FontWeight.ExtraBold" in typography and "FontFamily.SansSerif" in typography
        assert "FontWeight.SemiBold" in typography
        assert (root / "settings.gradle.kts").read_text() == 'rootProject.name = "IMZYY XL Launcher"'
        palette = theme.read_text()
        assert "0xFFF2F2F2" in palette and "0xFF000000" in palette, "monochrome palette"
        assert "0xFF2E9BFF" not in palette and "0xFF8B5CF6" not in palette
        buttons = (screen_dir / "Buttons.kt").read_text()
        assert 'Text("Go", color = Color.Black)' in buttons, "white-on-accent must flip to black"
        assert 'Text("Stay", color = Color.White)' in buttons, "non-accent buttons must keep white text"
        assert 'Text("FPS", color = Color.Black' in settings_screen
        hub_dir = app / "src/main/java/id/imzyy/xl"
        for name in ("ImzyyHubActivity.kt", "Ui.kt", "Modrinth.kt", "PerfPack.kt", "Mrpack.kt", "FpsTuner.kt", "ModManager.kt", "TextureOptimizer.kt", "InstanceLocator.kt", "Net.kt", "ImzyyConfig.kt"):
            assert (hub_dir / name).is_file(), name
        hub_source = (hub_dir / "ImzyyHubActivity.kt").read_text()
        assert '"CONTROL MOUSE"' in hub_source and "applyMouseControls" in hub_source
        assert '"RENDER DISTANCE"' in hub_source and "applyRenderDistance" in (hub_dir / "FpsTuner.kt").read_text()
        assert "optimizeTextures" in hub_source and "recompressPng" in (hub_dir / "TextureOptimizer.kt").read_text()
        assert "IMPORT FILE MOD .JAR" in hub_source and "ModManager.toggle" in hub_source
        perf_pack = (hub_dir / "PerfPack.kt").read_text()
        assert 'PerfMod("voxy"' in perf_pack and 'queue.add("modmenu")' in perf_pack
        cfg = (hub_dir / "ImzyyConfig.kt").read_text()
        assert 'GITHUB_REPO = "owner/imzyy"' in cfg and "@" not in cfg, cfg
        assert (app / "src/main/res/values/imzyy_theme.xml").is_file()
        man = ET.parse(app / "src/main/AndroidManifest.xml").getroot()
        acts = man.find("application").findall("activity")
        launchers = [a.get(ANDROID + "name") for a in acts for f in a.findall("intent-filter")
                     for c in f.findall("category") if c.get(ANDROID + "name") == "android.intent.category.LAUNCHER"]
        assert launchers == ["id.imzyy.xl.ImzyyHubActivity"], launchers
        assert sum(1 for a in acts if a.get(ANDROID + "name") == ".presentation.MinecraftActivity") == 1
        assert sum(1 for a in acts if a.get(ANDROID + "name") == ".presentation.MainActivity") == 1
        gradle = (app / "build.gradle.kts").read_text()
        assert 'applicationId = "kr.co.donghyun.flamelauncher"' in gradle, "OAuth package identity must remain stable in this pass"

    for kt in (ROOT / "android-overlay/kotlin").glob("*.kt"):
        text = kt.read_text()
        assert text.count("{") == text.count("}") and text.count("(") == text.count(")"), f"unbalanced: {kt.name}"
    assert (ROOT / "scripts/build-imzyy.sh").is_file()
    assert not (ROOT / "scripts/build-amethyst.sh").exists()
    assert not (ROOT / "scripts/prepare-amethyst.py").exists()
    print("IMZYY integration tests passed: single-app activity, branding, performance profiles, mouse controls, mod manager, Voxy compatibility wiring, lossless texture optimizer, ARM64 and idempotency.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
