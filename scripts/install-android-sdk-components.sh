#!/usr/bin/env bash
# Installs the exact SDK packages required by Amethyst and bridges the API 37
# package-directory naming mismatch expected by Android Gradle Plugin.
set -Eeuo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK_ROOT" ]]; then
  echo "ANDROID_SDK_ROOT/ANDROID_HOME is not set." >&2
  exit 2
fi

SDKMANAGER="${SDKMANAGER:-}"
if [[ -z "$SDKMANAGER" ]]; then
  if [[ -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]]; then
    SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
  elif command -v sdkmanager >/dev/null 2>&1; then
    SDKMANAGER="$(command -v sdkmanager)"
  else
    echo "sdkmanager was not found under $SDK_ROOT or PATH." >&2
    exit 2
  fi
fi

# API 37 is published as android-37.0, not android-37. The Amethyst build
# declares compileSdk = 37 and AGP looks for platforms/android-37, so after
# installing the canonical package we create a local compatibility symlink.
"$SDKMANAGER" --sdk_root="$SDK_ROOT" --install \
  "platforms;android-37.0" \
  "ndk;27.3.13750724"

PLATFORM_REAL="$SDK_ROOT/platforms/android-37.0"
PLATFORM_ALIAS="$SDK_ROOT/platforms/android-37"
NDK_DIR="$SDK_ROOT/ndk/27.3.13750724"

if [[ ! -s "$PLATFORM_REAL/android.jar" ]]; then
  echo "Android API 37.0 install is incomplete: missing $PLATFORM_REAL/android.jar" >&2
  exit 3
fi
if [[ ! -f "$NDK_DIR/source.properties" ]]; then
  echo "Android NDK 27.3.13750724 install is incomplete: missing $NDK_DIR/source.properties" >&2
  exit 3
fi

mkdir -p "$SDK_ROOT/platforms"
if [[ -L "$PLATFORM_ALIAS" ]]; then
  current_target="$(readlink "$PLATFORM_ALIAS")"
  if [[ "$current_target" != "android-37.0" ]]; then
    rm "$PLATFORM_ALIAS"
  fi
elif [[ -e "$PLATFORM_ALIAS" ]]; then
  # Keep an existing real API 37 directory only if it is already usable.
  if [[ ! -s "$PLATFORM_ALIAS/android.jar" ]]; then
    echo "A conflicting, incomplete SDK directory exists: $PLATFORM_ALIAS" >&2
    exit 4
  fi
fi

if [[ ! -e "$PLATFORM_ALIAS" ]]; then
  ln -s "android-37.0" "$PLATFORM_ALIAS"
fi

if [[ ! -s "$PLATFORM_ALIAS/android.jar" ]]; then
  echo "Android Gradle Plugin compatibility path is invalid: $PLATFORM_ALIAS/android.jar" >&2
  exit 4
fi

echo "Android SDK is ready:"
echo "  Platform package: platforms;android-37.0"
echo "  AGP compileSdk=37 path: $PLATFORM_ALIAS"
echo "  NDK: $NDK_DIR"
