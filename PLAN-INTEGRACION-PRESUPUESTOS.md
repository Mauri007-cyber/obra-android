# Integración de la PWA de presupuestos

Estado: **analizada y extraída** del zip `presupuestos-obra-codigo.zip` (136 KB, publicado en el repo).
La vista previa navegable está en `preview/` (ver el final).

---

## 1. Qué es exactamente lo que has validado

PWA React + Vite + TypeScript, Tailwind, Dexie (IndexedDB) y `pdf-lib`.
Se instala en el móvil, funciona sin conexión, no tiene cuentas ni servidor de datos: todo vive en el navegador.
`worker.js` solo sirve los archivos estáticos.

Pantallas: **Presupuestos** (lista), **editor**, **Mis precios**, **Ajustes**.
Patrón de interacción: hojas que suben desde abajo, autoguardado, deshacer en vez de "¿seguro?", botón grande abajo.

## 2. Modelo de datos (esto es lo que hay que portar)

```ts
Presupuesto { id, numero, clienteNombre, obraTitulo, obraDireccion?, fecha,
              validezDias, estado, lineas[], notas?, creadoEn, actualizadoEn, enviadoEn? }
Linea       { id, concepto, cantidad, unidad, precioUnitario, tipo, precioId? }
Precio      { id, concepto, unidad, precioUnitario, tipo, vecesUsado, ultimaVez }
Cliente     { id, nombre, telefono? }          // declarado, sin uso todavía
Ajustes     { id:'unico', nombre, oficio, telefono, nif?, direccion?,
              validezDias, serie, siguienteNumero, ultimaCopia? }
```

- `unidad`: `m2 | ml | ud | h | kg | global`. **`global` no es una unidad: es el modo "partida entera"** (precio cerrado, cantidad 1).
- `tipo`: `material | mano_obra | mixto` (en pantalla, "Todo incluido").
- `estado`: `borrador | enviado | aceptado | rechazado | terminado`.
- `numero`: `serie-siguiente` con relleno a 3 → `2026-001`. Se incrementa en una transacción.

## 3. Reglas de cálculo (y por qué están bien)

1. `subtotalLinea` redondea a céntimos.
2. **El total suma céntimos enteros**, no flotantes: el total coincide siempre con la suma de lo que ve el cliente. Esto se queda tal cual.
3. Nada de NaN: cantidad o precio no finitos cuentan como 0.
4. `parsearNumero` acepta `1.245,09`, `18,5` y `18,50`, y `aTexto` escribe con coma. Portar tal cual al campo de cantidad/precio.
5. IVA: **no existe en el código**. Ver hueco nº 1.

## 4. Lo que merece portarse sin tocar

- Suma en céntimos y redondeo por línea.
- "Partida entera" / "por medida" como elección explícita, no como unidad rara.
- Catálogo que **aprende solo**: al guardar una partida se recuerda concepto+unidad+precio y sube `vecesUsado`; los 6 más usados salen como botones en el formulario.
- Numeración por serie y año, con el siguiente número guardado.
- Estados con color fijo y fecha de envío.
- Aviso de copia de seguridad a los 15 días (y con 3 o más presupuestos).
- Autoguardado a los 350 ms y guardado forzoso al salir o antes de actualizar la app.
- Textos y criterios de `docs/01-estrategia-producto.md`: una cosa por pantalla, botones de 56 px, nada de iconos mudos, "Deshacer" en vez de confirmar, lenguaje de obra.

## 5. Huecos y contradicciones encontrados

| # | Qué pasa | Decisión |
|---|---|---|
| 1 | La especificación pide **IVA 21 % / 10 %** y el boceto del editor lo muestra, pero el código no lo guarda ni lo aplica: el total es la suma de partidas. | Se añade IVA por presupuesto (0 / 10 / 21), calculado sobre la base en céntimos. Va en la vista previa y es el cambio nº 1 al portar. |
| 2 | `Cliente` está declarado y nunca se usa: no hay agenda de clientes. | No se porta. El nombre se escribe en el presupuesto, como ya hace la app. |
| 3 | Sin capítulos ni subtotales: una lista plana de partidas. | Se mantiene plano. Con 5–15 partidas un capítulo es más ruido que ayuda; si algún día pasa de 25, se añade. |
| 4 | El PDF no lleva logo ni condiciones generales, solo notas libres. | La app ya tiene ficha de empresa; el PDF se hace con cabecera + partidas + total + notas, igual que la PWA, y se le añade el logo opcional más adelante. |
| 5 | `recordarPrecio` guarda siempre que la casilla esté marcada (por defecto en partidas nuevas), aunque el precio venga del catálogo. | Al portar: si la partida viene de `Mis precios`, no se duplica; solo se actualiza el precio si cambió. |
| 6 | Borrar una partida no se puede deshacer si cierras la app antes del aviso. | En la app Android se usa el mismo patrón pero el borrado ya es inmediato en Room; el aviso "Deshacer" reinserta. |
| 7 | No hay importación de la copia desde la interfaz (solo se genera). | En Android no hace falta: la copia es un JSON que se puede volver a abrir desde Ajustes. |
| 8 | `validezDias` se guarda pero no se muestra en el PDF como fecha límite calculada. | Se calcula y se imprime "Válido hasta el …". |

## 6. Cómo encaja en la app Android

La app ya tiene lo que a la PWA le falta: **las medidas**. La integración va en una sola dirección:

```
medidas (AR / a mano) → plano (muros + huecos) → partidas del plano → presupuesto → PDF
```

Mapeo:

| PWA | App (`dominio/Presupuesto.kt`) |
|---|---|
| `Linea.concepto` | `Partida.concepto` |
| `Linea.cantidad` | `Partida.cantidad` (ya viene de `superficie()` o de metros de muro) |
| `Linea.unidad` | `Unidad` (`M2, ML, UD, H, KG, GLOBAL`) |
| `Linea.precioUnitario` | `Partida.precio` |
| `Linea.tipo` | `TipoPartida` (`MATERIAL, MANO_OBRA, MIXTO`) |
| `Linea.precioId` | `precioId` (catálogo Room) |
| `Presupuesto.numero` | `numero` (serie + siguiente) |
| `Presupuesto.estado` | `EstadoPresupuesto` |
| `Ajustes.*` | `DatosOficio` (nombre, oficio, teléfono, NIF, serie) |
| `Precio` | tabla `precios` (nueva, con `vecesUsado`) |

Lo nuevo que hay que escribir en la app: tabla `precios`, estados, numeración, IVA por presupuesto y el generador de partidas desde el plano (una línea por tipo de trabajo, con la cantidad ya medida). Todo lo demás (PDF, compartir, guardado) ya existe.

## 7. Vista previa web (lo que puedes abrir ahora)

`preview/` es un MVP sin dependencias: HTML, CSS y JS servidos en local.
Pantallas: Inicio, La obra, Plano, Medir, Enseñar (con los renders reales), Presupuestos, Editor, Mis precios, Ajustes.

Funciona de verdad:
- totales en céntimos con IVA 0/10/21,
- cambiar cantidad con − / + y ver el total recalcularse,
- añadir partida (con atajos de Mis precios) y borrar con Deshacer,
- añadir y borrar precios del catálogo,
- cambiar estado y antes/después.

Los datos son de ejemplo (Carmen Ruiz, 2026-001). El zip y lo extraído en `pwa/` se pueden borrar cuando valides la vista previa; nada de la app depende de ellos.

Después se añadieron tres pantallas de fotorrealismo sobre la foto real (`1 · Foto y superficies`, `2 · Materiales y luces`, `3 · Retoque con IA`). Su plan técnico y sus costes están en [PLAN-FOTORREALISMO.md](PLAN-FOTORREALISMO.md).

## 8. Tres decisiones que necesito de ti

1. **¿Sustituir o convivir?** Recomiendo sustituir el presupuesto actual de la app por este: una sola pantalla de presupuesto, la de la PWA, alimentada por el plano.
2. **¿Precios propios o catálogo cerrado?** Recomiendo catálogo propio que aprende (el de la PWA), sin lista de precios de mercado.
3. **¿IVA siempre, o solo cuando lo pongas?** Recomiendo 21 % por defecto, con opción "sin IVA" para trabajos con IVA incluido.
