# Obra

App Android para medir, presupuestar y ensenar una reforma. Coste 0 EUR, sin cuenta.

**Estado 0.9.4**

- Proyecto por estancias: catalogo de mas de 45 cambios por oficio, con aprobacion del cliente.
- Recorrido final con solo lo aprobado.
- IA opcional (Cloudflare Workers AI, FLUX.2 klein): apagada por defecto y sin claves en el APK.
- APK firmada en la release v0.9.4.

**Documentacion**

- PLAN-OBRA-COMPLETA.md: el replanteo del producto.
- ROADMAP.md: hitos y estado.
- worker/DESPLEGAR.md: desplegar el intermediario.

**Actualizaciones**

La app consulta update.json, descarga el APK de la release, verifica el SHA-256 e instala encima.
