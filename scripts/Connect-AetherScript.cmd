@echo off
setlocal EnableExtensions DisableDelayedExpansion
title Connect AetherScript to Codex
set "MCP_JAR=%~dp0AetherScript-MCP-RELEASE_VERSION.jar"
set "INSTALL_DIR=%LOCALAPPDATA%\AetherScript"
set "JAVA_EXE=java.exe"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
set "CODEX_CLI=codex"
where codex >nul 2>nul
if not errorlevel 1 goto found_codex
for /f "usebackq delims=" %%P in (`powershell.exe -NoProfile -Command "(Get-AppxPackage OpenAI.Codex).InstallLocation"`) do set "CODEX_CLI=%%P\app\resources\codex.exe"
if not exist "%CODEX_CLI%" goto no_codex
:found_codex
if not exist "%MCP_JAR%" goto missing_jar
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"
if not exist "%INSTALL_DIR%\" goto failed
copy /y "%MCP_JAR%" "%INSTALL_DIR%\gateway.jar" >nul
if errorlevel 1 goto failed
echo In the Minecraft server console run: ais remote cloud-note
echo Paste the private pairing string at the prompt below.
"%JAVA_EXE%" -jar "%INSTALL_DIR%\gateway.jar" pair-cloud --profile "%INSTALL_DIR%\profiles\minecraft.json"
if errorlevel 1 goto failed
call "%CODEX_CLI%" mcp add aetherscript -- "%JAVA_EXE%" -jar "%INSTALL_DIR%\gateway.jar" serve --profile "%INSTALL_DIR%\profiles\minecraft.json"
if errorlevel 1 goto failed
echo Connected. Open a new Codex chat and ask it to list your AetherScript features.
pause
exit /b 0
:no_codex
echo Install Codex or add the codex command to your PATH, then open this file again.
goto failed
:missing_jar
echo Extract the full ZIP into one folder before running this file.
:failed
echo Setup did not finish. Check the message above and that Java 25 is installed.
pause
exit /b 1
