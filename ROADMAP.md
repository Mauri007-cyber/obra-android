# Roadmap de trabajo — "Obra" (Android)

> Documento vivo. Cada hito tiene un entregable que **se puede instalar y usar**, y un criterio de aceptación que se comprueba en un móvil real.
> Plan de producto: [PLAN-APP-OBRA.md](PLAN-APP-OBRA.md)

**Objetivo:** una app Android que escanee una estancia, dé un plano medido y editable, permita plantear la reforma y saque un presupuesto en PDF. **Coste 0 €, sin cuenta, sin conexión obligatoria y autoactualizable.**

---

## Restricciones globales (aplican a todas las tareas)

| # | Restricción | Valor exacto |
|---|---|---|
| G1 | Coste | 0 € de infraestructura. Sin Play Store (25 $), sin hosting de pago, sin suscripciones |
| G2 | Offline | El camino crítico (medir → plano → reforma → PDF) funciona en modo avión |
| G3 | Android | `minSdk 26` (Android 8), `targetSdk 35`, Kotlin 2.x, Compose |
| G4 | Distribución | APK firmado con keystore propio; **actualización dentro de la app, sin desinstalar** |
| G5 | Datos | Todo en el móvil (Room). Sin cuentas, sin analítica, sin rastreo |
| G6 | IA | Opcional, por proxy propio, solo cupos gratuitos sin tarjeta; nunca en el camino crítico |
| G7 | Accesibilidad | Toques ≥ 56 dp, texto ≥ 18 sp, contraste AA, tema claro forzado, funciona con guantes |
| G8 | Licencias | MIT / Apache-2.0 / BSD / OFL dentro del APK. GPL solo como herramienta externa |

---

## Autoactualización (requisito de primera clase)

**Diseño.** La app consulta un manifiesto de versión por HTTPS, compara `versionCode`, descarga el APK nuevo, comprueba su SHA-256 y lanza el instalador del sistema. Al ser el mismo paquete, la misma firma y un `versionCode` mayor, **Android reemplaza la app conservando datos, obras y fotos**. No se desinstala nada.

```
App  ──GET──►  update.json          { "versionCode": 7, "versionName": "0.7.0",
   (cada 24 h o al abrir)            "apkUrl": "https://…/obra-0.7.0.apk",
                                     "sha256": "…", "notes": "…" }
      ──GET──►  APK  ──SHA-256 ok──►  Intent.ACTION_VIEW (application/vnd.android.package-archive)
                                        └─ diálogo del sistema: "Actualizar" → datos intactos
```

| Control | Por qué |
|---|---|
| Solo HTTPS y host fijado en código | Evita que un manifiesto manipulado apunte a otro sitio |
| SHA-256 obligatorio del APK | Un APK corrupto o sustituido se rechaza antes de instalar |
| Rechazo de `versionCode` menor o igual | Evita downgrades y bucles de actualización |
| Nunca instalar sin acción del usuario | Android lo exige; además es lo correcto |
| Permiso `REQUEST_INSTALL_PACKAGES` + FileProvider | Único modo legítimo de instalar un APK desde la propia app |
| Comprobación silenciosa, aviso discreto | No interrumpir una medición en obra |

**Limitación honesta (va escrita en la app):** Android **siempre** muestra una confirmación de instalación; la actualización totalmente silenciosa solo existe con Play Store, dispositivos gestionados (MDM) o root. La primera vez hay que conceder "Instalar apps desconocidas" a Obra: es un toque, y solo una vez.

**Publicación del APK y del manifiesto:** GitHub Releases (gratis) sobre el repo del proyecto. El manifiesto `update.json` se genera en cada release desde la CI o a mano.

---

## Publicación y autoactualización reales (20-nov-2026)

- Repo público de distribución: **https://github.com/Mauri007-cyber/obra-android** (solo APK y manifiesto, sin código).
- Manifiesto: `https://raw.githubusercontent.com/Mauri007-cyber/obra-android/main/update.json` → es la `UPDATE_URL` compilada en la app desde la 0.9.2.
- Release de la versión: `https://github.com/Mauri007-cyber/obra-android/releases/tag/v0.9.2` (el APK se sube como asset; las herramientas del asistente no pueden subir binarios).
- `scripts/publicar.sh X.Y.Z` deja hecha la compilación firmada, el SHA-256 y el `update.json` con las URLs correctas.

## Revisión de robustez (20-nov-2026)

Pasada por toda la app buscando lo que puede fallar en un móvil de verdad, con plan de respaldo en cada punto:

- **Vídeo**: se guardaban las 96 imágenes a la vez (>100 MB) → cierre seguro en móviles modestos. Ahora se escribe fotograma a fotograma; solo hay una imagen en memoria.
- **GIF**: un color de cristal que no estaba en la paleta se pintaba mal; ahora se busca el más parecido.
- **Base de datos**: si una migración falla y la base no abre, se aparta el archivo (`obra-rota-<fecha>.db`) y la app arranca vacía en vez de no abrir nunca más.
- **PDF**: se generaba en el hilo de la interfaz; ahora en segundo plano.
- **Compartir**: si el menú del sistema falla, la app no se cae.
- **Imágenes grandes**: `largeHeap` y aviso claro si no hay memoria para la lámina o el vídeo.
- **Rotación**: girar el móvil ya no reinicia la pantalla.
- **Modelo 3D**: un muro más corto que una puerta rompía el render (`coerceIn` con rango vacío).
- **Presupuesto**: cobraba m² de tabique usando metros lineales (salía más barato de lo real); corregido.
- **Precios**: un texto corrupto con `NaN` envenenaba el total; filtrado.
- 53 pruebas en verde.

## Hitos

### H0 · Esqueleto instalable + autoactualización — *hecho (v0.1.0)*
**Entregable:** APK que arranca, guarda una obra y se actualiza a sí mismo.
- [x] Proyecto Gradle + Compose + tema "sol y guantes"
- [x] Room: `Obra`, `Estancia`, `Muro`, `Hueco`
- [x] Pantallas: Inicio (lista de obras), Nueva obra, Obra
- [x] Export `.obra` (JSON) y compartir
- [x] `Actualizador` + pantalla "Actualizar" con progreso, SHA-256 y rechazo de downgrade
- [x] Firma de release con keystore local (fuera de Git)
- [x] `assembleRelease` produce un APK firmado (12 MB)
- [x] Aviso automático de versión nueva al abrir (silencioso, con banda y sin bloquear)
- [ ] Import `.obra` (queda para H7, con el ZIP y las fotos)
- [ ] Publicar `update.json` y el APK en un host HTTPS y apuntar `UPDATE_URL`

**Aceptación:** se instala en el móvil, crea una obra, cierra la app, la abre y sigue ahí; con `versionCode` subido, el aviso de actualización aparece y actualizar **conserva la obra**.

### H1 · Escanear y medir — *en curso (medición AR hecha)*
**Entregable:** medir una habitación de tres formas y comparar con el láser.
- [x] `ArDisponible` con `ArCoreApk.checkAvailability()`: distingue disponible / hay que instalar / no soportado
- [x] Medición en AR con retícula central y dos puntos (SceneView + ARCore), offline
- [x] Rechazo de medidas absurdas (< 5 cm o > 60 m), ± mostrado y un solo decimal
- [x] **Mediana de las últimas 12 lecturas** en vez de un fotograma suelto (quita el temblor)
- [x] Aviso de lectura inestable: la retícula se pone roja y no deja marcar
- [x] **Corrección con una medida real**: «¿son 3,42 m?» → si no, se escribe la buena y se guarda el factor en la obra
- [x] Detección del error de teclear 342 en vez de 3,42
- [x] Migración de base de datos 1 → 2 para no perder obras al añadir el factor
- [x] 28 pruebas unitarias (geometría + medidas + polígono), en verde
- [ ] Escaneo de sala con muros y huecos (exige SceneView 4.69: compileSdk 37 + AGP 9.4)
- [x] Enderezado de ángulos (imán 0/45/90° ±3°) y cierre del polígono en el editor de plano
- [ ] Foto + referencia con homografía (4 esquinas + medida conocida)
- [ ] Dictado con `SpeechRecognizer`

**Aceptación:** 5 estancias medidas por él y por láser: desviación media < 5 %, ninguna > 10 %.

### H2 · Plano 2D editable — *en curso (editor completo y medido)*
- [x] Lienzo con rejilla (10 cm / 1 m), zoom con dedos y con botones, desplazamiento
- [x] Muro con grosor, imán a rejilla de 5 cm y a 0/45/90° (±3°), deshacer
- [x] Cotas por muro siempre visibles y metros lineales totales
- [x] Guardado automático de cada muro (sin botón de guardar)
- [x] **Seleccionar un muro tocándolo** (margen de 12 cm para el dedo) y actuar sobre él
- [x] Huecos (puerta 0,80 × 2,05, ventana 1,2 × 1,2) dibujados como corte en el muro
- [x] **Superficie del polígono cerrado** (tolerancia 3 cm en las esquinas); en una L da la L, no el rectángulo
- [x] Paredes exactas: se descuentan los huecos y no se cuenta lo que se tira (en medidas y en el PDF)
- [x] Migración de base de datos 2 → 3 (`superficieCerrada`) sin perder obras
- [x] 7 pruebas del contorno (cierre, orden, muro suelto, forma en L)
- [x] Sugerencia de puerta y ventana al tocar un muro («poner lo típico», se borra con un toque)
- [x] 40 pruebas unitarias en verde
- [ ] Importar foto de plano, escalar con cota conocida, calcar
- [ ] Exportar la planta como imagen suelta

**Aceptación:** reproduce el plano de una casa real en < 10 min.

### H3 · Reforma
- [ ] Copia inmutable del "antes"; el "después" como estado de muro
- [x] Tirar muro (rojo discontinuo), muro nuevo (verde), abrir hueco
- [ ] Mover puertas, ventanas, sanitarios, radiadores
- [ ] Zonas de suelo/pared/techo con material
- [x] Resumen en texto llano con los metros exactos (`ResumenReforma`, también en la lámina)
- [ ] Aviso de muro de carga

**Aceptación:** el resumen coincide con lo dibujado, al metro.

### H4 · Presentación visual para el cliente — *la fase que decide si dice sí*

> **Reformulación (19-nov-2026).** El usuario del móvil no es el que decide: decide el cliente, que no lee planos. Un plano no le dice nada; una imagen que reconoce como "su casa" sí. Por eso esta fase deja de ser "ver el 3D" y pasa a ser **fabricar el material que convence**: imágenes, una lámina y un vídeo corto que se mandan por WhatsApp.

**Prerrequisito — H3 mínimo (2 días):** marcar un muro como tirado o nuevo tocándolo (los colores ya están en el editor), abrir huecos y un resumen en texto. Sin esto el "después" no significa nada.

- [x] **3D del antes y del después desde la misma cámara** (motor propio, sin librerías): muros extruidos con su grosor, **huecos recortados en su sitio y a su altura** (puerta desde el suelo, ventana con alféizar de 0,95 m y cristal), suelo troceado del contorno real y corte tipo casa de muñecas para poder mirar dentro
- [x] **PNG a 2048 px** del antes y del después, sin depender de la pantalla
- [x] **Lámina "antes / después"** (A4 horizontal y PNG cuadrado para WhatsApp): las dos imágenes, el titular y tres frases sacadas de lo dibujado
- [x] **Modo presentación** a pantalla completa en el móvil: sin barras ni menús, la estancia gira sola, un toque cambia antes/después y la pantalla no se apaga mientras se enseña
- [ ] Materiales PBR, techo y luz de día real (el render actual es color plano con sombreado)
- [ ] **Reconocer puertas y ventanas desde una foto** (hoy se colocan desde el plano y con sugerencias por longitud de muro; hace falta un modelo en el móvil)
- [x] **Vídeo de órbita de 8 s** del después, en GIF (96 fotogramas, 640×480, ~0,6 MB): se abre solo en WhatsApp, en bucle y sin sonido, y no depende de codecs del móvil (rasterizador y codificador propios, verificados en el escritorio)
- [ ] **Moodboard opcional con IA** (FLUX schnell en Cloudflare, cupo gratis ≈173 imágenes/día), siempre etiquetado "idea de estilo, no es tu casa"
- [ ] **Foto real del sitio** como textura de pared, para que el 3D se parezca a lo que el cliente tiene delante
- [ ] Compartir imágenes y lámina por el menú del sistema

**Aceptación:** un cliente que nunca ha visto un plano entiende qué cambia en menos de 15 segundos y sabe señalar qué se tira; la lámina se abre bien en un iPhone desde WhatsApp y pesa menos de 2 MB.

**Límite honesto:** el 3D es fiel en geometría y materiales, no fotorrealista. Un render fotorrealista que respete *ese* salón no está en ningún cupo gratuito (la generación de imágenes de Gemini es de pago y las variantes con profundidad también). Lo gratis es: 3D decente + foto real como textura + moodboard de estilo.

### H5 · Dinero — *ficha y presupuesto hechos (v0.9.0)*
- [x] Cómputo de materiales (pintura, azulejo) con rendimientos de manual
- [x] Ficha de obra en A4 con `PdfDocument` nativo: medidas, materiales y croquis
- [x] Compartir por WhatsApp con el menú del sistema
- [ ] Rendimientos editables y guardados por él
- [x] Partidas con precios propios, mano de obra incluida e IVA
- [x] Precios editables **por obra**, guardados (migración 3 → 4) y usados en el cálculo
- [x] Presupuesto en PDF A4: partidas con metros reales, base, IVA y total
- [x] Las partidas salen de lo dibujado: demolición, tabique nuevo, puertas, ventanas, suelo y pintura (con huecos y muros tirados descontados)

**Aceptación:** presupuesto real enviado por WhatsApp.

### H6 · IA opcional
- [ ] Cloudflare Worker proxy: clave fuera del APK, token por instalación, tope diario
- [ ] Cascada Gemini → Workers AI → Groq, caché por hash
- [ ] Leer foto de plano → borrador editable
- [ ] Redactar presupuesto en lenguaje de cliente
- [ ] Interruptor "IA desactivada" que deja la app 100 % offline

**Aceptación:** con IA caída, la app no muestra un solo error y todo lo demás funciona.

### H7 · Robustez y entrega
- [ ] Diario de obra (fotos, notas dictadas, incidencias)
- [ ] Copia automática semanal y export `.obra`
- [ ] Modo "solo lectura" para enseñar al cliente
- [ ] Onboarding de 3 pantallas y manual de un folio
- [ ] Revisión de accesibilidad al sol y con guantes

**Aceptación:** una semana de uso real sin pedir ayuda.

---

## Diseño (aplicado desde H0)

**Dirección:** herramienta de obra, no app de diseño de interiores. Nada de gradientes ni tarjetas flotantes; el contenido manda.

- **Color:** papel de plano cálido `#F7F5F0`, tinta `#14181C`, gris de cota `#6B7280`, y **un solo acento**: amarillo de obra `#F2B01E` (marcas, medición en curso, acción principal). Rojo `#C0392B` solo para "tirar"; verde `#2E7D4F` solo para "nuevo".
- **Tipografía:** una sola familia, **Inter** (OFL), con dos roles: cifras en `tabular-nums` a 28–40 sp (las medidas son el héroe) y texto a 18–20 sp. Sin versalitas, sin etiquetas decorativas.
- **Estructura:** rejilla de 8 dp, bordes de 1 dp como líneas de cota (no sombras), esquinas de 4 dp. La pantalla de obra se lee como una ficha de obra: medidas arriba, acciones abajo.
- **Un momento orquestado:** al terminar un escaneo, el polígono se cierra y las cotas se dibujan una tras otra. Nada más se anima.
- **Regla de una sola pasada:** si algo no ayuda a medir, presupuestar o decidir, no entra.

---

## Seguridad (modelo de amenazas corto)

| Activo | Amenaza | Control |
|---|---|---|
| Clave de IA | Extracción del APK | Nunca en el APK; solo en el Worker, como secreto |
| Cupo de IA | URL filtrada y usada por terceros | Token por instalación + tope diario por dispositivo en el Worker |
| Actualizaciones | APK sustituido (MITM, host comprometido) | HTTPS + host fijado + **SHA-256 obligatorio** + rechazo de downgrade |
| Firma | APK de terceros con el mismo nombre | Android rechaza firmas distintas; keystore fuera del repo y con copia de seguridad |
| Datos del cliente | Fuga por copias | Todo local, export cifrado opcional, sin nube por defecto |
| Export `.obra` | ZIP malicioso al importar | Límite de tamaño, lista blanca de rutas (sin `..`), validación de JSON con esquema |
| Permisos | Cámara/micrófono de más | Solo al usar; sin ubicación ni contactos; almacenamiento con scoped storage |
| Superficie de red | Peticiones no deseadas | Solo el manifiesto de actualización y el proxy de IA; hosts en lista blanca |

---

## H6 · Fotorrealismo sobre la foto e IA opcional

**Idea:** el «después» deja de ser un 3D dibujado y pasa a ser la foto de la obra, con sus ventanas,
su puerta y su luz. Encima, se tiñen paredes y suelos, se colocan luces y, si se quiere, un modelo
propone cambios a partir de una frase.

**Regla que no se rompe:** la IA **nunca** está en el camino crítico. Sin conexión o sin clave, la app
sigue midiendo, dibujando el plano, enseñando y presupuestando. La IA solo añade una propuesta.

### Fases

| Fase | Qué entrega | Estado |
|---|---|---|
| **F0** | Proxy en Cloudflare Workers AI (clave fuera del APK, cupo diario, modo simulado) | **Hecho** — `worker/` con 8 pruebas en verde |
| **F1** | Pantalla «Retoque con IA» en la app: foto, frase, propuestas, «¿qué ha fallado?» y corrección | **Hecho** — compila; falta probar con el Worker desplegado |
| **F2** | Foto real con superficies: esquinas, homografía y materiales encima | Siguiente |
| **F3** | Luces colocadas sobre la foto y su partida en el presupuesto | Pendiente |
| **F4** | Máscara de ventana y puerta para que la IA no mueva la geometría | Pendiente |

### F0 · Proxy (hecho)

- [x] `worker/src/index.js`: `POST /retoque` y `GET /salud`.
- [x] Validación en servidor: tamaño (4 MB), JPEG/PNG de verdad, frase entre 10 y 1200 letras.
- [x] Cupo por dispositivo y día (`MAX_DIA`, 30 por defecto) sin base de datos.
- [x] `SIMULADO=1`: devuelve la misma foto para poder probar la app sin gastar neuronas.
- [x] Normalizador de respuestas: FLUX devuelve base64, Stable Diffusion devuelve bytes.
- [x] `node --test worker/prueba.mjs` — 8 pruebas, sin cuenta ni red.

**Lo que hace falta de ti (10 minutos):**
`cd worker && npm install && npx wrangler login && npx wrangler deploy`, y pegar la URL en
**Obra → Retoque con IA → Servicio**.

### F1 · Pantalla de retoque (hecho)

- [x] `ia/PromptRetoque.kt`: el prompt se compone de botones (frases, paredes, suelos, luces, fallos).
      Siempre cierra con «no cambies el tamaño ni el sitio de puertas y ventanas», y recorta antes de 1200.
- [x] `ia/CupoDiario.kt`: tope en el móvil, con reinicio por día.
- [x] `ia/ClienteIA.kt`: `HttpURLConnection`, 120 s de espera, errores traducidos a lenguaje de obra.
- [x] `ia/AjustesIA.kt`: dirección, consentimiento y cupo. La clave de Cloudflare no está aquí.
- [x] `ui/PantallaRetoque.kt`: cámara o galería, frase, chips, resultado, «¿qué ha fallado?», corregir,
      guardar y enviar, aviso de que la foto sale del móvil la primera vez.
- [x] 6 pruebas del prompt + 4 del cupo.

**Criterio de aceptación:** tres fotos reales de habitaciones distintas, una frase cada una, y que en
menos de 40 s vuelva una imagen que él reconozca como su habitación.

### F2 · Foto y superficies (siguiente)

- [ ] `foto/Captura.kt` (CameraX) y archivo en `filesDir`.
- [ ] `foto/Superficies.kt`: 4 puntos por superficie y `Matrix.setPolyToPoly` (sin OpenCV).
- [ ] `foto/Compositor.kt`: material con `BitmapShader` + `MULTIPLY`, huecos recortados y devueltos.
- [ ] Paleta de 6 paredes y 5 suelos, antes/después a un toque.

**Criterio:** la mancha de sol de la foto original sigue visible después de pintar la pared.

### F3 · Luces

- [ ] Toque sobre la foto → punto de luz con tipo y potencia; halo y sombra.
- [ ] Cada punto genera su partida en el presupuesto (`punto de luz`).

### F4 · Máscara y fidelidad

- [ ] Máscara PNG de ventana y puerta que se manda como segunda imagen al modelo.
- [ ] Si el resultado mueve la geometría, avisar y ofrecer reintento con más peso a la máscara.

### Coste y límites (medidos, no supuestos)

| Concepto | Dato |
|---|---|
| Plan gratuito Workers AI | 10.000 neuronas/día |
| Tope por dispositivo | 30 imágenes/día (configurable en `wrangler.jsonc`) |
| Coste por imagen | 0 € dentro del cupo |
| Gemini/Nano Banana | 0,039–0,067 $ por imagen, **sin nivel gratuito** |
| Fotos | No se guardan; salen del móvil solo con permiso explícito |

### Riesgos y plan B

| Riesgo | Qué se hace |
|---|---|
| Se agota el cupo diario | La app avisa y sigue funcionando; mañana se reinicia |
| El modelo mueve la puerta | Botón «la puerta no está donde era» y, en F4, máscara |
| Cloudflare cambia el modelo | `MODELO` es una variable del Worker: se cambia sin tocar la app |
| No hay cobertura en la obra | La pantalla lo dice; el resto de la app no se entera |
| La imagen no se parece | Se queda el compositor de F2, que es fiel por construcción |

---

## H7 · Obra completa (replanteo)

La reforma deja de ser «una foto con un prompt» y pasa a ser un proyecto con estancias,
cambios tipificados, aprobación del cliente y recorrido final. Detalle en
`PLAN-OBRA-COMPLETA.md`.

### F0 · Modelo y evaluación (hecho)

- [x] Catálogo de más de 45 partidas por oficio, con unidad, precio, acabados y capa visual.
- [x] `Proyecto` → `EstanciaProyecto` → `Cambio` con estados propuesto/aprobado/descartado
      y grupos A/B para dos propuestas.
- [x] Coste aprobado, coste pendiente, progreso, avisos y guion del recorrido.
- [x] `PromptEstancia`: el prompt sale del paquete aprobado, en orden de trabajo, con recorte
      y regla final fija.
- [x] Room v5: tabla `cambios`, superficie y foto por estancia, migración sin pérdida.
- [x] Pantalla «Proyecto»: estancias, catálogo con buscador, aprobación, recorrido final.
- [x] Vista web navegable con 4 estancias y comparador antes/después.

### F1 · Foto y superficies por estancia (siguiente)

- [ ] Captura guiada de la foto de cada estancia, con aviso de encuadre y luz.
- [ ] Marcar suelo, paredes, techo y huecos protegidos con homografía.
- [ ] Compositor de capas (`MULTIPLY`) que pinte los cambios aprobados sobre la foto.

### F2 · Enseñar la obra

- [ ] Versiones A/B lado a lado en la app.
- [ ] Luces dibujadas (focos, apliques, tiras) sobre la composición.
- [ ] Recorrido exportable a lámina, GIF y PDF por estancia.

### F3 · Puente con el presupuesto

- [ ] Botón «llevar los cambios aprobados al presupuesto» creando líneas con cantidad y precio.
- [ ] Capítulos por estancia y IVA en el presupuesto, no en el proyecto.

### F4 · IA por paquete

- [ ] Mandar al Worker la foto + los cambios aprobados de la estancia (no un prompt libre).
- [ ] Corrección por fallos sobre esa misma estancia y su paquete.

---

## Estado

| Hito | Estado |
|---|---|
| H0 | **Hecho** — `apk/obra-0.1.0.apk`, firmado y verificado |
| H1 | **En curso** — medición AR con mediana, corrección y aviso de medidas imposibles (`apk/obra-0.4.0.apk`); faltan escaneo de sala, foto y dictado |
| H2 | **Base hecha** — editor con rejilla, imán, cotas y guardado automático |
| H3 | **Siguiente (versión mínima)** — marcar muros tirados/nuevos y abrir huecos |
| H4 | **Siguiente** — presentación visual: antes/después, lámina y vídeo |
| H5 | **Parcial** — ficha en PDF lista; presupuesto con precios pendiente |
| H6 | **En curso** — F0 (Worker) y F1 (pantalla de retoque) hechos; F2–F4 pendientes |
| H7 | **En curso** — F0 hecho (modelo, catálogo, aprobación, recorrido, pantalla y demo web); F1–F4 pendientes |

**Siguiente acción inmediata:** probar la pantalla «Proyecto» en el móvil del padre con una obra
real de dos estancias, y validar la medición AR con un láser (5 estancias). Después, F1: foto
guiada y compositor de capas, que es lo que convierte la lista de cambios en una imagen creíble.
