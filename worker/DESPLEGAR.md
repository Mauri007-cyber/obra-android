# Desplegar el intermediario (Cloudflare Workers)

La app no lleva claves: llama al Worker, y el Worker usa tu cuenta de Cloudflare (gratis).

## Requisitos

- Node.js 20 o superior. Con 18 o menos, Wrangler no arranca.
- Cuenta de Cloudflare.

## Windows

1. Descomprime el ZIP en una carpeta normal, por ejemplo C:\obra-worker.
2. Doble clic en desplegar.cmd.
3. Pulsa Allow en el navegador.
4. Copia la direccion https://obra-ia.TU-USUARIO.workers.dev y pegala en la app:
   Ajustes de IA -> Direccion del intermediario.

El script no hace npm install: usa npx wrangler@4, que era el paso que mas fallaba.

## Probar

Abre (direccion)/salud. Debe contestar {"ok":true,...}.
Si sale simulado:true, el Worker no ve el modelo: revisa el bloque ai de wrangler.jsonc.

## Si falla

No cierres la ventana: se abre despliegue.log con el error exacto. Causas conocidas:

- workers.dev subdomain: la cuenta aun no tiene subdominio. Crealo en el panel y repite.
- not authenticated / 10000: sesion no valida. Repite el login.
- EBADENGINE: Node viejo. Instala Node 20+.
- EACCES / EPERM: carpeta sin permisos. Muevela fuera de Program Files.
- ENOTFOUND / ETIMEDOUT: sin red o cortafuegos.

Si el navegador no se abre: npx wrangler@4 login --browser=false

## Limite del modelo

FLUX.2 klein solo acepta fotos de entrada menores de 512x512. La app las reduce desde la 0.9.4
y el Worker rechaza con un aviso claro las que se pasen.

## Ajustes

- MAX_DIA: imagenes por dispositivo y dia (30).
- MODELO: @cf/black-forest-labs/flux-2-klein-4b
- SIMULADO: "1" devuelve la misma foto sin gastar cupo.
