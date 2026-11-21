// Prueba el handler entero (rutas, validación, cupo, respuesta) sin Cloudflare ni red.
// Ejecutar: node --test worker/prueba-handler.mjs
import test from 'node:test';
import assert from 'node:assert/strict';

// Cache API mínima: el Worker la usa para contar imágenes por día.
const almacen = new Map();
globalThis.caches = {
  default: {
    async match(clave) {
      return almacen.has(clave) ? new Response(almacen.get(clave)) : undefined;
    },
    async put(clave, respuesta) {
      almacen.set(clave, await respuesta.text());
    }
  }
};

const { default: worker } = await import('./src/index.js');

function jpegFalso(bytes = 3000) {
  const datos = new Uint8Array(bytes);
  datos[0] = 0xff;
  datos[1] = 0xd8;
  datos[2] = 0xff;
  return Buffer.from(datos).toString('base64');
}

const entorno = (extra = {}) => ({
  MODELO: '@cf/black-forest-labs/flux-2-klein-4b',
  MAX_DIA: '2',
  SIMULADO: '0',
  SAL: 'prueba',
  ...extra
});

const pedir = (cuerpo, cabeceras = {}) =>
  new Request('https://obra-ia.prueba/retoque', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Dispositivo': 'dispositivo-1', ...cabeceras },
    body: JSON.stringify(cuerpo)
  });

test('salud contesta sin foto y dice el modelo', async () => {
  const res = await worker.fetch(new Request('https://obra-ia.prueba/salud'), entorno());
  const cuerpo = await res.json();
  assert.equal(res.status, 200);
  assert.equal(cuerpo.ok, true);
  assert.match(cuerpo.modelo, /flux/);
  assert.equal(cuerpo.cupoDia, 2);
});

test('una ruta que no existe da 404 y GET a retoque da 405', async () => {
  assert.equal((await worker.fetch(new Request('https://x/nada'), entorno())).status, 404);
  assert.equal((await worker.fetch(new Request('https://x/retoque'), entorno())).status, 405);
});

test('sin foto o con una frase corta no se llama al modelo', async () => {
  let llamado = false;
  const ia = { run: async () => { llamado = true; return { image: 'abc' }; } };
  const res = await worker.fetch(
    pedir({ foto: jpegFalso(), prompt: 'corto' }),
    entorno({ AI: ia })
  );
  assert.equal(res.status, 400);
  assert.equal(llamado, false);
});

test('con el modelo simulado devuelve la misma foto y avisa', async () => {
  const foto = jpegFalso();
  const res = await worker.fetch(
    pedir({ foto, prompt: 'Pon el suelo de tarima clara' }),
    entorno({ SIMULADO: '1' })
  );
  const cuerpo = await res.json();
  assert.equal(res.status, 200);
  assert.equal(cuerpo.simulado, true);
  assert.equal(cuerpo.imagen, foto);
});

test('con modelo de verdad devuelve la imagen y gasta cupo hasta agotarlo', async () => {
  almacen.clear();
  const ia = { run: async () => ({ image: 'aW1hZ2Vu' }) };

  const primera = await worker.fetch(
    pedir({ foto: jpegFalso(), prompt: 'Pon el suelo de tarima clara' }),
    entorno({ AI: ia })
  );
  assert.equal(primera.status, 200);
  assert.equal((await primera.json()).imagen, 'aW1hZ2Vu');

  const segunda = await worker.fetch(
    pedir({ foto: jpegFalso(), prompt: 'Pon las paredes blancas' }),
    entorno({ AI: ia })
  );
  assert.equal((await segunda.json()).quedan, 0);

  const tercera = await worker.fetch(
    pedir({ foto: jpegFalso(), prompt: 'Otra vez, con el suelo más claro' }),
    entorno({ AI: ia })
  );
  assert.equal(tercera.status, 429);
});

test('si el modelo falla se responde 502 con lenguaje de obra', async () => {
  almacen.clear();
  const ia = { run: async () => { throw new Error('model timeout'); } };
  const res = await worker.fetch(
    pedir({ foto: jpegFalso(), prompt: 'Pon el suelo de tarima clara' }),
    entorno({ AI: ia })
  );
  const cuerpo = await res.json();
  assert.equal(res.status, 502);
  assert.match(cuerpo.error, /prueba otra vez|cupo/i);
});

test('sin Content-Type JSON se rechaza con 415', async () => {
  const res = await worker.fetch(
    new Request('https://x/retoque', { method: 'POST', body: 'hola' }),
    entorno()
  );
  assert.equal(res.status, 415);
});
