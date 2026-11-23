// Control de acceso y de gasto. Este módulo SÍ está conectado al handler, pero la ruta
// pública sigue cerrada mientras PROTECCION_ACTIVA no esté en 1 (ver config.js).
import { limitesDe } from './config.js';

const RESERVA_CADUCA = 24 * 60 * 60; // segundos
const MAX_SUB = 256;

const json = (cuerpo, estado = 200) =>
  new Response(JSON.stringify(cuerpo), {
    status: estado,
    headers: { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' }
  });

const decodificar = (s) => {
  if (!/^[A-Za-z0-9_-]+$/.test(s)) throw new Error('base64url inválido');
  const texto = atob(s.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - (s.length % 4)) % 4));
  return Uint8Array.from(texto, (c) => c.charCodeAt(0));
};

/**
 * Verifica un token ES256 de corta duración y devuelve el `sub` autorizado, o null.
 * Rechaza: algoritmo distinto de ES256, emisor/audiencia ajenos, usuario no permitido,
 * token caducado o emitido en el futuro, duración mayor de 15 minutos y clave privada.
 */
export async function verificarIdentidad(token, config, ahora = Math.floor(Date.now() / 1000)) {
  try {
    if (
      typeof token !== 'string' || token.length > 8192 || !config?.issuer || !config?.audience ||
      !Array.isArray(config.permitidos) || !config.permitidos.length || !config.clave
    ) return null;

    const partes = token.split('.');
    if (partes.length !== 3) return null;
    const [cabecera, datos, firma] = partes;
    const leer = (s) => JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(decodificar(s)));
    const header = leer(cabecera);
    const claims = leer(datos);

    if (
      header.alg !== 'ES256' || header.crit !== undefined || header.enc !== undefined ||
      claims.iss !== config.issuer || claims.aud !== config.audience ||
      typeof claims.sub !== 'string' || !config.permitidos.includes(claims.sub) ||
      !Number.isSafeInteger(claims.iat) || !Number.isSafeInteger(claims.exp) ||
      claims.iat > ahora || claims.exp <= ahora || claims.exp - claims.iat > 900 ||
      (claims.nbf !== undefined && (!Number.isSafeInteger(claims.nbf) || claims.nbf > ahora))
    ) return null;

    const { kty, crv, d } = config.clave;
    if (kty !== 'EC' || crv !== 'P-256' || d !== undefined) return null;

    const firmaBinaria = decodificar(firma);
    if (firmaBinaria.byteLength !== 64) return null; // ECDSA P-256 en formato raw r||s

    const key = await crypto.subtle.importKey(
      'jwk',
      config.clave,
      { name: 'ECDSA', namedCurve: 'P-256' },
      false,
      ['verify']
    );
    const ok = await crypto.subtle.verify(
      { name: 'ECDSA', hash: 'SHA-256' },
      key,
      firmaBinaria,
      new TextEncoder().encode(`${cabecera}.${datos}`)
    );
    return ok ? claims.sub : null;
  } catch {
    return null;
  }
}

/**
 * Contador global y atómico de llamadas facturables.
 *
 * Hay UNA sola instancia: el Worker usa siempre `idFromName('global')`. Cada reserva
 * incrementa el total y el del usuario dentro de una transacción síncrona, así que dos
 * peticiones simultáneas no pueden pasar del límite. La reserva se hace ANTES de invocar
 * al modelo; un fallo del modelo también consume cupo y no se devuelve.
 *
 * El contador limita llamadas, no dinero: el coste real depende del proveedor y debe
 * comprobarse aparte. Una reserva no devuelta por una caída del Worker reduce el cupo
 * disponible, que es el lado seguro del error.
 */
export class CupoGlobal {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
  }

  tablas() {
    const sql = this.ctx.storage.sql;
    sql.exec('CREATE TABLE IF NOT EXISTS cupo (id TEXT PRIMARY KEY, usadas INTEGER NOT NULL)');
    sql.exec(
      'CREATE TABLE IF NOT EXISTS reserva (id TEXT PRIMARY KEY, sub TEXT NOT NULL, creada INTEGER NOT NULL)'
    );
  }

  usadas(sql, id) {
    return sql.exec('SELECT usadas FROM cupo WHERE id = ?', id).toArray()[0]?.usadas ?? 0;
  }

  incrementar(sql, id) {
    sql.exec(
      'INSERT INTO cupo (id, usadas) VALUES (?, 1) ON CONFLICT(id) DO UPDATE SET usadas = usadas + 1',
      id
    );
  }

  async fetch(request) {
    const url = new URL(request.url);
    const limites = limitesDe(this.env);
    if (!limites) return json({ error: 'Los límites de cupo no están configurados.' }, 503);

    try {
      if (url.pathname === '/reservar' && request.method === 'POST') return await this.reservar(request, limites);
      if (url.pathname === '/devolver' && request.method === 'POST') return await this.devolver(request);
      if (url.pathname === '/estado' && request.method === 'GET') return this.estado(url, limites);
      return json({ error: 'Ruta de cupo desconocida.' }, 404);
    } catch {
      return json({ error: 'El contador de cupo ha fallado.' }, 500);
    }
  }

  async reservar(request, limites) {
    let sub;
    try {
      sub = (await request.json()).sub;
    } catch {
      return json({ error: 'Falta el usuario de la reserva.' }, 400);
    }
    if (typeof sub !== 'string' || !sub.length || sub.length > MAX_SUB) {
      return json({ error: 'Usuario no válido.' }, 400);
    }

    const ticket = crypto.randomUUID();
    const ahora = Math.floor(Date.now() / 1000);
    let resultado;

    // Todo dentro de una transacción síncrona: o se cuentan las dos filas, o ninguna.
    this.ctx.storage.transactionSync(() => {
      this.tablas();
      const sql = this.ctx.storage.sql;
      sql.exec('DELETE FROM reserva WHERE creada < ?', ahora - RESERVA_CADUCA);

      const global = this.usadas(sql, 'global');
      const usuario = this.usadas(sql, `usuario:${sub}`);
      if (global >= limites.global || usuario >= limites.usuario) {
        resultado = { ok: false, global, usuario };
        return;
      }

      this.incrementar(sql, 'global');
      this.incrementar(sql, `usuario:${sub}`);
      sql.exec('INSERT INTO reserva (id, sub, creada) VALUES (?, ?, ?)', ticket, sub, ahora);
      resultado = { ok: true, global: global + 1, usuario: usuario + 1 };
    });

    if (!resultado.ok) {
      return json(
        {
          error: 'Cupo agotado.',
          global: { usadas: resultado.global, maximo: limites.global },
          usuario: { usadas: resultado.usuario, maximo: limites.usuario }
        },
        429
      );
    }

    return json({
      ticket,
      global: { usadas: resultado.global, maximo: limites.global },
      usuario: { usadas: resultado.usuario, maximo: limites.usuario }
    });
  }

  /** Solo se usa si la reserva no llegó a invocar al proveedor (simulación). */
  async devolver(request) {
    let ticket;
    try {
      ticket = (await request.json()).ticket;
    } catch {
      return json({ error: 'Falta la reserva.' }, 400);
    }
    if (typeof ticket !== 'string' || !ticket.length) return json({ error: 'Reserva no válida.' }, 400);

    let devuelta = false;
    this.ctx.storage.transactionSync(() => {
      this.tablas();
      const sql = this.ctx.storage.sql;
      const fila = sql.exec('SELECT sub FROM reserva WHERE id = ?', ticket).toArray()[0];
      if (!fila) return;
      sql.exec('DELETE FROM reserva WHERE id = ?', ticket);
      sql.exec('UPDATE cupo SET usadas = usadas - 1 WHERE id = ? AND usadas > 0', 'global');
      sql.exec('UPDATE cupo SET usadas = usadas - 1 WHERE id = ? AND usadas > 0', `usuario:${fila.sub}`);
      devuelta = true;
    });

    return json({ devuelta }, devuelta ? 200 : 404);
  }

  estado(url, limites) {
    const sub = url.searchParams.get('sub');
    if (typeof sub !== 'string' || !sub.length || sub.length > MAX_SUB) {
      return json({ error: 'Falta el usuario.' }, 400);
    }
    this.tablas();
    const sql = this.ctx.storage.sql;
    return json({
      global: { usadas: this.usadas(sql, 'global'), maximo: limites.global },
      usuario: { usadas: this.usadas(sql, `usuario:${sub}`), maximo: limites.usuario }
    });
  }
}
