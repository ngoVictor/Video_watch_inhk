param([switch]$InitializeNewProject)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (!$InitializeNewProject) { throw 'Explicit -InitializeNewProject is required.' }
if ((Test-Path -LiteralPath (Join-Path $projectRoot 'publishing/signing-certificate.sha256')) -or (Test-Path -LiteralPath (Join-Path $projectRoot 'publishing/update.json'))) { throw 'This project has a release identity. Restore its signing backup; initialization is forbidden.' }
$directory = Join-Path $projectRoot '.tools/signing'
if (Test-Path -LiteralPath $directory) { throw 'Signing directory already exists. Refusing to overwrite it.' }
$jdk = (Get-ChildItem (Join-Path $projectRoot '.tools/jdk') -Directory | Select-Object -First 1).FullName
if (!$jdk) { throw 'Restore the local JDK before initialization.' }
New-Item -ItemType Directory -Path $directory | Out-Null
try {
    $bytes = New-Object byte[] 32
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    $env:JIAKAN_STORE_PASSWORD = [Convert]::ToBase64String($bytes)
    & (Join-Path $jdk 'bin/keytool.exe') -genkeypair -keystore (Join-Path $directory 'jiakan-release.jks') -storetype JKS -alias jiakan -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=JiaKan TV,OU=Personal App' -storepass:env JIAKAN_STORE_PASSWORD -keypass:env JIAKAN_STORE_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Key initialization failed; preserve the directory for inspection.' }
    @{password=$env:JIAKAN_STORE_PASSWORD} | ConvertTo-Json | Set-Content (Join-Path $directory 'credentials.json')
    Write-Output 'Back up the key and independently record its public certificate pin before releasing.'
} finally { Remove-Item Env:JIAKAN_STORE_PASSWORD -ErrorAction SilentlyContinue }
