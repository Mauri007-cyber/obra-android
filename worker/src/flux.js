import { normalizarImagen, tamanoSalida } from './logica.js';

// No está conectado al handler público: antes deben verificarse identidad y cuota.
export async function editarConFlux(ai, foto, prompt, medida) {
  if (!ai || typeof ai.run !== 'function' || !medida || medida.ancho < 1 || medida.alto < 1 || medida.ancho > 512 || medida.alto > 512) {
    throw new Error('Falta el modelo o la imagen no tiene dimensiones compatibles');
  }
  const tipo = foto[0] === 0xff && foto[1] === 0xd8 ? 'image/jpeg' : 'image/png';
  const { ancho, alto } = tamanoSalida(medida);
  const formulario = new FormData();
  formulario.append('input_image_0', new Blob([foto], { type: tipo }), 'estancia');
  formulario.append('prompt', prompt);
  formulario.append('width', String(ancho));
  formulario.append('height', String(alto));
  const cuerpo = new Response(formulario);
  const respuesta = await ai.run('@cf/black-forest-labs/flux-2-klein-4b', {
    multipart: { body: cuerpo.body, contentType: cuerpo.headers.get('content-type') }
  });
  const imagen = normalizarImagen(respuesta);
  if (!imagen || imagen.length > 16 * 1024 * 1024 || !/^[A-Za-z0-9+/]+={0,2}$/.test(imagen)) {
    throw new Error('El modelo no devolvió una imagen válida');
  }
  return imagen;
}
