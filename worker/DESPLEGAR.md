# Intermediario de IA: estado y seguridad

Las rutas que podrían consumir Cloudflare Workers AI o ApyHub están **bloqueadas en el código actual** hasta implementar autenticación y un control global y atómico de gasto. Aunque configures `APY_TOKEN` o cambies `SIMULADO`, no se activan. Con `SIMULADO=1`, `/retoque` devuelve la imagen original; no es una generación real.

**Si ya publicaste una versión anterior, sigue siendo vulnerable:** este cambio local no la actualiza ni la detiene. Desactiva ese Worker desde Cloudflare hasta poder desplegar una versión protegida. No añadas secretos a la versión anterior.

Hay prototipos **desconectados** en `src/flux.js` (multipart de FLUX) y `src/proteccion.js` (firma ES256, lista de usuarios permitidos y contador global acumulado). Sus pruebas usan simulaciones: no demuestran el comportamiento de Durable Objects ni del modelo real. No sirven todavía para activar el servicio.

Antes de volver a ofrecer generación real se necesita, como mínimo:

- Integrar inicio de sesión Android seguro (PKCE), emitir tokens reales de corta duración y validar identidad, claves, revocación y usuarios autorizados en el Worker. La lista y clave públicas del prototipo no están configuradas.
- Integrar una sola instancia SQLite de Durable Objects, reservar antes de invocar y contar también fallos y concurrencia. Probarlo en workerd y comprobar que ninguna otra ruta invoca al proveedor. El contador acumulado limita llamadas, no dinero: fijar el coste máximo por llamada con precios vigentes y controlar el gasto real del proveedor.
- Probar los controles frente a abuso y fallos, y comprobar el servicio real en Cloudflare.
- Revisar consentimiento y protección de fotos y datos de clientes.

Nada de esto garantiza uso gratuito ilimitado. La generación fotorrealista todavía no está disponible ni validada para uso final.

El script `desplegar.cmd` es una herramienta anterior sin validar en Windows. Si falla, conserva `despliegue.log` para diagnosticarlo; no lo uses como prueba de que el servicio funciona o está protegido.
