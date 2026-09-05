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

# Keep the order dependency-aware. jfx-demo and the standalone scalajs-jfx CSS
# package are intentionally not part of this publishing set.
$packageDirectories = @(
    "jfx-core",
    "scalajs-jfx-bridge",
    "jfx-json",
    "jfx-router",
    "jfx-controls",
    "jfx-viewport",
    "jfx-forms",
    "jfx-editor"
)

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
    if ($manifest.name -notlike "@anjunar/jfx-*" -and $manifest.name -ne "@anjunar/scalajs-jfx-bridge") {
        throw "Unexpected package name '$($manifest.name)' in $manifestPath."
    }
}

if (-not $SkipLinkBridge) {
    Write-Host "Linking the Scala.js bridge..."
    Invoke-CheckedCommand -Command "sbtn" -Arguments @("scalajs-jfx-bridge/fullLinkJS")
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

    Write-Host "Processing $($manifest.name)@$($manifest.version)..."

    if (-not $SkipVerify) {
        Write-Host "  Running verification..."
        Invoke-CheckedCommand -Command "npm" -Arguments @("--cache", $npmCache, "run", "verify", "--workspace", $workspace)
    }

    $publishArguments = @("--cache", $npmCache, "publish", "--workspace", $workspace, "--access", "public")
    if ($DryRun) {
        $publishArguments += "--dry-run"
        Write-Host "  Packing $($manifest.name) (dry run)..."
    } else {
        Write-Host "  Publishing $($manifest.name)..."
    }

    Invoke-CheckedCommand -Command "npm" -Arguments $publishArguments
}

if ($DryRun) {
    Write-Host "All selected npm packages were packed successfully (dry run)."
} else {
    Write-Host "All selected npm packages were published successfully."
}
