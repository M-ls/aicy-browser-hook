param(
    [switch]$Unsigned,
    [string]$ImageName = 'aicy-browser-hook-builder:1'
)
$ErrorActionPreference = 'Stop'
$hookRoot = $PSScriptRoot
$hookProject = Join-Path $hookRoot 'project'
$hookCache = Join-Path $hookRoot 'work/aicy-hook-build'
$hookRuntime = Join-Path $hookCache 'runtime'
$hookOutput = Join-Path $hookRoot 'dist'
New-Item -ItemType Directory -Force -Path $hookRuntime, $hookOutput | Out-Null
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker is required.' }
docker info --format '{{.ServerVersion}}'
if ($LASTEXITCODE -ne 0) { throw 'Docker is unavailable or permission was denied. Build stopped.' }
& (Join-Path $hookProject 'prepare-dependencies.ps1') -CacheDirectory $hookCache
docker image inspect $ImageName *> $null
if ($LASTEXITCODE -ne 0) {
    docker build --build-context "sdkcache=$hookCache" -t $ImageName -f (Join-Path $hookProject 'Dockerfile') $hookProject
    if ($LASTEXITCODE -ne 0) { throw 'Docker image build failed.' }
}
$hookMode = if ($Unsigned) { 'unsigned' } else { 'local' }
docker run --rm --env "SIGNING_MODE=$hookMode" `
    --mount "type=bind,src=$hookProject,dst=/project,readonly" `
    --mount "type=bind,src=$hookRuntime,dst=/build" `
    --mount "type=bind,src=$hookOutput,dst=/out" $ImageName
if ($LASTEXITCODE -ne 0) { throw 'Module build or validation failed.' }
$hookApkName = if ($Unsigned) { 'AicyBrowserHook-unsigned.apk' } else { 'AicyBrowserHook.apk' }
Get-FileHash -LiteralPath (Join-Path $hookOutput $hookApkName) -Algorithm SHA256
