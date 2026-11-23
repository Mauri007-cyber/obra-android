// Configuración de la ruta de pago. Todo falla cerrado: si algo no está completo,
// la IA se queda apagada. Ninguna clave ni emisor está en el repositorio.

/** Límites del contador. Un error de tecleo no debe convertirse en gasto sin tope. */
export const MAX_TOTAL_PERMITIDO = 100000;
export const MAX_USUARIO_PERMITIDO = 500;
export const MAX_USUARIOS = 200;

export const MOTIVO_SIN_ACTIVAR =
  'PROTECCION_ACTIVA no está en 1: la ruta de pago sigue cerrada aunque el resto esté configurado.';

export function limitesDe(env = {}) {
  const global = Number(env.MAX_LLAMADAS_TOTAL);
  const usuario = Number(env.MAX_LLAMADAS_USUARIO);
  if (!Number.isSafeInteger(global) || global < 1 || global > MAX_TOTAL_PERMITIDO) return null;
  if (!Number.isSafeInteger(usuario) || usuario < 1 || usuario > MAX_USUARIO_PERMITIDO) return null;
  if (usuario > global) return null;
  return { global, usuario };
}

/**
 * Devuelve { activa:false, motivo } o la configuración completa y validada.
 * `motivo` es para diagnóstico interno; nunca incluye el valor de un secreto.
 */
export function leerConfig(env = {}) {
  if (env.PROTECCION_ACTIVA !== '1') {
    return { activa: false, motivo: MOTIVO_SIN_ACTIVAR, modelo: modeloDe(env) };
  }

  const cerrada = (motivo) => ({ activa: false, motivo, modelo: modeloDe(env) });

  const issuer = typeof env.JWT_ISSUER === 'string' ? env.JWT_ISSUER.trim() : '';
  if (!/^https:\/\/[^\s]+$/.test(issuer) || issuer.length > 300) {
    return cerrada('JWT_ISSUER debe ser una URL https del proveedor de identidad.');
  }

  const audience = typeof env.JWT_AUDIENCE === 'string' ? env.JWT_AUDIENCE.trim() : '';
  if (!audience || audience.length > 128) {
    return cerrada('JWT_AUDIENCE no está configurada.');
  }

  const permitidos = (typeof env.USUARIOS_PERMITIDOS === 'string' ? env.USUARIOS_PERMITIDOS : '')
    .split(',')
    .map((valor) => valor.trim())
    .filter(Boolean);
  if (!permitidos.length || permitidos.length > MAX_USUARIOS) {
    return cerrada('USUARIOS_PERMITIDOS debe tener entre 1 y 200 cuentas.');
  }
  if (permitidos.some((sub) => sub.length > 256)) {
    return cerrada('Hay un identificador de USUARIOS_PERMITIDOS demasiado largo.');
  }

  let clave;
  try {
    clave = JSON.parse(env.JWT_CLAVE);
  } catch {
    return cerrada('JWT_CLAVE no es un JWK público en JSON.');
  }
  if (
    !clave || typeof clave !== 'object' || clave.kty !== 'EC' || clave.crv !== 'P-256' ||
    typeof clave.x !== 'string' || typeof clave.y !== 'string' || clave.d !== undefined
  ) {
    return cerrada('JWT_CLAVE debe ser un JWK público EC P-256 (sin la parte privada "d").');
  }

  const limites = limitesDe(env);
  if (!limites) {
    return cerrada(
      'MAX_LLAMADAS_TOTAL y MAX_LLAMADAS_USUARIO deben ser enteros válidos ' +
        `(total hasta ${MAX_TOTAL_PERMITIDO}, usuario hasta ${MAX_USUARIO_PERMITIDO} y nunca mayor que el total).`
    );
  }

  return {
    activa: true,
    issuer,
    audience,
    permitidos,
    clave,
    maxGlobal: limites.global,
    maxUsuario: limites.usuario,
    modelo: modeloDe(env)
  };
}

export function modeloDe(env = {}) {
  return typeof env.MODELO === 'string' && env.MODELO.trim()
    ? env.MODELO.trim()
    : '@cf/black-forest-labs/flux-2-klein-4b';
}
