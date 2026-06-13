@echo off
setlocal enabledelayedexpansion

REM === Configura JDK 21 ===
set JAVA_HOME=C:\openjdk-21_windows-x64_bin\jdk-21
set Path=%JAVA_HOME%\bin;%Path%

REM === Carrega variaveis do .env ===
if exist .env (
    echo [build-jdk21] Carregando .env...
    for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
        set "_line=%%a"
        if not "!_line:~0,1!"=="#" if not "!_line!"=="" (
            set "_key=%%a"
            set "_val=%%b"
            set "_key=!_key: =!"
            if not "!_key!"=="" (
                set "!_key!=!_val!"
            )
        )
    )
)

echo [build-jdk21] JDK: !JAVA_HOME!
echo [build-jdk21] DB:  !DB_URL!
echo [build-jdk21] Iniciando Maven com JDK 21...

C:\Users\pedro\Tools\apache-maven-3.9.9\bin\mvn.cmd %*
