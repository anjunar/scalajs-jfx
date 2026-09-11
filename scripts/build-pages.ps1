param()

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

function Invoke-CheckedCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Command,
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Command $($Arguments -join ' ') failed with exit code $LASTEXITCODE."
    }
}

$env:UI_BASE_PATH = "/scalajs-ui/scala"
$env:UI_SITE_URL = "https://anjunar.github.io/scalajs-ui/scala"
Write-Host "Linking the Scala.js demo..."
Invoke-CheckedCommand -Command "sbt" -Arguments @("--server", "scalajs-ui-demo / Compile / fullLinkJS")

Write-Host "Linking the shared Scala.js bridge..."
Invoke-CheckedCommand -Command "sbt" -Arguments @("--server", "scalajs-ui-bridge / Compile / fullLinkJS")

Write-Host "Building the static Pages artifact..."
Invoke-CheckedCommand -Command "node" -Arguments @("tools/build-pages.mjs")
