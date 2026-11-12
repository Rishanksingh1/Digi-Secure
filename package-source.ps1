param(
    [string]$OutputPath = (Join-Path (Split-Path $PSScriptRoot -Parent) "securedigitallocker-source.zip")
)

$projectRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$archivePath = [System.IO.Path]::GetFullPath($OutputPath)
if ($archivePath.StartsWith($projectRoot + [System.IO.Path]::DirectorySeparatorChar,
        [System.StringComparison]::OrdinalIgnoreCase) -or $archivePath -eq $projectRoot) {
    throw "Choose an archive output path outside the project directory."
}
if (Test-Path -LiteralPath $archivePath) {
    throw "The archive already exists: $archivePath"
}

$excluded = @(".git", ".vscode", "storage", "target")
$items = Get-ChildItem -LiteralPath $projectRoot -Force |
    Where-Object { $_.Name -notin $excluded -and $_.Name -notlike ".env*" }

Compress-Archive -Path $items.FullName -DestinationPath $archivePath
