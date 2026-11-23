# Obra

App Android para medir una estancia, sacar el plano, presupuestar una reforma y **enseñar la
obra terminada** al cliente. **Es 100 % gratuita**: no hay cuentas, no hay nube obligatoria y no
se llama a ningún servicio de pago.

- Planteamiento del producto: [PLAN-OBRA-COMPLETA.md](PLAN-OBRA-COMPLETA.md)
- Hoja de ruta y estado: [ROADMAP.md](ROADMAP.md)

## Estado: 0.9.4

- **Proyecto por estancias**: cada obra tiene las habitaciones que haga falta y cada habitación
  una lista de cambios elegidos de un catálogo de más de 45 partidas por oficio.
- **Evaluación del cliente**: cada cambio se aprueba, se deja pendiente o se descarta. Lo
  aprobado es lo que cuenta para el dinero y para lo que se enseña.
- **Imagen fotorrealista, gratis**: lo aprobado se convierte en un prompt y se pide la imagen
  a un Worker propio sobre el plan gratuito de Cloudflare Workers AI (sin ninguna clave en el
  APK), o se abre la foto en Gemini para la máxima calidad. Si no hay red, no hay servicio o se
  agota la cuota del día, se sigue con el retoque local.
- **Retoque de la foto, sin conexión**: se tiñe el suelo, la pared y el techo de la foto con el
  material aprobado, se encienden las luces nuevas y se compara el antes y el después. No sale
  del móvil y no tiene cuota.
- **Lenguaje natural sin conexión**: «suelo de tarima clara y paredes blancas, tres enchufes»
  se convierte en cambios del catálogo con sus acabados y cantidades. Son reglas sobre el
  propio catálogo, no un servicio externo: entiende lo que conoce y no inventa partidas.
- **Recorrido**: muestra la foto original y un listado de cambios aprobados por estancia.
- **Sin servicios de pago.** Medir, dibujar el plano, presupuestar, el PDF, las copias, el
  resumen y el retoque se calculan en el móvil, sin conexión. Las dos vías de imagen son
  opcionales, gratuitas y nunca están en el camino crítico.
- Las conexiones de red son la comprobación opcional de actualizaciones en GitHub y, solo si se
  pulsa generar, el envío de la foto al servicio de imagen que se elija.

## Distribución y actualizaciones

- `update.json`: lo consulta la app al abrir para saber si hay versión nueva.
- Releases: contienen `obra-X.Y.Z.apk`. La app descarga, comprueba el SHA-256 y lanza el
  instalador. Android siempre pide confirmación: es una actualización con un toque, no silenciosa.
- Para publicar: subir el APK como asset de una release `vX.Y.Z` y actualizar `update.json` con
  `versionCode`, `versionName`, `apkUrl` y `sha256`.

## Compilar

El toolchain vive fuera del repo (`$HOME/toolchain`): JDK, Gradle 8.13 y Android SDK.

```bash
scripts/build.sh :app:assembleDebug     # APK de pruebas
scripts/build.sh :app:assembleRelease   # APK firmada
```

### La firma es lo que permite actualizar

Todas las versiones deben ir firmadas con la misma clave. La del proyecto está fuera de Git
  (`$HOME/obra-keys/obra-release.jks` y `keystore.properties`). **Si se pierde, no se puede
actualizar**: habría que desinstalar y reinstalar. Verifica que tienes una copia segura de la clave y de los datos.

## Imagen fotorrealista gratis

Hay dos formas de enseñar la obra terminada, las dos sin pagar nada y sin cuentas para el
usuario, más el retoque local que ya existía.

### 1. En la app, con un Worker propio (cuota diaria gratuita)

La app convierte los cambios aprobados en un prompt (`dominio/PromptReforma.kt`) y pide la
imagen a un Worker propio sobre el **plan gratuito de Cloudflare Workers AI**
(FLUX.2 klein 4B). La cuenta vive en el Worker: **en el APK no hay ninguna clave**. Son 10.000
neuronas al día, unas 90 imágenes de 1024×1024; el Worker limita además a 20 imágenes por móvil
para que un teléfono no se coma la cuota de los demás. Al agotarse, responde `429` y la app
sigue con el retoque local. Nunca se cobra, porque el plan gratuito no tiene método de pago.

Para conectarlo hace falta una cuenta gratuita de Cloudflare, sin tarjeta:

```bash
export CLOUDFLARE_API_TOKEN=...      # Workers Scripts, Workers KV Storage y Workers AI (Edit)
export CLOUDFLARE_ACCOUNT_ID=...
scripts/desplegar-worker.sh          # crea el contador y publica el Worker
scripts/build.sh :app:assembleDebug -PimagenUrl=https://obra-imagen.<cuenta>.workers.dev
```

Sin `-PimagenUrl` la app funciona igual: el botón de generar avisa de que falta conectar el
servicio, y Gemini y el retoque local siguen disponibles.

### 2. Abriendo la foto en Gemini (máxima calidad, dos toques)

La app prepara la foto y el prompt y abre el menú de compartir; se elige Gemini, se envía y se
guarda la imagen. Después, «Ya la tengo: traer la imagen» la mete en la estancia. Es el modelo
Nano Banana de la cuenta gratuita de Google: la mejor calidad sin coste, con sus límites (se
refresca cada 5 horas, descarga a 1K y lleva marca de agua).

### 3. Retoque local (sin conexión)

Todo lo que hace falta para trabajar sin red:

- **Retoque local**: pinta el material aprobado sobre la zona de la foto que le corresponde
  (suelo, pared, techo), conserva la textura y las sombras, y enciende las luces añadidas. Es
  pintura sobre lo que ya hay: no se inventan muebles nuevos. Para eso están las otras dos
  vías, que sí generan la imagen entera.
- **Entender la frase**: convierte lo escrito en lenguaje normal en partidas del catálogo, sin
  red. Si la frase pide algo que no está en el catálogo, no se propone nada y se dice.

Cualquier cosa que se añada en el futuro tendrá que ser gratuita, funcionar sin cuenta y no
estar nunca en el camino crítico.

### La foto sale del móvil solo si se pide

Generar en la app envía la foto al Worker (Cloudflare declara retención cero en FLUX.2). Abrir
Gemini envía la foto y el texto a Google, y en cuenta gratuita Google puede usarlos para mejorar
sus productos. La app lo avisa y pide permiso la primera vez. El retoque local no envía nada.

La imagen generada es una **vista previa orientativa**: no sustituye al presupuesto, ni a las
medidas, ni a una visita a la obra.

## Limitaciones importantes

- No se ha probado la app en un dispositivo real ni la migración de bases de datos con datos reales. Las pruebas instrumentadas se ejecutan en un emulador Android (API 35): restauran una copia con foto, planos y presupuesto, y recorren las pantallas de presupuesto, plano y copia con una base de datos en memoria. Faltan pruebas entre dispositivos, con fotos reales y de la cámara/AR.
- Cada estancia permite editar su superficie, altura y plano. El presupuesto incluye únicamente cambios aprobados por estancia y exige cantidades y precios válidos; sin aprobaciones no genera partidas. Hay que comprobar las medidas y los precios en obra.
- La APK de depuración usa el identificador `es.obra.app.prueba`: se instala como una app distinta, sin acceso a los datos ni a las actualizaciones de la versión normal.
- Existe una copia ZIP por obra con datos y fotos de estancias, y restauración como una obra nueva. La lectura comprueba la integridad del ZIP y que las fotos puedan decodificarse. Una prueba instrumentada ha pasado en emulador Android con datos de prueba; no se ha probado la restauración entre dispositivos con datos reales. No borres los originales basándote en esta copia. La exportación antigua `.obra` sigue sin fotos y no es restaurable.
- El manifiesto de GitHub puede apuntar a una versión anterior; publicar una actualización requiere verificar el APK firmado, su release y el manifiesto.

## Pruebas

```bash
scripts/build.sh :app:testDebugUnitTest
scripts/build.sh :app:connectedDebugAndroidTest # requiere emulador o dispositivo conectado
```

Las pruebas instrumentadas de `app/src/androidTest` cubren la restauración con foto, los planos
por estancia, el presupuesto (solo aprobados, totales y bloqueo de precios inválidos), las
pantallas de copia y el retoque (frase entendida y foto retocada sin conexión). Se ejecutan en
el emulador de GitHub Actions en cada subida a `main`.

Las pruebas unitarias cubren, entre otras cosas, la interpretación de frases: acentos, acabados,
cantidades escritas con número o con palabra y partidas que no se deben inventar. También el
prompt de imagen (materiales en inglés, encuadre que se conserva, nada que inventar) y la
lectura de las respuestas del servicio (cuota agotada, error, imagen válida).

El Worker tiene su propia prueba, sin cuenta ni red:

```bash
node worker-obra/prueba.mjs
```
