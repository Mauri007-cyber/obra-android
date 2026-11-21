# Proxy de IA (Cloudflare Workers AI)

Un solo Worker que guarda la clave de Cloudflare y llama al modelo. La app nunca ve la clave:
solo conoce esta dirección.

- Modelo: `@cf/black-forest-labs/flux-2-klein-4b` (4 pasos, hasta 4 imágenes de entrada).
- `MAX_DIA` es un límite orientativo por dispositivo, no un límite fiable de gasto. Consulta el aviso de seguridad antes de desplegar.
- `SIMULADO=1`: no llama al modelo y devuelve la misma foto. Sirve para probar la app sin gastar cupo.

## Quitar el fondo (ApyHub)

Ruta `POST /quitar-fondo`. Recibe la foto en `multipart/form-data` (campo `image`), la manda al
endpoint documentado de ApyHub —`/remove-background-from-images/multi-part/download`— y devuelve
el PNG transparente sin tocarlo.

- **Token:** `APY_TOKEN`, solo aquí. En producción:
  `npx wrangler secret put APY_TOKEN`. En local: copia `.env.example` a `.dev.vars` y pégalo.
  El token no va en `wrangler.jsonc`, ni en la app, ni en la vista previa.
- **Coste:** ApyHub cobra 1.000 átomos por imagen. Esta ruta no lleva cupo propio; si hace falta,
  se limita igual que `/retoque`.
- **Respuesta:** `Content-Type: image/png`, `Content-Disposition: attachment; filename="recorte.png"`
  y las cabeceras `X-Recorte-Transparencia` (`si`/`no`), `X-Recorte-Ancho` y `X-Recorte-Alto`.
- **Bordes:** el recorte automático no es perfecto (pelo, cristal, barandillas). Se avisa en la
  interfaz y no se promete otra cosa.
- **Original:** el Worker no guarda nada. Si ApyHub falla, contesta JSON con el motivo y la foto
  que el usuario ya tenía en pantalla no se toca.

```bash
curl -X POST https://obra-ia.TU-SUBDOMINIO.workers.dev/quitar-fondo \
  -F "image=@foto.jpg" -o recorte.png -D -
# Content-Type: image/png
# X-Recorte-Transparencia: si
```

En la vista previa web está en **Foto real → Quitar el fondo de una foto**
(`preview/quitar-fondo.html`).

## Puesta en marcha

```bash
cd worker
npm install
npx wrangler login          # una vez, en tu ordenador
npx wrangler deploy
```

Al desplegar, apunta la URL que te da (`https://obra-ia.TU-SUBDOMINIO.workers.dev`) y pégala en la app:
**Obra → Retoque con IA → Engranaje → Dirección del servicio**.

## Comprobarlo sin la app

```bash
curl https://obra-ia.TU-SUBDOMINIO.workers.dev/salud
# {"ok":true,"modelo":"@cf/black-forest-labs/flux-2-klein-4b","simulado":false,"cupoDia":30}

curl -X POST https://obra-ia.TU-SUBDOMINIO.workers.dev/retoque \
  -H 'Content-Type: application/json' \
  -H 'X-Dispositivo: prueba-1' \
  -d "{\"foto\":\"$(base64 -w0 foto.jpg)\",\"prompt\":\"Pon el suelo de tarima clara y las paredes blancas\"}"
```

Respuesta correcta:

```json
{ "imagen": "<base64>", "simulado": false, "ms": 8200, "modelo": "…", "quedan": 29 }
```

## Lo que hay que saber

- **Seguridad y gasto:** el Worker no tiene autenticación. Cualquier persona que conozca su URL puede usar ambas rutas, y `/quitar-fondo` puede consumir crédito de ApyHub. No actives secretos ni lo expongas públicamente hasta implantar control de acceso y de gasto fiables.
- **Contador:** Cache API es local a cada centro de datos, no es atómica, y `X-Dispositivo` puede falsificarse. `MAX_DIA` no protege contra abuso ni garantiza permanecer en el plan gratuito.
- **Fotos:** el Worker no las guarda deliberadamente, pero se envían al proveedor correspondiente. Consulta su política vigente antes de enviar datos de clientes.
- **CORS:** está abierto; CORS no es un mecanismo de autenticación.
- **Pruebas:** `node --test worker/prueba.mjs worker/prueba-handler.mjs worker/prueba-fondo.mjs`
  (no necesitan cuenta ni red: el recorte se prueba con un `fetch` de mentira).
