@echo off
setlocal
title Instalador 1227
rem ------------------------------------------------------------------
rem  Abre o instalador do 1227 sem precisar do Java instalado no Windows.
rem  Usa o Java do sistema ou o Java que ja vem com o launcher do Minecraft.
rem  Deixe este arquivo na mesma pasta do 1227-x.y.z.jar e de dois cliques.
rem ------------------------------------------------------------------
cd /d "%~dp0"

rem Somente o jar do cliente: 1227.jar ou 1227-<versao>.jar (ex.: 1227-1.26.0.jar).
rem O plugin de servidor (1227-relay-*.jar) e outros jars da pasta nunca sao escolhidos.
set "JAR="
set /a COUNT=0
if exist "%~dp01227.jar" (
  set "JAR=%~dp01227.jar"
  set /a COUNT+=1
)
for %%f in ("%~dp01227-0*.jar" "%~dp01227-1*.jar" "%~dp01227-2*.jar" "%~dp01227-3*.jar" "%~dp01227-4*.jar" "%~dp01227-5*.jar" "%~dp01227-6*.jar" "%~dp01227-7*.jar" "%~dp01227-8*.jar" "%~dp01227-9*.jar") do (
  set "JAR=%%~ff"
  set /a COUNT+=1
)
if not defined JAR goto nojar
if %COUNT% GTR 1 goto many
echo Arquivo do mod: %JAR%
echo O instalador mostra a versao e a pasta de destino antes de copiar.

set "JAVAW="
for /f "delims=" %%j in ('where javaw 2^>nul') do if not defined JAVAW set "JAVAW=%%j"
if not defined JAVAW call :find "%LOCALAPPDATA%\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime"
if not defined JAVAW call :find "%ProgramFiles(x86)%\Minecraft Launcher\runtime"
if not defined JAVAW call :find "%ProgramFiles%\Minecraft Launcher\runtime"
if not defined JAVAW call :find "%APPDATA%\.minecraft\runtime"
if not defined JAVAW call :find "%LOCALAPPDATA%\Programs\Minecraft Launcher\runtime"
if not defined JAVAW call :find "%APPDATA%\.tlauncher"
if not defined JAVAW call :find "%ProgramFiles%\Eclipse Adoptium"
if not defined JAVAW call :find "%ProgramFiles%\Java"
if not defined JAVAW call :find "%ProgramFiles(x86)%\Java"
if not defined JAVAW goto nojava

echo Abrindo o instalador com: %JAVAW%
start "" "%JAVAW%" -jar "%JAR%"
exit /b 0

:find
if not exist "%~1" exit /b 0
for /f "delims=" %%j in ('dir /b /s "%~1\javaw.exe" 2^>nul') do if not defined JAVAW set "JAVAW=%%j"
exit /b 0

:many
echo.
echo Achei mais de um arquivo do cliente 1227 nesta pasta:
dir /b "%~dp01227.jar" "%~dp01227-0*.jar" "%~dp01227-1*.jar" "%~dp01227-2*.jar" "%~dp01227-3*.jar" "%~dp01227-4*.jar" "%~dp01227-5*.jar" "%~dp01227-6*.jar" "%~dp01227-7*.jar" "%~dp01227-8*.jar" "%~dp01227-9*.jar" 2>nul
echo.
echo Deixe aqui so a versao que voce quer instalar e abra este arquivo de novo.
echo.
pause
exit /b 1

:nojar
echo.
echo Nao achei o arquivo do cliente (1227-x.y.z.jar) nesta pasta.
echo (o 1227-relay-*.jar e o plugin de servidor, nao o mod)
echo Coloque este .bat na mesma pasta do jar e tente de novo.
echo.
pause
exit /b 1

:nojava
echo.
echo Nao achei nenhum Java neste computador.
echo.
echo Sem problema: instale copiando o arquivo.
echo   1. A pasta mods vai abrir agora.
echo   2. Copie para ela o arquivo: %JAR%
echo   3. Abra o Minecraft com o perfil Forge 1.8.9.
echo.
if not exist "%APPDATA%\.minecraft\mods" mkdir "%APPDATA%\.minecraft\mods"
start "" "%APPDATA%\.minecraft\mods"
pause
exit /b 1
