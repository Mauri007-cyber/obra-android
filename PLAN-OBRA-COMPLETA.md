# Obra completa: el replanteo

> **Decisión de producto (23-11-2026):** la app es 100 % gratuita. Se han retirado la IA
> remota (FLUX), el recorte de fondo y su Worker porque podían generar coste. Este
> documento se conserva como histórico de ideas descartadas.
> De «una foto con un prompt» a **una obra entera modelada, evaluada y enseñable**.

## 1. Por qué el enfoque anterior no llegaba

La versión 0.9.3 trataba la reforma como **una estancia y una foto con un prompt**. Eso sirve
para enseñar un cambio suelto, pero una obra real es otra cosa:

- Varias habitaciones, cada una con su estado y su presupuesto.
- Muchos cambios por habitación (o muy pocos: hay obras de pintar y obras de tirar tabiques).
- El cliente no decide sobre una imagen: decide sobre **trabajos concretos**.
- Al final hay que enseñar **la obra terminada completa**, no un retoque.

El fallo de fondo era el modelo de datos: si la unidad es «foto + texto», no hay sitio donde
meter veinte cambios, ni dos versiones, ni la aprobación del cliente. Con ese modelo, subir
la capacidad significa reescribirlo. Eso es lo que se ha hecho.

## 2. La idea

**Un proyecto de obra = estancias → cambios → estados → recorrido final.**

1. El albañil crea la obra y sus estancias (salón, cocina, baño, pasillo…).
2. En cada estancia añade cambios **elegidos de un catálogo**, no escritos a mano.
3. Cada cambio lleva cantidad, acabado y **estado**: propuesto, aprobado o descartado.
4. El cliente evalúa la lista con el albañil delante: aprueba, descarta, deja pendiente.
5. La app calcula dinero y arma el **recorrido final**: solo lo aprobado, estancia por estancia.
6. La IA, si se usa, no recibe un prompt libre: recibe **el paquete de cambios aprobados**
   de esa estancia.

Nada de esto depende de la IA. La IA es un extra al final del camino, nunca el camino.

## 3. Modelo de datos

```
Proyecto (la obra)
 └── EstanciaProyecto  (nombre, m², altura, foto)
      └── Cambio  (tipo, cantidad, acabado, precio propio, estado, grupo A/B)
```

- **TipoCambio**: catálogo cerrado y tipificado (ver §4). Sabe su oficio, su unidad, su precio
  orientativo, sus acabados y **qué capa de la foto toca**.
- **Cambio**: la instancia concreta. El importe sale de cantidad × precio, salvo las partidas
  enteras (`global`), que se cobran una vez.
- **EstadoCambio**: `PROPUESTO`, `APROBADO`, `RECHAZADO`. Es lo que convierte la app en una
  herramienta de evaluación y no en un escaparate.
- **grupo (A/B/C…)**: dos propuestas de la misma estancia, comparables. El cliente elige.
- Persistencia: tabla `cambios` en Room (migración 4→5), con borrado en cascada por estancia.
  Los tipos se guardan por nombre: si el catálogo evoluciona, lo que ya no exista se descarta
  al cargar en vez de romper la obra.

## 4. Catálogo de cambios

Más de 45 partidas agrupadas por oficio, que es como trabaja el gremio:

| Ámbito | Ejemplos |
|---|---|
| Tirar y abrir | tabique tirado, abrir hueco, quitar alicatado, levantar suelo |
| Albañilería | tabique de ladrillo, pladur, aislamiento, solera, impermeabilizar |
| Suelo | laminado, porcelánico, vinílico, tarima, microcemento, rodapié |
| Paredes | alicatado, papel pintado, panel decorativo |
| Pintura | plástica, antihumedad, techo, fachada |
| Techo | falso techo, registrable, moldura |
| Puertas y ventanas | interior, corredera, blindada, oscilobatiente, persiana, lucernario |
| Baño | baño completo, plato de ducha, bañera, mampara, inodoro, lavabo, grifería, espejo |
| Cocina | muebles, encimera, electrodomésticos, campana, frontal |
| Electricidad | punto de luz, foco, enchufe, cuadro, tira LED |
| Fontanería | toma de agua, desagüe, termo |
| Clima | radiador, suelo radiante, split, ventilación |
| Muebles a medida | armario, estantería, mueble de salón |
| Exterior | pintura de fachada |

Cada partida trae **cantidad sugerida** según la estancia: 18 m² de suelo en un salón de 18 m²,
40 m² de pintura en paredes de 4×4×2,5, 8 m de rodapié. Se puede cambiar, pero no hay que
calcularlo a mano.

## 5. Qué se ve y qué no (decisión de diseño importante)

No todos los cambios se pintan en una foto:

- **Se componen o se piden al modelo**: suelo, paredes, techo, huecos y objetos (muebles,
  sanitarios, electrodomésticos).
- **No se piden al modelo**: demoliciones y albañilería que mueven tabiques. Van al plano y al
  presupuesto, porque cambiar la distribución rompería la única regla que protege la obra:
  *no mover puertas ni ventanas*.

Esa distinción está en el modelo (`TipoCambio.capa`, `seVe`) y se aplica sola al construir el
prompt. La app avisa de los cambios que se ven y no tienen foto.

## 6. Motor visual (gratis y sin conexión)

Tres niveles, de más barato a más caro:

1. **Composición sobre la foto real** (0 €, offline): cada cambio pinta su capa —suelo, pared,
   techo— sobre las superficies marcadas con homografía (`setPolyToPoly`), en modo `MULTIPLY`
   para respetar luces y sombras, con las puertas y ventanas protegidas. Un cambio o veinte:
   el resultado es el mismo proceso repetido por capas.
2. **Luces**: focos, apliques y tiras se dibujan como halos aditivos sobre la composición; la
   luz que entra por la ventana se conserva porque la foto manda.
3. **IA por paquete** (opcional, cupo diario): se manda la foto y **la lista aprobada** de esa
   estancia, no un texto suelto. El prompt se genera solo (`PromptEstancia`), con el orden real
   de trabajo y la regla final de no mover nada. Si no cabe en el límite del Worker, se quitan
   primero los objetos, nunca la regla.

El compositor es lo que hace que la app siga siendo gratis y funcione en una obra sin cobertura.
La IA es el acabado fino, no el motor.

## 7. Evaluación y presentación final

- **Aprobar / por decidir / fuera** en cada cambio, con un toque.
- Barra de progreso: cuántos cambios están cerrados sobre el total.
- Avisos antes de enseñar: estancia sin cambios, cambio sin cantidad, cambio visible sin foto,
  cambio aprobado sin precio.
- **Recorrido final**: pantalla completa con las estancias ordenadas por lo que se puede
  enseñar, la foto, la lista de trabajos aprobados en frases que entiende el cliente, y el
  coste aprobado por estancia y total.
- La presentación no enseña nada pendiente ni descartado: lo que se ve es lo que se firma.

## 8. Cómo encaja con el presupuesto

El presupuesto deja de ser un documento aparte: cada cambio aprobado **es** una línea de
presupuesto con su cantidad y su precio. Quedan dos caminos, y hay que elegir uno:

- **A · El proyecto manda**: el presupuesto se genera desde los cambios aprobados.
- **B · Conviven**: el presupuesto de la PWA sigue siendo la fuente de precios y el proyecto
  aporta la parte visual y la evaluación del cliente.

Recomendación: **B para no romper lo que ya funciona**, con un botón «llevar los cambios
aprobados al presupuesto» que crea las líneas. El IVA y los capítulos se resuelven en el
presupuesto, no aquí.

## 9. Fases

| Fase | Qué | Estado |
|---|---|---|
| F0 | Catálogo tipificado y modelo Proyecto/Estancia/Cambio con estados | **Hecho** |
| F0 | Costes, progreso, avisos, guion y recorrido | **Hecho** |
| F0 | Prompt derivado del paquete de cambios (con recorte y regla fija) | **Hecho** |
| F0 | Room v5: tabla de cambios, superficie y foto por estancia | **Hecho** |
| F0 | Pantalla «Proyecto»: estancias, catálogo, aprobación, recorrido | **Hecho** (compila, sin probar en móvil) |
| F0 | Vista web del concepto con 4 estancias y comparador antes/después | **Hecho** |
| F1 | Captura de foto por estancia y marcado de superficies en la app | Pendiente |
| F1 | Compositor de capas sobre la foto (suelo/pared/techo, huecos protegidos) | Pendiente |
| F2 | Luces dibujadas y paso de la composición a la lámina y al GIF | Pendiente |
| F2 | Versiones A/B visibles en la interfaz y comparación lado a lado | Pendiente |
| F3 | Envío del paquete aprobado al Worker y corrección por fallos | Pendiente |
| F3 | Botón «llevar al presupuesto» y capítulos | Pendiente |
| F4 | Dossier final: PDF por estancia + recorrido + QR para el cliente | Pendiente |

## 10. Costes y límites

- Modelo de datos, catálogo, evaluación, recorrido, compositor: **0 €**, sin conexión.
- IA: cupo gratuito de Workers AI con tope de 30 imágenes/día por dispositivo. Si se agota,
  la app avisa y sigue funcionando en modo local.
- El APK no lleva ninguna clave: el intermediario (Worker) es del usuario.

## 11. Riesgos

- **Catálogo demasiado largo**: 45 partidas pueden abrumar. Se mitiga con buscador y con el
  orden por oficio; el que empieza usa cuatro y el que sabe usa el resto.
- **Fotos pobres**: sin foto buena no hay composición ni IA creíble. Por eso F1 empieza por la
  captura guiada, no por el modelo.
- **Promesas de fotorrealismo**: la composición por capas es honesta y barata; la IA puede
  inventar. La interfaz debe decir siempre qué es composición y qué es IA.
- **Migración**: la tabla nueva nace vacía; las obras antiguas se abren sin perder nada. Aun
  así, conviene una copia `.obra` antes de actualizar en el móvil del padre.

## 12. Qué se puede ver ya

- `preview/proyecto.html`: demo navegable con 4 estancias, catálogo real, aprobación en vivo,
  totales, y recorrido final con comparador antes/después.
- App Android: pantalla **Proyecto: estancias y cambios** desde la ficha de la obra, con el
  mismo catálogo y el mismo recorrido. Compila (81 pruebas en verde), **no probada en móvil**.
