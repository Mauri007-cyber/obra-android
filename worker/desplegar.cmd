@echo off
setlocal EnableExtensions
cd /d "%~dp0"
set "LOG=%~dp0despliegue.log"
break > "%LOG%"

echo ==================================================
echo  Obra - intermediario de IA en Cloudflare
echo ==================================================
echo.

rem ---------- 1. Node presente y moderno ----------
where node >nul 2>nul
if errorlevel 1 goto sin_node

for /f "delims=" %%v in ('node -v') do set "NODEV=%%v"
for /f "tokens=1 delims=." %%a in ('echo %NODEV:v=%') do set "MAJOR=%%a"

echo Node detectado: %NODEV%
echo Node detectado: %NODEV% >> "%LOG%"

rem Si no se ha podido leer la version, mejor no seguir con una comparacion rota.
if "%MAJOR%"=="" goto node_viejo
if %MAJOR% LSS 20 goto node_viejo

rem ---------- 2. Wrangler sin npm install ----------
echo.
echo [1/3] Preparando Wrangler...
echo       La primera vez descarga el programa. Puede tardar un par de minutos.
call npx --yes wrangler@4 --version >> "%LOG%" 2>&1
if errorlevel 1 goto fallo_wrangler

rem ---------- 3. Sesion de Cloudflare ----------
echo.
echo [2/3] Comprobando la sesion de Cloudflare...
call npx --yes wrangler@4 whoami >> "%LOG%" 2>&1
if errorlevel 1 (
  echo       No hay sesion. Se va a abrir el navegador.
  echo       Entra en Cloudflare y pulsa "Allow".
  echo.
  call npx --yes wrangler@4 login
  if errorlevel 1 goto fallo_login
) else (
  echo       Ya estabas dentro de Cloudflare.
)

rem ---------- 4. Desplegar ----------
echo.
echo [3/3] Desplegando el Worker...
echo       Espera sin cerrar la ventana.
call npx --yes wrangler@4 deploy >> "%LOG%" 2>&1
set "DEPLOY=%ERRORLEVEL%"

echo.
type "%LOG%"
if not "%DEPLOY%"=="0" goto fallo_deploy

echo.
echo ==================================================
echo  LISTO
echo ==================================================
echo.
echo Esta es tu direccion (copiala tal cual):
echo.
findstr /C:".workers.dev" "%LOG%"
echo.
echo Pegala en la app:
echo   Ajustes de IA - Direccion del intermediario
echo.
echo Para probarla, abre en el navegador:
echo   (la direccion de arriba)/salud
echo   Debe contestar: {"ok":true,...}
echo.
pause
exit /b 0

rem ---------------- errores ----------------

:sin_node
echo [ERROR] No encuentro Node.js en este ordenador.
echo.
echo Instala la version LTS (20 o superior) desde:
echo   https://nodejs.org
echo Marca la opcion de anadir Node al PATH, cierra esta ventana y vuelve a intentarlo.
echo.
pause
exit /b 1

:node_viejo
echo [ERROR] Tienes Node %NODEV%, y Wrangler necesita Node 20 o superior.
echo.
echo Descarga la version LTS desde https://nodejs.org, instalala y vuelve a ejecutar.
echo.
pause
exit /b 1

:fallo_wrangler
echo [ERROR] No se ha podido preparar Wrangler.
echo.
findstr /I /C:"EACCES" /C:"EPERM" "%LOG%" >nul && (
  echo Parece un problema de permisos: descomprime el ZIP en una carpeta normal,
  echo por ejemplo C:\obra-worker, y no dentro de Archivos de programa.
)
findstr /I /C:"ENOTFOUND" /C:"ETIMEDOUT" /C:"network" "%LOG%" >nul && (
  echo Parece un problema de conexion: comprueba internet o el cortafuegos.
)
echo.
echo El detalle esta en: %LOG%
start "" notepad "%LOG%"
pause
exit /b 1

:fallo_login
echo [ERROR] No se ha podido entrar en Cloudflare.
echo.
echo Vuelve a ejecutar este archivo y pulsa "Allow" en el navegador.
echo Si el navegador no se abre, ejecuta a mano en esta carpeta:
echo   npx wrangler@4 login --browser=false
echo.
echo El detalle esta en: %LOG%
start "" notepad "%LOG%"
pause
exit /b 1

:fallo_deploy
echo.
echo [ERROR] El despliegue no ha terminado bien.
echo.
findstr /I /C:"workers.dev subdomain" /C:"register a workers.dev" "%LOG%" >nul && (
  echo CAUSA: tu cuenta de Cloudflare todavia no tiene subdominio workers.dev.
  echo ARREGLO: abre el panel, crea el subdominio y vuelve a ejecutar este archivo.
  start "" "https://dash.cloudflare.com/"
)
findstr /I /C:"not authenticated" /C:"Authentication error" /C:"10000" "%LOG%" >nul && (
  echo CAUSA: la sesion de Cloudflare no es valida.
  echo ARREGLO: vuelve a ejecutar y pulsa "Allow" cuando se abra el navegador.
)
findstr /I /C:"workers.api.error.unauthorized" /C:"permission" "%LOG%" >nul && (
  echo CAUSA: a la cuenta le falta permiso de Workers.
  echo ARREGLO: entra en el panel de Cloudflare, abre Workers y activa el plan gratuito.
)
findstr /I /C:"already exists" /C:"duplicate" "%LOG%" >nul && (
  echo AVISO: parece que ya habia un Worker con ese nombre. No pasa nada:
  echo la nueva version sustituye a la anterior.
)
echo.
echo Copia TODO el texto de arriba y mandalo al chat. Con eso se arregla.
echo Tambien queda guardado en:
echo   %LOG%
start "" notepad "%LOG%"
pause
exit /b 1
