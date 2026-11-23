import test from 'node:test';
import assert from 'node:assert/strict';
import worker from './src/index.js';

function foto() {
  const bytes = new Uint8Array(3000);
  bytes.set([0xff, 0xd8, 0xff]);
  return Buffer.from(bytes).toString('base64');
}
function retoque(cuerpo = { foto: foto(), prompt: 'Pon el suelo de tarima clara' }) {
  return new Request('https://obra.test/retoque', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(cuerpo)
  });
}

test('las rutas de pago rechazan incluso con binding, token y peticiones simultáneas', async () => {
  let llamadas = 0;
  const env = {
    AI: { run: async () => { llamadas += 1; return { image: 'aW1hZ2Vu' }; } },
    APY_TOKEN: 'secreto-de-prueba',
    FETCH_RECORTE: async () => { llamadas += 1; return new Response(); },
    // Aunque existan cuota y límites, sin PROTECCION_ACTIVA la ruta sigue cerrada.
    CUPO: { idFromName: () => 'global', get: () => ({ fetch: async () => { llamadas += 1; } }) },
    MAX_LLAMADAS_TOTAL: '999999',
    MAX_LLAMADAS_USUARIO: '999999',
    SIMULADO: '0'
  };
  const respuestas = await Promise.all(
    Array.from({ length: 20 }, (_, i) =>
      worker.fetch(i % 2 ? retoque() : new Request('https://obra.test/quitar-fondo', { method: 'POST' }), env)
    )
  );
  assert.ok(respuestas.every((res) => res.status === 503));
  assert.equal(llamadas, 0);
});

test('la simulación devuelve la foto y no invoca ningún proveedor', async () => {
  let llamadas = 0;
  const original = foto();
  const res = await worker.fetch(retoque({ foto: original, prompt: 'Paredes blancas y suelo claro' }), {
    SIMULADO: '1',
    AI: { run: async () => { llamadas += 1; } }
  });
  const cuerpo = await res.json();
  assert.equal(res.status, 200);
  assert.equal(cuerpo.simulado, true);
  assert.equal(cuerpo.imagen, original);
  assert.equal(llamadas, 0);
});

test('la simulación valida entradas y rutas', async () => {
  assert.equal((await worker.fetch(retoque({ foto: foto(), prompt: 'corto' }), { SIMULADO: '1' })).status, 400);
  assert.equal(
    (await worker.fetch(new Request('https://x/retoque', { method: 'POST', body: 'hola' }), { SIMULADO: '1' })).status,
    415
  );
  assert.equal((await worker.fetch(new Request('https://x/nada'), {})).status, 404);
  assert.equal((await worker.fetch(new Request('https://x/retoque'), {})).status, 405);
  assert.equal((await worker.fetch(new Request('https://x/cupo'), {})).status, 503);
});

test('salud no presenta servicios de pago como activos sin protección', async () => {
  const res = await worker.fetch(
    new Request('https://x/salud'),
    {
      MODELO: 'modelo',
      SIMULADO: '0',
      PROTECCION_ACTIVA: '0',
      JWT_ISSUER: 'https://id.test',
      JWT_AUDIENCE: 'obra',
      USUARIOS_PERMITIDOS: 'ana',
      MAX_LLAMADAS_TOTAL: '10',
      MAX_LLAMADAS_USUARIO: '2'
    }
  );
  const cuerpo = await res.json();
  assert.equal(cuerpo.serviciosDePagoActivos, false);
  assert.equal(cuerpo.proteccionActiva, false);
  assert.equal(cuerpo.simulado, false);
});

test('salud avisa si la protección está a medias', async () => {
  // PROTECCION_ACTIVA=1 pero sin clave pública ni límites: no puede gastar.
  const cuerpo = await (await worker.fetch(
    new Request('https://x/salud'),
    { PROTECCION_ACTIVA: '1', SIMULADO: '0', JWT_ISSUER: 'https://id.test', JWT_AUDIENCE: 'obra', USUARIOS_PERMITIDOS: 'ana' }
  )).json();
  assert.equal(cuerpo.proteccionActiva, false);
  assert.equal(cuerpo.serviciosDePagoActivos, false);
});

test('CORS permite la cabecera de autorización', async () => {
  const res = await worker.fetch(new Request('https://x/salud', { method: 'OPTIONS' }), {});
  assert.equal(res.status, 204);
  assert.match(res.headers.get('Access-Control-Allow-Headers'), /authorization/);
});
