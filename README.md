# Obra — distribución del APK

Este repositorio solo sirve el APK firmado y el manifiesto de actualización de la app **Obra**.

- `update.json`: lo consulta la app al abrir para saber si hay versión nueva.
- Releases: contienen el `obra-X.Y.Z.apk`. La app descarga, comprueba el SHA-256 y lanza el instalador.

## Cómo publicar una versión nueva

1. El APK se genera con `scripts/nueva-version.sh` (o a mano) y queda en `apk/`.
2. Se sube como **asset** de una release con tag `vX.Y.Z` (arrastrar el fichero en la web de GitHub).
3. Se actualiza `update.json` con `versionCode`, `versionName`, `apkUrl`, `sha256` y notas.

La app solo acepta HTTPS, rechaza versiones con `versionCode` menor o igual y verifica el SHA-256 antes de instalar. Android siempre pide confirmación: es una actualización con un toque, no silenciosa.
