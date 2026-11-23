import { normalizarImagen, tamanoSalida } from './logica.js';

/**
 * Edita la foto con FLUX.2 [klein] 4B.
 *
 * `invocar(modelo, opciones)` es la llamada al proveedor; se inyecta para poder probar
 * todo el camino (autenticación, cupo y respuesta) sin gastar ni tener cuenta.
 */
export async function editarConFlux(invocar, foto, prompt, medida, modelo = '@cf/black-forest-labs/flux-2-klein-4b') {
  if (typeof invocar !== 'function') throw new Error('Falta el proveedor del modelo');
  if (
    !medida || medida.ancho < 1 || medida.alto < 1 ||
    medida.ancho > 512 || medida.alto > 512
  ) {
    throw new Error('La imagen no tiene dimensiones compatibles');
  }

  const tipo = foto[0] === 0xff && foto[1] === 0xd8 ? 'image/jpeg' : 'image/png';
  const { ancho, alto } = tamanoSalida(medida);
  const formulario = new FormData();
  formulario.append('input_image_0', new Blob([foto], { type: tipo }), 'estancia');
  formulario.append('prompt', prompt);
  formulario.append('width', String(ancho));
  formulario.append('height', String(alto));

  const cuerpo = new Response(formulario);
  const respuesta = await invocar(modelo, {
    multipart: { body: cuerpo.body, contentType: cuerpo.headers.get('content-type') }
  });

  const imagen = normalizarImagen(respuesta);
  if (!imagen || imagen.length > 16 * 1024 * 1024 || !/^[A-Za-z0-9+/]+={0,2}$/.test(imagen)) {
    throw new Error('El modelo no devolvió una imagen válida');
  }
  return imagen;
}
