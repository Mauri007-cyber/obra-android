// Lógica sin dependencias del runtime: se prueba con `node --test`.
// Todo lo que puede estar mal en una petición se comprueba aquí, no en el handler.

export const LIMITE_FOTO = 4 * 1024 * 1024; // 4 MB de JPEG: de sobra para 512 px
export const MIN_FOTO = 2 * 1024;
/**
 * FLUX.2 [klein] solo acepta imágenes de entrada menores de 512×512.
 * Si se manda una foto de móvil tal cual, el modelo falla con un error que no dice nada.
 * Se comprueba aquí para poder contestar qué pasa y qué hacer.
 */
export const MAX_ENTRADA = 512;
export const MIN_PROMPT = 10;
export const MAX_PROMPT = 1200;

/** Devuelve {ok:true, foto} o {ok:false, codigo, error}. */
export function validarPeticion(cuerpo) {
  if (cuerpo === null || typeof cuerpo !== 'object') {
    return { ok: false, codigo: 400, error: 'El cuerpo tiene que ser un objeto JSON.' };
  }

  const prompt = typeof cuerpo.prompt === 'string' ? cuerpo.prompt.trim() : '';
  if (prompt.length < MIN_PROMPT) {
    return { ok: false, codigo: 400, error: 'Falta decir qué cambiar (mínimo 10 letras).' };
  }
  if (prompt.length > MAX_PROMPT) {
    return { ok: false, codigo: 400, error: `La petición es demasiado larga (máximo ${MAX_PROMPT}).` };
  }

  if (typeof cuerpo.foto !== 'string' || cuerpo.foto.length < 100) {
    return { ok: false, codigo: 400, error: 'Falta la foto.' };
  }
  if (!/^[A-Za-z0-9+/=\r\n]+$/.test(cuerpo.foto)) {
    return { ok: false, codigo: 400, error: 'La foto no viene en base64.' };
  }

  let foto;
  try {
    foto = base64ABytes(cuerpo.foto);
  } catch {
    return { ok: false, codigo: 400, error: 'La foto está corrupta.' };
  }

  if (foto.byteLength < MIN_FOTO) {
    return { ok: false, codigo: 400, error: 'La foto es demasiado pequeña.' };
  }
  if (foto.byteLength > LIMITE_FOTO) {
    return { ok: false, codigo: 413, error: 'La foto es demasiado grande (máximo 4 MB).' };
  }
  if (!esJpegOPng(foto)) {
    return { ok: false, codigo: 400, error: 'La foto tiene que ser JPEG o PNG.' };
  }

  const medida = dimensionesImagen(foto);
  if (medida && (medida.ancho > MAX_ENTRADA || medida.alto > MAX_ENTRADA)) {
    return {
      ok: false,
      codigo: 400,
      error:
        `La foto es de ${medida.ancho}×${medida.alto} y el modelo solo admite hasta ` +
        `${MAX_ENTRADA}×${MAX_ENTRADA}. La app nueva ya la reduce sola; ` +
        'si vienes de otra herramienta, redúcela antes de mandarla.'
    };
  }

  return { ok: true, foto, prompt, medida };
}

export function esJpegOPng(bytes) {
  if (bytes.byteLength < 4) return false;
  const jpeg = bytes[0] === 0xff && bytes[1] === 0xd8;
  const png = bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47;
  return jpeg || png;
}

/**
 * Ancho y alto leyendo la cabecera, sin decodificar la imagen.
 * Devuelve null si el formato no se reconoce: mejor dejar pasar que rechazar de más.
 */
export function dimensionesImagen(bytes) {
  if (!bytes || bytes.byteLength < 24) return null;

  // PNG: IHDR empieza en el byte 16.
  if (bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47) {
    const ancho = ((bytes[16] << 24) | (bytes[17] << 16) | (bytes[18] << 8) | bytes[19]) >>> 0;
    const alto = ((bytes[20] << 24) | (bytes[21] << 16) | (bytes[22] << 8) | bytes[23]) >>> 0;
    if (ancho <= 0 || alto <= 0) return null;
    return { ancho, alto };
  }

  if (bytes[0] !== 0xff || bytes[1] !== 0xd8) return null;

  // JPEG: se recorre hasta el marcador SOF (C0–CF, salvo C4, C8 y CC).
  let i = 2;
  while (i + 9 < bytes.byteLength) {
    if (bytes[i] !== 0xff) {
      i += 1;
      continue;
    }
    const marca = bytes[i + 1];
    const esSof =
      marca >= 0xc0 && marca <= 0xcf && marca !== 0xc4 && marca !== 0xc8 && marca !== 0xcc;
    if (esSof) {
      const alto = (bytes[i + 5] << 8) | bytes[i + 6];
      const ancho = (bytes[i + 7] << 8) | bytes[i + 8];
      if (ancho <= 0 || alto <= 0) return null;
      return { ancho, alto };
    }
    const largo = (bytes[i + 2] << 8) | bytes[i + 3];
    if (largo <= 0) return null;
    i += 2 + largo;
  }
  return null;
}

export function base64ABytes(texto) {
  const limpio = texto.replace(/\s+/g, '');
  const binario = atob(limpio);
  const salida = new Uint8Array(binario.length);
  for (let i = 0; i < binario.length; i += 1) salida[i] = binario.charCodeAt(i);
  return salida;
}

export function bytesABase64(bytes) {
  const vista = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let binario = '';
  const trozo = 0x8000;
  for (let i = 0; i < vista.length; i += trozo) {
    binario += String.fromCharCode.apply(null, vista.subarray(i, i + trozo));
  }
  return btoa(binario);
}

/**
 * Tamaño de la imagen que se le pide al modelo, con el mismo formato que la foto.
 *
 * Si se pide siempre 1024×768, una foto vertical sale deformada. Se mantiene la
 * proporción, en múltiplos de 8 y sin pasar de 1024 (más grande no aporta y cuesta más).
 */
export function tamanoSalida(medida, maximo = 1024) {
  if (!medida || !medida.ancho || !medida.alto) return { ancho: 1024, alto: 768 };

  const escala = Math.min(maximo / medida.ancho, maximo / medida.alto);
  const redondear = (valor) =>
    Math.min(maximo, Math.max(256, Math.round(valor / 8) * 8));

  return {
    ancho: redondear(medida.ancho * escala),
    alto: redondear(medida.alto * escala)
  };
}

/**
 * Los modelos de Cloudflare no contestan igual: FLUX devuelve {image: base64},
 * la familia Stable Diffusion devuelve bytes. Se normaliza aquí y ya está.
 */
export function normalizarImagen(respuesta) {
  if (!respuesta) return null;
  if (typeof respuesta === 'string') return respuesta;
  if (typeof respuesta.image === 'string') return respuesta.image;
  if (respuesta.image instanceof ArrayBuffer) return bytesABase64(new Uint8Array(respuesta.image));
  if (ArrayBuffer.isView(respuesta)) return bytesABase64(respuesta);
  if (respuesta instanceof ArrayBuffer) return bytesABase64(new Uint8Array(respuesta));
  if (typeof respuesta.result?.image === 'string') return respuesta.result.image;
  if (typeof respuesta.imagen === 'string') return respuesta.imagen;
  return null;
}

export function claveCupo(sal, dispositivo, dia) {
  return `https://cupo.local/${sal}/${dispositivo}/${dia}`;
}

/** Sin cabecera de dispositivo se cuenta por IP: peor, pero nunca deja pasar de largo. */
export function identidad(request, sal) {
  const dispositivo = (request.headers.get('X-Dispositivo') || '').trim();
  const ip = request.headers.get('CF-Connecting-IP') || 'sin-ip';
  return dispositivo ? `d-${dispositivo.slice(0, 64)}` : `ip-${ip}`;
}

export function diaUtc(ahora = new Date()) {
  return ahora.toISOString().slice(0, 10);
}

export function cupoAgotado(usadas, maximo) {
  return usadas >= maximo;
}
