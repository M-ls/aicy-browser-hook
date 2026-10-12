param(
    [switch]$Unsigned,
    [switch]$Release,
    [string]$SigningKeyPath,
    [string]$ImageName = 'aicy-browser-hook-builder:1'
)
$ErrorActionPreference = 'Stop'
$hookRoot = $PSScriptRoot
$hookProject = Join-Path $hookRoot 'project'
$hookCache = Join-Path $hookRoot 'work/aicy-hook-build'
$hookRuntime = Join-Path $hookCache 'runtime'
$hookOutput = Join-Path $hookRoot 'dist'
if ($Unsigned -and $Release) { throw 'Unsigned and Release cannot be used together.' }
if ($Release) {
    if (-not $SigningKeyPath) { throw 'Release requires SigningKeyPath; a new key will never be generated.' }
    $SigningKeyPath = (Resolve-Path -LiteralPath $SigningKeyPath).Path
    foreach ($name in 'SIGNING_STORE_PASSWORD', 'SIGNING_KEY_PASSWORD', 'SIGNING_KEY_ALIAS', 'SIGNING_CERT_SHA256') {
        if (-not [Environment]::GetEnvironmentVariable($name)) { throw "Release requires $name." }
    }
}
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
$hookMode = if ($Unsigned) { 'unsigned' } elseif ($Release) { 'release' } else { 'local' }
$hookDockerArgs = @('run', '--rm', '--env', "SIGNING_MODE=$hookMode",
    '--mount', "type=bind,src=$hookProject,dst=/project,readonly",
    '--mount', "type=bind,src=$hookRuntime,dst=/build",
    '--mount', "type=bind,src=$hookOutput,dst=/out")
if ($Release) {
    $hookDockerArgs += @('--mount', "type=bind,src=$SigningKeyPath,dst=/signing/release.jks,readonly")
    foreach ($name in 'SIGNING_STORE_PASSWORD', 'SIGNING_KEY_PASSWORD', 'SIGNING_KEY_ALIAS', 'SIGNING_CERT_SHA256') {
        # Pass environment variable names so passwords never appear in command arguments.
        $hookDockerArgs += @('--env', $name)
    }
}
docker @hookDockerArgs $ImageName
if ($LASTEXITCODE -ne 0) { throw 'Module build or validation failed.' }
$hookApkName = if ($Unsigned) { 'AicyBrowserHook-unsigned.apk' } else { 'AicyBrowserHook.apk' }
Get-FileHash -LiteralPath (Join-Path $hookOutput $hookApkName) -Algorithm SHA256
