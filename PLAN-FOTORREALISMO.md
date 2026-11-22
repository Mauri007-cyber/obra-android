# Fotorrealismo sobre la foto real + retoque con IA

Lo que has pedido son dos cosas distintas, y conviene no mezclarlas porque una es gratis y la otra no:

1. **Que el «después» sea la habitación de verdad**, con su ventana, su puerta y su luz.
2. **Que una IA interprete un prompt** y devuelva la foto cambiada, con un bucle para decirle qué ha hecho mal.

La 1 se puede hacer sin IA, sin internet y sin gastar un céntimo. La 2 necesita un servicio de pago por imagen.
La app puede tener las dos: la 1 siempre, la 2 apagada hasta que se quiera.

---

## 1. Camino A · compositor sobre la foto (0 €, sin internet, al instante)

Es lo que ya se ve en la vista previa (pantallas 1 y 2).

**Cómo funciona, de verdad:**

1. **Foto** con la cámara del móvil (CameraX). Se guarda tal cual, sin filtros.
2. **Superficies**: se tocan 4 esquinas por superficie (pared del fondo, pared izquierda, suelo, techo). Con esas 4 parejas de puntos se calcula una **homografía** y se endereza cada superficie en perspectiva. En Android no hace falta OpenCV: `android.graphics.Matrix.setPolyToPoly` + `BitmapShader` hacen el warp.
3. **Materiales**: se pinta el material elegido (pintura, tarima, gres, microcemento) con la homografía aplicada, de modo que las lamas de la tarima siguen la fuga de la perspectiva. Se mezcla con `PorterDuff.Mode.MULTIPLY` para **no perder la luz de la foto**: la mancha de sol de la pared sigue ahí.
4. **Huecos protegidos**: la ventana y la puerta se recortan de la capa de pintura y se vuelven a dibujar con los píxeles originales. Nunca se tiñen, nunca se mueven.
5. **Luz**: se estima la dirección del sol por el brillo de la foto (la zona más luminosa marca el lado de la ventana) y se añaden luces nuevas como sprites con halo y sombra suave. Cada luz guardada es un punto con posición sobre la superficie.
6. **Salida**: la misma foto, con la misma geometría, y el antes/después a un toque.

**Lo que esto sí hace:** cambiar acabados, colores, luz y puntos de luz sobre la habitación real, en menos de un segundo, sin conexión.
**Lo que no hace:** meter un sofá que no existe, tirar un tabique o cambiar la distribución. Eso es lo que hace la IA, y lo hace inventando.

**Límite honesto:** con una foto de móvil y 4 puntos, un buen compositor engaña al cliente a 1080 px, pero no es un render de estudio. Es más creíble que el 3D actual porque es *su* habitación.

---

## 2. Camino B · IA con prompt (coste real, decidido por ti)

### Lo que cuesta de verdad (consultado hoy)

| Servicio | Nivel gratis | Coste | Nota |
|---|---|---|---|
| Gemini 2.5 Flash Image («Nano Banana») | **No hay** | 0,039 $/imagen | El más barato de Google; se apaga en 2027 |
| Gemini 3.1 Flash Image (Nano Banana 2) | **No hay** | 0,067 $/imagen a 1K | El recomendado hoy por calidad |
| Cloudflare Workers AI (FLUX schnell) | **10.000 neuronas/día ≈ 230 imágenes/día** | 0 dentro del cupo | Necesita cuenta y un Worker que guarde la clave: no puede ir dentro del APK |
| Pollinations | Sin clave, ~1 imagen/40 s | 0 | Sin control fino ni garantías; para pruebas |
| ImagineArt | No documenta cupo | De pago | API REST con clave |

**Conclusión:** «100 % gratuita» y «fotorrealismo con IA» no caben en la misma frase. Lo que sí cabe: la app es gratis y completa sin IA, y el retoque con IA es un extra que se enciende con una clave propia y un límite diario. Con el cupo de Cloudflare (230 imágenes/día) un albañil no gasta nada en la práctica.

### Arquitectura si se enciende

```
Móvil → foto + prompt → Worker propio (guarda la clave) → modelo → imagen
                          ↑ límite diario y registro
```

La clave **nunca** va en el APK: cualquiera puede descomprimirlo. El Worker es la única pieza con secreto, y su plan gratuito cubre de sobra este uso. Si no hay Worker configurado, el botón de IA no aparece: no se rompe nada.

**Privacidad:** la foto de la casa de un cliente sale del móvil. Antes de enviarla, la app lo dice con todas las letras y pide permiso cada obra, no una vez para siempre. Y las imágenes no se guardan en el servidor más allá de la respuesta.

---

## 3. El bucle de prompt (lo que pedías, y cómo lo hace fácil)

El padre no debe escribir prompts. La app los compone:

1. **Él elige con el dedo**: acabados de las pantallas 1 y 2, luces colocadas, y frases cortas ya hechas («suelo de tarima clara», «paredes blancas», «cocina integrada»).
2. **La app escribe el prompt** juntando: la orden, la geometría que no se toca, los acabados, las luces y las correcciones anteriores. Se puede **ver el texto exacto** que se envía.
3. **Tres propuestas** por ronda (mismo prompt, tres semillas). Toca la que más se parezca.
4. **¿Qué ha fallado?** Botones, no texto libre: «la puerta no está donde era», «la ventana ha cambiado», «el suelo no es ese», «ha metido muebles de más», «todo demasiado oscuro», «parece de revista», «ha cambiado el tamaño». Y un campo corto para lo demás.
5. **Corregir y pedir otra vez**: los fallos marcados se añaden al prompt como instrucciones negativas. Máximo 3 rondas (cada ronda cuesta dinero y paciencia).
6. **Cerrar**: la propuesta elegida se guarda junto al presupuesto, y las que no gustan se descartan.

### Truco técnico que hace que esto funcione

Editar con IA una foto de una habitación falla justo en lo que él va a marcar: mueve la puerta o cambia la ventana. La solución es **no dejar que toque los huecos**: se le pasa la foto *y* una máscara con las zonas protegidas (ventana, puerta, esquinas) y se pide edición solo del resto (inpainting / edición por regiones, como FLUX Kontext o el modo máscara de los modelos de imagen). Sin máscara, la IA improvisa la geometría; con máscara, respeta la obra.

Por eso el paso 1 (marcar superficies y huecos) no es un adorno: es lo que después protege el resultado.

---

## 4. Qué se implementa en la app y en qué orden

**F1 · Foto y superficies** (1–2 días de trabajo, sin dependencias nuevas)
- `foto/Captura.kt` con CameraX; guardar en `filesDir`.
- `foto/Superficies.kt`: 4 puntos por superficie, homografía con `setPolyToPoly`.
- `foto/Compositor.kt`: material + `MULTIPLY`, recorte de huecos, salida a Bitmap 1080 px.
- Pantallas: foto con esquinas arrastrables, paleta de materiales, antes/después.

**F2 · Luces** (1 día)
- Toque sobre la foto → punto de luz con tipo (foco, aplique, tira LED) y potencia.
- Halo + sombra con `BlurMaskFilter`; lista de puntos exportable.
- El presupuesto gana una partida automática por punto de luz.

**F3 · IA opcional** (2–3 días + Worker)
- `ia/Proxy.kt` (una sola llamada HTTPS), ajustes con «clave y límite», consentimiento por obra.
- Constructor de prompt y corrector por botones (la lógica ya está diseñada arriba).
- Caché por (foto + prompt) para no pagar dos veces lo mismo; límite diario en el propio móvil además del del Worker.

---

## 5. Lo que no voy a prometer

- No habrá fotorrealismo con IA sin conexión: un modelo de imagen no cabe en un móvil de gama media.
- La IA **inventa**: puede poner un suelo precioso que no existe en el catálogo. Sirve para vender, no para presupuestar. El presupuesto sigue saliendo de las medidas, no de la imagen.
- Las propuestas no son infinitas: 3 por ronda y 3 rondas es lo que se puede pagar y lo que se puede mirar sin volverse loco.
- Si el cliente pregunta «¿esto es exactamente lo que voy a tener?», la respuesta honesta es la del compositor (F1), no la de la IA.

---

## 6. Dónde está en la vista previa

Tres pantallas nuevas en `preview/`:

- **1 · Foto y superficies**: la foto real con las esquinas marcadas y lo que ha reconocido.
- **2 · Materiales y luces**: paleta de paredes y suelos, luz que ya hay y luces que se añaden, todo en vivo sobre la foto.
- **3 · Retoque con IA**: prompt, tres propuestas, botones de «qué ha fallado», corrección y prompt exacto a la vista.

En la vista previa las propuestas están marcadas como **simulación**: no hay ninguna llamada a un modelo. Los acabados y las luces de la pantalla 2 sí son reales (se calculan en el navegador sobre la foto), que es exactamente lo que hará la app en F1.
