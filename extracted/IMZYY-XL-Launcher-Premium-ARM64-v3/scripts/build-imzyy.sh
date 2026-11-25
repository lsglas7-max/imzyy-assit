#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENGINE_DIR="${IMZYY_ENGINE_DIR:-$ROOT/.build/FlameLauncher-Android}"
ENGINE_URL="https://github.com/FlameLaunchers/FlameLauncher-Android.git"
ENGINE_REF="${IMZYY_ENGINE_REF:-main}"
OUTPUT_DIR="$ROOT/output"
MODE="${1:-all}"

for command in git python3; do
  command -v "$command" >/dev/null 2>&1 || { echo "Missing required command: $command" >&2; exit 2; }
done

fetch_engine() {
  mkdir -p "$(dirname "$ENGINE_DIR")" "$OUTPUT_DIR"
  if [[ -d "$ENGINE_DIR/.git" ]]; then
    git -C "$ENGINE_DIR" fetch --depth 1 origin "$ENGINE_REF"
    git -C "$ENGINE_DIR" reset --hard FETCH_HEAD
    git -C "$ENGINE_DIR" submodule sync --recursive
    git -C "$ENGINE_DIR" submodule update --init --recursive --depth 1
  else
    rm -rf "$ENGINE_DIR"
    git clone --depth 1 --branch "$ENGINE_REF" --recurse-submodules --shallow-submodules "$ENGINE_URL" "$ENGINE_DIR"
  fi
  [[ -f "$ENGINE_DIR/app/build.gradle.kts" ]] || { echo "FlameLauncher app module missing." >&2; exit 3; }
}

build_engine() {
  [[ -d "$ENGINE_DIR/.git" ]] || { echo "Engine source missing; run fetch first." >&2; exit 3; }
  python3 "$ROOT/scripts/prepare-imzyy.py" --engine-dir "$ENGINE_DIR" --repo "${IMZYY_GITHUB_REPO:-}" --version "${IMZYY_VERSION:-3.0.0}"
  [[ -x "$ENGINE_DIR/gradlew" ]] || { echo "Upstream Gradle wrapper missing." >&2; exit 4; }
  (
    cd "$ENGINE_DIR"
    ./gradlew --no-daemon :app:assembleDebug
  )
  local apk
  apk="$(find "$ENGINE_DIR/app/build/outputs/apk/debug" -maxdepth 1 -type f -name '*.apk' -print -quit)"
  [[ -n "$apk" && -f "$apk" ]] || { echo "Gradle selesai tetapi APK tidak ditemukan." >&2; exit 5; }
  python3 - "$apk" <<'PY'
import sys, zipfile
apk = sys.argv[1]
with zipfile.ZipFile(apk) as z:
    names = z.namelist()
    arm64 = [n for n in names if n.startswith("lib/arm64-v8a/") and n.endswith(".so")]
    dex = [n for n in names if n.startswith("classes") and n.endswith(".dex")]
    if not arm64: raise SystemExit("APK validation failed: no ARM64 native libraries")
    if not dex: raise SystemExit("APK validation failed: no DEX bytecode")
    print(f"APK validation passed: {len(arm64)} ARM64 native libraries; {len(dex)} DEX files")
PY
  cp "$apk" "$OUTPUT_DIR/IMZYY-XL-Launcher-arm64-debug.apk"
  local commit
  commit="$(git -C "$ENGINE_DIR" rev-parse HEAD)"
  printf 'Engine: %s\nRef: %s\nCommit: %s\nABI: arm64-v8a\nBranding: IMZYY XL Launcher\nLicense: AGPL-3.0 (see THIRD_PARTY_NOTICES.md)\nAPK: %s\n' \
    "$ENGINE_URL" "$ENGINE_REF" "$commit" "$OUTPUT_DIR/IMZYY-XL-Launcher-arm64-debug.apk" > "$OUTPUT_DIR/BUILD-INFO.txt"
  # Preserve the exact patched corresponding source for AGPL source availability.
  tar --exclude='.git' --exclude='*/build' --exclude='.gradle' -czf "$OUTPUT_DIR/IMZYY-XL-Launcher-corresponding-source.tar.gz" -C "$(dirname "$ENGINE_DIR")" "$(basename "$ENGINE_DIR")" -C "$ROOT" scripts/prepare-imzyy.py THIRD_PARTY_NOTICES.md LICENSE
  printf 'UI: IMZYY XL premium dark UI + bold typography\nPerformance: FPS / Balanced / Visual device-capped profiles\nPackaging: one Android application APK; game activity is embedded\n' >> "$OUTPUT_DIR/BUILD-INFO.txt"
  echo "Built IMZYY XL ARM64 debug APK: $OUTPUT_DIR/IMZYY-XL-Launcher-arm64-debug.apk ($(du -h "$OUTPUT_DIR/IMZYY-XL-Launcher-arm64-debug.apk" | cut -f1))"
}

case "$MODE" in
  fetch) fetch_engine ;;
  build) build_engine ;;
  all) fetch_engine; build_engine ;;
  *) echo "Usage: $0 [fetch|build|all]" >&2; exit 2 ;;
esac
