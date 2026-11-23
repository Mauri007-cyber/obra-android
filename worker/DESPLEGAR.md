# Intermediario de IA: estado y seguridad

La integración de acceso y gasto **está escrita y probada**, pero la ruta de pago sigue
**cerrada por defecto**. Con `wrangler.jsonc` tal como está (`PROTECCION_ACTIVA=0`,
`MAX_LLAMADAS_TOTAL=0`, `MAX_LLAMADAS_USUARIO=0`, `SIMULADO=1`), `/retoque` solo devuelve la
foto original y `/quitar-fondo` responde 503. Ninguna petición llega al modelo.

**Si ya publicaste una versión anterior, sigue siendo vulnerable:** este cambio local no la
actualiza ni la detiene. Desactívala desde Cloudflare y no le añadas secretos.

## Qué hay implementado

- `src/index.js`: rutas `/salud`, `/retoque`, `/cupo` y `/quitar-fondo` (esta última sigue
  cerrada). Nada fuera de `/retoque` invoca al proveedor.
- `src/config.js`: configuración que **falla cerrada**. Sin emisor https, audiencia, lista de
  usuarios, JWK público EC P-256 y límites válidos, `activa` es `false`.
- `src/proteccion.js`: verificación de JWT **ES256** (emisor, audiencia, usuario permitido,
  `iat`/`nbf`/`exp`, duración máxima de 15 minutos, firma de 64 bytes) y el Durable Object
  `CupoGlobal`, con **una única instancia** (`idFromName('global')`).
- La reserva se hace **antes** de invocar al modelo y dentro de una transacción síncrona:
  incrementa el total y el del usuario o no incrementa ninguno. Dos peticiones simultáneas no
  pueden pasar del límite.
- Un fallo del modelo también consume cupo: no se devuelve la reserva, así que un reintento en
  bucle no puede gastar sin tope. La reserva solo se libera en el modo simulación, que no llama
  al proveedor.
- `/cupo` devuelve, para el usuario autenticado, las llamadas usadas y el máximo global y por
  usuario.

## Qué se ha verificado (y qué no)

```bash
npm --prefix worker test            # 50 pruebas: lógica, handler, config, pipeline, firma
npm --prefix worker run test:workerd # 9 pruebas reales en workerd
```

`test:workerd` arranca `wrangler dev` con `prueba/wrangler.jsonc` (no se despliega nunca) y
comprueba, con **workerd real, Durable Object SQLite real y firmas ES256 reales**:

- sin token, caducado, con otra audiencia o usuario no autorizado → 401 y cupo intacto;
- petición inválida → 400 y cupo intacto;
- fallo del proveedor → 502 **y cupo consumido**;
- llamada válida → imagen y descuento de cupo;
- límite por usuario aunque quede cupo global;
- 8 peticiones simultáneas: solo pasa el cupo global que queda y el resto recibe 429.

Lo que **no** está verificado: el proveedor real de FLUX (se sustituye por un simulador), el
coste real facturado por Cloudflare, el proveedor de identidad definitivo y el
comportamiento en un despliegue público con tráfico real.

## Para activarlo hace falta

1. **Proveedor de identidad** que emita tokens ES256 de corta duración con `iss`, `aud` y `sub`
   estables para cada instalación o cuenta autorizada, y su URL JWKS o su clave pública.
   El inicio de sesión Android con PKCE no está implementado: sin él no hay tokens reales.
2. **Secretos** en Cloudflare (nunca en el repositorio, nunca en la APK):

   ```bash
   cd worker
   npx wrangler secret put JWT_CLAVE        # JWK público EC P-256, sin "d"
   npx wrangler secret put JWT_ISSUER       # https://... del emisor
   npx wrangler secret put JWT_AUDIENCE     # p. ej. obra
   npx wrangler secret put USUARIOS_PERMITIDOS  # sub separados por comas
   ```

3. **Límites** en `wrangler.jsonc` (son variables, no secretos): `MAX_LLAMADAS_TOTAL` y
   `MAX_LLAMADAS_USUARIO`, enteros, usuario ≤ total. Después, desplegar:

   ```bash
   npx wrangler deploy
   ```

4. **Comprobación antes de abrir**: con `SIMULADO=1` y `PROTECCION_ACTIVA=1`, llamar a
   `/salud` (debe decir `proteccionActiva: true`, `serviciosDePagoActivos: false`), pedir
   `/cupo` con un token real y comprobar que rechaza tokens ajenos y caducados.
5. **Abrir el gasto** solo entonces: `SIMULADO=0` y `PROTECCION_ACTIVA=1`, desplegar y volver a
   comprobar `/salud` (ahora `serviciosDePagoActivos: true`). Fijar además un límite de gasto
   en el panel de Cloudflare: **el contador limita llamadas, no dinero**.

## Límites que siguen existiendo

- El contador de Durable Objects limita llamadas facturables; el precio por llamada depende del
  modelo y de Cloudflare y hay que comprobarlo con la factura real.
- Una caída del Worker entre la reserva y la llamada descuenta cupo sin generar imagen (el lado
  seguro del error). Las reservas caducan a las 24 h a efectos de limpieza, no se devuelven solas.
- Las fotos viajan al proveedor: hace falta consentimiento del cliente y revisar la política de
  datos antes de usarlo con obras reales.
- El resultado del modelo no garantiza medidas ni geometría: es una imagen orientativa.
- Nada de esto garantiza uso gratuito ilimitado.

## Apagar

Poner `PROTECCION_ACTIVA=0` y desplegar cierra la ruta al instante (o desactivar el Worker en
Cloudflare). El script `desplegar.cmd` es una herramienta anterior sin validar en Windows.
