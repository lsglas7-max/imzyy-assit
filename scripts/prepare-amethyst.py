#!/usr/bin/env python3
"""Overlay IMZYY into the official Amethyst Android app module.

The upstream TestStorageActivity remains the real MAIN/LAUNCHER entry point so
its storage permission, directory validation and asset-unpack bootstrap runs
before the IMZYY dashboard. The dashboard then opens Amethyst's own
LauncherActivity from inside the same APK.
"""
from __future__ import annotations

import argparse
import os
import re
import shutil
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID_NS = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID_NS)


def a(name: str) -> str:
    return f"{{{ANDROID_NS}}}{name}"


def preserve_namespaces(xml_path: Path) -> None:
    for _, (prefix, uri) in ET.iterparse(xml_path, events=("start-ns",)):
        if prefix not in ("android", "tools", "xml"):
            try:
                ET.register_namespace(prefix, uri)
            except ValueError:
                pass


def patch_manifest(manifest: Path) -> None:
    if not manifest.is_file():
        raise RuntimeError(f"Android manifest upstream tidak ditemukan: {manifest}")
    preserve_namespaces(manifest)
    tree = ET.parse(manifest)
    root = tree.getroot()
    application = root.find("application")
    if application is None:
        raise RuntimeError("Manifest Amethyst tidak memiliki elemen <application>.")

    # The official TestStorageActivity must remain the launcher. It checks the
    # storage root, loads preferences, and unpacks engine assets before exit().
    test_storage = None
    for component in application:
        if component.tag.rsplit("}", 1)[-1] != "activity":
            continue
        name = component.get(a("name"), "")
        if name.endswith("TestStorageActivity") or name == "TestStorageActivity":
            test_storage = component
            break
    if test_storage is None:
        raise RuntimeError("TestStorageActivity resmi tidak ditemukan; tidak aman mengganti entry point.")
    has_launcher_filter = False
    for intent_filter in test_storage.findall("intent-filter"):
        actions = {node.get(a("name")) for node in intent_filter.findall("action")}
        categories = {node.get(a("name")) for node in intent_filter.findall("category")}
        if "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in categories:
            has_launcher_filter = True
    if not has_launcher_filter:
        raise RuntimeError("TestStorageActivity tidak memiliki filter MAIN/LAUNCHER; patch dihentikan.")

    application.set(a("label"), "IMZYY XL Launcher")
    application.set(a("allowBackup"), "false")

    # Add (or update) the IMZYY dashboard as a private in-app activity.
    dashboard = None
    for component in application:
        if component.tag.rsplit("}", 1)[-1] == "activity" and component.get(a("name")) == "com.imzyy.xllauncher.MainActivity":
            dashboard = component
            break
    if dashboard is None:
        dashboard = ET.Element("activity")
        application.append(dashboard)
    dashboard.attrib.update({
        a("name"): "com.imzyy.xllauncher.MainActivity",
        a("exported"): "false",
        a("screenOrientation"): "unspecified",
        a("theme"): "@android:style/Theme.Material.NoActionBar",
        a("hardwareAccelerated"): "true",
    })
    # Never attach MAIN/LAUNCHER to the dashboard: storage/bootstrap stays first.
    for intent_filter in list(dashboard.findall("intent-filter")):
        actions = {node.get(a("name")) for node in intent_filter.findall("action")}
        categories = {node.get(a("name")) for node in intent_filter.findall("category")}
        if "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in categories:
            dashboard.remove(intent_filter)

    tree.write(manifest, encoding="utf-8", xml_declaration=True)


def patch_test_storage_entry(engine_root: Path) -> None:
    candidates = list((engine_root / "app_pojavlauncher/src/main/java").rglob("TestStorageActivity.java"))
    if len(candidates) != 1:
        raise RuntimeError(f"Diharapkan satu TestStorageActivity.java, ditemukan {len(candidates)}.")
    source_path = candidates[0]
    source = source_path.read_text(encoding="utf-8")
    target = "com.imzyy.xllauncher.MainActivity.class"
    if target in source:
        return  # Idempotent when preparing the same checkout more than once.
    pattern = re.compile(r"new\s+Intent\s*\(\s*this\s*,\s*(?:LauncherActivity|net\.kdt\.pojavlaunch\.LauncherActivity)\.class\s*\)")
    patched, count = pattern.subn(f"new Intent(this, {target})", source)
    if count != 1:
        raise RuntimeError(f"Tidak bisa mengalihkan satu-satunya target launcher dari TestStorageActivity (match={count}).")
    source_path.write_text(patched, encoding="utf-8")


def patch_gradle(gradle_file: Path) -> None:
    if not gradle_file.is_file():
        raise RuntimeError(f"Gradle file upstream tidak ditemukan: {gradle_file}")
    source = gradle_file.read_text(encoding="utf-8")

    # IMZYY's SAF backup/import code and archive manager need these libraries.
    dependencies = [
        ("androidx.documentfile:documentfile", "    implementation 'androidx.documentfile:documentfile:1.0.1'"),
    ]
    missing = [line for marker, line in dependencies if marker not in source]
    if missing:
        match = re.search(r"(?m)^dependencies\s*\{", source)
        if not match:
            raise RuntimeError("Blok dependencies {} tidak ditemukan di Gradle Amethyst.")
        source = source[:match.end()] + "\n" + "\n".join(missing) + source[match.end():]

    # The project defaults to ARM64 for a lean phone APK. Set
    # IMZYY_ABI_FILTER=all to preserve all upstream-supported ABIs.
    abi_value = os.environ.get("IMZYY_ABI_FILTER", "arm64-v8a").strip()
    if abi_value.lower() != "all":
        allowed = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}
        abis = [item.strip() for item in abi_value.split(",") if item.strip()]
        if not abis or any(item not in allowed for item in abis):
            raise RuntimeError(f"Unsupported IMZYY_ABI_FILTER: {abi_value}")
        if "IMZYY_ABI_FILTER" not in source:
            quoted = ", ".join("'" + item + "'" for item in abis)
            filter_block = (
                "\n        // IMZYY_ABI_FILTER: native APK ABI optimization\n"
                f"        ndk {{ abiFilters {quoted} }}"
            )
            default_config = re.search(r"(?m)^\s*defaultConfig\s*\{", source)
            if not default_config:
                raise RuntimeError("defaultConfig block not found in Amethyst Gradle file.")
            source = source[:default_config.end()] + filter_block + source[default_config.end():]

    # Raise the upstream min SDK only as far as the IMZYY dashboard requires.
    source, min_count = re.subn(r"(?m)(minSdkVersion\s+)21\b", r"\g<1>26", source, count=1)
    if min_count == 0 and not re.search(r"minSdk(?:Version)?\s*(?:=\s*)?(?:26|[2-9][7-9])", source):
        raise RuntimeError("Tidak dapat menemukan nilai minSdk upstream untuk memastikan API dashboard.")

    gradle_file.write_text(source, encoding="utf-8")


def overlay_sources(project_root: Path, engine_root: Path) -> None:
    source = project_root / "android/app/src/main/java/com/imzyy/xllauncher"
    destination = engine_root / "app_pojavlauncher/src/main/java/com/imzyy/xllauncher"
    if not source.is_dir():
        raise RuntimeError(f"Source IMZYY tidak ditemukan: {source}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists():
        shutil.rmtree(destination)
    shutil.copytree(source, destination)

    app_module = engine_root / "app_pojavlauncher"
    patch_manifest(app_module / "src/main/AndroidManifest.xml")
    patch_test_storage_entry(engine_root)
    patch_gradle(app_module / "build.gradle")

    (app_module / "IMZYY-INTEGRATION.txt").write_text(
        "IMZYY XL Launcher + Amethyst Android integration\n"
        "Engine: AngelAuraMC/Amethyst-Android (recursive source/submodules)\n"
        "Startup: TestStorageActivity -> IMZYY MainActivity -> Amethyst LauncherActivity -> game MainActivity\n"
        "Bootstrap: upstream PojavApplication + TestStorageActivity asset/storage setup is retained\n"
        "Renderer/input/runtime: compiled from upstream Amethyst source and native submodules\n"
        "Build model: one APK; no second launcher APK or fake renderer bridge\n",
        encoding="utf-8",
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--engine-dir", required=True, type=Path)
    parser.add_argument("--project-root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    engine = args.engine_dir.resolve()
    project = args.project_root.resolve()
    if not (engine / "app_pojavlauncher").is_dir() or not (engine / "settings.gradle").is_file():
        print("Folder source Amethyst tidak valid atau belum di-clone recursive.", file=sys.stderr)
        return 2
    overlay_sources(project, engine)
    print("Overlay IMZYY berhasil dipasang. TestStorageActivity dan bootstrap storage Amethyst tetap menjadi entry awal.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
