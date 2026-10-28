@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "SAUVEGARDES_A_GARDER=10"
if exist "parametres.txt" for /f "usebackq eol=# tokens=1,* delims==" %%a in ("parametres.txt") do set "%%a=%%b"

set "MONDE=world"
for /f "usebackq eol=# tokens=1,* delims==" %%a in ("server.properties") do if "%%a"=="level-name" set "MONDE=%%b"
if "%MONDE%"=="" set "MONDE=world"

if exist "%MONDE%" goto sauver
echo Pas encore de monde "%MONDE%" : rien a sauvegarder.
goto fin

:sauver
if not exist "sauvegardes" mkdir "sauvegardes"
for /f %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HH-mm-ss"') do set "DATE_SAUV=%%t"
set "ARCHIVE=sauvegardes\%MONDE%_%DATE_SAUV%.zip"
echo Sauvegarde du monde "%MONDE%" vers %ARCHIVE% ...
tar -a -c -f "%ARCHIVE%" "%MONDE%" 2>nul
if not errorlevel 1 goto nettoyage
echo (tar indisponible, utilisation de PowerShell)
powershell -NoProfile -Command "Compress-Archive -Path '%MONDE%' -DestinationPath '%ARCHIVE%' -Force"

:nettoyage
powershell -NoProfile -Command "Get-ChildItem 'sauvegardes\*.zip' | Sort-Object LastWriteTime -Descending | Select-Object -Skip %SAUVEGARDES_A_GARDER% | Remove-Item -Force"
echo Sauvegarde terminee (les %SAUVEGARDES_A_GARDER% plus recentes sont gardees).

:fin
if /i not "%~1"=="auto" pause
