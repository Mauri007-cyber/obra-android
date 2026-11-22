// Componentes de preparación: el handler sigue cerrado y no importa este módulo.
// Clave pública, emisor y audiencia deben pertenecer a un proveedor de identidad real.
const decodificar = s => {
  if (!/^[A-Za-z0-9_-]+$/.test(s)) throw new Error('base64url inválido');
  const texto = atob(s.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - s.length % 4) % 4));
  return Uint8Array.from(texto, c => c.charCodeAt(0));
};

export async function verificarIdentidad(token, config, ahora = Math.floor(Date.now() / 1000)) {
  try {
    if (typeof token !== 'string' || token.length > 8192 || !config?.issuer || !config?.audience ||
        !Array.isArray(config.permitidos) || !config.permitidos.length || !config.clave) return null;
    const partes = token.split('.');
    if (partes.length !== 3) return null;
    const [cabecera, datos, firma] = partes;
    const leer = s => JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(decodificar(s)));
    const header = leer(cabecera), claims = leer(datos);
    if (header.alg !== 'ES256' || header.crit || claims.iss !== config.issuer || claims.aud !== config.audience ||
        typeof claims.sub !== 'string' || !config.permitidos.includes(claims.sub) ||
        !Number.isSafeInteger(claims.iat) || !Number.isSafeInteger(claims.exp) ||
        claims.iat > ahora || claims.exp <= ahora || claims.exp - claims.iat > 900 ||
        (claims.nbf !== undefined && (!Number.isSafeInteger(claims.nbf) || claims.nbf > ahora))) return null;
    if (config.clave.kty !== 'EC' || config.clave.crv !== 'P-256' || config.clave.d) return null;
    const key = await crypto.subtle.importKey('jwk', config.clave, { name: 'ECDSA', namedCurve: 'P-256' }, false, ['verify']);
    const ok = await crypto.subtle.verify({ name: 'ECDSA', hash: 'SHA-256' }, key, decodificar(firma), new TextEncoder().encode(`${cabecera}.${datos}`));
    return ok ? claims.sub : null;
  } catch { return null; }
}

// Debe existir una única instancia global con idFromName constante, no una por usuario.
// El contador limita llamadas facturables, no equivale a un límite monetario del proveedor.
export class CupoGlobal {
  constructor(ctx, env) { this.ctx = ctx; this.env = env; }
  async fetch(request) {
    if (request.method !== 'POST') return new Response(null, { status: 405 });
    const global = Number(this.env.MAX_LLAMADAS_TOTAL);
    const usuario = Number(this.env.MAX_LLAMADAS_USUARIO);
    if (!Number.isSafeInteger(global) || global < 1 || !Number.isSafeInteger(usuario) || usuario < 1 || usuario > global) return new Response(null, { status: 503 });
    let sub;
    try { sub = (await request.json()).sub; } catch { return new Response(null, { status: 400 }); }
    if (typeof sub !== 'string' || !sub.length || sub.length > 256) return new Response(null, { status: 400 });
    const aceptada = this.ctx.storage.transactionSync(() => {
      const sql = this.ctx.storage.sql;
      sql.exec('CREATE TABLE IF NOT EXISTS cupo (id TEXT PRIMARY KEY, usadas INTEGER NOT NULL)');
      const obtener = id => sql.exec('SELECT usadas FROM cupo WHERE id = ?', id).toArray()[0]?.usadas ?? 0;
      const id = `usuario:${sub}`;
      if (obtener('global') >= global || obtener(id) >= usuario) return false;
      for (const clave of ['global', id]) sql.exec('INSERT INTO cupo (id, usadas) VALUES (?, 1) ON CONFLICT(id) DO UPDATE SET usadas = usadas + 1', clave);
      return true;
    });
    return new Response(null, { status: aceptada ? 204 : 429 });
  }
}
