param([switch]$SkipCentral, [switch]$SkipNpm, [switch]$InstallDependencies)

$ErrorActionPreference = "Stop"
if (-not $SkipCentral) { & (Join-Path $PSScriptRoot "publish-central.ps1"); if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE } }
if (-not $SkipNpm) {
    $arguments = @{}
    if ($InstallDependencies) { $arguments.InstallDependencies = $true }
    & (Join-Path $PSScriptRoot "publish-npm.ps1") @arguments
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
