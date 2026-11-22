# Intermediario de IA de Obra

**Estado: funciones de pago bloqueadas.** Las rutas `/retoque` y `/quitar-fondo` responden 503 antes de llamar a los proveedores. La configuración de un token o de un binding de IA no desbloquea estas rutas. El código anterior carecía de autenticación y su contador en Cache API no garantizaba un límite global de gasto.

Con `SIMULADO=1`, `/retoque` devuelve la foto original y `simulado: true`; no genera imágenes. `/quitar-fondo` sigue bloqueado. `/salud` informa `serviciosDePagoActivos: false`.

Esto solo protege la versión de código que se despliegue: **un Worker anterior ya publicado no cambia automáticamente**. Desactívalo en Cloudflare hasta actualizarlo. Antes de habilitar llamadas reales hace falta autenticación apropiada, autorización y un control de gasto global, atómico y resistente a abusos, además de revisar los precios y controles del proveedor. Una clave incluida en el APK no protege el servicio. Ningún límite de aplicación garantiza por sí solo gratuidad ilimitada.

Las fotos enviadas a servicios externos requieren revisar su tratamiento de datos y obtener los permisos correspondientes. El programa no ofrece todavía generación fotorrealista operativa ni verificada.

## Pruebas locales

```bash
node --test worker/prueba.mjs worker/prueba-handler.mjs worker/prueba-fondo.mjs
```

Las pruebas de proveedores usan simulaciones y no verifican ningún servicio real.
