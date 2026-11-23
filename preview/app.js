// Vista previa navegable de Obra con el modelo de presupuestos de la PWA.
// Sin dependencias: se abre en cualquier navegador y en el móvil.

const UNIDADES = { m2: 'm²', ml: 'ml', ud: 'ud', h: 'h', kg: 'kg', global: 'partida' };

const S = {
  pantalla: 'inicio',
  iva: 21,
  estado: 'borrador',
  comparar: 'despues',
  hoja: null,
  aviso: null,
  borrado: null,
  lineas: [
    {
      id: 'l1',
      concepto: 'Tabique nuevo de ladrillo hueco doble',
      cantidad: 7.5,
      unidad: 'm2',
      precio: 45,
      tipo: 'mixto',
      origen: 'plano',
      medida: '3,00 × 2,50 m'
    },
    {
      id: 'l2',
      concepto: 'Solado de gres 60×60',
      cantidad: 12,
      unidad: 'm2',
      precio: 35,
      tipo: 'mixto',
      origen: 'plano',
      medida: '4,00 × 3,00 m'
    },
    {
      id: 'l3',
      concepto: 'Pintura plástica lisa en paredes',
      cantidad: 31.92,
      unidad: 'm2',
      precio: 8,
      tipo: 'mano_obra',
      origen: 'plano',
      medida: 'paredes menos huecos'
    },
    {
      id: 'l4',
      concepto: 'Puerta de paso lacada en blanco',
      cantidad: 1,
      unidad: 'ud',
      precio: 220,
      tipo: 'material',
      origen: 'plano',
      medida: '1 hueco de 0,80 × 2,05'
    },
    {
      id: 'l5',
      concepto: 'Ventana de PVC con doble cristal',
      cantidad: 1,
      unidad: 'ud',
      precio: 350,
      tipo: 'material',
      origen: 'plano',
      medida: '1 hueco de 1,20 × 1,20'
    }
  ],
  foto: {
    pared: 'neutro',
    suelo: 'hormigon',
    luz: 'tarde',
    verAntes: false,
    luces: { techo: 0, aplique: 0, tira: 0 },
    prompt:
      'Pon el suelo de tarima de roble claro y las paredes en blanco roto. Cambia la puerta por una lacada en blanco.',
    propuestas: false,
    elegida: null,
    fallos: [],
    correccion: '',
    ronda: 0,
    verPrompt: false
  },
  precios: [
    { id: 'p1', concepto: 'Pintura plástica lisa', unidad: 'm2', precio: 8, tipo: 'mano_obra', usos: 15 },
    { id: 'p2', concepto: 'Alicatado de baño', unidad: 'm2', precio: 25, tipo: 'mixto', usos: 12 },
    { id: 'p3', concepto: 'Solado de gres 60×60', unidad: 'm2', precio: 35, tipo: 'mixto', usos: 9 },
    { id: 'p4', concepto: 'Tabique de ladrillo hueco doble', unidad: 'm2', precio: 45, tipo: 'mixto', usos: 7 },
    { id: 'p5', concepto: 'Retirada de escombro', unidad: 'global', precio: 180, tipo: 'mixto', usos: 6 },
    { id: 'p6', concepto: 'Puerta lacada blanca', unidad: 'ud', precio: 220, tipo: 'material', usos: 4 }
  ]
};

// ── cálculos: idénticos a la PWA (céntimos enteros) ────────────────────────
const redondear2 = (v) => Math.round((v + Number.EPSILON) * 100) / 100;
const subtotal = (l) =>
  redondear2((Number.isFinite(l.cantidad) ? l.cantidad : 0) * (Number.isFinite(l.precio) ? l.precio : 0));
const centimosBase = () => S.lineas.reduce((s, l) => s + Math.round(subtotal(l) * 100), 0);
const base = () => centimosBase() / 100;
// céntimos × tipo% / 100 = céntimos de IVA, redondeados como se hace en papel.
const cuotaIva = () => Math.round((centimosBase() * S.iva) / 100) / 100;
const total = () => (centimosBase() + Math.round(cuotaIva() * 100)) / 100;

const euros = (v) =>
  new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' }).format(
    Number.isFinite(v) ? v : 0
  );
const numero = (v) =>
  new Intl.NumberFormat('es-ES', { maximumFractionDigits: 2 }).format(Number.isFinite(v) ? v : 0);

// ── piezas ────────────────────────────────────────────────────────────────
const chipEstado = (e) =>
  `<span class="chip ${e}">${{ borrador: 'Borrador', enviado: 'Enviado', aceptado: 'Aceptado', rechazado: 'Rechazado', terminado: 'Terminado' }[e]}</span>`;

const tabs = (activa) => `
  <nav class="tabs">
    <button data-accion="ir" data-pantalla="lista" class="${activa === 'lista' ? 'activo' : ''}"><span class="icono">▤</span>Presupuestos</button>
    <button data-accion="ir" data-pantalla="precios" class="${activa === 'precios' ? 'activo' : ''}"><span class="icono">€</span>Mis precios</button>
    <button data-accion="ir" data-pantalla="ajustes" class="${activa === 'ajustes' ? 'activo' : ''}"><span class="icono">⚙</span>Ajustes</button>
  </nav>`;

const avisoHTML = () =>
  S.aviso
    ? `<div class="velo" style="background:transparent;align-items:flex-end;pointer-events:none">
         <div style="margin:0 12px 12px;background:#1c1917;color:#fff;border-radius:14px;padding:12px 14px;display:flex;gap:12px;align-items:center;font-size:13px;pointer-events:auto">
           <span style="flex:1">${S.aviso.texto}</span>
           ${S.aviso.accion ? `<button data-accion="deshacer" style="background:none;border:0;color:#f2b01e;font-weight:700;font-size:13px">${S.aviso.accion}</button>` : ''}
         </div>
       </div>`
    : '';

// ── pantallas ─────────────────────────────────────────────────────────────
function inicio() {
  const obras = [
    {
      id: 'mayor',
      nombre: 'Piso calle Mayor, 3º B',
      cliente: 'Carmen Ruiz',
      datos: '4 estancias · 48,2 m² · presupuesto 2026-001',
      chips: '<span class="chip plano">Plano hecho</span> <span class="chip borrador">Sin enviar</span>'
    },
    {
      id: 'bano',
      nombre: 'Reforma de baño',
      cliente: 'Juan Pérez',
      datos: '1 estancia · 5,4 m² · presupuesto 2025-014',
      chips: '<span class="chip enviado">Enviado</span>'
    },
    {
      id: 'trastero',
      nombre: 'Tabique en trastero',
      cliente: 'Comunidad Los Olmos',
      datos: '1 estancia · 6,0 m² · presupuesto 2025-013',
      chips: '<span class="chip aceptado">Aceptado</span>'
    }
  ];

  return `
  <div class="pantalla">
    <div class="barra">
      <h1>Obra</h1>
      <div class="derecha"><button class="boton secundario pequeno" data-accion="ir" data-pantalla="medir">Medir</button></div>
    </div>
    <div class="scroll">
      <h2 class="titulo">Buenos días, Manuel</h2>
      <p class="sub">Elige una obra para medir, enseñar el resultado o hacer el presupuesto.</p>

      ${obras
        .map(
          (o) => `
        <button class="tarjeta" style="display:block;width:100%;text-align:left" data-accion="abrir-obra" data-obra="${o.id}">
          <div class="fila entre" style="margin-bottom:6px">${o.chips}</div>
          <p style="margin:0;font-size:17px;font-weight:700">${o.nombre}</p>
          <p style="margin:2px 0 0;font-size:13.5px;color:var(--suave)">${o.cliente}</p>
          <p style="margin:8px 0 0;font-size:12px;color:var(--suave)">${o.datos}</p>
        </button>`
        )
        .join('')}

      <div class="tarjeta" style="background:var(--acento-claro);border-color:rgba(168,74,6,.25)">
        <p class="etiqueta" style="color:var(--acento)">Actualización</p>
        <p style="margin:4px 0 0;font-size:14px;line-height:1.45">
          Tienes la última versión (0.9.3). Se actualiza sola: no hay que borrar ni volver a instalar.
        </p>
      </div>
    </div>
  </div>`;
}

function obra() {
  return `
  <div class="pantalla">
    <div class="barra">
      <button class="volver" data-accion="ir" data-pantalla="inicio">‹ Obras</button>
      <div class="derecha"><span class="chip plano">Plano hecho</span></div>
    </div>
    <div class="scroll">
      <h2 class="titulo">Piso calle Mayor, 3º B</h2>
      <p class="sub">Carmen Ruiz · C/ Mayor 12, 3º B · 48,2 m² medidos</p>

      <div class="rejilla2" style="margin-bottom:14px">
        <button class="boton ancho" data-accion="ir" data-pantalla="medir">Medir</button>
        <button class="boton ancho secundario" data-accion="ir" data-pantalla="plano">Ver plano</button>
        <button class="boton ancho secundario" data-accion="ir" data-pantalla="ensenar">Enseñar</button>
        <button class="boton ancho" data-accion="ir" data-pantalla="editor">Presupuesto</button>
      </div>

      <div class="tarjeta">
        <div class="fila entre" style="margin-bottom:10px">
          <p class="etiqueta" style="margin:0">Lo que has medido</p>
          <button style="border:0;background:none;color:var(--acento);font-size:12px;font-weight:700" data-accion="ir" data-pantalla="plano">Ver plano</button>
        </div>
        ${[
          ['Salón', '4,00 × 3,00', '12,00 m²'],
          ['Cocina', '2,60 × 2,10', '5,46 m²'],
          ['Baño', '2,00 × 1,70', '3,40 m²'],
          ['Dormitorio', '3,20 × 2,80', '8,96 m²']
        ]
          .map(
            ([n, m, s]) => `
          <div class="item">
            <div class="cuerpo">
              <p class="nombre">${n}</p>
              <p class="detalle">${m} m</p>
            </div>
            <p class="importe">${s}</p>
          </div>`
          )
          .join('')}
      </div>

      <div class="tarjeta">
        <p class="etiqueta" style="margin:0 0 8px">Materiales que salen de las medidas</p>
        ${[
          ['Ladrillo hueco doble', '82 ud'],
          ['Saco de yeso 25 kg', '6 sacos'],
          ['Cemento cola 20 kg', '9 sacos'],
          ['Escombro a retirar', '1,2 m³']
        ]
          .map(
            ([n, c]) => `<div class="item"><div class="cuerpo"><p class="nombre">${n}</p></div><p class="importe">${c}</p></div>`
          )
          .join('')}
        <p style="margin:10px 0 0;font-size:12px;color:var(--suave)">Calculado de los metros de pared y suelo. Es una estimación para no quedarse corto.</p>
      </div>
    </div>
  </div>`;
}

function plano() {
  return `
  <div class="pantalla">
    <div class="barra">
      <button class="volver" data-accion="ir" data-pantalla="obra">‹ Obra</button>
      <div class="derecha"><span class="chip plano">Superficie 12,00 m²</span></div>
    </div>
    <div class="scroll">
      <div class="plano-marco">
        <svg viewBox="0 0 360 300" role="img" aria-label="Plano de la estancia con un tabique nuevo, una puerta y una ventana">
          <rect x="0" y="0" width="360" height="300" fill="#fbf9f5"/>
          <!-- suelo -->
          <rect x="40" y="35" width="280" height="210" fill="#f4efe4"/>
          <!-- muros exteriores -->
          <g stroke="#8c8378" stroke-width="9" stroke-linecap="square" fill="none">
            <line x1="40" y1="35" x2="90" y2="35"/>
            <line x1="174" y1="35" x2="320" y2="35"/>
            <line x1="320" y1="35" x2="320" y2="245"/>
            <line x1="40" y1="245" x2="225" y2="245"/>
            <line x1="281" y1="245" x2="320" y2="245"/>
            <line x1="40" y1="35" x2="40" y2="245"/>
          </g>
          <!-- ventana -->
          <g stroke="#1e4b8f" stroke-width="3" fill="none">
            <line x1="90" y1="31" x2="174" y2="31"/>
            <line x1="90" y1="39" x2="174" y2="39"/>
            <line x1="90" y1="31" x2="90" y2="39"/>
            <line x1="174" y1="31" x2="174" y2="39"/>
            <line x1="132" y1="31" x2="132" y2="39"/>
          </g>
          <!-- puerta exterior con arco -->
          <path d="M225 245 A56 56 0 0 1 281 189" stroke="#a84a06" stroke-width="2" fill="none" stroke-dasharray="4 4"/>
          <line x1="225" y1="245" x2="247" y2="199" stroke="#a84a06" stroke-width="3"/>
          <!-- tabique nuevo -->
          <g stroke="#a84a06" stroke-width="7" stroke-linecap="square">
            <line x1="180" y1="35" x2="180" y2="120"/>
            <line x1="180" y1="176" x2="180" y2="245"/>
          </g>
          <!-- puerta del tabique -->
          <path d="M180 120 A56 56 0 0 1 236 176" stroke="#a84a06" stroke-width="2" fill="none" stroke-dasharray="4 4"/>
          <!-- cotas -->
          <g fill="#6b6259" font-size="11" font-family="system-ui,sans-serif">
            <text x="168" y="268" text-anchor="middle">4,00 m</text>
            <text x="338" y="145" text-anchor="middle" transform="rotate(90 338 145)">3,00 m</text>
            <text x="186" y="66" fill="#a84a06">tabique nuevo</text>
            <text x="132" y="22" text-anchor="middle" fill="#1e4b8f">ventana 1,20</text>
            <text x="253" y="290" text-anchor="middle" fill="#a84a06">puerta 0,80</text>
          </g>
        </svg>
      </div>

      <div class="tarjeta">
        <div class="fila entre">
          <div><p class="etiqueta" style="margin:0">Superficie real</p><p class="cifra">12,00 <small>m²</small></p></div>
          <div style="text-align:right"><p class="etiqueta" style="margin:0">Perímetro</p><p class="cifra" style="font-size:22px">14,00 <small>m</small></p></div>
        </div>
        <p style="margin:10px 0 0;font-size:12.5px;color:var(--suave)">Medido con polígono, no con largo × ancho: si la estancia tiene un rincón, la superficie sigue siendo la buena.</p>
      </div>

      <div class="tarjeta">
        <p class="etiqueta" style="margin:0 0 8px">Toca para cambiar</p>
        <div class="banco">
          <button class="boton secundario pequeno" data-accion="nota" data-texto="Muro: toca dos puntos y queda recto.">Muro</button>
          <button class="boton secundario pequeno" data-accion="nota" data-texto="Puerta: 0,80 × 2,05. Se abre el hueco en el muro.">Puerta</button>
          <button class="boton secundario pequeno" data-accion="nota" data-texto="Ventana: 1,20 × 1,20. Resta pared y suma hueco.">Ventana</button>
        </div>
        <p style="margin:10px 0 0;font-size:12.5px;color:var(--suave)">Lo que pongas aquí es lo que después aparece como partida en el presupuesto.</p>
      </div>
    </div>
  </div>`;
}

function medir() {
  return `
  <div class="pantalla">
    <div class="barra">
      <button class="volver" data-accion="ir" data-pantalla="obra">‹ Obra</button>
      <div class="derecha"><span class="chip aceptado">Precisión ±2 cm</span></div>
    </div>
    <div class="scroll">
      <h2 class="titulo">Medir</h2>
      <p class="sub">Apunta a la pared y anda hasta la otra punta. Si la luz es mala, se mide a mano y listo.</p>

      <div class="tarjeta" style="background:var(--tinta);color:#fff">
        <p class="etiqueta" style="color:rgba(255,255,255,.6)">Última medida</p>
        <p class="cifra" style="margin:2px 0 0">12,00 <small style="color:rgba(255,255,255,.6)">m²</small></p>
        <p style="margin:4px 0 12px;font-size:13px;color:rgba(255,255,255,.7)">Salón · 4,00 × 3,00 m · guardado</p>
        <button class="boton ancho" data-accion="nota" data-texto="La cámara AR mide la estancia y coloca puertas y ventanas.">Medir con la cámara</button>
      </div>

      <div class="tarjeta">
        <p class="etiqueta" style="margin:0 0 8px">A mano</p>
        <div class="rejilla2">
          <div class="campo"><label>Largo</label><input value="4,00" readonly /></div>
          <div class="campo"><label>Ancho</label><input value="3,00" readonly /></div>
        </div>
        <button class="boton ancho secundario" data-accion="nota" data-texto="Se guarda la estancia y se actualiza el plano.">Guardar estancia</button>
      </div>

      <div class="tarjeta">
        <p class="etiqueta" style="margin:0 0 8px">Estancias medidas</p>
        ${[
          ['Salón', '12,00 m²'],
          ['Cocina', '5,46 m²'],
          ['Baño', '3,40 m²'],
          ['Dormitorio', '8,96 m²']
        ]
          .map(([n, s]) => `<div class="item"><div class="cuerpo"><p class="nombre">${n}</p></div><p class="importe">${s}</p></div>`)
          .join('')}
      </div>
    </div>
  </div>`;
}

function ensenar() {
  const antes = S.comparar === 'antes';
  return `
  <div class="pantalla">
    <div class="barra">
      <button class="volver" data-accion="ir" data-pantalla="obra">‹ Obra</button>
      <div class="derecha"><span class="chip plano">Listo para enseñar</span></div>
    </div>
    <div class="scroll">
      <h2 class="titulo">Enseñar al cliente</h2>
      <p class="sub">El mismo salón, antes y después. Toca la imagen para cambiar.</p>

      <button style="border:0;background:none;padding:0;width:100%" data-accion="comparar">
        <div class="comparar">
          <img src="img/${antes ? 'antes' : 'despues'}.png" alt="${antes ? 'Antes de la reforma' : 'Después de la reforma'}" />
          <span class="sello">${antes ? 'ANTES' : 'DESPUÉS'}</span>
        </div>
      </button>
      <p style="text-align:center;font-size:12px;color:var(--suave);margin:8px 0 14px">Toca la imagen para ver ${antes ? 'el después' : 'el antes'}.</p>

      <div class="banco" style="margin-bottom:14px">
        <button class="boton secundario pequeno" data-accion="comparar">Cambiar antes/después</button>
        <button class="boton secundario pequeno" data-accion="nota" data-texto="Se abre a pantalla completa y gira sola para enseñarlo en el móvil.">Pantalla completa</button>
      </div>

      <div class="tarjeta">
        <p class="etiqueta" style="margin:0 0 8px">Para mandar por WhatsApp</p>
        <div class="miniaturas">
          <figure><img src="img/despues.png" alt="Lámina del después" /><figcaption>Lámina 1080 px</figcaption></figure>
          <figure><img src="img/orbita-frame.png" alt="Fotograma del vídeo" /><figcaption>Vídeo de 8 s</figcaption></figure>
        </div>
        <p style="margin:10px 0 0;font-size:12.5px;color:var(--suave)">El vídeo va en GIF: se abre solo en WhatsApp, sin depender de códecs ni de datos.</p>
        <button class="boton ancho" style="margin-top:12px" data-accion="nota" data-texto="Se comparte la lámina o el vídeo por WhatsApp.">Compartir</button>
      </div>
    </div>
  </div>`;
}

function lista() {
  const presupuestos = [
    { numero: '2026-001', cliente: 'Carmen Ruiz', obra: 'Piso calle Mayor, 3º B', fecha: '20/11/2026', estado: S.estado, base: base(), n: S.lineas.length },
    { numero: '2025-014', cliente: 'Juan Pérez', obra: 'Reforma de baño', fecha: '12/11/2025', estado: 'enviado', base: 1245.09, n: 6 },
    { numero: '2025-013', cliente: 'Comunidad Los Olmos', obra: 'Tabique en trastero', fecha: '08/11/2025', estado: 'aceptado', base: 640, n: 3 }
  ];

  return `
  <div class="pantalla">
    <div class="barra">
      <h1>Presupuestos</h1>
      <div class="derecha"><button class="volver" data-accion="ir" data-pantalla="ajustes">Ajustes</button></div>
    </div>
    <div class="scroll" style="padding-bottom:96px">
      <p class="sub" style="margin-bottom:14px">${presupuestos.length} guardados · el que estás haciendo va siempre primero</p>

      <div class="tarjeta" style="padding:0">
        ${presupuestos
          .map(
            (p) => `
        <button style="display:flex;width:100%;gap:10px;text-align:left;background:none;border:0;border-bottom:1px solid var(--linea);padding:14px 16px;align-items:flex-start" data-accion="ir" data-pantalla="editor">
          <div style="flex:1;min-width:0">
            <div class="fila" style="gap:8px;margin-bottom:5px">${chipEstado(p.estado)}<span style="font-size:11.5px;color:var(--suave)">${p.fecha}</span></div>
            <p style="margin:0;font-size:15.5px;font-weight:700">${p.cliente}</p>
            <p style="margin:1px 0 0;font-size:12.5px;color:var(--suave)">${p.numero} · ${p.obra}</p>
          </div>
          <div style="text-align:right;white-space:nowrap">
            <p style="margin:0;font-size:15.5px;font-weight:700;font-variant-numeric:tabular-nums">${euros(p.base)}</p>
            <p style="margin:1px 0 0;font-size:11.5px;color:var(--suave)">${p.n} partidas</p>
          </div>
        </button>`
          )
          .join('')}
      </div>
    </div>
    <div style="position:absolute;left:0;right:0;bottom:58px;padding:0 16px 10px">
      <button class="boton ancho" data-accion="nuevo">Nuevo presupuesto</button>
    </div>
    ${tabs('lista')}
    ${avisoHTML()}
  </div>`;
}

function editor() {
  const listaLineas = S.lineas
    .map(
      (l) => `
    <div class="item">
      <div class="cuerpo">
        <p class="nombre">${l.concepto}</p>
        <p class="detalle">
          ${
            l.unidad === 'global'
              ? 'partida entera'
              : `${numero(l.cantidad)} ${UNIDADES[l.unidad]} × ${euros(l.precio)}`
          }
          ${l.medida ? ` · ${l.medida}` : ''}
        </p>
      </div>
            <div style="text-align:right">
        <p class="importe">${euros(subtotal(l))}</p>
        <div class="fila" style="justify-content:flex-end;gap:4px;margin-top:4px">
          ${l.unidad === 'global'
            ? ''
            : `<button data-accion="menos" data-id="${l.id}" aria-label="Quitar cantidad" style="width:26px;height:24px;border:1px solid var(--linea);background:#fff;border-radius:7px;line-height:1">−</button>
               <button data-accion="mas" data-id="${l.id}" aria-label="Añadir cantidad" style="width:26px;height:24px;border:1px solid var(--linea);background:#fff;border-radius:7px;line-height:1">+</button>`}
          <button class="borrar" aria-label="Borrar ${l.concepto}" data-accion="borrar" data-id="${l.id}" style="margin-left:6px">×</button>
        </div>
      </div>
    </div>`
    )
    .join('');

  return `
  <div class="pantalla">
    <div class="barra">
      <button class="volver" data-accion="ir" data-pantalla="lista">‹ Volver</button>
      <div style="flex:1;min-width:0">
        <p style="margin:0;font-size:14px;font-weight:700">2026-001</p>
        <p style="margin:0;font-size:11px;color:var(--suave)">Guardado</p>
      </div>
      ${chipEstado(S.estado)}
    </div>
    <div class="scroll" style="padding-bottom:12px">
      <div class="tarjeta">
        <div class="campo"><label>Cliente</label><input value="Carmen Ruiz" /></div>
        <div class="campo" style="margin-bottom:0"><label>Obra y dirección</label><input value="Piso calle Mayor, 3º B" /></div>
      </div>

      <div class="tarjeta">
        <div class="fila entre" style="margin-bottom:2px">
          <p class="etiqueta" style="margin:0">Partidas</p>
          <span style="font-size:12px;color:var(--suave)">${S.lineas.length}</span>
        </div>
        <p style="margin:0 0 6px;font-size:12.5px;line-height:1.4;color:var(--suave)">Todas salen del plano, con la cantidad ya medida. Puedes ajustarla con − y +.</p>
        ${listaLineas}
        <button class="boton ancho secundario" style="margin-top:12px" data-accion="nueva-partida">Añadir partida</button>
      </div>

      <div class="tarjeta">
        <p class="etiqueta" style="margin:0 0 8px">Estado</p>
        <div style="display:flex;gap:8px;flex-wrap:wrap">
          ${['borrador', 'enviado', 'aceptado', 'rechazado', 'terminado']
            .map(
              (e) =>
                `<button class="chip ${e}" style="height:32px;border:0;${
                  S.estado === e ? 'outline:2px solid rgba(28,25,23,.25)' : 'opacity:.55'
                }" data-accion="estado" data-estado="${e}">${{ borrador: 'Borrador', enviado: 'Enviado', aceptado: 'Aceptado', rechazado: 'Rechazado', terminado: 'Terminado' }[e]}</button>`
            )
            .join('')}
        </div>
      </div>

      <div class="tarjeta">
        <p class="etiqueta" style="margin:0 0 8px">IVA</p>
        <div style="display:flex;gap:8px">
          ${[0, 10, 21]
            .map(
              (t) =>
                `<button class="chip ${S.iva === t ? 'plano' : 'borrador'}" style="height:32px;border:0" data-accion="iva" data-iva="${t}">${
                  t === 0 ? 'Sin IVA' : `IVA ${t}%`
                }</button>`
            )
            .join('')}
        </div>
        <p style="margin:10px 0 0;font-size:12.5px;color:var(--suave)">El total se calcula sobre la suma de partidas, redondeada a céntimos.</p>
      </div>

      <div class="tarjeta">
        <p class="etiqueta" style="margin:0 0 8px">Más opciones</p>
        <div class="campo"><label>Validez</label><input value="30 días" readonly /></div>
        <div class="campo" style="margin-bottom:0"><label>Notas para el cliente</label><input value="Materiales incluidos. Plazo: 3 semanas." readonly /></div>
      </div>
    </div>

    <div class="pie-fijo">
      <div class="fila entre" style="margin-bottom:4px;font-size:12px;color:var(--suave)">
        <span>Base</span><span style="font-variant-numeric:tabular-nums">${euros(base())}</span>
      </div>
      ${S.iva > 0 ? `<div class="fila entre" style="margin-bottom:4px;font-size:12px;color:var(--suave)"><span>IVA ${S.iva}%</span><span style="font-variant-numeric:tabular-nums">${euros(cuotaIva())}</span></div>` : ''}
      <div class="fila entre">
        <div>
          <p class="etiqueta" style="margin:0">Total</p>
          <p style="margin:0;font-size:24px;font-weight:700;line-height:1.1;font-variant-numeric:tabular-nums">${euros(total())}</p>
        </div>
        <button class="boton" style="flex:1;max-width:190px" data-accion="nota" data-texto="Genera el PDF con tus datos y lo manda por WhatsApp como «Enviado».">Ver y enviar</button>
      </div>
    </div>
    ${avisoHTML()}
  </div>`;
}

function precios() {
  return `
  <div class="pantalla">
    <div class="barra">
      <h1>Mis precios</h1>
    </div>
    <div class="scroll" style="padding-bottom:96px">
      <p class="sub">Los que más usas, a un toque cuando haces un presupuesto. Se guardan solos al añadir una partida.</p>

      <div class="tarjeta" style="padding:0">
        ${S.precios
          .map(
            (p) => `
        <div class="item" style="padding:13px 16px">
          <div class="cuerpo">
            <p class="nombre">${p.concepto}</p>
            <p class="detalle">${
              p.unidad === 'global'
                ? `${euros(p.precio)} · partida entera`
                : `${euros(p.precio)} / ${UNIDADES[p.unidad]}`
            } · usado ${p.usos} ${p.usos === 1 ? 'vez' : 'veces'}</p>
          </div>
          <button class="borrar" aria-label="Borrar ${p.concepto}" data-accion="borrar-precio" data-id="${p.id}">×</button>
        </div>`
          )
          .join('')}
      </div>
    </div>
    <div style="position:absolute;left:0;right:0;bottom:58px;padding:0 16px 10px">
      <button class="boton ancho" data-accion="nuevo-precio">Añadir precio</button>
    </div>
    ${tabs('precios')}
    ${avisoHTML()}
  </div>`;
}

function ajustes() {
  return `
  <div class="pantalla">
    <div class="barra"><h1>Ajustes</h1></div>
    <div class="scroll" style="padding-bottom:96px">
      <p class="sub">Esto es lo que sale en el presupuesto que ve el cliente.</p>

      <div class="tarjeta">
        <div class="campo"><label>Nombre</label><input value="Manuel Ruiz" /></div>
        <div class="campo"><label>Oficio</label><input value="Albañilería" /></div>
        <div class="campo"><label>Teléfono</label><input value="600 000 000" /></div>
        <div class="campo" style="margin-bottom:0"><label>NIF (opcional)</label><input value="00000000A" /></div>
      </div>

      <div class="tarjeta">
        <div class="rejilla2">
          <div class="campo" style="margin-bottom:0"><label>Serie</label><input value="2026" readonly /></div>
          <div class="campo" style="margin-bottom:0"><label>Siguiente número</label><input value="002" readonly /></div>
        </div>
      </div>

      <div class="tarjeta">
        <p class="etiqueta" style="margin:0 0 8px">Copia de seguridad</p>
        <p style="margin:0 0 12px;font-size:13px;line-height:1.5;color:var(--suave)">Guarda un archivo con todos tus presupuestos y precios. Si cambias de móvil, lo abres y vuelve todo.</p>
        <button class="boton ancho secundario" data-accion="nota" data-texto="Copia guardada. Ya puedes mandártela por correo o guardarla en el móvil.">Guardar copia</button>
      </div>

      <div class="tarjeta">
        <p class="etiqueta" style="margin:0 0 8px">La app</p>
        <p style="margin:0;font-size:13.5px;line-height:1.5">Versión 0.9.3 · última · se actualiza sola</p>
      </div>
    </div>
    ${tabs('ajustes')}
    ${avisoHTML()}
  </div>`;
}

// ── foto real: capas sobre la foto, no geometría inventada ────────────────
const PAREDES = [
  { id: 'neutro', nombre: 'Yeso', color: '#efe9dc' },
  { id: 'blanco', nombre: 'Blanco roto', color: '#f7f4ec' },
  { id: 'salvia', nombre: 'Salvia', color: '#b6c2a8' },
  { id: 'perla', nombre: 'Gris perla', color: '#cfd3d1' },
  { id: 'arena', nombre: 'Arena', color: '#e3d3b8' },
  { id: 'azul', nombre: 'Azul humo', color: '#a9b7c0' }
];

const SUELOS = [
  { id: 'hormigon', nombre: 'Hormigón', color: '#c9c6bd' },
  { id: 'roble', nombre: 'Tarima roble', color: '#c99a63' },
  { id: 'gres', nombre: 'Gres gris', color: '#b9b6ae' },
  { id: 'micro', nombre: 'Microcemento', color: '#a8a39a' },
  { id: 'terrazo', nombre: 'Terrazo', color: '#dcd6c8' }
];

const LUCES = [
  { id: 'techo', nombre: 'Foco en el techo', detalle: 'Empotrado, luz cálida' },
  { id: 'aplique', nombre: 'Aplique en la pared', detalle: 'Pared izquierda, junto a la ventana' },
  { id: 'tira', nombre: 'Tira LED', detalle: 'Perímetro del techo' }
];

const nombreDe = (lista, id) => (lista.find((x) => x.id === id) || lista[0]).nombre;
const colorDe = (lista, id) => (lista.find((x) => x.id === id) || lista[0]).color;

// Polígonos medidos sobre la foto (1184 × 880).
const PARED_FONDO = '190,100 765,190 770,595 245,700';
const PARED_IZQUIERDA = '0,10 190,100 245,700 0,780';
const SUELO = '0,780 245,700 770,595 1184,640 1184,880 0,880';
const VENTANA = '4,38 158,96 158,517 4,497';

function capaFoto({ conLuces = true, conEsquinas = false } = {}) {
  const f = S.foto;
  const pared = colorDe(PAREDES, f.pared);
  const suelo = colorDe(SUELOS, f.suelo);
  const calido = f.luz === 'tarde';
  const frio = f.luz === 'fria';

  return `
  <div class="foto">
    <img src="img/habitacion.jpg" alt="Foto real de la habitación en obra" />
    <svg class="capa" viewBox="0 0 1184 880" preserveAspectRatio="none" aria-hidden="true">
      <defs>
        <clipPath id="recorte-ventana"><polygon points="${VENTANA}" /></clipPath>
        <radialGradient id="glow-techo">
          <stop offset="0" stop-color="#fff1c8" stop-opacity="0.95" />
          <stop offset="1" stop-color="#fff1c8" stop-opacity="0" />
        </radialGradient>
        <radialGradient id="glow-aplique">
          <stop offset="0" stop-color="#ffe3ad" stop-opacity="0.9" />
          <stop offset="1" stop-color="#ffe3ad" stop-opacity="0" />
        </radialGradient>
        <linearGradient id="luz-tarde" x1="0" y1="0" x2="1" y2="0.4">
          <stop offset="0" stop-color="#ffd9a0" stop-opacity="0.55" />
          <stop offset="0.55" stop-color="#ffcf8f" stop-opacity="0.18" />
          <stop offset="1" stop-color="#ffcf8f" stop-opacity="0" />
        </linearGradient>
        <linearGradient id="luz-fria" x1="0" y1="0" x2="1" y2="0.4">
          <stop offset="0" stop-color="#cfe2ff" stop-opacity="0.5" />
          <stop offset="1" stop-color="#cfe2ff" stop-opacity="0" />
        </linearGradient>
        <linearGradient id="tira-led" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stop-color="#fff4d0" stop-opacity="0.85" />
          <stop offset="1" stop-color="#fff4d0" stop-opacity="0" />
        </linearGradient>
      </defs>

      ${f.verAntes ? '' : `<g style="mix-blend-mode:multiply" opacity="0.55">
        <polygon points="${PARED_FONDO}" fill="${pared}" />
        <polygon points="${PARED_IZQUIERDA}" fill="${pared}" />
        <polygon points="${SUELO}" fill="${suelo}" />
      </g>`}

      <!-- la ventana y el sol vuelven a su sitio, nunca se tiñen -->
      <g clip-path="url(#recorte-ventana)">
        <image href="img/habitacion.jpg" width="1184" height="880" />
      </g>

      ${f.verAntes || !conLuces ? '' : `<g style="mix-blend-mode:screen">
        ${calido ? '<rect width="1184" height="880" fill="url(#luz-tarde)" opacity="0.55" />' : ''}
        ${frio ? '<rect width="1184" height="880" fill="url(#luz-fria)" opacity="0.6" />' : ''}
        ${f.luces.tira ? '<rect x="330" y="60" width="430" height="70" fill="url(#tira-led)" opacity="0.45" />' : ''}
        ${f.luces.techo ? '<circle cx="590" cy="185" r="250" fill="url(#glow-techo)" opacity="0.5" />' : ''}
        ${f.luces.aplique ? '<circle cx="78" cy="615" r="150" fill="url(#glow-aplique)" opacity="0.6" />' : ''}
      </g>`}

      ${conEsquinas
        ? [[190, 100], [765, 190], [770, 595], [245, 700]]
            .map(([x, y]) => `<circle cx="${x}" cy="${y}" r="13" fill="#fff" stroke="#a84a06" stroke-width="6" />`)
            .join('')
        : ''}
    </svg>
    ${conEsquinas ? '<span class="aviso-foto">4 esquinas · pared del fondo</span>' : ''}
    <span class="sello-foto">FOTO REAL · 1184 × 880</span>
  </div>`;
}

function foto() {
  const superficies = [
    ['Pared del fondo', '#efe9dc', '12,4 m²'],
    ['Pared izquierda', '#e9e3d5', '7,2 m²'],
    ['Suelo', '#c9c6bd', '12,0 m²'],
    ['Techo', '#f4f1e9', '12,0 m²'],
    ['Ventana', '#cfdff0', '1,44 m²'],
    ['Puerta', '#b9825b', '1,64 m²']
  ];

  return `
  <div class="pantalla">
    <div class="barra">
      <button class="volver" data-accion="ir" data-pantalla="obra">‹ Obra</button>
      <div class="derecha"><span class="chip plano">Foto del 20 nov</span></div>
    </div>
    <div class="scroll">
      <h2 class="titulo">Foto y superficies</h2>
      <p class="sub">Esta es la habitación de verdad, no un dibujo. Marca las esquinas de cada superficie y la app sabrá qué es pared, suelo y techo, y dónde están la ventana y la puerta.</p>

      ${capaFoto({ conLuces: false, conEsquinas: true })}

      <p class="etiqueta" style="margin:12px 0 6px">Lo que ha reconocido</p>
      <div class="superficies">
        ${superficies
          .map(
            ([n, c, m]) => `<div class="superficie"><span class="punto" style="background:${c}"></span>${n}<span class="m2">${m}</span></div>`
          )
          .join('')}
      </div>
      <p style="margin:10px 0 14px;font-size:12.5px;line-height:1.45;color:var(--suave)">La ventana y la puerta se quedan donde están: son las que dan la luz y la medida. Si una esquina no cae bien, se arrastra y listo.</p>

      <button class="boton ancho" data-accion="ir" data-pantalla="materiales">Seguir: materiales y luces</button>
    </div>
  </div>`;
}

function materiales() {
  return `
  <div class="pantalla">
    <div class="barra">
      <button class="volver" data-accion="ir" data-pantalla="foto">‹ Foto</button>
      <div class="derecha"><span class="chip plano">Sin IA · al instante</span></div>
    </div>
    <div class="scroll">
      ${capaFoto({})}
      <div class="fila entre" style="margin:10px 0 14px">
        <p style="margin:0;font-size:12px;color:var(--suave)">La foto manda: solo se tiñe y se ilumina.</p>
        <button class="chip borrador" style="border:1px solid var(--linea);background:#fff" data-accion="ver-antes">${S.foto.verAntes ? 'Ver después' : 'Ver antes'}</button>
      </div>

      <div class="tarjeta">
        <p class="etiqueta" style="margin:0 0 10px">Pared · ${nombreDe(PAREDES, S.foto.pared)}</p>
        <div class="paleta">
          ${PAREDES.map(
            (p) => `<button class="muestra ${S.foto.pared === p.id ? 'activa' : ''}" style="background:${p.color}" title="${p.nombre}" aria-label="${p.nombre}" data-accion="pared" data-id="${p.id}"></button>`
          ).join('')}
        </div>
      </div>

      <div class="tarjeta">
        <p class="etiqueta" style="margin:0 0 10px">Suelo · ${nombreDe(SUELOS, S.foto.suelo)}</p>
        <div class="paleta">
          ${SUELOS.map(
            (s) => `<button class="muestra ${S.foto.suelo === s.id ? 'activa' : ''}" style="background:${s.color}" title="${s.nombre}" aria-label="${s.nombre}" data-accion="suelo" data-id="${s.id}"></button>`
          ).join('')}
        </div>
        <p style="margin:10px 0 0;font-size:12px;line-height:1.45;color:var(--suave)">La tarima va en la dirección de la luz: se dibuja en perspectiva, no pegada plana.</p>
      </div>

      <div class="tarjeta">
        <p class="etiqueta" style="margin:0 0 10px">Luz que ya hay</p>
        <div style="display:flex;gap:6px">
          ${[
            ['tarde', 'Sol de tarde'],
            ['neutra', 'Nublado'],
            ['fria', 'Luz fría']
          ]
            .map(
              ([id, n]) => `<button class="chip ${S.foto.luz === id ? 'plano' : 'borrador'}" style="height:34px;border:0" data-accion="luz" data-id="${id}">${n}</button>`
            )
            .join('')}
        </div>
      </div>

      <div class="tarjeta">
        <p class="etiqueta" style="margin:0 0 10px">Luces que añades</p>
        ${LUCES.map(
          (l) => `
          <button class="opcion-luz ${S.foto.luces[l.id] ? 'encendida' : ''}" data-accion="foco" data-id="${l.id}">
            <span class="bombilla"></span>
            <span class="texto"><span class="nombre">${l.nombre}</span><br /><span class="detalle">${l.detalle}</span></span>
            <span class="estado">${S.foto.luces[l.id] ? 'Encendida' : 'Apagada'}</span>
          </button>`
        ).join('')}
        <p style="margin:6px 0 0;font-size:12px;line-height:1.45;color:var(--suave)">Cada luz enciende su zona en la foto y deja su sombra. Es lo que luego se pasa al electricista.</p>
      </div>

    </div>
  </div>`;
}

// ── hojas ─────────────────────────────────────────────────────────────────
function hojaPartida() {
  return `
  <div class="velo" data-accion="cerrar-hoja">
    <div class="hoja" data-detener="1">
      <h3>Nueva partida</h3>
      <p class="sub">Escribe o dicta. Si ya la tienes en Mis precios, es un toque.</p>

      <div class="campo"><label>¿Qué es?</label><input id="np-concepto" placeholder="Alicatado del baño" /></div>
      <div class="rejilla2">
        <div class="campo"><label>Cantidad</label><input id="np-cantidad" value="1" inputmode="decimal" /></div>
        <div class="campo"><label>Precio €</label><input id="np-precio" value="0" inputmode="decimal" /></div>
      </div>
      <div class="campo">
        <label>Unidad</label>
        <div style="display:flex;gap:6px;flex-wrap:wrap">
          ${['m2', 'ml', 'ud', 'h', 'global']
            .map(
              (u, i) =>
                `<button class="chip ${i === 0 ? 'plano' : 'borrador'}" style="height:34px;border:0" data-accion="unidad" data-unidad="${u}">${UNIDADES[u]}</button>`
            )
            .join('')}
        </div>
      </div>

      <p class="etiqueta" style="margin:0 0 6px">Mis precios</p>
      <div style="display:flex;gap:6px;flex-wrap:wrap;margin-bottom:14px">
        ${S.precios
          .slice(0, 4)
          .map(
            (p) =>
              `<button class="chip borrador" style="height:34px;border:1px solid var(--linea);background:#fff" data-accion="usar-precio" data-id="${p.id}">${p.concepto}</button>`
          )
          .join('')}
      </div>

      <div class="aviso" id="np-aviso">Va a salir 0,00 €</div>

      <div class="banco" style="margin-top:14px">
        <button class="boton ancho secundario" data-accion="cerrar-hoja">Cancelar</button>
        <button class="boton ancho" data-accion="guardar-partida">Añadir partida</button>
      </div>
    </div>
  </div>`;
}

function hojaPrecio() {
  return `
  <div class="velo" data-accion="cerrar-hoja">
    <div class="hoja" data-detener="1">
      <h3>Nuevo precio</h3>
      <p class="sub">Se guarda para la próxima vez.</p>
      <div class="campo"><label>¿Qué es?</label><input id="npr-concepto" placeholder="Enfoscado de pared" /></div>
      <div class="rejilla2">
        <div class="campo"><label>Precio €</label><input id="npr-precio" value="0" inputmode="decimal" /></div>
        <div class="campo"><label>Unidad</label><input value="m²" readonly /></div>
      </div>
      <div class="banco" style="margin-top:6px">
        <button class="boton ancho secundario" data-accion="cerrar-hoja">Cancelar</button>
        <button class="boton ancho" data-accion="guardar-precio">Guardar</button>
      </div>
    </div>
  </div>`;
}

// ── ficha lateral ─────────────────────────────────────────────────────────
const FICHAS = {
  inicio: {
    real: true,
    titulo: 'Inicio · obras',
    texto: 'Sustituye a la lista de presupuestos como puerta de entrada: primero la obra, y de la obra sale el presupuesto.',
    puntos: [
      'Tres obras con su estado real de trabajo.',
      'Aviso de actualización: <b>es la app la que se actualiza sola</b>, sin borrar nada.',
      'El botón Medir está siempre arriba, es lo que más se usa.'
    ]
  },
  obra: {
    real: true,
    titulo: 'La obra · resumen',
    texto: 'Una pantalla por obra con las cuatro cosas que hace: medir, plano, enseñar y presupuestar.',
    puntos: [
      'Los metros salen de las medidas guardadas.',
      'Los materiales se estiman de paredes, suelo y huecos.',
      'El presupuesto se abre ya con las partidas del plano.'
    ]
  },
  plano: {
    real: false,
    titulo: 'Plano 2D',
    texto: 'Esquema de lo que ya hace el editor nativo: tocar muro, abrir puerta, abrir ventana y superficie real.',
    puntos: [
      'Aquí es un dibujo; en la app es un editor táctil de verdad.',
      'La superficie se calcula por polígono, no por largo × ancho.',
      'Cada hueco se convierte en partida (puerta, ventana) y resta pintura.'
    ]
  },
  medir: {
    real: false,
    titulo: 'Medir',
    texto: 'La medición AR existe en la app (ARCore). Aquí se muestra el resultado y el plan B a mano.',
    puntos: [
      'La cámara mide la estancia; las medidas se pueden corregir a mano.',
      'Precisión declarada ±2 cm: sirve para presupuestar, no para carpintería de precisión.',
      'Nunca se queda bloqueado si la luz o el móvil no acompañan.'
    ]
  },
  ensenar: {
    real: true,
    titulo: 'Enseñar al cliente',
    texto: 'Aquí sí hay renders reales de la app: la imagen de antes y la de después son las que genera el motor 3D.',
    puntos: [
      'Antes/después con un toque.',
      'Lámina de 1080 px y vídeo GIF de 8 s para WhatsApp.',
      'El GIF se abre solo en WhatsApp, sin códecs.'
    ]
  },
  lista: {
    real: true,
    titulo: 'Presupuestos · lista',
    texto: 'Copiada de la PWA validada: estado, fecha, cliente, obra, importe y número de partidas.',
    puntos: [
      'Buscador por cliente, obra o número.',
      'Chips de estado con el mismo color que el PDF.',
      'Aviso de copia de seguridad cuando pasan días.'
    ]
  },
  editor: {
    real: true,
    titulo: 'Editor del presupuesto',
    texto: 'El corazón de la integración: las partidas del plano llegan ya escritas, con su cantidad medida, y se pueden tocar.',
    puntos: [
      'Cada partida viene de una medición del plano, no de la cabeza: cantidad, unidad y precio ya puestos.',
      'Los botones − y + cambian la cantidad y recalculan el total al instante.',
      'El total se suma en céntimos enteros, como en la PWA (nunca descuadra un céntimo).',
      'IVA 21 / 10 / sin IVA: la PWA no lo aplicaba y su especificación sí lo pedía. Aquí queda resuelto.'
    ]
  },
  precios: {
    real: true,
    titulo: 'Mis precios',
    texto: 'Catálogo que se llena solo: al guardar una partida, se recuerda su precio y su unidad.',
    puntos: [
      'Ordenado por lo que más usas.',
      'Un toque rellena concepto, precio y unidad.',
      'Se puede editar y borrar con deshacer.'
    ]
  },
  foto: {
    real: false,
    titulo: 'Foto y superficies',
    texto: 'El cambio de fondo: en vez de un 3D dibujado, la foto de verdad de la habitación como base.',
    puntos: [
      'Las esquinas se marcan tocando la foto, cuatro por superficie.',
      'La ventana y la puerta quedan clavadas: dan la luz y la medida.',
      'En la app esto ya no es un esquema: la foto se endereza en perspectiva y se recorta la superficie.'
    ]
  },
  materiales: {
    real: false,
    titulo: 'Materiales y luces',
    texto: 'Tiñe la foto con el material elegido y enciende luces donde irán. Sin IA, sin internet, al instante.',
    puntos: [
      'Los botones de color funcionan aquí y ahora: cambia la pared o el suelo y lo ves.',
      'La luz de la ventana no se toca: se tiñe por debajo de ella, con mezcla, no se pinta encima.',
      'Cada luz añadida se guarda con su posición y se pasa al presupuesto como punto de luz.'
    ]
  },
  ajustes: {
    real: true,
    titulo: 'Ajustes',
    texto: 'Los datos que salen en la cabecera del PDF y la numeración de los presupuestos.',
    puntos: [
      'Nombre, oficio, teléfono, NIF y dirección.',
      'Serie y siguiente número: 2026-001, 2026-002…',
      'Copia de seguridad en un archivo, sin cuentas ni nube.'
    ]
  }
};

// ── render ────────────────────────────────────────────────────────────────
const PANTALLAS = {
  inicio,
  obra,
  plano,
  medir,
  ensenar,
  foto,
  materiales,
  lista,
  editor,
  precios,
  ajustes
};

function render() {
  document.getElementById('pantalla').innerHTML = PANTALLAS[S.pantalla]();
  const f = FICHAS[S.pantalla];
  document.getElementById('ficha').innerHTML = `
    <span class="${f.real ? 'etiqueta-real' : 'etiqueta-mock'}">${f.real ? 'Real en la app' : 'Esquema'}</span>
    <h3>${f.titulo}</h3>
    <p>${f.texto}</p>
    <ul>${f.puntos.map((p) => `<li>${p}</li>`).join('')}</ul>
  `;
  document.querySelectorAll('.menu-item').forEach((b) => {
    b.classList.toggle('activo', b.dataset.pantalla === S.pantalla);
  });
  const hoja = S.hoja === 'partida' ? hojaPartida() : S.hoja === 'precio' ? hojaPrecio() : '';
  if (hoja) document.getElementById('pantalla').insertAdjacentHTML('beforeend', hoja);
  engancharHoja();
}

function engancharHoja() {
  const aviso = document.getElementById('np-aviso');
  if (!aviso) return;
  const campos = ['np-cantidad', 'np-precio'].map((id) => document.getElementById(id));
  const recalcular = () => {
    const c = parseFloat((campos[0].value || '0').replace(',', '.')) || 0;
    const p = parseFloat((campos[1].value || '0').replace(',', '.')) || 0;
    aviso.textContent = `Va a salir ${euros(redondear2(c * p))}`;
  };
  campos.forEach((c) => c && c.addEventListener('input', recalcular));
}

function avisar(texto, accion) {
  S.aviso = { texto, accion };
  render();
  window.clearTimeout(avisar.t);
  avisar.t = window.setTimeout(() => {
    S.aviso = null;
    render();
  }, 4200);
}

// ── acciones ──────────────────────────────────────────────────────────────
document.addEventListener('click', (ev) => {
  const objetivo = ev.target.closest('[data-accion]');
  if (!objetivo) return;
  const accion = objetivo.dataset.accion;

  if (objetivo.dataset.detener) {
    ev.stopPropagation();
    return;
  }

  switch (accion) {
    case 'ir':
      S.pantalla = objetivo.dataset.pantalla;
      S.hoja = null;
      render();
      break;

    case 'abrir-obra':
      S.pantalla = 'obra';
      render();
      break;

    case 'comparar':
      S.comparar = S.comparar === 'antes' ? 'despues' : 'antes';
      render();
      break;

    case 'estado':
      S.estado = objetivo.dataset.estado;
      render();
      break;

    case 'iva':
      S.iva = Number(objetivo.dataset.iva);
      render();
      break;

    case 'mas': {
      const l = S.lineas.find((x) => x.id === objetivo.dataset.id);
      if (l) l.cantidad = redondear2(l.cantidad + (l.unidad === 'ud' ? 1 : 0.5));
      render();
      break;
    }

    case 'menos': {
      const l = S.lineas.find((x) => x.id === objetivo.dataset.id);
      const paso = l.unidad === 'ud' ? 1 : 0.5;
      if (l) l.cantidad = Math.max(0, redondear2(l.cantidad - paso));
      render();
      break;
    }

    case 'borrar': {
      const i = S.lineas.findIndex((x) => x.id === objetivo.dataset.id);
      if (i >= 0) {
        const quitada = S.lineas[i];
        S.lineas.splice(i, 1);
        render();
        S.borrado = quitada;
        avisar('Partida borrada', 'Deshacer');
      }
      break;
    }

    case 'deshacer':
      if (S.borrado) {
        S.lineas.push(S.borrado);
        S.borrado = null;
      }
      S.aviso = null;
      render();
      break;

    case 'nueva-partida':
      S.hoja = 'partida';
      render();
      break;

    case 'cerrar-hoja':
      S.hoja = null;
      render();
      break;

    case 'usar-precio': {
      const p = S.precios.find((x) => x.id === objetivo.dataset.id);
      if (p) {
        document.getElementById('np-concepto').value = p.concepto;
        document.getElementById('np-precio').value = String(p.precio).replace('.', ',');
        engancharHoja();
        const aviso = document.getElementById('np-aviso');
        const c = parseFloat(document.getElementById('np-cantidad').value.replace(',', '.')) || 0;
        if (aviso) aviso.textContent = `Va a salir ${euros(redondear2(c * p.precio))}`;
      }
      break;
    }

    case 'guardar-partida': {
      const concepto = document.getElementById('np-concepto').value.trim();
      const cantidad = parseFloat(document.getElementById('np-cantidad').value.replace(',', '.')) || 0;
      const precio = parseFloat(document.getElementById('np-precio').value.replace(',', '.')) || 0;
      if (!concepto) {
        avisar('Pon qué es la partida');
        break;
      }
      S.lineas.push({
        id: 'n' + Date.now(),
        concepto,
        cantidad,
        unidad: 'm2',
        precio,
        tipo: 'mixto',
        origen: 'manual'
      });
      S.hoja = null;
      render();
      avisar('Partida añadida');
      break;
    }

    case 'nuevo':
      S.pantalla = 'editor';
      render();
      break;

    case 'nuevo-precio':
      S.hoja = 'precio';
      render();
      break;

    case 'guardar-precio': {
      const concepto = document.getElementById('npr-concepto').value.trim();
      const precio = parseFloat(document.getElementById('npr-precio').value.replace(',', '.')) || 0;
      if (!concepto) {
        avisar('Pon qué es el precio');
        break;
      }
      S.precios.unshift({ id: 'p' + Date.now(), concepto, unidad: 'm2', precio, tipo: 'mixto', usos: 0 });
      S.hoja = null;
      render();
      avisar('Precio guardado');
      break;
    }

    case 'borrar-precio': {
      const i = S.precios.findIndex((x) => x.id === objetivo.dataset.id);
      if (i >= 0) {
        S.precios.splice(i, 1);
        render();
        avisar('Precio borrado');
      }
      break;
    }

    case 'nota':
      avisar(objetivo.dataset.texto || 'Hecho');
      break;

    case 'ver-antes':
      S.foto.verAntes = !S.foto.verAntes;
      render();
      break;

    case 'pared':
      S.foto.pared = objetivo.dataset.id;
      render();
      break;

    case 'suelo':
      S.foto.suelo = objetivo.dataset.id;
      render();
      break;

    case 'luz':
      S.foto.luz = objetivo.dataset.id;
      render();
      break;

    case 'foco':
      S.foto.luces[objetivo.dataset.id] = S.foto.luces[objetivo.dataset.id] ? 0 : 1;
      render();
      break;

  }
});

render();
