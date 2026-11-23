import { LIMITE_FOTO, base64ABytes } from './logica.js';
import { leerConfig } from './config.js';
import { consultarCupo, procesarRetoque } from './servicio.js';
import { CupoGlobal } from './proteccion.js';

const CABECERAS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'content-type, authorization, x-dispositivo',
  'Access-Control-Max-Age': '86400'
};

const json = (cuerpo, estado = 200) =>
  new Response(JSON.stringify(cuerpo), {
    status: estado,
    headers: { ...CABECERAS, 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' }
  });

export async function manejar(request, env = {}, deps = {}) {
  const url = new URL(request.url);
  if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: CABECERAS });

  if (url.pathname === '/salud') {
    const config = leerConfig(env);
    return json({
      ok: true,
      modelo: config.modelo,
      simulado: env.SIMULADO === '1',
      proteccionActiva: config.activa,
      // Solo es cierto con protección completa y sin simulación: así se puede comprobar
      // de un vistazo si un despliegue puede gastar.
      serviciosDePagoActivos: config.activa && env.SIMULADO !== '1'
    });
  }

  if (url.pathname === '/quitar-fondo') {
    if (request.method !== 'POST') return json({ error: 'Usa POST.' }, 405);
    // Sigue fuera de servicio: no tiene identidad ni cuota verificadas.
    return json({ error: 'Recorte desactivado: falta protección de acceso y gasto.' }, 503);
  }

  if (url.pathname === '/retoque') {
    if (request.method !== 'POST') return json({ error: 'Usa POST.' }, 405);
    const resultado = await procesarRetoque(request, env, deps);
    return json(resultado.cuerpo, resultado.estado);
  }

  if (url.pathname === '/cupo') {
    if (request.method !== 'GET') return json({ error: 'Usa GET.' }, 405);
    const resultado = await consultarCupo(request, env, deps);
    return json(resultado.cuerpo, resultado.estado);
  }

  return json({ error: 'No existe esa dirección.' }, 404);
}

export default {
  fetch: (request, env) => manejar(request, env)
};

export { CupoGlobal, LIMITE_FOTO, base64ABytes };
