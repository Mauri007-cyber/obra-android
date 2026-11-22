/* Quitar el fondo de una foto con ApyHub — herramienta real de la vista previa.
   La foto va al Worker de Obra (worker/src/index.js), que guarda el token y llama a ApyHub.
   Esta página nunca ve el token. La imagen original no se modifica: si falla, se queda igual. */

const CLAVE_SERVICIO = 'obra-servicio';
const LIMITE = 8 * 1024 * 1024;
const ESPERA_MS = 120000;

const $ = (id) => document.getElementById(id);

const servicio = $('servicio');
const entrada = $('archivo');
const zona = $('zona');
const botonQuitar = $('quitar');
const botonLimpiar = $('limpiar');
const botonDescargar = $('descargar');
const estado = $('estado');
const original = $('original');
const recorte = $('recorte');
const marcoOriginal = original.closest('.marco');
const marcoRecorte = recorte.closest('.marco');
const sello = $('sello');

let archivo = null;
let urlOriginal = null;
let urlRecorte = null;
let trabajando = false;

// ── servicio ─────────────────────────────────────────────────────────────────

function leerServicio() {
  const deUrl = new URLSearchParams(location.search).get('servicio');
  let valor = deUrl || '';
  if (!valor) {
    try {
      valor = localStorage.getItem(CLAVE_SERVICIO) || '';
    } catch {
      valor = '';
    }
  }
  return valor;
}

function normalizarServicio(texto) {
  const limpio = texto.trim().replace(/\/+$/, '');
  if (!limpio) return '';
  if (/^https?:\/\//i.test(limpio)) return limpio;
  return `https://${limpio}`;
}

servicio.value = leerServicio();
servicio.addEventListener('change', () => {
  const valor = normalizarServicio(servicio.value);
  servicio.value = valor;
  try {
    localStorage.setItem(CLAVE_SERVICIO, valor);
  } catch {
    /* sin localStorage la herramienta sigue funcionando en esta visita */
  }
});

// ── estado visible ───────────────────────────────────────────────────────────

function decir(texto, clase = '') {
  estado.textContent = texto;
  estado.className = `estado ${clase}`.trim();
}

function pintarOriginal() {
  original.src = urlOriginal || '';
  marcoOriginal.classList.toggle('con-foto', Boolean(urlOriginal));
}

function pintarRecorte(blob, transparente) {
  if (urlRecorte) URL.revokeObjectURL(urlRecorte);
  urlRecorte = URL.createObjectURL(blob);
  recorte.src = urlRecorte;
  marcoRecorte.classList.add('con-foto');

  botonDescargar.hidden = false;
  botonDescargar.href = urlRecorte;
  const base = (archivo?.name || 'foto').replace(/\.(jpe?g|png)$/i, '');
  botonDescargar.download = `${base}-recorte.png`;

  sello.hidden = false;
  sello.textContent = transparente ? 'PNG con transparencia' : 'PNG sin zonas transparentes';
  sello.classList.toggle('sin-alfa', !transparente);
}

function limpiarRecorte() {
  if (urlRecorte) URL.revokeObjectURL(urlRecorte);
  urlRecorte = null;
  recorte.removeAttribute('src');
  marcoRecorte.classList.remove('con-foto');
  botonDescargar.hidden = true;
  botonDescargar.removeAttribute('href');
  sello.hidden = true;
  sello.textContent = '';
}

// ── elegir la foto ───────────────────────────────────────────────────────────

function aceptarArchivo(nuevo) {
  if (!nuevo) return;

  if (!/^image\/(png|jpeg)$/.test(nuevo.type)) {
    decir('Tiene que ser JPEG o PNG. Un PDF o un HEIC no valen: conviértelo antes.', 'mal');
    return;
  }
  if (nuevo.size > LIMITE) {
    decir('La foto pasa de 8 MB. Redúcela un poco y vuelve a intentarlo.', 'mal');
    return;
  }

  archivo = nuevo;
  if (urlOriginal) URL.revokeObjectURL(urlOriginal);
  urlOriginal = URL.createObjectURL(nuevo);
  pintarOriginal();
  limpiarRecorte();

  botonQuitar.disabled = false;
  decir(`«${nuevo.name}» lista. Pulsa «Quitar el fondo» para mandarla a ApyHub.`);
}

$('elegir').addEventListener('click', () => entrada.click());
entrada.addEventListener('change', () => aceptarArchivo(entrada.files?.[0]));

['dragenter', 'dragover'].forEach((evento) =>
  zona.addEventListener(evento, (e) => {
    e.preventDefault();
    zona.classList.add('encima');
  })
);
['dragleave', 'drop'].forEach((evento) =>
  zona.addEventListener(evento, (e) => {
    e.preventDefault();
    zona.classList.remove('encima');
  })
);
zona.addEventListener('drop', (e) => aceptarArchivo(e.dataTransfer?.files?.[0]));
zona.addEventListener('click', (e) => {
  if (e.target.closest('button')) return;
  entrada.click();
});

// ── quitar el fondo ──────────────────────────────────────────────────────────

async function quitarFondo() {
  if (trabajando) return;

  if (!archivo) {
    decir('Elige primero una foto.', 'mal');
    return;
  }

  const base = normalizarServicio(servicio.value);
  if (!base) {
    decir('Falta la dirección del servicio (el Worker de Obra).', 'mal');
    servicio.focus();
    return;
  }
  servicio.value = base;
  try {
    localStorage.setItem(CLAVE_SERVICIO, base);
  } catch {
    /* da igual: se puede volver a escribir */
  }

  trabajando = true;
  botonQuitar.disabled = true;
  botonQuitar.textContent = 'Quitando el fondo…';
  limpiarRecorte();
  decir('Mandando la foto a ApyHub. Suele tardar entre 5 y 20 segundos.');

  const control = new AbortController();
  const reloj = setTimeout(() => control.abort(), ESPERA_MS);

  try {
    const formulario = new FormData();
    formulario.append('image', archivo, archivo.name);

    const respuesta = await fetch(`${base}/quitar-fondo`, {
      method: 'POST',
      headers: { 'X-Dispositivo': 'vista-previa' },
      body: formulario,
      signal: control.signal
    });

    if (!respuesta.ok) {
      let mensaje = `El servicio ha contestado ${respuesta.status}.`;
      try {
        const datos = await respuesta.json();
        if (datos?.error) mensaje = datos.error;
      } catch {
        /* si no es JSON, el mensaje genérico sirve */
      }
      decir(mensaje, 'mal');
      return;
    }

    const blob = await respuesta.blob();
    if (blob.type !== 'image/png') {
      decir('El servicio no ha devuelto un PNG. La foto original sigue igual.', 'mal');
      return;
    }

    const transparente = respuesta.headers.get('X-Recorte-Transparencia') === 'si';
    pintarRecorte(blob, transparente);
    decir(
      transparente
        ? 'Listo: PNG con transparencia. Descárgalo o revísalo en el tablero de cuadros.'
        : 'El PNG ha llegado, pero sin zonas transparentes. Prueba con otra foto o revisa el recorte.',
      transparente ? 'bien' : 'mal'
    );
  } catch (error) {
    if (error?.name === 'AbortError') {
      decir('ApyHub ha tardado demasiado. La foto original sigue aquí; prueba otra vez.', 'mal');
    } else {
      decir(
        'No se ha podido hablar con el servicio. Revisa la dirección del Worker y que esté desplegado.',
        'mal'
      );
    }
  } finally {
    clearTimeout(reloj);
    trabajando = false;
    botonQuitar.disabled = !archivo;
    botonQuitar.textContent = 'Quitar el fondo';
  }
}

botonQuitar.addEventListener('click', quitarFondo);

botonLimpiar.addEventListener('click', () => {
  if (urlOriginal) URL.revokeObjectURL(urlOriginal);
  urlOriginal = null;
  archivo = null;
  entrada.value = '';
  original.removeAttribute('src');
  marcoOriginal.classList.remove('con-foto');
  limpiarRecorte();
  botonQuitar.disabled = true;
  decir('Todo limpio. Elige otra foto cuando quieras.');
});

recorte.addEventListener('error', () => {
  decir('El navegador no ha podido mostrar el PNG. Prueba a descargarlo.', 'mal');
});
