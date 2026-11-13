@echo off
setlocal EnableExtensions DisableDelayedExpansion
title Connect AetherScript to Codex
set "CODEX_CLI=codex"
where codex >nul 2>nul
if not errorlevel 1 goto found_codex
for /f "usebackq delims=" %%P in (`powershell.exe -NoProfile -Command "(Get-AppxPackage OpenAI.Codex).InstallLocation"`) do set "CODEX_CLI=%%P\app\resources\codex.exe"
if not exist "%CODEX_CLI%" goto no_codex
:found_codex
echo Return to the browser where you opened your Minecraft console link.
echo Codex will open its authorization page so you can approve the connection.
call "%CODEX_CLI%" mcp add aetherscript-shared --url "https://aetherscript-relay.mistdig143.workers.dev/mcp"
if errorlevel 1 goto failed
echo Open a new Codex chat and ask: Use AetherScript to read my server status.
echo If Codex asks you to sign in, use the same browser as the console link.
pause
exit /b 0
:no_codex
echo Install Codex on this computer, then open this file again.
:failed
echo Setup did not finish. The pairing page has the connection address and help.
pause
exit /b 1
