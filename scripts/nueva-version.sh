#!/usr/bin/env bash
# Publica una versión nueva: sube versionCode/versionName, compila el APK de release,
# calcula su SHA-256 y escribe update.json para que la app se actualice sola.
#
# Uso: scripts/nueva-version.sh 0.2.0 https://raw.githubusercontent.com/USUARIO/REPO/main/apk/obra-0.2.0.apk
set -euo pipefail

if [ $# -lt 2 ]; then
  echo "Uso: $0 <versionName> <apkUrl>" >&2
  exit 1
fi

VERSION="$1"
APK_URL="$2"
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_APP="$RAIZ/app/build.gradle.kts"

CODIGO_ACTUAL="$(grep -oP 'versionCode = \K[0-9]+' "$GRADLE_APP" | head -1)"
CODIGO_NUEVO=$((CODIGO_ACTUAL + 1))

sed -i "s/versionCode = $CODIGO_ACTUAL/versionCode = $CODIGO_NUEVO/" "$GRADLE_APP"
sed -i "s/versionName = \"[^\"]*\"/versionName = \"$VERSION\"/" "$GRADLE_APP"

"$RAIZ/scripts/build.sh" :app:assembleRelease

APK="$RAIZ/app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK" ]; then
  echo "No se encontró el APK en $APK" >&2
  exit 1
fi

SHA="$(sha256sum "$APK" | cut -d' ' -f1)"
mkdir -p "$RAIZ/apk" "$RAIZ/dist"
cp "$APK" "$RAIZ/apk/obra-$VERSION.apk"

cat > "$RAIZ/dist/update.json" <<JSON
{
  "versionCode": $CODIGO_NUEVO,
  "versionName": "$VERSION",
  "apkUrl": "$APK_URL",
  "sha256": "$SHA",
  "notas": "Versión $VERSION"
}
JSON

echo "versionCode: $CODIGO_NUEVO"
echo "versionName: $VERSION"
echo "sha256: $SHA"
echo "manifiesto: $RAIZ/dist/update.json"
echo "APK: $RAIZ/apk/obra-$VERSION.apk"
