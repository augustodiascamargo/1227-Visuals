@echo off
setlocal
title Instalador 1227
rem ------------------------------------------------------------------
rem  Abre o instalador do 1227 sem precisar do Java instalado no Windows.
rem  Usa o Java do sistema ou o Java que ja vem com o launcher do Minecraft.
rem  Deixe este arquivo na mesma pasta do 1227-x.y.z.jar e de dois cliques.
rem ------------------------------------------------------------------
cd /d "%~dp0"

set "JAR="
for %%f in ("%~dp01227*.jar") do set "JAR=%%~ff"
if not defined JAR for %%f in ("%~dp0*.jar") do set "JAR=%%~ff"
if not defined JAR goto nojar

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

:nojar
echo.
echo Nao achei o arquivo do mod (1227-x.y.z.jar) nesta pasta.
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
