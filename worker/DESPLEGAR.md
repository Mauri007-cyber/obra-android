# Desplegar el intermediario (Cloudflare Workers)

El Worker es el puente entre la app y el modelo. La app no lleva la clave de Cloudflare.

**Aviso de seguridad:** este Worker no autentica las peticiones. Cualquier persona con la URL puede consumir el servicio y, si se configura `APY_TOKEN`, el saldo de ApyHub. El contador no es un límite fiable de gasto. No despliegues esta configuración públicamente ni actives el token hasta implementar protección de acceso y límites fiables.

## Lo que necesitas

- **Node.js 20 o superior**. Si tienes 18 o menos, Wrangler no arranca. Descárgalo de
  https://nodejs.org (versión LTS) y marca la casilla de añadir Node al PATH.
- Una cuenta de Cloudflare (la que ya has creado).
- 5 minutos y conexión a internet.

## Windows: lo más fácil

1. Descomprime el ZIP en una carpeta normal, por ejemplo `C:\obra-worker`.
   Evita `C:\Program Files`, OneDrive y las carpetas con permisos raros.
2. Doble clic en **`desplegar.cmd`**.
3. Se abre el navegador: entra en Cloudflare y pulsa **Allow**.
4. Al final verás algo así:

   ```
   https://obra-ia.TU-USUARIO.workers.dev
   ```

5. Copia esa dirección y pégala en la app:
   **Ajustes de IA → Dirección del intermediario**.

El script ya no hace `npm install`: usa `npx wrangler@4`, que descarga lo justo y evita el paso
que más fallaba en Windows.

## Mac / Linux

```bash
cd obra-worker
npx --yes wrangler@4 login
npx --yes wrangler@4 deploy
```

## Probar que funciona

Abre en el navegador del móvil:

```
https://obra-ia.TU-USUARIO.workers.dev/salud
```

Debe contestar algo como:

```json
{"ok":true,"modelo":"@cf/black-forest-labs/flux-2-klein-4b","simulado":false,"cupoDia":30}
```

Si `simulado` sale `true`, el Worker no ve el modelo: revisa que el bloque `ai` de
`wrangler.jsonc` siga ahí y vuelve a desplegar.

## Si el script dice que algo ha fallado

**No cierres la ventana**: se abre `despliegue.log` con el error exacto. Mándame ese texto y lo
resuelvo. Los tres fallos que ya conozco y el script ahora detecta:

| Mensaje en el log | Qué pasa | Arreglo |
|---|---|---|
| `workers.dev subdomain` | Tu cuenta aún no tiene subdominio de Workers | Abre el panel de Cloudflare, crea el subdominio (es un nombre, gratis) y vuelve a ejecutar |
| `not authenticated` / `10000` | La sesión no es válida | Vuelve a ejecutar y pulsa Allow |
| `EBADENGINE` / versión de Node | Node viejo | Instala Node 20+ |
| `EACCES` / `EPERM` | Carpeta sin permisos | Mueve la carpeta fuera de Program Files |
| `ENOTFOUND` / `ETIMEDOUT` | Sin red o cortafuegos | Comprueba conexión / antivirus |

Si el navegador no se abre al hacer login:

```bat
npx wrangler@4 login --browser=false
```

y sigue el enlace que imprime.

## Quitar el fondo de fotos (ApyHub, opcional)

La ruta `/quitar-fondo` usa ApyHub. Si no la vas a usar, no hay que hacer nada: contesta 503 con
un aviso claro y el resto del Worker sigue igual.

1. Crea una cuenta en <https://apyhub.com> y genera un **APY Token** en su panel.
2. Pégalo como secreto (no lo escribas en `wrangler.jsonc` ni en ningún archivo del repo):

   ```bash
   npx --yes wrangler@4 secret put APY_TOKEN
   ```

   Pega el token cuando lo pida. No hace falta volver a desplegar: el secreto se aplica al momento.
3. Pruébalo con una foto:

   ```bash
   curl -X POST https://obra-ia.TU-USUARIO.workers.dev/quitar-fondo -F "image=@foto.jpg" -o recorte.png -D -
   ```

   Si sale `Content-Type: image/png` y al abrir `recorte.png` el fondo son cuadros, está bien.
   ApyHub cobra 1.000 átomos por imagen.

## Ajustes del Worker (`wrangler.jsonc`)

- `MAX_DIA` — retoques por dispositivo y día (30 por defecto).
- `MODELO` — `@cf/black-forest-labs/flux-2-klein-4b`.
- `SIMULADO` — pon `"1"` para probar sin gastar cupo: devuelve la misma foto.
- `CORS` — `*` sirve para la app Android; si publicas la web, pon ahí tu dominio.

## Límite importante del modelo

FLUX.2 [klein] **solo acepta imágenes de entrada menores de 512×512**. Por eso:

- La app reduce la foto a 512 px antes de mandarla (desde la 0.9.4).
- El Worker rechaza con un mensaje claro cualquier foto mayor, en vez de dejar que el modelo
  falle sin explicar nada.
- La imagen que devuelve el modelo se pide con la misma proporción que la foto, hasta 1024 px.

Si usas la app 0.9.3 o anterior, el Worker te dirá que actualices: es normal.

## Qué cuesta

Workers AI tiene límites y precios que dependen del plan y del modelo. El contador actual no garantiza mantenerse dentro del cupo gratuito. Revisa los límites vigentes de la cuenta y configura controles fiables antes de usarlo.
