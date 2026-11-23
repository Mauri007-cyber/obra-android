# Obra

App Android para medir una estancia, sacar el plano, presupuestar una reforma y **enseñar la
obra terminada** al cliente. **Es 100 % gratuita**: no hay cuentas, no hay nube obligatoria y no
se llama a ningún servicio de pago.

- Planteamiento del producto: [PLAN-OBRA-COMPLETA.md](PLAN-OBRA-COMPLETA.md)
- Hoja de ruta y estado: [ROADMAP.md](ROADMAP.md)

## Estado: 0.9.4

- **Proyecto por estancias**: cada obra tiene las habitaciones que haga falta y cada habitación
  una lista de cambios elegidos de un catálogo de más de 45 partidas por oficio.
- **Evaluación del cliente**: cada cambio se aprueba, se deja pendiente o se descarta. Lo
  aprobado es lo que cuenta para el dinero y para lo que se enseña.
- **Recorrido**: muestra la foto original y un listado de cambios aprobados por estancia.
  No se genera una imagen de la obra terminada.
- **Sin IA ni servicios de pago.** La app no envía fotos ni llama a ningún modelo: medir,
  dibujar el plano, presupuestar, el PDF, las copias y el resumen se calculan en el móvil.
  Todo funciona sin conexión y sin coste.
- La única conexión de red es la comprobación opcional de actualizaciones en GitHub.

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

## Sin servicios de pago

La app no incluye ningún cliente de IA, ni claves, ni direcciones de servicios externos. Se
retiraron la generación de imágenes y el recorte de fondo porque dependían de servicios que
pueden facturar, y el requisito es que la app sea gratuita para siempre. Si en el futuro se
añade algo opcional, tendrá que ser gratuito, funcionar sin cuenta y no estar nunca en el
camino crítico.

## Limitaciones importantes

- No se ha probado la app en un dispositivo real ni la migración de bases de datos con datos reales. Las pruebas instrumentadas se ejecutan en un emulador Android (API 35): restauran una copia con foto, planos y presupuesto, y recorren las pantallas de presupuesto, plano y copia con una base de datos en memoria. Faltan pruebas entre dispositivos, con fotos reales y de la cámara/AR.
- Cada estancia permite editar su superficie, altura y plano. El presupuesto incluye únicamente cambios aprobados por estancia y exige cantidades y precios válidos; sin aprobaciones no genera partidas. Hay que comprobar las medidas y los precios en obra.
- La APK de depuración usa el identificador `es.obra.app.prueba`: se instala como una app distinta, sin acceso a los datos ni a las actualizaciones de la versión normal.
- Existe una copia ZIP por obra con datos y fotos de estancias, y restauración como una obra nueva. La lectura comprueba la integridad del ZIP y que las fotos puedan decodificarse. Una prueba instrumentada ha pasado en emulador Android con datos de prueba; no se ha probado la restauración entre dispositivos con datos reales. No borres los originales basándote en esta copia. La exportación antigua `.obra` sigue sin fotos y no es restaurable.
- El manifiesto de GitHub puede apuntar a una versión anterior; publicar una actualización requiere verificar el APK firmado, su release y el manifiesto.

## Pruebas

```bash
scripts/build.sh :app:testDebugUnitTest
scripts/build.sh :app:connectedDebugAndroidTest # requiere emulador o dispositivo conectado
```

Las pruebas instrumentadas de `app/src/androidTest` cubren la restauración con foto, los planos
por estancia, el presupuesto (solo aprobados, totales y bloqueo de precios inválidos) y las
pantallas de copia. Se ejecutan en el emulador de GitHub Actions en cada subida a `main`.
