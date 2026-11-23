// Verificación de tokens: la clave se genera en cada ejecución y nunca es de producción.
import test from 'node:test';
import assert from 'node:assert/strict';
import { verificarIdentidad } from './src/proteccion.js';
import { AHORA, claimsBase, configBase, firmar, parDeClaves } from './prueba/jwt.mjs';

const par = await parDeClaves();
const config = configBase(par.publica);
const ahora = AHORA();
const claims = claimsBase({ iat: ahora - 5, exp: ahora + 300 });

test('acepta un token firmado, autorizado, vigente y de corta duración', async () => {
  assert.equal(await verificarIdentidad(await firmar(par.privada, claims), config, ahora), 'ana');
});

test('rechaza usuarios, emisores, audiencias y tiempos que no tocan', async () => {
  const variantes = [
    { ...claims, sub: 'intruso' },
    { ...claims, aud: 'otra' },
    { ...claims, iss: 'https://otro.test' },
    { ...claims, exp: ahora - 1 },
    { ...claims, iat: ahora + 60 },
    { ...claims, exp: ahora + 2000, iat: ahora },
    { ...claims, nbf: ahora + 30 },
    { ...claims, nbf: 'mañana' },
    { ...claims, iat: 'ayer' },
    { ...claims, sub: 42 },
    { ...claims, exp: undefined }
  ];
  for (const variante of variantes) {
    assert.equal(await verificarIdentidad(await firmar(par.privada, variante), config, ahora), null);
  }
});

test('rechaza firmas de otra clave, otro algoritmo y firmas truncadas', async () => {
  const otro = await parDeClaves();
  assert.equal(await verificarIdentidad(await firmar(otro.privada, claims), config, ahora), null);
  assert.equal(await verificarIdentidad(await firmar(par.privada, claims, { alg: 'none' }), config, ahora), null);
  assert.equal(await verificarIdentidad(await firmar(par.privada, claims, { alg: 'ES256', crit: ['exp'] }), config, ahora), null);

  const bueno = await firmar(par.privada, claims);
  const [cabecera, datos, firma] = bueno.split('.');
  assert.equal(await verificarIdentidad(`${cabecera}.${datos}.${firma.slice(0, -8)}`, config, ahora), null);
  assert.equal(await verificarIdentidad(`${cabecera}.${datos}`, config, ahora), null);
  assert.equal(await verificarIdentidad('basura', config, ahora), null);
  assert.equal(await verificarIdentidad(`${cabecera}.${datos}.${firma}`, config, ahora), 'ana'); // control
});

test('falla cerrado si la configuración está incompleta o trae clave privada', async () => {
  const bueno = await firmar(par.privada, claims);
  assert.equal(await verificarIdentidad(bueno, {}, ahora), null);
  assert.equal(await verificarIdentidad(bueno, { ...config, permitidos: [] }, ahora), null);
  assert.equal(await verificarIdentidad(bueno, { ...config, issuer: '' }, ahora), null);
  assert.equal(await verificarIdentidad(bueno, { ...config, clave: { ...par.publica, d: 'privada' } }, ahora), null);
  assert.equal(await verificarIdentidad(bueno, { ...config, clave: { ...par.publica, crv: 'P-384' } }, ahora), null);
});

test('un token enorme se descarta sin parsear', async () => {
  assert.equal(await verificarIdentidad('a'.repeat(9000), config, ahora), null);
});
