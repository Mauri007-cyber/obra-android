import test from 'node:test';
import assert from 'node:assert/strict';
import { editarConFlux } from './src/flux.js';

test('envía foto y texto libre como multipart al modelo de edición', async () => {
  const foto = new Uint8Array([0xff, 0xd8, 0xff, 0x01]);
  let invocaciones = 0;
  const ai = { async run(modelo, { multipart }) {
    invocaciones++;
    assert.equal(modelo, '@cf/black-forest-labs/flux-2-klein-4b');
    const form = await new Response(multipart.body, { headers: { 'Content-Type': multipart.contentType } }).formData();
    assert.equal(form.get('prompt'), 'Sustituye el suelo por madera oscura');
    assert.equal(form.get('width'), '1024');
    assert.equal(form.get('height'), '768');
    assert.deepEqual(new Uint8Array(await form.get('input_image_0').arrayBuffer()), foto);
    assert.equal(form.get('input_image_0').type, 'image/jpeg');
    return { image: 'aW1hZ2Vu' };
  } };
  assert.equal(await editarConFlux(ai, foto, 'Sustituye el suelo por madera oscura', { ancho: 512, alto: 384 }), 'aW1hZ2Vu');
  assert.equal(invocaciones, 1);
});

test('rechaza medidas desconocidas o grandes antes de invocar al modelo', async () => {
  let llamadas = 0;
  const ai = { run: async () => { llamadas++; } };
  for (const medida of [null, { ancho: 513, alto: 1 }, { ancho: 0, alto: 1 }]) {
    await assert.rejects(editarConFlux(ai, new Uint8Array([1]), 'cambiar la pared', medida));
  }
  assert.equal(llamadas, 0);
});

test('no trata una respuesta vacía como imagen', async () => {
  await assert.rejects(editarConFlux({ run: async () => ({}) }, new Uint8Array([1]), 'cambiar la pared', { ancho: 100, alto: 100 }));
});
