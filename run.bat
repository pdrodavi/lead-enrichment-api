@echo off
REM ============================================================
REM  Carrega variaveis do arquivo .env e inicia a aplicacao
REM  Uso: run.bat
REM ============================================================

setlocal enabledelayedexpansion

REM Carrega o .env se existir
if exist .env (
    echo [run.bat] Carregando .env...
    for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
        set "_line=%%a"
        if not "!_line:~0,1!"=="#" if not "!_line!"=="" (
            set "_key=%%a"
            set "_val=%%b"
            set "_key=!_key: =!"
            if not "!_key!"=="" (
                set "!_key!=!_val!"
                echo [run.bat]   SET !_key!=!_val!
            )
        )
    )
) else (
    echo [run.bat] WARN: .env nao encontrado — usando defaults do application.yml
)

echo [run.bat] Iniciando Spring Boot...
mvn spring-boot:run -Dmaven.test.skip=true

endlocal
