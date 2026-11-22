import { LIMITE_FOTO, base64ABytes, bytesABase64, validarPeticion } from './logica.js';

const CABECERAS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'content-type, x-dispositivo',
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
      return json({ ok: true, modelo: env.MODELO, simulado: env.SIMULADO === '1', serviciosDePagoActivos: false });
    }

    if (url.pathname === '/quitar-fondo') {
      if (request.method !== 'POST') return json({ error: 'Usa POST.' }, 405);
      // Bloqueo deliberado aunque el secreto esté configurado: el endpoint público
      // no tiene autenticación ni un límite global y atómico de gasto.
      return json({ error: 'Recorte desactivado: falta protección de acceso y gasto.' }, 503);
    }

    if (url.pathname !== '/retoque') return json({ error: 'No existe esa dirección.' }, 404);
    if (request.method !== 'POST') return json({ error: 'Usa POST.' }, 405);
    if (env.SIMULADO !== '1') {
      return json({ error: 'IA desactivada: falta protección de acceso y gasto.' }, 503);
    }

    const tipo = request.headers.get('Content-Type') || '';
    if (!tipo.includes('application/json')) return json({ error: 'Manda JSON con la foto en base64 y la petición.' }, 415);

    let cuerpo;
    try {
      cuerpo = await request.json();
    } catch {
      return json({ error: 'El JSON no se entiende.' }, 400);
    }
    const revision = validarPeticion(cuerpo);
    if (!revision.ok) return json({ error: revision.error }, revision.codigo);
    return json({ imagen: bytesABase64(revision.foto), simulado: true, ms: 0, modelo: 'simulado' });
  }
};

export { LIMITE_FOTO, base64ABytes };
