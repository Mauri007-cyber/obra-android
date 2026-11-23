// Camino completo del handler con un contador en memoria: orden de comprobaciones,
// reserva antes de gastar, fallos contados y simulación que no consume.
// El Durable Object real (SQL, atomicidad y concurrencia) se prueba en prueba-workerd.mjs.
import test from 'node:test';
import assert from 'node:assert/strict';
import { procesarRetoque, consultarCupo } from './src/servicio.js';
import { leerConfig } from './src/config.js';
import { parDeClaves, token } from './prueba/jwt.mjs';

const par = await parDeClaves();
const IMAGEN = 'aW1hZ2Vu';

function foto() {
  const bytes = new Uint8Array(3000);
  // SOF0 inmediatamente después de SOI, con 512×384 (el máximo que admite el modelo).
  bytes.set([0xff, 0xd8, 0xff, 0xc0, 0x00, 0x11, 0x08], 0);
  bytes[7] = 0x01; bytes[8] = 0x80; // alto 384
  bytes[9] = 0x02; bytes[10] = 0x00; // ancho 512
  return Buffer.from(bytes).toString('base64');
}

function peticion({ autorizacion, prompt = 'Pon el suelo de tarima clara', cuerpo, tipo = 'application/json' } = {}) {
  const cabeceras = { 'Content-Type': tipo };
  if (autorizacion) cabeceras.Authorization = autorizacion;
  return new Request('https://obra.test/retoque', {
    method: 'POST',
    headers: cabeceras,
    body: cuerpo ?? JSON.stringify({ foto: foto(), prompt })
  });
}

function cupoFalso({ global: maxGlobal = 3, usuario: maxUsuario = 2 } = {}) {
  const usadas = new Map();
  const reservas = new Map();
  const total = () => usadas.get('global') ?? 0;
  const de = (sub) => usadas.get(`usuario:${sub}`) ?? 0;
  const sumar = (id, n) => usadas.set(id, Math.max(0, (usadas.get(id) ?? 0) + n));

  const objeto = {
    reservas: 0,
    async fetch(url, init) {
      const { pathname, searchParams } = new URL(url);
      if (pathname === '/reservar') {
        const { sub } = JSON.parse(init.body);
        if (total() >= maxGlobal || de(sub) >= maxUsuario) return new Response('{}', { status: 429 });
        sumar('global', 1);
        sumar(`usuario:${sub}`, 1);
        const ticket = `t-${(objeto.reservas += 1)}`;
        reservas.set(ticket, sub);
        return Response.json({
          ticket,
          global: { usadas: total(), maximo: maxGlobal },
          usuario: { usadas: de(sub), maximo: maxUsuario }
        });
      }
      if (pathname === '/devolver') {
        const { ticket } = JSON.parse(init.body);
        const sub = reservas.get(ticket);
        if (!sub) return Response.json({ devuelta: false }, { status: 404 });
        reservas.delete(ticket);
        sumar('global', -1);
        sumar(`usuario:${sub}`, -1);
        return Response.json({ devuelta: true });
      }
      if (pathname === '/estado') {
        const sub = searchParams.get('sub');
        return Response.json({
          global: { usadas: total(), maximo: maxGlobal },
          usuario: { usadas: de(sub), maximo: maxUsuario }
        });
      }
      return new Response(null, { status: 404 });
    }
  };

  return {
    namespace: { idFromName: (nombre) => nombre, get: () => objeto },
    objeto,
    total,
    de
  };
}

const entorno = (extra = {}) => ({
  PROTECCION_ACTIVA: '1',
  JWT_ISSUER: 'https://id.test',
  JWT_AUDIENCE: 'obra',
  USUARIOS_PERMITIDOS: 'ana,luis',
  JWT_CLAVE: JSON.stringify(par.publica),
  MAX_LLAMADAS_TOTAL: '3',
  MAX_LLAMADAS_USUARIO: '2',
  SIMULADO: '0',
  MODELO: '@cf/black-forest-labs/flux-2-klein-4b',
  ...extra
});

function contadorDeLlamadas() {
  const registro = { llamadas: 0, prompts: [] };
  return {
    registro,
    deps: {
      invocarModelo: async (_env, _modelo, opciones) => {
        registro.llamadas += 1;
        const forma = await new Response(opciones.multipart.body, {
          headers: { 'Content-Type': opciones.multipart.contentType }
        }).formData();
        registro.prompts.push(forma.get('prompt'));
        if (registro.fallar) throw new Error('proveedor caído');
        return { image: IMAGEN };
      }
    }
  };
}

test('sin protección activa no se llama al proveedor ni con cuota configurada', async () => {
  const cupo = cupoFalso();
  const { registro, deps } = contadorDeLlamadas();
  const env = entorno({ PROTECCION_ACTIVA: '0', CUPO: cupo.namespace });
  const res = await procesarRetoque(peticion(), env, deps);
  assert.equal(res.estado, 503);
  assert.match(res.cuerpo.motivo, /PROTECCION_ACTIVA/);
  assert.equal(registro.llamadas, 0);
  assert.equal(cupo.total(), 0);
});

test('la simulación sin protección devuelve la foto y no gasta', async () => {
  const { registro, deps } = contadorDeLlamadas();
  const res = await procesarRetoque(peticion(), entorno({ PROTECCION_ACTIVA: '0', SIMULADO: '1' }), deps);
  assert.equal(res.estado, 200);
  assert.equal(res.cuerpo.simulado, true);
  assert.equal(res.cuerpo.imagen, foto());
  assert.equal(registro.llamadas, 0);
});

test('con protección activa, sin token no se lee la foto, no se reserva y no se gasta', async () => {
  const cupo = cupoFalso();
  const { registro, deps } = contadorDeLlamadas();
  const env = entorno({ CUPO: cupo.namespace });
  const res = await procesarRetoque(peticion(), env, deps);
  assert.equal(res.estado, 401);
  assert.equal(registro.llamadas, 0);
  assert.equal(cupo.total(), 0);
});

test('un token de un usuario no autorizado se rechaza sin gastar', async () => {
  const cupo = cupoFalso();
  const { registro, deps } = contadorDeLlamadas();
  const env = entorno({ CUPO: cupo.namespace });
  const res = await procesarRetoque(
    peticion({ autorizacion: `Bearer ${await token(par, { sub: 'intruso' })}` }),
    env,
    deps
  );
  assert.equal(res.estado, 401);
  assert.equal(registro.llamadas, 0);
  assert.equal(cupo.total(), 0);
});

test('una petición inválida se rechaza antes de reservar', async () => {
  const cupo = cupoFalso();
  const { registro, deps } = contadorDeLlamadas();
  const env = entorno({ CUPO: cupo.namespace });
  const autorizacion = `Bearer ${await token(par)}`;
  for (const res of [
    await procesarRetoque(peticion({ autorizacion, prompt: 'corto' }), env, deps),
    await procesarRetoque(peticion({ autorizacion, cuerpo: 'hola' }), env, deps),
    await procesarRetoque(peticion({ autorizacion, tipo: 'text/plain', cuerpo: 'hola' }), env, deps)
  ]) assert.ok([400, 415].includes(res.estado));
  assert.equal(registro.llamadas, 0);
  assert.equal(cupo.total(), 0);
  assert.equal(cupo.objeto.reservas, 0);
});

test('con token válido reserva, llama una vez y devuelve el restante', async () => {
  const cupo = cupoFalso();
  const { registro, deps } = contadorDeLlamadas();
  const env = entorno({ CUPO: cupo.namespace });
  const res = await procesarRetoque(
    peticion({ autorizacion: `Bearer ${await token(par)}` }),
    env,
    deps
  );
  assert.equal(res.estado, 200);
  assert.equal(res.cuerpo.imagen, IMAGEN);
  assert.equal(res.cuerpo.modelo, env.MODELO);
  assert.deepEqual(res.cuerpo.restante, { global: 2, usuario: 1 });
  assert.equal(registro.llamadas, 1);
  assert.deepEqual(registro.prompts, ['Pon el suelo de tarima clara']);
  assert.equal(cupo.total(), 1);
});

test('un fallo del modelo cuenta para el cupo y no se reintenta solo', async () => {
  const cupo = cupoFalso();
  const { registro, deps } = contadorDeLlamadas();
  registro.fallar = true;
  const env = entorno({ CUPO: cupo.namespace });
  const autorizacion = `Bearer ${await token(par)}`;

  const primero = await procesarRetoque(peticion({ autorizacion }), env, deps);
  assert.equal(primero.estado, 502);
  assert.equal(cupo.total(), 1);
  assert.equal(cupo.de('ana'), 1);

  const segundo = await procesarRetoque(peticion({ autorizacion }), env, deps);
  const tercero = await procesarRetoque(peticion({ autorizacion }), env, deps);
  assert.equal(segundo.estado, 502);
  assert.equal(tercero.estado, 429);
  assert.equal(registro.llamadas, 2);
  assert.equal(cupo.total(), 2);
});

test('el límite por usuario y el global se respetan de verdad', async () => {
  const cupo = cupoFalso({ global: 2, usuario: 1 });
  const { registro, deps } = contadorDeLlamadas();
  const env = entorno({ CUPO: cupo.namespace });
  const ana = `Bearer ${await token(par)}`;
  const luis = `Bearer ${await token(par, { sub: 'luis' })}`;

  assert.equal((await procesarRetoque(peticion({ autorizacion: ana }), env, deps)).estado, 200);
  assert.equal((await procesarRetoque(peticion({ autorizacion: ana }), env, deps)).estado, 429);
  assert.equal((await procesarRetoque(peticion({ autorizacion: luis }), env, deps)).estado, 200);
  assert.equal((await procesarRetoque(peticion({ autorizacion: luis }), env, deps)).estado, 429);
  assert.equal(registro.llamadas, 2);
});

test('la simulación protegida autentica pero no reserva ni gasta', async () => {
  const cupo = cupoFalso();
  const { registro, deps } = contadorDeLlamadas();
  const env = entorno({ CUPO: cupo.namespace, SIMULADO: '1' });
  const autorizacion = `Bearer ${await token(par)}`;

  const sinToken = await procesarRetoque(peticion(), env, deps);
  assert.equal(sinToken.estado, 401);

  const res = await procesarRetoque(peticion({ autorizacion }), env, deps);
  assert.equal(res.estado, 200);
  assert.equal(res.cuerpo.simulado, true);
  assert.equal(registro.llamadas, 0);
  assert.equal(cupo.total(), 0);
  assert.equal(cupo.objeto.reservas, 0);
});

test('sin el contador configurado se cierra en lugar de gastar', async () => {
  const { registro, deps } = contadorDeLlamadas();
  const res = await procesarRetoque(
    peticion({ autorizacion: `Bearer ${await token(par)}` }),
    entorno(),
    deps
  );
  assert.equal(res.estado, 503);
  assert.equal(registro.llamadas, 0);
});

test('/cupo exige token y devuelve los contadores sin gastar', async () => {
  const cupo = cupoFalso();
  const env = entorno({ CUPO: cupo.namespace });
  const autorizacion = `Bearer ${await token(par)}`;

  assert.equal((await consultarCupo(new Request('https://x/cupo'), env, { cupo: () => cupo.namespace })).estado, 401);

  await procesarRetoque(peticion({ autorizacion }), env, contadorDeLlamadas().deps);
  const res = await consultarCupo(
    new Request('https://x/cupo', { headers: { Authorization: autorizacion } }),
    env,
    { cupo: () => cupo.namespace }
  );
  assert.equal(res.estado, 200);
  assert.deepEqual(res.cuerpo, {
    global: { usadas: 1, maximo: 3 },
    usuario: { usadas: 1, maximo: 2 }
  });
});

test('la configuración falla cerrada ante cualquier valor incompleto', async () => {
  assert.equal(leerConfig({}).activa, false);
  assert.equal(leerConfig({ PROTECCION_ACTIVA: '1' }).activa, false);
  assert.equal(leerConfig(entorno({ JWT_ISSUER: 'http://id.test' })).activa, false);
  assert.equal(leerConfig(entorno({ JWT_AUDIENCE: '' })).activa, false);
  assert.equal(leerConfig(entorno({ USUARIOS_PERMITIDOS: '' })).activa, false);
  assert.equal(leerConfig(entorno({ JWT_CLAVE: 'no-json' })).activa, false);
  assert.equal(leerConfig(entorno({ JWT_CLAVE: JSON.stringify({ kty: 'EC', crv: 'P-256', x: 'a', y: 'b', d: 'privada' }) })).activa, false);
  assert.equal(leerConfig(entorno({ MAX_LLAMADAS_TOTAL: '0' })).activa, false);
  assert.equal(leerConfig(entorno({ MAX_LLAMADAS_USUARIO: '9' })).activa, false);
  assert.equal(leerConfig(entorno({ MAX_LLAMADAS_TOTAL: '1000000' })).activa, false);
  assert.equal(leerConfig(entorno({ MAX_LLAMADAS_TOTAL: '4', MAX_LLAMADAS_USUARIO: '2' })).activa, true);
});
