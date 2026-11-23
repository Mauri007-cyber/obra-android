# Obra

App Android para medir una estancia, sacar el plano, presupuestar una reforma y **enseñar la
obra terminada** al cliente. Sin cuenta ni nube obligatoria para las funciones locales; los servicios externos pueden tener coste.

- Replanteo del producto: [PLAN-OBRA-COMPLETA.md](PLAN-OBRA-COMPLETA.md)
- Hoja de ruta y estado: [ROADMAP.md](ROADMAP.md)
- Intermediario de IA: [worker/DESPLEGAR.md](worker/DESPLEGAR.md)

## Estado: 0.9.4

- **Proyecto por estancias**: cada obra tiene las habitaciones que haga falta y cada habitación
  una lista de cambios elegidos de un catálogo de más de 45 partidas por oficio.
- **Evaluación del cliente**: cada cambio se aprueba, se deja pendiente o se descarta. Lo
  aprobado es lo que cuenta para el dinero y para lo que se enseña.
- **Recorrido**: muestra la foto original y un listado de cambios aprobados por estancia; todavía no genera una imagen de la obra terminada.
- **IA opcional**: se puede preparar un retoque desde una estancia con su foto, medidas y cambios visibles aprobados, además de instrucciones editables. La integración real del modelo no está habilitada ni validada; las imágenes generadas no garantizan respetar medidas o geometría.
- La IA **nunca** está en el camino crítico: apagada por defecto y sin claves en el APK.
- **IA y recorte remotos bloqueados por defecto:** el Worker ya tiene la integración completa de identidad ES256 y cuota global en un único Durable Object, probada en workerd, pero `PROTECCION_ACTIVA=0` la mantiene cerrada hasta configurar un proveedor de identidad real y comprobar el gasto. La simulación devuelve la foto original.

## Distribución y actualizaciones

- `update.json`: lo consulta la app al abrir para saber si hay versión nueva.
- Releases: contienen `obra-X.Y.Z.apk`. La app descarga, comprueba el SHA-256 y lanza el
  instalador. Android siempre pide confirmación: es una actualización con un toque, no silenciosa.
- Para publicar: subir el APK como asset de una release `vX.Y.Z` y actualizar `update.json` con
  `versionCode`, `versionName`, `apkUrl` y `sha256`.

## Compilar

El toolchain vive fuera del repo (`$HOME/toolchain`): JDK, Gradle 8.13 y Android SDK.

```bash
scripts/build.sh :app:assembleDebug     # APK de pruebas
scripts/build.sh :app:assembleRelease   # APK firmada
```

### La firma es lo que permite actualizar

Todas las versiones deben ir firmadas con la misma clave. La del proyecto está fuera de Git
  (`$HOME/obra-keys/obra-release.jks` y `keystore.properties`). **Si se pierde, no se puede
actualizar**: habría que desinstalar y reinstalar. Verifica que tienes una copia segura de la clave y de los datos.

## Limitaciones importantes

- No se ha probado la app en un dispositivo real ni la migración de bases de datos con datos reales. Una prueba instrumentada ejecutada en emulador Android ejercita la restauración con una foto de prueba; faltan pruebas entre dispositivos y de la interfaz.
- Cada estancia permite editar su superficie, altura y plano. El presupuesto incluye únicamente cambios aprobados por estancia y exige cantidades y precios válidos; sin aprobaciones no genera partidas. Hay que comprobar las medidas y los precios en obra.
- La APK de depuración usa el identificador `es.obra.app.prueba`: se instala como una app distinta, sin acceso a los datos ni a las actualizaciones de la versión normal.
- Existe una copia ZIP por obra con datos y fotos de estancias, y restauración como una obra nueva. La lectura comprueba la integridad del ZIP y que las fotos puedan decodificarse. Una prueba instrumentada ha pasado en emulador Android con datos de prueba; no se ha probado la restauración entre dispositivos con datos reales. No borres los originales basándote en esta copia. La exportación antigua `.obra` sigue sin fotos y no es restaurable.
- Las llamadas de pago del Worker están bloqueadas por defecto. Una versión anterior ya desplegada sigue siendo insegura hasta desactivarla o actualizarla. La verificación de identidad y la cuota global están implementadas y probadas en workerd, pero falta el proveedor de identidad real y comprobar el coste facturado antes de abrirlas; ver `worker/DESPLEGAR.md`.
- El manifiesto de GitHub puede apuntar a una versión anterior; publicar una actualización requiere verificar el APK firmado, su release y el manifiesto.

## Intermediario de IA

Las llamadas de pago están desactivadas por defecto; consulta [worker/DESPLEGAR.md](worker/DESPLEGAR.md) antes de desplegar. La protección (token ES256 + cuota global atómica) está integrada y cubierta por pruebas reales en workerd, pero sin proveedor de identidad ni límite de dinero comprobado no se debe abrir. El script de Windows aún no está probado en un equipo real.

## Pruebas

```bash
npm --prefix worker test              # lógica, handler, configuración y pipeline de la IA
npm --prefix worker run test:workerd  # Durable Object, cuota global y concurrencia en workerd
scripts/build.sh :app:testDebugUnitTest
scripts/build.sh :app:connectedDebugAndroidTest # requiere emulador o dispositivo conectado
```
