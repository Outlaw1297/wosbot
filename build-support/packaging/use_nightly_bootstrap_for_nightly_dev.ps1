param(
    [Parameter(Mandatory = $true)]
    [string] $NightlyDevImage,

    [Parameter(Mandatory = $true)]
    [string] $NightlyImage
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$nightlyDev = (Resolve-Path -LiteralPath $NightlyDevImage).Path
$nightly = (Resolve-Path -LiteralPath $NightlyImage).Path
$files = @(
    @{
        Source = Join-Path $nightly "Frostguard Nightly.exe"
        Destination = Join-Path $nightlyDev "Frostguard Nightly Dev.exe"
        Sha256 = "5c728d3662d64c428d003874f6d62b798bbbe329f595b2b15a2ab5ab1fd1faa9"
    },
    @{
        Source = Join-Path $nightly "FrostguardNightlyWatcher.exe"
        Destination = Join-Path $nightlyDev "FrostguardNightlyDevWatcher.exe"
        Sha256 = "9c7452d890f39c7f4fdb2e5519993514c84f071deef222fe49784acfd459c209"
    }
)

foreach ($file in $files) {
    $sourceHash = (Get-FileHash -LiteralPath $file.Source -Algorithm SHA256).Hash
    if ($sourceHash -ine $file.Sha256) {
        throw "Nightly compatibility bootstrap has unexpected SHA-256: $($file.Source)"
    }
    Copy-Item -LiteralPath $file.Source -Destination $file.Destination -Force
    $destinationHash = (Get-FileHash -LiteralPath $file.Destination -Algorithm SHA256).Hash
    if ($destinationHash -ine $file.Sha256) {
        throw "Nightly Dev compatibility bootstrap copy failed: $($file.Destination)"
    }
}
