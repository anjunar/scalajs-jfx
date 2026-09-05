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

$env:JFX_BASE_PATH = "/scalajs-jfx/scala"
$env:JFX_SITE_URL = "https://anjunar.github.io/scalajs-jfx/scala"
Write-Host "Linking the Scala.js demo..."
Invoke-CheckedCommand -Command "sbt" -Arguments @("--server", "scalajs-jfx-demo / Compile / fullLinkJS")

Write-Host "Linking the shared Scala.js bridge..."
Invoke-CheckedCommand -Command "sbt" -Arguments @("--server", "scalajs-jfx-bridge / Compile / fullLinkJS")

Write-Host "Building the static Pages artifact..."
Invoke-CheckedCommand -Command "node" -Arguments @("tools/build-pages.mjs")
