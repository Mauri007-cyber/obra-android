# Worker de imagen (plan gratuito de Cloudflare)

Convierte una foto de una estancia y un prompt en una imagen de la reforma con
**FLUX.2 klein 4B**, dentro de las 10.000 neuronas diarias gratuitas. La app nunca lleva la
cuenta: solo la dirección de este Worker.

## Qué cuesta

- Cuenta de Cloudflare **gratuita, sin tarjeta**.
- 10.000 neuronas al día ≈ **90 imágenes** de 1024×1024 (~110 neuronas cada una). Se reinicia a
  las 00:00 UTC.
- Plan Free de Workers: 100.000 peticiones al día.
- Al agotarse la cuota, el modelo falla y el Worker responde `429 sin_cuota`. **No hay factura
  posible**, porque la cuenta no tiene método de pago.

## Desplegar

```bash
export CLOUDFLARE_API_TOKEN=...    # permiso Workers AI + Workers KV
export CLOUDFLARE_ACCOUNT_ID=...
scripts/desplegar-worker.sh
```

El script crea el espacio KV `CONTADORES` la primera vez (y escribe su id en `wrangler.toml`),
publica el Worker e imprime la dirección. Después se compila la app con esa dirección:

```bash
scripts/build.sh :app:assembleDebug -PimagenUrl=https://obra-imagen.<cuenta>.workers.dev
```

Sin `-PimagenUrl`, la app funciona igual: avisa de que el servicio no está conectado y deja
Gemini y el retoque local.

## Contrato

```
GET  /salud      → { ok, modelo, vision, texto }
POST /imagen     → { imagen, descripcion, restantes }
                   429 { error: "sin_cuota" }
POST /propuestas → { propuestas: [ { titulo, resumen, actuaciones } ], restantes }
                   429 { error: "sin_cuota" }
```

**Imagen.** Cuerpo: `{ prompt, foto (base64), dispositivo, ancho, alto }`. La app reduce la foto a
menos de 512 px de lado, que es lo que acepta el modelo como referencia.

Antes de generar, el Worker **analiza la foto** con **llama-3.2-11b-vision** en dos pasadas:
una estructurada (`FLOOR`, `WALLS`, `CEILING`, `OPENINGS`, `FIXED`, `VIEWPOINT`) y otra centrada
en paredes (`LAYOUT`: qué hay en la izquierda, la derecha y el fondo). El resultado se coloca
**delante** del prompt como «Existing room, keep every item in the same place», y su sitio está
reservado: si el prompt del cliente es muy largo se recorta **la petición**, nunca la lista de lo
que no se debe tocar. Tope final: 2000 caracteres.

Corregir una imagen generada usa la misma ruta: la app manda esa imagen y un prompt de «cambia
solo esto». El análisis de la nueva foto protege posiciones, colores y materiales del resto.

Si la visión falla, se genera igual y `descripcion` viene vacío (el fallo queda en el log del
Worker).

**Propuestas.** Cuerpo: `{ dispositivo, superficie, altura, presupuesto, estancias[], permite[],
deseo, catalogo[] }`. El catálogo lo manda la app (`clave`, `nombre`, `unidad`) y el modelo de
texto **solo puede usar esas claves**. La app lo recorta antes de enviarlo según los chips que
haya marcado el cliente («tirar paredes», «pintar»…): lo que no esté permitido ni siquiera llega
al modelo, y lo que se cuele se tira al validar. El Worker devuelve hasta 3 escenarios de 3 a 7
partidas y la app vuelve a validar, calcula los importes con sus precios y las deja como
propuestas. Las estancias se comparan sin tildes, para que «Salón» y «Salon» no acaben como dos
habitaciones distintas.

## Límites que conviene saber

- **20 imágenes por móvil y día** (`LIMITE_DIA`): evita que un solo teléfono se coma la cuota de
  todos. Se cuenta con una huella del identificador anónimo, no con el aparato.
- **10 tandas de propuestas por móvil y día** (`LIMITE_PROPUESTAS`).
- La visión añade neuronas a cada imagen, pero sigue entrando de sobra en las 10.000 diarias.
- La salida es 1K. El modelo puede mover algún mueble o una línea: es una vista previa, no un
  render de estudio.
- El Worker es público (la app no puede guardar secretos). La protección real es la cuota diaria
  y el tope de neuronas; si algún día hace falta más, se pone delante Cloudflare Turnstile.

## Prueba local

```bash
node worker-obra/prueba.mjs
```

Usa un `AI` y un `CONTADORES` falsos: comprueba rutas, validaciones, cuota por móvil, traducción
de la cuota agotada y del fallo del modelo. No necesita cuenta ni red.
