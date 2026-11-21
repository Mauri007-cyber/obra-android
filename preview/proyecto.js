/* Proyecto de obra completo — demo sin dependencias.
   El estado vive en memoria y se guarda en localStorage para poder refrescar. */

const CLAVE = 'obra-proyecto-v2';

const CATALOGO = [
  // Tirar y abrir
  { id:'tabique_tirado', nombre:'Tirar un tabique', ambito:'Tirar y abrir', unidad:'m2', precio:12 },
  { id:'abrir_hueco', nombre:'Abrir un hueco en un muro', ambito:'Tirar y abrir', unidad:'ud', precio:180 },
  { id:'quitar_alicatado', nombre:'Quitar alicatado viejo', ambito:'Tirar y abrir', unidad:'m2', precio:14 },
  { id:'levantar_suelo', nombre:'Levantar el suelo viejo', ambito:'Tirar y abrir', unidad:'m2', precio:10 },

  // Albañilería
  { id:'tabique_nuevo', nombre:'Tabique nuevo de ladrillo', ambito:'Albañilería', unidad:'m2', precio:45 },
  { id:'tabique_pladur', nombre:'Tabique de pladur', ambito:'Albañilería', unidad:'m2', precio:38 },
  { id:'aislamiento', nombre:'Aislar pared', ambito:'Albañilería', unidad:'m2', precio:16 },
  { id:'nivelar_suelo', nombre:'Nivelar el suelo con solera', ambito:'Albañilería', unidad:'m2', precio:22 },

  // Suelo
  { id:'suelo_laminado', nombre:'Suelo laminado', ambito:'Suelo', unidad:'m2', precio:35, acabados:['roble claro','roble oscuro','nogal'] },
  { id:'suelo_porcelanico', nombre:'Suelo porcelánico', ambito:'Suelo', unidad:'m2', precio:48, acabados:['gris cemento','blanco mate','imitación madera','negro'] },
  { id:'suelo_vinilico', nombre:'Suelo vinílico', ambito:'Suelo', unidad:'m2', precio:30, acabados:['roble','gris','beige'] },
  { id:'suelo_tarima', nombre:'Tarima natural', ambito:'Suelo', unidad:'m2', precio:62, acabados:['roble','haya','teka'] },
  { id:'suelo_microcemento', nombre:'Suelo de microcemento', ambito:'Suelo', unidad:'m2', precio:55, acabados:['gris','arena','blanco'] },
  { id:'rodapie', nombre:'Rodapié nuevo', ambito:'Suelo', unidad:'ml', precio:6, acabados:['blanco','madera','aluminio'] },

  // Paredes
  { id:'alicatado', nombre:'Alicatar pared', ambito:'Paredes', unidad:'m2', precio:42, acabados:['blanco brillo','beige','verde','imitación mármol'] },
  { id:'papel_pintado', nombre:'Papel pintado', ambito:'Paredes', unidad:'m2', precio:18, acabados:['liso','rayas','floral','textura'] },
  { id:'panel_decorativo', nombre:'Panel decorativo en pared', ambito:'Paredes', unidad:'m2', precio:45, acabados:['madera','microcemento','lacado'] },

  // Pintura
  { id:'pintura_plastica', nombre:'Pintar paredes (2 manos)', ambito:'Pintura', unidad:'m2', precio:8, acabados:['blanco roto','arena','verde salvia','gris perla','azul humo'] },
  { id:'pintura_antihumedad', nombre:'Pintura antihumedad', ambito:'Pintura', unidad:'m2', precio:12, acabados:['blanco','beige'] },
  { id:'pintura_techo', nombre:'Pintar el techo', ambito:'Pintura', unidad:'m2', precio:7, acabados:['blanco mate'] },

  // Techo
  { id:'falso_techo', nombre:'Falso techo de pladur', ambito:'Techo', unidad:'m2', precio:28 },
  { id:'techo_registrable', nombre:'Techo registrable', ambito:'Techo', unidad:'m2', precio:32, acabados:['blanco','gris'] },

  // Puertas y ventanas
  { id:'puerta_interior', nombre:'Puerta interior nueva', ambito:'Puertas y ventanas', unidad:'ud', precio:220, acabados:['blanca lisa','roble','cristal'], mide:false },
  { id:'puerta_corredera', nombre:'Puerta corredera', ambito:'Puertas y ventanas', unidad:'ud', precio:380, acabados:['blanca','roble','cristal'], mide:false },
  { id:'ventana_nueva', nombre:'Ventana nueva', ambito:'Puertas y ventanas', unidad:'ud', precio:350, acabados:['aluminio blanco','aluminio negro','PVC'], mide:false },
  { id:'ventana_oscilo', nombre:'Ventana oscilobatiente', ambito:'Puertas y ventanas', unidad:'ud', precio:460, acabados:['aluminio blanco','aluminio negro'], mide:false },
  { id:'persiana', nombre:'Persiana nueva', ambito:'Puertas y ventanas', unidad:'ud', precio:180, acabados:['aluminio','PVC'], mide:false },

  // Baño
  { id:'sanitario_completo', nombre:'Cambiar el baño completo', ambito:'Baño', unidad:'global', precio:2400, mide:false },
  { id:'plato_ducha', nombre:'Plato de ducha', ambito:'Baño', unidad:'ud', precio:380, acabados:['blanco','gris','pizarra'], mide:false },
  { id:'banera', nombre:'Bañera nueva', ambito:'Baño', unidad:'ud', precio:520, acabados:['blanca'], mide:false },
  { id:'mampara', nombre:'Mampara de ducha', ambito:'Baño', unidad:'ud', precio:320, acabados:['transparente','ahumada'], mide:false },
  { id:'inodoro', nombre:'Inodoro', ambito:'Baño', unidad:'ud', precio:210, acabados:['blanco','blanco mate'], mide:false },
  { id:'lavabo', nombre:'Lavabo con mueble', ambito:'Baño', unidad:'ud', precio:340, acabados:['blanco','roble','negro'], mide:false },
  { id:'griferia', nombre:'Grifería', ambito:'Baño', unidad:'ud', precio:120, acabados:['cromada','negra mate','dorada'], mide:false },
  { id:'espejo_bano', nombre:'Espejo con luz', ambito:'Baño', unidad:'ud', precio:190 },

  // Cocina
  { id:'mueble_cocina', nombre:'Mueble de cocina', ambito:'Cocina', unidad:'ml', precio:280, acabados:['blanco mate','roble','verde','gris'] },
  { id:'encimera', nombre:'Encimera', ambito:'Cocina', unidad:'ml', precio:180, acabados:['compacto blanco','granito','madera'] },
  { id:'electrodomesticos', nombre:'Electrodomésticos', ambito:'Cocina', unidad:'global', precio:1800, mide:false },
  { id:'campana', nombre:'Campana extractora', ambito:'Cocina', unidad:'ud', precio:220, mide:false },
  { id:'frontal', nombre:'Frontal de cocina', ambito:'Cocina', unidad:'m2', precio:65, acabados:['blanco','verde','madera'] },

  // Electricidad
  { id:'punto_luz', nombre:'Punto de luz', ambito:'Electricidad', unidad:'ud', precio:45 },
  { id:'foco_empotrado', nombre:'Foco empotrado', ambito:'Electricidad', unidad:'ud', precio:38 },
  { id:'enchufe', nombre:'Punto de enchufe', ambito:'Electricidad', unidad:'ud', precio:32 },
  { id:'cuadro_electrico', nombre:'Cuadro eléctrico nuevo', ambito:'Electricidad', unidad:'ud', precio:380, mide:false },
  { id:'tira_led', nombre:'Tira LED', ambito:'Electricidad', unidad:'ml', precio:22 },

  // Fontanería
  { id:'toma_agua', nombre:'Toma de agua', ambito:'Fontanería', unidad:'ud', precio:90 },
  { id:'desague', nombre:'Desagüe nuevo', ambito:'Fontanería', unidad:'ud', precio:85 },
  { id:'termo', nombre:'Termo o caldera', ambito:'Fontanería', unidad:'ud', precio:650, mide:false },

  // Clima
  { id:'radiador', nombre:'Radiador', ambito:'Clima y aire', unidad:'ud', precio:260, mide:false },
  { id:'suelo_radiante', nombre:'Suelo radiante', ambito:'Clima y aire', unidad:'m2', precio:55 },
  { id:'split', nombre:'Split de aire acondicionado', ambito:'Clima y aire', unidad:'ud', precio:420, mide:false },

  // Muebles
  { id:'armario_empotrado', nombre:'Armario empotrado', ambito:'Muebles a medida', unidad:'ml', precio:320, acabados:['blanco','roble','lacado gris'] },
  { id:'estanteria', nombre:'Estantería a medida', ambito:'Muebles a medida', unidad:'ml', precio:190 },
  { id:'mueble_tv', nombre:'Mueble de salón a medida', ambito:'Muebles a medida', unidad:'ml', precio:240 },
];

const AMBITOS = [...new Set(CATALOGO.map(t => t.ambito))];

const ETIQUETA_UNIDAD = { m2:'m²', ml:'m', ud:'ud', global:'partida' };
const NOMBRE_ESTADO = { aprobado:'Aprobado', propuesto:'Por decidir', rechazado:'Fuera' };

const DEMO = {
  nombre: 'Piso de la abuela',
  direccion: 'Calle Mayor 12 · 3º B',
  estancias: [
    {
      id: 'salon', nombre: 'Salón', m2: 22.5, altura: 2.5, foto: 'img/habitacion.jpg',
      cambios: [
        { tipo:'suelo_porcelanico', cantidad:22.5, acabado:'imitación madera', estado:'aprobado' },
        { tipo:'pintura_plastica', cantidad:50, acabado:'blanco roto', estado:'aprobado' },
        { tipo:'falso_techo', cantidad:22.5, acabado:'', estado:'aprobado' },
        { tipo:'foco_empotrado', cantidad:6, acabado:'', estado:'aprobado' },
        { tipo:'puerta_corredera', cantidad:1, acabado:'cristal', estado:'propuesto' },
        { tipo:'armario_empotrado', cantidad:3.2, acabado:'roble', estado:'rechazado' },
      ]
    },
    {
      id: 'cocina', nombre: 'Cocina', m2: 12, altura: 2.5, foto: 'img/despues.png',
      cambios: [
        { tipo:'mueble_cocina', cantidad:4.5, acabado:'blanco mate', estado:'aprobado' },
        { tipo:'encimera', cantidad:4.5, acabado:'compacto blanco', estado:'aprobado' },
        { tipo:'frontal', cantidad:3, acabado:'verde', estado:'aprobado' },
        { tipo:'electrodomesticos', cantidad:1, acabado:'', estado:'aprobado' },
        { tipo:'suelo_vinilico', cantidad:12, acabado:'gris', estado:'aprobado' },
        { tipo:'alicatado', cantidad:8, acabado:'beige', estado:'propuesto' },
      ]
    },
    {
      id: 'bano', nombre: 'Baño', m2: 6.5, altura: 2.5, foto: 'img/antes.png',
      cambios: [
        { tipo:'sanitario_completo', cantidad:1, acabado:'', estado:'aprobado' },
        { tipo:'alicatado', cantidad:20, acabado:'blanco brillo', estado:'aprobado' },
        { tipo:'mampara', cantidad:1, acabado:'transparente', estado:'aprobado' },
        { tipo:'suelo_porcelanico', cantidad:6.5, acabado:'gris cemento', estado:'aprobado' },
        { tipo:'espejo_bano', cantidad:1, acabado:'', estado:'propuesto' },
      ]
    },
    {
      id: 'pasillo', nombre: 'Pasillo', m2: 8, altura: 2.5, foto: '',
      cambios: [
        { tipo:'pintura_antihumedad', cantidad:25, acabado:'blanco', estado:'propuesto' },
        { tipo:'suelo_laminado', cantidad:8, acabado:'roble claro', estado:'aprobado' },
        { tipo:'puerta_interior', cantidad:2, acabado:'blanca lisa', estado:'propuesto' },
      ]
    }
  ]
};

let estado = cargarEstado() || copia(DEMO);
let estanciaAbierta = null;
let tipoElegido = null;
let cantidadElegida = '';
let acabadoElegido = '';

/* ---------------- utilidades ---------------- */

const $ = sel => document.querySelector(sel);
const esc = t => String(t ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const copia = obj => JSON.parse(JSON.stringify(obj));
const tipoDe = id => CATALOGO.find(t => t.id === id);

function euros(v) {
  return new Intl.NumberFormat('es-ES', { style:'currency', currency:'EUR', maximumFractionDigits:0 }).format(v || 0);
}
function numero(v, d = 1) {
  return new Intl.NumberFormat('es-ES', { minimumFractionDigits:d, maximumFractionDigits:d }).format(v || 0);
}

function importe(cambio) {
  const tipo = tipoDe(cambio.tipo);
  if (!tipo) return 0;
  return tipo.unidad === 'global' ? tipo.precio : tipo.precio * (cambio.cantidad || 0);
}

function detalle(cambio) {
  const tipo = tipoDe(cambio.tipo);
  if (!tipo) return '';
  const partes = [];
  if (tipo.mide !== false && cambio.cantidad > 0) {
    partes.push(`${numero(cambio.cantidad)} ${ETIQUETA_UNIDAD[tipo.unidad]}`);
  } else if (tipo.mide === false) {
    partes.push('partida entera');
  }
  if (cambio.acabado) partes.push(cambio.acabado);
  return partes.join(' · ');
}

function lineaCambio(cambio) {
  const tipo = tipoDe(cambio.tipo);
  const d = detalle(cambio);
  return tipo ? (d ? `${tipo.nombre} — ${d}` : tipo.nombre) : 'Cambio';
}

function totales(est) {
  let aprobado = 0, pendiente = 0, nAprob = 0, nTotal = 0;
  est.estancias.forEach(e => e.cambios.forEach(c => {
    if (c.estado === 'rechazado') return;
    nTotal++;
    if (c.estado === 'aprobado') { aprobado += importe(c); nAprob++; }
    else pendiente += importe(c);
  }));
  return { aprobado, pendiente, nAprob, nTotal };
}

function costeEstancia(e) {
  return e.cambios.filter(c => c.estado === 'aprobado').reduce((s, c) => s + importe(c), 0);
}

function sugerir(tipo, estancia) {
  if (tipo.mide === false) return 0;
  const m2 = estancia.m2 || 12;
  if (tipo.unidad === 'm2') {
    const esPared = tipo.ambito === 'Paredes' || tipo.ambito === 'Pintura';
    return Math.round((esPared ? 4 * Math.sqrt(m2) * (estancia.altura || 2.5) : m2) * 10) / 10;
  }
  if (tipo.unidad === 'ml') return Math.round(2 * Math.sqrt(m2) * 10) / 10;
  return 1;
}

function guardarEstado() {
  try { localStorage.setItem(CLAVE, JSON.stringify(estado)); } catch (e) {}
}
function cargarEstado() {
  try {
    const crudo = localStorage.getItem(CLAVE);
    if (!crudo) return null;
    const datos = JSON.parse(crudo);
    if (!datos || !Array.isArray(datos.estancias)) return null;
    return datos;
  } catch (e) { return null; }
}

/* ---------------- render ---------------- */

function render() {
  $('#titulo-obra').textContent = estado.nombre;
  $('#dir-obra').textContent = estado.direccion || '';

  const t = totales(estado);
  $('#total-aprobado').textContent = euros(t.aprobado);
  $('#total-pendiente').textContent = euros(t.pendiente);
  $('#total-estancias').textContent = estado.estancias.length;
  const pct = t.nTotal ? Math.round((t.nAprob / t.nTotal) * 100) : 0;
  $('#barra-progreso').style.width = pct + '%';
  $('#texto-progreso').textContent =
    `${t.nAprob} de ${t.nTotal} cambios aprobados · ${pct}% del proyecto cerrado`;

  $('#estancias').innerHTML = estado.estancias.map(tarjeta).join('');
  guardarEstado();
}

function tarjeta(e) {
  const aprobados = e.cambios.filter(c => c.estado === 'aprobado').length;
  const pendientes = e.cambios.filter(c => c.estado === 'propuesto').length;

  const foto = e.foto
    ? `<img src="${esc(e.foto)}" alt="Foto de ${esc(e.nombre)}" loading="lazy">`
    : `<div style="display:flex;align-items:center;justify-content:center;height:100%;color:var(--gris);font-size:14px">Sin foto todavía</div>`;

  const cambios = e.cambios.length
    ? e.cambios.map((c, i) => filaCambio(e, c, i)).join('')
    : `<p class="sub">Todavía no hay cambios en esta estancia.</p>`;

  return `
    <article class="tarjeta">
      <div class="foto-estancia">${foto}<span class="m2">${numero(e.m2)} m²</span></div>
      <div class="cuerpo-tarjeta">
        <div class="titulo-fila">
          <h2>${esc(e.nombre)}</h2>
          <span class="coste">${euros(costeEstancia(e))}</span>
        </div>
        <p class="sub">${aprobados} aprobados${pendientes ? ` · ${pendientes} por decidir` : ''}</p>
        <ul class="cambios">${cambios}</ul>
        <div class="pie-tarjeta">
          <button class="boton" data-add="${esc(e.id)}">+ Añadir cambio</button>
          <button class="boton" data-vaciar="${esc(e.id)}">Vaciar</button>
        </div>
      </div>
    </article>`;
}

function filaCambio(e, c, i) {
  const color = c.estado === 'aprobado' ? 'var(--verde)'
    : c.estado === 'rechazado' ? 'var(--rojo)' : 'var(--obra)';
  return `
    <li class="cambio">
      <div class="cambio-linea">
        <span class="bola" style="background:${color}"></span>
        <div class="cambio-texto">
          <p>${esc(lineaCambio(c))}</p>
          <small>${NOMBRE_ESTADO[c.estado]}</small>
        </div>
        <span class="precio-cambio">${euros(importe(c))}</span>
      </div>
      <div class="estados">
        ${['aprobado','propuesto','rechazado'].map(est => `
          <button class="pastilla ${c.estado === est ? 'activa ' + claseEstado(est) : ''}"
                  data-estado="${est}" data-est="${esc(e.id)}" data-i="${i}">
            ${est === 'propuesto' ? 'Por decidir' : est === 'rechazado' ? 'Fuera' : 'Aprobado'}
          </button>`).join('')}
      </div>
    </li>`;
}

function claseEstado(est) {
  return est === 'aprobado' ? 'aprobado' : est === 'rechazado' ? 'fuera' : 'pendiente';
}

/* ---------------- catálogo ---------------- */

function abrirCatalogo(estanciaId) {
  estanciaAbierta = estanciaId;
  tipoElegido = null;
  $('#buscar').value = '';
  $('#velo-catalogo').hidden = false;
  $('#titulo-catalogo').textContent = 'Añadir cambio a ' + (estado.estancias.find(e => e.id === estanciaId)?.nombre || '');
  pintarCatalogo();
  setTimeout(() => $('#buscar').focus(), 50);
}

function cerrarCatalogo() {
  $('#velo-catalogo').hidden = true;
  estanciaAbierta = null;
  tipoElegido = null;
}

function pintarCatalogo() {
  const q = ($('#buscar').value || '').toLowerCase().trim();
  const estancia = estado.estancias.find(e => e.id === estanciaAbierta);

  if (tipoElegido) {
    const tipo = tipoElegido;
    const chips = (tipo.acabados || []).map(a => `
      <button class="chip ${acabadoElegido === a ? 'activo' : ''}" data-acabado="${esc(a)}">${esc(a)}</button>`).join('');
    $('#catalogo-ambito').textContent = tipo.ambito;
    $('#titulo-catalogo').textContent = tipo.nombre;
    $('#lista-catalogo').innerHTML = `
      <div class="confirmar">
        <h3>${esc(tipo.nombre)}</h3>
        <p class="sub">${tipo.mide === false ? 'Se cobra como partida entera.' : 'Cantidad propuesta según la estancia; cámbiala si hace falta.'}</p>
        ${tipo.mide === false ? '' : `
          <div class="fila-campos">
            <label class="campo">Cantidad (${ETIQUETA_UNIDAD[tipo.unidad]})
              <input type="number" id="cantidad-nueva" min="0" step="0.1" value="${esc(cantidadElegida)}">
            </label>
          </div>`}
        ${chips ? `<div class="chips">${chips}</div>` : ''}
        <button class="boton principal" id="confirmar-cambio">Añadir a ${esc(estancia?.nombre || '')}</button>
        <button class="boton fantasma" id="volver-catalogo" style="width:100%;margin-top:8px">Volver al catálogo</button>
      </div>`;
    return;
  }

  $('#catalogo-ambito').textContent = 'Catálogo';
  $('#titulo-catalogo').textContent = 'Añadir cambio a ' + (estancia?.nombre || '');

  const filtrados = CATALOGO.filter(t =>
    !q || t.nombre.toLowerCase().includes(q) || t.ambito.toLowerCase().includes(q) ||
    (t.acabados || []).some(a => a.toLowerCase().includes(q))
  );

  if (!filtrados.length) {
    $('#lista-catalogo').innerHTML = `<p class="sub" style="margin-top:16px">No hay nada con «${esc(q)}». Prueba con suelo, baño, puerta o luz.</p>`;
    return;
  }

  const grupos = {};
  filtrados.forEach(t => { (grupos[t.ambito] = grupos[t.ambito] || []).push(t); });

  $('#lista-catalogo').innerHTML = AMBITOS.filter(a => grupos[a]).map(a => `
    <p class="grupo-ambito">${esc(a)}</p>
    ${grupos[a].map(t => `
      <button class="opcion" data-tipo="${esc(t.id)}">
        <span>${esc(t.nombre)}<small>${t.mide === false ? 'partida entera' : 'por ' + ETIQUETA_UNIDAD[t.unidad]}</small></span>
        <span class="precio">${euros(t.precio)}</span>
      </button>`).join('')}
  `).join('');
}

function elegirTipo(id) {
  const tipo = tipoDe(id);
  if (!tipo) return;
  const estancia = estado.estancias.find(e => e.id === estanciaAbierta);
  tipoElegido = tipo;
  acabadoElegido = (tipo.acabados || [])[0] || '';
  cantidadElegida = tipo.mide === false ? '' : String(sugerir(tipo, estancia));
  pintarCatalogo();
}

function confirmarCambio() {
  const tipo = tipoElegido;
  const estancia = estado.estancias.find(e => e.id === estanciaAbierta);
  if (!tipo || !estancia) return;
  const cantidad = tipo.mide === false ? 0 : (parseFloat(String(cantidadElegida).replace(',', '.')) || 0);
  estancia.cambios.push({ tipo: tipo.id, cantidad, acabado: acabadoElegido, estado: 'propuesto' });
  cerrarCatalogo();
  render();
}

/* ---------------- recorrido ---------------- */

function abrirRecorrido() {
  const t = totales(estado);
  const orden = [...estado.estancias].sort((a, b) => {
    const peso = e => (e.foto ? 1 : 0) * 10 + e.cambios.filter(c => c.estado === 'aprobado').length;
    return peso(b) - peso(a);
  });

  const paradas = orden.map((e, i) => {
    const aprobados = e.cambios.filter(c => c.estado === 'aprobado');
    const frases = aprobados.map(c => `<li>${esc(lineaCambio(c))}</li>`).join('');
    const foto = i === 0
      ? `<div class="comparador" style="--pos:50%">
           <img src="img/antes.png" alt="Antes">
           <img class="despues" src="img/despues.png" alt="Después">
           <span class="eje"></span>
           <span class="rotulo antes">Antes</span>
           <span class="rotulo despues">Después</span>
           <input type="range" min="0" max="100" value="50" aria-label="Comparar antes y después">
         </div>`
      : e.foto ? `<img src="${esc(e.foto)}" alt="${esc(e.nombre)}" loading="lazy">` : '';

    return `
      <section class="parada">
        <div class="parada-cabecera">
          <h3>${esc(e.nombre)}</h3>
          <span class="coste">${euros(costeEstancia(e))}</span>
        </div>
        ${foto}
        ${frases
          ? `<ul class="frases">${frases}</ul>`
          : `<p class="sin-cambios">Sin cambios aprobados en esta estancia.</p>`}
      </section>`;
  }).join('');

  const aviso = estado.estancias.some(e => e.cambios.some(c => c.estado === 'propuesto'))
    ? `<p class="sin-cambios">Hay cambios aún sin decidir. El recorrido enseña solo lo aprobado.</p>`
    : '';

  $('#recorrido-cuerpo').innerHTML = paradas + aviso;
  $('#total-recorrido').textContent = euros(t.aprobado);
  $('#velo-recorrido').hidden = false;

  $('#recorrido-cuerpo').querySelectorAll('.comparador input[type=range]').forEach(control => {
    control.addEventListener('input', () => {
      control.closest('.comparador').style.setProperty('--pos', control.value + '%');
    });
  });
}

/* ---------------- eventos ---------------- */

$('#estancias').addEventListener('click', ev => {
  const boton = ev.target.closest('button');
  if (!boton) return;

  if (boton.dataset.add) { abrirCatalogo(boton.dataset.add); return; }

  if (boton.dataset.vaciar) {
    const est = estado.estancias.find(e => e.id === boton.dataset.vaciar);
    if (est && confirm(`¿Quitar todos los cambios de ${est.nombre}?`)) {
      est.cambios = [];
      render();
    }
    return;
  }

  if (boton.dataset.estado) {
    const est = estado.estancias.find(e => e.id === boton.dataset.est);
    const cambio = est && est.cambios[Number(boton.dataset.i)];
    if (cambio) {
      cambio.estado = boton.dataset.estado;
      render();
    }
  }
});

$('#lista-catalogo').addEventListener('click', ev => {
  const boton = ev.target.closest('button');
  if (!boton) return;
  if (boton.dataset.tipo) { elegirTipo(boton.dataset.tipo); return; }
  if (boton.dataset.acabado) {
    acabadoElegido = boton.dataset.acabado;
    pintarCatalogo();
    return;
  }
  if (boton.id === 'confirmar-cambio') { confirmarCambio(); return; }
  if (boton.id === 'volver-catalogo') { tipoElegido = null; pintarCatalogo(); }
});

$('#lista-catalogo').addEventListener('input', ev => {
  if (ev.target.id === 'cantidad-nueva') cantidadElegida = ev.target.value;
});

$('#buscar').addEventListener('input', () => {
  tipoElegido = null;
  pintarCatalogo();
});

$('#cerrar-catalogo').addEventListener('click', cerrarCatalogo);
$('#velo-catalogo').addEventListener('click', ev => {
  if (ev.target.id === 'velo-catalogo') cerrarCatalogo();
});

$('#ensenar').addEventListener('click', abrirRecorrido);
$('#cerrar-recorrido').addEventListener('click', () => { $('#velo-recorrido').hidden = true; });
$('#volver').addEventListener('click', () => { $('#velo-recorrido').hidden = true; });
$('#velo-recorrido').addEventListener('click', ev => {
  if (ev.target.id === 'velo-recorrido') $('#velo-recorrido').hidden = true;
});

document.addEventListener('keydown', ev => {
  if (ev.key !== 'Escape') return;
  if (!$('#velo-catalogo').hidden) cerrarCatalogo();
  else if (!$('#velo-recorrido').hidden) $('#velo-recorrido').hidden = true;
});

$('#reiniciar').addEventListener('click', () => {
  if (!confirm('¿Volver a los datos de ejemplo?')) return;
  estado = copia(DEMO);
  try { localStorage.removeItem(CLAVE); } catch (e) {}
  render();
});

render();
