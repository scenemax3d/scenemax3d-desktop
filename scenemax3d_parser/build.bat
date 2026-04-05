@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
pushd "%SCRIPT_DIR%"

if exist *.java del /Q *.java
if exist *.tokens del /Q *.tokens

set "ANTLR_JAR=%SCRIPT_DIR%antlr-4.5.3-complete.jar"
if not exist "%ANTLR_JAR%" (
    echo Missing ANTLR jar: %ANTLR_JAR%
    exit /b 1
)

if defined JAVA_HOME (
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
    set "JAVAC_EXE=%JAVA_HOME%\bin\javac.exe"
    set "JAR_EXE=%JAVA_HOME%\bin\jar.exe"
) else (
    set "JAVA_EXE=java"
    set "JAVAC_EXE=javac"
    set "JAR_EXE=jar"
)

"%JAVA_EXE%" -Xmx500M -cp "%ANTLR_JAR%" org.antlr.v4.Tool -no-listener -visitor SceneMax.g4
if errorlevel 1 exit /b 1

if not exist build mkdir build
if exist build\com rmdir /S /Q build\com
if exist build\scenemax_parser.jar del /Q build\scenemax_parser.jar

set "CLASSPATH=.;%ANTLR_JAR%;%CLASSPATH%"
"%JAVAC_EXE%" -source 1.8 -target 1.8 -d build SceneMax*.java
if errorlevel 1 exit /b 1

if exist *.java del /Q *.java
if exist *.tokens del /Q *.tokens

pushd build
"%JAR_EXE%" cf scenemax_parser.jar *
if errorlevel 1 exit /b 1

call :copy_if_exists "scenemax_parser.jar" "..\..\scenemax_win_projector\libs"
call :copy_if_exists "scenemax_parser.jar" "..\..\scenemax3d_compiler\libs"
call :copy_if_exists "scenemax_parser.jar" "..\..\..\SceneMax3DGameHub\app\libs"
call :copy_if_exists "scenemax_parser.jar" "..\..\..\scenemax3d-mobile-engine\libs"

popd
popd
exit /b 0

:copy_if_exists
set "SOURCE_FILE=%~1"
set "TARGET_DIR=%~2"
if exist "%TARGET_DIR%" (
    xcopy /Y /I "%SOURCE_FILE%" "%TARGET_DIR%" >nul
)
exit /b 0
