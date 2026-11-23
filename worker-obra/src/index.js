/**
 * Worker de Obra: una imagen de reforma por petición, sin claves en el APK.
 *
 * La cuenta de Cloudflare vive aquí, como binding `AI`; la app solo conoce esta dirección. El
 * plan gratuito da 10.000 neuronas al día (unas 90 imágenes de 1024×1024) y no tiene método de
 * pago: cuando se agota, el modelo falla y nunca se cobra nada.
 *
 * Contrato:
 *   GET  /salud   → { ok, modelo }
 *   POST /imagen  → { imagen: base64, restantes } | { error, restantes }
 *
 * La foto llega ya reducida por la app (menos de 512 px de lado), que es lo que acepta el modelo.
 */

const MODELO = "@cf/black-forest-labs/flux-2-klein-4b";
const MAX_FOTO = 2_000_000; // caracteres base64, ~1,4 MB de imagen
const MAX_PROMPT = 900;
const LIMITE_DIA = 20;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/salud") {
      return json({ ok: true, modelo: MODELO });
    }
    // La raíz también genera: alguna versión de la app pedía la imagen a "/" y se comía un 404.
    if (url.pathname !== "/imagen" && url.pathname !== "/") {
      return json({ error: "Ruta no encontrada" }, 404);
    }
    if (request.method !== "POST") return json({ error: "Método no permitido" }, 405);

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

    let restantes = null;
    if (env.CONTADORES) {
      const cupo = await gastar(env, dispositivo);
      if (cupo.agotado) return json({ error: "sin_cuota", restantes: 0 }, 429);
      restantes = cupo.restantes;
    }

    try {
      const imagen = await generar(env, prompt, foto, datos?.ancho, datos?.alto);
      if (!imagen) return json({ error: "El modelo no devolvió imagen" }, 502);
      return json({ imagen, restantes });
    } catch (error) {
      const texto = String(error?.message ?? error);
      if (/neuron|quota|capacity|429|rate.?limit/i.test(texto)) {
        return json({ error: "sin_cuota", restantes: 0 }, 429);
      }
      return json({ error: "El modelo no pudo generar la imagen" }, 502);
    }
  }
};

async function generar(env, prompt, foto, ancho, alto) {
  const bytes = deBase64(foto);
  const formulario = new FormData();
  formulario.append("prompt", prompt);
  formulario.append("width", String(lado(ancho, 1024)));
  formulario.append("height", String(lado(alto, 768)));
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

function deBase64(texto) {
  const binario = atob(texto);
  const bytes = new Uint8Array(binario.length);
  for (let i = 0; i < binario.length; i++) bytes[i] = binario.charCodeAt(i);
  return bytes;
}

/** El modelo acepta de 512 a 1024 y en múltiplos de 32. */
function lado(valor, porDefecto) {
  const numero = Number(valor);
  if (!Number.isFinite(numero)) return porDefecto;
  return Math.min(1024, Math.max(512, Math.round(numero / 32) * 32));
}

/** Contador por móvil y día UTC. Se guarda una huella, no el identificador completo. */
async function gastar(env, dispositivo) {
  const dia = new Date().toISOString().slice(0, 10);
  const clave = `d:${huella(dispositivo)}:${dia}`;
  const usadas = Number(await env.CONTADORES.get(clave)) || 0;
  if (usadas >= LIMITE_DIA) return { agotado: true, restantes: 0 };
  await env.CONTADORES.put(clave, String(usadas + 1), { expirationTtl: 172_800 });
  return { agotado: false, restantes: LIMITE_DIA - usadas - 1 };
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
