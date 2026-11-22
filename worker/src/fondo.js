// Recorte de fondo con ApyHub. Sin dependencias del runtime: se prueba con `node --test`.
//
// ApyHub cobra 1.000 átomos por imagen y el token es del intermediario (este Worker),
// así que la app y la vista previa nunca lo ven: solo mandan la foto aquí.

export const APY_ENDPOINT =
  'https://api.eu.apyhub.com/apyhub/remove-background-from-images/multi-part/download';

export const LIMITE_RECORTE = 8 * 1024 * 1024; // 8 MB: una foto de móvil entra de sobra
export const MINIMO_RECORTE = 256; // por debajo de esto no es una foto, es un fallo de subida

const FIRMA_PNG = [0x89, 0x50, 0x4e, 0x47];

export function esPng(bytes) {
  return FIRMA_PNG.every((valor, i) => bytes[i] === valor);
}

export function esJpeg(bytes) {
  return bytes[0] === 0xff && bytes[1] === 0xd8;
}

/**
 * Comprueba el archivo que llega en el formulario antes de gastar átomos.
 * Devuelve {ok:true, bytes, tipo, nombre} o {ok:false, codigo, error}.
 */
export async function leerImagen(archivo) {
  if (!archivo || typeof archivo.arrayBuffer !== 'function') {
    return { ok: false, codigo: 400, error: 'Falta la imagen: mándala en el campo «image».' };
  }

  let bytes;
  try {
    bytes = new Uint8Array(await archivo.arrayBuffer());
  } catch {
    return { ok: false, codigo: 400, error: 'La imagen no se ha podido leer.' };
  }

  if (bytes.byteLength === 0) {
    return { ok: false, codigo: 400, error: 'La imagen está vacía.' };
  }
  if (bytes.byteLength < MINIMO_RECORTE) {
    return { ok: false, codigo: 400, error: 'La imagen es demasiado pequeña para recortarla.' };
  }
  if (bytes.byteLength > LIMITE_RECORTE) {
    return { ok: false, codigo: 413, error: 'La imagen es demasiado grande (máximo 8 MB).' };
  }

  const png = esPng(bytes);
  if (!png && !esJpeg(bytes)) {
    return { ok: false, codigo: 400, error: 'La imagen tiene que ser JPEG o PNG.' };
  }

  const tipo = png ? 'image/png' : 'image/jpeg';
  const nombre = typeof archivo.name === 'string' && archivo.name ? archivo.name : png ? 'foto.png' : 'foto.jpg';
  return { ok: true, bytes, tipo, nombre };
}

/** El campo documentado por ApyHub es `image`; el nombre del archivo es decorativo. */
export function formularioApyhub(bytes, tipo, nombre) {
  const formulario = new FormData();
  formulario.append('image', new Blob([bytes], { type: tipo }), nombre || 'foto');
  return formulario;
}

/**
 * Llama a ApyHub y devuelve el PNG ya comprobado.
 * `fetchImpl` se inyecta en las pruebas: el Worker usa fetch y no se toca.
 */
export async function pedirRecorte(revision, { token, fetchImpl = fetch, endpoint = APY_ENDPOINT } = {}) {
  let respuesta;
  try {
    respuesta = await fetchImpl(endpoint, {
      method: 'POST',
      headers: { 'apy-token': token }, // sin Content-Type: fetch pone el boundary del multipart
      body: formularioApyhub(revision.bytes, revision.tipo, revision.nombre)
    });
  } catch (error) {
    console.error('apyhub no contesta', String(error?.message || error));
    return {
      ok: false,
      codigo: 502,
      error: 'ApyHub no ha contestado. Prueba otra vez en un momento.'
    };
  }

  if (!respuesta.ok) {
    const detalle = await leerDetalle(respuesta);
    console.error('apyhub', respuesta.status, detalle);
    return { ok: false, codigo: 502, error: mensajeApyhub(respuesta.status) };
  }

  let png;
  try {
    png = new Uint8Array(await respuesta.arrayBuffer());
  } catch {
    return { ok: false, codigo: 502, error: 'ApyHub ha contestado algo que no es una imagen.' };
  }

  const datos = analizarPng(png);
  if (!datos) {
    return {
      ok: false,
      codigo: 502,
      error: 'ApyHub no ha devuelto un PNG. No se ha tocado la foto original.'
    };
  }

  return { ok: true, png, ...datos };
}

/** Mensajes de obrar: qué ha pasado y qué puede hacer quien lo usa. */
export function mensajeApyhub(estado) {
  if (estado === 401 || estado === 403) {
    return 'El token de ApyHub no vale o no tiene permiso. Revisa APY_TOKEN en el Worker.';
  }
  if (estado === 402 || estado === 429) {
    return 'ApyHub no tiene saldo o has ido demasiado rápido. Espera un poco o revisa los átomos.';
  }
  if (estado === 413) return 'ApyHub ha rechazado la foto por tamaño. Prueba con una más pequeña.';
  return 'ApyHub no ha podido con esta foto. Prueba otra vez o con otra imagen.';
}

async function leerDetalle(respuesta) {
  try {
    const texto = await respuesta.text();
    return texto.slice(0, 300);
  } catch {
    return '';
  }
}

/**
 * Lee la cabecera del PNG sin decodificar píxeles: medidas, tipo de color y si lleva alfa.
 * Con eso se puede avisar de que el recorte trae transparencia y no prometer bordes perfectos.
 */
export function analizarPng(bytes) {
  if (!bytes || bytes.byteLength < 33 || !esPng(bytes)) return null;
  if (bytes[12] !== 0x49 || bytes[13] !== 0x48 || bytes[14] !== 0x44 || bytes[15] !== 0x52) return null;

  const ancho = leerUint32(bytes, 16);
  const alto = leerUint32(bytes, 20);
  const profundidad = bytes[24];
  const tipoColor = bytes[25];
  if (ancho <= 0 || alto <= 0) return null;

  // 4 = gris+alfa, 6 = RGBA; 3 = paleta, que solo es transparente si trae chunk tRNS.
  let transparencia = tipoColor === 4 || tipoColor === 6;
  if (tipoColor === 3) transparencia = tieneTrozo(bytes, 'tRNS');

  return { ancho, alto, profundidad, tipoColor, canalAlfa: transparencia };
}

function tieneTrozo(bytes, nombre) {
  const buscado = [nombre.charCodeAt(0), nombre.charCodeAt(1), nombre.charCodeAt(2), nombre.charCodeAt(3)];
  for (let i = 8; i + 8 <= bytes.byteLength; ) {
    const largo = leerUint32(bytes, i);
    if (largo < 0 || i + 12 + largo > bytes.byteLength) return false;
    if (buscado.every((valor, j) => bytes[i + 4 + j] === valor)) return true;
    i += 12 + largo;
  }
  return false;
}

function leerUint32(bytes, i) {
  return ((bytes[i] << 24) | (bytes[i + 1] << 16) | (bytes[i + 2] << 8) | bytes[i + 3]) >>> 0;
}
