// Entrada SOLO para pruebas en workerd (wrangler dev). No es el Worker desplegado.
// Usa el handler real, el Durable Object real y un proveedor simulado en lugar de FLUX.
import { CupoGlobal, manejar } from '../src/index.js';
import { PNG } from './png.mjs';

export { CupoGlobal };

/** El prompt viaja dentro del multipart, igual que se le manda al modelo. */
async function promptDe(opciones) {
  try {
    const forma = await new Response(opciones.multipart.body, {
      headers: { 'Content-Type': opciones.multipart.contentType }
    }).formData();
    return String(forma.get('prompt') || '');
  } catch {
    return '';
  }
}

export default {
  async fetch(request, env) {
    return manejar(request, env, {
      invocarModelo: async (_env, modelo, opciones) => {
        const prompt = await promptDe(opciones);
        // Permite probar que un fallo del proveedor también consume cupo.
        if (prompt.includes('FALLA')) throw new Error('proveedor simulado caído');
        if (modelo !== '@cf/black-forest-labs/flux-2-klein-4b') throw new Error('modelo inesperado');
        return { image: PNG };
      }
    });
  }
};
