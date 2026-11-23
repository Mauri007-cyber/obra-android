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

function entorno({ conKv = true, falloIa = "" } = {}) {
  const guardado = new Map();
  return {
    AI: {
      async run() {
        if (falloIa) throw new Error(falloIa);
        return { image: PIXEL };
      }
    },
    CONTADORES: conKv
      ? {
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

function peticion(cuerpo) {
  return new Request("https://obra.test/imagen", {
    method: "POST",
    body: JSON.stringify(cuerpo),
    headers: { "content-type": "application/json" }
  });
}

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

  const errorIa = await worker.fetch(
    peticion(cuerpoValido()),
    entorno({ falloIa: "model crashed" })
  );
  comprobar("un fallo del modelo es 502", errorIa.status === 502);

  const ruta = await worker.fetch(new Request("https://obra.test/otra"), entorno());
  comprobar("ruta desconocida responde 404", ruta.status === 404);

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
