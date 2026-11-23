# Plan de producto — "Obra" (nombre provisional)

> **Decisión de producto (23-11-2026):** la app es 100 % gratuita. Se han retirado la IA
> remota (FLUX), el recorte de fondo y su Worker porque podían generar coste. Este
> documento se conserva como histórico de ideas descartadas.
**App Android para escanear estancias, obtener un plano medido y planificar la reforma**
Usuario principal: albañil autónomo, poca familiaridad con la tecnología. Requisito duro: **coste 0 €, para siempre**.

Documento de planificación. **Versión 2.0 — Android — 19 de noviembre de 2026.**
Sustituye a la v1 (planteada como PWA multi-dispositivo). Lo que cambia y por qué está en §1.

---

## 1. Qué cambia al replantearlo para Android

La v1 asumía PWA porque tenía que servir también en iPhone. Al fijar Android como único destino, la restricción desaparece y se abre la puerta que estaba cerrada: **ARCore nativo**, que es lo que hace posible el escaneo de una habitación de verdad, gratis.

### 1.1 Decisión: app Android nativa, no PWA

| | App nativa Android (recomendada) | PWA en Chrome Android |
|---|---|---|
| Escaneo de sala | **ARCore completo**, con profundidad, muros y huecos | Solo marcar esquinas a mano sobre el plano detectado |
| Precisión esperada | ±1–3 % | ±2–4 % |
| Coste de infraestructura | **0 €** (no hay servidor ni dominio: el APK va en el móvil) | 0 € pero depende de un hosting gratuito |
| Instalación | Se la instalo yo una vez (APK por WhatsApp) | Enlace + "Añadir a pantalla de inicio" |
| Actualizaciones | Le mando el APK nuevo; 30 segundos | Automáticas |
| Funciona sin cobertura | Sí, siempre | Sí, tras la primera carga |
| Trabajo de desarrollo | Más (Kotlin) | Menos, pero reimplementando lo que ARCore ya da |
| Publicación en Play Store | **No hace falta** (evita los 25 $ de alta) | No aplica |

**Recomendación: nativa.** El motivo no es técnico, es de producto: con ARCore el gesto de medir pasa de "toca cuatro esquinas y reza" a "da una vuelta a la habitación y ya está". Para alguien que no lee manuales, esa diferencia decide si la app se usa o se abandona. Y como no hay backend, el "0 € para siempre" deja de depender de ninguna capa gratuita.

> Si en algún momento quisieras que también funcione en iPhone sin instalar nada, la v1 (PWA) sigue siendo válida y el modelo de datos de este documento sirve igual. Pero no se construyen las dos.

### 1.2 Lo que se desbloquea

1. **Escaneo de sala real.** SceneView (Apache-2.0, versión 4.69 de noviembre de 2026) ya trae escaneo de espacios con **muros y huecos** (puertas/ventanas) y consultas del tipo "¿qué muro tengo más cerca y a qué distancia?". Eso es el 70 % de M2 hecho.
2. **Profundidad.** El 94 % de los móviles Android activos soportan la Depth API de ARCore: sirve para detectar mejor los muros y para que la medición no se vaya cuando hay poca textura.
3. **Todo local.** Base de datos en el móvil, PDF generado en el móvil, ni una petición obligatoria. Sin cuenta, sin registro, sin servidor que se caiga.
4. **Cero cuotas.** No hay Play Store (25 $ de alta, único pago que rompería el requisito), no hay hosting, no hay dominio, no hay suscripción.

### 1.3 Lo que cuesta

- El móvil tiene que ser **compatible con ARCore** (§2). Si no lo es, la app sigue funcionando con foto y medidas a mano, pero se pierde lo mejor.
- Las actualizaciones no son automáticas: hay que mandarle el APK.
- Es una app solo-Android: si algún día cambia de móvil a iPhone, hay que replantear.

---

## 2. Comprobar el móvil de tu padre (2 minutos, antes de escribir código)

Es el único bloqueante real del proyecto. ARCore exige que el móvil **haya salido de fábrica con Google Play Store** y tenga instalado *Google Play Services for AR* (gratis).

1. En el móvil: **Ajustes → Aplicaciones → Ver todas → buscar "AR"**. Si aparece *Google Play Services for AR* / *Servicios de Google Play para RA*, está instalado.
2. Si no aparece: abrir Play Store → buscar **"Google Play Services for AR"** → Instalar. Si Play Store no existe (Huawei sin servicios de Google, o algunas ROM chinas), **ARCore no va a funcionar**.
3. Prueba de 60 segundos: instalar cualquier app gratis de medir en AR y ver si detecta el suelo. Si detecta, la nuestra también.
4. Mínimo recomendado: **Android 9 o superior, 4 GB de RAM**. (ARCore acepta desde Android 7, pero con menos de eso la experiencia se arrastra.)

En la propia app, al arrancar, se comprueba con `ArCoreApk.checkAvailability()` y **si no hay AR, el botón de escanear no aparece**: se muestran foto y medidas a mano, sin mensajes técnicos.

---

## 3. Usuario y principios de diseño

### Perfil
- Trabaja de pie, con polvo, guantes, a pleno sol, muchas veces **sin cobertura** en un piso en obra.
- Usa el móvil sobre todo para: WhatsApp, fotos, calculadora, notas de voz.
- Aprende tocando, no leyendo manuales. Abandona una app si en 60 segundos no entiende qué tocar.
- Descubre la IA: le atrae, pero desconfía de "inventos". Necesita ver que el número que le da la app cuadra con el que mide él.

### Principios (no negociables)
1. **Un botón por pantalla.** La acción principal siempre abajo, ancha, con texto, nunca solo un icono.
2. **Todo se puede corregir a mano.** Ningún número calculado es definitivo: se toca y se edita.
3. **Nunca bloquear por falta de internet o de cuenta.** Nada de "regístrate para continuar".
4. **Vocabulario de obra, no de app.** "Tirar tabique", no "eliminar entidad". "Hueco de puerta", no "opening".
5. **Se lee al sol**: contraste alto, tipografía ≥ 18 sp, botones ≥ 56 dp de alto (guantes).
6. **Nada se pierde**: guardado automático tras cada cambio, sin botón "guardar".
7. **La IA es opcional y se puede apagar.** Si falla o se agota el cupo, la app sigue entera.

---

## 4. Piezas open source que se reutilizan

Verificado el 19-nov-2026. La columna "riesgo" es lo que pasa si el proyecto desaparece: por eso ninguna pieza es la única vía de nada.

| Pieza | Licencia | Qué aporta | Cómo se usa | Riesgo si muere |
|---|---|---|---|---|
| **SceneView** (sceneview/sceneview, v4.69.6) | Apache-2.0 | ARCore + render 3D con Filament para Compose; demo de medición; **escaneo de sala con muros y huecos**; consulta de muro/hueco más cercano | SDK base. Es el sustituto mantenido de Sceneform (Google lo archivó) | Bajo: ARCore sigue siendo de Google; se podría sustituir por Sceneform Maintained o ARCore directo |
| **ReMeasure** (Qu4X/ReMeasure) | Apache-2.0 | App de medida AR completa en Compose que recrea la difunta Google Measure: distancias, alturas, áreas | Referencia de UX y de código para la pantalla de medir | Nulo (es una referencia, se copia la idea) |
| **AR-Measure-App** (mmd-nemati) | sin licencia declarada → **no copiar código**, solo ideas | Buen patrón: retícula central, hit-test sobre plano y *fallback* a Depth API | Referencia de interacción | Nulo |
| **openPlan3D** (laanlabs) | **MIT** | Editor 2D/3D de planos en web (SvelteKit + Three.js), local-first, JSON, muros que se unen, huecos, habitaciones con área, 3D navegable | Referencia de modelo de datos y de comportamiento del editor; si algún día se hace la PWA, se parte de aquí | Nulo |
| **OpenCalque** (laurent512) | **MIT** | Editor arquitectónico 2D con esquema JSON publicado, muros que se juntan solos, áreas, alzados | Referencia del esquema JSON y de las operaciones de edición | Nulo |
| **Sweet Home 3D** | GPL-2+ | Programa de escritorio gratis para dibujar y amueblar la casa, con 3D y PDF | **Camino B sin programar** (§4.2) | Nulo |
| **LibreCAD / FreeCAD** | GPL-2 / LGPL-2+ | CAD 2D/3D que abre y guarda DXF | Solo si algún día hay que entregar un DXF al técnico | Nulo |
| **ARCore SDK** | Propietario, gratis | Seguimiento, planos, profundidad, anclajes | Base de todo el AR | No aplica (es de Google) |
| **Android Jetpack / Room / Compose** | Apache-2.0 | UI, base de datos, ciclo de vida | Base de la app | No aplica |

> Nota de licencias: MIT y Apache-2.0 se pueden usar sin condiciones raras. **GPL** (Sweet Home 3D, LibreCAD) obliga a publicar el código si distribuyes una versión modificada; para uso propio no afecta. Las piezas marcadas GPL se usan como **herramientas externas**, nunca enlazadas dentro del APK.

### 4.1 Lo que se descarta (y por qué)

| Opción | Por qué no |
|---|---|
| **Apple RoomPlan** | Framework Swift, iOS 16+ con LiDAR. Inaccesible desde Android. (Era la razón de peso de la v1 para justificar la PWA.) |
| **RoomFormer** (MIT, CVPR 2023) | Reconstruye plantas a partir de escaneos 3D, pero necesita GPU y Detectron2 en servidor. ARCore ya da la geometría en el móvil. |
| **phone-video-3d-recon / Depth Anything 3** | Convierte un vídeo en nube de puntos y DXF, muy interesante… pero pide un PC con GPU de 8 GB. Fuera del móvil. Guardado como experimento de escritorio, no como producto. |
| **CubiCasa5K** | Dataset CC BY-NC (solo investigación, no comercial) y pensado para plantas escaneadas, no para habitaciones. |
| **Escaneo LiDAR** | No existe en Android de forma generalizada. |
| **Render fotorrealista con IA** | La generación de imágenes no está en el cupo gratuito de Gemini. Ver §5.5 y el sustituto en §5.1 (FLUX schnell en Cloudflare, imágenes decorativas). |

### 4.2 Camino B: no programar nada

Si el objetivo fuera "que lo tenga esta semana" y no "que sea su app", existe una vía con 0 € y 0 líneas de código: **Sweet Home 3D en un portátil** para dibujar el plano definitivo (muros, huecos, muebles, 3D y PDF), y en el móvil solo una libreta de medidas. Funciona, es gratis y es de código abierto, pero son dos herramientas separadas, no hay presupuesto integrado y no mide en AR. Se documenta como plan de emergencia, no como producto.

---

## 5. IA con cupo gratuito: lo que hay de verdad

La pregunta era si se puede usar IA sin pagar, aprovechando que el uso será bajo. **Sí, y con margen de sobra.** Estos son los cupos reales comprobados hoy (cambian; hay que reverificarlos antes de fiarse):

| Proveedor | Cupo gratuito | Modelos útiles | ¿Tarjeta? | Encaje en la app |
|---|---|---|---|---|
| **Google Gemini API** | Flash-Lite (3.5/3.1): **15 peticiones/min, 500/día**. Flash 3.8: 5/min, **20/día**. Gemma 4: 30/min, **14.400/día** | Visión + texto. Entiende fotos de planos y de habitaciones | **No** | **Principal**: leer una foto de plano, redactar el presupuesto, explicar la reforma |
| **Cloudflare Workers AI** | **10.000 neuronas/día** (≈ 367.000 tokens de salida con Gemma 4 26B, ≈ 173 imágenes FLUX.1 schnell, ≈ 4 h de transcripción Whisper) | Gemma 4 26B, Llama 3.3 70B, Llama 4 Scout (visión), FLUX.1 schnell, Whisper | **No** | **Respaldo** y generador de imágenes de estilo. También aloja el proxy |
| **Groq** | 30 peticiones/min, **1.000/día**, 200.000 tokens/día por modelo; Whisper 2.000/día | gpt-oss-120b, gpt-oss-20b, Qwen3.8 27B; Whisper para dictado | **No** | **Segundo respaldo** de texto (va muy rápido) |
| **OpenRouter** | 20/min, **50/día** (1.000/día solo si compras 10 créditos → deja de ser gratis) | Varios `:free`, algunos con visión (Nemotron 3.5 Super VL, Gemma 4) | No | Último recurso. 50/día llega para probar, no para depender |
| **Mistral** | "Free mode" con topes no publicados; la documentación reciente dice que la API pide activar facturación | — | No | Descartado como pilar |
| **Voz del sistema (Android)** | Ilimitado, en el móvil | `SpeechRecognizer` con paquete de español offline | No | **Dictado**: no gasta cupo de nadie |

### 5.1 Arquitectura de la IA: proxy propio, nunca la clave en el APK

Una clave metida en un APK se extrae en cinco minutos. Y tu padre no va a crear una cuenta de Google Cloud. Solución, gratis y sencilla:

```
Móvil (Obra)  ──HTTPS──►  Cloudflare Worker (mío, plan gratis)  ──►  Gemini API
   sin claves                guarda la clave como secreto            (cupo gratis)
                             limita por dispositivo y por día
                             cachea respuestas idénticas
```

- La clave vive **solo** en el Worker, como secreto. En el teléfono no hay nada que robar.
- El Worker lleva un **token por instalación** (se genera al instalar) y un tope diario por dispositivo. Si alguien encuentra la URL, no puede quemar el cupo.
- Si Gemini devuelve 429 (cupo agotado), el Worker prueba **Cloudflare Workers AI** y luego **Groq**. Si los tres fallan, responde "hoy no hay IA" y la app sigue funcionando.
- Coste: Worker gratuito (100.000 peticiones/día) + cupos gratuitos de los tres proveedores. **0 €.**
- Cada respuesta se cachea en el móvil: la misma obra no se analiza dos veces.

### 5.2 Presupuesto de uso (por qué "se usará poco" es la clave)

Llamadas de IA por obra, siendo generosos:

| Momento | Llamadas |
|---|---|
| Leer una foto de plano e interpretar estancias | 1 visión |
| Redactar el presupuesto en lenguaje de cliente | 1 texto |
| "Explícame esta reforma paso a paso" | 1 texto |
| Resumen para el informe final | 1 texto |

**≈ 4 llamadas por obra.** Con Flash-Lite (500/día) salen ~125 obras al día; el límite real es 1 cada 4 segundos, irrelevante para una persona. Uso realista: 3–5 obras por semana → **menos de 1 llamada al día**. Hay un factor 100 de margen, así que el cupo nunca será el problema.

Y si un día se acabara: se muestra un aviso discreto ("IA no disponible hoy, todo lo demás sigue funcionando") y él sigue midiendo y presupuestando igual.

### 5.3 Privacidad: lo que hay que decirle claro

- En la **capa gratuita de Gemini, Google puede usar lo que envías para mejorar sus productos**. Por eso: **las fotos se envían solo si él pulsa "analizar con IA"**, se avisa en una línea, y se recomienda no fotografiar personas ni documentos.
- El Worker **no guarda** las imágenes: las pasa y las olvida.
- Todo lo demás (medidas, planos, fotos, presupuestos) **no sale del móvil** salvo que él lo comparta por WhatsApp.
- Se puede apagar la IA del todo en Ajustes: la app queda 100 % offline y sin ninguna petición.

### 5.4 Qué hace la IA y qué no

**Sí (opcional, siempre como sugerencia editable):**
- Leer la foto de un plano o de un croquis y proponer dónde están los muros y las estancias → se dibuja como **borrador editable**, no como verdad.
- Pasar el resumen de obra a lenguaje de cliente: "se tirará el tabique entre salón y cocina…".
- Estimar un orden de trabajo razonable ("primero demolición, luego fontanería…").
- Dictado de notas (con el motor del propio Android, sin cupo).
- **Imágenes de estilo** con FLUX schnell: "así podría quedar una cocina con estos colores". Son orientativas y decorativas, nunca "tu salón exacto".

**No:**
- No mide. La medida sale de ARCore, de la foto o de lo que escriba él.
- No decide si un muro es de carga. Eso es criterio suyo + técnico.
- No da precios de mercado.
- No sustituye un plano con validez legal.

---

## 6. Alcance funcional

Prioridad MoSCoW: **M** = imprescindible v1, **S** = debería, **C** = podría, **W** = no se hará.

### M1. Obras y clientes — M
- Lista de obras con nombre, dirección, cliente, teléfono, foto de portada y fecha.
- Buscar por nombre/dirección. Duplicar obra (para presupuestar variantes).
- Estados: presupuesto / en curso / terminada.
- **No** hace falta CRM ni facturación.

### M2. Captura y medida — M
Tres caminos en una pantalla con tres botones grandes (el de AR solo si el móvil lo soporta):

**A) Escanear la habitación (ARCore)**
- "Da una vuelta a la habitación apuntando al suelo y a las paredes" → barrido de 30–60 s con barra de progreso y aviso de "ya vale" cuando la geometría está cerrada.
- Al terminar: planta 2D con muros, huecos detectados y superficie. Todo **editable**.
- Apoyo opcional con Depth API para muros con poca textura (pared lisa recién pintada).
- Si el seguimiento se pierde: se conserva lo escaneado y se puede continuar.
- **Medir a mano en AR** (modo simple, por si el escaneo falla): retícula central, tocar esquina A y B, distancia en metros. Igual que la difunta Google Measure.

**B) Medir con foto + referencia (siempre disponible)**
1. Foto del suelo desde una esquina, con las cuatro esquinas visibles.
2. Toca las 4 esquinas.
3. Indica una medida conocida: ancho de puerta (80 cm por defecto), largo de baldosa (60 cm) o lo que haya medido con el láser.
4. La app rectifica la perspectiva (homografía) y da ancho, largo, superficie y perímetro con su ±.

**C) Escribir/dictar**
- Formulario mínimo: largo, ancho, alto. Teclado numérico grande; coma y punto aceptados.
- **Dictado** con el reconocimiento de voz de Android (funciona offline si el paquete de español está descargado): "tres metros cuarenta por dos ochenta".
- Láser Bluetooth: **W** (protocolos privados por marca, frágil y no gratis de mantener).

**Siempre**: añadir más estancias (baño, pasillo) y unirlas en una obra. Altura libre por defecto 2,50 m, editable.

**Plano de obra (foto o PDF)**
- Importar la foto de un plano → fondo del lienzo → marcar una cota conocida → la app escala el plano → **calcar** los muros encima.
- PDF: se rasteriza la primera página en el móvil (sin subir nada). OCR de cotas **C**, y siempre como sugerencia a confirmar.

### M3. Editor de plano 2D — M
- Lienzo con rejilla, zoom con dos dedos, desplazamiento con un dedo.
- Herramientas: muro (línea con grosor), **snap** a esquinas y a 0°/45°/90°, borrar, deshacer ≥ 20 pasos.
- Huecos sobre un muro: puerta (0,80 × 2,05), ventana (libre), paso. Se arrastran y se editan.
- Etiquetas de largo de cada muro y superficie de la estancia, siempre visibles.
- Vista "planta con cotas", exportable como imagen.

### M4. Editor de reforma (el "después") — M
Se trabaja sobre una **copia**; el antes queda intacto para comparar.
- **Tirar muro** → rojo discontinuo, desaparece del "después".
- **Muro nuevo** → verde, con su grosor y sistema (tabique de yeso, ladrillo).
- **Abrir hueco** en muro existente → descuenta pared y suma dintel.
- **Mover/cambiar** puertas, ventanas, sanitarios, radiadores.
- **Zonas**: suelo, paredes y techo por zonas, cada una con su material.
- Cambios no constructivos con **biblioteca de bloques** simple: encimera, fregadero, placa, inodoro, ducha, cama, armario.
- Resumen en texto claro: *"Se tiran 2 tabiques (6,4 m), se levantan 3,1 m de tabique nuevo, 2 huecos nuevos, suelo nuevo 32 m²"*.

### M5. Ver el antes y el después — M (simple) / S (materiales)
- 3D **generado de la geometría**, no inventado: muros extruidos a la altura real, huecos recortados, suelo y techo.
- Colores de pintura con carta real (RAL/NCS habituales) y muestras de azulejo/tarima.
- Girar, zoom y **comparador antes/después** (cortinilla o dos pestañas).
- Foto real como textura de pared (opcional): barato de hacer y con mucho efecto.
- **Extra C**: moodboard con FLUX schnell dentro del cupo de Cloudflare (~173 imágenes/día). Etiquetado siempre como "idea de estilo, no es tu casa".
- **W**: render fotorrealista del salón exacto con IA. No está en ningún cupo gratuito y no aporta precisión.

### M6. Cómputo de materiales — M
Con **rendimientos editables** guardados en ajustes:

| Material | Fórmula base | Salida |
|---|---|---|
| Pintura | (perímetro × alto − huecos) × nº manos ÷ rendimiento (m²/L) | litros + botes de 4/15 L |
| Azulejo | m² × 1,10 (merma) ÷ m²/caja | cajas |
| Tabique nuevo | largo × alto | m² + sacos/placas según sistema |
| Falso techo / yeso | m² | m² + kg de pasta |
| Solado | m² × 1,08 | m² + cajas |
| Escombro de demolición | m² de muro tirado × factor | m³ / sacos |

Cada línea es editable y se puede desactivar. El objetivo es **que ninguna cuenta le sorprenda**, no sustituir su criterio.

### M7. Presupuesto y PDF — M
- Partidas: descripción, cantidad, unidad (m², ml, ud), precio unitario, total.
- Precios propios guardados (catálogo local reutilizable). La app **no** trae precios de mercado: cambian por provincia y serían datos falsos.
- Mano de obra como partida aparte, con horas estimadas opcionales. IVA y descuento opcionales.
- **PDF generado en el móvil** con el generador nativo de Android (sin librerías de pago): presupuesto A4 + "ficha de obra" con plano acotado y vista del después.
- Compartir: menú del sistema → WhatsApp. Sin servidor.

### M8. Diario de obra — S
- Fotos con fecha, notas de voz transcritas, incidencias.
- Fotos "antes"/"después" emparejadas para el informe final.

### M9. Datos y copias — M
- Todo local (Room/SQLite). Exportar/importar **un archivo `.obra`** (JSON + imágenes) para copia en el móvil, Drive o WhatsApp.
- Copia automática semanal a una carpeta del propio móvil.
- Modo "solo lectura" para enseñar al cliente sin que toque nada.
- Sincronización en nube: **W**. Con una sola persona y export/import no hace falta, y es lo que mantiene el coste en 0 €.

### Fuera de alcance (W)
Facturación/VeriFactu, personal y nóminas, compras a proveedores, mediciones certificadas, planos con validez legal, Play Store, versión iOS.

---

## 7. Requisitos funcionales

| ID | Requisito | Prioridad | Criterio de aceptación |
|---|---|---|---|
| RF-01 | Crear obra y estancia en ≤ 3 toques | M | De abrir la app al lienzo: ≤ 15 s |
| RF-02 | Escanear una habitación con ARCore | M | Muros y superficie en ≤ 90 s, editable después |
| RF-03 | Medir una distancia en AR (modo simple) | M | Error ≤ 4 % frente a láser en 3 estancias |
| RF-04 | Medir con foto + referencia | M | Con 80 cm reales, error ≤ 6 % |
| RF-05 | Introducir medidas a mano/dictando | M | Se puede hacer una obra entera sin cámara |
| RF-06 | Editar plano con snap y deshacer | M | ≥ 20 pasos de deshacer |
| RF-07 | Añadir/tirar muros y abrir huecos | M | El resumen refleja los metros exactos |
| RF-08 | Ver 3D antes/después con colores | M | Cambiar un color se ve en < 300 ms |
| RF-09 | Cómputo de materiales editable | M | Pintura y azulejo con rendimientos ajustables |
| RF-10 | Presupuesto con precios propios y PDF | M | PDF A4 compartible por WhatsApp |
| RF-11 | Funcionar 100 % sin conexión | M | Modo avión: escanear*, editar, 3D, PDF (*el escaneo es local) |
| RF-12 | Detectar si el móvil soporta AR | M | Sin AR: el botón no aparece y no hay errores técnicos |
| RF-13 | Importar foto de plano y escalarlo | S | Error de escala ≤ 3 % sobre cota conocida |
| RF-14 | IA opcional con respaldo y aviso de cupo | S | Si falla, la app sigue igual; nunca bloquea |
| RF-15 | Diario de fotos y notas dictadas | S | Nota dictada guardada como texto editable |
| RF-16 | Export/import `.obra` | M | Restaurar en otro móvil y ver todo igual |

---

## 8. Requisitos no funcionales

- **Offline-first real**: ninguna función crítica hace red. La IA es la única excepción, y es opcional.
- **Rendimiento**: arranque < 2 s en un Android de gama media (2022); escaneo a ≥ 25 fps; 3D ≥ 30 fps con ≤ 40 muros.
- **Tamaño**: APK < 30 MB (SceneView/Filament y ARCore pesan; no hay módulos que descargar).
- **Permisos**: cámara y micrófono solo al usarlos, con una línea de explicación llana. Sin ubicación, sin contactos, sin almacenamiento global (scoped storage).
- **Accesibilidad**: contraste AA, toques ≥ 56 dp, sin gestos ocultos, texto escalable, tema claro forzado (sol directo), funciona con guantes.
- **Privacidad**: sin cuentas, sin analítica, sin publicidad, sin rastreo. Nada sale del móvil salvo lo que él comparta.
- **Tolerancia a fallos**: autoguardado; si el AR se corta, no se pierde lo medido.
- **Idioma**: español de España, sin tecnicismos.
- **Instalación**: APK firmado con keystore local, instalado por ti una vez; actualización enviando el APK nuevo.

---

## 9. Pantallas

```
Inicio ─┬─ [ + Nueva obra ]
        ├─ Obra ─┬─ Resumen (medidas, materiales, coste)
        │        ├─ [Medir] → Escanear (AR) / Foto / A mano
        │        ├─ Plano 2D ──► Reforma (tirar / levantar / huecos)
        │        ├─ Ver 3D (Antes | Después | Comparar)
        │        ├─ Materiales y colores
        │        ├─ Presupuesto ──► PDF / Compartir
        │        └─ Diario (fotos y notas)
        └─ Ajustes (precios, rendimientos, IA on/off, copia, idioma)
```

Regla: **nunca más de 3 toques** desde una obra a cualquier acción principal. Atrás siempre arriba a la izquierda.

**Flujo de 3 minutos (el que hay que proteger):** abre app → "Nueva obra" → "Medir" → "Escanear" → 45 s de barrido → aparece la planta → toca el tabique → "Tirar" → "Presupuesto" → "PDF" → compartir por WhatsApp.

---

## 10. Arquitectura técnica

### App (todo en el móvil)
| Pieza | Elección | Coste | Por qué |
|---|---|---|---|
| Lenguaje | Kotlin 2.x | 0 € | Estándar Android actual |
| UI | Jetpack Compose + Material 3 | 0 € | Botones grandes y temas consistentes |
| AR / 3D | **SceneView 4.69** (ARCore + Filament) | 0 € | Escaneo de sala, medición y render en una sola dependencia |
| AR base | ARCore SDK + Play Services for AR | 0 € | Seguimiento, planos, profundidad |
| Datos | Room (SQLite) | 0 € | Transaccional, exportable, offline |
| Fotos | Coil + compresión propia | 0 € | Miniaturas rápidas, poco peso |
| Editor 2D | Compose Canvas propio | 0 € | Control total de snap y cotas |
| PDF | `android.graphics.pdf.PdfDocument` | 0 € | Nativo, sin licencias |
| Voz | `SpeechRecognizer` del sistema | 0 € | Sin clave ni cupo |
| Export | `kotlinx.serialization` + ZIP | 0 € | Formato `.obra` abierto |
| Iconos/fuentes | Material Symbols + Inter (OFL) | 0 € | Licencias libres |
| Distribución | APK firmado, enviado por WhatsApp / enlace | 0 € | Sin Play Store (25 $) |

### Backend (opcional, y solo para IA)
Un único **Cloudflare Worker** (plan gratis) que hace de portero de las claves y de los cupos (§5.1). No guarda datos de obra. Si se cae, la app funciona igual salvo los botones de IA.

### Reglas de la capa gratuita que hay que respetar en el código
- Fotos comprimidas a 1600 px antes de enviarlas a la IA (≈ 250 KB): menos cuota y más rápido.
- Cachear toda respuesta de IA por hash del contenido: la misma foto no se analiza dos veces.
- Tope diario por dispositivo en el Worker (p. ej. 20 llamadas/día, muy por encima del uso real).
- Reintento escalonado Gemini → Workers AI → Groq, y solo entonces "no disponible".
- Nunca reintentar en bucle: un 429 no se arregla insistiendo.

---

## 11. Modelo de datos

```
Obra        id, nombre, direccion, cliente, telefono, estado, fecha, notas
Estancia    id, obra_id, nombre, altura, tipo_suelo, tipo_techo, plano_fondo
Escaneo     id, estancia_id, metodo(arcore|foto|manual), fecha, calidad(0..1), origen(tracking_id)
Punto       id, estancia_id, x, y            // metros, origen en esquina
Muro        id, estancia_id, x1,y1, x2,y2, grosor, altura, tipo, material_id, estado(original|nuevo|tirado), carga(bool)
Hueco       id, muro_id, posicion(0..1), ancho, alto, alfeizar, tipo(puerta|ventana|paso)
Zona        id, estancia_id, tipo(suelo|pared|techo), material_id, poligono
Material    id, nombre, tipo, color_hex, rendimiento, unidad, merma, precio_ref
Partida     id, obra_id, descripcion, cantidad, unidad, precio_unit, tipo(material|mano_obra)
Precio      id, descripcion, unidad, precio          // catálogo propio reutilizable
Foto        id, obra_id, estancia_id, fecha, ruta, nota, etiqueta(antes|durante|despues)
AnalisisIA  id, hash_contenido, proveedor, modelo, respuesta_json, fecha   // caché
```

Reglas: geometría **siempre en metros**; cada muro pertenece a una estancia; el "antes" es inmutable y la reforma se guarda como estado del muro + huecos nuevos. Con eso, resumen y 3D se recalculan solos. El `Escaneo` guarda la calidad para poder decir "esta medida es floja, repítela".

---

## 12. Los cuatro cálculos que hay que hacer bien

**1) Del escaneo ARCore al plano**
El escaneo devuelve muros como segmentos en el espacio (con su plano y normal) y huecos con posición. Se proyecta todo al plano del suelo, se **enderezan** los ángulos (snap a 0/90° con tolerancia ±3°) y se cierra el polígono de cada estancia. Sin este enderezado, el plano sale "torcido" y parece mal hecho aunque las medidas sean buenas. Se conserva la medida original por si él prefiere la real.

**2) Rectificación con 4 puntos (medir por foto)**
4 esquinas del suelo + una medida real → homografía píxeles→metros → área por Gauss y perímetro por suma de lados. Si solo da una longitud, se asume rectángulo y se avisa. Se muestra siempre el **±** ("≈ 12,4 m² ± 0,5") y nunca más de un decimal. Objetivo: presupuestar, no certificar.

**3) Superficie de paredes y huecos**
`área_pared = perímetro × altura − Σ(ancho × alto de huecos)`. Con muros tirados y nuevos el cómputo se hace **por muro**, no por perímetro, para que el "después" sea exacto.

**4) Del plano 2D al 3D**
Cada muro es un segmento con grosor → prisma de altura `altura`; los huecos se restan como cajas; suelo y techo se triangulan. Materiales por cara. Geometría directa, sin mallas pesadas: va sobrado en un móvil modesto.

---

## 13. Plan por fases

| Fase | Contenido | Esfuerzo | Terminada cuando… |
|---|---|---|---|
| **F0 · Esqueleto** | Proyecto Android, navegación, Room, tema "sol/guantes", export `.obra` | 2 días | Instalada en su móvil, guarda una obra tras cerrar la app |
| **F1 · Escanear y medir** | SceneView: escaneo de sala, medición simple en AR, foto+referencia, manual/dictado | 6–8 días | 5 estancias medidas y comparadas con láser (media < 5 %, ninguna > 10 %) |
| **F2 · Plano** | Editor 2D, snap, huecos, cotas, export imagen | 4–5 días | Reproduce el plano de una casa real en < 10 min |
| **F3 · Reforma** | Tirar/levantar/huecos + resumen en texto | 3–4 días | El resumen coincide con lo dibujado, al metro |
| **F4 · Ver** | 3D antes/después, colores y materiales | 4–6 días | Cambiar un color se ve al instante y el 3D cuadra con el plano |
| **F5 · Dinero** | Materiales, partidas, precios propios, PDF, compartir | 4–5 días | Presupuesto real enviado por WhatsApp |
| **F6 · IA y robustez** | Worker proxy, análisis de plano, redacción, diario, copias, accesibilidad | 3–4 días | Modo avión una jornada entera sin errores; IA caída y app intacta |
| **v1.0** | Pulido, onboarding de 3 pantallas, manual de un folio | 2 días | La usa una semana sin pedirte ayuda |

Total realista: **6–7 semanas** de trabajo efectivo (algo más que la v1: el escaneo ARCore se cobra su tiempo). Pero cada fase ya le sirve sola.

**MVP que ya le resuelve algo (2–3 semanas):** F0 + F1 (escanear y medir) + F2 + materiales y un PDF simple. Sin 3D y sin IA. Que lo pruebe en obra real antes de invertir en el resto. **La pregunta que responde ese MVP es la única que importa: ¿mide lo que él necesita, de forma que quiera usarlo?**

---

## 14. Validación con el usuario real

1. **Prueba de 60 segundos**: darle el móvil sin explicar nada. Si no llega a medir una habitación, la pantalla está mal.
2. **Prueba del láser**: 5 estancias medidas por él con láser y por la app. Aceptable si la desviación media < 5 % y ninguna > 10 %.
3. **Prueba del presupuesto**: comparar con un presupuesto suyo real. Aceptable si la diferencia está solo en los precios que él introduce.
4. **Prueba del andamio**: de pie, con guantes y sol directo, 10 minutos seguidos.
5. **Prueba del móvil malo**: forzar un móvil sin ARCore y comprobar que la app no parece rota.
6. Nada de tests automáticos de UI al principio: el criterio es que él la use.

---

## 15. Riesgos y mitigación

| # | Riesgo | Impacto | Mitigación |
|---|---|---|---|
| R1 | **Su móvil no soporta ARCore** (Huawei sin servicios de Google, ROM china, gama muy baja) | Alto | Comprobación de §2 **antes de empezar**; si falla, la app sigue con foto y manual (el producto se sostiene, pero pierde lo mejor) |
| R2 | El escaneo sale torcido o con muros inventados | Alto (pierde confianza) | Enderezado de ángulos, edición manual siempre disponible, indicador de calidad del escaneo, y nunca decir "escaneo 3D profesional" |
| R3 | SceneView cambia o rompe su API de escaneo de sala | Medio | Fijar la versión en Gradle; tener como plan B Sceneform Maintained o ARCore directo; la capa de escaneo va aislada en un módulo |
| R4 | Se espera "lo mismo que el anuncio de Apple" | Alto (decepción) | Onboarding de 3 pantallas que muestra lo que hace y lo que no; llamarlo "medir", no "escanear en 3D" |
| R5 | Cupo de IA agotado o proveedor caído | Bajo | Tres proveedores en cascada, caché, tope por dispositivo y aviso claro; el camino crítico no usa IA |
| R6 | Clave de IA filtrada | Medio | Nunca en el APK: solo en el Worker; token por instalación y tope diario |
| R7 | Crecimiento de fotos en el móvil | Bajo | Compresión, límite por obra, exportar y archivar |
| R8 | Abandono por complejidad | Alto | Fases que sirven por sí solas; máximo 3 toques; ningún ajuste obligatorio |
| R9 | Actualizaciones manuales del APK | Bajo | Aviso dentro de la app ("hay versión nueva, pídemela") + APK por WhatsApp |

---

## 16. Qué NO promete la app (decirlo desde el principio)

- No sustituye a un **medidor certificado** ni sirve para licencias, catastro o certificados energéticos.
- El escaneo es **bueno, no perfecto**: ±1–3 % en el mejor caso, y puede pedir repasar una pared. Se dice siempre.
- No genera **renders fotorrealistas de su casa con IA**: muestra un 3D fiel en medidas y colores, no una foto publicitaria. Las imágenes de estilo son orientativas.
- No trae **precios de mercado**: los pone él.
- No calcula **estructura**. Si marca un muro como de carga, la app avisa: *"muro de carga: consulta antes de tirarlo"*.
- No hay versión para iPhone.

---

## 17. Checklist "100 % gratis" (verificar antes de entregar)

- [ ] Cero APIs de pago en el código; ninguna clave dentro del APK.
- [ ] El camino crítico (escanear → plano → reforma → PDF) funciona en **modo avión**.
- [ ] La IA usa solo cupos gratuitos sin tarjeta (Gemini free / Workers AI / Groq) y se puede apagar.
- [ ] Sin Play Store, sin hosting, sin dominio, sin suscripción, sin "premium".
- [ ] Librerías con licencia permisiva (MIT/Apache/BSD/OFL); las GPL solo como herramientas externas.
- [ ] Fuentes e iconos libres; imágenes de ejemplo propias o de dominio público.
- [ ] Exportación `.obra` disponible para no quedar atado a nada.
- [ ] Coste de mantenimiento: **0 €/mes**, sin renovaciones que caduquen.
- [ ] Revisado en 6 meses: los cupos gratuitos cambian (este documento cita los de nov-2026).

---

## 18. Siguiente paso recomendado

1. **Hoy**: comprobar el móvil de tu padre (§2). Es un bloqueante y son 2 minutos.
2. **Si hay ARCore**: hacer F0 + F1 en una app mínima — escanear una habitación, ver la planta y el área, y comparar con su láser. Sin planos bonitos, sin 3D, sin IA.
3. **Si no hay ARCore**: replantear a foto + manual (sigue siendo un producto útil) y valorar cambiar de móvil antes de invertir semanas.
4. La IA llega en F6, cuando ya haya algo que medir. Si nunca se usa, el plan no se resiente: está fuera del camino crítico a propósito.
