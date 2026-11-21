# Obra

App Android para medir y presupuestar reformas. El código completo de la app y del Worker todavía no está en este repositorio.

## Estado de distribución

`update.json` apunta a la versión **0.9.2**. No hay una release v0.9.4 verificada aquí; no se debe anunciar como disponible hasta subir y comprobar el APK firmado y actualizar el manifiesto.

## Limitaciones importantes

El recorrido de la versión de desarrollo muestra fotos originales y cambios aprobados: aún no genera una imagen de la obra terminada. La exportación no es una copia restaurable completa y no incluye los archivos de fotos.

El Worker de desarrollo no autentica peticiones. Su contador no garantiza límites de gasto; no lo expongas ni actives servicios de pago hasta protegerlo. La guía `worker/DESPLEGAR.md` debe revisarse antes de usarla.

No se ha validado la app en un dispositivo real ni el modelo de IA desplegado. Las actualizaciones requieren la misma clave de firma y confirmación en Android.
