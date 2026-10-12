param([Parameter(Mandatory = $true)][string]$CacheDirectory)
$ErrorActionPreference = 'Stop'
$cachePath = [System.IO.Path]::GetFullPath($CacheDirectory)
New-Item -ItemType Directory -Force -Path $cachePath | Out-Null
$dependencies = @(
    @{ File = 'platform-35_r02.zip'; Url = 'https://dl.google.com/android/repository/platform-35_r02.zip'; Algorithm = 'SHA1'; Hash = '0bb560a90a7a2cbd0dd8348224d518b638fe7949' },
    @{ File = 'build-tools_r35_linux.zip'; Url = 'https://dl.google.com/android/repository/build-tools_r35_linux.zip'; Algorithm = 'SHA1'; Hash = '2cfaa0bbb2336e9ec18ed3ecea84fa2e2af607bc' },
    @{ File = 'xposed-api-82.jar'; Url = 'https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar'; Algorithm = 'SHA256'; Hash = 'f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25' }
)
foreach ($dependency in $dependencies) {
    $destination = Join-Path $cachePath $dependency.File
    $valid = (Test-Path -LiteralPath $destination) -and ((Get-FileHash -LiteralPath $destination -Algorithm $dependency.Algorithm).Hash -eq $dependency.Hash)
    if (-not $valid) { Invoke-WebRequest -Uri $dependency.Url -OutFile $destination -TimeoutSec 600 }
    if ((Get-FileHash -LiteralPath $destination -Algorithm $dependency.Algorithm).Hash -ne $dependency.Hash) { throw "Checksum mismatch: $($dependency.File)" }
    Write-Output "Verified $($dependency.File)"
}
