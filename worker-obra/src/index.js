/**
 * Worker de Obra: imagen de reforma y propuestas de escenarios, sin claves en el APK.
 *
 * La cuenta de Cloudflare vive aquí, como binding `AI`; la app solo conoce esta dirección. El
 * plan gratuito da 10.000 neuronas al día y no tiene método de pago: cuando se agota, el modelo
 * falla y nunca se cobra nada.
 *
 * Contrato:
 *   GET  /salud      → { ok, modelo, vision, texto }
 *   POST /imagen     → { imagen: base64, descripcion, restantes } | { error, restantes }
 *   POST /propuestas → { propuestas: [...], restantes } | { error, restantes }
 *
 * La foto llega ya reducida por la app (menos de 512 px de lado), que es lo que acepta el modelo.
 */

const MODELO = "@cf/black-forest-labs/flux-2-klein-4b";
const MODELO_VISION = "@cf/meta/llama-3.2-11b-vision-instruct";
const MODELO_TEXTO = "@cf/meta/llama-3.3-70b-instruct-fp8-fast";
const MODELO_TEXTO_RESPALDO = "@cf/meta/llama-3.1-8b-instruct";

const MAX_FOTO = 2_000_000; // caracteres base64, ~1,4 MB de imagen
const MAX_PROMPT = 2000;
const MAX_DESCRIPCION = 460;
const LIMITE_DIA = 20;
const LIMITE_PROPUESTAS = 10;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/salud") {
      return json({
        ok: true,
        modelo: MODELO,
        vision: MODELO_VISION,
        texto: MODELO_TEXTO
      });
    }

    if (url.pathname === "/propuestas") {
      if (request.method !== "POST") return json({ error: "Método no permitido" }, 405);
      return propuestas(request, env);
    }

    // La raíz también genera: alguna versión de la app pedía la imagen a "/" y se comía un 404.
    if (url.pathname !== "/imagen" && url.pathname !== "/") {
      return json({ error: "Ruta no encontrada" }, 404);
    }
    if (request.method !== "POST") return json({ error: "Método no permitido" }, 405);

    return imagen(request, env);
  }
};

/* ------------------------------------------------------------------ */
/* Imagen                                                              */
/* ------------------------------------------------------------------ */

async function imagen(request, env) {
  let datos;
  try {
    datos = await request.json();
  } catch {
    return json({ error: "El cuerpo no es JSON válido" }, 400);
  }

  const prompt = String(datos?.prompt ?? "").trim();
  const foto = String(datos?.foto ?? "").replace(/^data:image\/\w+;base64,/, "");
  const dispositivo = String(datos?.dispositivo ?? "").slice(0, 120);

  if (!prompt || prompt.length > MAX_PROMPT) return json({ error: "Prompt inválido" }, 400);
  if (!foto) return json({ error: "Falta la foto" }, 400);
  if (foto.length > MAX_FOTO) return json({ error: "La foto es demasiado grande" }, 413);
  if (dispositivo.length < 8) return json({ error: "Falta el identificador del móvil" }, 400);

  const bytes = deBase64(foto);

  // La cuota se mira antes, pero solo se gasta si la imagen sale: un fallo del modelo o de la
  // red no debe dejar al albañil sin sus imágenes del día.
  let restantes = null;
  if (env.CONTADORES) {
    const cupo = await consultar(env, dispositivo, "d", LIMITE_DIA);
    if (cupo.agotado) return json({ error: "sin_cuota", restantes: 0 }, 429);
    restantes = cupo.restantes;
  }

  // Analizar la habitación ANTES de tocarla: así el modelo respeta la posición de puertas y
  // ventanas, el suelo, el techo y lo que no se ha pedido cambiar. Si la visión falla, se sigue
  // sin análisis: la imagen no puede depender de ella.
  const descripcion = await describir(env, bytes);
  const promptFinal = armarPrompt(prompt, descripcion);

  try {
    const imagen = await generar(env, promptFinal, bytes, datos?.ancho, datos?.alto);
    if (!imagen) return json({ error: "El modelo no devolvió imagen" }, 502);
    if (env.CONTADORES) {
      restantes = (await gastar(env, dispositivo, "d", LIMITE_DIA)).restantes;
    }
    return json({ imagen, descripcion, restantes });
  } catch (error) {
    const texto = String(error?.message ?? error);
    if (/neuron|quota|capacity|429|rate.?limit/i.test(texto)) {
      return json({ error: "sin_cuota", restantes: 0 }, 429);
    }
    return json({ error: "El modelo no pudo generar la imagen" }, 502);
  }
}

const ETIQUETAS = ["FLOOR", "WALLS", "CEILING", "OPENINGS", "FIXED", "VIEWPOINT"];

/**
 * Análisis estructurado de la foto: qué hay y DÓNDE está. Las posiciones son lo que evita que el
 * modelo mueva una puerta o ponga la ventana en otra pared al generar.
 */
async function describir(env, bytes) {
  const general = await correrVision(
    env,
    bytes,
    "Analyse this room photograph so an image editor does not move anything. " +
      "Answer in English with exactly these lines and nothing else, at most 14 words each:\n" +
      "FLOOR: material and colour\n" +
      "WALLS: colour and finish\n" +
      "CEILING: material and colour\n" +
      "OPENINGS: every door and window, on which wall (left, right, front, behind the camera), " +
      "with frame material and colour\n" +
      "FIXED: fixed elements (radiators, island, fitted furniture, sockets) and which wall\n" +
      "VIEWPOINT: camera height and angle\n" +
      "Write unknown when something is not visible. No advice, no people, no extra text.",
    220
  );

  let analisis = ordenarAnalisis(general);

  // La primera pasada a veces deja puertas y ventanas fuera, que es justo lo que hay que clavar.
  // Se pregunta por paredes (izquierda, derecha, fondo): así el modelo tiene que colocarlas.
  if (!/OPENINGS:/.test(analisis)) {
    const layout = await correrVision(
      env,
      bytes,
      "Look at this room photograph. In one sentence of at most 35 words, say what is on the " +
        "left wall, on the right wall and on the back wall: doors, windows, radiators, fixed " +
        "furniture, each with its colour and material. Start with the word LAYOUT. " +
        "Do not describe the floor, the walls or the ceiling in general. No advice, no people.",
      130
    );
    const limpio = layout.replace(/\s+/g, " ").replace(/^\W*LAYOUT\W*/i, "").trim();
    if (limpio.length > 8) {
      analisis = analisis
        ? `${analisis}; LAYOUT: ${limpio.slice(0, 220)}`
        : `LAYOUT: ${limpio.slice(0, 220)}`;
      analisis = analisis.slice(0, MAX_DESCRIPCION);
    }
  }

  return analisis;
}

/** Una llamada al modelo de visión; devuelve el texto o "" sin lanzar. */
async function correrVision(env, bytes, prompt, maxTokens) {
  try {
    const salida = await env.AI.run(MODELO_VISION, {
      image: Array.from(bytes),
      prompt,
      max_tokens: maxTokens,
      temperature: 0.2
    });
    return String(salida?.response ?? salida?.description ?? salida?.result?.response ?? "");
  } catch (error) {
    // Sin análisis se genera igual: la imagen no puede depender de la visión.
    console.warn("visión:", String(error?.message ?? error).slice(0, 300));
    return "";
  }
}

/**
 * Deja solo las líneas que se entienden, en orden fijo y sin repetir. Así el análisis no se corta
 * por el medio ni pierde las posiciones si el modelo añade adornos.
 */
function ordenarAnalisis(bruto) {
  const lineas = String(bruto ?? "").split(/\n|(?=\b(?:FLOOR|WALLS|CEILING|OPENINGS|FIXED|VIEWPOINT):)/i);
  const encontradas = new Map();
  lineas.forEach((linea) => {
    const limpia = linea.replace(/^[-*•\s]+/, "").trim();
    const partes = limpia.match(/^(FLOOR|WALLS|CEILING|OPENINGS|FIXED|VIEWPOINT)\s*:\s*(.+)$/i);
    if (!partes) return;
    const etiqueta = partes[1].toUpperCase();
    const valor = partes[2].replace(/\s+/g, " ").replace(/[.;]+$/, "").trim();
    if (!valor || /^unknown\b/i.test(valor)) return;
    if (!encontradas.has(etiqueta)) encontradas.set(etiqueta, valor.slice(0, 130));
  });
  const ordenado = ETIQUETAS
    .filter((etiqueta) => encontradas.has(etiqueta))
    .map((etiqueta) => `${etiqueta}: ${encontradas.get(etiqueta)}`)
    .join("; ");

  if (ordenado.length > 8) return ordenado.slice(0, MAX_DESCRIPCION);

  // Si el modelo no respetó el formato, se usa su frase tal cual: mejor algo que nada.
  const suelto = String(bruto ?? "").replace(/\s+/g, " ").trim();
  return suelto.length > 8 ? suelto.slice(0, MAX_DESCRIPCION) : "";
}

/**
 * El análisis va delante y se reserva su sitio: si el prompt del cliente es muy largo se recorta
 * la petición, nunca la lista de lo que no se debe tocar.
 */
function armarPrompt(prompt, analisis) {
  if (!analisis) return limitar(prompt, MAX_PROMPT);
  // «Do not add…» va con el análisis, delante: el modelo tiende a amueblar y decorar por su
  // cuenta, y lo que no pide el cliente no debe aparecer en la imagen.
  const cabecera =
    "Existing room, keep every item in the same place unless the request changes it; " +
    "do not add furniture or decoration that the request does not name: ";
  const sitio = MAX_PROMPT - cabecera.length - analisis.length - 2;
  const peticion = limitar(prompt, Math.max(400, sitio));
  return limitar(`${cabecera}${analisis} || ${peticion}`, MAX_PROMPT);
}

async function generar(env, prompt, bytes, ancho, alto) {
  const formulario = new FormData();
  formulario.append("prompt", prompt);
  formulario.append("width", String(lado(ancho, 1024)));
  formulario.append("height", String(lado(alto, 768)));
  // El modelo obedece algo más al prompt con guidance alto (por defecto viene bajo).
  formulario.append("guidance", "7");
  formulario.append("input_image_0", new Blob([bytes], { type: "image/jpeg" }), "estancia.jpg");

  // FormData no expone su cuerpo: hay que serializarlo en una Response para obtener el boundary.
  const serializado = new Response(formulario);
  const salida = await env.AI.run(MODELO, {
    multipart: {
      body: serializado.body,
      contentType: serializado.headers.get("content-type")
    }
  });
  return salida?.image ?? salida?.result?.image ?? "";
}

/* ------------------------------------------------------------------ */
/* Propuestas                                                          */
/* ------------------------------------------------------------------ */

const INSTRUCCIONES = `Eres un jefe de obra español que prepara escenarios de reforma para un
albañil y su cliente. Devuelve SOLO un objeto JSON, sin texto alrededor, con esta forma exacta:
{"propuestas":[{"titulo":"...","resumen":"...","actuaciones":[{"tipo":"CLAVE","estancia":"NOMBRE","cantidad":12.5,"motivo":"..."}]}]}

Reglas:
- Exactamente 3 propuestas, con enfoques distintos: una económica, una equilibrada y una ambiciosa.
- Entre 2 y 7 actuaciones por propuesta (con el catálogo recortado puede haber pocas).
- "tipo" tiene que ser una de las claves del catálogo que recibes. Nunca inventes claves.
- "estancia" tiene que ser una de las estancias que recibes, escrita igual.
- "cantidad" en la unidad de esa partida (m², ml o ud). Si la partida es global, cantidad 1.
- Usa cantidades coherentes con las superficies que recibes; no sumes más m² que los disponibles.
- Si renuevas un baño o una cocina, incluye las tomas de agua, los desagües y el termo que hagan
  falta: forman parte de esa obra y están en el catálogo.
- "motivo" en una frase corta, en español, para el cliente (máximo 120 caracteres).
- "resumen" máximo 220 caracteres, en español de España, sin prometer precios exactos.
- No incluyas presupuesto, coste ni texto legal: la app calcula los importes.
- Respeta lo que el cliente NO está dispuesto a hacer (por ejemplo, no tirar paredes).`;

async function propuestas(request, env) {
  let datos;
  try {
    datos = await request.json();
  } catch {
    return json({ error: "El cuerpo no es JSON válido" }, 400);
  }

  const dispositivo = String(datos?.dispositivo ?? "").slice(0, 120);
  const deseo = String(datos?.deseo ?? "").slice(0, 600);
  const permite = Array.isArray(datos?.permite) ? datos.permite.slice(0, 12).map(String) : [];
  const estancias = Array.isArray(datos?.estancias) ? datos.estancias.slice(0, 12) : [];
  const catalogo = Array.isArray(datos?.catalogo) ? datos.catalogo.slice(0, 120) : [];

  if (dispositivo.length < 8) return json({ error: "Falta el identificador del móvil" }, 400);
  // Con los chips marcados el catálogo puede ser pequeño: basta con que quepan dos partidas.
  if (catalogo.length < 2) return json({ error: "Falta el catálogo de partidas" }, 400);
  if (deseo.length < 4 && permite.length === 0) {
    return json({ error: "Cuenta al menos qué se quiere conseguir" }, 400);
  }

  // Igual que con la imagen: si la IA falla, la consulta de propuestas no se descuenta.
  let restantes = null;
  if (env.CONTADORES) {
    const cupo = await consultar(env, dispositivo, "p", LIMITE_PROPUESTAS);
    if (cupo.agotado) return json({ error: "sin_cuota", restantes: 0 }, 429);
    restantes = cupo.restantes;
  }

  const claves = new Set(catalogo.map((c) => String(c?.clave ?? "")));
  const entrada = {
    superficie_total_m2: numero(datos?.superficie),
    altura_m: numero(datos?.altura, 2.5),
    presupuesto_aproximado_eur: numero(datos?.presupuesto, 0),
    estancias,
    quiere_conseguir: deseo || "(no lo tiene claro: propone tú algo razonable)",
    esta_dispuesto_a: permite.length ? permite : ["(sin restricciones dichas)"],
    catalogo
  };

  try {
    const texto = await pedirTexto(env, entrada);
    const objeto = extraerJson(texto);
    const limpias = validar(objeto, claves, estancias);
    if (limpias.length === 0) {
      return json(
        { error: "La IA no devolvió propuestas utilizables. Prueba otra vez.", restantes },
        502
      );
    }
    if (env.CONTADORES) {
      restantes = (await gastar(env, dispositivo, "p", LIMITE_PROPUESTAS)).restantes;
    }
    return json({ propuestas: limpias, restantes });
  } catch (error) {
    const mensaje = String(error?.message ?? error);
    if (/neuron|quota|capacity|429|rate.?limit/i.test(mensaje)) {
      return json({ error: "sin_cuota", restantes: 0 }, 429);
    }
    return json({ error: "La IA no pudo preparar las propuestas. Prueba en un rato." }, 502);
  }
}

async function pedirTexto(env, entrada) {
  const mensajes = [
    { role: "system", content: INSTRUCCIONES },
    { role: "user", content: JSON.stringify(entrada) }
  ];
  const opciones = { messages: mensajes, max_tokens: 2200, temperature: 0.7 };

  try {
    return textoDe(await env.AI.run(MODELO_TEXTO, opciones));
  } catch (error) {
    // Si lo que falta es cuota o capacidad, el modelo pequeño tampoco va a poder: se sube el
    // error tal cual en vez de hacer esperar al albañil una segunda llamada perdida.
    if (/neuron|quota|capacity|429|rate.?limit/i.test(String(error?.message ?? error))) {
      throw error;
    }
    return textoDe(await env.AI.run(MODELO_TEXTO_RESPALDO, opciones));
  }
}

/**
 * Workers AI no siempre devuelve una cadena: a veces ya trae el objeto JSON, o lo mete en
 * `response`. Se normaliza aquí para no perder la respuesta buena.
 */
function textoDe(salida) {
  if (typeof salida === "string") return salida;
  const directo = salida?.response ?? salida?.result?.response ?? salida?.result ?? salida;
  if (typeof directo === "string") return directo;
  if (directo && typeof directo === "object") return JSON.stringify(directo);
  return "";
}

/** Los modelos a veces envuelven el JSON en ```json … ``` o añaden una frase antes. */
function extraerJson(texto) {
  const limpio = String(texto ?? "").replace(/```(?:json)?/gi, "").trim();
  const inicio = limpio.indexOf("{");
  const fin = limpio.lastIndexOf("}");
  if (inicio < 0 || fin <= inicio) return null;
  try {
    return JSON.parse(limpio.slice(inicio, fin + 1));
  } catch {
    return null;
  }
}

/** Se queda solo con lo que la app puede convertir en partidas de verdad. */
function validar(objeto, claves, estancias) {
  const raiz = objeto?.propuestas
    ? objeto
    : (objeto?.response && typeof objeto.response === "object" ? objeto.response : objeto);
  const lista = Array.isArray(raiz?.propuestas) ? raiz.propuestas : [];
  const nombres = new Set(estancias.map((e) => nombreClave(e?.nombre ?? e)));

  return lista
    .slice(0, 3)
    .map((propuesta) => {
      const actuaciones = (Array.isArray(propuesta?.actuaciones) ? propuesta.actuaciones : [])
        .slice(0, 8)
        .map((a) => {
          const tipo = String(a?.tipo ?? "").trim();
          if (!claves.has(tipo)) return null;
          const estancia = String(a?.estancia ?? "").trim().slice(0, 40);
          if (nombres.size > 0 && !nombres.has(nombreClave(estancia))) return null;
          const cantidad = numero(a?.cantidad, 1);
          return {
            tipo,
            estancia,
            cantidad: cantidad > 0 ? Math.min(cantidad, 500) : 1,
            motivo: String(a?.motivo ?? "").trim().slice(0, 140)
          };
        })
        .filter(Boolean);

      return {
        titulo: String(propuesta?.titulo ?? "").trim().slice(0, 60),
        resumen: String(propuesta?.resumen ?? "").trim().slice(0, 240),
        actuaciones
      };
    })
    // Sin título no se tira el escenario: la app lo numera como «Propuesta A».
    .filter((p) => p.actuaciones.length >= 2);
}

/** «Salón» y «Salon» son la misma estancia: sin tildes y en minúsculas. */
function nombreClave(texto) {
  return String(texto ?? "")
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .trim()
    .toLowerCase();
}

/* ------------------------------------------------------------------ */
/* Utilidades                                                          */
/* ------------------------------------------------------------------ */

function deBase64(texto) {
  const binario = atob(texto);
  const bytes = new Uint8Array(binario.length);
  for (let i = 0; i < binario.length; i++) bytes[i] = binario.charCodeAt(i);
  return bytes;
}

/** El modelo acepta de 512 a 1024 y en múltiplos de 32. */
function lado(valor, porDefecto) {
  const n = Number(valor);
  if (!Number.isFinite(n)) return porDefecto;
  return Math.min(1024, Math.max(512, Math.round(n / 32) * 32));
}

function numero(valor, porDefecto = 0) {
  const n = Number(valor);
  return Number.isFinite(n) ? n : porDefecto;
}

function limitar(texto, maximo) {
  if (texto.length <= maximo) return texto;
  const corte = texto.slice(0, maximo);
  const frase = corte.lastIndexOf(".");
  if (frase > maximo / 2) return corte.slice(0, frase + 1).trim();
  // Sin frase completa, se corta por palabra: nunca «metre fro».
  const espacio = corte.lastIndexOf(" ");
  return (espacio > maximo / 2 ? corte.slice(0, espacio) : corte).trim();
}

/** Mira el cupo del día sin gastarlo: solo se cobra cuando hay resultado que devolver. */
async function consultar(env, dispositivo, prefijo, limite) {
  const dia = new Date().toISOString().slice(0, 10);
  const clave = `${prefijo}:${huella(dispositivo)}:${dia}`;
  const usadas = Number(await env.CONTADORES.get(clave)) || 0;
  return usadas >= limite
    ? { agotado: true, restantes: 0 }
    : { agotado: false, restantes: limite - usadas };
}

/** Contador por móvil, día UTC y tipo. Se guarda una huella, no el identificador completo. */
async function gastar(env, dispositivo, prefijo, limite) {
  const dia = new Date().toISOString().slice(0, 10);
  const clave = `${prefijo}:${huella(dispositivo)}:${dia}`;
  const usadas = Number(await env.CONTADORES.get(clave)) || 0;
  if (usadas >= limite) return { agotado: true, restantes: 0 };
  await env.CONTADORES.put(clave, String(usadas + 1), { expirationTtl: 172_800 });
  return { agotado: false, restantes: limite - usadas - 1 };
}

function huella(texto) {
  let hash = 0x811c9dc5;
  for (let i = 0; i < texto.length; i++) {
    hash ^= texto.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193);
  }
  return (hash >>> 0).toString(16).padStart(8, "0");
}

function json(datos, estado = 200) {
  return new Response(JSON.stringify(datos), {
    status: estado,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store"
    }
  });
}
