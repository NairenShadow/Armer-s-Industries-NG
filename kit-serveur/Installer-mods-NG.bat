@echo off
setlocal EnableExtensions
cd /d "%~dp0"
title ShadowState-NG - installation des mods NationsGlory

rem Dossier du jeu NationsGlory installe par le launcher
set "SOURCE=%APPDATA%\gg.recube.launcher\games\nationsglory\stable"

echo ==========================================================
echo  Copie des mods et de la config NationsGlory vers le serveur
echo ==========================================================
echo Source : %SOURCE%
echo.
echo IMPORTANT : le serveur doit etre ARRETE (tape "stop" dans sa console).
echo Lance aussi le launcher NationsGlory une fois avant, pour qu'il
echo mette ses mods a jour.
echo.
pause

if exist "%SOURCE%\mods" goto source_ok
echo [ERREUR] Dossier introuvable : %SOURCE%\mods
echo Installe NationsGlory avec le launcher et lance le jeu une fois.
pause
exit /b 1

:source_ok
echo.
echo --- Copie des mods (le dossier mods du serveur devient identique a celui du jeu) ---
robocopy "%SOURCE%\mods" "mods" /MIR /R:1 /W:1 /NFL /NDL /NJH /NP
if errorlevel 8 goto erreur_copie

echo --- Copie de la config ---
robocopy "%SOURCE%\config" "config" /E /R:1 /W:1 /NFL /NDL /NJH /NP
if errorlevel 8 goto erreur_copie

echo.
echo --- Retrait des mods reserves au jeu sur PC (mods-client-a-exclure.txt) ---
for /f "usebackq eol=# delims=" %%m in ("mods-client-a-exclure.txt") do call :retirer "%%m"

echo.
echo Termine ! Tu peux lancer Demarrer.bat
pause
exit /b 0

:retirer
if exist "mods\%~1\" (
  rmdir /s /q "mods\%~1"
  echo   retire : %~1
  goto :eof
)
if exist "mods\%~1" (
  del /f /q "mods\%~1"
  echo   retire : %~1
)
goto :eof

:erreur_copie
echo.
echo [ERREUR] La copie a echoue. Le serveur ou le jeu est peut-etre encore ouvert.
pause
exit /b 1
