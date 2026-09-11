$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
    throw "Install Gradle 8.9 or use the included GitHub Actions workflow."
}
gradle wrapper --gradle-version 8.9 --distribution-type bin
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
