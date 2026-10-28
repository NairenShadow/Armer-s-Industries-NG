@echo off
setlocal EnableExtensions
cd /d "%~dp0"
title Serveur NationsGlory (ShadowState-NG)

rem ---------- Valeurs par defaut (remplacees par parametres.txt) ----------
set "JAVA=auto"
set "RAM_MIN=2G"
set "RAM_MAX=4G"
set "DELTAHEIGHT=non"
set "REDEMARRAGE_AUTO=oui"
set "SAUVEGARDE_AU_DEMARRAGE=oui"
set "SAUVEGARDES_A_GARDER=10"
if exist "parametres.txt" for /f "usebackq eol=# tokens=1,* delims==" %%a in ("parametres.txt") do set "%%a=%%b"

rem ---------- Recherche de Java 17 ----------
if /i not "%JAVA%"=="auto" goto java_verif
set "JAVA="
for /d %%d in ("%ProgramFiles%\Java\jdk-17*" "%ProgramFiles%\Eclipse Adoptium\jdk-17*" "%ProgramFiles%\Microsoft\jdk-17*" "%ProgramFiles%\Zulu\zulu-17*" "%ProgramFiles%\BellSoft\LibericaJDK-17*") do if exist "%%~d\bin\java.exe" set "JAVA=%%~d\bin\java.exe"
if defined JAVA goto java_verif
where java >nul 2>&1 && set "JAVA=java"
if defined JAVA goto java_verif
echo.
echo [ERREUR] Aucun Java trouve.
echo Installe Java 17 (Eclipse Temurin 17 JDK conseille : https://adoptium.net)
echo ou indique son chemin complet dans parametres.txt (ligne JAVA=...).
pause
exit /b 1

:java_verif
if /i "%JAVA%"=="java" goto java_ok
if exist "%JAVA%" goto java_ok
echo.
echo [ERREUR] Java introuvable a cet endroit : %JAVA%
echo Corrige la ligne JAVA=... dans parametres.txt
pause
exit /b 1

:java_ok
if exist "mods\ngcore.jar" goto mods_ok
echo.
echo [ATTENTION] Les mods NationsGlory ne sont pas installes dans ce serveur.
echo Lance d'abord Installer-mods-NG.bat (il les copie depuis le launcher).
pause
exit /b 1
:mods_ok
echo Java utilise : %JAVA%
"%JAVA%" -version

rem ---------- Nom du monde (level-name dans server.properties) ----------
set "MONDE=world"
for /f "usebackq eol=# tokens=1,* delims==" %%a in ("server.properties") do if "%%a"=="level-name" set "MONDE=%%b"
if "%MONDE%"=="" set "MONDE=world"

rem ---------- Agents Java ----------
set "AGENTS=-javaagent:mcpc-java17-agent.jar"
if /i "%DELTAHEIGHT%"=="oui" goto dh_actif
if not exist "%MONDE%\deltaheight.marqueur" goto boucle
echo.
echo [STOP] Ce monde a deja tourne avec DeltaHeight (monde de Y -64 a 319).
echo Le lancer sans DeltaHeight regenererait les chunks hors de 0..255 et abimerait la map.
echo Remets DELTAHEIGHT=oui dans parametres.txt.
echo (Si tu es SUR qu'aucun bloc n'a ete pose hors de 0..255, supprime le fichier
echo  %MONDE%\deltaheight.marqueur pour forcer.)
pause
exit /b 1

:dh_actif
if exist "DeltaHeight.jar" goto dh_jar_ok
echo [ERREUR] DELTAHEIGHT=oui mais DeltaHeight.jar est absent du dossier.
pause
exit /b 1
:dh_jar_ok
set "AGENTS=%AGENTS% -javaagent:DeltaHeight.jar"
powershell -NoProfile -Command "(Get-Content 'server.properties') -replace '^max-build-height=.*','max-build-height=320' | Set-Content -Encoding ASCII 'server.properties'"
if not exist "%MONDE%" mkdir "%MONDE%"
if not exist "%MONDE%\deltaheight.marqueur" echo Ce monde utilise DeltaHeight : ne pas lancer sans. > "%MONDE%\deltaheight.marqueur"
echo DeltaHeight active : monde de Y -64 a Y 319.

rem ---------- Boucle de lancement ----------
:boucle
if /i "%SAUVEGARDE_AU_DEMARRAGE%"=="oui" if exist "%~dp0Sauvegarder.bat" call "%~dp0Sauvegarder.bat" auto
echo.
echo ===== Demarrage du serveur (RAM %RAM_MIN% a %RAM_MAX%) =====
echo Serveur pret quand la console affiche : [NGUpgrades] Available plugins
echo Pour arreter proprement : tape "stop" dans cette fenetre.
echo.
rem -Djdk.reflect.useDirectMethodHandle=false : sans effet sur Java 17, indispensable sur Java 18 a 21
"%JAVA%" -Djdk.reflect.useDirectMethodHandle=false %AGENTS% -Xms%RAM_MIN% -Xmx%RAM_MAX% -jar server.jar nogui
if /i not "%REDEMARRAGE_AUTO%"=="oui" goto fin
echo.
echo Le serveur s'est arrete. Redemarrage dans 10 secondes...
echo (ferme la fenetre ou fais Ctrl+C pour ne pas redemarrer)
timeout /t 10
goto boucle

:fin
echo Serveur arrete.
pause
