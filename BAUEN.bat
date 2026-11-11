@echo off
rem Baut die Mod und legt die RICHTIGE (fuer das Spiel umgewandelte) JAR in den Ordner FERTIG.
cd /d "%~dp0"

if exist "C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot\bin\java.exe" set "JAVA_HOME=C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot"
call gradlew.bat build
if errorlevel 1 (
	echo.
	echo Build fehlgeschlagen. Schick die Fehlermeldung oben.
	pause
	exit /b 1
)

if not exist FERTIG mkdir FERTIG
del /q FERTIG\*.jar 2>nul

rem Nur build\libs nehmen - NICHT build\devlibs (die -dev.jar crasht im Spiel)
for %%F in (build\libs\zero-client-*.jar) do (
	echo %%~nxF | findstr /i /c:"-sources" /c:"-dev" >nul || copy /y "%%F" FERTIG\ >nul
)

echo.
echo Fertig! Die JAR liegt in: %~dp0FERTIG
echo Nur diese JAR in den mods-Ordner legen und alte quiqzy-/zero-JARs vorher loeschen.
explorer "%~dp0FERTIG"
pause
