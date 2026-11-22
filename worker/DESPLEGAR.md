# Intermediario de IA: estado y seguridad

Las rutas que podrían consumir Cloudflare Workers AI o ApyHub están **bloqueadas en el código actual** hasta implementar autenticación y un control global y atómico de gasto. Aunque configures `APY_TOKEN` o cambies `SIMULADO`, no se activan. Con `SIMULADO=1`, `/retoque` devuelve la imagen original; no es una generación real.

**Si ya publicaste una versión anterior, sigue siendo vulnerable:** este cambio local no la actualiza ni la detiene. Desactiva ese Worker desde Cloudflare hasta poder desplegar una versión protegida. No añadas secretos a la versión anterior.

Antes de volver a ofrecer generación real se necesita, como mínimo:

- Identificar y autorizar a los usuarios sin incluir credenciales privadas en el APK.
- Limitar de forma global y atómica todas las llamadas que puedan gastar, incluidas las fallidas y concurrentes, y revisar los límites y precios actuales del proveedor.
- Probar los controles frente a abuso y fallos, y comprobar el servicio real en Cloudflare.
- Revisar consentimiento y protección de fotos y datos de clientes.

Nada de esto garantiza uso gratuito ilimitado. La generación fotorrealista todavía no está disponible ni validada para uso final.

El script `desplegar.cmd` es una herramienta anterior sin validar en Windows. Si falla, conserva `despliegue.log` para diagnosticarlo; no lo uses como prueba de que el servicio funciona o está protegido.
