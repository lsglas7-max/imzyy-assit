#!/usr/bin/env python3
"""Apply the IMZYY XL identity to a clean FlameLauncher Android checkout.

The upstream applicationId/namespace are intentionally preserved in this
first integration pass: changing Android OAuth callback identity without
re-registering and validating the Microsoft application can break sign-in.
The visible app name, launcher icon, palette and output artifact are branded.
"""
from __future__ import annotations
import argparse
import os
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID)

ICON = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#FF000000" android:pathData="M0,0h108v108h-108z" />
    <path android:fillColor="#FFFFFFFF" android:pathData="M20,26 L43,26 L54,45 L65,26 L88,26 L88,82 L68,82 L68,55 L54,76 L40,55 L40,82 L20,82 Z" />
    <path android:fillColor="#FF8C8C8C" android:pathData="M54,17 L63,32 L54,47 L45,32 Z" />
</vector>
'''


def require_file(path: Path) -> str:
    if not path.is_file():
        raise RuntimeError(f"File upstream tidak ditemukan: {path}")
    return path.read_text(encoding="utf-8")


def patch_strings(app: Path) -> None:
    patched_count = 0
    for path in (app / "src/main/res").rglob("strings.xml"):
        source = path.read_text(encoding="utf-8")
        patched, count = re.subn(
            r'(<string\s+name="app_name"[^>]*>).*?(</string>)',
            r'\g<1>IMZYY XL Launcher\g<2>',
            source,
            count=1,
            flags=re.DOTALL,
        )
        if count:
            path.write_text(patched, encoding="utf-8")
            patched_count += 1
    if patched_count == 0:
        raise RuntimeError("Resource string app_name tidak ditemukan.")


def patch_palette(app: Path) -> None:
    path = app / "src/main/java/kr/co/donghyun/flamelauncher/presentation/ui/theme/Color.kt"
    source = require_file(path)
    replacements = {
        "0xFF5AB8FF": "0xFFE6E6E6",
        "0xFF2E9BFF": "0xFFF2F2F2",
        "0xFF22E0FF": "0xFFBDBDBD",
        "0xFF7CC9FF": "0xFFD9D9D9",
        "0xFF0B4A8F": "0xFF3A3A3A",
        "0xFF05080F": "0xFF000000",
        "0xFF0B1422": "0xFF0A0A0A",
        "0xFF1C2E47": "0xFF262626",
        "0xFF8CA2C0": "0xFF8C8C8C",
        "0xFF08101B": "0xFF050505",
        # sources that were already patched by v2 (violet) are converted as well
        "0xFF9B7BFF": "0xFFE6E6E6",
        "0xFF8B5CF6": "0xFFF2F2F2",
        "0xFF080B16": "0xFF000000",
        "0xFF11182B": "0xFF0A0A0A",
        "0xFF293653": "0xFF262626",
        "0xFF9AA8C7": "0xFF8C8C8C",
        "0xFF0D1424": "0xFF050505",
        "0xFFC4B5FD": "0xFFD9D9D9",
        "0xFF4C1D95": "0xFF3A3A3A",
        "0xFF22D3EE": "0xFFBDBDBD",
        "0xFFA78BFA": "0xFFBDBDBD",
        "0xFFE8F2FF": "0xFFF5F5F5",
        "0xFF6D7F96": "0xFF6E6E6E",
        "0xFF2A1424": "0xFF1A1A1A",
        "0x9905080F": "0x99000000",
    }
    for old, new in replacements.items():
        source = source.replace(old, new)
    if "0xFFF2F2F2" not in source or "0xFF000000" not in source:
        raise RuntimeError("Palette Compose tidak berhasil dipatch.")
    path.write_text(source, encoding="utf-8")

    colors = app / "src/main/res/values/colors.xml"
    xml = require_file(colors)
    xml = xml.replace("#FF05080F", "#FF000000")
    xml = xml.replace("#FF0B1422", "#FF0A0A0A")
    xml = xml.replace("#FF2E9BFF", "#FFF2F2F2")
    xml = xml.replace("#FF22E0FF", "#FFBDBDBD")
    colors.write_text(xml, encoding="utf-8")


def patch_icon(app: Path) -> None:
    (app / "src/main/res/drawable").mkdir(parents=True, exist_ok=True)
    (app / "src/main/res/drawable/imzyy_launcher_foreground.xml").write_text(ICON, encoding="utf-8")
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        path = app / "src/main/res/mipmap-anydpi-v26" / name
        if not path.exists():
            continue
        tree = ET.parse(path)
        root = tree.getroot()
        foreground = root.find("foreground")
        if foreground is None:
            raise RuntimeError(f"Adaptive icon tidak memiliki foreground: {path}")
        foreground.set(f"{{{ANDROID}}}drawable", "@drawable/imzyy_launcher_foreground")
        tree.write(path, encoding="utf-8", xml_declaration=True)


def patch_in_app_branding(app: Path) -> None:
    main_screen = app / "src/main/java/kr/co/donghyun/flamelauncher/presentation/ui/screen/MainScreen.kt"
    source = require_file(main_screen)
    source, count_long = re.subn(r'"FlameLauncher"', '"IMZYY XL"', source)
    source, count_short = re.subn(r'"Flame"', '"IMZYY"', source)
    if (count_long < 1 and '"IMZYY XL"' not in source) or (count_short < 1 and '"IMZYY"' not in source):
        raise RuntimeError("Teks brand di app bar tidak ditemukan; upstream UI mungkin berubah.")
    source, topbar_count = re.subn(
        r"\.height\(52\.dp\)(\s*\.background\(BgSurface\))",
        r".height(58.dp)\1",
        source,
        count=1,
    )
    source = source.replace(
        '"IMZYY XL", color = TextPrimary, fontSize = 14.sp',
        '"IMZYY XL", color = TextPrimary, fontSize = 16.sp',
        1,
    )
    main_screen.write_text(source, encoding="utf-8")

    # Replace the upstream flame logo with a vector mark so the in-app header
    # and Android launcher icon use the same IMZYY identity.
    res = app / "src/main/res"
    for path in res.rglob("img_logo.*"):
        if path.is_file():
            path.unlink()
    logo = res / "drawable/img_logo.xml"
    logo.parent.mkdir(parents=True, exist_ok=True)
    logo.write_text(ICON.replace('android:width="108dp" android:height="108dp"',
                                 'android:width="48dp" android:height="48dp"'), encoding="utf-8")



def patch_typography(app: Path) -> None:
    theme_dir = app / "src/main/java/kr/co/donghyun/flamelauncher/presentation/ui/theme"
    type_file = theme_dir / "Type.kt"
    require_file(type_file)
    typography = '''package kr.co.donghyun.flamelauncher.presentation.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// IMZYY XL typography: strong hierarchy, compact tracking and a clean system sans face.
val Typography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = (-0.3).sp),
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
)
'''
    type_file.write_text(typography, encoding="utf-8")

    # Strengthen explicit text weights without touching layout or state logic.
    ui_root = app / "src/main/java/kr/co/donghyun/flamelauncher/presentation/ui"
    for source_file in ui_root.rglob("*.kt"):
        source = source_file.read_text(encoding="utf-8")
        updated = source.replace("FontWeight.Normal", "FontWeight.SemiBold")
        updated = updated.replace("FontWeight.Medium", "FontWeight.SemiBold")
        updated = re.sub(
            r"RoundedCornerShape\((8|10)\.dp\)",
            lambda match: f"RoundedCornerShape({10 if match.group(1) == '8' else 12}.dp)",
            updated,
        )
        if updated != source:
            source_file.write_text(updated, encoding="utf-8")


def patch_performance_profiles(app: Path) -> None:
    path = app / "src/main/java/kr/co/donghyun/flamelauncher/presentation/ui/screen/SettingsScreen.kt"
    source = require_file(path)
    marker = "// IMZYY PERFORMANCE PROFILES"
    if marker in source:
        return

    anchor = '''            verticalArrangement = Arrangement.spacedBy(if (tablet) 16.dp else 10.dp)
        ) {
            // 언어 섹션'''
    replacement = '''            verticalArrangement = Arrangement.spacedBy(if (tablet) 16.dp else 10.dp)
        ) {
            // IMZYY PERFORMANCE PROFILES
            ImzyyPerformanceProfiles(
                settings = settings,
                maxHeapCeilingMb = maxHeapCeilingMb,
                compact = compact,
                onSettingsChange = onSettingsChange,
            )
            // 언어 섹션'''
    if anchor not in source:
        raise RuntimeError("SettingsScreen content anchor changed; performance presets were not injected.")
    source = source.replace(anchor, replacement, 1)

    function_anchor = '''/**
 * 전역 렌더러 선택용 라디오 한 줄.'''
    profile_function = '''/**
 * One-tap, conservative game profiles. These modify existing upstream settings only;
 * no undocumented JVM flags or unsupported renderer switches are injected.
 */
@Composable
private fun ImzyyPerformanceProfiles(
    settings: JvmSettings,
    maxHeapCeilingMb: Int,
    compact: Boolean,
    onSettingsChange: (JvmSettings) -> Unit,
) {
    val safeCeiling = maxHeapCeilingMb.coerceAtLeast(1024)
    fun heapShare(percent: Float, hardLimitMb: Int): Int =
        (safeCeiling * percent).toInt().coerceIn(1024, minOf(safeCeiling, hardLimitMb))

    val performanceHeap = heapShare(0.45f, 2048)
    val balancedHeap = heapShare(0.58f, 3072)
    val visualHeap = heapShare(0.68f, 4096)
    val cardShape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgSurface, cardShape)
            .border(1.dp, BgBorder, cardShape)
            .padding(if (compact) 12.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "IMZYY PERFORMANCE",
            color = TextMain,
            fontSize = if (compact) 12.sp else 14.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.4.sp,
        )
        Text(
            "Quick profiles for memory and render scale. Changes apply on the next game launch.",
            color = TextSub,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = {
                    onSettingsChange(settings.copy(
                        maxHeapMb = performanceHeap,
                        resolutionScalePercent = 75,
                        useG1GC = true,
                    ))
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Flame),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
            ) {
                Text("FPS", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
            }
            Button(
                onClick = {
                    onSettingsChange(settings.copy(
                        maxHeapMb = balancedHeap,
                        resolutionScalePercent = 85,
                        useG1GC = true,
                    ))
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = BgBorder),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
            ) {
                Text("BALANCED", color = TextMain, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
            }
            Button(
                onClick = {
                    onSettingsChange(settings.copy(
                        maxHeapMb = visualHeap,
                        resolutionScalePercent = 100,
                        useG1GC = true,
                    ))
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = BgBorder),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
            ) {
                Text("VISUAL", color = TextMain, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
            }
        }
        Text(
            "Heap is capped by detected device memory; keep extra RAM available for graphics and Android.",
            color = TextSub,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

'''
    if function_anchor not in source:
        raise RuntimeError("SettingsScreen helper insertion anchor changed.")
    source = source.replace(function_anchor, profile_function + function_anchor, 1)
    path.write_text(source, encoding="utf-8")


def _replace_once(source: str, old: str, new: str, label: str) -> str:
    """Anchor-strict replace: the anchor must exist exactly once, unless the patch is already applied."""
    if new in source:
        return source
    if source.count(old) != 1:
        raise RuntimeError(f"Anchor tidak ditemukan/tidak unik untuk patch '{label}' (upstream berubah?)")
    return source.replace(old, new)


def patch_controls(app: Path) -> None:
    """Hook IMZYY controls (look sensitivity, touch slop/long-press, virtual mouse + cursor overlay)
    into the upstream game input code and recolor the on-screen buttons to monochrome."""
    base = app / "src/main/java/kr/co/donghyun/flamelauncher/presentation"

    activity = base / "MinecraftActivity.kt"
    src = require_file(activity)
    src = _replace_once(
        src,
        "    internal val MOUSE_SENSITIVITY = 1.5f",
        "    internal val MOUSE_SENSITIVITY: Float get() = id.imzyy.xl.ImzyyControls.lookSensitivity(this)",
        "look sensitivity",
    )
    src = _replace_once(
        src,
        "    private var gameControllerView: GameControllerView? = null",
        "    internal var gameControllerView: GameControllerView? = null",
        "controller view visibility",
    )
    activity.write_text(src, encoding="utf-8")

    surface = base / "ui/components/MinecraftSurface.kt"
    src = require_file(surface)
    src = _replace_once(
        src,
        "private const val DRAG_SLOP = 20f",
        "private val DRAG_SLOP: Float get() = id.imzyy.xl.ImzyyControls.dragSlop",
        "drag slop",
    )
    src = _replace_once(
        src,
        "private const val LONG_PRESS_TIMEOUT = 500L",
        "private val LONG_PRESS_TIMEOUT: Long get() = id.imzyy.xl.ImzyyControls.longPressMs",
        "long press",
    )
    src = _replace_once(
        src,
        "    val buf = bufferSize()\n",
        "    val buf = bufferSize()\n    if (id.imzyy.xl.ImzyyControls.handleTouchpad(activity, v, event, buf)) return@OnTouchListener true\n",
        "touchpad hook",
    )
    surface.write_text(src, encoding="utf-8")

    controller = base / "ui/components/GameControllerView.kt"
    src = require_file(controller)
    src = _replace_once(
        src,
        "    override fun onDraw(canvas: Canvas) {\n",
        "    override fun onDraw(canvas: Canvas) {\n        id.imzyy.xl.ImzyyControls.drawCursor(canvas, this, activity)\n",
        "cursor overlay",
    )
    for old, new in (
        ("Color.argb(217, 26, 10, 20)", "Color.argb(210, 10, 10, 10)"),
        ("Color.argb(217, 107, 0, 64)", "Color.argb(217, 55, 55, 55)"),
        ("Color.argb(255, 156, 16, 96)", "Color.argb(255, 95, 95, 95)"),
        ("Color.argb(255, 233, 30, 140)", "Color.argb(255, 150, 150, 150)"),
        ("Color.argb(178, 122, 40, 85)", "Color.argb(178, 90, 90, 90)"),
        ("Color.argb(178, 255, 107, 181)", "Color.argb(210, 255, 255, 255)"),
    ):
        src = src.replace(old, new)
    controller.write_text(src, encoding="utf-8")
    print("controls: look sensitivity, slop, long-press, touchpad + cursor hooks applied")


def patch_fps_text(app: Path) -> None:
    """The FPS chip is accent-filled; its label must be dark now that the accent is near-white."""
    path = app / "src/main/java/kr/co/donghyun/flamelauncher/presentation/ui/screen/SettingsScreen.kt"
    src = require_file(path)
    src = src.replace('Text("FPS", color = Color.White,', 'Text("FPS", color = Color.Black,')
    path.write_text(src, encoding="utf-8")


def patch_contrast(app: Path) -> None:
    """The accent is now near-white. Upstream hard-codes white text/icons on accent-filled
    buttons, which would turn invisible; flip the first Color.White that follows a
    `containerColor = Flame*` (within one button block) to black. Idempotent."""
    ui_root = app / "src/main/java/kr/co/donghyun/flamelauncher/presentation/ui"
    pattern = re.compile(
        r"(containerColor\s*=\s*(?:Flame|FlamePrimary|Orange)\b(?:(?!Button\s*\().){0,900}?)Color\.White",
        re.DOTALL,
    )
    flipped = 0
    for source_file in ui_root.rglob("*.kt"):
        source = source_file.read_text(encoding="utf-8")
        updated, count = pattern.subn(r"\1Color.Black", source)
        if count:
            source_file.write_text(updated, encoding="utf-8")
            flipped += count
    print(f"contrast: {flipped} white-on-accent usages flipped to black")


def install_hub(app: Path, repo: str, version: str) -> None:
    """Copy the self-contained IMZYY Hub (Compose) into the app and make it the launcher entry.
    The upstream MainActivity stays untouched; PLAY opens it."""
    overlay = Path(__file__).resolve().parents[1] / "android-overlay"
    src_dir = overlay / "kotlin"
    if not src_dir.is_dir():
        raise RuntimeError("android-overlay/kotlin tidak ditemukan")
    dest = app / "src/main/java/id/imzyy/xl"
    dest.mkdir(parents=True, exist_ok=True)
    for kt in src_dir.glob("*.kt"):
        (dest / kt.name).write_text(kt.read_text(encoding="utf-8"), encoding="utf-8")
    tpl = (src_dir / "ImzyyConfig.kt.tpl").read_text(encoding="utf-8")
    (dest / "ImzyyConfig.kt").write_text(tpl.replace("@VERSION@", version).replace("@REPO@", repo), encoding="utf-8")
    res = app / "src/main/res/values"
    res.mkdir(parents=True, exist_ok=True)
    (res / "imzyy_theme.xml").write_text((overlay / "res/imzyy_theme.xml").read_text(encoding="utf-8"), encoding="utf-8")

    manifest_path = app / "src/main/AndroidManifest.xml"
    ns_tools = "http://schemas.android.com/tools"
    ET.register_namespace("tools", ns_tools)
    tree = ET.parse(manifest_path)
    root = tree.getroot()
    application = root.find("application")
    if application is None:
        raise RuntimeError("<application> tidak ada di manifest")
    name_attr = f"{{{ANDROID}}}name"
    hub_name = "id.imzyy.xl.ImzyyHubActivity"
    if not any(a.get(name_attr) == hub_name for a in application.findall("activity")):
        moved = 0
        for tag in ("activity", "activity-alias"):
            for node in application.findall(tag):
                for flt in list(node.findall("intent-filter")):
                    cats = [c.get(name_attr) for c in flt.findall("category")]
                    if "android.intent.category.LAUNCHER" in cats:
                        node.remove(flt)
                        moved += 1
        if moved == 0:
            raise RuntimeError("Intent filter LAUNCHER upstream tidak ditemukan")
        hub = ET.SubElement(application, "activity")
        hub.set(name_attr, hub_name)
        hub.set(f"{{{ANDROID}}}exported", "true")
        hub.set(f"{{{ANDROID}}}theme", "@style/ImzyyHubTheme")
        hub.set(f"{{{ANDROID}}}label", "IMZYY XL")
        flt = ET.SubElement(hub, "intent-filter")
        ET.SubElement(flt, "action").set(name_attr, "android.intent.action.MAIN")
        ET.SubElement(flt, "category").set(name_attr, "android.intent.category.LAUNCHER")
    perms = [p.get(name_attr) for p in root.findall("uses-permission")]
    if "android.permission.INTERNET" not in perms:
        perm = ET.Element("uses-permission")
        perm.set(name_attr, "android.permission.INTERNET")
        root.insert(0, perm)
    tree.write(manifest_path, encoding="utf-8", xml_declaration=True)


def patch_root_branding(root: Path) -> None:
    settings = root / "settings.gradle.kts"
    if settings.is_file():
        source = settings.read_text(encoding="utf-8")
        source = re.sub(r'rootProject\.name\s*=\s*"[^"]+"', 'rootProject.name = "IMZYY XL Launcher"', source, count=1)
        settings.write_text(source, encoding="utf-8")


def validate_upstream(root: Path) -> None:
    app = root / "app"
    gradle = require_file(app / "build.gradle.kts")
    manifest = require_file(app / "src/main/AndroidManifest.xml")
    is_app = "com.android.application" in gradle or "android.application" in gradle  # version-catalog alias too
    if not is_app or "abiFilters" not in gradle or "arm64-v8a" not in gradle:
        raise RuntimeError("Build upstream tidak sesuai: modul Android ARM64 tidak terdeteksi.")
    if "kr.co.donghyun.flamelauncher.presentation.MainActivity" not in manifest and 'android:name=".presentation.MainActivity"' not in manifest:
        raise RuntimeError("MainActivity upstream tidak ditemukan di manifest.")
    if 'android:name=".presentation.MinecraftActivity"' not in manifest:
        raise RuntimeError("MinecraftActivity tidak terdaftar di aplikasi utama; pemeriksaan single-app gagal.")
    for relative in (
        "src/main/java/kr/co/donghyun/flamelauncher/presentation/MinecraftActivity.kt",
        "src/main/java/kr/co/donghyun/flamelauncher/presentation/ui/theme/Color.kt",
        "src/main/java/kr/co/donghyun/flamelauncher/presentation/ui/theme/Type.kt",
        "src/main/java/kr/co/donghyun/flamelauncher/presentation/ui/screen/SettingsScreen.kt",
        "src/main/res/values/strings.xml",
    ):
        require_file(app / relative)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--engine-dir", required=True, type=Path)
    parser.add_argument("--repo", default=os.environ.get("IMZYY_GITHUB_REPO", ""), help="owner/name for the in-app GitHub card")
    parser.add_argument("--version", default=os.environ.get("IMZYY_VERSION", "3.0.0"))
    args = parser.parse_args()
    root = args.engine_dir.resolve()
    validate_upstream(root)
    app = root / "app"
    patch_strings(app)
    patch_palette(app)
    patch_typography(app)
    patch_performance_profiles(app)
    patch_icon(app)
    patch_in_app_branding(app)
    patch_contrast(app)
    patch_fps_text(app)
    patch_controls(app)
    install_hub(app, args.repo, args.version)
    patch_root_branding(root)
    # Keep upstream Android namespace/applicationId intact until the Microsoft
    # OAuth redirect URI has been independently reconfigured and tested.
    print("IMZYY XL monochrome UI, Hub (Boost/Mods/.mrpack import), typography and device-capped performance profiles applied (ARM64 upstream engine retained).")
    return 0

if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"IMZYY prepare failed: {exc}", file=sys.stderr)
        raise SystemExit(2)
