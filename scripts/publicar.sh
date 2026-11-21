#!/usr/bin/env bash
# Prepara una versión nueva para el repo público de distribución.
#
# Uso: scripts/publicar.sh 0.9.3
#
# Hace: sube versionCode y versionName, compila el release firmado, copia el APK a apk/,
# calcula el SHA-256 y escribe dist/update.json apuntando a la release de GitHub.
# Después hay que subir el APK a la release (arrastrarlo en la web) y subir update.json.
set -euo pipefail

OWNER="Mauri007-cyber"
REPO="obra-android"

if [ $# -lt 1 ]; then
  echo "Uso: $0 <versionName>   (ejemplo: $0 0.9.3)" >&2
  exit 1
fi

VERSION="$1"
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_APP="$RAIZ/app/build.gradle.kts"

if [ ! -f "$RAIZ/keystore.properties" ]; then
  echo "Falta keystore.properties: no se puede publicar sin la clave de firma de producción." >&2
  exit 1
fi

CODIGO_ACTUAL="$(grep -oP 'versionCode = \K[0-9]+' "$GRADLE_APP" | head -1)"
CODIGO_NUEVO=$((CODIGO_ACTUAL + 1))

sed -i "s/versionCode = $CODIGO_ACTUAL/versionCode = $CODIGO_NUEVO/" "$GRADLE_APP"
sed -i "s/versionName = \"[^\"]*\"/versionName = \"$VERSION\"/" "$GRADLE_APP"

"$RAIZ/scripts/build.sh" :app:assembleRelease

APK_FUENTE="$RAIZ/app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK_FUENTE" ]; then
  echo "No se encontró el APK en $APK_FUENTE" >&2
  exit 1
fi

mkdir -p "$RAIZ/apk" "$RAIZ/dist"
DESTINO="$RAIZ/apk/obra-$VERSION.apk"
cp "$APK_FUENTE" "$DESTINO"
SHA="$(sha256sum "$DESTINO" | cut -d' ' -f1)"

cat > "$RAIZ/dist/update.json" <<JSON
{
  "versionCode": $CODIGO_NUEVO,
  "versionName": "$VERSION",
  "apkUrl": "https://github.com/$OWNER/$REPO/releases/download/v$VERSION/obra-$VERSION.apk",
  "sha256": "$SHA",
  "notas": "Versión $VERSION"
}
JSON

echo ""
echo "versionCode: $CODIGO_NUEVO"
echo "versionName: $VERSION"
echo "sha256:      $SHA"
echo "APK:         $DESTINO"
echo "manifiesto:  $RAIZ/dist/update.json"
echo ""
echo "Falta, en GitHub:"
echo "  1. https://github.com/$OWNER/$REPO/releases/new?tag=v$VERSION"
echo "     Arrastra $DESTINO como asset de la release."
echo "  2. Sube dist/update.json a la raíz del repo (rama main)."
echo "Hasta que exista el asset, la app da la comprobación por fallida en silencio."
