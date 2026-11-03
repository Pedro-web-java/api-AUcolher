# Sobe a API apontando para o PostgreSQL do Aiven.
# Lê DB_URL / DB_USERNAME / DB_PASSWORD de .env.aiven (ignorado pelo Git).
# Argumentos extras vão direto para o Spring, ex.: .\run-aiven.ps1 --server.port=8089
param([Parameter(ValueFromRemainingArguments = $true)] [string[]] $SpringArgs)

$envFile = Join-Path $PSScriptRoot ".env.aiven"
if (-not (Test-Path $envFile)) { throw "Arquivo $envFile não encontrado." }

foreach ($line in Get-Content $envFile) {
    if ($line -match '^\s*([A-Z_]+)=(.*)$') {
        Set-Item -Path "Env:$($Matches[1])" -Value $Matches[2]
    }
}

$env:JAVA_HOME = Join-Path $env:USERPROFILE ".jdks\ms-21.0.12"
# Usa o Maven do PATH; sem ele, procura o que vem com o IntelliJ (a pasta muda conforme a versão)
$mvn = (Get-Command mvn.cmd -ErrorAction SilentlyContinue).Source
if (-not $mvn) {
    $mvn = Get-ChildItem "$env:LOCALAPPDATA\Programs\IntelliJ IDEA*\plugins\maven*\lib\maven3\bin\mvn.cmd",
                         "$env:ProgramFiles\JetBrains\IntelliJ IDEA*\plugins\maven*\lib\maven3\bin\mvn.cmd" -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $mvn) { throw "Maven não encontrado: instale-o ou adicione-o ao PATH." }

Set-Location $PSScriptRoot
if ($SpringArgs) {
    & $mvn -o spring-boot:run "-Dspring-boot.run.arguments=$($SpringArgs -join ' ')"
} else {
    & $mvn -o spring-boot:run
}
