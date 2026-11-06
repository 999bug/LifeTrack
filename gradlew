#!/usr/bin/env sh
# Lightweight bootstrap for this source bundle. It downloads the pinned Gradle
# distribution when no Gradle 8.13 installation is available. The official
# wrapper JAR is intentionally not embedded in this source-only archive.
set -eu
VERSION="8.13"
if command -v gradle >/dev/null 2>&1; then
  GV="$(gradle --version 2>/dev/null | sed -n 's/^Gradle \([0-9.]*\).*/\1/p' | head -n 1)"
  if [ "$GV" = "$VERSION" ]; then exec gradle "$@"; fi
fi
BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/life-track-bootstrap"
DIST="$BASE/gradle-$VERSION"
BIN="$DIST/bin/gradle"
if [ ! -x "$BIN" ]; then
  mkdir -p "$BASE"
  ZIP="$BASE/gradle-$VERSION-bin.zip"
  echo "Downloading Gradle $VERSION (first run only)..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$ZIP"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP" "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
  else
    echo "Please install Gradle $VERSION or curl/wget." >&2; exit 1
  fi
  if command -v unzip >/dev/null 2>&1; then unzip -q -o "$ZIP" -d "$BASE"
  else echo "Please install unzip and retry." >&2; exit 1; fi
fi
exec "$BIN" "$@"
