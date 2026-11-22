#!/usr/bin/env bash
# Compila la app sin depender de variables de entorno del sistema.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-$HOME/toolchain/jdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/toolchain/sdk}"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
GRADLE="${GRADLE:-$HOME/toolchain/gradle-8.13/bin/gradle}"

cd "$RAIZ"
"$GRADLE" --no-daemon "$@"
