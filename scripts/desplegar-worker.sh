#!/usr/bin/env bash
# Publica el Worker de imagen en el plan gratuito de Cloudflare.
#
#   export CLOUDFLARE_API_TOKEN=...   # Workers Scripts + Workers KV Storage + Workers AI (Edit)
#   export CLOUDFLARE_ACCOUNT_ID=...
#   scripts/desplegar-worker.sh
set -euo pipefail

cd "$(dirname "$0")/../worker-obra"

: "${CLOUDFLARE_API_TOKEN:?Falta CLOUDFLARE_API_TOKEN (permisos: Workers Scripts:Edit, Workers KV Storage:Edit, Workers AI:Edit)}"
: "${CLOUDFLARE_ACCOUNT_ID:?Falta CLOUDFLARE_ACCOUNT_ID}"
export CLOUDFLARE_API_TOKEN CLOUDFLARE_ACCOUNT_ID

if ! command -v npx >/dev/null 2>&1; then
  echo "Hace falta Node/npm para ejecutar wrangler." >&2
  exit 1
fi

if grep -q 'PENDIENTE' wrangler.toml; then
  echo "Creando el contador KV CONTADORES…"
  if ! salida="$(npx --yes wrangler kv namespace create CONTADORES 2>&1)"; then
    printf '%s\n' "$salida"
    echo "No se pudo crear el contador. Revisa que el token tenga 'Workers KV Storage: Edit'." >&2
    exit 1
  fi
  printf '%s\n' "$salida"
  id="$(printf '%s' "$salida" | grep -oE '[0-9a-f]{32}' | head -n1 || true)"
  if [ -z "$id" ]; then
    echo "No se pudo leer el id del espacio KV. Rellénalo a mano en wrangler.toml." >&2
    exit 1
  fi
  sed -i "s/PENDIENTE/$id/" wrangler.toml
  echo "Contador creado: $id"
fi

echo "Publicando el Worker…"
if ! npx --yes wrangler deploy; then
  echo "No se pudo publicar. Revisa que el token tenga 'Workers Scripts: Edit'." >&2
  exit 1
fi

cat <<'FIN'

Listo. Copia la dirección que ha impreso wrangler y compila la app con ella:

  scripts/build.sh :app:assembleDebug -PimagenUrl=https://obra-imagen.<cuenta>.workers.dev

Comprueba que responde:

  curl https://obra-imagen.<cuenta>.workers.dev/salud
FIN
