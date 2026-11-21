import { leerImagen, pedirRecorte } from './fondo.js';
import {
  LIMITE_FOTO,
  base64ABytes,
  bytesABase64,
  claveCupo,
  cupoAgotado,
  diaUtc,
  identidad,
  normalizarImagen,
  tamanoSalida,
  validarPeticion
} from './logica.js';

const CABECERAS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'content-type, x-dispositivo',
  'Access-Control-Expose-Headers': 'x-recorte-transparencia, x-recorte-ancho, x-recorte-alto',
  'Access-Control-Max-Age': '86400'
};

const json = (cuerpo, estado = 200) =>
  new Response(JSON.stringify(cuerpo), {
    status: estado,
    headers: { ...CABECERAS, 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' }
  });

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: CABECERAS });

    if (url.pathname === '/salud') {
      return json({
        ok: true,
        modelo: env.MODELO,
        simulado: env.SIMULADO === '1' || !env.AI,
        cupoDia: Number(env.MAX_DIA || 30)
      });
    }

    if (url.pathname === '/quitar-fondo') {
      if (request.method !== 'POST') return json({ error: 'Usa POST.' }, 405);

      const tipoFondo = request.headers.get('Content-Type') || '';
      if (!tipoFondo.includes('multipart/form-data')) {
        return json({ error: 'Manda la foto en multipart/form-data, en el campo «image».' }, 415);
      }

      // El token es del intermediario: si falta, se dice qué hacer y no se llama a ApyHub.
      const token = (env.APY_TOKEN || '').trim();
      if (!token) {
        return json(
          {
            error:
              'Falta APY_TOKEN en el Worker. Se pone una vez con «npx wrangler secret put APY_TOKEN».'
          },
          503
        );
      }

      let formulario;
      try {
        formulario = await request.formData();
      } catch {
        return json({ error: 'El formulario no se entiende.' }, 400);
      }

      const revision = await leerImagen(formulario.get('image'));
      if (!revision.ok) return json({ error: revision.error }, revision.codigo);

      const recorte = await pedirRecorte(revision, {
        token,
        fetchImpl: env.FETCH_RECORTE || fetch
      });

      if (!recorte.ok) return json({ error: recorte.error }, recorte.codigo);

      return new Response(recorte.png, {
        status: 200,
        headers: {
          ...CABECERAS,
          'Content-Type': 'image/png',
          'Content-Disposition': 'attachment; filename="recorte.png"',
          'Cache-Control': 'no-store',
          'X-Recorte-Transparencia': recorte.canalAlfa ? 'si' : 'no',
          'X-Recorte-Ancho': String(recorte.ancho),
          'X-Recorte-Alto': String(recorte.alto)
        }
      });
    }

    if (url.pathname !== '/retoque') return json({ error: 'No existe esa dirección.' }, 404);
    if (request.method !== 'POST') return json({ error: 'Usa POST.' }, 405);

    const tipo = request.headers.get('Content-Type') || '';
    if (!tipo.includes('application/json')) {
      return json({ error: 'Manda JSON con la foto en base64 y la petición.' }, 415);
    }

    let cuerpo;
    try {
      cuerpo = await request.json();
    } catch {
      return json({ error: 'El JSON no se entiende.' }, 400);
    }

    const revision = validarPeticion(cuerpo);
    if (!revision.ok) return json({ error: revision.error }, revision.codigo);

    const maximo = Math.max(1, Number(env.MAX_DIA || 30));
    const dia = diaUtc();
    const quien = identidad(request, env.SAL || 'obra');
    const clave = claveCupo(env.SAL || 'obra', quien, dia);
    const cache = caches.default;

    // Contador por dispositivo y día. Cache API basta para un proxy de un solo usuario;
    // si algún día hace falta rigor, se cambia por KV o D1 sin tocar la app.
    const guardado = await cache.match(clave);
    const usadas = guardado ? Number(await guardado.text()) || 0 : 0;
    if (cupoAgotado(usadas, maximo)) {
      return json({ error: `Has llegado al límite de ${maximo} imágenes por hoy.` }, 429);
    }

    const simulado = env.SIMULADO === '1' || !env.AI;
    const inicio = Date.now();

    if (simulado) {
      return json({
        imagen: bytesABase64(revision.foto),
        simulado: true,
        ms: Date.now() - inicio,
        modelo: 'simulado',
        quedan: Math.max(0, maximo - usadas - 1)
      });
    }

    try {
      // El modelo SOLO acepta multipart, y el binding no traga un FormData suelto:
      // hay que serializarlo y pasarle el cuerpo y el content-type con su boundary.
      // (Documentado así por Cloudflare; si se pasa el FormData a pelo, el modelo falla.)
      const tamano = tamanoSalida(revision.medida);
      const formulario = new FormData();
      formulario.append('prompt', revision.prompt);
      formulario.append(
        'input_image_0',
        new Blob([revision.foto], { type: 'image/jpeg' }),
        'habitacion.jpg'
      );
      formulario.append('width', String(tamano.ancho));
      formulario.append('height', String(tamano.alto));

      const serializado = new Response(formulario);
      const respuesta = await env.AI.run(env.MODELO, {
        multipart: {
          body: serializado.body,
          contentType: serializado.headers.get('content-type')
        }
      });
      const imagen = normalizarImagen(respuesta);
      if (!imagen) return json({ error: 'El modelo no ha devuelto ninguna imagen.' }, 502);

      await cache.put(
        clave,
        new Response(String(usadas + 1), { headers: { 'Cache-Control': 'max-age=172800' } })
      );

      return json({
        imagen,
        simulado: false,
        ms: Date.now() - inicio,
        modelo: env.MODELO,
        quedan: Math.max(0, maximo - usadas - 1)
      });
    } catch (error) {
      const mensaje = String(error?.message || error);
      console.error('fallo del modelo', mensaje);
      return json(
        {
          error: mensaje.includes('neurons')
            ? 'Se ha agotado el cupo gratuito de Cloudflare por hoy.'
            : 'El modelo no ha podido con esta foto. Prueba otra vez o baja la exigencia.'
        },
        502
      );
    }
  }
};

export { LIMITE_FOTO, base64ABytes };
