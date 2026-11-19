@echo off
rem Runs a Scheme sketch without the Processing IDE: run.bat sketch.scm
set HERE=%~dp0
set SKETCH=%~1
if "%SKETCH%"=="" set SKETCH=%HERE%examples\Basics\HelloCircle\HelloCircle.scm
where java >nul 2>nul || (echo Java 17 or newer is needed to turn Scheme into Python: https://adoptium.net & exit /b 1)
for %%F in ("%SKETCH%") do (set DIR=%%~dpF& set NAME=%%~nF)
if not exist "%DIR%build" mkdir "%DIR%build"
java -cp "%HERE%lib\SchemeMode.jar" processing.mode.scheme.SchemeCli "%SKETCH%" "%DIR%build\%NAME%.py" || exit /b 1
if exist "%DIR%data" (rmdir /s /q "%DIR%build\assets" 2>nul & xcopy /e /i /q "%DIR%data" "%DIR%build\assets" >nul)
cd /d "%DIR%build"
set RUST_LOG=error
set BEVY_LOG=error
set WGPU_LOG=error
"%HERE%python\windows-x86_64\python.exe" -u "%NAME%.py"
