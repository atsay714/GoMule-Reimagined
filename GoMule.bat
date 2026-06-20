@echo off
rem Launches GoMule with the working directory set to this script's own folder.
rem Without this, Windows can launch a double-clicked .jar (or a shortcut without
rem "Start in" set) with the working directory pointing somewhere unrelated
rem (commonly C:\Windows\System32), and GoMule's data/config paths are relative,
rem so it fails to find d2111\ or create its projects\ folder.
cd /d "%~dp0"
start "" javaw -jar GoMule.jar
