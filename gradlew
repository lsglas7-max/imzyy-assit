#!/usr/bin/env sh
# Small Gradle bootstrapper (Gradle 8.9). Android Studio can also sync/build this project.
set -eu
GRADLE_VERSION="8.9"
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
BASE_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/galaxy-gradle"
GRADLE_HOME_DIR="$BASE_DIR/gradle-$GRADLE_VERSION"
if [ ! -x "$GRADLE_HOME_DIR/bin/gradle" ]; then
  mkdir -p "$BASE_DIR"
  ZIP_FILE="$BASE_DIR/gradle-$GRADLE_VERSION-bin.zip"
  URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  if command -v curl >/dev/null 2>&1; then
    curl -fL "$URL" -o "$ZIP_FILE"
  elif command -v wget >/dev/null 2>&1; then
    wget "$URL" -O "$ZIP_FILE"
  else
    echo "curl atau wget dibutuhkan untuk mengunduh Gradle $GRADLE_VERSION."
    exit 1
  fi
  if ! command -v unzip >/dev/null 2>&1; then
    echo "unzip dibutuhkan untuk mengekstrak Gradle."
    exit 1
  fi
  unzip -q -o "$ZIP_FILE" -d "$BASE_DIR"
fi
exec "$GRADLE_HOME_DIR/bin/gradle" --no-daemon "$@"
