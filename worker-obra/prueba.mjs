/**
 * Prueba del Worker sin desplegar nada: se le pasa un `env` falso con un AI que devuelve un
 * píxel y un KV en memoria. Comprueba rutas, validaciones, cuota y traducción de errores.
 *
 *   node worker-obra/prueba.mjs
 */
import worker from "./src/index.js";

const PIXEL =
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

let fallos = 0;

function comprobar(nombre, condicion, detalle = "") {
  if (condicion) {
    console.log(`ok   ${nombre}`);
  } else {
    fallos += 1;
    console.log(`FALLO ${nombre}${detalle ? ` → ${detalle}` : ""}`);
  }
}

function entorno({ conKv = true, falloIa = "", texto = "", vision = "", captura = null } = {}) {
  const guardado = new Map();
  return {
    AI: {
      async run(modelo, opciones) {
        if (falloIa) throw new Error(falloIa);
        if (opciones?.messages) return { response: texto };
        if (opciones?.image) return { response: vision };
        if (opciones?.multipart) {
          // El prompt viaja dentro del FormData: se guarda para poder comprobarlo.
          if (captura) captura.push(await new Response(opciones.multipart.body).text());
          return { image: PIXEL };
        }
        return { response: "" };
      }
    },
    CONTADORES: conKv
      ? {
          // El mapa queda a la vista de las pruebas para comprobar qué se ha gastado.
          _datos: guardado,
          async get(clave) {
            return guardado.get(clave) ?? null;
          },
          async put(clave, valor) {
            guardado.set(clave, valor);
          }
        }
      : undefined
  };
}

function peticion(cuerpo, ruta = "/imagen") {
  return new Request(`https://obra.test${ruta}`, {
    method: "POST",
    body: JSON.stringify(cuerpo),
    headers: { "content-type": "application/json" }
  });
}

const CATALOGO = [
  { clave: "PINTURA_PLASTICA", nombre: "Pintar paredes", unidad: "m²" },
  { clave: "PINTURA_TECHO", nombre: "Pintar el techo", unidad: "m²" },
  { clave: "PINTURA_ANTIHUMEDAD", nombre: "Pintura antihumedad", unidad: "m²" },
  { clave: "PAPEL_PINTADO", nombre: "Papel pintado", unidad: "m²" },
  { clave: "RODAPIE", nombre: "Rodapié nuevo", unidad: "m" }
];

const TEXTO_PROPUESTAS = JSON.stringify({
  propuestas: [
    {
      titulo: "Pintura",
      resumen: "Renovar sin obras",
      actuaciones: [
        { tipo: "PINTURA_PLASTICA", estancia: "Salon", cantidad: 20, motivo: "Paredes" },
        { tipo: "PINTURA_TECHO", estancia: "salón", cantidad: 20, motivo: "Techo" },
        { tipo: "TABIQUE_TIRADO", estancia: "Salón", cantidad: 4, motivo: "Fuera de catálogo" },
        { tipo: "PINTURA_PLASTICA", estancia: "Cocina", cantidad: 8, motivo: "No existe" }
      ]
    }
  ]
});

const cuerpoPropuestas = () => ({
  dispositivo: "movil-de-prueba-0001",
  superficie: 20,
  altura: 2.5,
  estancias: [{ nombre: "Salón", superficie: 20 }],
  permite: ["pintar"],
  deseo: "",
  catalogo: CATALOGO
});

const cuerpoValido = () => ({
  prompt: "Change only the floor to light oak laminate flooring.",
  foto: PIXEL,
  dispositivo: "movil-de-prueba-0001",
  ancho: 1024,
  alto: 768
});

async function main() {
  const salud = await worker.fetch(
    new Request("https://obra.test/salud"),
    entorno()
  );
  const datosSalud = await salud.json();
  comprobar("GET /salud responde", salud.status === 200 && datosSalud.ok === true);

  const sinPrompt = await worker.fetch(
    peticion({ ...cuerpoValido(), prompt: "" }),
    entorno()
  );
  comprobar("sin prompt responde 400", sinPrompt.status === 400);

  const sinFoto = await worker.fetch(
    peticion({ ...cuerpoValido(), foto: "" }),
    entorno()
  );
  comprobar("sin foto responde 400", sinFoto.status === 400);

  const grande = await worker.fetch(
    peticion({ ...cuerpoValido(), foto: "x".repeat(2_000_001) }),
    entorno()
  );
  comprobar("foto gigante responde 413", grande.status === 413);

  const ok = await worker.fetch(peticion(cuerpoValido()), entorno());
  const datosOk = await ok.json();
  comprobar(
    "genera y devuelve la imagen con restantes",
    ok.status === 200 && datosOk.imagen === PIXEL && datosOk.restantes === 19,
    JSON.stringify(datosOk)
  );
  comprobar("la imagen vuelve sin análisis si la visión no dice nada", datosOk.descripcion === "");

  // El modelo tiende a amueblar y decorar por su cuenta: el prompt final tiene que prohibirlo.
  const capturado = [];
  await worker.fetch(
    peticion(cuerpoValido()),
    entorno({ vision: "OPENINGS: one window on the left wall", captura: capturado })
  );
  const promptFinal = capturado.join(" ");
  comprobar(
    "el prompt final prohíbe añadir muebles o decoración",
    promptFinal.includes("do not add furniture or decoration") &&
      promptFinal.includes("Change only the floor"),
    promptFinal.slice(0, 200)
  );

  const sinKv = await worker.fetch(peticion(cuerpoValido()), entorno({ conKv: false }));
  comprobar(
    "sin contador sigue funcionando",
    sinKv.status === 200 && (await sinKv.json()).restantes === null
  );

  const agotado = entorno();
  let ultimo = null;
  for (let i = 0; i < 21; i += 1) {
    ultimo = await worker.fetch(peticion(cuerpoValido()), agotado);
  }
  const datosAgotado = await ultimo.json();
  comprobar(
    "la cuota por móvil corta en 20",
    ultimo.status === 429 && datosAgotado.error === "sin_cuota"
  );

  const cuotaIa = await worker.fetch(
    peticion(cuerpoValido()),
    entorno({ falloIa: "out of neurons for today" })
  );
  comprobar("la cuota de Cloudflare se traduce a 429", cuotaIa.status === 429);

  // Un fallo no puede dejar al móvil sin sus imágenes del día: la cuota se gasta al final.
  const conFalloContador = entorno({ falloIa: "model crashed" });
  await worker.fetch(peticion(cuerpoValido()), conFalloContador);
  comprobar(
    "una imagen fallida no gasta cuota",
    conFalloContador.CONTADORES._datos.size === 0,
    JSON.stringify([...conFalloContador.CONTADORES._datos.keys()])
  );

  const errorIa = await worker.fetch(
    peticion(cuerpoValido()),
    entorno({ falloIa: "model crashed" })
  );
  comprobar("un fallo del modelo es 502", errorIa.status === 502);

  const ruta = await worker.fetch(new Request("https://obra.test/otra"), entorno());
  comprobar("ruta desconocida responde 404", ruta.status === 404);

  const propuestas = await worker.fetch(
    peticion(cuerpoPropuestas(), "/propuestas"),
    entorno({ texto: TEXTO_PROPUESTAS })
  );
  const datosPropuestas = await propuestas.json();
  const actuaciones = datosPropuestas.propuestas?.[0]?.actuaciones ?? [];
  comprobar(
    "propuestas: la estancia sin tilde vale y la clave ajena se tira",
    propuestas.status === 200 &&
      actuaciones.length === 2 &&
      actuaciones.every((a) => a.tipo !== "TABIQUE_TIRADO" && a.estancia !== "Cocina"),
    JSON.stringify(datosPropuestas)
  );

  const sinCatalogo = await worker.fetch(
    peticion({ ...cuerpoPropuestas(), catalogo: [] }, "/propuestas"),
    entorno({ texto: TEXTO_PROPUESTAS })
  );
  comprobar("propuestas sin catálogo responde 400", sinCatalogo.status === 400);

  const conDepurar = await worker.fetch(
    peticion(cuerpoPropuestas(), "/propuestas?depurar=1"),
    entorno({ texto: TEXTO_PROPUESTAS })
  );
  comprobar(
    "propuestas ya no devuelve texto crudo ni con depurar=1",
    (await conDepurar.json()).bruto === undefined
  );

  const imagenDepurar = await worker.fetch(
    peticion(cuerpoValido(), "/imagen?depurar=1"),
    entorno()
  );
  comprobar(
    "imagen ya no devuelve el prompt final ni con depurar=1",
    (await imagenDepurar.json()).promptFinal === undefined
  );

  if (fallos > 0) {
    console.log(`\n${fallos} prueba(s) fallidas`);
    process.exit(1);
  }
  console.log("\nTodo correcto");
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
