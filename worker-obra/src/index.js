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
const MAX_PROMPT = 1500;
const MAX_DESCRIPCION = 420;
const LIMITE_DIA = 20;
const LIMITE_PROPUESTAS = 10;
const LIMITE_VISION = 30;

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

    // Solo para comprobar la visión desde el navegador: no la usa la app.
    if (url.pathname === "/vision") {
      if (request.method !== "POST") return json({ error: "Método no permitido" }, 405);
      let cuerpo;
      try {
        cuerpo = await request.json();
      } catch {
        return json({ error: "El cuerpo no es JSON válido" }, 400);
      }
      const foto = String(cuerpo?.foto ?? "").replace(/^data:image\/\w+;base64,/, "");
      const dispositivo = String(cuerpo?.dispositivo ?? "").slice(0, 120);
      if (!foto) return json({ error: "Falta la foto" }, 400);
      if (foto.length > MAX_FOTO) return json({ error: "La foto es demasiado grande" }, 413);
      if (dispositivo.length < 8) return json({ error: "Falta el identificador del móvil" }, 400);
      if (env.CONTADORES) {
        const cupo = await gastar(env, dispositivo, "v", LIMITE_VISION);
        if (cupo.agotado) return json({ error: "sin_cuota" }, 429);
      }
      const vista = await describir(env, deBase64(foto));
      return json({ descripcion: vista.texto, error: vista.error });
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

  let restantes = null;
  if (env.CONTADORES) {
    const cupo = await gastar(env, dispositivo, "d", LIMITE_DIA);
    if (cupo.agotado) return json({ error: "sin_cuota", restantes: 0 }, 429);
    restantes = cupo.restantes;
  }

  // Ver la habitación antes de tocarla: así el modelo respeta suelo, techo y paredes que no se
  // han pedido cambiar, en vez de inventarse un baño nuevo. Si la visión falla, se sigue sin ella.
  const vista = await describir(env, bytes);
  const descripcion = vista.texto;
  const promptFinal = limitar(
    descripcion
      ? `${prompt} Keep these real features of the room exactly as they are unless the request ` +
          `changes them: ${descripcion}`
      : prompt,
    MAX_PROMPT
  );

  try {
    const imagen = await generar(env, promptFinal, bytes, datos?.ancho, datos?.alto);
    if (!imagen) return json({ error: "El modelo no devolvió imagen" }, 502);
    return json({ imagen, descripcion, restantes });
  } catch (error) {
    const texto = String(error?.message ?? error);
    if (/neuron|quota|capacity|429|rate.?limit/i.test(texto)) {
      return json({ error: "sin_cuota", restantes: 0 }, 429);
    }
    return json({ error: "El modelo no pudo generar la imagen" }, 502);
  }
}

/** Una frase corta y literal de lo que ya hay: materiales, colores y huecos. */
async function describir(env, bytes) {
  try {
    const salida = await env.AI.run(MODELO_VISION, {
      image: Array.from(bytes),
      prompt:
        "Describe this room in one sentence of at most 45 English words. Only what is visible: " +
        "floor material and colour, walls, ceiling, window and door frames, fixed furniture. " +
        "Do not invent, do not mention people, no lists, no advice.",
      max_tokens: 140,
      temperature: 0.2
    });
    const texto = String(
      salida?.response ?? salida?.description ?? salida?.result?.response ?? ""
    )
      .replace(/\s+/g, " ")
      .trim();
    return { texto: texto.length > 8 ? texto.slice(0, MAX_DESCRIPCION) : "", error: "" };
  } catch (error) {
    return { texto: "", error: String(error?.message ?? error).slice(0, 300) };
  }
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
- Entre 3 y 7 actuaciones por propuesta.
- "tipo" tiene que ser una de las claves del catálogo que recibes. Nunca inventes claves.
- "estancia" tiene que ser una de las estancias que recibes, escrita igual.
- "cantidad" en la unidad de esa partida (m², ml o ud). Si la partida es global, cantidad 1.
- Usa cantidades coherentes con las superficies que recibes; no sumes más m² que los disponibles.
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
  if (catalogo.length < 5) return json({ error: "Falta el catálogo de partidas" }, 400);
  if (deseo.length < 4 && permite.length === 0) {
    return json({ error: "Cuenta al menos qué se quiere conseguir" }, 400);
  }

  let restantes = null;
  if (env.CONTADORES) {
    const cupo = await gastar(env, dispositivo, "p", LIMITE_PROPUESTAS);
    if (cupo.agotado) return json({ error: "sin_cuota", restantes: 0 }, 429);
    restantes = cupo.restantes;
  }

  const claves = new Set(catalogo.map((c) => String(c?.clave ?? "")));
  const entrada = {
    tipo_de_obra: String(datos?.tipo ?? "piso").slice(0, 40),
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
    // Para depurar desde el navegador: /propuestas?depurar=1 devuelve lo que dijo el modelo.
    const bruto = new URL(request.url).searchParams.get("depurar") === "1"
      ? { bruto: String(texto).slice(0, 4000) }
      : {};
    if (limpias.length === 0) {
      return json(
        {
          error: "La IA no devolvió propuestas utilizables. Prueba otra vez.",
          restantes,
          ...bruto
        },
        502
      );
    }
    return json({ propuestas: limpias, restantes, ...bruto });
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

  let salida;
  try {
    salida = await env.AI.run(MODELO_TEXTO, opciones);
  } catch {
    salida = await env.AI.run(MODELO_TEXTO_RESPALDO, opciones);
  }
  return textoDe(salida);
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
  const nombres = new Set(estancias.map((e) => String(e?.nombre ?? e).trim().toLowerCase()));

  return lista
    .slice(0, 3)
    .map((propuesta) => {
      const actuaciones = (Array.isArray(propuesta?.actuaciones) ? propuesta.actuaciones : [])
        .slice(0, 8)
        .map((a) => {
          const tipo = String(a?.tipo ?? "").trim();
          if (!claves.has(tipo)) return null;
          const estancia = String(a?.estancia ?? "").trim().slice(0, 40);
          if (nombres.size > 0 && !nombres.has(estancia.toLowerCase())) return null;
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
    .filter((p) => p.titulo && p.actuaciones.length >= 2);
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
  return (frase > maximo / 2 ? corte.slice(0, frase + 1) : corte).trim();
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
