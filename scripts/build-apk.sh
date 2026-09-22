#!/usr/bin/env bash
#
# build-apk.sh — compiles the NinnaNanna APK and copies it into ./dist/
#
# Usage:
#   ./scripts/build-apk.sh            # assembleDebug (default)
#   ./scripts/build-apk.sh release    # assembleRelease (signing: debug if there are no keystore env vars)
#
# Optional environment variables:
#   JAVA_HOME                 JDK path (if unset uses "java" from PATH)
#   ANDROID_SDK_ROOT          Android SDK path (fallback: ANDROID_HOME, then ~/android-sdk)
#   KEYSTORE_FILE             Path of the .jks keystore to sign the release
#   KEYSTORE_PASSWORD         Keystore password
#   KEY_ALIAS                 Key alias
#   KEY_PASSWORD              Key password
#   KEYSTORE_BASE64           Alternatively: keystore encoded in base64 (decoded into keystore.jks)
#   GRADLE_ARGS               Extra arguments to pass to Gradle (e.g. "--no-daemon")
#
# On the first run it automatically installs commandline-tools, accepts the
# licenses and downloads platform/build-tools if ANDROID_SDK_ROOT is not set.

set -euo pipefail

# ---------------------------------------------------------------- config
CMD_LINE_TOOLS_URL="${CMD_LINE_TOOLS_URL:-https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip}"
PLATFORM_VERSION="${PLATFORM_VERSION:-android-34}"
BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-34.0.0}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
DIST_DIR="${DIST_DIR:-$PROJECT_DIR/dist}"

BUILD_TYPE="${1:-debug}"
case "$BUILD_TYPE" in
  debug|release) ;;
  *) echo "ERROR: invalid build type '$BUILD_TYPE' (use 'debug' or 'release')." >&2; exit 2 ;;
esac

info()  { echo -e "\033[1;34m[build-apk]\033[0m $*"; }
warn()  { echo -e "\033[1;33m[build-apk]\033[0m $*" >&2; }
die()   { echo -e "\033[1;31m[build-apk]\033[0m $*" >&2; exit 1; }

# ---------------------------------------------------------------- JDK
find_java() {
  if [ -n "${JAVA_HOME:-}" ]; then
    if [ ! -x "$JAVA_HOME/bin/java" ]; then
      die "JAVA_HOME is set to '$JAVA_HOME' but it does not contain bin/java."
    fi
    JAVA_BIN="$JAVA_HOME/bin/java"
  elif command -v java >/dev/null 2>&1; then
    JAVA_BIN="$(command -v java)"
    JAVA_HOME="$(cd "$(dirname "$(dirname "$JAVA_BIN")")" && pwd)"
  else
    die "No JDK found. Install a JDK 17/21 and set JAVA_HOME."
  fi
  export JAVA_HOME

  local version
  version="$("$JAVA_BIN" -version 2>&1 | head -n1 | sed -E 's/.*version "([0-9]+).*/\1/')"
  info "JAVA_HOME=$JAVA_HOME (Java $version)"
  if [ "$version" -lt 17 ] || [ "$version" -gt 22 ]; then
    warn "Java $version is not the recommended version for Gradle 8.7 (use 17 or 21)."
  fi
}

# ---------------------------------------------------------------- SDK
setup_sdk() {
  if [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -d "$ANDROID_SDK_ROOT" ]; then
    SDK_ROOT="$ANDROID_SDK_ROOT"
  elif [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ]; then
    SDK_ROOT="$ANDROID_HOME"
  else
    SDK_ROOT="$HOME/android-sdk"
  fi
  export ANDROID_SDK_ROOT="$SDK_ROOT"
  info "ANDROID_SDK_ROOT=$SDK_ROOT"

  local sdkmanager="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

  if [ ! -x "$sdkmanager" ]; then
    info "cmdline-tools not found: installing into $SDK_ROOT ..."
    mkdir -p "$SDK_ROOT/cmdline-tools"
    local tmp
    tmp="$(mktemp -d)"
    if ! command -v curl >/dev/null 2>&1; then
      die "curl not found: needed to download the SDK tools."
    fi
    curl -sSL -o "$tmp/clt.zip" "$CMD_LINE_TOOLS_URL" || die "cmdline-tools download failed."
    unzip -q "$tmp/clt.zip" -d "$tmp/extracted"
    if [ -d "$SDK_ROOT/cmdline-tools/latest" ]; then
      rm -rf "$SDK_ROOT/cmdline-tools/latest"
    fi
    mv "$tmp/extracted/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
    rm -rf "$tmp"
    info "cmdline-tools installed."
  fi

  info "Accepting SDK licenses..."
  yes | "$sdkmanager" --licenses >/dev/null 2>&1 || true

  info "Installing SDK components (platform-tools, platforms;$PLATFORM_VERSION, build-tools;$BUILD_TOOLS_VERSION)..."
  "$sdkmanager" \
    "platform-tools" \
    "platforms;${PLATFORM_VERSION}" \
    "build-tools;${BUILD_TOOLS_VERSION}"
}

# ---------------------------------------------------------------- release signing
prepare_signing() {
  if [ "$BUILD_TYPE" != "release" ]; then
    return
  fi
  if [ -n "${KEYSTORE_BASE64:-}" ] && [ -z "${KEYSTORE_FILE:-}" ]; then
    KEYSTORE_FILE="$PROJECT_DIR/keystore.jks"
    info "Decoding KEYSTORE_BASE64 into $KEYSTORE_FILE"
    printf '%s' "$KEYSTORE_BASE64" | base64 -d > "$KEYSTORE_FILE"
    export KEYSTORE_FILE
  fi
  if [ -z "${KEYSTORE_FILE:-}" ]; then
    warn "No KEYSTORE_FILE/KEYSTORE_BASE64: the release APK will be signed with the debug key (not suitable for Google Play)."
  else
    info "Release signing configured with KEYSTORE_FILE=$KEYSTORE_FILE"
  fi
}

# ---------------------------------------------------------------- build
build_apk() {
  cd "$PROJECT_DIR"
  if [ ! -x ./gradlew ]; then
    die "gradlew not found or not executable. Generate the Gradle wrapper (gradle wrapper --gradle-version 8.7)."
  fi

  local task
  if [ "$BUILD_TYPE" = "release" ]; then
    task="assembleRelease"
  else
    task="assembleDebug"
  fi

  info "Running ./gradlew $task ${GRADLE_ARGS:-}..."
  # shellcheck disable=SC2086
  ./gradlew "$task" $GRADLE_ARGS --console=plain

  local apk="$PROJECT_DIR/app/build/outputs/apk/$BUILD_TYPE/app-$BUILD_TYPE.apk"
  if [ ! -f "$apk" ]; then
    die "APK not found at $apk."
  fi

  mkdir -p "$DIST_DIR"
  cp "$apk" "$DIST_DIR/"
  info "APK copied to $DIST_DIR/$(basename "$apk")"
}

# ---------------------------------------------------------------- main
find_java
setup_sdk
prepare_signing
build_apk
info "Done. APK: $DIST_DIR/app-$BUILD_TYPE.apk"