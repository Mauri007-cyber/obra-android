// Camino completo de una petición de pago, en un solo sitio y en orden estricto:
// configuración -> identidad -> validación -> reserva de cupo -> modelo -> respuesta.
//
// Nada de esto se activa mientras PROTECCION_ACTIVA no esté en 1. Con la protección
// activa, ninguna petición llega al proveedor sin token válido y sin reserva previa.
import { bytesABase64, validarPeticion } from './logica.js';
import { leerConfig } from './config.js';
import { verificarIdentidad } from './proteccion.js';
import { editarConFlux } from './flux.js';

export const AVISO_BLOQUEO =
  'IA desactivada: falta verificar el acceso y el gasto. No se ha llamado a ningún proveedor.';

export const AVISO_CUPO_AGOTADO =
  'Se ha agotado el cupo de generaciones. No se ha llamado al modelo.';

const respuesta = (estado, cuerpo) => ({ estado, cuerpo });

function tokenDe(request) {
  const cabecera = request.headers.get('Authorization') || '';
  if (!cabecera.startsWith('Bearer ')) return '';
  return cabecera.slice(7).trim();
}

async function leerPeticion(request) {
  const tipo = request.headers.get('Content-Type') || '';
  if (!tipo.includes('application/json')) {
    return { ok: false, codigo: 415, error: 'Manda JSON con la foto en base64 y la petición.' };
  }
  let cuerpo;
  try {
    cuerpo = await request.json();
  } catch {
    return { ok: false, codigo: 400, error: 'El JSON no se entiende.' };
  }
  return validarPeticion(cuerpo);
}

function simulacion(revision) {
  return respuesta(200, {
    imagen: bytesABase64(revision.foto),
    simulado: true,
    ms: 0,
    modelo: 'simulado'
  });
}

function cupoDe(env, deps) {
  return deps.cupo ? deps.cupo(env) : env.CUPO;
}

function invocadorDe(env, deps) {
  if (deps.invocarModelo) return (modelo, opciones) => deps.invocarModelo(env, modelo, opciones);
  return (modelo, opciones) => env.AI.run(modelo, opciones);
}

async function identidadDe(request, config) {
  const token = tokenDe(request);
  if (!token) return { ok: false, estado: 401, error: 'Falta el token de acceso.' };
  const sub = await verificarIdentidad(token, config);
  if (!sub) return { ok: false, estado: 401, error: 'Token inválido o caducado.' };
  return { ok: true, sub };
}

export async function procesarRetoque(request, env = {}, deps = {}) {
  const config = leerConfig(env);

  // Sin protección activa solo existe la simulación: devuelve la foto y no gasta nada.
  if (!config.activa) {
    if (env.SIMULADO !== '1') {
      return respuesta(503, { error: AVISO_BLOQUEO, motivo: config.motivo });
    }
    const revision = await leerPeticion(request);
    if (!revision.ok) return respuesta(revision.codigo, { error: revision.error });
    return simulacion(revision);
  }

  // Con protección activa, la identidad se comprueba antes de leer la foto.
  const identidad = await identidadDe(request, config);
  if (!identidad.ok) return respuesta(identidad.estado, { error: identidad.error });

  const revision = await leerPeticion(request);
  if (!revision.ok) return respuesta(revision.codigo, { error: revision.error });

  // Modo protegido sin gasto: sirve para probar la app contra un servicio que autentica.
  if (env.SIMULADO === '1') return simulacion(revision);

  const cupo = cupoDe(env, deps);
  if (!cupo || typeof cupo.idFromName !== 'function') {
    return respuesta(503, { error: 'La cuota no está configurada: no se llama al modelo.' });
  }

  // UNA sola instancia para todo el servicio: el límite es global de verdad.
  const contador = cupo.get(cupo.idFromName('global'));
  let reserva;
  try {
    reserva = await contador.fetch('https://cupo.local/reservar', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sub: identidad.sub })
    });
  } catch {
    return respuesta(503, { error: 'No se pudo reservar cupo: no se llama al modelo.' });
  }

  if (reserva.status === 429) return respuesta(429, { error: AVISO_CUPO_AGOTADO });
  if (reserva.status !== 200) {
    return respuesta(503, { error: 'No se pudo reservar cupo: no se llama al modelo.' });
  }

  const datos = await reserva.json();
  const restante = {
    global: Math.max(0, datos.global.maximo - datos.global.usadas),
    usuario: Math.max(0, datos.usuario.maximo - datos.usuario.usadas)
  };

  try {
    const imagen = await editarConFlux(
      invocadorDe(env, deps),
      revision.foto,
      revision.prompt,
      revision.medida,
      config.modelo
    );
    return respuesta(200, { imagen, modelo: config.modelo, restante });
  } catch {
    // El fallo también cuenta: no se devuelve la reserva, para que un reintento en bucle
    // no pueda gastar sin tope. El detalle del proveedor no se expone al cliente.
    return respuesta(502, {
      error: 'El modelo no ha devuelto una imagen. La llamada cuenta para el cupo.',
      restante
    });
  }
}

/** Estado del contador para el usuario autenticado. No consume cupo. */
export async function consultarCupo(request, env = {}, deps = {}) {
  const config = leerConfig(env);
  if (!config.activa) return respuesta(503, { error: AVISO_BLOQUEO, motivo: config.motivo });

  const identidad = await identidadDe(request, config);
  if (!identidad.ok) return respuesta(identidad.estado, { error: identidad.error });

  const cupo = cupoDe(env, deps);
  if (!cupo || typeof cupo.idFromName !== 'function') {
    return respuesta(503, { error: 'La cuota no está configurada.' });
  }

  const contador = cupo.get(cupo.idFromName('global'));
  const estado = await contador.fetch(
    `https://cupo.local/estado?sub=${encodeURIComponent(identidad.sub)}`
  );
  if (estado.status !== 200) return respuesta(503, { error: 'No se pudo leer el cupo.' });
  return respuesta(200, await estado.json());
}
