// Prueba de extremo a extremo en workerd (wrangler dev, sin cuenta):
// Durable Object SQLite real, firma ES256 real, concurrencia real y proveedor simulado.
// Ejecutar: npm run test:workerd
import test from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parDeClaves, token } from './prueba/jwt.mjs';
import { PNG } from './prueba/png.mjs';

const RAIZ = path.dirname(fileURLToPath(import.meta.url));
const PUERTO = 8799;
const BASE = `http://127.0.0.1:${PUERTO}`;

function foto() {
  const bytes = new Uint8Array(3000);
  bytes.set([0xff, 0xd8, 0xff, 0xc0, 0x00, 0x11, 0x08], 0);
  bytes[7] = 0x01; bytes[8] = 0x80; // alto 384
  bytes[9] = 0x02; bytes[10] = 0x00; // ancho 512
  return Buffer.from(bytes).toString('base64');
}

function retoque(autorizacion, prompt = 'Pon el suelo de tarima clara') {
  const cabeceras = { 'Content-Type': 'application/json' };
  if (autorizacion) cabeceras.Authorization = autorizacion;
  return new Request(`${BASE}/retoque`, {
    method: 'POST',
    headers: cabeceras,
    body: JSON.stringify({ foto: foto(), prompt })
  });
}

function cupo(autorizacion) {
  return new Request(`${BASE}/cupo`, { headers: autorizacion ? { Authorization: autorizacion } : {} });
}

async function arrancar(clavePublica) {
  const persistencia = await mkdtemp(path.join(tmpdir(), 'obra-cupo-'));
  const registro = [];
  const proceso = spawn(
    process.execPath,
    [
      path.join(RAIZ, 'node_modules/wrangler/bin/wrangler.js'),
      'dev',
      '--ip', '127.0.0.1',
      '--port', String(PUERTO),
      '-c', 'prueba/wrangler.jsonc',
      '--persist-to', persistencia,
      '--var', `JWT_CLAVE:${JSON.stringify(clavePublica)}`
    ],
    {
      cwd: RAIZ,
      stdio: ['ignore', 'pipe', 'pipe'],
      env: { ...process.env, CI: '1', NO_COLOR: '1', WRANGLER_SEND_METRICS: 'false' }
    }
  );
  proceso.stdout.on('data', (d) => registro.push(String(d)));
  proceso.stderr.on('data', (d) => registro.push(String(d)));

  const limite = Date.now() + 90_000;
  while (Date.now() < limite) {
    if (proceso.exitCode !== null) {
      throw new Error(`wrangler dev terminó con ${proceso.exitCode}:\n${registro.join('').slice(-4000)}`);
    }
    try {
      const res = await fetch(`${BASE}/salud`, { signal: AbortSignal.timeout(2000) });
      if (res.ok) return { proceso, detener: () => detener(proceso, persistencia) };
    } catch {}
    await new Promise((r) => setTimeout(r, 500));
  }
  await detener(proceso, persistencia);
  throw new Error(`wrangler dev no arrancó:\n${registro.join('').slice(-4000)}`);
}

async function detener(proceso, persistencia) {
  if (proceso.exitCode === null) {
    proceso.kill('SIGTERM');
    const salida = new Promise((r) => proceso.once('exit', r));
    const limite = setTimeout(() => proceso.kill('SIGKILL'), 5000);
    await salida;
    clearTimeout(limite);
  }
  await rm(persistencia, { recursive: true, force: true });
}

test('workerd: identidad, cuota global y reserva previa', { timeout: 240_000 }, async (t) => {
  const par = await parDeClaves();
  const servidor = await arrancar(par.publica);
  const ana = `Bearer ${await token(par, { sub: 'ana' })}`;
  const luis = `Bearer ${await token(par, { sub: 'luis' })}`;

  try {
    await t.test('el estado inicial declara la protección activa', async () => {
      const cuerpo = await (await fetch(`${BASE}/salud`)).json();
      assert.equal(cuerpo.proteccionActiva, true);
      assert.equal(cuerpo.simulado, false);
      assert.equal(cuerpo.serviciosDePagoActivos, true);
    });

    await t.test('sin token, caducado, con otra audiencia o usuario ajeno se rechaza', async () => {
      assert.equal((await fetch(retoque())).status, 401);
      const caducado = await token(par, { iat: Math.floor(Date.now() / 1000) - 310, exp: Math.floor(Date.now() / 1000) - 10 });
      assert.equal((await fetch(retoque(`Bearer ${caducado}`))).status, 401);
      const otraAudiencia = await token(par, { aud: 'otra' });
      assert.equal((await fetch(retoque(`Bearer ${otraAudiencia}`))).status, 401);
      const intruso = await token(par, { sub: 'intruso' });
      assert.equal((await fetch(retoque(`Bearer ${intruso}`))).status, 401);
      const cuerpo = await (await fetch(cupo(ana))).json();
      assert.equal(cuerpo.global.usadas, 0);
    });

    await t.test('una petición inválida no reserva cupo', async () => {
      const res = await fetch(retoque(ana, 'corto'));
      assert.equal(res.status, 400);
      const cuerpo = await (await fetch(cupo(ana))).json();
      assert.deepEqual(cuerpo, { global: { usadas: 0, maximo: 3 }, usuario: { usadas: 0, maximo: 2 } });
    });

    await t.test('un fallo del proveedor responde 502 y consume cupo', async () => {
      const res = await fetch(retoque(ana, 'FALLA y pon el suelo de tarima clara'));
      assert.equal(res.status, 502);
      const cuerpo = await (await fetch(cupo(ana))).json();
      assert.deepEqual(cuerpo, { global: { usadas: 1, maximo: 3 }, usuario: { usadas: 1, maximo: 2 } });
    });

    await t.test('una llamada válida devuelve la imagen y descuenta cupo', async () => {
      const res = await fetch(retoque(ana));
      assert.equal(res.status, 200);
      const cuerpo = await res.json();
      assert.equal(cuerpo.imagen, PNG);
      assert.deepEqual(cuerpo.restante, { global: 1, usuario: 0 });
      const estado = await (await fetch(cupo(ana))).json();
      assert.deepEqual(estado, { global: { usadas: 2, maximo: 3 }, usuario: { usadas: 2, maximo: 2 } });
    });

    await t.test('el límite por usuario corta aunque quede cupo global', async () => {
      const res = await fetch(retoque(ana));
      assert.equal(res.status, 429);
      const estado = await (await fetch(cupo(ana))).json();
      assert.equal(estado.global.usadas, 2);
    });

    await t.test('en concurrencia, solo pasa el cupo global que queda', async () => {
      const respuestas = await Promise.all(Array.from({ length: 8 }, () => fetch(retoque(luis))));
      const codigos = respuestas.map((r) => r.status).sort();
      assert.equal(codigos.filter((c) => c === 200).length, 1);
      assert.equal(codigos.filter((c) => c === 429).length, 7);

      const estado = await (await fetch(cupo(luis))).json();
      assert.deepEqual(estado, { global: { usadas: 3, maximo: 3 }, usuario: { usadas: 1, maximo: 2 } });

      assert.equal((await fetch(retoque(ana))).status, 429);
      assert.equal((await fetch(retoque(luis))).status, 429);
    });

    await t.test('/cupo y /retoque solo aceptan su método', async () => {
      assert.equal((await fetch(new Request(`${BASE}/cupo`, { method: 'POST' }))).status, 405);
      assert.equal((await fetch(`${BASE}/retoque`)).status, 405);
      assert.equal((await fetch(`${BASE}/cupo`)).status, 401);
    });
  } finally {
    await servidor.detener();
  }
});
