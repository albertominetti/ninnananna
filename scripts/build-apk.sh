#!/usr/bin/env bash
#
# build-apk.sh — compila l'APK di NinnaNanna e lo copia in ./dist/
#
# Uso:
#   ./scripts/build-apk.sh            # assembleDebug (default)
#   ./scripts/build-apk.sh release    # assembleRelease (firma: debug se non ci sono env keystore)
#
# Variabili d'ambiente opzionali:
#   JAVA_HOME                 Percorso del JDK (se non impostato usa "java" dal PATH)
#   ANDROID_SDK_ROOT          Percorso SDK Android (fallback: ANDROID_HOME, poi ~/android-sdk)
#   KEYSTORE_FILE             Percorso del keystore .jks per firmare la release
#   KEYSTORE_PASSWORD         Password del keystore
#   KEY_ALIAS                 Alias della chiave
#   KEY_PASSWORD              Password della chiave
#   KEYSTORE_BASE64           In alternativa: keystore codificato in base64 (decodificato in keystore.jks)
#   GRADLE_ARGS               Argomenti extra da passare a Gradle (es. "--no-daemon")
#
# Il primo avvio installa automaticamente commandline-tools, accetta le licenze
# e scarica platform/build-tools se ANDROID_SDK_ROOT non è configurato.

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
  *) echo "ERRORE: tipo di build non valido '$BUILD_TYPE' (usa 'debug' o 'release')." >&2; exit 2 ;;
esac

info()  { echo -e "\033[1;34m[build-apk]\033[0m $*"; }
warn()  { echo -e "\033[1;33m[build-apk]\033[0m $*" >&2; }
die()   { echo -e "\033[1;31m[build-apk]\033[0m $*" >&2; exit 1; }

# ---------------------------------------------------------------- JDK
find_java() {
  if [ -n "${JAVA_HOME:-}" ]; then
    if [ ! -x "$JAVA_HOME/bin/java" ]; then
      die "JAVA_HOME è impostato a '$JAVA_HOME' ma non contiene bin/java."
    fi
    JAVA_BIN="$JAVA_HOME/bin/java"
  elif command -v java >/dev/null 2>&1; then
    JAVA_BIN="$(command -v java)"
    JAVA_HOME="$(cd "$(dirname "$(dirname "$JAVA_BIN")")" && pwd)"
  else
    die "Nessun JDK trovato. Installa un JDK 17/21 e imposta JAVA_HOME."
  fi
  export JAVA_HOME

  local version
  version="$("$JAVA_BIN" -version 2>&1 | head -n1 | sed -E 's/.*version "([0-9]+).*/\1/')"
  info "JAVA_HOME=$JAVA_HOME (Java $version)"
  if [ "$version" -lt 17 ] || [ "$version" -gt 22 ]; then
    warn "Java $version non è la versione consigliata per Gradle 8.7 (usare 17 o 21)."
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
    info "cmdline-tools non trovato: installazione in $SDK_ROOT ..."
    mkdir -p "$SDK_ROOT/cmdline-tools"
    local tmp
    tmp="$(mktemp -d)"
    if ! command -v curl >/dev/null 2>&1; then
      die "curl non trovato: necessario per scaricare gli strumenti SDK."
    fi
    curl -sSL -o "$tmp/clt.zip" "$CMD_LINE_TOOLS_URL" || die "Download cmdline-tools fallito."
    unzip -q "$tmp/clt.zip" -d "$tmp/extracted"
    if [ -d "$SDK_ROOT/cmdline-tools/latest" ]; then
      rm -rf "$SDK_ROOT/cmdline-tools/latest"
    fi
    mv "$tmp/extracted/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
    rm -rf "$tmp"
    info "cmdline-tools installati."
  fi

  info "Accettazione licenze SDK..."
  yes | "$sdkmanager" --licenses >/dev/null 2>&1 || true

  info "Installazione componenti SDK (platform-tools, platforms;$PLATFORM_VERSION, build-tools;$BUILD_TOOLS_VERSION)..."
  "$sdkmanager" \
    "platform-tools" \
    "platforms;${PLATFORM_VERSION}" \
    "build-tools;${BUILD_TOOLS_VERSION}"
}

# ---------------------------------------------------------------- firma release
prepare_signing() {
  if [ "$BUILD_TYPE" != "release" ]; then
    return
  fi
  if [ -n "${KEYSTORE_BASE64:-}" ] && [ -z "${KEYSTORE_FILE:-}" ]; then
    KEYSTORE_FILE="$PROJECT_DIR/keystore.jks"
    info "Decodifica KEYSTORE_BASE64 in $KEYSTORE_FILE"
    printf '%s' "$KEYSTORE_BASE64" | base64 -d > "$KEYSTORE_FILE"
    export KEYSTORE_FILE
  fi
  if [ -z "${KEYSTORE_FILE:-}" ]; then
    warn "Nessuna KEYSTORE_FILE/KEYSTORE_BASE64: l'APK release verrà firmato con la chiave debug (non adatto a Google Play)."
  else
    info "Firma release configurata con KEYSTORE_FILE=$KEYSTORE_FILE"
  fi
}

# ---------------------------------------------------------------- build
build_apk() {
  cd "$PROJECT_DIR"
  if [ ! -x ./gradlew ]; then
    die "gradlew non trovato o non eseguibile. Generare il wrapper Gradle (gradle wrapper --gradle-version 8.7)."
  fi

  local task
  if [ "$BUILD_TYPE" = "release" ]; then
    task="assembleRelease"
  else
    task="assembleDebug"
  fi

  info "Lancio ./gradlew $task ${GRADLE_ARGS:-}..."
  # shellcheck disable=SC2086
  ./gradlew "$task" $GRADLE_ARGS --console=plain

  local apk="$PROJECT_DIR/app/build/outputs/apk/$BUILD_TYPE/app-$BUILD_TYPE.apk"
  if [ ! -f "$apk" ]; then
    die "APK non trovato in $apk."
  fi

  mkdir -p "$DIST_DIR"
  cp "$apk" "$DIST_DIR/"
  info "APK copiato in $DIST_DIR/$(basename "$apk")"
}

# ---------------------------------------------------------------- main
find_java
setup_sdk
prepare_signing
build_apk
info "Fatto. APK: $DIST_DIR/app-$BUILD_TYPE.apk"