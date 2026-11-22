// Prueba el recorte de fondo (ruta /quitar-fondo) sin red ni cuenta de ApyHub.
// Ejecutar: node --test worker/prueba-fondo.mjs
import test from 'node:test';
import assert from 'node:assert/strict';
import zlib from 'node:zlib';

// Estas pruebas ejercitan las funciones del proveedor directamente: la ruta pública
// está bloqueada hasta contar con control de acceso y gasto fiable.
const { APY_ENDPOINT, analizarPng, leerImagen, pedirRecorte } = await import('./src/fondo.js');
const worker = { async fetch(request, env) {
  const json = (body, status) => new Response(JSON.stringify(body), { status });
  if (!(request.headers.get('Content-Type') || '').includes('multipart/form-data')) return json({ error: 'Se requiere multipart' }, 415);
  if (!env.APY_TOKEN) return json({ error: 'Falta APY_TOKEN' }, 503);
  const revision = await leerImagen((await request.formData()).get('image'));
  if (!revision.ok) return json({ error: revision.error }, revision.codigo);
  const recorte = await pedirRecorte(revision, { token: env.APY_TOKEN, fetchImpl: env.FETCH_RECORTE });
  if (!recorte.ok) return json({ error: recorte.error }, recorte.codigo);
  return new Response(recorte.png, { headers: {
    'Content-Type': 'image/png', 'X-Recorte-Transparencia': recorte.canalAlfa ? 'si' : 'no',
    'X-Recorte-Ancho': String(recorte.ancho), 'X-Recorte-Alto': String(recorte.alto)
  } });
} };

// ── PNG de muestra, montado a mano ────────────────────────────────────────────

const CRC = new Int32Array(256);
for (let n = 0; n < 256; n += 1) {
  let c = n;
  for (let k = 0; k < 8; k += 1) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  CRC[n] = c;
}

function crc32(datos) {
  let c = -1;
  for (const byte of datos) c = CRC[(c ^ byte) & 0xff] ^ (c >>> 8);
  return (c ^ -1) >>> 0;
}

function trozo(tipo, datos) {
  const largo = Buffer.alloc(4);
  largo.writeUInt32BE(datos.length);
  const tipoBytes = Buffer.from(tipo, 'ascii');
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([tipoBytes, datos])));
  return Buffer.concat([largo, tipoBytes, datos, crc]);
}

/** PNG de ancho×alto con un píxel transparente (alfa = 0) si `conAlfa`. */
function pngDeMuestra({ ancho = 4, alto = 4, conAlfa = true } = {}) {
  const canales = conAlfa ? 4 : 3;
  const filas = [];
  for (let y = 0; y < alto; y += 1) {
    const fila = Buffer.alloc(1 + ancho * canales);
    fila[0] = 0; // sin filtro: los píxeles quedan legibles tal cual
    for (let x = 0; x < ancho; x += 1) {
      const i = 1 + x * canales;
      fila[i] = 200;
      fila[i + 1] = 120;
      fila[i + 2] = 60;
      if (conAlfa) fila[i + 3] = x === 0 && y === 0 ? 0 : 255;
    }
    filas.push(fila);
  }

  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(ancho, 0);
  ihdr.writeUInt32BE(alto, 4);
  ihdr[8] = 8; // bits por canal
  ihdr[9] = conAlfa ? 6 : 2; // 6 = RGBA, 2 = RGB
  ihdr[10] = 0;
  ihdr[11] = 0;
  ihdr[12] = 0;
  const firma = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

  // Un tEXt de relleno lo deja en un tamaño de foto de verdad y no toca los píxeles.
  return Buffer.concat([
    firma,
    trozo('IHDR', ihdr),
    trozo('tEXt', Buffer.alloc(512, 0x41)),
    trozo('IDAT', zlib.deflateSync(Buffer.concat(filas))),
    trozo('IEND', Buffer.alloc(0))
  ]);
}

/** Lee los píxeles del PNG de muestra (sin filtro) y cuenta los que son transparentes. */
function pixelesTransparentes(png, ancho, alto, canales) {
  const inicio = png.indexOf(Buffer.from('IDAT', 'ascii')) + 4;
  const largo = png.readUInt32BE(inicio - 8);
  const filas = zlib.inflateSync(png.subarray(inicio, inicio + largo));
  const anchoFila = 1 + ancho * canales;
  assert.equal(filas.length, anchoFila * alto);
  let transparentes = 0;
  for (let y = 0; y < alto; y += 1) {
    for (let x = 0; x < ancho; x += 1) {
      const alfa = filas[y * anchoFila + 1 + x * canales + 3];
      if (alfa === 0) transparentes += 1;
    }
  }
  return transparentes;
}

const entorno = (extra = {}) => ({ MODELO: 'modelo', MAX_DIA: '2', SAL: 'prueba', ...extra });

function subir(bytes, { nombre = 'foto.png', tipo = 'image/png', cabeceras = {} } = {}) {
  const forma = new FormData();
  forma.append('image', new Blob([bytes], { type: tipo }), nombre);
  return new Request('https://obra-ia.prueba/quitar-fondo', {
    method: 'POST',
    headers: { 'X-Dispositivo': 'dispositivo-1', ...cabeceras },
    body: forma
  });
}

function apyhubResponde(png, { estado = 200, capturado } = {}) {
  return async (url, init) => {
    if (capturado) {
      capturado.url = url;
      capturado.init = init;
    }
    return new Response(estado === 200 ? png : '{"error":"sin saldo"}', {
      status: estado,
      headers: { 'Content-Type': estado === 200 ? 'image/png' : 'application/json' }
    });
  };
}

test('el PNG de muestra tiene píxeles transparentes', () => {
  const png = pngDeMuestra();
  const datos = analizarPng(png);
  assert.equal(datos.ancho, 4);
  assert.equal(datos.alto, 4);
  assert.equal(datos.canalAlfa, true);
  assert.equal(pixelesTransparentes(png, 4, 4, 4), 1, 'solo el píxel 0,0 es transparente');
});

test('un PNG opaco se detecta como sin transparencia', () => {
  assert.equal(analizarPng(pngDeMuestra({ conAlfa: false })).canalAlfa, false);
});

test('sin APY_TOKEN no se llama a ApyHub y se explica qué hacer', async () => {
  let llamado = false;
  const res = await worker.fetch(
    subir(pngDeMuestra()),
    entorno({ FETCH_RECORTE: async () => { llamado = true; } })
  );
  const cuerpo = await res.json();
  assert.equal(res.status, 503);
  assert.match(cuerpo.error, /APY_TOKEN/);
  assert.equal(llamado, false);
});

test('sin multipart se rechaza con 415', async () => {
  const res = await worker.fetch(
    new Request('https://x/quitar-fondo', { method: 'POST', body: 'hola' }),
    entorno({ APY_TOKEN: 'token-de-prueba' })
  );
  assert.equal(res.status, 415);
});

test('un archivo que no es imagen no gasta átomos', async () => {
  let llamado = false;
  const res = await worker.fetch(
    subir(new Uint8Array(4000), { nombre: 'nota.png', tipo: 'image/png' }),
    entorno({ APY_TOKEN: 'token-de-prueba', FETCH_RECORTE: async () => { llamado = true; } })
  );
  assert.equal(res.status, 400);
  assert.equal(llamado, false);
});

test('usa el endpoint documentado, manda apy-token y el campo image', async () => {
  const png = pngDeMuestra();
  const capturado = {};
  const res = await worker.fetch(
    subir(png),
    entorno({ APY_TOKEN: 'token-de-prueba', FETCH_RECORTE: apyhubResponde(png, { capturado }) })
  );

  assert.equal(res.status, 200);
  assert.equal(capturado.url, APY_ENDPOINT);
  assert.equal(capturado.init.method, 'POST');
  assert.equal(capturado.init.headers['apy-token'], 'token-de-prueba');
  assert.ok(capturado.init.body instanceof FormData);
  assert.ok(capturado.init.body.get('image') instanceof Blob);

  assert.equal(res.headers.get('Content-Type'), 'image/png');
  assert.equal(res.headers.get('X-Recorte-Transparencia'), 'si');
  assert.equal(res.headers.get('X-Recorte-Ancho'), '4');
  const devuelto = new Uint8Array(await res.arrayBuffer());
  assert.deepEqual(devuelto, new Uint8Array(png));
});

test('si ApyHub rechaza el token se responde 502 con un mensaje útil', async () => {
  const res = await worker.fetch(
    subir(pngDeMuestra()),
    entorno({ APY_TOKEN: 'token-malo', FETCH_RECORTE: apyhubResponde(null, { estado: 401 }) })
  );
  const cuerpo = await res.json();
  assert.equal(res.status, 502);
  assert.match(cuerpo.error, /token de ApyHub/i);
});

test('si ApyHub devuelve algo que no es PNG no se entrega como recorte', async () => {
  const res = await worker.fetch(
    subir(pngDeMuestra()),
    entorno({
      APY_TOKEN: 'token-de-prueba',
      FETCH_RECORTE: async () => new Response('no soy una imagen', { status: 200 })
    })
  );
  const cuerpo = await res.json();
  assert.equal(res.status, 502);
  assert.match(cuerpo.error, /PNG/);
});

test('si la red falla se avisa sin romper la app', async () => {
  const res = await worker.fetch(
    subir(pngDeMuestra()),
    entorno({
      APY_TOKEN: 'token-de-prueba',
      FETCH_RECORTE: async () => { throw new Error('ECONNRESET'); }
    })
  );
  const cuerpo = await res.json();
  assert.equal(res.status, 502);
  assert.match(cuerpo.error, /no ha contestado/i);
});

test('una foto de más de 8 MB se rechaza antes de llamar a ApyHub', async () => {
  const grande = new Uint8Array(9 * 1024 * 1024);
  grande.set([0x89, 0x50, 0x4e, 0x47]);
  let llamado = false;
  const res = await worker.fetch(
    subir(grande),
    entorno({ APY_TOKEN: 'token-de-prueba', FETCH_RECORTE: async () => { llamado = true; } })
  );
  assert.equal(res.status, 413);
  assert.equal(llamado, false);
});
