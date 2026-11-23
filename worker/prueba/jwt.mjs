// Utilidades de prueba: claves y tokens ES256 reales, generados en cada ejecución.
// Nunca se usa una clave de producción aquí.
import { webcrypto } from 'node:crypto';

if (!globalThis.crypto) globalThis.crypto = webcrypto;

const codificar = (valor) => Buffer.from(JSON.stringify(valor)).toString('base64url');

export const AHORA = () => Math.floor(Date.now() / 1000);

export async function parDeClaves() {
  const par = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, [
    'sign',
    'verify'
  ]);
  return {
    privada: par.privateKey,
    publica: await crypto.subtle.exportKey('jwk', par.publicKey)
  };
}

export function claimsBase(extra = {}) {
  const ahora = AHORA();
  return { iss: 'https://id.test', aud: 'obra', sub: 'ana', iat: ahora - 5, exp: ahora + 300, ...extra };
}

export async function firmar(privada, claims, header = { alg: 'ES256' }) {
  const datos = `${codificar(header)}.${codificar(claims)}`;
  const firma = await crypto.subtle.sign(
    { name: 'ECDSA', hash: 'SHA-256' },
    privada,
    new TextEncoder().encode(datos)
  );
  return `${datos}.${Buffer.from(firma).toString('base64url')}`;
}

export async function token(par, extra = {}) {
  return firmar(par.privada, claimsBase(extra));
}

export const configBase = (clave) => ({
  issuer: 'https://id.test',
  audience: 'obra',
  permitidos: ['ana', 'luis'],
  clave
});
