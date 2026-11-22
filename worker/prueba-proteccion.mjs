import test from 'node:test';
import assert from 'node:assert/strict';
import { webcrypto } from 'node:crypto';
import { verificarIdentidad, CupoGlobal } from './src/proteccion.js';

if (!globalThis.crypto) globalThis.crypto = webcrypto;
const codificar = value => Buffer.from(JSON.stringify(value)).toString('base64url');

async function firmar(privada, claims, header = { alg: 'ES256' }) {
  const datos = `${codificar(header)}.${codificar(claims)}`;
  const firma = await crypto.subtle.sign({ name: 'ECDSA', hash: 'SHA-256' }, privada, new TextEncoder().encode(datos));
  return `${datos}.${Buffer.from(firma).toString('base64url')}`;
}

test('solo acepta tokens firmados, autorizados, vigentes y de corta duración', async () => {
  const par = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify']);
  const otro = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify']);
  const config = { issuer: 'https://id.test', audience: 'obra', permitidos: ['usuario'], clave: await crypto.subtle.exportKey('jwk', par.publicKey) };
  const claims = { iss: config.issuer, aud: 'obra', sub: 'usuario', iat: 1000, exp: 1100 };
  assert.equal(await verificarIdentidad(await firmar(par.privateKey, claims), config, 1010), 'usuario');
  for (const variante of [
    { ...claims, sub: 'intruso' }, { ...claims, aud: 'otra' }, { ...claims, iss: 'otra' },
    { ...claims, exp: 1010 }, { ...claims, iat: 1020 }, { ...claims, exp: 3000 }, { ...claims, nbf: 1011 }
  ]) assert.equal(await verificarIdentidad(await firmar(par.privateKey, variante), config, 1010), null);
  assert.equal(await verificarIdentidad(await firmar(otro.privateKey, claims), config, 1010), null);
  assert.equal(await verificarIdentidad(await firmar(par.privateKey, claims, { alg: 'none' }), config, 1010), null);
  assert.equal(await verificarIdentidad('basura', config, 1010), null);
  assert.equal(await verificarIdentidad(await firmar(par.privateKey, claims), {}, 1010), null);
});

function almacenamiento() {
  const datos = new Map();
  return {
    transactionSync(fn) { return fn(); },
    sql: { exec(sql, id) {
      if (sql.startsWith('SELECT')) return { toArray: () => datos.has(id) ? [{ usadas: datos.get(id) }] : [] };
      if (sql.startsWith('INSERT')) datos.set(id, (datos.get(id) ?? 0) + 1);
      return { toArray: () => [] };
    } }
  };
}
const peticion = sub => new Request('https://cupo.local/', { method: 'POST', body: JSON.stringify({ sub }) });

test('reserva atómicamente antes de gastar con límites global y por usuario', async () => {
  const cupo = new CupoGlobal({ storage: almacenamiento() }, { MAX_LLAMADAS_TOTAL: '4', MAX_LLAMADAS_USUARIO: '3' });
  const resultados = await Promise.all(Array.from({ length: 20 }, () => cupo.fetch(peticion('a'))));
  assert.equal(resultados.filter(r => r.status === 204).length, 3);
  assert.equal((await cupo.fetch(peticion('b'))).status, 204);
  assert.equal((await cupo.fetch(peticion('c'))).status, 429);
});

test('falla cerrado si falta configuración', async () => {
  const cupo = new CupoGlobal({ storage: almacenamiento() }, {});
  assert.equal((await cupo.fetch(peticion('a'))).status, 503);
});
