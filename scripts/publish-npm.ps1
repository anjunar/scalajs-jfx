param(
    [switch]$InstallDependencies,
    [switch]$SkipVerify,
    [switch]$SkipLinkBridge,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot
$npmCache = Join-Path $repoRoot "target\npm-publish-cache"
New-Item -ItemType Directory -Force -Path $npmCache | Out-Null

# Keep the order dependency-aware. The CSS package comes first because jfx-core
# declares its matching major as a peer dependency. jfx-demo stays private.
$packageDirectories = @(
    "scalajs-jfx",
    "jfx-core",
    "scalajs-jfx-bridge",
    "jfx-json",
    "jfx-router",
    "jfx-controls",
    "jfx-viewport",
    "jfx-forms",
    "jfx-editor"
)
$releaseVersion = $null

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

foreach ($packageDirectory in $packageDirectories) {
    $manifestPath = Join-Path $repoRoot "npm\$packageDirectory\package.json"
    if (-not (Test-Path $manifestPath)) {
        throw "Package manifest not found: $manifestPath"
    }

    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    if ($manifest.private -eq $true) {
        throw "Refusing to publish private package '$($manifest.name)'."
    }
    if (
        $manifest.name -notlike "@anjunar/jfx-*" -and
        $manifest.name -ne "@anjunar/scalajs-jfx" -and
        $manifest.name -ne "@anjunar/scalajs-jfx-bridge"
    ) {
        throw "Unexpected package name '$($manifest.name)' in $manifestPath."
    }
    if ($null -eq $releaseVersion) {
        $releaseVersion = $manifest.version
    } elseif ($manifest.version -ne $releaseVersion) {
        throw "Release set contains mixed versions: $releaseVersion and $($manifest.version)."
    }
}

if (-not $SkipLinkBridge) {
    Write-Host "Linking the Scala.js bridge..."
    Invoke-CheckedCommand -Command "sbt" -Arguments @("--server", "scalajs-jfx-bridge/fullLinkJS")
}

if ($InstallDependencies) {
    Write-Host "Installing npm workspaces..."
    Invoke-CheckedCommand -Command "npm" -Arguments @("--cache", $npmCache, "ci", "--no-audit", "--no-fund")
} elseif (-not (Test-Path (Join-Path $repoRoot "node_modules"))) {
    throw "node_modules is missing. Run this script with -InstallDependencies."
}

foreach ($packageDirectory in $packageDirectories) {
    $workspace = "npm/$packageDirectory"
    $manifestPath = Join-Path $repoRoot "npm\$packageDirectory\package.json"
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json

    if (-not $SkipVerify) {
        Write-Host "Verifying $($manifest.name)@$($manifest.version)..."
        Invoke-CheckedCommand -Command "npm" -Arguments @("--cache", $npmCache, "run", "verify", "--workspace", $workspace)
    }
}

foreach ($packageDirectory in $packageDirectories) {
    $workspace = "npm/$packageDirectory"
    $manifestPath = Join-Path $repoRoot "npm\$packageDirectory\package.json"
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json

    Write-Host "Packing $($manifest.name)@$($manifest.version) (release preflight)..."
    Invoke-CheckedCommand -Command "npm" -Arguments @("--cache", $npmCache, "publish", "--workspace", $workspace, "--access", "public", "--dry-run")
}

if ($DryRun) {
    Write-Host "The complete npm release set passed verification and packing."
    return
}

foreach ($packageDirectory in $packageDirectories) {
    $workspace = "npm/$packageDirectory"
    $manifestPath = Join-Path $repoRoot "npm\$packageDirectory\package.json"
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json

    Write-Host "Publishing $($manifest.name)@$($manifest.version)..."
    Invoke-CheckedCommand -Command "npm" -Arguments @("--cache", $npmCache, "publish", "--workspace", $workspace, "--access", "public")
}

Write-Host "Verifying the published release set from a clean registry consumer..."
Invoke-CheckedCommand -Command "node" -Arguments @("scripts/verify-published-npm-set.mjs")
Write-Host "All selected npm packages were published and installed successfully."
