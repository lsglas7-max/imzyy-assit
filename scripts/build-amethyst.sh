#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENGINE_DIR="${AMETHYST_DIR:-$ROOT/.build/Amethyst-Android}"
ENGINE_REF="${AMETHYST_REF:-v3_openjdk}"
ENGINE_URL="https://github.com/AngelAuraMC/Amethyst-Android.git"
OUTPUT_DIR="$ROOT/output"
MODE="${1:-all}"

for command in git python3; do
  command -v "$command" >/dev/null 2>&1 || { echo "Missing required command: $command" >&2; exit 2; }
done

case "$ENGINE_DIR" in
  *" "*) echo "Amethyst's native build cannot be placed under a path containing spaces: $ENGINE_DIR" >&2; exit 2 ;;
esac

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
  [[ -d "$ENGINE_DIR/app_pojavlauncher" ]] || { echo "Amethyst app module missing after clone." >&2; exit 3; }
}

build_engine() {
  [[ -d "$ENGINE_DIR/.git" ]] || { echo "Amethyst source missing. Run: bash scripts/build-amethyst.sh fetch" >&2; exit 3; }
  python3 "$ROOT/scripts/prepare-amethyst.py" --engine-dir "$ENGINE_DIR" --project-root "$ROOT"

  # Amethyst's official CI injects this separately-built JRE 8 asset before
  # compiling the app. Do not silently produce an APK without the bootstrap JRE.
  local jre_dir="$ENGINE_DIR/app_pojavlauncher/src/main/assets/components/jre"
  if [[ ! -d "$jre_dir" ]] || ! find "$jre_dir" -type f -print -quit | grep -q .; then
    cat >&2 <<MSG
Official Amethyst JRE 8 asset is missing:
  $jre_dir
For GitHub Actions, run the provided jre8-pojav artifact download step.
For a local build, download the upstream 'jre8-pojav' artifact from
AngelAuraMC/angelauramc-openjdk-build (branch buildjre8) and extract its
contents into that directory, then rerun: bash scripts/build-amethyst.sh build
MSG
    exit 4
  fi

  if [[ -f "$ENGINE_DIR/scripts/languagelist_updater.sh" ]]; then
    chmod +x "$ENGINE_DIR/scripts/languagelist_updater.sh"
    (cd "$ENGINE_DIR" && bash scripts/languagelist_updater.sh)
  fi

  local gradle_cmd
  if command -v gradle >/dev/null 2>&1; then
    gradle_cmd="gradle"
  elif [[ -x "$ENGINE_DIR/gradlew" ]]; then
    gradle_cmd="$ENGINE_DIR/gradlew"
  else
    echo "Gradle 9.6.1 or the upstream Gradle wrapper is required." >&2
    exit 2
  fi

  (
    cd "$ENGINE_DIR"
    "$gradle_cmd" --no-daemon :app_pojavlauncher:assembleDebug
  )

  local apk
  apk="$(find "$ENGINE_DIR/app_pojavlauncher/build/outputs/apk/debug" -maxdepth 1 -type f -name '*.apk' -print -quit)"
  if [[ -z "$apk" || ! -f "$apk" ]]; then
    echo "Gradle selesai tetapi APK debug tidak ditemukan." >&2
    exit 5
  fi

  # Verify that the artifact really contains native ARM64 code and the JRE
  # bootstrap assets instead of accepting a superficially successful empty APK.
  python3 - "$apk" <<'PY'
import sys, zipfile
apk = sys.argv[1]
with zipfile.ZipFile(apk) as z:
    names = z.namelist()
    native = [n for n in names if n.startswith("lib/arm64-v8a/") and n.endswith(".so")]
    jre = [n for n in names if "assets/components/jre/" in n]
    if not native:
        raise SystemExit("APK validation failed: no ARM64 native .so libraries packaged")
    if not jre:
        raise SystemExit("APK validation failed: Amethyst JRE 8 bootstrap assets are absent")
    print(f"APK validation passed: {len(native)} ARM64 native libraries, {len(jre)} JRE asset entries")
PY

  cp "$apk" "$OUTPUT_DIR/IMZYY-XL-Amethyst-Engine-debug.apk"
  printf 'Engine repository: %s\nEngine ref: %s\nEngine commit: %s\nABI filter: %s\nJRE8 asset: present\nAPK: %s\n' \
    "$ENGINE_URL" "$ENGINE_REF" "$(git -C "$ENGINE_DIR" rev-parse HEAD)" "${IMZYY_ABI_FILTER:-arm64-v8a}" "$OUTPUT_DIR/IMZYY-XL-Amethyst-Engine-debug.apk" \
    > "$OUTPUT_DIR/BUILD-INFO.txt"
  echo "Built and validated single-app IMZYY XL + Amethyst APK: $OUTPUT_DIR/IMZYY-XL-Amethyst-Engine-debug.apk"
}

case "$MODE" in
  fetch) fetch_engine ;;
  build) build_engine ;;
  all) fetch_engine; build_engine ;;
  *) echo "Usage: $0 [fetch|build|all]" >&2; exit 2 ;;
esac
