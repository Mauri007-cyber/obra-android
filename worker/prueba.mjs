// Pruebas de la lógica del Worker, sin runtime de Cloudflare ni cuenta.
// Ejecutar: node --test worker/prueba.mjs
import test from 'node:test';
import assert from 'node:assert/strict';
import {
  LIMITE_FOTO,
  MAX_ENTRADA,
  base64ABytes,
  bytesABase64,
  claveCupo,
  cupoAgotado,
  diaUtc,
  dimensionesImagen,
  tamanoSalida,
  normalizarImagen,
  validarPeticion
} from './src/logica.js';

// JPEG mínimo: cabecera FFD8FF + relleno hasta pasar el mínimo de 2 KB.
function jpegFalso(bytes = 3000) {
  const datos = new Uint8Array(bytes);
  datos[0] = 0xff;
  datos[1] = 0xd8;
  datos[2] = 0xff;
  datos[3] = 0xe0;
  return datos;
}

const enBase64 = (datos) => bytesABase64(datos);

// JPEG con cabecera SOF0 y las medidas pedidas, para probar el límite del modelo.
function jpegCon(ancho, alto) {
  const datos = new Uint8Array(3000);
  datos[0] = 0xff;
  datos[1] = 0xd8;
  datos[2] = 0xff;
  datos[3] = 0xc0;
  datos[4] = 0x00;
  datos[5] = 0x11;
  datos[6] = 0x08;
  datos[7] = (alto >> 8) & 0xff;
  datos[8] = alto & 0xff;
  datos[9] = (ancho >> 8) & 0xff;
  datos[10] = ancho & 0xff;
  return datos;
}

// PNG con IHDR de las medidas pedidas.
function pngCon(ancho, alto) {
  const datos = new Uint8Array(3000);
  datos.set([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a], 0);
  datos.set([0x00, 0x00, 0x00, 0x0d], 8);
  datos.set([0x49, 0x48, 0x44, 0x52], 12);
  datos[16] = (ancho >>> 24) & 0xff;
  datos[17] = (ancho >>> 16) & 0xff;
  datos[18] = (ancho >>> 8) & 0xff;
  datos[19] = ancho & 0xff;
  datos[20] = (alto >>> 24) & 0xff;
  datos[21] = (alto >>> 16) & 0xff;
  datos[22] = (alto >>> 8) & 0xff;
  datos[23] = alto & 0xff;
  return datos;
}

test('una petición correcta se acepta y devuelve los bytes', () => {
  const r = validarPeticion({
    foto: enBase64(jpegFalso()),
    prompt: 'Pon el suelo de tarima clara y las paredes blancas'
  });
  assert.equal(r.ok, true);
  assert.equal(r.foto.byteLength, 3000);
  assert.match(r.prompt, /tarima/);
});

test('sin foto, con foto falsa o con prompt corto se rechaza', () => {
  assert.equal(validarPeticion({ prompt: 'suelo de tarima claro' }).codigo, 400);
  assert.equal(validarPeticion({ foto: 'no-es-base64!!', prompt: 'suelo de tarima claro' }).codigo, 400);
  assert.equal(validarPeticion({ foto: enBase64(jpegFalso()), prompt: 'corto' }).codigo, 400);
});

test('un archivo que no es JPEG ni PNG se rechaza aunque sea grande', () => {
  const basura = new Uint8Array(3000);
  basura.fill(7);
  const r = validarPeticion({ foto: enBase64(basura), prompt: 'cambia el suelo a madera clara' });
  assert.equal(r.ok, false);
  assert.equal(r.codigo, 400);
  assert.match(r.error, /JPEG o PNG/);
});

test('por encima del límite de foto se responde 413', () => {
  const enorme = jpegFalso(LIMITE_FOTO + 10);
  const r = validarPeticion({ foto: enBase64(enorme), prompt: 'cambia el suelo a madera clara' });
  assert.equal(r.ok, false);
  assert.equal(r.codigo, 413);
});

test('el prompt largo se corta en el servidor, no se acepta a medias', () => {
  const r = validarPeticion({
    foto: enBase64(jpegFalso()),
    prompt: 'x'.repeat(1300)
  });
  assert.equal(r.ok, false);
  assert.equal(r.codigo, 400);
});

test('normaliza las tres formas de respuesta que dan los modelos', () => {
  assert.equal(normalizarImagen('abc'), 'abc');
  assert.equal(normalizarImagen({ image: 'abc' }), 'abc');
  assert.equal(normalizarImagen({ result: { image: 'abc' } }), 'abc');
  const bytes = new Uint8Array([1, 2, 3]);
  assert.equal(normalizarImagen(bytes), bytesABase64(bytes));
  assert.equal(normalizarImagen({ nada: true }), null);
});

test('el cupo cuenta por día y se agota', () => {
  assert.equal(diaUtc(new Date('2026-11-20T23:00:00Z')), '2026-11-20');
  assert.equal(cupoAgotado(29, 30), false);
  assert.equal(cupoAgotado(30, 30), true);
  assert.notEqual(
    claveCupo('sal', 'd-abc', '2026-11-20'),
    claveCupo('sal', 'd-abc', '2026-11-21')
  );
});

test('una foto de móvil se rechaza con un aviso claro, no con un error del modelo', () => {
  const r = validarPeticion({
    foto: enBase64(jpegCon(1024, 768)),
    prompt: 'Pon el suelo de tarima clara y las paredes blancas'
  });

  assert.equal(r.ok, false);
  assert.equal(r.codigo, 400);
  assert.match(r.error, /1024×768/);
  assert.match(r.error, new RegExp(String(MAX_ENTRADA)));
});

 test('una foto dentro del limite del modelo se acepta', () => {
  const r = validarPeticion({
    foto: enBase64(jpegCon(512, 384)),
    prompt: 'Pon el suelo de tarima clara y las paredes blancas'
  });

  assert.equal(r.ok, true);
  assert.deepEqual(r.medida, { ancho: 512, alto: 384 });
});

test('la salida mantiene la forma de la foto y no pasa de 1024', () => {
  assert.deepEqual(tamanoSalida({ ancho: 512, alto: 384 }), { ancho: 1024, alto: 768 });
  assert.deepEqual(tamanoSalida({ ancho: 512, alto: 512 }), { ancho: 1024, alto: 1024 });
  assert.deepEqual(tamanoSalida({ ancho: 384, alto: 512 }), { ancho: 768, alto: 1024 });
  assert.deepEqual(tamanoSalida(null), { ancho: 1024, alto: 768 });

  const raro = tamanoSalida({ ancho: 500, alto: 333 });
  assert.equal(raro.ancho % 8, 0);
  assert.equal(raro.alto % 8, 0);
  assert.ok(raro.ancho <= 1024 && raro.alto <= 1024);
});

test('el PNG tambien se mide, no solo el JPEG', () => {
  assert.deepEqual(dimensionesImagen(pngCon(800, 600)), { ancho: 800, alto: 600 });
  assert.deepEqual(dimensionesImagen(pngCon(512, 512)), { ancho: 512, alto: 512 });
  assert.equal(dimensionesImagen(jpegFalso()), null); // sin SOF: no se puede saber
});

test('base64 va y vuelve sin perder bytes', () => {
  const original = jpegFalso(5000);
  const vuelta = base64ABytes(bytesABase64(original));
  assert.deepEqual(Array.from(vuelta.slice(0, 8)), Array.from(original.slice(0, 8)));
  assert.equal(vuelta.byteLength, original.byteLength);
});
