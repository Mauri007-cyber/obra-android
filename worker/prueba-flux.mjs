import test from 'node:test';
import assert from 'node:assert/strict';
import { editarConFlux } from './src/flux.js';

test('envía foto y texto libre como multipart al modelo de edición', async () => {
  const foto = new Uint8Array([0xff, 0xd8, 0xff, 0x01]);
  let invocaciones = 0;
  const invocar = async (modelo, { multipart }) => {
    invocaciones += 1;
    assert.equal(modelo, '@cf/black-forest-labs/flux-2-klein-4b');
    const forma = await new Response(multipart.body, {
      headers: { 'Content-Type': multipart.contentType }
    }).formData();
    assert.equal(forma.get('prompt'), 'Sustituye el suelo por madera oscura');
    assert.equal(forma.get('width'), '1024');
    assert.equal(forma.get('height'), '768');
    assert.deepEqual(new Uint8Array(await forma.get('input_image_0').arrayBuffer()), foto);
    assert.equal(forma.get('input_image_0').type, 'image/jpeg');
    return { image: 'aW1hZ2Vu' };
  };

  assert.equal(
    await editarConFlux(invocar, foto, 'Sustituye el suelo por madera oscura', { ancho: 512, alto: 384 }),
    'aW1hZ2Vu'
  );
  assert.equal(invocaciones, 1);
});

test('usa el modelo que le pasa la configuración', async () => {
  let recibido = '';
  const invocar = async (modelo) => {
    recibido = modelo;
    return { image: 'aW1hZ2Vu' };
  };
  await editarConFlux(invocar, new Uint8Array([0xff, 0xd8]), 'cambiar la pared a blanco', { ancho: 100, alto: 100 }, 'modelo-propio');
  assert.equal(recibido, 'modelo-propio');
});

test('rechaza medidas desconocidas o grandes antes de invocar al modelo', async () => {
  let llamadas = 0;
  const invocar = async () => {
    llamadas += 1;
  };
  for (const medida of [null, { ancho: 513, alto: 1 }, { ancho: 0, alto: 1 }]) {
    await assert.rejects(editarConFlux(invocar, new Uint8Array([1]), 'cambiar la pared', medida));
  }
  assert.equal(llamadas, 0);
});

test('sin proveedor no hay llamada', async () => {
  await assert.rejects(editarConFlux(null, new Uint8Array([1]), 'cambiar la pared', { ancho: 100, alto: 100 }));
});

test('no trata una respuesta vacía como imagen', async () => {
  await assert.rejects(
    editarConFlux(async () => ({}), new Uint8Array([1]), 'cambiar la pared', { ancho: 100, alto: 100 })
  );
});
